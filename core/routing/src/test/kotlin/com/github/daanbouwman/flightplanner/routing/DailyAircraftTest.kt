package com.github.daanbouwman.flightplanner.routing

import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.time.LocalDate
import kotlin.test.Test

/**
 * "Aircraft of the day" makes the same promise the challenge does — everyone
 * with the same fleet sees the same airframe on the same day — and adds one of
 * its own: marking that airframe flown must not change which airframe it is.
 */
class DailyAircraftTest {

    private val fleet = (1..5).map { aircraft(id = it, rangeNm = 600 * it, takeoffMeters = 400 * it) }
    private val day = LocalDate.of(2026, 9, 16)

    @Test
    fun `the same date yields the same airframe twice`() {
        val first = dailyAircraft(fleet, day).shouldNotBeNull()
        dailyAircraft(fleet, day) shouldBe first
    }

    @Test
    fun `the order the fleet arrives in does not matter`() {
        val sorted = dailyAircraft(fleet, day).shouldNotBeNull()
        dailyAircraft(fleet.reversed(), day).shouldNotBeNull().id shouldBe sorted.id
        dailyAircraft(fleet.shuffled(kotlin.random.Random(7)), day).shouldNotBeNull().id shouldBe sorted.id
    }

    @Test
    fun `marking the day's airframe flown does not change which airframe it is`() {
        val fresh = dailyAircraft(fleet, day).shouldNotBeNull()
        val allFlown = dailyAircraft(fleet.map { it.copy(flown = true) }, day).shouldNotBeNull()
        allFlown.id shouldBe fresh.id
    }

    @Test
    fun `different days are different airframes`() {
        val fortnight = (0L until 14L).map { offset -> dailyAircraft(fleet, day.plusDays(offset)).shouldNotBeNull().id }
        fortnight.distinct().size shouldNotBe 1
    }

    /**
     * Not a fairness proof — it is one sequence — but a mixer that collapsed
     * onto a subset of a five-airframe fleet would fail here, and the obvious
     * way to get that wrong (an unmixed epoch day, whose low bits march) does.
     */
    @Test
    fun `over a year every airframe in the fleet gets a turn`() {
        val year = (0L until 365L).map { offset -> dailyAircraft(fleet, day.plusDays(offset)).shouldNotBeNull().id }
        year.distinct() shouldContainAll fleet.map { it.id }
    }

    @Test
    fun `the seed is a mix of the epoch day, so it is not the challenge's`() {
        // Distinct from `dailyChallengeSeed`, which is the bare epoch day — the
        // two widgets draw from independent sequences on the same date.
        dailyAircraftSeed(day) shouldNotBe dailyChallengeSeed(day)
        // A bijection: no two days share a seed.
        val week = (0L until 7L).map { dailyAircraftSeed(day.plusDays(it)) }
        week.distinct().size shouldBe 7
        dailyAircraftSeed(LocalDate.of(1970, 1, 1)) shouldBe 0L
    }

    @Test
    fun `an empty fleet has no aircraft of the day`() {
        dailyAircraft(emptyList(), day).shouldBeNull()
    }

    @Test
    fun `a one-airframe fleet always yields that airframe`() {
        val only = fleet.take(1)
        (0L until 30L).forEach { offset ->
            dailyAircraft(only, day.plusDays(offset)).shouldNotBeNull().id shouldBe only.single().id
        }
    }
}
