package com.github.daanbouwman.flightplanner.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.github.daanbouwman.flightplanner.core.database.repository.AirportRepository
import com.github.daanbouwman.flightplanner.core.database.repository.FleetRepository
import com.github.daanbouwman.flightplanner.model.AircraftSpec
import com.github.daanbouwman.flightplanner.model.Airport
import com.github.daanbouwman.flightplanner.navigation.Destination
import com.github.daanbouwman.flightplanner.routing.FlightTime
import com.github.daanbouwman.flightplanner.routing.GeoArc
import com.github.daanbouwman.flightplanner.routing.GreatCircle
import com.github.daanbouwman.flightplanner.routing.RouteArc
import com.github.daanbouwman.flightplanner.routing.WorldOutline
import com.github.daanbouwman.flightplanner.world.WorldOutlineLoader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the detail screen draws once its two airports have been read. */
data class RouteDetailUiState(
    val departure: Airport? = null,
    val destination: Airport? = null,
    val aircraft: AircraftSpec? = null,
    val distanceNm: Int = 0,
    val flightTime: FlightTime? = null,
    val arc: GeoArc? = null,
    val outline: WorldOutline = WorldOutline.Empty,
    /**
     * True until both airports have been read, so the screen can show a skeleton
     * instead of a half-populated card. It is not a *failure* state: the codes
     * came through the navigation arguments, so the header is drawable from the
     * first frame either way.
     */
    val loading: Boolean = true,
)

/**
 * Reads the two airports a route detail is about.
 *
 * The destination carries codes rather than an id — a generated route has no
 * identity to look up, it exists only in the batch that produced it — so the
 * screen has to resolve those codes into airports itself. That is two indexed
 * lookups, which is why this is a plain load rather than anything shared with
 * the Plan screen's ViewModel: reading a route again is cheaper than keeping one
 * alive across a navigation.
 */
@HiltViewModel
class RouteDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val airportRepository: AirportRepository,
    private val fleetRepository: FleetRepository,
    private val worldOutlineLoader: WorldOutlineLoader,
) : ViewModel() {

    private val route: Destination.RouteDetail = savedStateHandle.toRoute()

    private val _state = MutableStateFlow(RouteDetailUiState(distanceNm = route.distanceNm))
    val state: StateFlow<RouteDetailUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val departure = airportRepository.findByIcao(route.departureIcao)
            val destination = airportRepository.findByIcao(route.destinationIcao)
            val aircraft = fleetRepository.byId(route.aircraftId)

            val arc = if (departure != null && destination != null) {
                RouteArc.sampleGeographic(
                    depLat = departure.latitude,
                    depLon = departure.longitude,
                    destLat = destination.latitude,
                    destLon = destination.longitude,
                    samples = RouteArc.CARD_SAMPLES,
                )
            } else {
                null
            }

            _state.value = RouteDetailUiState(
                departure = departure,
                destination = destination,
                aircraft = aircraft,
                distanceNm = route.distanceNm,
                flightTime = aircraft?.let {
                    GreatCircle.flightTime(route.distanceNm.toDouble(), it.cruiseSpeedKt)
                },
                arc = arc,
                outline = worldOutlineLoader.load(),
                loading = false,
            )
        }
    }
}
