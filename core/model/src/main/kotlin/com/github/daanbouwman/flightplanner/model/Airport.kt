package com.github.daanbouwman.flightplanner.model

/**
 * An airport that is usable for route planning.
 *
 * Only airports with at least one open runway of known, non-zero length reach
 * this type — see the ETL in `:tools:airportdb`.
 */
data class Airport(
    val id: Int,
    /** Four-character code. Real ICAO when [hasIcaoCode], otherwise the OurAirports ident. */
    val icao: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val elevationFt: Int,
    /** ISO 3166-1 alpha-2. */
    val country: String,
    val municipality: String?,
    val sizeClass: AirportSizeClass,
    /**
     * Longest open runway in feet, denormalised at build time because it is the
     * hot filter in every route generation.
     */
    val longestRunwayFt: Int,
    val runwayCount: Int,
    val hasHardSurface: Boolean,
    /**
     * False when [icao] came from the OurAirports `ident` column rather than a
     * real ICAO code. Flight planning tools such as SimBrief only accept real
     * ICAO codes, so this drives the "ICAO airports only" preference.
     */
    val hasIcaoCode: Boolean,
) {
    /** "Amsterdam Airport Schiphol (EHAM)" — the desktop app's display format. */
    val displayName: String get() = "$name ($icao)"
}

enum class AirportSizeClass {
    SMALL,
    MEDIUM,
    LARGE,
    ;

    companion object {
        /** Maps the OurAirports `type` column. Anything else is filtered out entirely. */
        fun fromOurAirportsType(type: String): AirportSizeClass? = when (type) {
            "large_airport" -> LARGE
            "medium_airport" -> MEDIUM
            "small_airport" -> SMALL
            else -> null
        }
    }
}
