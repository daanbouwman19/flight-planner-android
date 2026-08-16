package com.github.daanbouwman.flightplanner.routing

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test

/**
 * The invariants a generated route must satisfy.
 *
 * These are the actual product promise — "every route is flyable by the chosen
 * aircraft" — expressed as assertions, so a refactor of the sampling strategy
 * cannot quietly start emitting routes that are out of range or land on a
 * runway that is too short.
 */
class RouteGeneratorInvariantTest {

    private val world = randomWorld(5_000, seed = 2026)
    private val generator = RouteGenerator(world)

    private fun assertFlyable(routes: List<GeneratedRoute>) {
        for (route in routes) {
            val required = route.aircraft.requiredRunwayFt

            withClue(route) {
                route.departureRunwayFt shouldBeGreaterThanOrEqual required
                route.destinationRunwayFt shouldBeGreaterThanOrEqual required
                route.distanceNm shouldBeLessThanOrEqual route.aircraft.rangeNm
                (route.departureSlot != route.destinationSlot) shouldBe true
            }
        }
    }

    @Test
    fun `every route is within range and lands on a long enough runway`() = runTest {
        val fleet = listOf(
            aircraft(id = 1, rangeNm = 87, takeoffMeters = 200),
            aircraft(id = 2, rangeNm = 703, takeoffMeters = 1_107),
            aircraft(id = 3, rangeNm = 2_935, takeoffMeters = 2_300),
            aircraft(id = 4, rangeNm = 8_900, takeoffMeters = 3_300),
        )
        val routes = generator.generate(
            RouteRequest(RouteMode.AllAircraft, fleet, amount = 500),
            Random(1),
        )
        routes.size shouldBeGreaterThan 0
        assertFlyable(routes)
    }

    @Test
    fun `short range aircraft still get routes`() = runTest {
        // 87 NM is the shortest range in the shipped fleet. It exercises the
        // band-scan path rather than rejection sampling.
        val fleet = listOf(aircraft(id = 1, rangeNm = 87, takeoffMeters = 200))
        val routes = generator.generate(
            RouteRequest(RouteMode.AllAircraft, fleet, amount = 200),
            Random(2),
        )
        routes.size shouldBeGreaterThan 0
        assertFlyable(routes)
        routes.forEach { it.distanceNm shouldBeLessThanOrEqual 87 }
    }

    @Test
    fun `long range aircraft still get routes`() = runTest {
        val fleet = listOf(aircraft(id = 1, rangeNm = 8_900, takeoffMeters = 3_300))
        val routes = generator.generate(
            RouteRequest(RouteMode.AllAircraft, fleet, amount = 200),
            Random(3),
        )
        routes.size shouldBeGreaterThan 0
        assertFlyable(routes)
    }

    @Test
    fun `a locked departure is honoured for every route`() = runTest {
        val icao = world.icaoOf(world.size / 2)
        val fleet = listOf(aircraft(id = 1, rangeNm = 3_000, takeoffMeters = 1_000))
        val routes = generator.generate(
            RouteRequest(RouteMode.AllAircraft, fleet, amount = 100, lockedDepartureIcao = icao),
            Random(4),
        )
        routes.size shouldBeGreaterThan 0
        routes.forEach { world.icaoOf(it.departureSlot) shouldBe icao }
    }

    @Test
    fun `an unknown locked departure yields nothing rather than ignoring the request`() = runTest {
        val fleet = listOf(aircraft(id = 1, rangeNm = 3_000))
        generator.generate(
            RouteRequest(RouteMode.AllAircraft, fleet, lockedDepartureIcao = "ZZZZ"),
            Random(5),
        ).shouldBeEmpty()
    }

    @Test
    fun `not-flown mode only draws from airframes that have not been flown`() = runTest {
        val fleet = listOf(
            aircraft(id = 1, rangeNm = 3_000, flown = true),
            aircraft(id = 2, rangeNm = 3_000, flown = false),
            aircraft(id = 3, rangeNm = 3_000, flown = true),
        )
        val routes = generator.generate(
            RouteRequest(RouteMode.NotFlownOnly, fleet, amount = 50),
            Random(6),
        )
        routes.size shouldBeGreaterThan 0
        routes.forEach { it.aircraft.id shouldBe 2 }
    }

