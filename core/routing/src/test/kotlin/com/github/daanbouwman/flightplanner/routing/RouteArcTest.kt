package com.github.daanbouwman.flightplanner.routing

import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeGreaterThanOrEqual
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.doubles.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import kotlin.math.abs
import kotlin.test.Test

/**
 * The arc is asserted as *geography* — degrees on a sphere — rather than as
 * coordinates in a box.
 *
 * It used to be the other way round, because the arc's only consumer was a
 * sparkline that normalised itself. Once a map came under it the projection moved
 * to [MapFrame], and the assertions moved with it: where a point lands on a card
 * is `MapFrameTest`'s question, and whether it is a real place on the shortest
 * path between two airports is this one's.
 */
class RouteArcTest {

    @Test
    fun `one point per sample, in both columns`() {
        val arc = RouteArc.sampleGeographic(52.31, 4.76, 40.64, -73.78, samples = 12)

        arc.size shouldBe 12
        arc.lats.size shouldBe 12
        arc.lons.size shouldBe 12
    }

    @Test
    fun `every sample is a real place`() {
        // A deliberately awkward spread: polar, equatorial, seam-crossing and
        // hemisphere-crossing routes all through one sampler.
        val routes = listOf(
            doubleArrayOf(52.31, 4.76, 40.64, -73.78),
            doubleArrayOf(-33.95, 151.18, 51.15, -0.18),
            doubleArrayOf(64.19, -51.68, 78.25, 15.47),
            doubleArrayOf(-17.75, 177.44, 61.17, -150.00),
            doubleArrayOf(0.0, 0.0, 0.0, 90.0),
        )
        for (route in routes) {
            val arc = RouteArc.sampleGeographic(route[0], route[1], route[2], route[3])
            for (i in 0 until arc.size) {
                arc.lats[i] shouldBeGreaterThanOrEqual -90.0
                arc.lats[i] shouldBeLessThanOrEqual 90.0
                arc.lons[i].isNaN() shouldBe false
            }
        }
    }

    @Test
    fun `a route due east along the equator stays on the equator`() {
        val arc = RouteArc.sampleGeographic(0.0, 0.0, 0.0, 90.0)

        for (i in 0 until arc.size) {
            abs(arc.lats[i]) shouldBeLessThan 1e-9
        }
        for (i in 1 until arc.size) {
            arc.lons[i] shouldBeGreaterThan arc.lons[i - 1]
        }
    }

    @Test
    fun `a great circle bows away from the straight line between its ends`() {
        // Amsterdam to Tokyo passes far north of the rhumb line — over Siberia,
        // not over the Gobi — which is the entire reason the card draws a great
        // circle rather than two points and a ruler.
        val arc = RouteArc.sampleGeographic(52.31, 4.76, 35.55, 139.78)
        val midpoint = arc.lats[arc.size / 2]

        midpoint shouldBeGreaterThan (52.31 + 35.55) / 2.0 + 8.0
    }

    @Test
    fun `crossing the antimeridian keeps longitude running the same way`() {
        // Fiji to Anchorage. Left raw, the longitudes jump from +179 to -179 and
        // anything drawing them draws a line back across the entire planet.
        val arc = RouteArc.sampleGeographic(-17.75, 177.44, 61.17, -150.00)

        for (i in 1 until arc.size) {
            arc.lons[i] shouldBeGreaterThan arc.lons[i - 1]
        }
        // Past the seam rather than wrapped back under it.
        arc.lons.last() shouldBeGreaterThan 180.0
    }

    @Test
    fun `identical endpoints collapse to the point rather than dividing by zero`() {
        val arc = RouteArc.sampleGeographic(52.31, 4.76, 52.31, 4.76)

        for (i in 0 until arc.size) {
            abs(arc.lats[i] - 52.31) shouldBeLessThan 1e-9
            abs(arc.lons[i] - 4.76) shouldBeLessThan 1e-9
        }
    }

    @Test
    fun `antipodal endpoints still produce a drawable path`() {
        val arc = RouteArc.sampleGeographic(0.0, 0.0, 0.0, 180.0)

        arc.size shouldBe RouteArc.DEFAULT_SAMPLES
        for (i in 0 until arc.size) {
            arc.lats[i].isNaN() shouldBe false
            arc.lons[i].isNaN() shouldBe false
        }
    }

    @Test
    fun `sampled points lie on the great circle between the endpoints`() {
        // This is what distinguishes real spherical interpolation from a
        // decorative curve. For any point on the shortest path,
        // distance-to-departure plus distance-to-destination equals the whole
        // route; a point off the great circle makes that sum larger, and linear
        // interpolation of lat/lon over this route is out by hundreds of miles.
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
        val arc = RouteArc.sampleGeographic(52.31, 4.76, 40.64, -73.78, samples = 6)

        abs(arc.departureLat - 52.31) shouldBeLessThan 1e-9
        abs(arc.departureLon - 4.76) shouldBeLessThan 1e-9
        abs(arc.destinationLat - 40.64) shouldBeLessThan 1e-9
        abs(arc.destinationLon - (-73.78)) shouldBeLessThan 1e-9
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
