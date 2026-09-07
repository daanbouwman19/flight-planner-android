package com.github.daanbouwman.flightplanner.feature.globe.tile

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.fail

/**
 * The loader against a real HTTP stack — a `MockWebServer` under the one shared
 * client — with the decode faked, because `BitmapFactory` returns null on the
 * JVM. Every assertion is on what the server saw or on the loader's own
 * bookkeeping, never on decoded pixels.
 *
 * The workers are real coroutines on `Dispatchers.IO`, so the tests wait on the
 * loader's counters with a deadline rather than on a test dispatcher: the thing
 * under test is what happens on the wire, and a virtual clock cannot drive a
 * socket.
 */
class TileLoaderTest {

    private lateinit var server: MockWebServer
    private var serverOpen = false
    private lateinit var cacheDir: File
    private lateinit var client: OkHttpClient
    private val loaders = mutableListOf<TileLoader>()

    /** What the loaders' clock reads; the tests move it by hand. */
    private var clock = 0L

    @BeforeTest
    fun start() {
        server = MockWebServer()
        server.start()
        serverOpen = true
        cacheDir = createTempDirectory("globe-tiles-test").toFile()
        client = TileHttp.build(cacheDir)
        clock = 0L
    }

    @AfterTest
    fun stop() {
        loaders.forEach { it.shutdown() }
        loaders.clear()
        client.cache?.close()
        closeServer()
        cacheDir.deleteRecursively()
    }

    private fun closeServer() {
        if (!serverOpen) return
        serverOpen = false
        server.close()
    }

    /** One worker, so the queue's order is the fetch order. */
    private fun loader(provider: TileProvider, started: Boolean = true): TileLoader =
        TileLoader(
            provider = provider,
            client = client,
            decoder = FakeDecoder,
            workers = 1,
            now = { clock },
        ).also {
            loaders += it
            if (started) it.start()
        }

    @Test
    fun `a tile fetched by one loader is served to the next from the disk cache`() {
        server.respondWith(cacheControl = "max-age=259200")
        val provider = ServerProvider(server, immutable = true)
        val key = TileKey.of(3, 1, 2)

        val first = loader(provider)
        first.request(key)
        awaitUntil("the first fetch") { first.stats().let { it.decoded == 1 && it.pending == 0 } }
        server.requestCount shouldBe 1
        first.stats().fromNetwork shouldBe 1
        server.takeRequest(1, TimeUnit.SECONDS).shouldNotBeNull().headers["User-Agent"] shouldBe
            "FlightPlannerAndroid/1.0 (+https://github.com/daanbouwman19/flight-planner-android)"

        val second = loader(provider)
        second.request(key)
        awaitUntil("the cached fetch") { second.stats().let { it.decoded == 1 && it.pending == 0 } }
        // The whole point: the server never heard about it again.
        server.requestCount shouldBe 1
        second.stats().fromCache shouldBe 1
        second.stats().fromNetwork shouldBe 0
    }

    @Test
    fun `an immutable provider's tiles are cached whatever the origin said`() {
        // GIBS sends three days; an origin that forbids caching is the case worth proving.
        server.respondWith(cacheControl = "no-store")
        val provider = ServerProvider(server, immutable = true)
        val key = TileKey.of(2, 1, 1)

        val first = loader(provider)
        first.request(key)
        awaitUntil("the first fetch") { first.stats().let { it.decoded == 1 && it.pending == 0 } }

        val second = loader(provider)
        second.request(key)
        awaitUntil("the cached fetch") { second.stats().let { it.decoded == 1 && it.pending == 0 } }
        server.requestCount shouldBe 1
        second.stats().fromCache shouldBe 1
    }