    @Test
    fun `specific mode uses only the chosen airframe`() = runTest {
        val fleet = (1..5).map { aircraft(id = it, rangeNm = 3_000) }
        val routes = generator.generate(
            RouteRequest(RouteMode.Specific(aircraftId = 3), fleet, amount = 50),
            Random(7),
        )
        routes.size shouldBeGreaterThan 0
        routes.forEach { it.aircraft.id shouldBe 3 }
    }

    @Test
    fun `an empty fleet produces nothing instead of throwing`() = runTest {
        generator.generate(RouteRequest(RouteMode.AllAircraft, emptyList()), Random(8)).shouldBeEmpty()
        generator.generate(
            RouteRequest(RouteMode.NotFlownOnly, listOf(aircraft(rangeNm = 500, flown = true))),
            Random(9),
        ).shouldBeEmpty()
    }

    @Test
    fun `an impossible runway requirement produces nothing`() = runTest {
        val fleet = listOf(aircraft(id = 1, rangeNm = 5_000, takeoffMeters = 100_000))
        generator.generate(RouteRequest(RouteMode.AllAircraft, fleet), Random(10)).shouldBeEmpty()
    }

    @Test
    fun `a minimum distance suppresses trivial hops`() = runTest {
        val fleet = listOf(aircraft(id = 1, rangeNm = 3_000, takeoffMeters = 1_000))
        val routes = generator.generate(
            RouteRequest(RouteMode.AllAircraft, fleet, amount = 300, minDistanceNm = 400),
            Random(11),
        )
        routes.size shouldBeGreaterThan 0
        routes.forEach { it.distanceNm shouldBeGreaterThanOrEqual 400 }
    }

    @Test
    fun `icao-only mode never routes through a local-code airport`() = runTest {
        val fleet = listOf(aircraft(id = 1, rangeNm = 4_000, takeoffMeters = 1_000))
        val routes = generator.generate(
            RouteRequest(RouteMode.AllAircraft, fleet, amount = 200, icaoOnly = true),
            Random(12),
        )
        routes.size shouldBeGreaterThan 0
        routes.forEach {
            world.hasIcaoCode(it.departureSlot) shouldBe true
            world.hasIcaoCode(it.destinationSlot) shouldBe true
        }
    }

    @Test
    fun `hard surface mode never routes through a grass strip`() = runTest {
        val fleet = listOf(aircraft(id = 1, rangeNm = 4_000, takeoffMeters = 1_000))
        val routes = generator.generate(
            RouteRequest(RouteMode.AllAircraft, fleet, amount = 200, requireHardSurface = true),
            Random(13),
        )
        routes.size shouldBeGreaterThan 0
        routes.forEach {
            world.hasHardSurface(it.departureSlot) shouldBe true
            world.hasHardSurface(it.destinationSlot) shouldBe true
        }
    }
}

class RouteGeneratorDeterminismTest {

    @Test
    fun `the same seed produces byte-identical routes`() = runTest {
        // Determinism is what makes the daily-challenge widget possible and what
        // makes every other test in this file reproducible.
        val world = randomWorld(2_000, seed = 77)
        val generator = RouteGenerator(world)
        val fleet = (1..6).map { aircraft(id = it, rangeNm = 500 * it, takeoffMeters = 500 * it) }
        val request = RouteRequest(RouteMode.AllAircraft, fleet, amount = 50)

        val first = generator.generate(request, Random(12345))
        val second = generator.generate(request, Random(12345))
        first shouldBe second
    }

    @Test
    fun `different seeds produce different routes`() = runTest {
        val world = randomWorld(2_000, seed = 78)
        val generator = RouteGenerator(world)
        val fleet = listOf(aircraft(id = 1, rangeNm = 3_000, takeoffMeters = 1_000))
        val request = RouteRequest(RouteMode.AllAircraft, fleet, amount = 50)

        val a = generator.generate(request, Random(1))
        val b = generator.generate(request, Random(2))
        (a == b) shouldBe false
    }

