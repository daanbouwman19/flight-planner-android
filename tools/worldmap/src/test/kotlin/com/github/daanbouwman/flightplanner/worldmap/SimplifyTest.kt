package com.github.daanbouwman.flightplanner.worldmap

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlin.test.Test

/**
 * The builder runs once and its output is committed, so nothing on a device ever
 * exercises this code — which is exactly why it is tested here. A ring thinned
 * wrongly does not crash anything; it ships a coastline with a headland missing,
 * and the only place that is visible is a 180 dp card nobody screenshots at 3×.
 */
class SimplifyTest {

    @Test
    fun `collinear points are removed and the ends are kept`() {
        val straight = ring(0.0 to 0.0, 1.0 to 0.0, 2.0 to 0.0, 3.0 to 0.0, 4.0 to 0.0)

        val simplified = Simplify.douglasPeucker(straight, epsilon = 0.05)

        simplified.points() shouldContainExactly listOf(0.0 to 0.0, 4.0 to 0.0)
    }

    @Test
    fun `a deviation larger than the tolerance survives`() {
        val bay = ring(0.0 to 0.0, 1.0 to 0.2, 2.0 to 0.0)

        val simplified = Simplify.douglasPeucker(bay, epsilon = 0.05)

        simplified.points() shouldContainExactly listOf(0.0 to 0.0, 1.0 to 0.2, 2.0 to 0.0)
    }

    @Test
    fun `a deviation smaller than the tolerance is dropped`() {
        val almostStraight = ring(0.0 to 0.0, 1.0 to 0.01, 2.0 to 0.0)

        val simplified = Simplify.douglasPeucker(almostStraight, epsilon = 0.05)

        simplified.points() shouldContainExactly listOf(0.0 to 0.0, 2.0 to 0.0)
    }

    /**
     * A spit running back parallel to the coast is the case that separates
     * measuring against the segment from measuring against the infinite line the
     * segment sits on: against the line, the tip is *on* it and disappears.
     */
    @Test
    fun `a point beyond the end of the chord is measured against the end, not the line`() {
        val spit = ring(0.0 to 0.0, 5.0 to 0.0, 0.0 to 0.0)

        val simplified = Simplify.douglasPeucker(spit, epsilon = 0.05)

        simplified.points() shouldContainExactly listOf(0.0 to 0.0, 5.0 to 0.0, 0.0 to 0.0)
    }

    @Test
    fun `snapping to the grid drops the points it made identical`() {
        val fine = ring(0.0 to 0.0, 0.001 to 0.0, 0.002 to 0.0, 1.0 to 0.0)

        val snapped = Simplify.quantise(fine, snapLon = { kotlin.math.round(it) }, snapLat = { kotlin.math.round(it) })

        snapped.points() shouldContainExactly listOf(0.0 to 0.0, 1.0 to 0.0)
    }

    @Test
    fun `a ring left open by thinning is closed again`() {
        val open = ring(0.0 to 0.0, 1.0 to 0.0, 1.0 to 1.0)

        Simplify.closed(open).points() shouldContainExactly
            listOf(0.0 to 0.0, 1.0 to 0.0, 1.0 to 1.0, 0.0 to 0.0)
    }

    @Test
    fun `a ring that is already closed is left alone`() {
        val alreadyClosed = ring(0.0 to 0.0, 1.0 to 0.0, 1.0 to 1.0, 0.0 to 0.0)

        Simplify.closed(alreadyClosed).size shouldBe 4
    }

    @Test
    fun `visibility is judged on the larger span, so a long thin island survives`() {
        val skerry = ring(0.0 to 0.0, 0.1 to 0.0, 0.1 to 0.1, 0.0 to 0.0)
        val barrierIsland = ring(0.0 to 0.0, 2.0 to 0.0, 2.0 to 0.05, 0.0 to 0.0)

        Simplify.isVisible(skerry, minSpanDegrees = 0.75) shouldBe false
        Simplify.isVisible(barrierIsland, minSpanDegrees = 0.75) shouldBe true
    }

    @Test
    fun `assembling rings produces offsets that bound each of them`() {
        val outline = assemble(
            listOf(
                ring(0.0 to 0.0, 1.0 to 0.0, 1.0 to 1.0, 0.0 to 0.0),
                ring(10.0 to 10.0, 12.0 to 10.0, 12.0 to 12.0, 10.0 to 10.0),
            ),
        )

        outline.ringCount shouldBe 2
        outline.ringStart.toList() shouldContainExactly listOf(0, 4, 8)
        outline.lon.toList().take(4) shouldContainExactly listOf(0f, 1f, 1f, 0f)
        outline.ringMinLon.toList() shouldContainExactly listOf(0f, 10f)
    }

    private fun ring(vararg points: Pair<Double, Double>) = Ring(
        lon = DoubleArray(points.size) { points[it].first },
        lat = DoubleArray(points.size) { points[it].second },
    )

    private fun Ring.points(): List<Pair<Double, Double>> = (0 until size).map { lon[it] to lat[it] }
}
