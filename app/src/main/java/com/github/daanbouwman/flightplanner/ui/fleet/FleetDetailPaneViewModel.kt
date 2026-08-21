package com.github.daanbouwman.flightplanner.ui.fleet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.daanbouwman.flightplanner.core.database.repository.FleetRepository
import com.github.daanbouwman.flightplanner.model.AircraftSpec
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** One airframe's worth of the detail pane, or nothing selected. */
data class FleetDetailPaneState(val airframeId: Int, val aircraft: AircraftSpec?)

/**
 * Holds the selection for Fleet's two-pane detail, the same job
 * [com.github.daanbouwman.flightplanner.ui.detail.RouteDetailPaneViewModel] does
 * for route detail.
 *
 * Simpler than that counterpart: Fleet has one reactive source
 * ([FleetRepository.observeFleet]) rather than a multi-stage suspend load, so
 * [select] seeds the selection from the row already in hand — from the tap
 * that chose it — and [combine] resolves it against the fleet's own latest
 * value, never the tapped copy. [select] can only be called after the list
 * itself has rendered a row *from* [FleetRepository.observeFleet], so by the
 * time it runs `combine` already has a fleet value to resolve against — there
 * is no start-up race to fall back to the tapped copy for. Falling back to it
 * anyway was tried and was wrong: if the id later disappears from the fleet
 * (`restoreDefaults()` reassigns every bundled airframe's id), the pane would
 * have kept showing the deleted airframe's stale data forever, and a save or
 * toggle against it would silently update zero rows. `aircraft = null` instead
 * — [FleetDetailContent] already renders that as "aircraft not found".
 */
@HiltViewModel
class FleetDetailPaneViewModel @Inject constructor(
    fleetRepository: FleetRepository,
) : ViewModel() {

    private val selected = MutableStateFlow<Int?>(null)

    val state: StateFlow<FleetDetailPaneState?> =
        combine(fleetRepository.observeFleet(), selected) { fleet, airframeId ->
            airframeId?.let { id ->
                FleetDetailPaneState(airframeId = id, aircraft = fleet.find { it.id == id })
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    fun select(aircraft: AircraftSpec) {
        selected.value = aircraft.id
    }

    fun clear() {
        selected.value = null
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
