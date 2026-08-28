package com.github.daanbouwman.flightplanner.model

/**
 * How current an observation is.
 *
 * A METAR carries no expiry, so this is a reading of its own timestamp against
 * the clock rather than anything the station said. The bands are set by how
 * often a station is obliged to report — see [ObservationAges].
 */
enum class ObservationFreshness {
    /** Within the reporting cycle. The report is what the field is doing now. */
    CURRENT,

    /** A cycle or more has been missed, or the fetch is not getting through. */
    AGEING,

    /** Old enough that it describes a different day's weather. */
    STALE,
}

/**
 * The elapsed time since an observation, and what that elapsed time means.
 *
 * [minutes] is the figure and [freshness] is the reading; both are wanted,
 * because "18 min" answers a different question from "current". A UI that showed
 * only the band would hide a report creeping toward the edge of it, and one that
 * showed only the figure would make every reader do the arithmetic that decides
 * whether to trust it.
 */
data class ObservationAge(val minutes: Long, val freshness: ObservationFreshness)

/**
 * The bands, and where they come from.
 *
 * **This exists because a stale report was being drawn exactly like a current
 * one.** `ZUZH 241300Z` — three days old — rendered identically to a report from
 * four minutes ago: [Metar.observationEpochSeconds] was fetched, cached and
 * displayed nowhere. Of the defects the redesign turned up this is the one with a
 * safety flavour, because every other unknown in the scene at least draws as
 * hatch, while an out-of-date report draws as confident weather.
 *
 * **Cache freshness is a different question and does not cover this.**
 * `DefaultWeatherRepository` re-fetches on a fifteen-minute TTL, so the *row* is
 * always young; a station that has stopped publishing simply returns its last
 * report again, and the cache is perfectly fresh about a three-day-old
 * observation. Only the observation's own timestamp can say.
 */
object ObservationAges {

    /**
     * Beyond this a report has missed its cycle.
     *
     * Routine METARs are issued hourly (US stations, at about H+53) or
     * half-hourly (much of Europe, at H+20 and H+50), and then take a few
     * minutes to disseminate. So a report up to about an hour and a quarter old
     * is the *normal* state of an hourly station immediately before its next one
     * lands, and flagging that would train the reader to ignore the flag.
     */
    const val AgeingAfterMinutes: Long = 75

    /**
     * Beyond this a report describes weather that has had time to change
     * completely.
     *
     * Six hours rather than a round twelve or twenty-four: a field can go from
     * clear to fogged in and back inside a morning, and the point at which a
     * report stops being *late* and starts being *a different day's weather* is
     * nearer the length of a weather system's passage than the length of a day.
     */
    const val StaleAfterMinutes: Long = 6 * 60

    /**
     * The age of an observation taken at [observationEpochSeconds], as of
     * [nowEpochSeconds], or `null` when the observation carries no timestamp.
     *
     * `null` is a third answer and not a zero: a report whose time is unknown
     * cannot be called current, and the caller has to say so rather than let an
     * absent timestamp read as a fresh one. That is the same distinction
     * [SkyCover.Unknown] draws about the sky, for the same reason.
     *
     * **A timestamp in the future clamps to zero rather than going negative.**
     * Two things produce one, and neither is served by arithmetic that renders as
     * `-3 min ago`: a device clock a minute or two off UTC, which is common and
     * harmless, and a corrupt provider field, which this readout is not the place
     * to catch. Clamping makes the first invisible and the second read as
     * *current* — the direction that gives an obviously wrong figure rather than
     * an obviously wrong sign.
     */
    fun of(observationEpochSeconds: Long?, nowEpochSeconds: Long): ObservationAge? {
        if (observationEpochSeconds == null) return null
        val minutes = ((nowEpochSeconds - observationEpochSeconds) / 60L).coerceAtLeast(0L)
        return ObservationAge(minutes = minutes, freshness = freshnessOf(minutes))
    }

    /** Which band [minutes] falls in. See [AgeingAfterMinutes] and [StaleAfterMinutes]. */
    fun freshnessOf(minutes: Long): ObservationFreshness = when {
        minutes >= StaleAfterMinutes -> ObservationFreshness.STALE
        minutes >= AgeingAfterMinutes -> ObservationFreshness.AGEING
        else -> ObservationFreshness.CURRENT
    }
}

/** This observation's age as of [nowEpochSeconds]. See [ObservationAges.of]. */
fun Metar.ageAt(nowEpochSeconds: Long): ObservationAge? =
    ObservationAges.of(observationEpochSeconds, nowEpochSeconds)
