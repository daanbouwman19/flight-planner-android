package com.github.daanbouwman.flightplanner.ui.fleet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.daanbouwman.flightplanner.core.database.repository.FleetRepository
import com.github.daanbouwman.flightplanner.di.DefaultDispatcher
import com.github.daanbouwman.flightplanner.model.AircraftSpec
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives the Fleet screen: every airframe, grouped by category, filtered by
 * [FleetMode].
 *
 * The grouping and filtering run on [defaultDispatcher] rather than the
 * collector's context, for the same reason [LogbookViewModel] does —
 * `collectAsStateWithLifecycle` collects on the main thread and the fleet is
 * unbounded in principle even though it is ~116 rows today.
 */
@HiltViewModel
class FleetViewModel @Inject constructor(
    private val fleetRepository: FleetRepository,
    @param:DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val mode = MutableStateFlow(FleetMode.All)

    val uiState: StateFlow<FleetUiState> =
        combine(fleetRepository.observeFleet(), mode) { fleet, mode ->
            // One pass for the flown count and the distinct categories, rather
            // than two count{} calls plus a separate map+distinct+sorted — all
            // four were walking the same list independently.
            var flownCount = 0
            val categories = sortedSetOf<String>()
            for (aircraft in fleet) {
                if (aircraft.flown) flownCount++
                categories += aircraft.category
            }
            FleetUiState(
                groups = fleet.filterByMode(mode).groupByCategory(),
                mode = mode,
                totalCount = fleet.size,
                flownCount = flownCount,
                notFlownCount = fleet.size - flownCount,
                categories = categories.toList(),
                status = FleetStatus.Ready,
            )
        }
            .flowOn(defaultDispatcher)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), FleetUiState())

    fun setMode(newMode: FleetMode) {
        mode.value = newMode
    }

    /** Flips one airframe's flown flag, stamping or clearing the date. */
    fun toggleFlown(aircraft: AircraftSpec) {
        viewModelScope.launch {
            runCatchingCancellable { fleetRepository.setFlown(aircraft.id, !aircraft.flown) }
        }
    }

    /** The desktop app's "mark all aircraft as not flown". */
    fun markAllNotFlown() {
        viewModelScope.launch { runCatchingCancellable { fleetRepository.markAllNotFlown() } }
    }

    /** Replaces the bundled airframes with a fresh seed; user-added ones survive. */
    fun restoreDefaults() {
        viewModelScope.launch { runCatchingCancellable { fleetRepository.restoreDefaults() } }
    }

    /** Adds a user-defined airframe. [spec]'s id is ignored — the repository assigns one. */
    fun addAircraft(spec: AircraftSpec) {
        viewModelScope.launch { runCatchingCancellable { fleetRepository.add(spec) } }
    }

    /** Saves changes to an existing airframe — the two-pane detail's inline edit. */
    fun updateAircraft(spec: AircraftSpec) {
        viewModelScope.launch { runCatchingCancellable { fleetRepository.update(spec) } }
    }

    private companion object {
        /** Matches [com.github.daanbouwman.flightplanner.ui.logbook.LogbookViewModel]'s own constant. */
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

/** [runCatching] that lets cancellation through. See [com.github.daanbouwman.flightplanner.ui.plan.PlanViewModel] for why. */
private inline fun <T> runCatchingCancellable(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (failure: Throwable) {
    Result.failure(failure)
}
