package com.github.daanbouwman.flightplanner.core.database.airport

import androidx.room.Dao
import androidx.room.Query

@Dao
interface AirportDao {

    @Query("SELECT COUNT(*) FROM airports")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM airports WHERE has_icao = 1")
    suspend fun countWithIcao(): Int

    /**
     * Every airport, ordered ascending by longest runway.
     *
     * The in-memory index binary-searches that column, so the order is a hard
     * requirement rather than a nicety. It has to be imposed here: `id` is an
     * `INTEGER PRIMARY KEY`, which SQLite aliases to `rowid`, so rows are stored
     * in upstream-id order regardless of how the ETL inserts them. The tie-break
     * on `id` keeps the result deterministic.
     *
     * This is the one query allowed to return the whole table; it runs once at
     * startup. Everything else in the app queries the index, not the database.
     */
    @Query("SELECT * FROM airports ORDER BY longest_runway_ft ASC, id ASC")
    suspend fun loadAllForIndex(): List<AirportEntity>

    @Query("SELECT * FROM airports WHERE icao = :icao LIMIT 1")
    suspend fun findByIcao(icao: String): AirportEntity?

    @Query("SELECT * FROM airports WHERE id = :id LIMIT 1")
    suspend fun findById(id: Int): AirportEntity?
}

@Dao
interface RunwayDao {

    @Query("SELECT COUNT(*) FROM runways")
    suspend fun count(): Int

    @Query("SELECT * FROM runways WHERE airport_id = :airportId ORDER BY length_ft DESC, ident ASC")
    suspend fun forAirport(airportId: Int): List<RunwayEntity>

    /** Used by the asset-integrity test to prove there are no orphaned runways. */
    @Query("SELECT COUNT(*) FROM runways WHERE airport_id NOT IN (SELECT id FROM airports)")
    suspend fun countOrphans(): Int
}

@Dao
interface DatasetMetaDao {

    @Query("SELECT value FROM dataset_meta WHERE `key` = :key LIMIT 1")
    suspend fun value(key: String): String?

    @Query("SELECT * FROM dataset_meta")
    suspend fun all(): List<DatasetMetaEntity>
}