    @Test
    fun `a mutable provider's tiles honour the origin's cache policy`() {
        server.respondWith(cacheControl = "no-store")
        val provider = ServerProvider(server, immutable = false)
        val key = TileKey.of(2, 1, 1)

        val first = loader(provider)
        first.request(key)
        awaitUntil("the first fetch") { first.stats().let { it.decoded == 1 && it.pending == 0 } }

        val second = loader(provider)
        second.request(key)
        awaitUntil("the second fetch") { second.stats().let { it.decoded == 1 && it.pending == 0 } }
        server.requestCount shouldBe 2
        second.stats().fromNetwork shouldBe 1
    }

    @Test
    fun `a failed tile is retried with doubling backoff and a missing one never`() {
        server.respondWith { path ->
            when {
                path.startsWith("/tile/5/") -> 500
                path.startsWith("/tile/6/") -> 404
                else -> 200
            }
        }
        val loader = loader(ServerProvider(server, immutable = true))
        val flaky = TileKey.of(5, 3, 3)

        // What the scene does each frame: forget the due time, then re-request
        // what is visible, which re-arms it for whatever is still resting.
        fun frame(key: TileKey) {
            loader.resetRetryDue()
            loader.request(key)
        }

        frame(flaky)
        awaitUntil("the first failure") { loader.stats().let { it.errors == 1 && it.pending == 0 } }
        server.requestCount shouldBe 1
        loader.retryDue(499) shouldBe false
        loader.retryDue(500) shouldBe true

        // Resting: asked for before it is due, nothing happens — and the frame
        // that asked has re-armed the wake-up for when it will be.
        clock = 499
        frame(flaky)
        loader.stats().pending shouldBe 0
        server.requestCount shouldBe 1
        loader.retryDue(499) shouldBe false
        loader.retryDue(500) shouldBe true

        clock = 500
        frame(flaky)
        awaitUntil("the second failure") { loader.stats().let { it.errors == 2 && it.pending == 0 } }
        server.requestCount shouldBe 2
        // Doubled: due at 500 + 1000.
        loader.retryDue(1499) shouldBe false
        loader.retryDue(1500) shouldBe true

        clock = 1499
        frame(flaky)
        loader.stats().pending shouldBe 0
        server.requestCount shouldBe 2

        clock = 1500
        frame(flaky)
        awaitUntil("the third failure") { loader.stats().let { it.errors == 3 && it.pending == 0 } }
        server.requestCount shouldBe 3
        // Doubled again: due at 1500 + 2000.
        loader.retryDue(3499) shouldBe false
        loader.retryDue(3500) shouldBe true

        // A frame that no longer wants the tile leaves nothing armed.
        loader.resetRetryDue()
        loader.retryDue(Long.MAX_VALUE - 1) shouldBe false

        // A 404 is the provider saying the tile does not exist: never again,
        // and nothing to wake up for.
        val missing = TileKey.of(6, 0, 0)
        frame(missing)
        awaitUntil("the 404") { loader.stats().let { it.errors == 4 && it.pending == 0 } }
        server.requestCount shouldBe 4
        clock = 1_000_000
        frame(missing)
        loader.stats().pending shouldBe 0
        server.requestCount shouldBe 4
        loader.retryDue(clock) shouldBe false
    }

    @Test
    fun `a decoded tile is held until the atlas lets it go`() {
        server.respondWith(cacheControl = "no-store")
        val loader = loader(ServerProvider(server, immutable = false))
        val key = TileKey.of(4, 2, 2)

        loader.request(key)
        awaitUntil("the decode") { loader.stats().let { it.decoded == 1 && it.pending == 0 } }
        // Decoded and not yet drained — the window that used to leak a second fetch.
        loader.request(key)
        loader.stats().pending shouldBe 0
        server.requestCount shouldBe 1

        val tile = loader.pollReady().shouldNotBeNull()
        tile.key shouldBe key
        assertNull(loader.pollReady())
        loader.hasWork shouldBe false

        // Drained and uploaded: held still.
        loader.markResident(key)
        loader.request(key)
        loader.stats().pending shouldBe 0
        server.requestCount shouldBe 1

        // Evicted: wanted again.
        loader.markEvicted(key)
        loader.request(key)
        awaitUntil("the refetch") { loader.stats().let { it.decoded == 2 && it.pending == 0 } }
        server.requestCount shouldBe 2
    }

