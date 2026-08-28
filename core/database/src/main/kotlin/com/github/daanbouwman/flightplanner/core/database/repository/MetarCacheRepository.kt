package com.github.daanbouwman.flightplanner.core.database.repository

import com.github.daanbouwman.flightplanner.core.database.user.MetarCacheDao
import com.github.daanbouwman.flightplanner.core.database.user.toEntity
import com.github.daanbouwman.flightplanner.core.database.user.toMetar
import com.github.daanbouwman.flightplanner.model.Metar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The read-through METAR cache.
 *
 * Freshness is checked at read time rather than enforced by periodic
 * deletion — no `WorkManager` job. The table stays well under a few hundred
 * rows for the life of a session, so leaving a stale row in place until it is
 * next asked for costs nothing; [MetarCacheDao.evictOlderThan] stays
 * available for a possible future "clear weather cache" action but nothing
 * calls it automatically.
 */
interface MetarCacheRepository {

    /** Rows for [stations] younger than [maxAgeMillis]. Stale or absent stations are simply not in the map. */
    suspend fun freshFor(stations: List<String>, maxAgeMillis: Long, now: Long = System.currentTimeMillis()): Map<String, Metar>

    suspend fun upsertAll(metars: List<Metar>)
}

@Singleton
internal class DefaultMetarCacheRepository @Inject constructor(
    private val dao: MetarCacheDao,
) : MetarCacheRepository {

    override suspend fun freshFor(stations: List<String>, maxAgeMillis: Long, now: Long): Map<String, Metar> {
        if (stations.isEmpty()) return emptyMap()
        val wanted = stations.map { it.trim().uppercase() }.distinct()
        return dao.forStations(wanted)
            .filter { now - it.fetchedAt < maxAgeMillis }
            .associate { it.station to it.toMetar() }
    }

    override suspend fun upsertAll(metars: List<Metar>) {
        // A report with no raw text cannot be reconstructed on read — `raw` is
        // what every decoded field is re-derived from — and the UI shows the raw
        // text anyway. Never store a row this table could not faithfully return.
        val cacheable = metars.filter { it.raw.isNotBlank() }
        if (cacheable.isEmpty()) return
        val fetchedAt = System.currentTimeMillis()
        dao.upsertAll(cacheable.map { it.toEntity(fetchedAt) })
    }
}
