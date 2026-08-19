package com.github.daanbouwman.flightplanner.routing

import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.floats.shouldBeLessThan
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeInRange
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.shouldBe
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test

class GreatCircleDistanceTest {

    @Test
    fun `matches an independent double precision reference`() {
        // The port trades Double for Float deliberately; this pins how much
        // accuracy that actually costs, rather than assuming it is negligible.
        val random = Random(20260816)
        var worst = 0.0
        repeat(20_000) {
            val lat1 = random.nextDouble(-90.0, 90.0)
            val lon1 = random.nextDouble(-180.0, 180.0)
            val lat2 = random.nextDouble(-90.0, 90.0)
            val lon2 = random.nextDouble(-180.0, 180.0)

            val ported = GreatCircle.distanceNm(lat1, lon1, lat2, lon2).toDouble()
            val reference = referenceDistanceNm(lat1, lon1, lat2, lon2)
            worst = maxOf(worst, abs(ported - reference))
        }
        // Sub-2 NM over the whole globe: far finer than any flight-planning use.
        worst shouldBeLessThan 2.0
    }

    @Test
    fun `known city pairs land where an atlas says they should`() {
        // Schiphol to JFK: about 3,160 NM.
        GreatCircle.distanceNm(52.3086, 4.7639, 40.6394, -73.7793) shouldBeInRange 3_100..3_220
        // Heathrow to JFK: about 3,000 NM.
        GreatCircle.distanceNm(51.4706, -0.4619, 40.6394, -73.7793) shouldBeInRange 2_950..3_060
        // Auckland to Santiago, a long trans-Pacific leg crossing the antimeridian.
        GreatCircle.distanceNm(-37.0120, 174.7863, -33.3930, -70.7858) shouldBeInRange 5_100..5_300
    }

    @Test
    fun `distance is zero for coincident points and symmetric otherwise`() {
        GreatCircle.distanceNm(52.0, 4.0, 52.0, 4.0) shouldBe 0
        val a = GreatCircle.distanceNm(52.0, 4.0, -33.0, 151.0)
        val b = GreatCircle.distanceNm(-33.0, 151.0, 52.0, 4.0)
        a shouldBe b
    }

    @Test
    fun `antipodal points are half the circumference apart`() {
        // Roughly 10,807 NM. A formula that wraps incorrectly fails here loudly.
        GreatCircle.distanceNm(0.0, 0.0, 0.0, 180.0) shouldBeInRange 10_700..10_900
        GreatCircle.distanceNm(90.0, 0.0, -90.0, 0.0) shouldBeInRange 10_700..10_900
    }

    @Test
    fun `crossing the antimeridian is a short hop, not a trip round the world`() {
        // Two points either side of the date line, one degree apart.
        val distance = GreatCircle.distanceNm(0.0, 179.5, 0.0, -179.5)
        distance shouldBeInRange 55..65
    }
}

class HaversineThresholdTest {

    @Test
    fun `threshold agrees with the distance function it stands in for`() {
        // This is the invariant the whole hot loop rests on: comparing the
        // pre-computed threshold must give the same answer as measuring.
        val random = Random(7)
        val index = randomWorld(400, seed = 11)

        for (maxDistance in listOf(100, 500, 1_500, 3_000, 8_900)) {
            val threshold = GreatCircle.thresholdFor(maxDistance)
            repeat(5_000) {
                val a = random.nextInt(index.size)
                val b = random.nextInt(index.size)
                val within = GreatCircle.withinThreshold(index, a, b, threshold)
                val distance = GreatCircle.distanceNm(index, a, b)

                // Disagreement is only tolerable within a nautical mile of the
                // boundary, where Float rounding legitimately bites.
                if (abs(distance - maxDistance) > 1) {
                    within shouldBe (distance <= maxDistance)
                }
            }
        }
    }

    @Test
    fun `a range beyond half the circumference admits the entire planet`() {
        GreatCircle.thresholdFor(20_000) shouldBe 1.0f
    }

