package com.github.daanbouwman.flightplanner.ui.airport

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.github.daanbouwman.flightplanner.core.database.repository.AirportRepository
import com.github.daanbouwman.flightplanner.model.Airport
import com.github.daanbouwman.flightplanner.model.Metar
import com.github.daanbouwman.flightplanner.model.Runway
import com.github.daanbouwman.flightplanner.navigation.Destination
import com.github.daanbouwman.flightplanner.weather.WeatherRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One airport's worth of the full-screen detail: itself, its runways, and its weather. */
data class AirportDetailUiState(
    val airport: Airport? = null,
    val runways: List<Runway> = emptyList(),
    /** Null means not yet resolved — not necessarily unavailable. */
    val metar: Metar? = null,
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
    private val weatherRepository: WeatherRepository,
) : ViewModel() {

    private val route: Destination.AirportDetail = savedStateHandle.toRoute()

    private val _state = MutableStateFlow(AirportDetailUiState())
    val state: StateFlow<AirportDetailUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val airport = airportRepository.findById(route.airportId)
            val runways = airport?.let { airportRepository.runwaysFor(it.id) }.orEmpty()
            _state.value = AirportDetailUiState(airport = airport, runways = runways, loading = false)

            // Weather last, as on the route detail screen: the network-bound
            // step is published after the on-device queries, behind the
            // weather block's own reserved height.
            //
            // Guarded, like `PlanViewModel`'s batch fetch and `RouteDetailLoader`'s.
            // This is a bare `launch`, so anything `fetch` throws takes the process
            // down — and it is not only network: it reaches Room, and it builds a
            // URL, which throws `IllegalArgumentException` rather than `IOException`
            // on a malformed one. An airport with no weather is a legible screen.
            if (airport != null) {
                val metar = runCatchingCancellable { weatherRepository.fetch(listOf(airport.icao)) }
                    .onFailure { Log.w(TAG, "Fetching weather for ${airport.icao} failed", it) }
                    .getOrNull()
                    ?.get(airport.icao)
                _state.update { it.copy(metar = metar) }
            }
        }
    }
}

private const val TAG = "AirportDetailViewModel"

/**
 * `runCatching`, minus the part that swallows cancellation.
 *
 * The same helper `PlanViewModel` and the other ViewModels carry, file-private in
 * each for the same reason: `runCatching` catches `Throwable`, which includes
 * `CancellationException`, and swallowing that breaks structured concurrency — a
 * cancelled load would be reported as a failure and the coroutine would carry on
 * running past the point it was told to stop.
 */
private inline fun <T> runCatchingCancellable(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (failure: Throwable) {
    Result.failure(failure)
}
