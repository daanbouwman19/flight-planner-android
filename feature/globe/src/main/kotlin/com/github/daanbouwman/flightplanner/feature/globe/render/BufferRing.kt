package com.github.daanbouwman.flightplanner.feature.globe.render

import android.os.Handler
import android.os.Looper
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * A small rotation of direct byte buffers for geometry that is rewritten every
 * frame.
 *
 * Filament does **not** copy the storage handed to `setBufferAt` or `setBuffer`.
 * It holds the buffer until the driver has actually consumed it, and says so by
 * running the descriptor's callback. Writing into the same buffer on the next
 * frame therefore risks overwriting geometry the GPU is still reading — a race
 * that shows up as the arc flickering or briefly drawing a shape from two
 * frames blended together, and only under load.
 *
 * The fix is to rotate: take a free buffer, fill it, hand it over, and put it
 * back when the callback says the driver is done. Two are usually enough, three
 * covers a frame that gets ahead of the driver.
 */
internal class BufferRing(private val capacityBytes: Int, count: Int = 3) {

    private val free = ArrayDeque<ByteBuffer>(count)
    private val handler = Handler(Looper.myLooper() ?: Looper.getMainLooper())

    init {
        repeat(count) {
            free.addLast(ByteBuffer.allocateDirect(capacityBytes).order(ByteOrder.nativeOrder()))
        }
    }

    /**
     * The next buffer to write into, cleared, or null when every buffer is still
     * with the driver.
     *
     * Null is not a failure: it means this frame is running ahead of the GPU, and
     * the caller keeps the geometry it already handed over — which is one frame
     * stale and indistinguishable at 60 Hz.
     */
    fun acquire(): ByteBuffer? = free.removeFirstOrNull()?.apply { clear() }

    /**
     * Hands a buffer back **without** having given it to the driver.
     *
     * The case this exists for is a frame that acquires a buffer and then
     * decides it has nothing to draw. Dropping it on the floor there is not a
     * leak that costs memory - the ring is three buffers - it is a leak that
     * costs the *ring*: three such frames and [acquire] returns null forever,
     * the geometry silently freezes at whatever was last uploaded, and nothing
     * anywhere reports an error. That is exactly what happened, and it looked
     * like a texture bug for a long time.
     */
    fun release(buffer: ByteBuffer) {
        free.addLast(buffer)
    }

    /** The callback to attach to the descriptor, returning [buffer] to the ring. */
    fun releaseCallback(buffer: ByteBuffer): Pair<Any, Runnable> =
        handler to Runnable { free.addLast(buffer) }

    val capacity: Int get() = capacityBytes
}
