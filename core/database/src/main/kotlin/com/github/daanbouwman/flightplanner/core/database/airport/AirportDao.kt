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

    /**
     * Display data for a set of airports, in **one** query.
     *
     * This is the other half of the trade the index makes: the index carries no
     * text, so a screen showing fifty rows fetches the text for exactly those
     * fifty here. Doing it per row would be fifty statement preparations and
     * fifty cursor round trips for the same bytes.
     *
     * Rows come back in whatever order SQLite finds them; the repository
     * re-orders to the caller's request.
     */
    @Query(
        """
        SELECT id, icao, name, lat, lon, elevation_ft, country, municipality,
               size_class, longest_runway_ft, runway_count, has_hard_surface, has_icao
        FROM airports
        WHERE id IN (:ids)
        """,
    )
    suspend fun displayRowsByIds(ids: List<Int>): List<AirportDisplayRow>

    /** The same projection keyed by code, for a route whose airports are known by ICAO. */
    @Query(
        """
        SELECT id, icao, name, lat, lon, elevation_ft, country, municipality,
               size_class, longest_runway_ft, runway_count, has_hard_surface, has_icao
        FROM airports
        WHERE icao IN (:icaos)
        """,
    )
    suspend fun displayRowsByIcao(icaos: List<String>): List<AirportDisplayRow>

    /**
     * Every searchable text column, ordered by id so the reader can binary-search
     * it instead of building a hash map.
     *
     * The second query allowed to return the whole table — but unlike
     * [loadAllForIndex] it is **never** run at startup. It backs the name index,
     * which is built on a background scope once the first frame is on screen.
     */
    @Query("SELECT id, name, municipality, country FROM airports ORDER BY id ASC")
    suspend fun nameRows(): List<AirportNameRow>
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
