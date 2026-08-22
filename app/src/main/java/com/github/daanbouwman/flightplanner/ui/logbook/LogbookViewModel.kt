package com.github.daanbouwman.flightplanner.ui.logbook

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.daanbouwman.flightplanner.core.database.repository.AirportRepository
import com.github.daanbouwman.flightplanner.core.database.repository.FleetRepository
import com.github.daanbouwman.flightplanner.core.database.repository.LogbookRepository
import com.github.daanbouwman.flightplanner.di.DefaultDispatcher
import com.github.daanbouwman.flightplanner.index.AirportIndexProvider
import com.github.daanbouwman.flightplanner.model.AircraftSpec
import com.github.daanbouwman.flightplanner.model.Airport
import com.github.daanbouwman.flightplanner.model.FlightRecord
import com.github.daanbouwman.flightplanner.routing.GreatCircle
import com.github.daanbouwman.flightplanner.search.airportSearchScope
import com.github.daanbouwman.flightplanner.search.rankedAircraftResults
import com.github.daanbouwman.flightplanner.search.rankedAirportResults
import com.github.daanbouwman.flightplanner.ui.plan.SearchScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

private const val TAG = "LogbookViewModel"

/**
 * Drives the Logbook segment of Profile: every flight, grouped by month, with
 * a summary of this year's flying above them, plus the add-flight sheet (D2)
 * that lets a flight be logged directly rather than only via Plan's
 * mark-as-flown swipe.
 *
 * The grouping and the year summary run on [defaultDispatcher] rather than in
 * the collector's context, for the same reason
 * [LogbookRepository.observeAll]'s own entity-to-domain map already does: the
 * logbook is unbounded, and `collectAsStateWithLifecycle` collects on the main
 * thread.
 */