    @Test
    fun `max latitude delta reflects sixty nautical miles per degree`() {
        val oneDegree = GreatCircle.maxLatitudeDeltaRad(60)
        abs(oneDegree - Math.toRadians(1.0).toFloat()) shouldBeLessThan 1e-6f
    }
}

class LongitudeWrapTest {

    @Test
    fun `longitude delta takes the short way round`() {
        longitudeDelta(179.0, -179.0) shouldBe 2.0
        longitudeDelta(-179.0, 179.0) shouldBe -2.0
        longitudeDelta(0.0, 90.0) shouldBe 90.0
    }

    @Test
    fun `longitude window spans the date line`() {
        // The desktop implementation builds a raw [lon-r, lon+r] interval, which
        // is empty on one side here and silently skews destinations near Fiji or
        // western Alaska.
        longitudeWithin(lon = -179.0, centre = 179.0, halfWidth = 5.0) shouldBe true
        longitudeWithin(lon = 179.0, centre = -179.0, halfWidth = 5.0) shouldBe true
        longitudeWithin(lon = 170.0, centre = 179.0, halfWidth = 5.0) shouldBe false
    }

    @Test
    fun `a half width of 180 degrees or more accepts everything`() {
        longitudeWithin(lon = -12.0, centre = 179.0, halfWidth = 180.0) shouldBe true
    }
}

class FlightTimeTest {

    @Test
    fun `converts distance and cruise speed into hours and minutes`() {
        GreatCircle.flightTime(300.0, 300) shouldBe FlightTime(1, 0, 300.0)
        GreatCircle.flightTime(450.0, 300) shouldBe FlightTime(1, 30, 300.0)
    }

    @Test
    fun `falls back to 300 knots when an airframe records no cruise speed`() {
        val time = GreatCircle.flightTime(600.0, 0)
        time.effectiveSpeedKt shouldBe 300.0
        time.hours shouldBe 2
    }

    @Test
    fun `minutes rounding to sixty rolls over into an hour`() {
        // 1.9999 h must read 2h 00m, never 1h 60m.
        val time = GreatCircle.flightTime(599.99, 300)
        time.hours shouldBe 2
        time.minutes shouldBe 0
        time.format() shouldBe "2:00"
    }
}

class InitialBearingTest {

    @Test
    fun `cardinal directions come out as expected`() {
        abs(GreatCircle.initialBearingDeg(0.0, 0.0, 10.0, 0.0) - 0.0) shouldBeLessThan 0.001
        abs(GreatCircle.initialBearingDeg(0.0, 0.0, 0.0, 10.0) - 90.0) shouldBeLessThan 0.001
        abs(GreatCircle.initialBearingDeg(0.0, 0.0, -10.0, 0.0) - 180.0) shouldBeLessThan 0.001
        abs(GreatCircle.initialBearingDeg(0.0, 0.0, 0.0, -10.0) - 270.0) shouldBeLessThan 0.001
    }

    @Test
    fun `bearing stays in range across the date line`() {
        val bearing = GreatCircle.initialBearingDeg(0.0, 179.0, 0.0, -179.0)
        bearing shouldBeGreaterThan 0.0
        bearing shouldBeLessThan 360.0
        abs(bearing - 90.0) shouldBeLessThan 0.001
    }
}

class FinalBearingTest {

    @Test
    fun `a leg along a meridian or the equator arrives on the heading it left on`() {
        // The two cases where the great circle is also a rhumb line, so there is
        // no convergence of the meridians for the heading to follow.
        abs(GreatCircle.finalBearingDeg(0.0, 0.0, 10.0, 0.0) - 0.0) shouldBeLessThan 0.001
        abs(GreatCircle.finalBearingDeg(0.0, 0.0, 0.0, 10.0) - 90.0) shouldBeLessThan 0.001
    }

