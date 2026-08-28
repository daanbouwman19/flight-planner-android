package com.github.daanbouwman.flightplanner.core.database.user

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * The user's own data: fleet, logbook and cached weather.
 *
 * This database is **never** migrated destructively. It holds the only
 * information in the app that cannot be regenerated, which is the entire reason
 * it is separate from the shipped airport database.
 *
 * Version 2 added decoded weather columns to [MetarCacheEntity]; **version 3
 * removes them again** in favour of re-deriving them from the raw METAR text,
 * and adds the provider-only facts a METAR does not carry. See the entity's
 * KDoc for the reasoning and [MetarCacheV3Migration] for the migration.
 *
 * Both steps are `@AutoMigration`s: every column version 3 adds is nullable, and
 * the deletions are declared on the spec, so Room generates the `ALTER TABLE`
 * and table-recreate steps without a hand-written
 * [androidx.room.migration.Migration]. Neither step touches `aircraft` or
 * `flight_log`.
 */
@Database(
    entities = [AircraftEntity::class, FlightLogEntity::class, MetarCacheEntity::class],
    version = UserDatabase.VERSION,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3, spec = MetarCacheV3Migration::class),
    ],
    exportSchema = true,
)
abstract class UserDatabase : RoomDatabase() {

    abstract fun aircraftDao(): AircraftDao

    abstract fun flightLogDao(): FlightLogDao

    abstract fun metarCacheDao(): MetarCacheDao

    companion object {
        const val VERSION = 3
        const val NAME = "user.db"
    }
}
