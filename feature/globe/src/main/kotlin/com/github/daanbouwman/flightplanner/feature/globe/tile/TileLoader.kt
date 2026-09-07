package com.github.daanbouwman.flightplanner.feature.globe.tile

import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.HttpURLConnection
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.min

private const val TAG = "TileLoader"

/** A tile that has been fetched and decoded, waiting to be uploaded. */
internal class DecodedTile(val key: TileKey, val pixels: ByteBuffer)

/** Counters for the on-screen diagnostics and for the tests to assert against. */
internal data class TileStats(
    /** Queued or in flight. */
    val pending: Int = 0,
    /** Bodies that came over the wire, revalidations included. */
    val fromNetwork: Int = 0,
    /** Bodies served from the disk cache without a round trip. */
    val fromCache: Int = 0,
    val errors: Int = 0,
    val decoded: Int = 0,
)

/**
 * Fetches, decodes and hands back tiles — everything between a `(z, x, y)` and
 * a buffer the atlas can upload.
 *
 * ### The queue: coarse first, then newest first
 *
 * Requests go into [TileQueue], one deque per level, popped shallowest level
 * first and newest entry first within a level. Coarse first is what gives every
 * leaf an ancestor to be drawn from while its own tile is in flight; newest
 * first is what makes the globe sharpen where the finger *is* rather than where
 * it was a second ago. See that class for the failure the single stack it
 * replaced produced.
 *
 * ### Eight workers
 *
 * The same figure as `tile_manager.rs`'s `NUM_WORKERS`. The worker count **is**
 * the network concurrency: the fetch is a blocking `execute()`, so OkHttp's
 * per-host dispatcher limit does not apply, and GIBS is HTTP/1.1-only so there
 * is no multiplexing over fewer connections. The decode is not split onto a
 * dispatcher of its own because a 256² RGB565 decode is one to three
 * milliseconds against a round trip of two hundred or more — a worker spends
 * its life waiting on the socket, not on the codec.
 *
 * ### Held, not resident
 *
 * [held] is every key this loader has produced and not been told was thrown
 * away — decoded and waiting in [ready], or uploaded and sitting in the atlas.
 * It is marked **before** the tile enters the ready queue, because a key that
 * has left `pending` and not yet been uploaded is otherwise re-requested by the
 * very next traversal: a duplicate fetch, a duplicate decode, and a second
 * upload that reset the tile's arrival time and re-ran its sharpen fade.
 *
 * ### Failure is memoed with backoff
 *
 * **Without a memo a failure is a busy loop.** The traversal requests every
 * visible node every frame; a tile that times out never becomes held, so it is
 * asked for again next frame, and again — eight workers retrying the same URLs
 * at vsync rate on a picture that is not changing. The memo holds a tile back
 * for 500 ms after its first failure and doubles that each time, capped at
 * 30 s, so a flaky connection recovers in half a second and a dead one costs a
 * request every half minute. HTTP 400 and 404 are different: GIBS answers 400
 * above a layer's ceiling and a partial-coverage layer 404s, and neither will
 * ever succeed, so they go into [absent] and are never asked for again.
 *
 * ### The buffer pool
 *
 * Pixel buffers are reused through a completion callback from the driver, which
 * is what keeps the steady state allocation-free. It holds enough for a full
 * ready queue plus one per worker; smaller than that, and every entry of a
 * backlog allocates a fresh 128 KB direct buffer.
 *
 * [now] and [decoder] are injectable for the tests: `SystemClock` returns 0 on
 * the JVM and `BitmapFactory` returns null, so nothing here would ever expire
 * or decode without them.
 */
