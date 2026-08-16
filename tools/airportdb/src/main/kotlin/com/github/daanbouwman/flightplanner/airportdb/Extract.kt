package com.github.daanbouwman.flightplanner.airportdb

import com.github.daanbouwman.flightplanner.model.AirportSizeClass
import com.github.daanbouwman.flightplanner.model.SurfaceKind
import com.github.daanbouwman.flightplanner.model.SurfaceKinds
import java.io.File

/** Airport types that are usable for the kind of flying this app plans. */
private val USABLE_TYPES = setOf("large_airport", "medium_airport", "small_airport")

/** Placeholder for a runway whose ident is not published upstream. */
const val UNNAMED_RUNWAY_IDENT = "RWY"

private val LEADING_DIGITS = Regex("^(\\d{1,2})")

private val COMPASS_HEADINGS = mapOf(
    "N" to 0.0, "NNE" to 22.5, "NE" to 45.0, "ENE" to 67.5,
    "E" to 90.0, "ESE" to 112.5, "SE" to 135.0, "SSE" to 157.5,
    "S" to 180.0, "SSW" to 202.5, "SW" to 225.0, "WSW" to 247.5,
    "W" to 270.0, "WNW" to 292.5, "NW" to 315.0, "NNW" to 337.5,
)

/**
 * Derives a true heading from a runway identifier.
 *
 * Handles the two conventions in the upstream data: numeric idents ("09" → 90°,
 * "27L" → 270°) and compass idents ("NE" → 45°), the latter being common on
 * grass strips. Returns null when neither applies.
 */
internal fun headingFromIdent(ident: String): Double? {
    val trimmed = ident.trim().uppercase()
    LEADING_DIGITS.find(trimmed)?.groupValues?.get(1)?.toIntOrNull()?.let {
        if (it in 1..36) return it * 10.0
    }
    return COMPASS_HEADINGS[trimmed.trimEnd('L', 'R', 'C')]
}

private fun Map<String, String>.str(key: String): String = this[key]?.trim().orEmpty()
private fun Map<String, String>.intOrNull(key: String): Int? = str(key).toIntOrNull()
private fun Map<String, String>.doubleOrNull(key: String): Double? = str(key).toDoubleOrNull()
private fun Map<String, String>.flag(key: String): Boolean = str(key) == "1"

/**
 * Reads `runways.csv`, keeping every runway that has a usable length.
 *
 * A missing ident or heading degrades a runway's *presentation*, never its
 * existence: the length is what decides whether an aircraft can use the airport
 * at all, so a runway is only discarded when it is closed or its length is
 * unknown.
 */
fun readRunways(file: File, report: BuildReport): Map<Int, MutableList<RunwayRecord>> {
    val byAirport = HashMap<Int, MutableList<RunwayRecord>>(24_000)

    Source.readRows(file) { rows ->
        for (row in rows) {
            report.runwaysRead++

            val csvId = row.intOrNull("id") ?: run { report.rejectRunway("no id"); continue }
            val airportRef = row.intOrNull("airport_ref")
                ?: run { report.rejectRunway("no airport_ref"); continue }

            if (row.flag("closed")) {
                report.rejectRunway("closed")
                continue
            }
            val lengthFt = row.intOrNull("length_ft")
            if (lengthFt == null || lengthFt <= 0) {
                report.rejectRunway("no usable length_ft")
                continue
            }

            val widthFt = row.intOrNull("width_ft") ?: 0
            val rawSurface = row.str("surface")
            val surfaceKind = SurfaceKinds.classify(rawSurface)
            if (surfaceKind == SurfaceKind.UNKNOWN && rawSurface.isNotEmpty()) {
                report.noteUnmappedSurface(rawSurface)
            }
            val lighted = row.flag("lighted")

            fun end(prefix: String, id: Int, ident: String): RunwayEndRow {
                val heading = row.doubleOrNull("${prefix}_heading_degT") ?: headingFromIdent(ident)
                if (heading == null) report.endsWithoutHeading++
                return RunwayEndRow(
                    id = id,
                    airportId = airportRef,
                    ident = ident,
                    trueHeading = heading,
                    lengthFt = lengthFt,
                    widthFt = widthFt,
                    surface = rawSurface,
                    surfaceKind = surfaceKind.ordinal,
                    lat = row.doubleOrNull("${prefix}_latitude_deg"),
                    lon = row.doubleOrNull("${prefix}_longitude_deg"),
                    elevationFt = row.intOrNull("${prefix}_elevation_ft"),
                    lighted = lighted,
                )
            }

            // Ids derive from the upstream id, so they are stable across rebuilds.
            val leIdent = row.str("le_ident")
            val heIdent = row.str("he_ident")
            val ends = buildList {
                if (leIdent.isNotEmpty()) add(end("le", csvId * 2, leIdent))
                if (heIdent.isNotEmpty()) add(end("he", csvId * 2 + 1, heIdent))
                if (isEmpty()) {
                    // Length is known but neither direction is named. Keep the runway
                    // under a placeholder rather than losing the airport with it.
                    report.unnamedRunways++
                    add(end("le", csvId * 2, UNNAMED_RUNWAY_IDENT))
                }
            }

            byAirport.getOrPut(airportRef) { ArrayList(2) }.add(
                RunwayRecord(
                    airportId = airportRef,
                    lengthFt = lengthFt,
                    isHardSurface = surfaceKind == SurfaceKind.HARD,
                    lighted = lighted,
                    ends = ends,
                ),
            )
        }
    }
    return byAirport
}

