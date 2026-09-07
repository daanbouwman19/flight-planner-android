package com.github.daanbouwman.flightplanner.feature.globe.tile

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * The packing: 5 bits of level and 24 of each axis in one `Long`, good to z24.
 * It was 13 bits per axis in an `Int`, good to z12, and `of` runs on the render
 * thread once per visited node — so a provider deeper than the packing was an
 * exception per frame rather than detail.
 */
class TileKeyTest {

    @Test
    fun `round-trips the corner tiles of every level the packing claims`() {
        for (z in listOf(0, 8, 18, TileKey.MAX_PACKABLE_LEVEL)) {
            val last = (1 shl z) - 1
            for ((x, y) in listOf(0 to 0, last to 0, 0 to last, last to last)) {
                val key = TileKey.of(z, x, y)
                key.z shouldBe z
                key.x shouldBe x
                key.y shouldBe y
                key shouldBe TileKey.of(z, x, y)
            }
        }
    }

    @Test
    fun `the axes do not bleed into each other or into the level`() {
        val last = (1 shl TileKey.MAX_PACKABLE_LEVEL) - 1
        val xOnly = TileKey.of(TileKey.MAX_PACKABLE_LEVEL, last, 0)
        val yOnly = TileKey.of(TileKey.MAX_PACKABLE_LEVEL, 0, last)
        xOnly shouldNotBe yOnly
        xOnly.y shouldBe 0
        yOnly.x shouldBe 0
        // Every axis bit set, directly under the level field, leaves the level intact.
        TileKey.of(TileKey.MAX_PACKABLE_LEVEL, last, last).z shouldBe TileKey.MAX_PACKABLE_LEVEL
        // The same numeric coordinates at different levels are different tiles.
        TileKey.of(1, 0, 0) shouldNotBe TileKey.of(0, 0, 0)
    }

    @Test
    fun `parent halves the coordinates and the root has none`() {
        TileKey.of(18, 131_071, 200_000).parent() shouldBe TileKey.of(17, 65_535, 100_000)
        TileKey.of(1, 1, 1).parent() shouldBe TileKey.of(0, 0, 0)
        assertNull(TileKey.of(0, 0, 0).parent())
    }

    @Test
    fun `off-grid coordinates and out-of-range levels are rejected`() {
        assertFailsWith<IllegalArgumentException> { TileKey.of(3, 8, 0) }
        assertFailsWith<IllegalArgumentException> { TileKey.of(3, 0, 8) }
        assertFailsWith<IllegalArgumentException> { TileKey.of(3, -1, 0) }
        assertFailsWith<IllegalArgumentException> { TileKey.of(0, 1, 0) }
        assertFailsWith<IllegalArgumentException> { TileKey.of(TileKey.MAX_PACKABLE_LEVEL + 1, 0, 0) }
        assertFailsWith<IllegalArgumentException> { TileKey.of(-1, 0, 0) }
    }

    @Test
    fun `reads as z slash x slash y`() {
        TileKey.of(5, 3, 7).toString() shouldBe "5/3/7"
    }
}
