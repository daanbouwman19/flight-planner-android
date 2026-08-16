package com.github.daanbouwman.flightplanner.model

/**
 * An airframe in the user's fleet.
 *
 * Field-for-field equivalent to the desktop app's `aircraft` table, so an
 * existing `aircrafts.csv` imports without conversion. Note the asymmetry
 * inherited from that format and kept deliberately: [rangeNm] is in nautical
 * miles but [takeoffDistanceMeters] is in metres.
 */
data class AircraftSpec(
    val id: Int,
    val manufacturer: String,
    val variant: String,
    /** Aircraft type code, e.g. "B738". Not an airport code. */
    val icaoCode: String,
    val flown: Boolean,
    val rangeNm: Int,
    val category: String,
    val cruiseSpeedKt: Int,
    /** ISO-8601 date, or null when never flown. */
    val dateFlown: String?,
    val takeoffDistanceMeters: Int?,
    /** True for airframes the user added, as opposed to the bundled seed fleet. */
    val isCustom: Boolean = false,
) {
    /** "Boeing 737-800" — matches the desktop app's `aircraft_info` string. */
    val displayName: String get() = "$manufacturer $variant"

    /** Minimum runway length this airframe needs, in feet. */
    val requiredRunwayFt: Int get() = Units.takeoffDistanceToRunwayFeet(takeoffDistanceMeters)
}
