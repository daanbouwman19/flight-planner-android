package com.github.daanbouwman.flightplanner.airportdb

import com.github.daanbouwman.flightplanner.model.AircraftSpec
import com.github.daanbouwman.flightplanner.model.AirportSizeClass
import com.github.daanbouwman.flightplanner.model.FleetCsv
import com.github.daanbouwman.flightplanner.routing.AirportIndex
import com.github.daanbouwman.flightplanner.routing.AirportIndexBuilder
import com.github.daanbouwman.flightplanner.routing.RouteGenerator
import com.github.daanbouwman.flightplanner.routing.RouteMode
import com.github.daanbouwman.flightplanner.routing.RouteRequest
import java.io.File
import java.sql.DriverManager
import kotlin.random.Random
import kotlin.system.exitProcess

/**
 * Verifies the checked-in airport asset against the current Room schema.
 *
 * This exists because of one specific, severe failure mode: if the generated
 * database's `room_master_table` identity hash drifts from what Room computes
 * for the current entities, Room refuses to open it — on first launch, on every
 * device, with no recovery path. That drift happens silently the moment anyone
 * edits an entity and forgets to regenerate the asset.
 *
 * Wired into `check`, so it fails the build instead of the app.
 */
fun main(args: Array<String>) {
    val opts = Options.parse(args)
    val schema = RoomSchema.read(opts.schemaJson)

    if (!opts.output.isFile) {
        fail("Airport asset missing: ${opts.output}. Run ./gradlew :tools:airportdb:run")
    }

    // The device-side installer decides staleness from this file alone, so a
    // missing or stale sidecar means devices keep an out-of-date database.
    val versionFile = File(opts.output.parentFile, "${opts.output.name}.version")
    if (!versionFile.isFile) {
        fail("Version sidecar missing: $versionFile. Run ./gradlew :tools:airportdb:run")
    }
    if (versionFile.readText().trim() != schema.identityHash) {
        fail(
            "Version sidecar is stale.\n" +
                "  sidecar: ${versionFile.readText().trim()}\n" +
                "  schema : ${schema.identityHash}\n" +
                "  Fix: ./gradlew :tools:airportdb:run",
        )
    }

    val failures = mutableListOf<String>()
    fun check(condition: Boolean, message: String) {
        if (!condition) failures += message
    }

    DriverManager.getConnection("jdbc:sqlite:${opts.output.path}").use { conn ->
        fun <T> queryOne(sql: String, extract: (java.sql.ResultSet) -> T): T? =
            conn.createStatement().use { st ->
                st.executeQuery(sql).use { rs -> if (rs.next()) extract(rs) else null }
            }

        val hash = queryOne("SELECT identity_hash FROM room_master_table WHERE id = 42") { it.getString(1) }
        check(
            hash == schema.identityHash,
            "Room identity hash mismatch.\n" +
                "  asset : $hash\n" +
                "  schema: ${schema.identityHash}\n" +
                "  The database entities changed without the asset being regenerated.\n" +
                "  Fix: ./gradlew :tools:airportdb:run",
        )

        val userVersion = queryOne("PRAGMA user_version") { it.getInt(1) }
        check(
            userVersion == schema.version,
            "user_version is $userVersion but the schema is version ${schema.version}",
        )

        val airportCount = queryOne("SELECT COUNT(*) FROM airports") { it.getInt(1) } ?: 0
        val runwayCount = queryOne("SELECT COUNT(*) FROM runways") { it.getInt(1) } ?: 0
        check(airportCount >= opts.minAirports, "only $airportCount airports, expected >= ${opts.minAirports}")
        check(runwayCount >= airportCount, "only $runwayCount runway ends for $airportCount airports")

        val declaredAirports = queryOne(
            "SELECT value FROM dataset_meta WHERE `key` = 'airport_count'",
        ) { it.getString(1) }?.toIntOrNull()
        check(
            declaredAirports == airportCount,
            "dataset_meta.airport_count is $declaredAirports but the table holds $airportCount",
        )

        val orphans = queryOne(
            "SELECT COUNT(*) FROM runways WHERE airport_id NOT IN (SELECT id FROM airports)",
        ) { it.getInt(1) } ?: 0
        check(orphans == 0, "$orphans runway rows reference a missing airport")

        val zeroLength = queryOne("SELECT COUNT(*) FROM airports WHERE longest_runway_ft <= 0") { it.getInt(1) } ?: 0
        check(zeroLength == 0, "$zeroLength airports have no usable runway length")

        val duplicateCodes = queryOne(
            "SELECT COUNT(*) FROM (SELECT icao FROM airports GROUP BY icao HAVING COUNT(*) > 1)",
        ) { it.getInt(1) } ?: 0
        check(duplicateCodes == 0, "$duplicateCodes codes appear more than once")

        val badCoords = queryOne(
            "SELECT COUNT(*) FROM airports WHERE lat < -90 OR lat > 90 OR lon < -180 OR lon > 180",
        ) { it.getInt(1) } ?: 0
        check(badCoords == 0, "$badCoords airports have out-of-range coordinates")

        failures += smokeTestRouteGeneration(conn, opts)

        if (failures.isEmpty()) {
            println(
                "Airport asset OK: %,d airports, %,d runway ends, identityHash %s"
                    .format(airportCount, runwayCount, schema.identityHash),
            )
        }
    }

    if (failures.isNotEmpty()) {
        System.err.println("Airport asset verification FAILED:")
        failures.forEach { System.err.println("  - $it") }
        exitProcess(1)
    }
}

