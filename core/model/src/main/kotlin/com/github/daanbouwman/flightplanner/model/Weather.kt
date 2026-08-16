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
         * Derives the category locally.
         *
         * NOAA usually reports `fltCat` directly, but not always — this is the
         * fallback for those reports. The rule is "whichever of ceiling and
         * visibility is worse decides", which is why each branch tests both.
         *
         * @param ceilingFt height of the lowest broken/overcast layer, AGL.
         *   Null means no ceiling was reported, i.e. unlimited.
         * @param visibilityStatuteMiles reported prevailing visibility.
         */
        fun derive(ceilingFt: Int?, visibilityStatuteMiles: Double?): FlightRules {
            if (ceilingFt == null && visibilityStatuteMiles == null) return UNKNOWN

            val ceiling = ceilingFt ?: Int.MAX_VALUE
            val visibility = visibilityStatuteMiles ?: Double.MAX_VALUE

            return when {
                ceiling < 500 || visibility < 1.0 -> LIFR
                ceiling < 1_000 || visibility < 3.0 -> IFR
                ceiling <= 3_000 || visibility <= 5.0 -> MVFR
                else -> VFR
            }
        }
    }
}

/** A decoded METAR observation for one station. */
data class Metar(
    val station: String,
    val raw: String,
    val flightRules: FlightRules,
    /** Raw observation timestamp as reported, for display. */
    val observationTime: String? = null,
    /** Observation time as an ISO-8601 instant, when the provider supplies one. */
    val observationInstant: String? = null,
    val windDirectionDeg: Int? = null,
    val windSpeedKt: Int? = null,
    val windGustKt: Int? = null,
    val visibilityStatuteMiles: Double? = null,
    val ceilingFt: Int? = null,
    val temperatureC: Double? = null,
    val dewpointC: Double? = null,
    val altimeterInHg: Double? = null,
)
