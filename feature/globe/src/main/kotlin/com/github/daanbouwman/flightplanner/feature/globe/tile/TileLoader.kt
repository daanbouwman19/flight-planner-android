package com.github.daanbouwman.flightplanner.feature.globe.tile

import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

private const val TAG = "TileLoader"

/** A tile that has been fetched and decoded, waiting to be uploaded. */
internal class DecodedTile(val key: TileKey, val pixels: ByteBuffer)

/** Counters for the on-screen diagnostics and for the tests to assert against. */
internal data class TileStats(
    val pending: Int = 0,
    val fetched: Int = 0,
    val errors: Int = 0,
    val decoded: Int = 0,
)

/**
 * Fetches, decodes and hands back tiles — everything between a `(z, x, y)` and
 * a buffer the atlas can upload.
 *
 * ### LIFO, not FIFO
 *
 * The queue is a **stack**. During a pan the camera asks for a new set of tiles
 * every frame, and a fair queue would serve the position the finger was at a
 * second ago before the one it is at now — so the globe sharpens behind the
 * gesture and never catches up. Newest first means the visible tiles win and the
 * stale ones are simply never reached, which is the correct outcome for a
 * request nobody is waiting on any more.
 *
 * ### Four workers
 *
 * The desktop uses eight threads. Four is the figure here because these are
 * coroutines on [Dispatchers.IO] sharing one connection pool against one host,
 * where HTTP/2 multiplexes anyway, and because the decode is the expensive half
 * — eight concurrent JPEG decodes on a phone contend for the same couple of big
 * cores that are also running the render thread.
 *
 * ### The two pools
 *
 * Bitmaps are reused through `inBitmap` and pixel buffers are reused through a
 * completion callback from the driver. Together they make the steady state
 * allocation-free; without them a fast pan pushes several megabytes a second of
 * short-lived 128 KB objects through the heap, and the resulting collections
 * land as dropped frames on the render thread.
 *
 * They are separate pools because they are freed at different moments by
 * different threads: a bitmap is free the instant its pixels have been copied,
 * on a worker, while a buffer is only free once Filament says the driver has
 * finished reading it.
 */
