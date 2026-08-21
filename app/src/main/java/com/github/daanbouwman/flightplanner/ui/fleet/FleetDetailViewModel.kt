package com.github.daanbouwman.flightplanner.ui.fleet

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.github.daanbouwman.flightplanner.core.database.repository.FleetRepository
import com.github.daanbouwman.flightplanner.model.AircraftSpec
import com.github.daanbouwman.flightplanner.navigation.Destination
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One airframe's worth of the full-screen detail. */
data class FleetDetailUiState(val aircraft: AircraftSpec? = null, val loading: Boolean = true)

/**
 * Holds the state of one full-screen Fleet detail.
 *
 * Reads the id from the navigation arguments — the pane host has no
 * `SavedStateHandle`, which is why the reactive read lives here rather than in
 * something both hosts could share, the same split
 * [com.github.daanbouwman.flightplanner.ui.detail.RouteDetailViewModel]'s KDoc
 * describes for route detail. Driven by [FleetRepository.observeFleet] rather
 * than a one-shot `byId`, so a write from here or from the list underneath it
 * shows up on both without a manual refetch.
 */
@HiltViewModel
class FleetDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val fleetRepository: FleetRepository,
) : ViewModel() {

    private val route: Destination.FleetDetail = savedStateHandle.toRoute()

    val state: StateFlow<FleetDetailUiState> = fleetRepository.observeFleet()
        .map { fleet -> FleetDetailUiState(aircraft = fleet.find { it.id == route.airframeId }, loading = false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), FleetDetailUiState())

    fun toggleFlown() {
        val aircraft = state.value.aircraft ?: return
        viewModelScope.launch { runCatchingCancellable { fleetRepository.setFlown(aircraft.id, !aircraft.flown) } }
    }

    fun update(spec: AircraftSpec) {
        viewModelScope.launch { runCatchingCancellable { fleetRepository.update(spec) } }
    }

    private companion object {
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
