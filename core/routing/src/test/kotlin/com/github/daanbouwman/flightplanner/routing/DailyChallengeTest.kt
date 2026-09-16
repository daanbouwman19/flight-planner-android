package com.github.daanbouwman.flightplanner.routing

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.test.runTest
import java.time.LocalDate
import kotlin.test.Test

/**
 * The daily challenge is a promise that everyone with the same fleet sees the
 * same route on the same day. These pin the three things that promise rests on.
 */
class DailyChallengeTest {

    private val world = randomWorld(3_000, seed = 2026)
    private val generator = RouteGenerator(world)
    private val fleet = (1..5).map { aircraft(id = it, rangeNm = 600 * it, takeoffMeters = 400 * it) }
    private val day = LocalDate.of(2026, 9, 16)

    /** The identity of a challenge: where from, where to, in what. */
    private fun GeneratedRoute.identity() = Triple(departureSlot, destinationSlot, aircraft.id)

    @Test
    fun `the same date yields the same route twice`() = runTest {
        val first = generator.dailyChallenge(fleet, day).shouldNotBeNull()
        val second = generator.dailyChallenge(fleet, day).shouldNotBeNull()
        second shouldBe first
    }

    @Test
    fun `the order the fleet arrives in does not matter`() = runTest {
        val sorted = generator.dailyChallenge(fleet, day).shouldNotBeNull()
        val reversed = generator.dailyChallenge(fleet.reversed(), day).shouldNotBeNull()
        val shuffled = generator.dailyChallenge(fleet.shuffled(kotlin.random.Random(7)), day).shouldNotBeNull()
        reversed.identity() shouldBe sorted.identity()
        shuffled.identity() shouldBe sorted.identity()
    }

    @Test
    fun `marking airframes flown during the day does not change the route`() = runTest {
        val fresh = generator.dailyChallenge(fleet, day).shouldNotBeNull()
        val allFlown = generator.dailyChallenge(fleet.map { it.copy(flown = true) }, day).shouldNotBeNull()
        allFlown.identity() shouldBe fresh.identity()
    }

    @Test
    fun `different days are different challenges`() = runTest {
        val week = (0L until 7L).map { offset ->
            generator.dailyChallenge(fleet, day.plusDays(offset)).shouldNotBeNull().identity()
        }
        week.distinct().size shouldNotBe 1
    }

    @Test
    fun `the seed is the epoch day and stays a Long`() {
        dailyChallengeSeed(LocalDate.of(1970, 1, 1)) shouldBe 0L
        dailyChallengeSeed(day) shouldBe day.toEpochDay()
    }

    @Test
    fun `an empty fleet has no challenge`() = runTest {
        generator.dailyChallenge(emptyList(), day).shouldBeNull()
    }
}
