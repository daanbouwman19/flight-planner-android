package com.github.daanbouwman.flightplanner.core.database.repository

import android.util.Log
import com.github.daanbouwman.flightplanner.core.database.airport.AirportDao
import com.github.daanbouwman.flightplanner.core.database.airport.AirportNameIndex
import com.github.daanbouwman.flightplanner.core.database.airport.NameIndexState
import com.github.daanbouwman.flightplanner.core.database.airport.RunwayDao
import com.github.daanbouwman.flightplanner.core.database.airport.buildAirportNameIndex
import com.github.daanbouwman.flightplanner.core.database.airport.toAirport
import com.github.daanbouwman.flightplanner.core.database.airport.toRunway
import com.github.daanbouwman.flightplanner.model.Airport
import com.github.daanbouwman.flightplanner.model.Runway
import com.github.daanbouwman.flightplanner.routing.AirportIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AirportRepository"

/**
 * SQLite variable ceiling for one `IN (...)`.
 *
 * Modern SQLite allows 32,766, but the number of *visible* rows is in the
 * dozens; chunking at a value no screen will ever reach costs nothing and keeps
 * a future bulk caller from tripping the limit.
 */
private const val ID_CHUNK = 900

/**
 * Display data for airports, and the name index that search ranks over.
 *
 * The in-memory `AirportIndex` is the source of truth for everything route
 * generation needs; this is the other side of that split — the text and the
 * elevations that were stripped out of it to keep cold start under budget.
 * Nothing here runs on the startup path, and [prepareNameIndex] is the one
 * expensive call, deliberately explicit so a caller has to choose when to pay it.
 */
interface AirportRepository {

    /** Ranked-search readiness. See [NameIndexState]; treat anything but Ready as "fall back". */
    val nameIndexState: StateFlow<NameIndexState>

    suspend fun findByIcao(icao: String): Airport?

    suspend fun findById(id: Int): Airport?

    /**
     * Display rows for [ids], in the order given, in one query per 900 ids.
     * Ids with no matching airport are dropped rather than reported.
     */
    suspend fun airportsByIds(ids: List<Int>): List<Airport>

    /** As [airportsByIds], keyed by id, for a caller that joins rather than lists. */
    suspend fun airportsByIdMap(ids: List<Int>): Map<Int, Airport>

    /** Display rows for a set of codes, in the order given. */
    suspend fun airportsByIcao(icaos: List<String>): List<Airport>

    /**
     * Display rows for a set of `AirportIndex` slots, in the order given.
     *
     * The tail of a ranked search: a scorer walks the name index and yields
     * slots, and those slots become airports here without the caller having to
     * know that slots are joined to the database by id.
     */
    suspend fun airportsForSlots(index: AirportIndex, slots: IntArray): List<Airport>

    suspend fun runwaysFor(airportId: Int): List<Runway>

    /**
     * Starts building the name index against [index], on a background scope.
     *
     * Idempotent and non-blocking: the second call while a build is in flight,
     * or any call once one has succeeded, does nothing. **Call it after the
     * first frame** — it reads three text columns for every airport, which is
     * the exact cost the index split removed from startup.
     */
    fun prepareNameIndex(index: AirportIndex)

    /** The built index, or null when it is not ready. Never blocks, never builds. */
    fun nameIndexOrNull(): AirportNameIndex?
}

@Singleton
internal class DefaultAirportRepository @Inject constructor(
    private val airportDao: AirportDao,
    private val runwayDao: RunwayDao,
) : AirportRepository {

    /**
     * Owned rather than injected: the module has no application-scoped
     * `CoroutineScope` binding, and the one job this scope runs is a
     * fire-and-forget build whose result lands in [state]. A `SupervisorJob`
     * keeps a failed build from cancelling the scope, so a retry is possible.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val state = MutableStateFlow<NameIndexState>(NameIndexState.Idle)

    override val nameIndexState: StateFlow<NameIndexState> = state.asStateFlow()

    override suspend fun findByIcao(icao: String): Airport? =
        airportDao.findByIcao(icao.trim().uppercase())?.toAirport()

    override suspend fun findById(id: Int): Airport? = airportDao.findById(id)?.toAirport()

    override suspend fun airportsByIds(ids: List<Int>): List<Airport> {
        if (ids.isEmpty()) return emptyList()
        val byId = airportsByIdMap(ids)
        return ids.mapNotNull(byId::get)
    }

    override suspend fun airportsByIdMap(ids: List<Int>): Map<Int, Airport> {
        if (ids.isEmpty()) return emptyMap()
        val distinct = ids.distinct()
        val result = LinkedHashMap<Int, Airport>(distinct.size)
        distinct.chunked(ID_CHUNK).forEach { chunk ->
            airportDao.displayRowsByIds(chunk).forEach { row ->
                result[row.id] = row.toAirport()
            }
        }
        return result
    }

    override suspend fun airportsByIcao(icaos: List<String>): List<Airport> {
        if (icaos.isEmpty()) return emptyList()
        val wanted = icaos.map { it.trim().uppercase() }
        val byCode = HashMap<String, Airport>(wanted.size)
        wanted.distinct().chunked(ID_CHUNK).forEach { chunk ->
            airportDao.displayRowsByIcao(chunk).forEach { row ->
                byCode[row.icao] = row.toAirport()
            }
        }
        return wanted.mapNotNull(byCode::get)
    }

    override suspend fun airportsForSlots(index: AirportIndex, slots: IntArray): List<Airport> =
        airportsByIds(slots.map { index.ids[it] })

    override suspend fun runwaysFor(airportId: Int): List<Runway> =
        runwayDao.forAirport(airportId).map { it.toRunway() }

    override fun prepareNameIndex(index: AirportIndex) {
        // Ready is terminal; Building is already in flight. Idle and Failed both
        // mean "no build running", so both are legitimate starting points, and
        // compareAndSet makes the transition the mutual exclusion.
        val current = state.value
        if (current is NameIndexState.Ready || current is NameIndexState.Building) return
        if (!state.compareAndSet(current, NameIndexState.Building)) return

        scope.launch {
            val built = runCatching {
                val rows = airportDao.nameRows()
                withContext(Dispatchers.Default) { buildAirportNameIndex(index, rows) }
            }
            state.value = built.fold(
                onSuccess = { NameIndexState.Ready(it) },
                onFailure = {
                    Log.e(TAG, "Name index build failed; search stays ICAO-only", it)
                    NameIndexState.Failed(it)
                },
            )
        }
    }

    override fun nameIndexOrNull(): AirportNameIndex? =
        (state.value as? NameIndexState.Ready)?.index
}
