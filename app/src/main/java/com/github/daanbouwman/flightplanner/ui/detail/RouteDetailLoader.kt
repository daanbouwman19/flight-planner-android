package com.github.daanbouwman.flightplanner.ui.detail

import android.util.Log
import com.github.daanbouwman.flightplanner.core.database.repository.AirportRepository
import com.github.daanbouwman.flightplanner.core.database.repository.FleetRepository
import com.github.daanbouwman.flightplanner.model.Airport
import com.github.daanbouwman.flightplanner.navigation.Destination
import com.github.daanbouwman.flightplanner.routing.GreatCircle
import com.github.daanbouwman.flightplanner.routing.RouteArc
import com.github.daanbouwman.flightplanner.weather.WeatherRepository
import com.github.daanbouwman.flightplanner.world.WorldOutlineLoader
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * Turns a route's navigation arguments back into a route.
 *
 * A generated route has no identity to look up — it exists only in the batch that
 * produced it — so the arguments carry two codes and an airframe id, and this
 * resolves them. It is a class rather than code inside the ViewModel for two
 * reasons: it is the part worth unit-testing, and the same load has to serve both
 * the detail *screen*, which is entered by navigation, and the detail *pane*,
 * which is entered by a selection in a list beside it.
 *
 * ### Two calls, not one
 *
 * [load] returns everything the screen leads with — the airports, the arc, the
 * two bearings — and [withRunways] adds the runway lists afterwards. Splitting
 * them is what lets the hero map and the leg's figures draw without waiting on
 * two further queries for detail nobody has scrolled to yet. [withWeather] is
 * a third, later step still: it is the network-bound one, so it is published
 * last, after the two on-device queries, and behind the weather panel's own
 * reserved height so nothing reflows when it lands.
 */
class RouteDetailLoader @Inject constructor(
    private val airportRepository: AirportRepository,
    private val fleetRepository: FleetRepository,
    private val worldOutlineLoader: WorldOutlineLoader,
    private val weatherRepository: WeatherRepository,
) {

    /** Everything the screen leads with. Two indexed lookups and the fleet row. */
    suspend fun load(route: Destination.RouteDetail): RouteDetailUiState {
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

        return RouteDetailUiState(
            departure = departure,
            destination = destination,
            aircraft = aircraft,
            distanceNm = route.distanceNm,
            flightTime = aircraft?.let {
                GreatCircle.flightTime(route.distanceNm.toDouble(), it.cruiseSpeedKt)
            },
            arc = arc,
            outline = worldOutlineLoader.load(),
            initialBearingDeg = bearing(departure, destination, GreatCircle::initialBearingDeg),
            finalBearingDeg = bearing(departure, destination, GreatCircle::finalBearingDeg),
            requiredRunwayFt = aircraft?.requiredRunwayFt ?: 0,
            loading = false,
        )
    }

    /** The runway lists for both ends of an already-loaded [state]. */
    suspend fun withRunways(state: RouteDetailUiState): RouteDetailUiState = state.copy(
        departureRunways = state.departure?.let { airportRepository.runwaysFor(it.id) }.orEmpty(),
        destinationRunways = state.destination?.let { airportRepository.runwaysFor(it.id) }.orEmpty(),
    )

    /**
     * Weather for both ends of an already-loaded [state]. A missing station is
     * simply absent from the result.
     *
     * **A failure returns the state unweathered rather than propagating**, which is
     * the same shape `PlanViewModel` gives its own batch fetch and the reason it is
     * here rather than at the two call sites: both of them assign the result
     * straight into a `MutableStateFlow` inside a bare `launch`, so anything thrown
     * takes the process down. `fetch` is not only network — it reaches Room, and it
     * builds a URL, which throws `IllegalArgumentException` rather than `IOException`
     * on a malformed one. A route detail with no weather is a legible screen; a
     * crash is not.
     */
    suspend fun withWeather(state: RouteDetailUiState): RouteDetailUiState {
        val stations = listOfNotNull(state.departure?.icao, state.destination?.icao)
        val fetched = if (stations.isEmpty()) {
            emptyMap()
        } else {
            runCatchingCancellable { weatherRepository.fetch(stations) }
                .onFailure { Log.w(TAG, "Fetching weather for $stations failed", it) }
                .getOrNull()
                .orEmpty()
        }
        return state.copy(
            departureMetar = state.departure?.icao?.let(fetched::get),
            destinationMetar = state.destination?.icao?.let(fetched::get),
        )
    }
}

/**
 * One bearing between two airports, rounded to a whole degree, or null when
 * either end is missing.
 *
 * The modulo is not redundant after rounding: 359.7° rounds to 360, and a heading
 * of 360 is written 0 — a compass rose has no 360th degree.
 */
private fun bearing(
    from: Airport?,
    to: Airport?,
    compute: (Double, Double, Double, Double) -> Double,
): Int? {
    if (from == null || to == null) return null
    return compute(from.latitude, from.longitude, to.latitude, to.longitude)
        .roundToInt()
        .mod(360)
}

private const val TAG = "RouteDetailLoader"

/**
 * `runCatching`, minus the part that swallows cancellation.
 *
 * The same helper `PlanViewModel` and four other ViewModels carry, and it is
 * file-private in each of them for the same reason: `runCatching` catches
 * `Throwable`, which includes `CancellationException`, and swallowing that breaks
 * structured concurrency — a cancelled load would be reported as a failure and the
 * coroutine would carry on running past the point it was told to stop.
 */
private inline fun <T> runCatchingCancellable(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (failure: Throwable) {
    Result.failure(failure)
}
