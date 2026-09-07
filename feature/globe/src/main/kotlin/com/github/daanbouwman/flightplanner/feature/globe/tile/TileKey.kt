package com.github.daanbouwman.flightplanner.feature.globe.tile

/**
 * One slippy-map tile address.
 *
 * A value class over a packed `Long` rather than a data class of three, because
 * this is the key of every map, set and queue in the tile layer and it is
 * created a few thousand times per frame by the quadtree traversal. Packed, it
 * boxes only when it enters a collection and hashes without touching a field.
 *
 * The packing is `z` in 5 bits at bit 48 and `x`, `y` in 24 bits each at bits
 * 24 and 0, which covers levels up to 24 — past the level 18 the keyed Esri
 * provider publishes and the 18 the Rust reference's `quadtree.rs` asks for.
 * It used to be an `Int` with 13 bits per axis, good to level 12: [of] runs on
 * the render thread from the traversal's per-node request callback, so a
 * provider deeper than the packing was an `IllegalArgumentException` per frame
 * rather than detail. The ceiling is checked by [of] rather than assumed.
 */
@JvmInline
internal value class TileKey private constructor(val packed: Long) {

    val z: Int get() = ((packed ushr Z_SHIFT) and Z_MASK).toInt()
    val x: Int get() = ((packed ushr X_SHIFT) and AXIS_MASK).toInt()
    val y: Int get() = (packed and AXIS_MASK).toInt()

    /** The parent tile, or null at the root. */
    fun parent(): TileKey? = if (z == 0) null else of(z - 1, x / 2, y / 2)

    override fun toString(): String = "$z/$x/$y"

    companion object {
        /** Deepest level the packing can represent: 24 bits per axis. */
        const val MAX_PACKABLE_LEVEL: Int = 24

        private const val Z_SHIFT = 48
        private const val X_SHIFT = 24
        private const val Z_MASK = 0x1FL
        private const val AXIS_MASK = 0xFF_FFFFL

        fun of(z: Int, x: Int, y: Int): TileKey {
            require(z in 0..MAX_PACKABLE_LEVEL) { "level $z is outside 0..$MAX_PACKABLE_LEVEL" }
            val span = 1 shl z
            require(x in 0 until span && y in 0 until span) { "tile $z/$x/$y is off the grid" }
            return TileKey((z.toLong() shl Z_SHIFT) or (x.toLong() shl X_SHIFT) or y.toLong())
        }
    }
}