private fun fail(message: String): Nothing {
    System.err.println(message)
    exitProcess(1)
}

/**
 * Generates real routes from the shipped dataset using the shipped fleet.
 *
 * The structural checks above prove the file is well-formed; this proves it is
 * *useful*. A dataset can satisfy every integrity constraint and still be unable
 * to produce a flyable route for a short-range aircraft — and that failure would
 * otherwise only surface in the user's hands.
 */
private fun smokeTestRouteGeneration(
    conn: java.sql.Connection,
    opts: Options,
): List<String> {
    val failures = mutableListOf<String>()

    val index = loadIndex(conn)
    val fleet = readSeedFleet(opts) ?: run {
        return listOf("Could not read the seed fleet; route generation was not exercised.")
    }

    val generator = RouteGenerator(index)
    val shortest = fleet.minBy { it.rangeNm }
    val longest = fleet.maxBy { it.rangeNm }

    for (aircraft in listOf(shortest, longest)) {
        val routes = kotlinx.coroutines.runBlocking {
            generator.generate(
                RouteRequest(RouteMode.AllAircraft, listOf(aircraft), amount = 50),
                Random(20260816),
            )
        }
        val label = "${aircraft.displayName} (${aircraft.rangeNm} NM, ${aircraft.requiredRunwayFt} ft)"

        if (routes.isEmpty()) {
            failures += "no routes could be generated for $label"
            continue
        }

        val outOfRange = routes.count { it.distanceNm > aircraft.rangeNm }
        if (outOfRange > 0) failures += "$outOfRange routes exceed the range of $label"

        val tooShort = routes.count {
            it.departureRunwayFt < aircraft.requiredRunwayFt ||
                it.destinationRunwayFt < aircraft.requiredRunwayFt
        }
        if (tooShort > 0) failures += "$tooShort routes use a runway too short for $label"

        val longestLeg = routes.maxOf { it.distanceNm }
        println(
            "  route smoke test: %-34s %2d/50 routes, longest %,5d NM"
                .format(aircraft.displayName, routes.size, longestLeg),
        )
    }

    return failures
}

private fun loadIndex(conn: java.sql.Connection): AirportIndex {
    val count = conn.createStatement().use { st ->
        st.executeQuery("SELECT COUNT(*) FROM airports").use { if (it.next()) it.getInt(1) else 0 }
    }
    val builder = AirportIndexBuilder(count)

    conn.createStatement().use { st ->
        st.executeQuery(
            """
            SELECT id, icao, has_icao, name, lat, lon, elevation_ft, country, municipality,
                   size_class, longest_runway_ft, runway_count, has_hard_surface, has_lighting
            FROM airports
            """.trimIndent(),
        ).use { rs ->
            val sizeClasses = AirportSizeClass.entries.toTypedArray()
            while (rs.next()) {
                builder.add(
                    id = rs.getInt(1),
                    icaoCode = rs.getString(2),
                    name = rs.getString(4),
                    latitude = rs.getDouble(5),
                    longitude = rs.getDouble(6),
                    elevation = rs.getInt(7),
                    longestRunway = rs.getInt(11),
                    runways = rs.getInt(12),
                    country = rs.getString(8) ?: "",
                    municipality = rs.getString(9),
                    packedFlags = AirportIndex.packFlags(
                        hasIcao = rs.getInt(3) == 1,
                        hardSurface = rs.getInt(13) == 1,
                        lighting = rs.getInt(14) == 1,
                        sizeClass = sizeClasses[rs.getInt(10).coerceIn(0, sizeClasses.lastIndex)],
                    ),
                )
            }
        }
    }
    return builder.build()
}

private fun readSeedFleet(opts: Options): List<AircraftSpec>? {
    val file = File(opts.output.parentFile.parentFile, "seed/aircrafts.csv")
    if (!file.isFile) return null
    return FleetCsv.parse(file.readText()).aircraft.takeIf { it.isNotEmpty() }
}
