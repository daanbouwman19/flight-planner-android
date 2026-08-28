package com.github.daanbouwman.flightplanner.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.github.daanbouwman.flightplanner.model.AircraftSpec
import com.github.daanbouwman.flightplanner.model.Airport
import com.github.daanbouwman.flightplanner.model.Metar
import com.github.daanbouwman.flightplanner.model.Runway
import com.github.daanbouwman.flightplanner.navigation.Destination
import com.github.daanbouwman.flightplanner.routing.FlightTime
import com.github.daanbouwman.flightplanner.routing.GeoArc
import com.github.daanbouwman.flightplanner.routing.WorldOutline
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
     * The heading the leg leaves on and the one it arrives on, in degrees true.
     *
     * Two figures rather than one because on any leg that is not a meridian or
     * the equator they differ — by 62° on EHAM–KJFK — and a pilot reading a
     * single "bearing" would have the wrong one for half the flight.
     */
    val initialBearingDeg: Int? = null,
    val finalBearingDeg: Int? = null,
    /**
     * Every runway end at each field, longest first.
     *
     * Empty means *not read yet* rather than *none*: the ETL admits no airport
     * without at least one open runway of known length, so a field with no rows
     * cannot reach this screen. The blocks fall back to the denormalised longest
     * figure until these land, which is why the read does not gate the first
     * emission.
     */
    val departureRunways: List<Runway> = emptyList(),
    val destinationRunways: List<Runway> = emptyList(),
    /** Weather for each end, once fetched. Null means not yet resolved — not necessarily unavailable. */
    val departureMetar: Metar? = null,
    val destinationMetar: Metar? = null,
    /**
     * The shortest runway this airframe can leave from, in feet. An end below it
     * is marked, exactly as the Plan card marks one.
     */
    val requiredRunwayFt: Int = 0,
    /**
     * True until both airports have been read, so the screen can show a skeleton
     * instead of a half-populated card. It is not a *failure* state: the codes
     * came through the navigation arguments, so the header is drawable from the
     * first frame either way.
     */
    val loading: Boolean = true,
)

/**
 * Holds the state of one route detail screen.
 *
 * The reading itself belongs to [RouteDetailLoader], which is what lets the same
 * load serve a detail *pane* — selected in a list beside it, with no
 * `SavedStateHandle` of navigation arguments to read. What is left here is the
 * part that is genuinely about being a ViewModel: a scope to load in, and a
 * `StateFlow` to publish into.
 *
 * It publishes twice, deliberately; see [RouteDetailLoader] for why.
 */
@HiltViewModel
class RouteDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val loader: RouteDetailLoader,
) : ViewModel() {

    private val route: Destination.RouteDetail = savedStateHandle.toRoute()

    private val _state = MutableStateFlow(RouteDetailUiState(distanceNm = route.distanceNm))
    val state: StateFlow<RouteDetailUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val loaded = loader.load(route)
            _state.value = loaded
            val withRunways = loader.withRunways(loaded)
            _state.value = withRunways
            _state.value = loader.withWeather(withRunways)
        }
    }
}