/**
 * Reads `airports.csv`, keeping only airports usable for route planning: a
 * usable type, a code acceptable under [tier], valid coordinates, and at least
 * one open runway of known length.
 */
fun readAirports(
    file: File,
    runwaysByAirport: Map<Int, MutableList<RunwayRecord>>,
    tier: FilterTier,
    report: BuildReport,
): List<AirportRow> {
    val out = ArrayList<AirportRow>(30_000)

    Source.readRows(file) { rows ->
        for (row in rows) {
            report.airportsRead++

            val id = row.intOrNull("id") ?: run { report.rejectAirport("no id"); continue }

            val type = row.str("type")
            val sizeClass = if (type in USABLE_TYPES) AirportSizeClass.fromOurAirportsType(type) else null
            if (sizeClass == null) {
                // Counted per type so heliport/closed/seaplane volumes stay visible.
                report.rejectAirport("type=$type")
                continue
            }

            val (code, hasIcao) = resolveCode(row.str("icao_code"), row.str("ident"), tier)
                ?: run { report.rejectAirport("no acceptable code (tier=${tier.cliName})"); continue }

            val lat = row.doubleOrNull("latitude_deg")
            val lon = row.doubleOrNull("longitude_deg")
            if (lat == null || lon == null || lat !in -90.0..90.0 || lon !in -180.0..180.0) {
                report.rejectAirport("missing or out-of-range coordinates")
                continue
            }

            val runways = runwaysByAirport[id]
            if (runways.isNullOrEmpty()) {
                report.rejectAirport("no usable runway")
                continue
            }

            out.add(
                AirportRow(
                    id = id,
                    icao = code,
                    hasIcao = hasIcao,
                    ident = row.str("ident").uppercase(),
                    name = row.str("name").ifEmpty { code },
                    lat = lat,
                    lon = lon,
                    elevationFt = row.intOrNull("elevation_ft") ?: 0,
                    country = row.str("iso_country"),
                    municipality = row.str("municipality").ifEmpty { null },
                    sizeClass = sizeClass.ordinal,
                    scheduledService = row.str("scheduled_service") == "yes",
                    longestRunwayFt = runways.maxOf { it.lengthFt },
                    runwayCount = runways.size,
                    hasHardSurface = runways.any { it.isHardSurface },
                    hasLighting = runways.any { it.lighted },
                ),
            )
        }
    }
    return out
}

/**
 * Resolves airports that ended up sharing a code, keeping the larger airport and
 * then the one with the longer runway — almost always the intended match.
 */
fun deduplicateByCode(airports: List<AirportRow>, report: BuildReport): List<AirportRow> {
    val best = HashMap<String, AirportRow>(airports.size)
    for (airport in airports) {
        val existing = best[airport.icao]
        if (existing == null) {
            best[airport.icao] = airport
            continue
        }
        report.rejectAirport("duplicate code")
        if (compareValuesBy(airport, existing, { it.sizeClass }, { it.longestRunwayFt }) > 0) {
            best[airport.icao] = airport
        }
    }
    return best.values.toList()
}
