package com.github.daanbouwman.flightplanner.routing

import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.ints.shouldBeLessThan
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

    /**
     * The whole point of sorting bands by longitude is that the window query
     * skips airports a latitude-only scan would visit — so it is exactly the
     * query that could start *missing* airports. Brute force is the oracle.
     */
    @Test
    fun `a longitude window returns a superset of the exact matches`() {
        val index = randomWorld(3_000, seed = 46)
        val random = Random(101)

        repeat(2_000) {
            val centreLat = random.nextDouble(-89.0, 89.0)
            val centreLon = random.nextDouble(-180.0, 180.0)
            val latHalfWidth = random.nextDouble(0.1, 20.0)
            val lonHalfWidth = random.nextDouble(0.1, 200.0)
            val min = centreLat - latHalfWidth
            val max = centreLat + latHalfWidth

            val visited = mutableSetOf<Int>()
            index.bands.forEachInWindow(min, max, centreLon, lonHalfWidth) { visited += it }

            val expected = (0 until index.size).filter {
                index.latDeg[it] in min..max &&
                    longitudeWithin(index.lonDeg[it], centreLon, lonHalfWidth)
            }
            visited shouldContainAll expected
        }
    }

    @Test
    fun `a window spanning the antimeridian finds both sides of it`() {
        val index = buildIndex(
            listOf(
                TestAirport("EAST", 0.0, 179.0, 5_000),
                TestAirport("WEST", 0.0, -179.0, 5_000),
                TestAirport("AWAY", 0.0, 0.0, 5_000),
            ),
        )

        val visited = mutableSetOf<Int>()
        index.bands.forEachInWindow(-1.0, 1.0, centreLon = 180.0, lonHalfWidth = 3.0) { visited += it }

        visited shouldContainAll listOf(index.slotOf("EAST"), index.slotOf("WEST"))
        visited.contains(index.slotOf("AWAY")) shouldBe false

        // The mirrored window, expressed from the other side of the seam.
        val mirrored = mutableSetOf<Int>()
        index.bands.forEachInWindow(-1.0, 1.0, centreLon = -180.0, lonHalfWidth = 3.0) { mirrored += it }
        mirrored shouldContainAll listOf(index.slotOf("EAST"), index.slotOf("WEST"))
    }

    @Test
    fun `a full-width window degenerates to the latitude scan`() {
        val index = randomWorld(500, seed = 47)
        val visited = mutableSetOf<Int>()
        index.bands.forEachInWindow(-90.0, 90.0, centreLon = 0.0, lonHalfWidth = 180.0) { visited += it }
        visited.size shouldBe index.size
    }

    /**
     * The narrow window has to be genuinely selective, not merely correct: a
     * superset check alone would pass an implementation that ignored longitude.
     */
    @Test
    fun `a narrow window visits far fewer slots than the latitude band holds`() {
        val index = randomWorld(20_000, seed = 48)

        var windowVisits = 0
        index.bands.forEachInWindow(39.0, 41.0, centreLon = 0.0, lonHalfWidth = 2.0) { windowVisits++ }

        var bandVisits = 0
        index.bands.forEachInLatRange(39.0, 41.0) { bandVisits++ }

        // 4° of 360° is about 1%; allow generous slack for band granularity.
        (windowVisits * 10) shouldBeLessThan bandVisits
    }
}
