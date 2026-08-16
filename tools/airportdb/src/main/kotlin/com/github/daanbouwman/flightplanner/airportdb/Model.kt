package com.github.daanbouwman.flightplanner.airportdb

/** A single runway end, ready to insert. */
data class RunwayEndRow(
    val id: Int,
    val airportId: Int,
    val ident: String,
    /**
     * Null when the upstream heading is blank and none can be derived from the
     * ident. That is common for grass strips, and dropping such a runway would
     * also discard its length — which is the field route generation depends on.
     */
    val trueHeading: Double?,
    val lengthFt: Int,
    val widthFt: Int,
    val surface: String,
    val surfaceKind: Int,
    val lat: Double?,
    val lon: Double?,
    val elevationFt: Int?,
    val lighted: Boolean,
)

/**
 * One physical runway, i.e. one upstream CSV row, with its landing directions.
 *
 * Keeping the runway and its ends together is what makes `runway_count` correct:
 * counting inserted end rows would double every runway and miscount the ones
 * where only a single end is named.
 */
data class RunwayRecord(
    val airportId: Int,
    val lengthFt: Int,
    val isHardSurface: Boolean,
    val lighted: Boolean,
    val ends: List<RunwayEndRow>,
)

/** An airport, ready to insert. */
data class AirportRow(
    val id: Int,
    val icao: String,
    val hasIcao: Boolean,
    val ident: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    val elevationFt: Int,
    val country: String,
    val municipality: String?,
    val sizeClass: Int,
    val scheduledService: Boolean,
    val longestRunwayFt: Int,
    val runwayCount: Int,
    val hasHardSurface: Boolean,
    val hasLighting: Boolean,
)

/**
 * Every reason a row was dropped, counted.
 *
 * Silent filtering is how a dataset quietly loses a third of its airports
 * without anyone noticing, so the tool prints this table on every run and fails
 * the build if the surviving count collapses.
 */
class BuildReport {
    val airportRejects = linkedMapOf<String, Int>()
    val runwayRejects = linkedMapOf<String, Int>()
    val unmappedSurfaces = hashMapOf<String, Int>()

    var airportsRead = 0
    var runwaysRead = 0
    var airportsWritten = 0
    var runwayEndsWritten = 0
    var airportsWithIcao = 0
    var endsWithoutHeading = 0
    var unnamedRunways = 0

    fun rejectAirport(reason: String) {
        airportRejects[reason] = (airportRejects[reason] ?: 0) + 1
    }

    fun rejectRunway(reason: String) {
        runwayRejects[reason] = (runwayRejects[reason] ?: 0) + 1
    }

    fun noteUnmappedSurface(raw: String) {
        unmappedSurfaces[raw] = (unmappedSurfaces[raw] ?: 0) + 1
    }

    fun render(): String = buildString {
        appendLine("Airports read:        %,d".format(airportsRead))
        appendLine("Runway rows read:     %,d".format(runwaysRead))
        appendLine()
        appendLine("Airport rejects:")
        airportRejects.entries.sortedByDescending { it.value }.forEach {
            appendLine("  %-46s %,7d".format(it.key, it.value))
        }
        appendLine()
        appendLine("Runway rejects:")
        if (runwayRejects.isEmpty()) {
            appendLine("  (none)")
        } else {
            runwayRejects.entries.sortedByDescending { it.value }.forEach {
                appendLine("  %-46s %,7d".format(it.key, it.value))
            }
        }
        appendLine()
        appendLine("Retained but incomplete:")
        appendLine("  %-46s %,7d".format("runway ends with no known heading", endsWithoutHeading))
        appendLine("  %-46s %,7d".format("runways with no published ident", unnamedRunways))
        if (unmappedSurfaces.isNotEmpty()) {
            appendLine()
            appendLine("Unclassified surface values (top 20 by frequency):")
            unmappedSurfaces.entries.sortedByDescending { it.value }.take(20).forEach {
                appendLine("  %-46s %,7d".format("'${it.key}'", it.value))
            }
        }
        appendLine()
        appendLine(
            "Airports written:     %,d  (of which %,d with a real ICAO code)"
                .format(airportsWritten, airportsWithIcao),
        )
        appendLine("Runway ends written:  %,d".format(runwayEndsWritten))
    }
}
