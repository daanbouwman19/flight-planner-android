package com.github.daanbouwman.flightplanner.core.database.user

import androidx.room.DeleteColumn
import androidx.room.migration.AutoMigrationSpec

/**
 * Version 3 drops [MetarCacheEntity]'s decoded columns.
 *
 * Wind, visibility, ceiling, altimeter and the two observation-time columns are
 * gone because every one of them is now re-derived from `raw` by
 * `MetarParser` on read — see the entity's KDoc for why that is safer than
 * storing them. Room recreates `metar_cache`, copying the columns that survive;
 * `aircraft` and `flight_log` are untouched, so **no user data is lost** and the
 * "this database is never migrated destructively" invariant holds.
 */
@DeleteColumn(tableName = "metar_cache", columnName = "observation_time")
@DeleteColumn(tableName = "metar_cache", columnName = "observation_instant")
@DeleteColumn(tableName = "metar_cache", columnName = "wind_direction_deg")
@DeleteColumn(tableName = "metar_cache", columnName = "wind_speed_kt")
@DeleteColumn(tableName = "metar_cache", columnName = "wind_gust_kt")
@DeleteColumn(tableName = "metar_cache", columnName = "visibility_sm")
@DeleteColumn(tableName = "metar_cache", columnName = "ceiling_ft")
@DeleteColumn(tableName = "metar_cache", columnName = "altimeter_inhg")
class MetarCacheV3Migration : AutoMigrationSpec
