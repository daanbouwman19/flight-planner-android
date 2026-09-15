package com.github.daanbouwman.flightplanner.core.database.user

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * `UserDatabase` 1 → 2 → 3, against the committed schema JSONs.
 *
 * This is the one database in the app that holds data nobody can regenerate —
 * the fleet's flown flags and the logbook — and `UserDatabase`'s own KDoc
 * promises it is never migrated destructively. Both steps are
 * `@AutoMigration`s, which means the migration SQL is generated at build time
 * from the schema JSONs and never read by a person; the only way to know that
 * the eight-column drop in version 3 recreates `metar_cache` without touching
 * `aircraft` or `flight_log` is to run it against a real version-1 file with
 * rows in it. That needs a device: `MigrationTestHelper` opens the exported
 * schemas as test assets and drives the same generated code the app ships.
 *
 * Driver-API form throughout (`SQLiteConnection`, not `SupportSQLiteDatabase`),
 * because the app opens this database through `BundledSQLiteDriver` and the
 * migration has to be proven on the SQLite build it will actually run on.
 */
@RunWith(AndroidJUnit4::class)
class UserDatabaseMigrationTest {

    private val databaseFile =
        ApplicationProvider.getApplicationContext<Context>().getDatabasePath("user-migration-test.db")

    @get:Rule
    val helper = MigrationTestHelper(
        instrumentation = InstrumentationRegistry.getInstrumentation(),
        file = databaseFile,
        driver = BundledSQLiteDriver(),
        databaseClass = UserDatabase::class,
        databaseFactory = { UserDatabase_Impl() },
        autoMigrationSpecs = listOf(MetarCacheV3Migration()),
    )

    @Before
    fun deleteStaleFile() {
        databaseFile.delete()
        listOf("-wal", "-shm", "-journal").forEach { java.io.File(databaseFile.path + it).delete() }
    }

    @Test
    fun migrate1To2To3_keepsFleetAndLogbookRows_andDropsTheDecodedMetarColumns() {
        // Version 1, with one row in each user table and one cached report,
        // written with the version-1 column set from schemas/…/1.json.
        helper.createDatabase(1).use { v1 ->
            v1.execSQL(
                """
                INSERT INTO aircraft
                    (id, manufacturer, variant, icao_code, flown, range_nm, category, cruise_speed_kt,
                     date_flown, takeoff_distance_m, is_custom)
                VALUES (7, 'Boeing', '737-800', 'B738', 1, 2935, 'Narrow-body', 450, '2026-03-01', 2300, 0)
                """.trimIndent(),
            )
            v1.execSQL(
                """
                INSERT INTO flight_log (id, departure_icao, arrival_icao, aircraft_id, date, distance_nm)
                VALUES (3, 'EHAM', 'KJFK', 7, '2026-03-01', 3163)
                """.trimIndent(),
            )
            v1.execSQL(
                """
                INSERT INTO metar_cache (station, raw, flight_rules, observation_time, observation_instant, fetched_at)
                VALUES ('EHAM', 'METAR EHAM 271855Z 31003KT CAVOK 22/17 Q1008', 'VFR', '271855Z', '2026-08-27T18:55:00Z', 1000)
                """.trimIndent(),
            )
        }

        // 1 → 2 → 3 through the auto-migrations `UserDatabase` declares; the
        // helper validates the result against schemas/…/3.json.
        helper.runMigrationsAndValidate(3).use { v3 ->
            // The promise: no user data lost.
            v3.queryOne("SELECT manufacturer || ' ' || variant || '|' || flown || '|' || range_nm FROM aircraft WHERE id = 7")
                .let { assertEquals("Boeing 737-800|1|2935", it) }
            v3.queryOne("SELECT departure_icao || '>' || arrival_icao || '|' || distance_nm FROM flight_log WHERE id = 3")
                .let { assertEquals("EHAM>KJFK|3163", it) }
            assertEquals("1", v3.queryOne("SELECT COUNT(*) FROM aircraft"))
            assertEquals("1", v3.queryOne("SELECT COUNT(*) FROM flight_log"))

            // The cache row survives with the columns that survive, and `raw`
            // — the one lossless field everything is now derived from — intact.
            v3.queryOne("SELECT raw || '|' || flight_rules || '|' || fetched_at FROM metar_cache WHERE station = 'EHAM'")
                .let { assertEquals("METAR EHAM 271855Z 31003KT CAVOK 22/17 Q1008|VFR|1000", it) }

            // The eight columns MetarCacheV3Migration deletes are gone…
            val columns = v3.columnsOf("metar_cache")
            for (dropped in DROPPED_IN_V3) {
                assertFalse("metar_cache still has `$dropped` after migrating to 3", dropped in columns)
            }
            // …and the provider-only columns version 3 adds are present and null
            // for a row that predates them.
            for (added in ADDED_IN_V3) {
                assertTrue("metar_cache lacks `$added` after migrating to 3", added in columns)
            }
            assertEquals(null, v3.queryOne("SELECT report_kind FROM metar_cache WHERE station = 'EHAM'"))
        }
    }

    private fun SQLiteConnection.queryOne(sql: String): String? = prepare(sql).use { statement ->
        assertTrue("no row for: $sql", statement.step())
        if (statement.isNull(0)) null else statement.getText(0)
    }

    private fun SQLiteConnection.columnsOf(table: String): Set<String> =
        prepare("PRAGMA table_info(`$table`)").use { statement ->
            buildSet { while (statement.step()) add(statement.getText(1)) }
        }

    private companion object {
        /** The `@DeleteColumn`s on [MetarCacheV3Migration], verbatim. */
        val DROPPED_IN_V3 = listOf(
            "observation_time", "observation_instant", "wind_direction_deg", "wind_speed_kt",
            "wind_gust_kt", "visibility_sm", "ceiling_ft", "altimeter_inhg",
        )

        /** A sample of what version 3 adds; all nullable, per `UserDatabase`'s KDoc. */
        val ADDED_IN_V3 = listOf("report_kind", "observation_epoch_seconds", "station_name", "latitude")
    }
}
