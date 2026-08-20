package com.github.daanbouwman.flightplanner.ui.logbook

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.daanbouwman.flightplanner.core.database.repository.FleetRepository
import com.github.daanbouwman.flightplanner.core.database.repository.LogbookRepository
import com.github.daanbouwman.flightplanner.core.database.user.FlightLogEntity
import com.github.daanbouwman.flightplanner.di.DefaultDispatcher
import com.github.daanbouwman.flightplanner.model.FlightRecord
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/**
 * Drives the Logbook segment of Profile: every flight, grouped by month, with
 * a summary of this year's flying above them.
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
}

/** [runCatching] that lets cancellation through. See [com.github.daanbouwman.flightplanner.ui.plan.PlanViewModel] for why. */
private inline fun <T> runCatchingCancellable(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (failure: Throwable) {
    Result.failure(failure)
}
