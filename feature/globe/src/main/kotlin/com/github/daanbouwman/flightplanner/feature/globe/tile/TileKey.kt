package com.github.daanbouwman.flightplanner.feature.globe.tile

/**
 * One slippy-map tile address.
 *
 * A value class over a packed `Int` rather than a data class of three, because
 * this is the key of every map, set and queue in the tile layer and it is
 * created a few thousand times per frame by the quadtree traversal. Packed, it
 * boxes only when it enters a collection and hashes without touching a field.
 *
 * The packing is `z` in the top 5 bits and `x`, `y` in 13 each, which covers
 * levels up to 12 — comfortably past the level 8 any provider here publishes,
 * and checked by [of] rather than assumed.
 */
@JvmInline
internal value class TileKey private constructor(val packed: Int) {

    val z: Int get() = (packed ushr 26) and 0x1F
    val x: Int get() = (packed ushr 13) and 0x1FFF
    val y: Int get() = packed and 0x1FFF

    /** The parent tile, or null at the root. */
    fun parent(): TileKey? = if (z == 0) null else of(z - 1, x / 2, y / 2)

    override fun toString(): String = "$z/$x/$y"

    companion object {
        /** Deepest level the packing can represent. */
        const val MAX_PACKABLE_LEVEL: Int = 12

        fun of(z: Int, x: Int, y: Int): TileKey {
            require(z in 0..MAX_PACKABLE_LEVEL) { "level $z is outside 0..$MAX_PACKABLE_LEVEL" }
            val span = 1 shl z
            require(x in 0 until span && y in 0 until span) { "tile $z/$x/$y is off the grid" }
            return TileKey((z shl 26) or (x shl 13) or y)
        }
    }
}
