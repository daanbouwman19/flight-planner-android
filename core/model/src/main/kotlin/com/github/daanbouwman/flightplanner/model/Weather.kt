package com.github.daanbouwman.flightplanner.model

/**
 * Flight category derived from ceiling and visibility.
 *
 * The thresholds and wording are ported from the desktop app's
 * `FlightRules::description()` so the two apps say the same thing.
 */
enum class FlightRules(val code: String, val description: String) {
    VFR(
        code = "VFR",
        description = "Visual Flight Rules\n\nCeiling > 3,000 ft AND\nVisibility > 5 statute miles",
    ),
    MVFR(
        code = "MVFR",
        description = "Marginal Visual Flight Rules\n\nCeiling 1,000 to 3,000 ft OR\n" +
            "Visibility 3 to 5 statute miles",
    ),
    IFR(
        code = "IFR",
        description = "Instrument Flight Rules\n\nCeiling 500 to < 1,000 ft OR\n" +
            "Visibility 1 to < 3 statute miles",
    ),
    LIFR(
        code = "LIFR",
        description = "Low Instrument Flight Rules\n\nCeiling < 500 ft OR\n" +
            "Visibility < 1 statute mile",
    ),
    UNKNOWN(code = "N/A", description = "Flight category unknown"),
    ;

    companion object {
        fun fromCode(code: String?): FlightRules = when (code?.trim()?.uppercase()) {
            "VFR" -> VFR
            "MVFR" -> MVFR
            "IFR" -> IFR
            "LIFR" -> LIFR
            else -> UNKNOWN
        }

        /**
         * Derives the category locally, when the provider does not supply one.
         *
         * NOAA sends `fltCat` directly for nearly every station, so this is the
         * fallback — for AVWX, and for the rare NOAA report without it.
         *
         * There is no Rust source function for this: the desktop app's AVWX
         * integration always receives `flight_rules` from the provider, so it
         * never needed to compute one. These thresholds are transcribed from
         * this enum's own [description] text above, not ported from an algorithm.
         *
         * ### Why an unreported input yields [UNKNOWN] rather than the benefit of the doubt
         *
         * The category is the *worse* of what the ceiling says and what the
         * visibility says, so a missing input cannot simply be treated as
         * unlimited. A clear-and-10-miles report and a report that mentions no
         * sky at all are not the same thing: the second could be hiding a 200 ft
         * overcast, and answering VFR for it is exactly the defect this whole
         * redesign exists to remove — it is how an IFR field came to be drawn
         * with a sun on it.
         *
         * So a missing input yields [UNKNOWN], with one exception that is safe by
         * construction: when the input that *is* present already forces [LIFR],
         * nothing the missing one could say would make the answer better, so the
         * answer stands. [Ceiling.Unlimited] is a *reported* fact and is not
         * missing — a clear sky is known, not unknown.
         */
        fun derive(ceiling: Ceiling, visibilityStatuteMiles: Double?): FlightRules {
            val ceilingFt: Int? = when (ceiling) {
                Ceiling.Unknown -> null
                Ceiling.Unlimited -> Int.MAX_VALUE
                is Ceiling.At -> ceiling.ft
            }

            // Neither input says anything.
            if (ceilingFt == null && visibilityStatuteMiles == null) return UNKNOWN

            // One input missing: only a category that cannot be worsened stands.
            if (ceilingFt == null || visibilityStatuteMiles == null) {
                val fromCeiling = ceilingFt?.let { if (it < 500) LIFR else null }
                val fromVisibility = visibilityStatuteMiles?.let { if (it < 1.0) LIFR else null }
                return fromCeiling ?: fromVisibility ?: UNKNOWN
            }

            return when {
                ceilingFt < 500 || visibilityStatuteMiles < 1.0 -> LIFR
                ceilingFt < 1_000 || visibilityStatuteMiles < 3.0 -> IFR
                ceilingFt <= 3_000 || visibilityStatuteMiles <= 5.0 -> MVFR
                else -> VFR
            }
        }
    }
}

/**
 * A decoded METAR observation for one station.
 *
 * ### The sky is a [SkyCover], not a nullable ceiling
 *
 * This type used to carry `ceilingFt: Int?`, where null stood for every one of:
 * a genuinely clear sky, a sky with only scattered layers, an obscuration the
 * parser did not recognise, a provider that decodes no cloud data, and no report
 * at all. Those were then rendered identically — as fine weather. [skyCover]
 * keeps them apart, and [Ceiling] keeps "no ceiling was reported" apart from
 * "there is no ceiling".
 */
