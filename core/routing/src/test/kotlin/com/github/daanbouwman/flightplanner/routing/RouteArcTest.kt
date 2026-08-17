package com.github.daanbouwman.flightplanner.routing

import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.floats.shouldBeGreaterThanOrEqual
import io.kotest.matchers.floats.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import kotlin.math.abs
import kotlin.test.Test

/** Longitudes of the sample at [sample], recovered from a normalised path. */
private fun FloatArray.x(sample: Int): Float = this[sample * 2]

private fun FloatArray.y(sample: Int): Float = this[sample * 2 + 1]

private fun FloatArray.sampleCount(): Int = size / 2

class RouteArcTest {

    @Test
    fun `produces two floats per sample`() {
        val path = RouteArc.normalisedPath(52.31, 4.76, 40.64, -73.78, samples = 12)
        path.size shouldBe 24
    }

    @Test
    fun `every point lands inside the unit box`() {
        // A deliberately awkward spread: polar, equatorial, seam-crossing and
        // hemisphere-crossing routes all share one normalisation.
        val routes = listOf(
            doubleArrayOf(52.31, 4.76, 40.64, -73.78),
            doubleArrayOf(-33.95, 151.18, 51.15, -0.18),
            doubleArrayOf(64.19, -51.68, 78.25, 15.47),
            doubleArrayOf(-17.75, 177.44, 61.17, -150.00),
            doubleArrayOf(0.0, 0.0, 0.0, 90.0),
        )
        for (route in routes) {
            val path = RouteArc.normalisedPath(route[0], route[1], route[2], route[3])
            for (i in 0 until path.sampleCount()) {
                path.x(i) shouldBeGreaterThanOrEqual 0f
                path.x(i) shouldBeLessThanOrEqual 1f
                path.y(i) shouldBeGreaterThanOrEqual 0f
                path.y(i) shouldBeLessThanOrEqual 1f
            }
        }
    }

    @Test
    fun `endpoints sit on the boundary of the dominant axis`() {
        // Due east along the equator: longitude dominates, so the two ends are
        // the extremes of x and the whole path is flat.
        val path = RouteArc.normalisedPath(0.0, 0.0, 0.0, 90.0)
        val last = path.sampleCount() - 1
        path.x(0) shouldBe 0f
        path.x(last) shouldBe 1f
        for (i in 0..last) {
            abs(path.y(i) - 0.5f).toDouble() shouldBeLessThan 1e-5
        }
    }

    @Test
    fun `north is up`() {
        // Due north from the equator: the northern end must have the smaller y,
        // because a canvas grows downwards and the projection flips for it.
        val path = RouteArc.normalisedPath(0.0, 10.0, 60.0, 10.0)
        val last = path.sampleCount() - 1
        path.y(last).toDouble() shouldBeLessThan path.y(0).toDouble()
    }

    @Test
    fun `a great circle bows away from the straight line between its ends`() {
        // Amsterdam to Tokyo passes far north of the rhumb line, which is the
        // entire reason a route sparkline is drawn from a great circle rather
        // than from two points and a ruler.
        val path = RouteArc.normalisedPath(52.31, 4.76, 35.55, 139.78)
        val last = path.sampleCount() - 1
        val mid = last / 2

        // y on the chord at the midpoint's x, versus the actual midpoint.
        val t = (path.x(mid) - path.x(0)) / (path.x(last) - path.x(0))
        val chordY = path.y(0) + (path.y(last) - path.y(0)) * t
        // Smaller y is further north.
        (chordY - path.y(mid)).toDouble() shouldBeGreaterThan 0.02
    }

    @Test
    fun `crossing the antimeridian does not wrap the path back across the box`() {
        // Fiji to Anchorage. Unwrapped naively the longitudes jump from +179 to
        // -179 and the polyline draws a full-width horizontal line; with the seam
        // handled, x advances monotonically.
        val path = RouteArc.normalisedPath(-17.75, 177.44, 61.17, -150.00)
        for (i in 1 until path.sampleCount()) {
            path.x(i) shouldBeGreaterThanOrEqual path.x(i - 1)
        }
    }

    @Test
    fun `identical endpoints collapse to the centre rather than dividing by zero`() {
        val path = RouteArc.normalisedPath(52.31, 4.76, 52.31, 4.76)
        for (i in 0 until path.sampleCount()) {
            path.x(i) shouldBe 0.5f
            path.y(i) shouldBe 0.5f
        }
    }

    @Test
    fun `antipodal endpoints still produce a drawable path`() {
        val path = RouteArc.normalisedPath(0.0, 0.0, 0.0, 180.0)
        path.sampleCount() shouldBe RouteArc.DEFAULT_SAMPLES
        for (i in 0 until path.sampleCount()) {
            path.x(i).isNaN() shouldBe false
            path.y(i).isNaN() shouldBe false
        }
    }

    @Test
    fun `sampled points lie on the great circle between the endpoints`() {
        // This is what distinguishes real spherical interpolation from a
        // decorative curve, and it cannot be seen once the path has been squashed
        // into a unit box — hence testing the sampler directly. For any point on
        // the shortest path, distance-to-departure plus distance-to-destination
        // equals the whole route. A point off the great circle makes that sum
        // larger; linear interpolation of lat/lon over this route is out by
        // hundreds of miles.
        val depLat = 52.31
        val depLon = 4.76
        val destLat = -33.95
        val destLon = 151.18
        val total = GreatCircle.distanceNm(depLat, depLon, destLat, destLon)

        val count = 9
        val lats = DoubleArray(count)
        val lons = DoubleArray(count)
        RouteArc.sampleInto(depLat, depLon, destLat, destLon, lats, lons)

        for (i in 0 until count) {
            val toDeparture = GreatCircle.distanceNm(depLat, depLon, lats[i], lons[i])
            val toDestination = GreatCircle.distanceNm(lats[i], lons[i], destLat, destLon)
            // A nautical mile of slack absorbs the Float rounding in distanceNm.
            abs(toDeparture + toDestination - total).toDouble() shouldBeLessThan 2.0
        }
    }

    @Test
    fun `samples are evenly spaced along the arc`() {
        val count = 5
        val lats = DoubleArray(count)
        val lons = DoubleArray(count)
        RouteArc.sampleInto(52.31, 4.76, 40.64, -73.78, lats, lons)

        val legs = (1 until count).map {
            GreatCircle.distanceNm(lats[it - 1], lons[it - 1], lats[it], lons[it])
        }
        val shortest = legs.min()
        val longest = legs.max()
        (longest - shortest).toDouble() shouldBeLessThan 2.0
    }

    @Test
    fun `endpoints are reproduced exactly`() {
        val lats = DoubleArray(6)
        val lons = DoubleArray(6)
        RouteArc.sampleInto(52.31, 4.76, 40.64, -73.78, lats, lons)

        abs(lats.first() - 52.31) shouldBeLessThan 1e-9
        abs(lons.first() - 4.76) shouldBeLessThan 1e-9
        abs(lats.last() - 40.64) shouldBeLessThan 1e-9
        abs(lons.last() - (-73.78)) shouldBeLessThan 1e-9
    }

    @Test
    fun `unwrapping keeps longitudes continuous across the seam`() {
        val lons = doubleArrayOf(178.0, 179.5, -179.0, -177.5)
        RouteArc.unwrapLongitudes(lons)
        lons[0] shouldBe 178.0
        abs(lons[1] - 179.5) shouldBeLessThan 1e-9
        abs(lons[2] - 181.0) shouldBeLessThan 1e-9
        abs(lons[3] - 182.5) shouldBeLessThan 1e-9
    }
}
