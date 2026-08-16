package com.github.daanbouwman.flightplanner.model

/**
 * A single runway **end**.
 *
 * OurAirports stores one CSV row per runway with a low end and a high end; the
 * ETL splits each into two rows so that every landing direction has its own
 * ident and true heading. This matches the shape of the desktop app's `Runways`
 * table and is what a pilot actually wants to read.
 */
data class Runway(
    val id: Int,
    val airportId: Int,
    /** e.g. "09", "27L", or "RWY" when upstream publishes no ident. */
    val ident: String,
    /** Null when not published and not derivable from [ident]; common on grass strips. */
    val trueHeadingDeg: Double?,
    val lengthFt: Int,
    val widthFt: Int,
    /** The raw upstream surface string, preserved for display. */
    val surface: String,
    val surfaceKind: SurfaceKind,
    /** Threshold position; null upstream for most runways, then falls back to the airport's. */
    val latitude: Double?,
    val longitude: Double?,
    val elevationFt: Int,
    val lighted: Boolean,
)

/**
 * Normalised surface classification.
 *
 * The upstream `surface` column is free text with roughly fifty spellings in
 * common use (`ASP`, `ASPH`, `ASPH-G`, `asphalt`, `PEM`, …), so the raw value is
 * kept for display and this enum is used for filtering.
 */
enum class SurfaceKind {
    HARD,
    GRASS,
    GRAVEL,
    DIRT,
    SAND,
    WATER,
    SNOW_ICE,
    UNKNOWN,
}