data class Metar(
    val station: String,
    val raw: String,
    val flightRules: FlightRules,
    /** The raw `ddhhmmZ` group as reported, for display. Carries no month or year. */
    val observationTime: String? = null,
    /**
     * An unambiguous instant, when a provider supplied one.
     *
     * Epoch seconds rather than a string: NOAA's `reportTime` has at least three
     * live formats and is never parsed, and only `obsTime` is unambiguous.
     */
    val observationEpochSeconds: Long? = null,

    /** The state of the sky. [SkyCover.Unknown] when nothing usable was reported. */
    val skyCover: SkyCover = SkyCover.Unknown,
    /** Decoded present-weather groups, in report order. Empty means none reported. */
    val presentWeather: List<PresentWeather> = emptyList(),

    val windDirectionDeg: Int? = null,
    /** `VRB` — direction genuinely varying, which is not the same as unreported. */
    val windVariable: Boolean = false,
    val windSpeedKt: Int? = null,
    val windGustKt: Int? = null,
    val windRangeFromDeg: Int? = null,
    val windRangeToDeg: Int? = null,

    val visibilityStatuteMiles: Double? = null,
    /** True for `10+SM` or an ICAO `9999` group — "this or better". */
    val visibilityIsOrGreater: Boolean = false,

    val temperatureC: Double? = null,
    val dewpointC: Double? = null,

    val altimeterInHg: Double? = null,
    val altimeterHectopascals: Double? = null,
    /** Which convention the station itself transmitted. See [AltimeterConvention]. */
    val altimeterConvention: AltimeterConvention? = null,
    /** Sea-level pressure, hectopascals. From the remarks, so provider-supplied. */
    val seaLevelPressureHpa: Double? = null,

    /**
     * Precipitation in the last hour, inches.
     *
     * Reported by US stations only. Null means *not reported*, which is most of
     * the world — never "no rain".
     */
    val hourlyPrecipInches: Double? = null,
    val precip3hInches: Double? = null,
    val precip6hInches: Double? = null,
    val precip24hInches: Double? = null,

    /** Lying snow depth, inches. US stations, winter only. Null means not reported. */
    val snowDepthInches: Double? = null,

    /** Station position, from the provider — a METAR carries none. Drives the celestial position. */
    val latitude: Double? = null,
    val longitude: Double? = null,
    /** **Feet.** NOAA sends metres; converted once at the network boundary. */
    val elevationFt: Int? = null,
    val stationName: String? = null,

    val isAutomated: Boolean = false,
    val isSpeci: Boolean = false,
    /** The station's sensors are flagged as needing maintenance — trust the figures less. */
    val maintenanceNeeded: Boolean = false,
) {
    /** The reported ceiling, or the reason there isn't one. */
    val ceiling: Ceiling get() = skyCover.ceiling

    /** The likely surface state. See [GroundCondition] for the measured/inferred boundary. */
    val groundCondition: GroundCondition
        get() = GroundConditions.derive(
            temperatureC = temperatureC,
            dewpointC = dewpointC,
            presentWeather = presentWeather,
            hourlyPrecipInches = hourlyPrecipInches,
            snowDepthInches = snowDepthInches,
        )

    /** True when nothing about the sky was reported — the state that must never look sunny. */
    val skyUnknown: Boolean get() = skyCover is SkyCover.Unknown

    companion object {
        /**
         * An observation with nothing decoded — the honest starting point.
         *
         * Useful as a `copy` base in tests and as the value for a station that
         * returned no usable report at all. Every field says "unknown", and the
         * sky in particular is [SkyCover.Unknown] rather than clear.
         */
        fun unknown(station: String, raw: String = ""): Metar =
            Metar(station = station, raw = raw, flightRules = FlightRules.UNKNOWN)

        /**
         * Builds an observation from raw text alone.
         *
         * Shorthand for [buildMetar] with no provider metadata. The universal
         * path: `raw` is present for every provider and every cache row, so this
         * is what makes an AVWX report and a cache hit as rich as a fresh NOAA
         * one.
         */
        fun fromRaw(station: String, raw: String): Metar =
            buildMetar(station = station, raw = raw, supplement = MetarSupplement.None)
    }
}