    @Test
    fun `ready tiles drain newest first`() {
        server.respondWith()
        val loader = loader(ServerProvider(server, immutable = true), started = false)
        val coarse = TileKey.of(4, 0, 0)
        val fine = TileKey.of(5, 0, 0)
        // Queued before the worker starts, so the queue's order decides: coarse
        // is fetched first, and fine is the newest thing in the ready deque.
        loader.request(fine)
        loader.request(coarse)
        loader.start()
        awaitUntil("both decodes") { loader.stats().let { it.decoded == 2 && it.pending == 0 } }
        loader.pollReady().shouldNotBeNull().key shouldBe fine
        loader.pollReady().shouldNotBeNull().key shouldBe coarse
    }

    @Test
    fun `offline, a stale cached tile is served and an uncached one fails honestly`() {
        // Cacheable but immediately stale, so the ordinary request has to go to
        // the network and finds it gone.
        server.respondWith(cacheControl = "max-age=0")
        val provider = ServerProvider(server, immutable = false)
        val cached = TileKey.of(3, 0, 0)
        val prime = loader(provider)
        prime.request(cached)
        awaitUntil("priming the cache") { prime.stats().let { it.decoded == 1 && it.pending == 0 } }
        prime.shutdown()
        closeServer()

        val offline = loader(provider)
        offline.request(cached)
        awaitUntil("the stale hit") { offline.stats().let { it.decoded == 1 && it.pending == 0 } }
        offline.stats().fromCache shouldBe 1
        offline.stats().errors shouldBe 0

        // Nothing cached: the FORCE_CACHE retry is a miss, and a miss is a
        // synthetic 504 that has to surface as the original failure — backed
        // off and retried, never recorded as absent.
        val never = TileKey.of(3, 1, 1)
        offline.request(never)
        awaitUntil("the failure") { offline.stats().let { it.errors == 1 && it.pending == 0 } }
        offline.retryDue(499) shouldBe false
        offline.retryDue(500) shouldBe true
        clock = 500
        offline.request(never)
        awaitUntil("the retry") { offline.stats().let { it.errors == 2 && it.pending == 0 } }
    }

    private fun MockWebServer.respondWith(
        cacheControl: String? = null,
        code: (path: String) -> Int = { 200 },
    ) {
        dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val builder = MockResponse.Builder().code(code(request.url.encodedPath)).body("tile")
                if (cacheControl != null) builder.addHeader("Cache-Control", cacheControl)
                return builder.build()
            }
        }
    }

    /**
     * Polls [condition] to a deadline. Generous, because the offline test's
     * failure path is bounded by the client's 4 s connect timeout — twice, with
     * `retryOnConnectionFailure` — on a host that times out rather than refuses;
     * a localhost refusal is immediate and the tests take milliseconds.
     */
    private fun awaitUntil(what: String, timeoutMs: Long = 20_000L, condition: () -> Boolean) {
        val deadline = System.nanoTime() + timeoutMs * 1_000_000L
        while (!condition()) {
            if (System.nanoTime() > deadline) fail("timed out waiting for $what")
            Thread.sleep(2)
        }
    }
}

/** Writes a tile's worth of nothing: the loader checks the count, not the pixels. */
private object FakeDecoder : TileDecoder {
    override fun decode(bytes: ByteArray, into: ByteBuffer): Boolean {
        into.position(into.position() + TileAtlas.TILE_BYTES)
        return true
    }
}

/** A provider pointed at the mock server, with the cache policy under test. */
private class ServerProvider(
    private val server: MockWebServer,
    override val immutable: Boolean,
) : TileProvider {
    override val maxLevel: Int = 8
    override val attribution = ImageryAttribution("Test", null, "Test imagery", "https://example.test")
    override fun tileUrl(z: Int, x: Int, y: Int): String = server.url("/tile/$z/$y/$x").toString()
}
