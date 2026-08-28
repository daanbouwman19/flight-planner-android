package com.github.daanbouwman.flightplanner.model

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlin.test.Test

/** An arbitrary fixed instant. Nothing here reads a clock. */
private const val NOW = 1_787_853_060L
private const val MINUTE = 60L

class ObservationAgeTest {

    @Test
    fun `a report from four minutes ago is current`() {
        val age = ObservationAges.of(NOW - 4 * MINUTE, NOW)

        age shouldBe ObservationAge(minutes = 4, freshness = ObservationFreshness.CURRENT)
    }

    @Test
    fun `an hourly station just before its next report is still current`() {
        // The band exists to avoid crying wolf: an hourly station is routinely
        // an hour and a bit old immediately before its next report lands, and
        // flagging that would train the reader to ignore the flag.
        ObservationAges.freshnessOf(74) shouldBe ObservationFreshness.CURRENT
        ObservationAges.freshnessOf(75) shouldBe ObservationFreshness.AGEING
    }

    @Test
    fun `six hours is where late becomes a different day's weather`() {
        ObservationAges.freshnessOf(6 * 60 - 1) shouldBe ObservationFreshness.AGEING
        ObservationAges.freshnessOf(6 * 60) shouldBe ObservationFreshness.STALE
    }

    @Test
    fun `the reported defect — a three-day-old report — is stale`() {
        // `ZUZH 241300Z`, three days old, drew identically to a report from four
        // minutes ago. Defect 3 in docs/WEATHER-PLAN.md.
        val age = ObservationAges.of(NOW - 3 * 24 * 60 * MINUTE, NOW)

        age!!.minutes shouldBe 3 * 24 * 60
        age.freshness shouldBe ObservationFreshness.STALE
    }

    @Test
    fun `an observation with no timestamp has no age, which is not the same as a fresh one`() {
        ObservationAges.of(observationEpochSeconds = null, nowEpochSeconds = NOW).shouldBeNull()
    }

    @Test
    fun `a timestamp in the future clamps to zero rather than going negative`() {
        // A device clock a minute or two off UTC is common and must not render
        // as `-2 min ago`.
        val skewed = ObservationAges.of(NOW + 2 * MINUTE, NOW)

        skewed shouldBe ObservationAge(minutes = 0, freshness = ObservationFreshness.CURRENT)
    }

    @Test
    fun `the extension reads the observation's own timestamp`() {
        val metar = Metar(
            station = "EHAM",
            raw = "METAR EHAM 271855Z 31003KT CAVOK 22/17 Q1008",
            flightRules = FlightRules.VFR,
            observationEpochSeconds = NOW - 90 * MINUTE,
        )

        metar.ageAt(NOW) shouldBe ObservationAge(minutes = 90, freshness = ObservationFreshness.AGEING)
        // A report with no epoch — an AVWX row before its time field lands, or a
        // raw string on its own — has no age at all.
        Metar.fromRaw("EHAM", metar.raw).ageAt(NOW).shouldBeNull()
    }
}