internal class TileLoader(
    private val provider: TileProvider = NasaGibsBlueMarble,
    private val client: OkHttpClient,
    private val decoder: TileDecoder = BitmapTileDecoder(),
    private val workers: Int = DEFAULT_WORKERS,
    private val now: () -> Long = { SystemClock.uptimeMillis() },
) {

    companion object {
        /** Parity with `tile_manager.rs`: `NUM_WORKERS = 8`. See the class note. */
        private const val DEFAULT_WORKERS = 8

        /**
         * How many requests may sit in the queue.
         *
         * On overflow the deepest buckets are dropped, whole, until the queue is
         * half empty. Every entry belongs to some earlier camera position;
         * anything still wanted is re-requested by the next frame's traversal,
         * which is cheap and always correct, and the deep ones are both the
         * most likely to be stale and the ones with a coarser fallback.
         */
        private const val PENDING_CAP = 512

        /**
         * Decoded tiles waiting for the render thread.
         *
         * Bounded, because a hidden surface drains nothing: without a bound the
         * workers would fill memory with tiles nobody is uploading. When it is
         * full the oldest is dropped and its key un-held, so it is re-requested
         * — from the disk cache — the next time it is wanted.
         */
        private const val READY_CAP = 64

        /** First backoff after a failure; doubles per attempt up to [MAX_RETRY_MS]. */
        private const val BASE_RETRY_MS = 500L

        /** Long enough to rest, short enough that coming back into signal fills
         *  the globe in without a gesture. */
        private const val MAX_RETRY_MS = 30_000L

        /** The memos are dropped wholesale past this, rather than grown. */
        private const val RETRY_MEMO_CAP = 1_024

        /**
         * Doublings past which the shift alone would exceed [MAX_RETRY_MS]; the
         * attempt count keeps climbing, the delay does not.
         */
        private const val MAX_BACKOFF_DOUBLINGS = 8

        /** The attempt count lives in the low byte of a memo; the due time above it. */
        private const val ATTEMPT_BITS = 8
        private const val ATTEMPT_MASK = (1L shl ATTEMPT_BITS) - 1

        private fun dueOf(memo: Long): Long = memo ushr ATTEMPT_BITS
        private fun attemptsOf(memo: Long): Int = (memo and ATTEMPT_MASK).toInt()
        private fun memo(due: Long, attempts: Int): Long =
            (due shl ATTEMPT_BITS) or min(attempts, ATTEMPT_MASK.toInt()).toLong()
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val queue = TileQueue(provider.maxLevel)

    /** Queued or in flight. A worker drops its key in its `finally`, not on pop. */
    private val pending = HashSet<TileKey>(PENDING_CAP)
    private val queueLock = Any()

    /**
     * Keys this loader has produced: decoded and waiting, or in the atlas.
     *
     * Mirrored here rather than read from the atlas because the atlas's maps
     * belong to the render thread and are not synchronised; reading them from
     * a worker would be a data race that shows up as a corrupted slot rather
     * than as a crash. The renderer keeps this in step through [markResident]
     * and [markEvicted].
     */
    private val held: MutableSet<TileKey> = ConcurrentHashMap.newKeySet()

    /** Failed tiles: when each may be asked for again, and how often it has failed. */
    private val retryAfter = ConcurrentHashMap<TileKey, Long>()

    /** Tiles the provider has said do not exist. Never asked for again. */
    private val absent: MutableSet<TileKey> = ConcurrentHashMap.newKeySet()

    /**
     * The soonest any memoed tile becomes due, or `Long.MAX_VALUE` when none is.
     *
     * The render loop asks [retryDue] so a settled globe wakes for exactly one
     * frame when a failed tile may be retried, instead of either polling at
     * 60 Hz or never retrying until the camera moves. The scene resets it
     * before each traversal and the traversal re-arms it — [request] on a key
     * that is not yet due folds that key's due time back in — so it always
     * describes the tiles that were wanted last frame.
     */
    private val earliestRetryAt = AtomicLong(Long.MAX_VALUE)

    /**
     * Wakes a sleeping worker. One token per queued request, unbounded: it used
     * to be conflated, and because [request] returns before sending when a key
     * is already pending, two idle workers could be woken as one.
     */
    private val wakeup = Channel<Unit>(Channel.UNLIMITED)

    /** Finished tiles, drained newest-first by the render thread. */
    private val ready = ArrayDeque<DecodedTile>(READY_CAP)
    private val readyLock = Any()

    private val bufferPool = ConcurrentLinkedQueue<ByteBuffer>()
    private val bufferPoolCap = READY_CAP + workers

    private val fromNetwork = AtomicInteger()
    private val fromCache = AtomicInteger()
    private val errors = AtomicInteger()
    private val decoded = AtomicInteger()

    private var jobs: List<Job> = emptyList()

    val attribution: ImageryAttribution get() = provider.attribution
    val maxLevel: Int get() = provider.maxLevel

    fun start() {
        if (jobs.isNotEmpty()) return
        jobs = List(workers) { scope.launch { workerLoop() } }
    }

    /** The atlas has [key]. Idempotent: the loader usually already holds it. */
    fun markResident(key: TileKey) {
        held.add(key)
    }

    /** The atlas has given [key]'s slot away, or declined to take it. */
    fun markEvicted(key: TileKey) {
        held.remove(key)
    }

    /**
     * Queues [key] unless it is held, in flight, known absent, or resting.
     *
     * Called once per visited quadtree node per frame — a few hundred times —
     * so every early exit matters.
     */
    fun request(key: TileKey) = enqueue(key, prefetch = false)

    /** Queues [key] behind everything on screen. See [TileQueue]. */
    fun prefetch(key: TileKey) = enqueue(key, prefetch = true)

    private fun enqueue(key: TileKey, prefetch: Boolean) {
        if (key.z > provider.maxLevel) return
        if (key in held) return
        if (key in absent) return
        retryAfter[key]?.let { memo ->
            val due = dueOf(memo)
            if (now() < due) {
                earliestRetryAt.accumulateAndGet(due) { a, b -> min(a, b) }
                return
            }
        }
        synchronized(queueLock) {
            if (!pending.add(key)) return
            if (queue.size >= PENDING_CAP) {
                // **Only the queued backlog goes.** `pending` is also the
                // in-flight set — a worker drops its key in `workerLoop`'s
                // `finally`, not when it pops it — so clearing the whole thing
                // forgets the tiles the workers are currently fetching. The
                // next frame then re-requests one, `add` succeeds, and two
                // workers fetch and decode the same tile; whichever finishes
                // first removes the other's entry and lets a third in. That is
                // duplicated network and duplicated decode on exactly the frames
                // that are already behind, which is what overflowed the queue.
                queue.dropDeepestUntil(PENDING_CAP / 2) { dropped -> pending.remove(dropped) }
            }
            queue.push(key, prefetch)
        }
        wakeup.trySend(Unit)
    }

    /**
     * The most recently decoded tile, or null when none is waiting.
     *
     * Newest first, for the same reason the queue is: a tile decoded a moment
     * ago is for where the camera is, and one from the start of a pan may be
     * for a place that has scrolled off.
     */
    fun pollReady(): DecodedTile? = synchronized(readyLock) { ready.pollFirst() }

    /** Returns a buffer to the pool once the driver has finished reading it. */
    fun recycleBuffer(buffer: ByteBuffer) {
        if (bufferPool.size < bufferPoolCap) bufferPool.offer(buffer)
    }

    /**
     * Whether anything is queued, in flight, or waiting to be uploaded.
     *
     * The render loop asks this to decide whether a frame is worth drawing at
     * all. Cheap on purpose: two emptiness checks under two locks.
     */
    val hasWork: Boolean
        get() = synchronized(readyLock) { ready.isNotEmpty() } ||
            synchronized(queueLock) { pending.isNotEmpty() }

    /** Whether a memoed failure has become due for another try. */
    fun retryDue(now: Long = this.now()): Boolean = now >= earliestRetryAt.get()

    /**
     * Forgets the pending retry time. The scene calls this immediately before a
     * traversal, whose requests re-arm it for whatever is still wanted.
     */
    fun resetRetryDue() {
        earliestRetryAt.set(Long.MAX_VALUE)
    }

    fun stats(): TileStats = TileStats(
        pending = synchronized(queueLock) { pending.size },
        fromNetwork = fromNetwork.get(),
        fromCache = fromCache.get(),
        errors = errors.get(),
        decoded = decoded.get(),
    )

    /**
     * Stops the workers and cancels this loader's in-flight calls.
     *
     * The client is shared by every loader in the process, so `cancelAll()`
     * would take another session's calls down with these; every request is
     * tagged with the loader that made it, and only those are cancelled.
     */
    fun shutdown() {
        scope.cancel()
        wakeup.close()
        for (call in client.dispatcher.runningCalls()) {
            if (call.request().tag(TileLoader::class.java) === this) call.cancel()
        }
        synchronized(queueLock) {
            queue.clear()
            pending.clear()
        }
        decoder.close()
        bufferPool.clear()
        synchronized(readyLock) { ready.clear() }
        held.clear()
        jobs = emptyList()
    }

    private suspend fun workerLoop() {
        while (scope.isActive) {
            val key = popNext()
            if (key == null) {
                wakeup.receiveCatching().getOrNull() ?: return
                continue
            }
            try {
                fetchAndDecode(key)
            } catch (e: RuntimeException) {
                // A worker dying takes an eighth of the throughput with it for
                // the life of the session, and an uncaught exception on a
                // coroutine takes the process. A tile that cannot be produced
                // is backed off like any other failure and the fault is logged
                // where it can be found.
                Log.e(TAG, "tile $key failed unexpectedly", e)
                errors.incrementAndGet()
                failed(key)
            } finally {
                synchronized(queueLock) { pending.remove(key) }
            }
        }
    }

    /**
     * The next request still worth doing.
     *
     * Entries can go stale between being pushed and being popped — another
     * worker may have finished the same key, or the overflow valve may have
     * dropped it — so the queue is drained past them rather than returning work
     * that would be thrown away after a network round trip.
     */
    private fun popNext(): TileKey? = synchronized(queueLock) {
        var key = queue.pop()
        while (key != null && (key !in pending || key in held)) {
            // **Dropping it from the queue is not enough — it has to leave
            // `pending` too.** A key that became held between being requested
            // and being popped is never fetched, so nothing ever reaches the
            // `finally` that would remove it, and `request` will not re-add it
            // because a held key returns before the add. It would sit in
            // `pending` for the life of the loader, which made the loader claim
            // work it was never going to do and kept the render loop from ever
            // deciding the scene had settled.
            pending.remove(key)
            key = queue.pop()
        }
        key
    }

    private fun fetchAndDecode(key: TileKey) {
        val bytes = fetch(key) ?: return

        val buffer = bufferPool.poll() ?: ByteBuffer
            .allocateDirect(TileAtlas.TILE_BYTES)
            // Native order, because `copyPixelsToBuffer` writes the bitmap's own
            // 16-bit words and Filament reads them back the same way. The JVM's
            // default is big-endian, which would swap every pixel's two bytes
            // and tint the whole planet.
            .order(ByteOrder.nativeOrder())
        buffer.clear()
        val ok = decoder.decode(bytes, buffer)
        buffer.flip()
        if (!ok || buffer.remaining() != TileAtlas.TILE_BYTES) {
            Log.d(TAG, "tile $key did not decode to 256² RGB565")
            errors.incrementAndGet()
            recycleBuffer(buffer)
            failed(key)
            return
        }

        decoded.incrementAndGet()
        retryAfter.remove(key)
        // Held first, then offered: see the class note.
        held.add(key)
        offerReady(DecodedTile(key, buffer))
    }

    /**
     * Queues a decoded tile, dropping the **deepest** when the queue is full.
     *
     * The same rule the request queue drops by, and for the same reason. Tiles
     * are fetched coarsest-first, so they arrive here in that order and the tail
     * is the finest: a leaf whose own tile is dropped still draws, from the
     * ancestor under it, whereas an ancestor that is dropped takes every leaf
     * that was relying on it back to the pinned floor. Draining is
     * correspondingly first-in-first-out — see [pollReady] — so the atlas gains
     * a level's ancestors before the level itself.
     */
    private fun offerReady(tile: DecodedTile) {
        val dropped = synchronized(readyLock) {
            val deepest = if (ready.size >= READY_CAP) ready.pollLast() else null
            ready.addLast(tile)
            deepest
        }
        if (dropped != null) {
            held.remove(dropped.key)
            recycleBuffer(dropped.pixels)
        }
    }

    private fun fetch(key: TileKey): ByteArray? {
        val request = Request.Builder()
            .url(provider.tileUrl(key.z, key.x, key.y))
            // The provider decides the cache policy in `TileHttp`, and the
            // loader is what `shutdown` cancels by.
            .tag(TileProvider::class.java, provider)
            .tag(TileLoader::class.java, this)
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                when {
                    response.isSuccessful -> {
                        val body = response.body.bytes()
                        if (response.networkResponse != null) {
                            fromNetwork.incrementAndGet()
                        } else {
                            fromCache.incrementAndGet()
                        }
                        body
                    }
                    response.code == HttpURLConnection.HTTP_BAD_REQUEST ||
                        response.code == HttpURLConnection.HTTP_NOT_FOUND -> {
                        errors.incrementAndGet()
                        markAbsent(key)
                        null
                    }
                    else -> {
                        errors.incrementAndGet()
                        failed(key)
                        null
                    }
                }
            }
        } catch (e: IOException) {
            // Offline is the ordinary case here, not an exceptional one: the
            // pinned base levels are already drawn and the globe simply stops
            // sharpening. Logged at debug so a real failure is still findable.
            Log.d(TAG, "tile $key could not be fetched: ${e.message}")
            errors.incrementAndGet()
            failed(key)
            null
        }
    }

    /** Holds [key] back for twice as long as last time, and keeps the memo bounded. */
    private fun failed(key: TileKey) {
        if (retryAfter.size > RETRY_MEMO_CAP) retryAfter.clear()
        val attempts = (retryAfter[key]?.let(::attemptsOf) ?: 0) + 1
        val delay = min(BASE_RETRY_MS shl min(attempts - 1, MAX_BACKOFF_DOUBLINGS), MAX_RETRY_MS)
        val due = now() + delay
        retryAfter[key] = memo(due, attempts)
        earliestRetryAt.accumulateAndGet(due) { a, b -> min(a, b) }
    }

    /** The provider says [key] does not exist. Bounded like the retry memo. */
    private fun markAbsent(key: TileKey) {
        if (absent.size > RETRY_MEMO_CAP) absent.clear()
        absent.add(key)
    }
}
