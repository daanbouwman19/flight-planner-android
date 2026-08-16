package com.github.daanbouwman.flightplanner.airportdb

import com.github.daanbouwman.flightplanner.model.DatasetMetaKeys
import java.io.File
import kotlin.system.exitProcess

private const val SOURCE_URL = "https://davidmegginson.github.io/ourairports-data/"

/**
 * Builds the shipped airport database from the OurAirports snapshots.
 *
 * Run manually rather than as part of `assemble`:
 *
 *     ./gradlew :tools:airportdb:run
 *
 * The upstream data changes nightly. Wiring a network fetch into the app build
 * would make builds non-reproducible and CI dependent on a third party; instead
 * the snapshots are checked in, and refreshing them is an explicit, reviewable
 * commit.
 */
fun main(args: Array<String>) {
    val opts = Options.parse(args)

    println("Flight Planner — airport database builder")
    println("  tier:     ${opts.tier.cliName} (${opts.tier.description})")
    println("  airports: ${opts.airportsCsv}")
    println("  runways:  ${opts.runwaysCsv}")
    println("  schema:   ${opts.schemaJson}")
    println("  output:   ${opts.output}")
    println()

    val schema = RoomSchema.read(opts.schemaJson)
    println("Room schema version ${schema.version}, identityHash ${schema.identityHash}")

    val report = BuildReport()

    val runways = readRunways(opts.runwaysCsv, report)
    val airports = deduplicateByCode(readAirports(opts.airportsCsv, runways, opts.tier, report), report)

    Writer.write(
        output = opts.output,
        schema = schema,
        airports = airports,
        runwaysByAirport = runways,
        metadata = mapOf(
            DatasetMetaKeys.SOURCE to SOURCE_URL,
            DatasetMetaKeys.UPSTREAM_MODIFIED to opts.upstreamModified,
            DatasetMetaKeys.FILTER_TIER to opts.tier.cliName,
            DatasetMetaKeys.AIRPORT_COUNT to airports.size.toString(),
            DatasetMetaKeys.RUNWAY_COUNT to
                airports.sumOf { a -> runways[a.id]?.sumOf { it.ends.size } ?: 0 }.toString(),
        ),
        report = report,
    )

    println()
    println(report.render())
    println("Wrote %s (%,.1f MiB)".format(opts.output, opts.output.length() / 1024.0 / 1024.0))

    // A dataset that silently collapses is worse than a failed build.
    if (report.airportsWritten < opts.minAirports) {
        System.err.println(
            "FAILED: only ${report.airportsWritten} airports survived filtering, " +
                "expected at least ${opts.minAirports}. The upstream format may have changed.",
        )
        exitProcess(1)
    }
}

/**
 * Reads the upstream `Last-Modified` recorded next to the CSV snapshots, so the
 * shipped database records which snapshot it came from.
 */
private fun readUpstreamModified(dataDir: File): String {
    val file = File(dataDir, "source.json")
    if (!file.isFile) return "unknown"
    return runCatching {
        kotlinx.serialization.json.Json
            .parseToJsonElement(file.readText())
            .let { it as kotlinx.serialization.json.JsonObject }["airports"]
            ?.let { (it as kotlinx.serialization.json.JsonPrimitive).content }
    }.getOrNull() ?: "unknown"
}

data class Options(
    val airportsCsv: File,
    val runwaysCsv: File,
    val schemaJson: File,
    val output: File,
    val tier: FilterTier,
    /**
     * Upstream `Last-Modified`. Note there is deliberately no build timestamp in
     * the metadata: the output must be byte-identical for identical inputs, so a
     * regenerated database shows up as "no change" rather than a spurious diff.
     */
    val upstreamModified: String,
    val minAirports: Int,
) {
    companion object {
        fun parse(args: Array<String>): Options {
            val map = HashMap<String, String>()
            var i = 0
            while (i < args.size) {
                val key = args[i]
                require(key.startsWith("--")) { "Unexpected argument '$key'" }
                require(i + 1 < args.size) { "Missing value for $key" }
                map[key.removePrefix("--")] = args[i + 1]
                i += 2
            }

            val dataDir = File(map["data-dir"] ?: "tools/airportdb/data")
            return Options(
                airportsCsv = File(map["airports"] ?: File(dataDir, "airports.csv.gz").path),
                runwaysCsv = File(map["runways"] ?: File(dataDir, "runways.csv.gz").path),
                schemaJson = File(
                    map["schema"]
                        ?: "core/database/schemas/" +
                        "com.github.daanbouwman.flightplanner.core.database.airport.AirportDatabase/1.json",
                ),
                output = File(map["out"] ?: "app/src/main/assets/databases/airports.db"),
                tier = FilterTier.of(map["tier"] ?: FilterTier.ALNUM.cliName),
                upstreamModified = map["upstream-modified"] ?: readUpstreamModified(dataDir),
                minAirports = map["min-airports"]?.toInt() ?: 5_000,
            )
        }
    }
}
