package com.github.daanbouwman.flightplanner.model

/**
 * Reader and writer for the fleet CSV format.
 *
 * The format is the desktop application's `aircrafts.csv`, unchanged, so a file
 * exported from there imports here and vice versa:
 *
 * ```
 * manufacturer,variant,icao_code,flown,aircraft_range,category,cruise_speed,date_flown,takeoff_distance
 * ATR,42-600,AT46,0,703,Regional,300,,1107
 * ```
 *
 * Columns are addressed by header name, so column order and any extra columns
 * are tolerated.
 */
object FleetCsv {

    private const val MANUFACTURER = "manufacturer"
    private const val VARIANT = "variant"
    private const val ICAO_CODE = "icao_code"
    private const val FLOWN = "flown"
    private const val RANGE = "aircraft_range"
    private const val CATEGORY = "category"
    private const val CRUISE_SPEED = "cruise_speed"
    private const val DATE_FLOWN = "date_flown"
    private const val TAKEOFF_DISTANCE = "takeoff_distance"

    private val HEADER = listOf(
        MANUFACTURER, VARIANT, ICAO_CODE, FLOWN, RANGE,
        CATEGORY, CRUISE_SPEED, DATE_FLOWN, TAKEOFF_DISTANCE,
    )

    data class ParseResult(
        val aircraft: List<AircraftSpec>,
        /** Human-readable problems; parsing skips the offending row and continues. */
        val warnings: List<String>,
    )

    /**
     * Parses fleet CSV text.
     *
     * A malformed row is skipped with a warning rather than aborting the whole
     * import: a user importing a hand-edited file should get the 115 airframes
     * that are fine, plus a note about the one that is not.
     */
    fun parse(text: String): ParseResult {
        val rows = readRows(text)
        if (rows.isEmpty()) return ParseResult(emptyList(), listOf("The file is empty."))

        val header = rows.first().map { it.trim().lowercase() }
        val missing = listOf(MANUFACTURER, VARIANT, RANGE).filterNot { it in header }
        if (missing.isNotEmpty()) {
            return ParseResult(emptyList(), listOf("Missing required column(s): ${missing.joinToString()}"))
        }

        val aircraft = mutableListOf<AircraftSpec>()
        val warnings = mutableListOf<String>()

        for ((lineIndex, row) in rows.drop(1).withIndex()) {
            val lineNumber = lineIndex + 2
            if (row.all { it.isBlank() }) continue

            fun field(name: String): String {
                val index = header.indexOf(name)
                return if (index in row.indices) row[index].trim() else ""
            }

            val manufacturer = field(MANUFACTURER)
            val variant = field(VARIANT)
            if (manufacturer.isEmpty() && variant.isEmpty()) {
                warnings += "Line $lineNumber: no manufacturer or variant; skipped."
                continue
            }

            val range = field(RANGE).toIntOrNull()
            if (range == null || range <= 0) {
                warnings += "Line $lineNumber: '${field(RANGE)}' is not a usable range; skipped."
                continue
            }

            aircraft += AircraftSpec(
                id = 0,
                manufacturer = manufacturer,
                variant = variant,
                icaoCode = field(ICAO_CODE),
                flown = field(FLOWN).let { it == "1" || it.equals("true", ignoreCase = true) },
                rangeNm = range,
                category = field(CATEGORY).ifEmpty { "Uncategorised" },
                cruiseSpeedKt = field(CRUISE_SPEED).toIntOrNull() ?: 0,
                dateFlown = field(DATE_FLOWN).ifEmpty { null },
                takeoffDistanceMeters = field(TAKEOFF_DISTANCE).toIntOrNull(),
            )
        }

        return ParseResult(aircraft, warnings)
    }

    /** Writes the same format, so export round-trips through [parse]. */
    fun write(aircraft: List<AircraftSpec>): String = buildString {
        appendLine(HEADER.joinToString(","))
        for (a in aircraft) {
            appendLine(
                listOf(
                    a.manufacturer,
                    a.variant,
                    a.icaoCode,
                    if (a.flown) "1" else "0",
                    a.rangeNm.toString(),
                    a.category,
                    a.cruiseSpeedKt.toString(),
                    a.dateFlown.orEmpty(),
                    a.takeoffDistanceMeters?.toString().orEmpty(),
                ).joinToString(",") { escape(it) },
            )
        }
    }

    private fun escape(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' }) {
            '"' + value.replace("\"", "\"\"") + '"'
        } else {
            value
        }

    /**
     * Minimal RFC 4180 reader: handles quoted fields, escaped quotes and
     * newlines inside quotes. Aircraft names such as `Beech 18, Twin Beech`
     * really do occur, so naive splitting on commas is not safe.
     */
    internal fun readRows(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0

        fun endField() {
            row.add(field.toString())
            field.setLength(0)
        }

        fun endRow() {
            endField()
            rows.add(row)
            row = mutableListOf()
        }

        while (i < text.length) {
            val c = text[i]
            when {
                inQuotes && c == '"' && i + 1 < text.length && text[i + 1] == '"' -> {
                    field.append('"'); i++
                }
                c == '"' -> inQuotes = !inQuotes
                !inQuotes && c == ',' -> endField()
                !inQuotes && (c == '\n' || c == '\r') -> {
                    // Treat CRLF as a single terminator.
                    if (c == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                    endRow()
                }
                else -> field.append(c)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) endRow()

        return rows.filterNot { it.size == 1 && it[0].isBlank() }
    }
}