@HiltViewModel
class LogbookViewModel @Inject constructor(
    private val logbookRepository: LogbookRepository,
    private val fleetRepository: FleetRepository,
    private val airportRepository: AirportRepository,
    private val indexProvider: AirportIndexProvider,
    @param:DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : ViewModel() {

    val uiState: StateFlow<LogbookUiState> =
        combine(logbookRepository.observeAll(), fleetRepository.observeFleet()) { records, fleet ->
            val aircraftById = fleet.associateBy { it.id }
            val rows = records.mapNotNull { it.toLogbookRowOrNull(aircraftById) }
            LogbookUiState(
                groups = rows.groupByMonth(),
                summary = rows.summarizeYear(LocalDate.now().year),
                status = LogbookStatus.Ready,
            )
        }
            .flowOn(defaultDispatcher)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), LogbookUiState())

    private val _events = Channel<LogbookEvent>(capacity = Channel.CONFLATED)
    val events = _events.receiveAsFlow()

    private var undoLog: LogbookRow? = null

    /**
     * Removes a flight from the logbook.
     *
     * It is not a placeholder: the record is deleted from the user database,
     * and the year summary above updates to reflect the loss. The airframe's
     * own "flown" flag is not touched — deleting a log entry is a correction
     * of a record, not a reversal of the flight's effect on the fleet.
     */
    fun delete(row: LogbookRow) {
        viewModelScope.launch {
            runCatchingCancellable {
                logbookRepository.delete(row.toRecord())
            }.onSuccess {
                undoLog = row
                _events.trySend(LogbookEvent.FlightDeleted)
            }
        }
    }

    /** Reverses the last [delete]. */
    fun undoDelete() {
        val row = undoLog ?: return
        undoLog = null
        viewModelScope.launch {
            runCatchingCancellable {
                logbookRepository.add(row.toRecord())
            }
        }
    }

    private fun LogbookRow.toRecord() = FlightRecord(
        id = id,
        departureIcao = departureIcao,
        arrivalIcao = arrivalIcao,
        aircraftId = aircraftId,
        date = date.toString(),
        distanceNm = distanceNm,
    )

    // ------------------------------------------------------- add-flight sheet

    /**
     * The fleet, observed independently of [uiState] for the aircraft picker —
     * mirrors [com.github.daanbouwman.flightplanner.ui.plan.PlanViewModel]'s own
     * `fleet` flow, kept separate so the picker does not carry the logbook rows
     * it has no use for.
     */
    private val fleet: StateFlow<List<AircraftSpec>> = fleetRepository.observeFleet()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    private val addFlightAircraftQuery = MutableStateFlow("")
    private val addFlightDepartureQuery = MutableStateFlow("")
    private val addFlightDestinationQuery = MutableStateFlow("")

    val addFlightAircraftResults: StateFlow<List<AircraftSpec>> = rankedAircraftResults(
        viewModelScope,
        addFlightAircraftQuery,
        fleet,
        defaultDispatcher,
        stopTimeoutMillis = STOP_TIMEOUT_MILLIS,
    )

    val addFlightDepartureResults: StateFlow<List<Airport>> = rankedAirportResults(
        viewModelScope,
        addFlightDepartureQuery,
        indexProvider,
        airportRepository,
        defaultDispatcher,
        stopTimeoutMillis = STOP_TIMEOUT_MILLIS,
    )

    val addFlightDestinationResults: StateFlow<List<Airport>> = rankedAirportResults(
        viewModelScope,
        addFlightDestinationQuery,
        indexProvider,
        airportRepository,
        defaultDispatcher,
        stopTimeoutMillis = STOP_TIMEOUT_MILLIS,
    )

    /** Shared by both airport targets — it reflects the name index, not which field is open. */
    val addFlightSearchScope: StateFlow<SearchScope> =
        airportSearchScope(viewModelScope, airportRepository, stopTimeoutMillis = STOP_TIMEOUT_MILLIS)

    fun setAddFlightAircraftQuery(query: String) {
        addFlightAircraftQuery.value = query
    }

    fun setAddFlightDepartureQuery(query: String) {
        addFlightDepartureQuery.value = query
    }

    fun setAddFlightDestinationQuery(query: String) {
        addFlightDestinationQuery.value = query
    }

    /** As [com.github.daanbouwman.flightplanner.ui.plan.PlanViewModel.retryNameSearch]. */
    fun retryAddFlightNameSearch() {
        viewModelScope.launch {
            runCatchingCancellable { airportRepository.prepareNameIndex(indexProvider.get()) }
                .onFailure { Log.w(TAG, "Retrying the airport name index failed", it) }
        }
    }

    /**
     * Logs a flight directly, independent of route generation.
     *
     * [distanceNm] is the caller's already-computed [GreatCircle.distanceNm] —
     * the same value [AddFlightValidation] used to draw the sheet's live DIST
     * chip — rather than recomputed here, so what was shown and what gets
     * persisted cannot drift apart.
     *
     * Only the logbook is written — [FleetRepository.setFlown] is deliberately
     * not called here, matching the desktop reference's own `add_history_entry`
     * (as opposed to `mark_route_as_flown`, which writes both): a manually
     * logged flight is a record of something that already happened, not an
     * event that should also flip the airframe's own flown flag.
     */
    fun addFlight(aircraft: AircraftSpec, departure: Airport, destination: Airport, date: LocalDate, distanceNm: Int) {
        viewModelScope.launch {
            runCatchingCancellable {
                logbookRepository.add(
                    FlightRecord(
                        id = 0,
                        departureIcao = departure.icao,
                        arrivalIcao = destination.icao,
                        aircraftId = aircraft.id,
                        date = date.toString(),
                        distanceNm = distanceNm,
                    ),
                )
            }.onSuccess {
                _events.trySend(LogbookEvent.FlightAdded(departure.icao, destination.icao))
            }.onFailure { failure ->
                Log.e(TAG, "Adding a flight from ${departure.icao} to ${destination.icao} failed", failure)
                _events.trySend(LogbookEvent.FlightAddFailed)
            }
        }
    }

    private companion object {
        /**
         * How long the flow keeps running after the last collector goes away.
         * Matches [com.github.daanbouwman.flightplanner.ui.plan.PlanViewModel]'s
         * own constant: long enough to survive a configuration change, short
         * enough that a backgrounded app stops observing the database.
         */
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

/** Events the Logbook screen should act on, usually by showing a snackbar. */
sealed interface LogbookEvent {
    data object FlightDeleted : LogbookEvent
    data class FlightAdded(val departureIcao: String, val destinationIcao: String) : LogbookEvent
    data object FlightAddFailed : LogbookEvent
}

/** [runCatching] that lets cancellation through. See [com.github.daanbouwman.flightplanner.ui.plan.PlanViewModel] for why. */
private inline fun <T> runCatchingCancellable(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (failure: Throwable) {
    Result.failure(failure)
}
