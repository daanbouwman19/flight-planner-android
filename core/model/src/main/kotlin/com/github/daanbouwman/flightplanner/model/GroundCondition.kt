package com.github.daanbouwman.flightplanner.model

/**
 * What the surface at the field is likely to be like.
 *
 * **Read the honesty boundary below before using this.** A METAR does not report
 * the state of the ground. It reports the state of the *air*, plus — on US
 * stations — an hourly precipitation total and a snow depth. Everything here is
 * therefore one of two things, and the distinction matters:
 *
 * - **Measured**: [Snow] when a depth was actually reported, [Wet] when an
 *   hourly precipitation total was actually reported, and any condition derived
 *   from a present-weather group the observer put in the report.
 * - **Inferred**: [Frost], which is a guess. Frost forms according to the
 *   temperature of the *surface*, and a METAR gives air temperature at two
 *   metres. The inference here is deliberately conservative (see
 *   [GroundConditions.derive]) and it is still a guess.
 *
 * Runway contamination proper — the RWYCC/SNOWTAM codes a pilot would actually
 * act on — is **not in this feed at all** and is not modelled here. Nothing in
 * this type should be read as a braking-action report.
 */
sealed interface GroundCondition {

    /** Not enough was reported to say anything. Must not render as a fine dry day. */
    data object Unknown : GroundCondition

    /** Nothing on the surface. */
    data object Dry : GroundCondition

    /** Liquid water — rain or drizzle now, or measurable precipitation in the last hour. */
    data object Wet : GroundCondition

    /**
     * Snow or ice accumulation.
     *
     * [depthInches] is present only when the station actually reported a depth
     * (NOAA's `snow` field, US stations, winter only). Null means frozen
     * precipitation is falling but no depth was measured — still snow on the
     * ground, unknown how much.
     */
    data class Snow(val depthInches: Double? = null) : GroundCondition

    /**
     * An ice glaze from freezing rain or freezing drizzle.
     *
     * Kept apart from [Snow] because they are not the same surface and not the
     * same hazard: snow is loose and can be swept, glaze ice is bonded and is
     * the single worst thing this feed can tell you about a runway.
     */
    data object Icy : GroundCondition

    /**
     * Frost deposition — **inferred, not measured.** See the type KDoc.
     */
    data object Frost : GroundCondition
}

/** Derivation of [GroundCondition] from what a report actually contains. */
object GroundConditions {

    /**
     * Air temperature at or below this is "below freezing" for surface purposes.
     *
     * Zero, with no fudge factor. A fudge would be inventing precision this feed
     * does not have.
     */
    const val FREEZING_C: Double = 0.0

    /**
     * How close the dewpoint must be to the temperature before frost is claimed.
     *
     * Frost needs moisture to deposit as well as a cold surface. A field at
     * −20 °C with a −35 °C dewpoint is bone dry and frost-free, and a rule that
     * said "below freezing therefore frosted" would call it frosted. Three
     * degrees is a conservative spread — it under-claims rather than over-claims,
     * which is the right direction for a guess presented next to measurements.
     */
    const val FROST_SPREAD_C: Double = 3.0

    /**
     * The surface state, from the air.
     *
     * Priority order is worst-and-most-specific first, because a report can
     * satisfy several clauses at once and the most consequential one is the one
     * worth showing: freezing rain outranks rain, and lying snow outranks a
     * cold dry surface.
     *
     * @param temperatureC air temperature, °C. Null when not reported.
     * @param dewpointC dewpoint, °C. Null when not reported.
     * @param presentWeather the decoded present-weather groups, possibly empty.
     * @param hourlyPrecipInches precipitation in the last hour, inches. Null when
     *   not reported — which is most non-US stations, not "no rain".
     * @param snowDepthInches lying snow depth, inches. Null when not reported.
     */
    fun derive(
        temperatureC: Double?,
        dewpointC: Double?,
        presentWeather: List<PresentWeather>,
        hourlyPrecipInches: Double? = null,
        snowDepthInches: Double? = null,
    ): GroundCondition {
        // Nothing at all to go on. Distinct from Dry, deliberately.
        if (temperatureC == null &&
            presentWeather.isEmpty() &&
            hourlyPrecipInches == null &&
            snowDepthInches == null
        ) {
            return GroundCondition.Unknown
        }

        // Measured depth wins outright — it is the only direct observation of the
        // surface anywhere in this feed.
        if (snowDepthInches != null && snowDepthInches > 0.0) {
            return GroundCondition.Snow(depthInches = snowDepthInches)
        }

        if (presentWeather.any { it.isFreezingPrecipitation }) return GroundCondition.Icy
        if (presentWeather.any { it.isFrozenPrecipitation }) return GroundCondition.Snow()
        if (presentWeather.any { it.isPrecipitating }) return GroundCondition.Wet

        // Recent rain with none falling now: the surface is still wet. Only
        // reachable where the station reports the hourly total.
        if (hourlyPrecipInches != null && hourlyPrecipInches > 0.0) {
            val freezing = temperatureC != null && temperatureC <= FREEZING_C
            return if (freezing) GroundCondition.Snow() else GroundCondition.Wet
        }

        // The one inference. Requires both a cold surface and moisture to deposit.
        if (temperatureC != null && temperatureC <= FREEZING_C) {
            val nearSaturated = dewpointC != null && dewpointC >= temperatureC - FROST_SPREAD_C
            return if (nearSaturated) GroundCondition.Frost else GroundCondition.Dry
        }

        // Temperature reported and above freezing, nothing falling, nothing
        // measured on the ground.
        if (temperatureC != null) return GroundCondition.Dry

        // Present weather was reported but none of it touches the ground (haze,
        // vicinity showers), and there is no temperature to reason about.
        return GroundCondition.Unknown
    }
}