internal class TileLoader(
    cacheDir: File,
    private val provider: TileProvider = NasaGibsBlueMarble,
    baseClient: OkHttpClient = OkHttpClient(),
    private val workers: Int = DEFAULT_WORKERS,
) {

    companion object {
        private const val DEFAULT_WORKERS = 4

        /**
         * Disk cache for tiles.
         *
         * Sized to hold the pinned base levels many times over, so airplane mode
         * after any real use still draws a recognisable planet. Its own cache
         * rather than the app's shared one, because tile traffic would otherwise
         * evict the METAR responses — which are tiny, and expensive to lose.
         */
        private const val DISK_CACHE_BYTES = 96L * 1024 * 1024

        /**
         * How many requests may sit in the stack.
         *
         * On overflow the whole stack is dropped rather than trimmed. Every entry
         * in it belongs to some earlier camera position; anything still wanted is
         * re-requested by the next frame's traversal, which is cheap and always
         * correct. Trimming instead would keep an arbitrary half of a stale set.
         */
        private const val PENDING_CAP = 512

        /** Bitmaps held for `inBitmap` reuse. */
        private const val BITMAP_POOL_CAP = 8

        /** Pixel buffers held for re-upload. */
        private const val BUFFER_POOL_CAP = 16

        /** How long a failed tile is left alone. Long enough to rest, short
         *  enough that coming back into signal fills the globe in without a
         *  gesture. */
        private const val RETRY_DELAY_MS = 30_000L

        /** The failure memo is dropped wholesale past this, rather than grown. */
        private const val RETRY_MEMO_CAP = 1_024
    }

    private val http: OkHttpClient = baseClient.newBuilder()
        .cache(Cache(File(cacheDir, "globe-tiles"), DISK_CACHE_BYTES))
        .build()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val stack = ArrayDeque<TileKey>(PENDING_CAP)
    private val pending = HashSet<TileKey>(PENDING_CAP)
    private val stackLock = Any()

    /**
     * Keys the atlas holds, mirrored here so a worker can check residency
     * without reaching into the atlas.
     *
     * The atlas's own maps belong to the render thread and are not synchronised;
     * reading them from a worker would be a data race that shows up as a
     * corrupted slot rather than as a crash. The renderer keeps this in step
     * through [markResident] and [markEvicted].
     */
    private val resident: MutableSet<TileKey> = ConcurrentHashMap.newKeySet()

    /**
     * Tiles that failed, and when they may be asked for again.
     *
     * **Without this a failure is a busy loop.** The traversal requests every
     * visible node every frame; a tile that 404s or times out never becomes
     * resident, so it is requested again on the next frame, and again — four IO
     * workers retrying the same URLs at vsync rate, on a picture that is not
     * changing. It also keeps [hasWork] permanently true, so the render loop
     * never gets to decide the scene has settled: offline, the globe would draw
     * at 60 Hz forever.
     *
     * A delay rather than a permanent memo, because most failures here are the
     * network being away rather than the tile not existing, and the two are not
     * distinguishable from a failed request.
     */
    private val retryAfter = ConcurrentHashMap<TileKey, Long>()

    /**
     * Wakes a sleeping worker. Conflated because it carries no payload — a
     * worker that wakes to an empty stack simply sleeps again.
     */
    private val wakeup = Channel<Unit>(Channel.CONFLATED)

    /** Finished tiles, drained by the render thread. */
    private val ready = ConcurrentLinkedQueue<DecodedTile>()

    private val bitmapPool = ArrayDeque<Bitmap>(BITMAP_POOL_CAP)
    private val bufferPool = ConcurrentLinkedQueue<ByteBuffer>()

    private val fetched = AtomicInteger()
    private val errors = AtomicInteger()
    private val decoded = AtomicInteger()

    private var jobs: List<Job> = emptyList()

    val attribution: String get() = provider.attribution
    val maxLevel: Int get() = provider.maxLevel

    fun start() {
        if (jobs.isNotEmpty()) return
        jobs = List(workers) { scope.launch { workerLoop() } }
    }

    fun markResident(key: TileKey) {
        resident.add(key)
    }

    fun markEvicted(key: TileKey) {
        resident.remove(key)
    }

    /**
     * Queues [key] unless the atlas already has it or it is already in flight.
     *
     * Called once per visited quadtree node per frame — a few hundred times —
     * so both early exits matter.
     */
    fun request(key: TileKey) {
        if (key.z > provider.maxLevel) return
        if (key in resident) return
        retryAfter[key]?.let { at ->
            if (SystemClock.uptimeMillis() < at) return
            retryAfter.remove(key)
        }
        synchronized(stackLock) {
            if (!pending.add(key)) return
            if (stack.size >= PENDING_CAP) {
                // **Only the queued backlog goes.** `pending` is also the
                // in-flight set - a worker drops its key in `workerLoop`’s
                // `finally`, not when it pops it - so clearing the whole thing
                // forgets the tiles four workers are currently fetching. The
                // next frame then re-requests one, `add` succeeds, and two
                // workers fetch and decode the same tile; whichever finishes
                // first removes the other’s entry and lets a third in. That is
                // duplicated network and duplicated decode on exactly the frames
                // that are already behind, which is what overflowed the stack.
                for (queued in stack) pending.remove(queued)
                stack.clear()
            }
            stack.addLast(key)
        }
        wakeup.trySend(Unit)
    }

    /**
     * Hands over everything decoded since the last call, up to [limit].
     *
     * Capped because uploading is a synchronous texture write on the render
     * thread: draining fifty tiles in the frame after a pan settles is a visible
     * hitch, and spreading them over a few frames is invisible — each one only
     * sharpens a tile that already has a coarse ancestor drawn under it.
     */
    fun drainReady(limit: Int, into: MutableList<DecodedTile>) {
        into.clear()
        while (into.size < limit) {
            into += ready.poll() ?: break
        }
    }

    /** Returns a buffer to the pool once the driver has finished reading it. */
    fun recycleBuffer(buffer: ByteBuffer) {
        if (bufferPool.size < BUFFER_POOL_CAP) bufferPool.offer(buffer)
    }

    /**
     * Whether anything is queued, in flight, or waiting to be uploaded.
     *
     * The render loop asks this to decide whether a frame is worth drawing at
     * all. Cheap on purpose: two emptiness checks, one of them under the lock
     * the request path already takes a few hundred times a frame.
     */
    val hasWork: Boolean
        get() = ready.isNotEmpty() || synchronized(stackLock) { pending.isNotEmpty() }

    fun stats(): TileStats = TileStats(
        pending = synchronized(stackLock) { pending.size },
        fetched = fetched.get(),
        errors = errors.get(),
        decoded = decoded.get(),
    )

    fun shutdown() {
        scope.cancel()
        wakeup.close()
        synchronized(stackLock) {
            stack.clear()
            pending.clear()
        }
        synchronized(bitmapPool) {
            bitmapPool.forEach { it.recycle() }
            bitmapPool.clear()
        }
        bufferPool.clear()
        ready.clear()
        resident.clear()
        jobs = emptyList()
    }

    private suspend fun workerLoop() {
        while (scope.isActive) {
            val key = popNewest()
            if (key == null) {
                wakeup.receiveCatching().getOrNull() ?: return
                continue
            }
            try {
                fetchAndDecode(key)
            } finally {
                synchronized(stackLock) { pending.remove(key) }
            }
        }
    }

    /**
     * The newest request still worth doing.
     *
     * Entries can go stale between being pushed and being popped — another
     * worker may have finished the same key, or the atlas may have gained it
     * from a cache hit — so the stack is drained past them rather than
     * returning work that would be thrown away after a network round trip.
     */
    private fun popNewest(): TileKey? = synchronized(stackLock) {
        var key = stack.pollLast()
        while (key != null && (key !in pending || key in resident)) {
            // **Dropping it from the stack is not enough — it has to leave
            // `pending` too.** A key that became resident between being requested
            // and being popped is never fetched, so nothing ever reaches the
            // `finally` that would remove it, and `request` will not re-add it
            // because a resident key returns before the add. It would sit in
            // `pending` for the life of the loader, which made the loader claim
            // work it was never going to do and kept the render loop from ever
            // deciding the scene had settled.
            pending.remove(key)
            key = stack.pollLast()
        }
        key
    }

    private fun fetchAndDecode(key: TileKey) {
        val bytes = fetch(key) ?: return failed(key)
        val bitmap = decode(key, bytes) ?: return failed(key)

        if (bitmap.width != TileAtlas.TILE_PX || bitmap.height != TileAtlas.TILE_PX) {
            Log.w(TAG, "tile $key decoded at ${bitmap.width}×${bitmap.height}, expected 256²")
            errors.incrementAndGet()
            recycleBitmap(bitmap)
            return failed(key)
        }

        val buffer = bufferPool.poll() ?: ByteBuffer
            .allocateDirect(TileAtlas.TILE_BYTES)
            // Native order, because `copyPixelsToBuffer` writes the bitmap's own
            // 16-bit words and Filament reads them back the same way. The JVM's
            // default is big-endian, which would swap every pixel's two bytes
            // and tint the whole planet.
            .order(ByteOrder.nativeOrder())
        buffer.clear()
        bitmap.copyPixelsToBuffer(buffer)
        buffer.flip()
        recycleBitmap(bitmap)

        decoded.incrementAndGet()
        ready.add(DecodedTile(key, buffer))
    }

    private fun fetch(key: TileKey): ByteArray? {
        val url = provider.tileUrl(key.z, key.x, key.y)
        return try {
            http.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) {
                    errors.incrementAndGet()
                    null
                } else {
                    response.body.bytes()
                }
            }
        } catch (e: Exception) {
            // Offline is the ordinary case here, not an exceptional one: the
            // pinned base levels are already drawn and the globe simply stops
            // sharpening. Logged at debug so a real failure is still findable.
            Log.d(TAG, "tile $key could not be fetched: ${e.message}")
            errors.incrementAndGet()
            null
        }?.also { fetched.incrementAndGet() }
    }

    private fun decode(key: TileKey, bytes: ByteArray): Bitmap? {
        var reuse = synchronized(bitmapPool) { bitmapPool.pollFirst() }
        val bitmap = try {
            BitmapFactory.decodeByteArray(
                bytes,
                0,
                bytes.size,
                BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.RGB_565
                    inMutable = true
                    inBitmap = reuse
                },
            )
        } catch (e: IllegalArgumentException) {
            // `inBitmap` is rejected when the candidate cannot hold the decoded
            // image. That is the documented contract rather than a fault, so the
            // tile is decoded fresh instead of dropped.
            Log.d(TAG, "inBitmap rejected for $key, decoding fresh: ${e.message}")
            // Recycled *and forgotten*. The failure path below returns `reuse`
            // to the pool, and returning it after this would put a recycled
            // bitmap in front of the next decode, which throws — one entry
            // poisoning the pool for the life of the loader.
            reuse?.recycle()
            reuse = null
            BitmapFactory.decodeByteArray(
                bytes,
                0,
                bytes.size,
                BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.RGB_565
                    inMutable = true
                },
            )
        }
        if (bitmap == null) {
            errors.incrementAndGet()
            reuse?.let { recycleBitmap(it) }
        }
        return bitmap
    }

    /** Holds [key] back for [RETRY_DELAY_MS], and keeps the memo bounded. */
    private fun failed(key: TileKey) {
        if (retryAfter.size > RETRY_MEMO_CAP) retryAfter.clear()
        retryAfter[key] = SystemClock.uptimeMillis() + RETRY_DELAY_MS
    }

    private fun recycleBitmap(bitmap: Bitmap) {
        // A recycled bitmap in the pool poisons every decode that draws it, and
        // the `inBitmap` rejection path below recycles before it retries.
        if (bitmap.isRecycled) return
        synchronized(bitmapPool) {
            if (bitmapPool.size < BITMAP_POOL_CAP) bitmapPool.addLast(bitmap) else bitmap.recycle()
        }
    }
}
