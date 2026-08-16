package com.github.daanbouwman.flightplanner.routing

import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import kotlin.random.Random
import kotlin.test.Test

/**
 * The band index replaces the desktop application's R-tree, so the bar is that
 * it must never miss an airport a brute-force scan would find. These tests
 * compare it against exactly that.
 */
class LatBandIndexTest {

    @Test
    fun `a latitude window returns a superset of the exact matches`() {
        val index = randomWorld(3_000, seed = 42)
        val random = Random(99)

        repeat(1_000) {
            val centre = random.nextDouble(-89.0, 89.0)
            val halfWidth = random.nextDouble(0.1, 30.0)
            val min = centre - halfWidth
            val max = centre + halfWidth

            val visited = mutableSetOf<Int>()
            index.bands.forEachInLatRange(min, max) { visited += it }

            val expected = (0 until index.size).filter { index.latDeg[it] in min..max }
            // A superset is correct: bands have whole-degree granularity, and the
            // caller applies the exact distance test afterwards.
            visited shouldContainAll expected
        }
    }

    @Test
    fun `every slot is reachable when the whole globe is queried`() {
        val index = randomWorld(1_500, seed = 43)
        val visited = mutableSetOf<Int>()
        index.bands.forEachInLatRange(-90.0, 90.0) { visited += it }
        visited.size shouldBe index.size
    }

    @Test
    fun `slots are visited exactly once`() {
        val index = randomWorld(1_000, seed = 44)
        var visits = 0
        index.bands.forEachInLatRange(-90.0, 90.0) { visits++ }
        visits shouldBe index.size
    }

    @Test
    fun `queries beyond the poles are clamped rather than throwing`() {
        val index = randomWorld(200, seed = 45)
        val visited = mutableSetOf<Int>()
        index.bands.forEachInLatRange(-1_000.0, 1_000.0) { visited += it }
        visited.size shouldBe index.size
    }

    @Test
    fun `polar airports are found`() {
        // The pole is where a naive band calculation goes out of bounds.
        val index = buildIndex(
            listOf(
                TestAirport("NORT", 89.9, 0.0, 5_000),
                TestAirport("SOUT", -89.9, 0.0, 5_000),
                TestAirport("EQUA", 0.0, 0.0, 5_000),
            ),
        )

        val north = mutableSetOf<Int>()
        index.bands.forEachInLatRange(89.0, 90.0) { north += it }
        north shouldContainAll listOf(index.slotOf("NORT"))

        val south = mutableSetOf<Int>()
        index.bands.forEachInLatRange(-90.0, -89.0) { south += it }
        south shouldContainAll listOf(index.slotOf("SOUT"))
    }

    @Test
    fun `an empty band range visits nothing`() {
        val index = buildIndex(listOf(TestAirport("EQUA", 0.0, 0.0, 5_000)))
        var visits = 0
        index.bands.forEachInLatRange(40.0, 45.0) { visits++ }
        visits shouldBe 0
    }
}
