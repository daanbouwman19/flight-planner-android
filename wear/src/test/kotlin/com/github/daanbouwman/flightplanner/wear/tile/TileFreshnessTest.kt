package com.github.daanbouwman.flightplanner.wear.tile

import io.kotest.matchers.shouldBe
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test

/** The tile stays fresh until the date changes — local midnight, not UTC's. */
class TileFreshnessTest {

    private val amsterdam = ZoneId.of("Europe/Amsterdam")

    private fun at(dateTime: String, zone: ZoneId = amsterdam) =
        ZonedDateTime.of(LocalDateTime.parse(dateTime), zone)

    @Test
    fun `runs to the next local midnight`() {
        millisUntilNextDay(at("2026-09-24T21:30:00")) shouldBe Duration.ofMinutes(150).toMillis()
    }

    @Test
    fun `just after midnight is almost a whole day`() {
        millisUntilNextDay(at("2026-09-24T00:00:01")) shouldBe Duration.ofDays(1).minusSeconds(1).toMillis()
    }

    /**
     * The night the clocks go back is 25 hours long, and the challenge is a
     * property of the date: counting 24 hours would refresh an hour early,
     * still on the old date.
     */
    @Test
    fun `counts the real length of a daylight-saving night`() {
        millisUntilNextDay(at("2026-10-25T00:00:00")) shouldBe Duration.ofHours(25).toMillis()
    }

    @Test
    fun `is floored so a request at midnight does not spin`() {
        millisUntilNextDay(at("2026-09-24T23:59:59.900")) shouldBe 60_000L
    }
}
