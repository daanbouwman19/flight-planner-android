package com.github.daanbouwman.flightplanner.airportdb

import com.github.daanbouwman.flightplanner.airportdb.RoomSchema.ddl
import com.github.daanbouwman.flightplanner.routing.IcaoCode
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

/**
 * Writes the shipped SQLite asset.
 *
 * `user_version` and `room_master_table` both come from Room's exported schema.
 * Room checks both when opening a prepackaged database, and a mismatch is a
 * crash on first launch rather than a graceful fallback.
 *
 * Note there is deliberately no attempt to control physical row order. The
 * in-memory index needs airports sorted by `longest_runway_ft`, but `id` is
 * declared `INTEGER PRIMARY KEY`, which SQLite aliases to `rowid`; rows are
 * therefore stored in upstream-id order no matter what order they are inserted
 * in. The ordering is imposed by the loading query instead — see
 * `AirportDao.loadAllForIndex`.
 */
object Writer {

    fun write(
        output: File,
        schema: RoomSchemaDatabase,
        airports: List<AirportRow>,
        runwaysByAirport: Map<Int, MutableList<RunwayRecord>>,
        metadata: Map<String, String>,
        report: BuildReport,
    ) {
        output.parentFile?.mkdirs()
        // Remove any stale sidecar files too, or SQLite may resurrect old pages.
        listOf(output, File("${output.path}-wal"), File("${output.path}-shm"), File("${output.path}-journal"))
            .forEach { if (it.exists()) check(it.delete()) { "Could not delete $it" } }

        DriverManager.getConnection("jdbc:sqlite:${output.path}").use { conn ->
            conn.autoCommit = false
            conn.createStatement().use { st ->
                st.executeUpdate("PRAGMA journal_mode = DELETE")
                for (statement in schema.ddl()) st.executeUpdate(statement)
            }

            insertAirports(conn, airports, report)
            insertRunways(conn, airports, runwaysByAirport, report)
            insertMetadata(conn, metadata)

            conn.commit()

            conn.autoCommit = true
            conn.createStatement().use { st ->
                st.executeUpdate("ANALYZE")
                st.executeUpdate("PRAGMA user_version = ${schema.version}")
                st.executeUpdate("VACUUM")
            }
        }
    }

    private fun insertAirports(conn: Connection, airports: List<AirportRow>, report: BuildReport) {
        val sql = """
            INSERT INTO airports
              (id, icao, code_packed, has_icao, ident, name, lat, lon, elevation_ft, country, municipality,
               size_class, scheduled_service, longest_runway_ft, runway_count, has_hard_surface, has_lighting)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
        """.trimIndent()

        conn.prepareStatement(sql).use { ps ->
            for (a in airports) {
                // Packed here rather than on the device: it removes the only text
                // column from the startup index read.
                val packed = IcaoCode.encode(a.icao)
                check(packed >= 0) { "Code '${a.icao}' cannot be packed; the filter should have excluded it" }

                ps.setInt(1, a.id)
                ps.setString(2, a.icao)
                ps.setInt(3, packed)
                ps.setInt(4, if (a.hasIcao) 1 else 0)
                ps.setString(5, a.ident)
                ps.setString(6, a.name)
                ps.setDouble(7, a.lat)
                ps.setDouble(8, a.lon)
                ps.setInt(9, a.elevationFt)
                ps.setString(10, a.country)
                if (a.municipality == null) ps.setNull(11, java.sql.Types.VARCHAR) else ps.setString(11, a.municipality)
                ps.setInt(12, a.sizeClass)
                ps.setInt(13, if (a.scheduledService) 1 else 0)
                ps.setInt(14, a.longestRunwayFt)
                ps.setInt(15, a.runwayCount)
                ps.setInt(16, if (a.hasHardSurface) 1 else 0)
                ps.setInt(17, if (a.hasLighting) 1 else 0)
                ps.addBatch()
                report.airportsWritten++
                if (a.hasIcao) report.airportsWithIcao++
            }
            ps.executeBatch()
        }
    }

    private fun insertRunways(
        conn: Connection,
        airports: List<AirportRow>,
        runwaysByAirport: Map<Int, MutableList<RunwayRecord>>,
        report: BuildReport,
    ) {
        val sql = """
            INSERT INTO runways
              (id, airport_id, ident, true_heading, length_ft, width_ft, surface, surface_kind,
               lat, lon, elevation_ft, lighted)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
        """.trimIndent()

        conn.prepareStatement(sql).use { ps ->
            for (airport in airports) {
                // Only runways of surviving airports are written, so the table can
                // never contain an orphan.
                val records = runwaysByAirport[airport.id] ?: continue
                for (r in records.flatMap { it.ends }) {
                    ps.setInt(1, r.id)
                    ps.setInt(2, r.airportId)
                    ps.setString(3, r.ident)
                    if (r.trueHeading == null) ps.setNull(4, java.sql.Types.REAL) else ps.setDouble(4, r.trueHeading)
                    ps.setInt(5, r.lengthFt)
                    ps.setInt(6, r.widthFt)
                    ps.setString(7, r.surface)
                    ps.setInt(8, r.surfaceKind)
                    if (r.lat == null) ps.setNull(9, java.sql.Types.REAL) else ps.setDouble(9, r.lat)
                    if (r.lon == null) ps.setNull(10, java.sql.Types.REAL) else ps.setDouble(10, r.lon)
                    ps.setInt(11, r.elevationFt ?: airport.elevationFt)
                    ps.setInt(12, if (r.lighted) 1 else 0)
                    ps.addBatch()
                    report.runwayEndsWritten++
                }
            }
            ps.executeBatch()
        }
    }

    private fun insertMetadata(conn: Connection, metadata: Map<String, String>) {
        conn.prepareStatement("INSERT INTO dataset_meta (`key`, `value`) VALUES (?,?)").use { ps ->
            for ((k, v) in metadata) {
                ps.setString(1, k)
                ps.setString(2, v)
                ps.addBatch()
            }
            ps.executeBatch()
        }
    }
}
