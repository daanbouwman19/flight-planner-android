package com.github.daanbouwman.flightplanner.feature.globe.tile

import android.os.Handler
import android.os.Looper
import com.google.android.filament.Engine
import com.google.android.filament.Texture
import java.nio.ByteBuffer

/**
 * One texture holding every tile on screen, and the slot bookkeeping around it.
 *
 * ### Why one atlas rather than a texture per tile
 *
 * The desktop original keeps an LRU of 512 individual textures. On a phone that
 * is 128 MB of VRAM and an out-of-memory kill on the first pan, and it is also
 * 512 potential draw calls — a texture bind per tile means the sphere cannot be
 * one primitive. A single 4096² atlas is **32 MB**, fixed, allocated once, and
 * lets the whole visible set be drawn in one call with the slot's UV rect baked
 * into each vertex.
 *
 * 4096 is the smallest edge every device at this app's `minSdk` is guaranteed to
 * support, and at 256-pixel tiles it divides into exactly [SLOTS] slots.
 *
 * ### The pinned floor
 *
 * Levels 0 through [PINNED_MAX_LEVEL] — 1 + 4 + 16 = [PINNED_SLOTS] tiles — are
 * loaded once and **never evicted**. That is what makes the globe legible
 * offline and what guarantees every leaf has an ancestor to fall back on while
 * its own tile is in flight, which is the difference between a globe that
 * sharpens and one that shows holes. The rest of the atlas is a
 * least-recently-used pool of [EVICTABLE_SLOTS].
 *
 * The floor used to be z0–z3, 85 slots. It is z0–z2 now, the trade the Rust
 * reference makes (`tile_manager.rs`: `BASE_LOD = 3`, an exclusive bound): a
 * leaf with nothing closer resident falls back to a z2 crop instead of a z3 one
 * — twice as blurry, for the few frames until its own tile lands — and in
 * exchange 64 more slots are evictable and the session's warm-up is 21 tiles
 * rather than 85. The evictable count is what bounds how much detail a frame may
 * ask for at all, and 171 was too few for a phone-sized viewport: see
 * `Quadtree`'s note on the structural budget.
 *
 * ### Threading
 *
 * Every method here calls into the Filament engine and must therefore be called
 * from the render thread. Decoding happens elsewhere; what arrives here is a
 * finished RGB565 buffer.
 */
internal class TileAtlas(private val engine: Engine) {

    companion object {
        /** Edge of the atlas texture, pixels. */
        const val ATLAS_PX: Int = 4096

        /** Edge of one tile, pixels — the provider's native size. */
        const val TILE_PX: Int = 256

        /** Slots along one edge of the atlas. */
        const val SLOTS_PER_EDGE: Int = ATLAS_PX / TILE_PX

        /** Total slots: 16 × 16. */
        const val SLOTS: Int = SLOTS_PER_EDGE * SLOTS_PER_EDGE

        /** The deepest level held permanently. */
        const val PINNED_MAX_LEVEL: Int = 2

        /**
         * Tiles in levels 0..[PINNED_MAX_LEVEL]: the geometric sum `(4^(n+1) - 1) / 3`.
         *
         * Derived, not written down, so the two constants cannot drift apart —
         * they used to be two hand-written numbers.
         */
        const val PINNED_SLOTS: Int = ((1 shl (2 * (PINNED_MAX_LEVEL + 1))) - 1) / 3

        /** Slots the LRU pool may reclaim. */
        const val EVICTABLE_SLOTS: Int = SLOTS - PINNED_SLOTS

        /** Bytes in one RGB565 tile. */
        const val TILE_BYTES: Int = TILE_PX * TILE_PX * 2

        /**
         * Half a texel, in atlas UV space.
         *
         * Linear filtering at a slot's edge samples its neighbour, which is a
         * different place on Earth — so a tile shows a one-pixel seam of
         * somewhere else along every border. Insetting the UV rect by half a
         * texel keeps every sample inside its own slot. It costs half a pixel of
         * the tile's outermost row, which is invisible; the seam is not.
         */
        const val HALF_TEXEL: Float = 0.5f / ATLAS_PX

        /** "No slot" in the recency list's link arrays. */
        private const val NONE = -1
    }

