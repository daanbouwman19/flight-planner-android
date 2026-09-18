package com.github.daanbouwman.flightplanner.widget

import io.kotest.matchers.shouldBe
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test

/** The alarm time is local midnight, whatever the clock offset does that night. Shared by both widgets. */
class MidnightRefreshTest {

    private val amsterdam = ZoneId.of("Europe/Amsterdam")

    private fun at(text: String): Instant = ZonedDateTime.parse(text).toInstant()

    @Test
    fun `the next midnight is the start of the following local day`() {
        val next = MidnightRefresh.nextLocalMidnightMillis(at("2026-09-16T14:30:00+02:00[Europe/Amsterdam]"), amsterdam)
        Instant.ofEpochMilli(next).atZone(amsterdam).toString() shouldBe "2026-09-17T00:00+02:00[Europe/Amsterdam]"
    }

    @Test
    fun `just before midnight it is a minute away, just after it is a day away`() {
        val before = at("2026-09-16T23:59:00+02:00[Europe/Amsterdam]")
        val after = at("2026-09-17T00:01:00+02:00[Europe/Amsterdam]")
        Duration.ofMillis(MidnightRefresh.nextLocalMidnightMillis(before, amsterdam) - before.toEpochMilli()) shouldBe
            Duration.ofMinutes(1)
        Duration.ofMillis(MidnightRefresh.nextLocalMidnightMillis(after, amsterdam) - after.toEpochMilli()) shouldBe
            Duration.ofHours(23).plusMinutes(59)
    }

    @Test
    fun `across the spring clock change the day is twenty-three hours long`() {
        // Europe moves to summer time on the last Sunday of March; in 2026 that is the 29th.
        val eve = at("2026-03-28T12:00:00+01:00[Europe/Amsterdam]")
        val midnightBefore = MidnightRefresh.nextLocalMidnightMillis(eve, amsterdam)
        val midnightAfter = MidnightRefresh.nextLocalMidnightMillis(Instant.ofEpochMilli(midnightBefore), amsterdam)
        Duration.ofMillis(midnightAfter - midnightBefore) shouldBe Duration.ofHours(23)
        Instant.ofEpochMilli(midnightAfter).atZone(amsterdam).toString() shouldBe "2026-03-30T00:00+02:00[Europe/Amsterdam]"
    }

    @Test
    fun `the zone is the widget's, not the machine's`() {
        val now = at("2026-09-16T23:30:00Z[UTC]")
        val tokyo = MidnightRefresh.nextLocalMidnightMillis(now, ZoneId.of("Asia/Tokyo"))
        val utc = MidnightRefresh.nextLocalMidnightMillis(now, ZoneId.of("UTC"))
        // In Tokyo it is already the 17th at 08:30, so midnight is the 18th's.
        Instant.ofEpochMilli(tokyo).atZone(ZoneId.of("Asia/Tokyo")).toLocalDate().toString() shouldBe "2026-09-18"
        Instant.ofEpochMilli(utc).atZone(ZoneId.of("UTC")).toLocalDate().toString() shouldBe "2026-09-17"
    }
}