    @Test
    fun `a high-latitude crossing arrives on a markedly different heading`() {
        // EHAM to KJFK: out over Scotland heading west-north-west, in over Long
        // Island heading south-west. This is the case the second figure exists
        // for — a screen stating one bearing for this leg would be 62° out by the
        // time the aircraft arrived.
        //
        // The two expected values are the spherical-Earth figures, computed from
        // the formula rather than recalled from a chart. A real flight plan's
        // headings also carry magnetic variation, which this app does not model.
        val ehamLat = 52.3086
        val ehamLon = 4.7639
        val kjfkLat = 40.6398
        val kjfkLon = -73.7789

        val initial = GreatCircle.initialBearingDeg(ehamLat, ehamLon, kjfkLat, kjfkLon)
        val final = GreatCircle.finalBearingDeg(ehamLat, ehamLon, kjfkLat, kjfkLon)

        abs(initial - 290.56) shouldBeLessThan 0.01
        abs(final - 228.98) shouldBeLessThan 0.01
    }

    @Test
    fun `the final bearing is the reversed leg's initial bearing, turned around`() {
        val random = Random(20260819)
        repeat(500) {
            val lat1 = random.nextDouble(-85.0, 85.0)
            val lon1 = random.nextDouble(-180.0, 180.0)
            val lat2 = random.nextDouble(-85.0, 85.0)
            val lon2 = random.nextDouble(-180.0, 180.0)

            val final = GreatCircle.finalBearingDeg(lat1, lon1, lat2, lon2)
            val reciprocal = (GreatCircle.initialBearingDeg(lat2, lon2, lat1, lon1) + 180.0) % 360.0

            abs(final - reciprocal) shouldBeLessThan 0.001
        }
    }

    @Test
    fun `the result stays in range across the date line`() {
        val final = GreatCircle.finalBearingDeg(0.0, 179.0, 0.0, -179.0)
        final shouldBeGreaterThan 0.0
        final shouldBeLessThan 360.0
        abs(final - 90.0) shouldBeLessThan 0.001
    }
}

class AirportIndexInvariantTest {

    @Test
    fun `rows are sorted ascending by longest runway`() {
        val index = randomWorld(2_000, seed = 3)
        for (slot in 1 until index.size) {
            (index.longestRunwayFt[slot] >= index.longestRunwayFt[slot - 1]) shouldBe true
        }
    }

    @Test
    fun `binary search finds the exact first qualifying slot`() {
        val index = randomWorld(2_000, seed = 4)
        for (required in listOf(0, 1, 999, 3_000, 7_500, 14_999, 15_000, 99_999)) {
            val first = index.firstSlotWithRunway(required)
            // Everything below is too short...
            for (slot in 0 until first) {
                (index.longestRunwayFt[slot] < required) shouldBe true
            }
            // ...and the slot itself, if any, is long enough.
            if (first < index.size) {
                (index.longestRunwayFt[first] >= required) shouldBe true
            }
        }
    }

    @Test
    fun `an impossible runway requirement yields an empty tail`() {
        val index = randomWorld(500, seed = 5)
        index.firstSlotWithRunway(1_000_000) shouldBe index.size
    }

    @Test
    fun `icao lookup is case insensitive and round-trips`() {
        val index = buildIndex(
            listOf(
                TestAirport("EHAM", 52.31, 4.76, 12_467),
                TestAirport("KJFK", 40.64, -73.78, 14_511),
            ),
        )
        index.slotOf("eham") shouldBe index.slotOf("EHAM")
        index.icaoOf(index.slotOf("EHAM")) shouldBe "EHAM"
        index.slotOf("ZZZZ") shouldBe -1
    }

    @Test
    fun `cached trigonometry agrees with computing it directly`() {
        val index = randomWorld(500, seed = 6)
        var worst = 0
        repeat(2_000) {
            val a = it % index.size
            val b = (it * 7 + 3) % index.size
            val cached = GreatCircle.distanceNm(index, a, b)
            val direct = GreatCircle.distanceNm(
                index.latDeg[a], index.lonDeg[a], index.latDeg[b], index.lonDeg[b],
            )
            worst = maxOf(worst, abs(cached - direct))
        }
        // The cached path uses a different but algebraically equivalent identity.
        worst shouldBeLessThan 3
    }
}