    /** For the scene to hand to the quadtree, so the two cannot disagree. */
    val evictableSlots: Int get() = EVICTABLE_SLOTS
    val pinnedMaxLevel: Int get() = PINNED_MAX_LEVEL

    /**
     * The atlas texture.
     *
     * RGB565 rather than RGBA8888: the imagery has no alpha and never will, and
     * halving the bytes halves both the resident memory and the upload
     * bandwidth. Banding in a satellite photograph at this scale is not visible;
     * a 64 MB allocation on a mid-range phone is.
     *
     * One mip level. Mipmapping an atlas is a trap — the lower levels blend
     * across slot borders, so a minified tile shows its neighbours smeared into
     * it — and the quadtree is already choosing a level of detail per tile,
     * which is what mipmapping would otherwise be for.
     */
    val texture: Texture = Texture.Builder()
        .width(ATLAS_PX)
        .height(ATLAS_PX)
        .levels(1)
        .sampler(Texture.Sampler.SAMPLER_2D)
        .format(Texture.InternalFormat.RGB565)
        .build(engine)

    /** Which key occupies each slot, or null when the slot is free. */
    private val slotKeys = arrayOfNulls<TileKey>(SLOTS)

    /** Slot index by key, for both the draw path and eviction. */
    private val keyToSlot = HashMap<TileKey, Int>(SLOTS)

    /**
     * Recency order over the evictable slots, as an intrusive doubly-linked list.
     *
     * [lruPrev] and [lruNext] are indexed by slot; [lruHead] is the least
     * recently used slot and [lruTail] the most. Touching a slot is unlink and
     * append, evicting is read the head — both O(1), no allocation, no boxing,
     * in two kilobytes. It replaced an `ArrayDeque<Int>` whose `remove(slot)`
     * was a linear scan over boxed integers per visible tile per frame, under a
     * comment that called it measured; it was not.
     */
    private val lruPrev = IntArray(SLOTS) { NONE }
    private val lruNext = IntArray(SLOTS) { NONE }
    private var lruHead = NONE
    private var lruTail = NONE

    private var nextFreeSlot = 0

    /** Whether [key] currently has a slot. */
    fun contains(key: TileKey): Boolean = keyToSlot.containsKey(key)

    /**
     * When each resident tile's imagery landed, on the scene clock.
     *
     * Kept here rather than in the mesh builder because it is a property of the
     * slot: a tile that is evicted and fetched again is new imagery arriving,
     * and it should sharpen in the same way it did the first time.
     */
    private val arrivalSeconds = HashMap<TileKey, Float>(SLOTS)

    /** The scene clock, in seconds, as of the most recent upload. */
    fun arrivalOf(key: TileKey): Float = arrivalSeconds[key] ?: 0f

    /**
     * The UV rectangle for [key], or null when it is not resident.
     *
     * Returns `[u0, v0, u1, v1]` already inset by half a texel — see
     * [HALF_TEXEL]. Read-only: nothing is fetched as a side effect of drawing.
     */
    fun uvRect(key: TileKey, out: FloatArray): Boolean {
        val slot = keyToSlot[key] ?: return false
        touch(key, slot)
        val col = slot % SLOTS_PER_EDGE
        val row = slot / SLOTS_PER_EDGE
        val u0 = col * TILE_PX / ATLAS_PX.toFloat()
        val v0 = row * TILE_PX / ATLAS_PX.toFloat()
        val span = TILE_PX / ATLAS_PX.toFloat()
        out[0] = u0 + HALF_TEXEL
        out[1] = v0 + HALF_TEXEL
        out[2] = u0 + span - HALF_TEXEL
        out[3] = v0 + span - HALF_TEXEL
        return true
    }

