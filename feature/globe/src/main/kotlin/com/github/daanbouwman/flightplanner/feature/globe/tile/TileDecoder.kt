package com.github.daanbouwman.flightplanner.feature.globe.tile

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import java.nio.ByteBuffer
import java.util.ArrayDeque

private const val TAG = "TileDecoder"

/**
 * Turns a fetched tile's bytes into the RGB565 pixels the atlas uploads.
 *
 * A seam rather than a private method of the loader because `BitmapFactory`
 * returns null on the JVM — the unit tests run with `isReturnDefaultValues`
 * and no real graphics stack — so everything after the fetch was untestable
 * while the decode was welded to it. The loader's tests hand in a decoder that
 * writes 128 KB of nothing and returns true.
 */
internal fun interface TileDecoder {

    /**
     * Decodes [bytes] into [into], which is cleared and has room for exactly
     * [TileAtlas.TILE_BYTES]. On success the pixels have been written from
     * position 0 and the position advanced past them; the loader flips the
     * buffer and checks the count. Returns false when the bytes are not a
     * `256²` image.
     */
    fun decode(bytes: ByteArray, into: ByteBuffer): Boolean

    /** Frees anything held for reuse. The default holds nothing. */
    fun close() {}
}

/**
 * The real decoder: `BitmapFactory` into a pooled `inBitmap`, then a pixel copy.
 *
 * ### The bitmap pool
 *
 * Bitmaps are reused through `inBitmap`. Without it a fast pan pushes several
 * megabytes a second of short-lived 128 KB objects through the heap, and the
 * resulting collections land as dropped frames on the render thread. The pool is
 * separate from the loader's buffer pool because the two are freed at different
 * moments by different threads: a bitmap is free the instant its pixels have
 * been copied, on a worker, while a buffer is only free once Filament says the
 * driver has finished reading it.
 */
internal class BitmapTileDecoder : TileDecoder {

    private companion object {
        /** Bitmaps held for `inBitmap` reuse. */
        const val BITMAP_POOL_CAP = 8
    }

    private val pool = ArrayDeque<Bitmap>(BITMAP_POOL_CAP)

    override fun decode(bytes: ByteArray, into: ByteBuffer): Boolean {
        val bitmap = decodeBitmap(bytes) ?: return false
        if (bitmap.width != TileAtlas.TILE_PX || bitmap.height != TileAtlas.TILE_PX) {
            Log.w(TAG, "tile decoded at ${bitmap.width}×${bitmap.height}, expected 256²")
            recycle(bitmap)
            return false
        }
        bitmap.copyPixelsToBuffer(into)
        recycle(bitmap)
        return true
    }

    private fun decodeBitmap(bytes: ByteArray): Bitmap? {
        var reuse = synchronized(pool) { pool.pollFirst() }
        val bitmap = try {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options(reuse))
        } catch (e: IllegalArgumentException) {
            // `inBitmap` is rejected when the candidate cannot hold the decoded
            // image. That is the documented contract rather than a fault, so the
            // tile is decoded fresh instead of dropped.
            Log.d(TAG, "inBitmap rejected, decoding fresh: ${e.message}")
            // Recycled *and forgotten*. The failure path below returns `reuse`
            // to the pool, and returning it after this would put a recycled
            // bitmap in front of the next decode, which throws — one entry
            // poisoning the pool for the life of the decoder.
            reuse?.recycle()
            reuse = null
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options(null))
        }
        if (bitmap == null) reuse?.let { recycle(it) }
        return bitmap
    }

    private fun options(reuse: Bitmap?) = BitmapFactory.Options().apply {
        inPreferredConfig = Bitmap.Config.RGB_565
        inMutable = true
        inBitmap = reuse
    }

    private fun recycle(bitmap: Bitmap) {
        // A recycled bitmap in the pool poisons every decode that draws it, and
        // the `inBitmap` rejection path above recycles before it retries.
        if (bitmap.isRecycled) return
        synchronized(pool) {
            if (pool.size < BITMAP_POOL_CAP) pool.addLast(bitmap) else bitmap.recycle()
        }
    }

    override fun close() {
        synchronized(pool) {
            pool.forEach { it.recycle() }
            pool.clear()
        }
    }
}
