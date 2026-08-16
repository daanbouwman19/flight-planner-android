package com.github.daanbouwman.flightplanner.core.database.user

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface AircraftDao {

    @Query("SELECT * FROM aircraft ORDER BY manufacturer, variant")
    fun observeAll(): Flow<List<AircraftEntity>>

    @Query("SELECT * FROM aircraft ORDER BY manufacturer, variant")
    suspend fun all(): List<AircraftEntity>

    @Query("SELECT * FROM aircraft WHERE id = :id")
    suspend fun byId(id: Int): AircraftEntity?

    @Query("SELECT COUNT(*) FROM aircraft")
    suspend fun count(): Int

    /** Surfaced as a badge on the Fleet tab and the "not flown" generation mode. */
    @Query("SELECT COUNT(*) FROM aircraft WHERE flown = 0")
    fun observeNotFlownCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(rows: List<AircraftEntity>)

    @Upsert
    suspend fun upsert(row: AircraftEntity): Long

    @Update
    suspend fun update(row: AircraftEntity)

    @Delete
    suspend fun delete(row: AircraftEntity)

    @Query("UPDATE aircraft SET flown = :flown, date_flown = :dateFlown WHERE id = :id")
    suspend fun setFlown(id: Int, flown: Boolean, dateFlown: String?)

    /** The desktop app's "mark all aircraft as not flown", which also clears the dates. */
    @Query("UPDATE aircraft SET flown = 0, date_flown = NULL")
    suspend fun markAllNotFlown()

    /** Used by "restore default fleet": user-added airframes are preserved. */
    @Query("DELETE FROM aircraft WHERE is_custom = 0")
    suspend fun deleteSeeded()

    @Query("DELETE FROM aircraft")
    suspend fun deleteAll()
}

@Dao
interface FlightLogDao {

    @Query("SELECT * FROM flight_log ORDER BY date DESC, id DESC")
    fun observeAll(): Flow<List<FlightLogEntity>>

    @Query("SELECT * FROM flight_log ORDER BY date DESC, id DESC")
    suspend fun all(): List<FlightLogEntity>

    @Query("SELECT * FROM flight_log ORDER BY date DESC, id DESC LIMIT :limit OFFSET :offset")
    suspend fun page(limit: Int, offset: Int): List<FlightLogEntity>

    @Query("SELECT COUNT(*) FROM flight_log")
    fun observeCount(): Flow<Int>

    /**
     * Destinations already visited with a given airframe.
     *
     * This is what makes "somewhere I have not been in this aircraft" possible;
     * the desktop app has the equivalent notion but only at the airframe level.
     */
    @Query("SELECT DISTINCT arrival_icao FROM flight_log WHERE aircraft_id = :aircraftId")
    suspend fun arrivalsForAircraft(aircraftId: Int): List<String>

    @Insert
    suspend fun insert(row: FlightLogEntity): Long

    @Delete
    suspend fun delete(row: FlightLogEntity)

    @Query("DELETE FROM flight_log")
    suspend fun deleteAll()
}

@Dao
interface MetarCacheDao {

    @Query("SELECT * FROM metar_cache WHERE station IN (:stations)")
    suspend fun forStations(stations: List<String>): List<MetarCacheEntity>

    @Query("SELECT * FROM metar_cache WHERE station = :station LIMIT 1")
    suspend fun forStation(station: String): MetarCacheEntity?

    @Upsert
    suspend fun upsertAll(rows: List<MetarCacheEntity>)

    @Query("DELETE FROM metar_cache WHERE fetched_at < :olderThanEpochMillis")
    suspend fun evictOlderThan(olderThanEpochMillis: Long)

    @Query("DELETE FROM metar_cache")
    suspend fun deleteAll()
}