    /**
     * Uploads [pixels] — exactly [TILE_BYTES] of RGB565 — into a slot for [key].
     *
     * [onConsumed] runs once the driver has finished reading the buffer, which
     * is the only moment it is safe to write into again. Filament does **not**
     * copy the pixels: it keeps the descriptor's storage until the upload has
     * actually happened on the GPU timeline, so recycling the buffer on return
     * from this call would hand the pool a buffer that is still being read.
     * That is the kind of race that draws one wrong tile every few minutes.
     *
     * Returns false when every slot is pinned, which cannot happen with the
     * current budgets and is handled rather than asserted so that a future
     * change to either number degrades instead of crashing.
     */
    fun upload(
        key: TileKey,
        pixels: ByteBuffer,
        nowSeconds: Float,
        onConsumed: Runnable,
    ): Boolean {
        val slot = keyToSlot[key] ?: allocate(key) ?: return false
        arrivalSeconds[key] = nowSeconds

        val col = slot % SLOTS_PER_EDGE
        val row = slot / SLOTS_PER_EDGE
        texture.setImage(
            engine,
            0,
            col * TILE_PX,
            row * TILE_PX,
            TILE_PX,
            TILE_PX,
            // **The stride is not optional here, and a zero one crashes.**
            //
            // Filament reads a stride of 0 as "tightly packed", and for a
            // sub-region upload it resolves that against the *texture* width,
            // not the region width. This atlas is 4096 wide, so the default
            // asks for 4096 x 256 x 2 bytes - sixteen times the tile - and
            // throws BufferOverflowException on the first tile that lands.
            // Naming the tile width says what is actually in the buffer.
            Texture.PixelBufferDescriptor(
                pixels,
                Texture.Format.RGB,
                Texture.Type.USHORT_565,
                /* alignment = */ 1,
                /* left = */ 0,
                /* top = */ 0,
                /* stride = */ TILE_PX,
                callbackHandler,
                onConsumed,
            ),
        )
        return true
    }

    /**
     * Where Filament posts the "driver is done with this buffer" callback.
     *
     * The render thread's own looper, so the recycle lands on the thread that
     * owns the pool rather than on whichever internal thread the driver happens
     * to finish on.
     */
    private val callbackHandler = Handler(Looper.myLooper() ?: Looper.getMainLooper())

    /** Told when a slot is reclaimed, so the loader can stop calling the key held. */
    var onEvicted: (TileKey) -> Unit = {}

    private fun allocate(key: TileKey): Int? {
        val pinned = key.z <= PINNED_MAX_LEVEL

        if (nextFreeSlot < SLOTS) {
            val slot = nextFreeSlot++
            occupy(key, slot, pinned)
            return slot
        }

        // Evict the least recently used evictable slot. Pinned slots are never
        // in the recency list, so this can only ever reclaim something the base
        // layer does not depend on.
        val victim = lruHead
        if (victim == NONE) return null
        unlink(victim)
        slotKeys[victim]?.let {
            keyToSlot.remove(it)
            arrivalSeconds.remove(it)
            onEvicted(it)
        }
        occupy(key, victim, pinned)
        return victim
    }

    private fun occupy(key: TileKey, slot: Int, pinned: Boolean) {
        slotKeys[slot] = key
        keyToSlot[key] = slot
        if (!pinned) append(slot)
    }

    private fun touch(key: TileKey, slot: Int) {
        if (key.z <= PINNED_MAX_LEVEL) return
        if (lruTail == slot) return
        unlink(slot)
        append(slot)
    }

    /** Appends [slot] at the most-recently-used end. */
    private fun append(slot: Int) {
        lruPrev[slot] = lruTail
        lruNext[slot] = NONE
        if (lruTail != NONE) lruNext[lruTail] = slot else lruHead = slot
        lruTail = slot
    }

    /** Removes [slot] from the list, wherever it is. */
    private fun unlink(slot: Int) {
        val prev = lruPrev[slot]
        val next = lruNext[slot]
        if (prev != NONE) lruNext[prev] = next else lruHead = next
        if (next != NONE) lruPrev[next] = prev else lruTail = prev
        lruPrev[slot] = NONE
        lruNext[slot] = NONE
    }

    fun destroy() {
        engine.destroyTexture(texture)
        keyToSlot.clear()
        arrivalSeconds.clear()
        lruPrev.fill(NONE)
        lruNext.fill(NONE)
        lruHead = NONE
        lruTail = NONE
        slotKeys.fill(null)
        nextFreeSlot = 0
    }
}
