package com.github.daanbouwman.flightplanner.core.database.user

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * The user's own data: fleet, logbook and cached weather.
 *
 * This database is **never** migrated destructively. It holds the only
 * information in the app that cannot be regenerated, which is the entire reason
 * it is separate from the shipped airport database.
 */
@Database(
    entities = [AircraftEntity::class, FlightLogEntity::class, MetarCacheEntity::class],
    version = UserDatabase.VERSION,
    exportSchema = true,
)
abstract class UserDatabase : RoomDatabase() {

    abstract fun aircraftDao(): AircraftDao

    abstract fun flightLogDao(): FlightLogDao

    abstract fun metarCacheDao(): MetarCacheDao

    companion object {
        const val VERSION = 1
        const val NAME = "user.db"
    }
}
