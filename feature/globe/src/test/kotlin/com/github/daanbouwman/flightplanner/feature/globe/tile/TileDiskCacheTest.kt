package com.github.daanbouwman.flightplanner.feature.globe.tile

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNull

/**
 * The disk cache on a real directory. Every tile here is small, so each file
 * takes exactly one 4 KB block and the caps below are counted in blocks.
 */
class TileDiskCacheTest {

    private lateinit var root: File
    private lateinit var directory: File

    @BeforeTest
    fun setUp() {
        root = createTempDirectory("tile-disk-cache-test").toFile()
        directory = File(root, "tiles")
    }

    @AfterTest
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun `a tile reads back as it was written, by a new instance too`() {
        val key = TileKey.of(5, 7, 9)
        TileDiskCache(directory).put(Provider, key, tile("seven-nine", expiresAt = 1234L, etag = "\"e\""))

        // A fresh instance has only the directory to go on: the scan is the index.
        val read = TileDiskCache(directory).get(Provider, key).shouldNotBeNull()
        read.body.decodeToString() shouldBe "seven-nine"
        read.expiresAt shouldBe 1234L
        read.etag shouldBe "\"e\""
        read.lastModified shouldBe null
        read.isFresh(1233L) shouldBe true
        read.isFresh(1234L) shouldBe false
    }

    @Test
    fun `providers do not share tiles`() {
        val key = TileKey.of(2, 1, 1)
        val cache = TileDiskCache(directory)
        cache.put(Provider, key, tile("theirs"))
        assertNull(cache.get(Other, key))
    }

    @Test
    fun `the cap is in blocks on disk, and the least recently used goes first`() {
        val cache = TileDiskCache(directory, maxBytesOnDisk = 3 * BLOCK)
        val a = TileKey.of(3, 0, 0)
        val b = TileKey.of(3, 0, 1)
        val c = TileKey.of(3, 0, 2)
        val d = TileKey.of(3, 0, 3)
        cache.put(Provider, a, tile("a"))
        cache.put(Provider, b, tile("b"))
        cache.put(Provider, c, tile("c"))
        // A few dozen bytes each, but a block each on disk.
        cache.size shouldBe 3 * BLOCK

        // Reading `a` makes `b` the eldest, so `b` is what the fourth displaces.
        cache.get(Provider, a).shouldNotBeNull()
        cache.put(Provider, d, tile("d"))

        cache.size shouldBe 3 * BLOCK
        assertNull(cache.get(Provider, b))
        cache.get(Provider, a).shouldNotBeNull()
        cache.get(Provider, c).shouldNotBeNull()
        cache.get(Provider, d).shouldNotBeNull()
        directory.list().orEmpty().size shouldBe 3
    }

    @Test
    fun `recency survives a restart through the files' modification times`() {
        val first = TileDiskCache(directory)
        val old = TileKey.of(4, 0, 0)
        val recent = TileKey.of(4, 0, 1)
        first.put(Provider, old, tile("old"))
        first.put(Provider, recent, tile("recent"))
        File(directory, "test-4-0-0").setLastModified(1_000_000L)
        File(directory, "test-4-0-1").setLastModified(2_000_000L)

        // Reopened with room for one: the scan trims the elder away.
        val reopened = TileDiskCache(directory, maxBytesOnDisk = BLOCK)
        assertNull(reopened.get(Provider, old))
        reopened.get(Provider, recent).shouldNotBeNull()
    }

    @Test
    fun `a file that does not read back is a miss, and is deleted`() {
        val key = TileKey.of(6, 1, 2)
        TileDiskCache(directory).put(Provider, key, tile("fine"))
        val file = File(directory, "test-6-1-2")
        // Right magic, a zero expiry, no ETag, no Last-Modified, then a body
        // length of Int.MAX_VALUE — far past the end of the file.
        file.writeBytes(file.readBytes().copyOf(4) + ByteArray(10) + byteArrayOf(0x7F, -1, -1, -1))

        val cache = TileDiskCache(directory)
        assertNull(cache.get(Provider, key))
        file.exists() shouldBe false
        cache.size shouldBe 0L
    }

    @Test
    fun `a removed tile is gone from disk and from the total`() {
        val key = TileKey.of(1, 1, 0)
        val cache = TileDiskCache(directory)
        cache.put(Provider, key, tile("drop me"))
        cache.remove(Provider, key)
        assertNull(cache.get(Provider, key))
        cache.size shouldBe 0L
        directory.list().orEmpty().size shouldBe 0
    }

    @Test
    fun `opening deletes the obsolete directory and stray temporaries`() {
        val obsolete = File(root, "globe-tiles").apply { mkdirs() }
        File(obsolete, "journal").writeText("libcore.io.DiskLruCache")
        File(obsolete, "0123abcd.0").writeText("an OkHttp entry")
        directory.mkdirs()
        val stray = File(directory, "test-2-0-0123.tmp").apply { writeText("half a tile") }

        val cache = TileDiskCache(directory, obsolete = obsolete)
        cache.size shouldBe 0L
        obsolete.exists() shouldBe false
        stray.exists() shouldBe false
    }

    private fun tile(body: String, expiresAt: Long = Long.MAX_VALUE, etag: String? = null) =
        CachedTile(body.encodeToByteArray(), expiresAt, etag, lastModified = null)

    private companion object {
        const val BLOCK = 4096L
    }

    private object Provider : TileProvider by TestProvider("test")

    private object Other : TileProvider by TestProvider("other")

    private class TestProvider(override val cacheName: String) : TileProvider {
        override val maxLevel: Int = 8
        override val immutable: Boolean = true
        override val attribution = ImageryAttribution("Test", null, "Test imagery", "https://example.test")
        override fun tileUrl(z: Int, x: Int, y: Int): String = "https://example.test/$z/$y/$x"
    }
}
