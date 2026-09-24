package com.github.daanbouwman.flightplanner.wear.tile

import com.github.daanbouwman.flightplanner.model.AircraftSpec
import com.github.daanbouwman.flightplanner.routing.AirportIndex
import com.github.daanbouwman.flightplanner.wear.route.WatchRouteFeed
import com.github.daanbouwman.flightplanner.wear.route.testAircraft
import com.github.daanbouwman.flightplanner.wear.route.testIndex
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import java.time.LocalDate
import kotlin.test.Test

/**
 * The tile's source: one route per date, the feed's own challenge, remembered
 * for the day and never remembered as a failure.
 */
class WatchChallengeSourceTest {

    private val dispatcher = StandardTestDispatcher()

    private val testFleet = listOf(
        testAircraft(id = 1),
        testAircraft(id = 2, manufacturer = "Boeing", variant = "737-800", typeCode = "B738", cruiseKt = 450),
    )

    private val day = LocalDate.of(2026, 9, 24)

    private fun source(
        index: suspend () -> AirportIndex = { testIndex },
        fleet: suspend () -> List<AircraftSpec> = { testFleet },
    ) = WatchChallengeSource(loadIndex = index, loadFleet = fleet, generatorDispatcher = dispatcher)

    /**
     * The tile and the face tapped from it must agree, or the tap opens on a
     * different route from the one the tile showed.
     */
    @Test
    fun `is the feed's own challenge for the day`() = runTest(dispatcher) {
        val ready = source().forDate(day).shouldBeInstanceOf<TileChallenge.Ready>()
        ready.date shouldBe day
        val expected = WatchRouteFeed(testIndex, testFleet, dispatcher).challengeFor(day)
        // By key and figures rather than whole card: `GeoArc` holds arrays and is
        // not a data class, so two cards with identical arcs are never `equals`.
        ready.card.key() shouldBe expected?.key()
        ready.card.distanceText shouldBe expected?.distanceText
    }

    @Test
    fun `a day's challenge is read once`() = runTest(dispatcher) {
        var reads = 0
        val source = source(index = { reads++; testIndex })

        val first = source.forDate(day)
        val second = source.forDate(day)

        second shouldBe first
        reads shouldBe 1
    }

    @Test
    fun `a new day is computed afresh`() = runTest(dispatcher) {
        var reads = 0
        val source = source(index = { reads++; testIndex })

        source.forDate(day)
        source.forDate(day.plusDays(1)).shouldBeInstanceOf<TileChallenge.Ready>().date shouldBe day.plusDays(1)

        reads shouldBe 2
    }

    /** A transient failure must not blank the tile for the rest of the day. */
    @Test
    fun `a failed read is not remembered`() = runTest(dispatcher) {
        var failing = true
        val source = source(index = { check(!failing) { "asset truncated" }; testIndex })

        source.forDate(day) shouldBe TileChallenge.Unavailable
        failing = false
        source.forDate(day).shouldBeInstanceOf<TileChallenge.Ready>()
    }

    @Test
    fun `an empty fleet is unavailable`() = runTest(dispatcher) {
        source(fleet = { emptyList() }).forDate(day) shouldBe TileChallenge.Unavailable
    }
}
