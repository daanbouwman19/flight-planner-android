package com.github.daanbouwman.flightplanner.airportdb

import com.github.doyaaaaaken.kotlincsv.dsl.csvReader
import java.io.File
import java.io.InputStream
import java.util.zip.GZIPInputStream

/**
 * Reads the OurAirports CSV snapshots.
 *
 * Two things about this data are worth stating, because both have bitten people:
 *
 *  1. **The published data dictionary lists the columns in the wrong order.**
 *     The real header has `icao_code` where the documentation claims `gps_code`.
 *     Every field here is therefore addressed by name, never by position.
 *  2. **The `type` value for a closed airport is `closed`, not `closed_airport`**
 *     as the documentation says.
 */
object Source {

    fun openCsv(file: File): InputStream {
        val raw = file.inputStream().buffered()
        return if (file.name.endsWith(".gz")) GZIPInputStream(raw) else raw
    }

    /** Streams rows as name→value maps so column order can never matter. */
    fun <T> readRows(file: File, block: (Sequence<Map<String, String>>) -> T): T =
        openCsv(file).use { stream ->
            csvReader().open(stream) { block(readAllWithHeaderAsSequence()) }
        }
}

/** Which airports are considered to have a usable code. */
enum class FilterTier(val cliName: String, val description: String) {
    /** Only real ICAO codes: what SimBrief and most simulators accept. */
    STRICT("strict", "real icao_code only"),

    /** Adds four-letter alphabetic idents. */
    ALPHA("alpha", "icao_code, or a 4-letter alphabetic ident"),

    /**
     * Adds any four-character alphanumeric ident, which brings in FAA-style
     * local codes and a great deal of general-aviation and bush flying.
     * This is the shipped default; the app filters down to real ICAO codes at
     * runtime via the `has_icao` column and a user preference.
     */
    ALNUM("alnum", "icao_code, or any 4-character alphanumeric ident"),
    ;

    companion object {
        fun of(name: String): FilterTier =
            entries.firstOrNull { it.cliName == name }
                ?: error("Unknown tier '$name'. Expected one of ${entries.joinToString { it.cliName }}")
    }
}

private val ALPHA_IDENT = Regex("^[A-Z]{4}$")
private val ALNUM_IDENT = Regex("^[A-Z0-9]{4}$")

/**
 * Resolves the code an airport is published under.
 *
 * @return the code and whether it is a genuine ICAO code, or null when the
 *   airport does not qualify under [tier].
 */
fun resolveCode(icaoCode: String, ident: String, tier: FilterTier): Pair<String, Boolean>? {
    val icao = icaoCode.trim().uppercase()
    if (icao.isNotEmpty()) return icao to true

    val fallback = ident.trim().uppercase()
    val accepted = when (tier) {
        FilterTier.STRICT -> false
        FilterTier.ALPHA -> ALPHA_IDENT.matches(fallback)
        FilterTier.ALNUM -> ALNUM_IDENT.matches(fallback)
    }
    return if (accepted) fallback to false else null
}