    @Test
    fun `a full batch is produced when the world is dense enough`() = runTest {
        val world = randomWorld(5_000, seed = 79)
        val generator = RouteGenerator(world)
        val fleet = listOf(aircraft(id = 1, rangeNm = 5_000, takeoffMeters = 1_000))
        generator.generate(
            RouteRequest(RouteMode.AllAircraft, fleet, amount = DEFAULT_ROUTE_BATCH),
            Random(80),
        ) shouldHaveSize DEFAULT_ROUTE_BATCH
    }
}

/**
 * Regression tests for the two defects in the desktop implementation that this
 * port fixes. Both concern places where the longitude window arithmetic breaks
 * down, and both silently bias results rather than failing.
 */
class RouteGeneratorGeographyTest {

    @Test
    fun `destinations are found across the antimeridian`() = runTest {
        // A departure just west of the date line with its only neighbours just
        // east of it. The desktop version builds a raw [lon-r, lon+r] window,
        // which excludes them entirely.
        val index = buildIndex(
            listOf(
                TestAirport("DEPA", 0.0, 179.5, 10_000),
                TestAirport("EAST", 0.0, -179.5, 10_000),
                TestAirport("EAS2", 0.5, -179.0, 10_000),
            ),
        )
        val generator = RouteGenerator(index)
        // Range 200 NM keeps this on the band-scan path.
        val fleet = listOf(aircraft(id = 1, rangeNm = 200, takeoffMeters = 1_000))

        val routes = generator.generate(
            RouteRequest(RouteMode.AllAircraft, fleet, amount = 60, lockedDepartureIcao = "DEPA"),
            Random(1),
        )

        routes.size shouldBeGreaterThan 0
        routes.forEach {
            (index.icaoOf(it.destinationSlot) in setOf("EAST", "EAS2")) shouldBe true
        }
    }

    @Test
    fun `destinations are found near the pole`() = runTest {
        // Near the pole, one degree of longitude is a few hundred metres, so the
        // half-width the desktop version computes explodes. These three airports
        // are all within a short hop of one another despite 120 degrees of
        // longitude between them.
        val index = buildIndex(
            listOf(
                TestAirport("POLA", 89.5, 0.0, 10_000),
                TestAirport("POLB", 89.6, 120.0, 10_000),
                TestAirport("POLC", 89.4, -120.0, 10_000),
            ),
        )
        val generator = RouteGenerator(index)
        val fleet = listOf(aircraft(id = 1, rangeNm = 100, takeoffMeters = 1_000))

        val routes = generator.generate(
            RouteRequest(RouteMode.AllAircraft, fleet, amount = 60, lockedDepartureIcao = "POLA"),
            Random(2),
        )

        routes.size shouldBeGreaterThan 0
        routes.forEach { it.distanceNm shouldBeLessThanOrEqual 100 }
    }

    @Test
    fun `an isolated airport yields no route rather than an out-of-range one`() = runTest {
        val index = buildIndex(
            listOf(
                TestAirport("LONE", 0.0, 0.0, 10_000),
                TestAirport("FARA", 0.0, 90.0, 10_000),
            ),
        )
        val generator = RouteGenerator(index)
        val fleet = listOf(aircraft(id = 1, rangeNm = 100, takeoffMeters = 1_000))

        generator.generate(
            RouteRequest(RouteMode.AllAircraft, fleet, amount = 20, lockedDepartureIcao = "LONE"),
            Random(3),
        ).shouldBeEmpty()
    }
}

// --- small assertion helpers, kept local to avoid pulling in a matcher DSL ---

private infix fun Int.shouldBeGreaterThanOrEqual(other: Int) {
    if (this < other) throw AssertionError("expected $this >= $other")
}

private infix fun Int.shouldBeLessThanOrEqual(other: Int) {
    if (this > other) throw AssertionError("expected $this <= $other")
}

private fun <T> withClue(clue: Any?, block: () -> T): T =
    try {
        block()
    } catch (e: AssertionError) {
        throw AssertionError("${e.message}\n  context: $clue", e)
    }
