package com.github.daanbouwman.flightplanner.core.database.airport

import androidx.room.ColumnInfo
import com.github.daanbouwman.flightplanner.model.Airport
import com.github.daanbouwman.flightplanner.model.AirportSizeClass
import com.github.daanbouwman.flightplanner.model.Runway
import com.github.daanbouwman.flightplanner.model.SurfaceKind

/**
 * Everything needed to render one airport, and nothing else.
 *
 * The in-memory index deliberately holds no text (see `AirportIndex`), so the
 * display fields have to come back from SQLite. Fetching them costs per
 * *column*, not per byte, which is why this is a projection rather than
 * `AirportEntity`: `code_packed`, `ident`, `scheduled_service` and
 * `has_lighting` are never shown, and every column left out is one fewer
 * cursor fetch per row.
 */
data class AirportDisplayRow(
    @ColumnInfo(name = "id") val id: Int,
    @ColumnInfo(name = "icao") val icao: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "lat") val lat: Double,
    @ColumnInfo(name = "lon") val lon: Double,
    @ColumnInfo(name = "elevation_ft") val elevationFt: Int,
    @ColumnInfo(name = "country") val country: String,
    @ColumnInfo(name = "municipality") val municipality: String?,
    @ColumnInfo(name = "size_class") val sizeClass: Int,
    @ColumnInfo(name = "longest_runway_ft") val longestRunwayFt: Int,
    @ColumnInfo(name = "runway_count") val runwayCount: Int,
    @ColumnInfo(name = "has_hard_surface") val hasHardSurface: Boolean,
    @ColumnInfo(name = "has_icao") val hasIcao: Boolean,
)

/**
 * The three searchable text columns plus the id they belong to.
 *
 * Only read once, when the name index is built on a background scope after the
 * first frame. Nothing on the startup path touches it.
 */
data class AirportNameRow(
    @ColumnInfo(name = "id") val id: Int,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "municipality") val municipality: String?,
    @ColumnInfo(name = "country") val country: String,
)

/** 0 = small, 1 = medium, 2 = large, matching the ETL and `AirportIndex`. */
private val SIZE_CLASSES: Array<AirportSizeClass> = AirportSizeClass.entries.toTypedArray()

private val SURFACE_KINDS: Array<SurfaceKind> = SurfaceKind.entries.toTypedArray()

internal fun sizeClassOfOrdinal(ordinal: Int): AirportSizeClass =
    SIZE_CLASSES.getOrElse(ordinal) { AirportSizeClass.SMALL }

fun AirportDisplayRow.toAirport(): Airport = Airport(
    id = id,
    icao = icao,
    name = name,
    latitude = lat,
    longitude = lon,
    elevationFt = elevationFt,
    country = country,
    municipality = municipality,
    sizeClass = sizeClassOfOrdinal(sizeClass),
    longestRunwayFt = longestRunwayFt,
    runwayCount = runwayCount,
    hasHardSurface = hasHardSurface,
    hasIcaoCode = hasIcao,
)

fun AirportEntity.toAirport(): Airport = Airport(
    id = id,
    icao = icao,
    name = name,
    latitude = lat,
    longitude = lon,
    elevationFt = elevationFt,
    country = country,
    municipality = municipality,
    sizeClass = sizeClassOfOrdinal(sizeClass),
    longestRunwayFt = longestRunwayFt,
    runwayCount = runwayCount,
    hasHardSurface = hasHardSurface,
    hasIcaoCode = hasIcao,
)

fun RunwayEntity.toRunway(): Runway = Runway(
    id = id,
    airportId = airportId,
    ident = ident,
    trueHeadingDeg = trueHeading,
    lengthFt = lengthFt,
    widthFt = widthFt,
    surface = surface,
    surfaceKind = SURFACE_KINDS.getOrElse(surfaceKind) { SurfaceKind.UNKNOWN },
    latitude = lat,
    longitude = lon,
    elevationFt = elevationFt,
    lighted = lighted,
)
