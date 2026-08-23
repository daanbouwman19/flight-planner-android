package com.github.daanbouwman.flightplanner.ui.airport

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.github.daanbouwman.flightplanner.core.database.repository.AirportRepository
import com.github.daanbouwman.flightplanner.model.Airport
import com.github.daanbouwman.flightplanner.model.Runway
import com.github.daanbouwman.flightplanner.navigation.Destination
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One airport's worth of the full-screen detail: itself, and its runways. */
data class AirportDetailUiState(
    val airport: Airport? = null,
    val runways: List<Runway> = emptyList(),
    val loading: Boolean = true,
)

/**
 * Holds the state of the Airport detail screen (E2).
 *
 * Reads the id from the navigation arguments, like
 * [com.github.daanbouwman.flightplanner.ui.fleet.FleetDetailViewModel]. Unlike
 * that one, airport display data has no `Flow` to observe — it is static
 * dataset content, not something the user's own actions rewrite — so this
 * loads once in [init] rather than collecting a repository flow. Two
 * repository calls is not enough to warrant a separate loader class the way
 * [com.github.daanbouwman.flightplanner.ui.detail.RouteDetailLoader] exists
 * for a multi-step arc/bearing computation this screen doesn't have.
 */
@HiltViewModel
class AirportDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val airportRepository: AirportRepository,
) : ViewModel() {

    private val route: Destination.AirportDetail = savedStateHandle.toRoute()

    private val _state = MutableStateFlow(AirportDetailUiState())
    val state: StateFlow<AirportDetailUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val airport = airportRepository.findById(route.airportId)
            val runways = airport?.let { airportRepository.runwaysFor(it.id) }.orEmpty()
            _state.value = AirportDetailUiState(airport = airport, runways = runways, loading = false)
        }
    }
}
