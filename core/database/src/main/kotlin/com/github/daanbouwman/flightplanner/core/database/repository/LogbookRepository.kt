package com.github.daanbouwman.flightplanner.core.database.repository

import com.github.daanbouwman.flightplanner.core.database.user.FlightLogDao
import com.github.daanbouwman.flightplanner.core.database.user.FlightLogEntity
import com.github.daanbouwman.flightplanner.model.FlightRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** The user's logged flights, in domain terms. */
interface LogbookRepository {

    /** Newest first. The whole log; use [page] when the list is long enough to matter. */
    fun observeAll(): Flow<List<FlightRecord>>

    fun observeCount(): Flow<Int>

    suspend fun all(): List<FlightRecord>

    suspend fun page(limit: Int, offset: Int): List<FlightRecord>

    /** Returns the assigned row id. [record]'s own id is ignored. */
    suspend fun add(record: FlightRecord): Long

    suspend fun delete(record: FlightRecord)

    /**
     * Destinations already reached with a given airframe.
     *
     * A `Set` because every caller asks "have I been here", never "in what
     * order" — and the DAO's `DISTINCT` already guarantees the shape.
     */
    suspend fun arrivalsForAircraft(aircraftId: Int): Set<String>

    suspend fun clear()
}

@Singleton
internal class DefaultLogbookRepository @Inject constructor(
    private val flightLogDao: FlightLogDao,
) : LogbookRepository {

    override fun observeAll(): Flow<List<FlightRecord>> =
        flightLogDao.observeAll().map { rows -> rows.map { it.toRecord() } }

    override fun observeCount(): Flow<Int> = flightLogDao.observeCount()

    override suspend fun all(): List<FlightRecord> = flightLogDao.all().map { it.toRecord() }

    override suspend fun page(limit: Int, offset: Int): List<FlightRecord> =
        flightLogDao.page(limit, offset).map { it.toRecord() }

    override suspend fun add(record: FlightRecord): Long =
        flightLogDao.insert(record.toEntity().copy(id = 0))

    override suspend fun delete(record: FlightRecord) = flightLogDao.delete(record.toEntity())

    override suspend fun arrivalsForAircraft(aircraftId: Int): Set<String> =
        flightLogDao.arrivalsForAircraft(aircraftId).toSet()

    override suspend fun clear() = flightLogDao.deleteAll()
}

internal fun FlightLogEntity.toRecord(): FlightRecord = FlightRecord(
    id = id,
    departureIcao = departureIcao,
    arrivalIcao = arrivalIcao,
    aircraftId = aircraftId,
    date = date,
    distanceNm = distanceNm,
)

internal fun FlightRecord.toEntity(): FlightLogEntity = FlightLogEntity(
    id = id,
    departureIcao = departureIcao,
    arrivalIcao = arrivalIcao,
    aircraftId = aircraftId,
    date = date,
    distanceNm = distanceNm,
)
