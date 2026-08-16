package com.github.daanbouwman.flightplanner.airportdb

import java.io.File
import java.sql.DriverManager
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
