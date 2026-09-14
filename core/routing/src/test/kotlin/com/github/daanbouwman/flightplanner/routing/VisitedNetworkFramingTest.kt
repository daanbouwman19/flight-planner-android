package com.github.daanbouwman.flightplanner.routing

import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import kotlin.math.abs
import kotlin.test.Test

/**
 * The visited network's frame, on the one set of airports that breaks a naive
 * fit: fields either side of the antimeridian.
 *
 * Fiji and Samoa are 800 NM apart. Fitting a frame to their raw longitudes —
 * +177° and −172° — framed 349° of the planet and drew the leg between them as
 * two diagonals leaving opposite edges of the card. Every assertion here is
 * written against what a reader sees on that card: a regional window, both
 * fields inside it, and one continuous leg.
 */
class VisitedNetworkFramingTest {

    // Nadi, Faleolo, Pago Pago: the antimeridian runs between the first and the
    // other two. Auckland is south-west of all of them and well clear of it.
    private val nadi = -17.75 to 177.44
    private val faleolo = -13.83 to -172.01
    private val pagoPago = -14.33 to -170.71
    private val auckland = -37.01 to 174.79

    private val amsterdam = 52.31 to 4.76
    private val london = 51.48 to -0.46
    private val riga = 56.92 to 23.97

    @Test
    fun `a network either side of the antimeridian spans ten degrees, not three hundred and fifty`() {
        val lons = NetworkFraming.unwrapLongitudes(lonsOf(nadi, faleolo, pagoPago))

        (lons.max() - lons.min()) shouldBeLessThan 12.0
    }

    @Test
    fun `the frame fitted to it is a regional window with every field inside`() {
        val fields = listOf(nadi, faleolo, pagoPago, auckland)
        val lons = NetworkFraming.unwrapLongitudes(lonsOf(*fields.toTypedArray()))
        val frame = MapFrame.forRoute(latsOf(*fields.toTypedArray()), lons, aspect = 2.0)

        // Nadi to Pago Pago is 13° of longitude; with Auckland it is still the
        // South Pacific, not the hemisphere.
        frame.spanLon shouldBeLessThan 90.0
        for (i in fields.indices) {
            val x = frame.x(lons[i]).toDouble()
            val y = frame.y(fields[i].first).toDouble()
            x shouldBeGreaterThan 0.0
            x shouldBeLessThan 1.0
            y shouldBeGreaterThan 0.0
            y shouldBeLessThan 1.0
        }
    }

    /**
     * The arc is sampled and unwrapped relative to its own departure, which the
     * frame may have put a turn away. Drawn in the frame's convention it has to
     * be one continuous line across the card, not two diagonals leaving opposite
     * edges.
     */
    @Test
    fun `a leg across the seam is one continuous line inside the card`() {
        for ((departure, destination) in listOf(nadi to faleolo, faleolo to nadi, pagoPago to nadi)) {
            val fields = listOf(nadi, faleolo, pagoPago)
            val lons = NetworkFraming.unwrapLongitudes(lonsOf(*fields.toTypedArray()))
            val frame = MapFrame.forRoute(latsOf(*fields.toTypedArray()), lons, aspect = 2.0)
            val arc = RouteArc.sampleGeographic(
                departure.first, departure.second, destination.first, destination.second, samples = 32,
            )

            val shifts = NetworkFraming.arcShifts(arc, frame.centreLon)
            shifts.size shouldBe 1

            val shifted = DoubleArray(arc.size) { arc.lons[it] + shifts[0] }
            val projected = frame.project(arc.lats, shifted)
            for (i in 0 until arc.size) {
                projected[i * 2].toDouble() shouldBeGreaterThan 0.0
                projected[i * 2].toDouble() shouldBeLessThan 1.0
            }
            for (i in 1 until arc.size) {
                // Thirty-two samples over 13° of longitude: no step is more than
                // a few percent of the card. A seam crossing is a step of ~1.0.
                abs(projected[i * 2] - projected[(i - 1) * 2]).toDouble() shouldBeLessThan 0.1
            }
        }
    }

    @Test
    fun `the unwrap does not depend on which side of the seam is listed first`() {
        val eastFirst = NetworkFraming.unwrapLongitudes(lonsOf(nadi, faleolo, pagoPago))
        val westFirst = NetworkFraming.unwrapLongitudes(lonsOf(pagoPago, faleolo, nadi))

        eastFirst.sorted() shouldBe westFirst.sorted()
    }

    @Test
    fun `a network nowhere near the seam is left exactly where it is`() {
        val lons = lonsOf(amsterdam, london, riga)

        NetworkFraming.unwrapLongitudes(lons).toList() shouldBe lons.toList()
    }

    @Test
    fun `one field and no fields are handled`() {
        NetworkFraming.unwrapLongitudes(doubleArrayOf(177.44)).toList() shouldBe listOf(177.44)
        NetworkFraming.unwrapLongitudes(DoubleArray(0)).size shouldBe 0
    }

    /**
     * Wider than a hemisphere, a flat map cannot show every leg whole — the
     * globe is the honest drawing there. What it can do is what a wall map does:
     * draw the leg leaving one edge and entering the other, which is the arc at
     * two shifts.
     */
    @Test
    fun `a network wider than a hemisphere draws a seam-crossing leg at both turns`() {
        val lons = NetworkFraming.unwrapLongitudes(doubleArrayOf(0.0, 100.0, -160.0))
        (lons.max() - lons.min()) shouldBe 200.0
        val frame = MapFrame.forRoute(doubleArrayOf(0.0, 0.0, 0.0), lons, aspect = 2.0)

        // From Greenwich to 160°W the short way is westward, across the seam —
        // out of a frame centred on 100°E and back in at the far side.
        val arc = RouteArc.sampleGeographic(0.0, 0.0, 0.0, -160.0, samples = 32)
        val shifts = NetworkFraming.arcShifts(arc, frame.centreLon)

        shifts.size shouldBe 2
        (shifts[0] - shifts[1]).let { abs(it) } shouldBe 360.0
    }

    @Test
    fun `nearestTurn moves by whole turns toward a reference that may itself be unwrapped`() {
        NetworkFraming.nearestTurn(-172.0, 183.0) shouldBe 188.0
        NetworkFraming.nearestTurn(178.0, 183.0) shouldBe 178.0
        NetworkFraming.nearestTurn(4.76, 4.76) shouldBe 4.76
        NetworkFraming.nearestTurn(170.0, -175.0) shouldBe -190.0
    }

    private fun lonsOf(vararg fields: Pair<Double, Double>) = DoubleArray(fields.size) { fields[it].second }

    private fun latsOf(vararg fields: Pair<Double, Double>) = DoubleArray(fields.size) { fields[it].first }
}
