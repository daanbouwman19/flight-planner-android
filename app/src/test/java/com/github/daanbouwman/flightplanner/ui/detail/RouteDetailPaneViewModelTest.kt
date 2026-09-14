@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.github.daanbouwman.flightplanner.ui.detail

import com.github.daanbouwman.flightplanner.core.database.airport.AirportNameIndex
import com.github.daanbouwman.flightplanner.core.database.airport.NameIndexState
import com.github.daanbouwman.flightplanner.core.database.repository.AirportRepository
import com.github.daanbouwman.flightplanner.core.database.repository.FleetRepository
import com.github.daanbouwman.flightplanner.model.AircraftSpec
import com.github.daanbouwman.flightplanner.model.Airport
import com.github.daanbouwman.flightplanner.model.AirportSizeClass
import com.github.daanbouwman.flightplanner.model.Metar
import com.github.daanbouwman.flightplanner.model.Runway
import com.github.daanbouwman.flightplanner.navigation.Destination
import com.github.daanbouwman.flightplanner.routing.AirportIndex
import com.github.daanbouwman.flightplanner.routing.WorldOutline
import com.github.daanbouwman.flightplanner.weather.WeatherRepository
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import java.time.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

private fun airport(id: Int, icao: String, lat: Double, lon: Double) = Airport(
    id = id,
    icao = icao,
    name = icao,
    latitude = lat,
    longitude = lon,
    elevationFt = 0,
    country = "NL",
    municipality = icao,
    sizeClass = AirportSizeClass.LARGE,
    longestRunwayFt = 12467,
    runwayCount = 2,
    hasHardSurface = true,
    hasIcaoCode = true,
)

private val eham = airport(1, "EHAM", 52.3086, 4.7639)
private val kjfk = airport(2, "KJFK", 40.6398, -73.7789)
private val egll = airport(3, "EGLL", 51.4700, -0.4543)

private val boeing = AircraftSpec(
    id = 7,
    manufacturer = "Boeing",
    variant = "737-800",
    icaoCode = "B738",
    flown = false,
    rangeNm = 2935,
    category = "Jet",
    cruiseSpeedKt = 460,
    dateFlown = null,
    takeoffDistanceMeters = 2000,
)

/**
 * Airports by code, with an optional gate: a lookup for a gated code suspends
 * until the test releases it, which is how a load is held in flight long enough
 * to be cancelled or to be raced against a second selection.
 */
private class GatedAirportRepository(
    private val airports: List<Airport>,
    private val gates: Map<String, CompletableDeferred<Unit>> = emptyMap(),
) : AirportRepository {
    override val nameIndexState: StateFlow<NameIndexState> = MutableStateFlow(NameIndexState.Idle)

    override suspend fun findByIcao(icao: String): Airport? {
        gates[icao]?.await()
        return airports.firstOrNull { it.icao == icao }
    }

    override suspend fun findById(id: Int): Airport? = airports.firstOrNull { it.id == id }
    override suspend fun airportsByIds(ids: List<Int>): List<Airport> = airports.filter { it.id in ids }
    override suspend fun airportsByIdMap(ids: List<Int>): Map<Int, Airport> = airportsByIds(ids).associateBy { it.id }
    override suspend fun airportsByIcao(icaos: List<String>): List<Airport> = airports.filter { it.icao in icaos }
    override suspend fun airportsForSlots(index: AirportIndex, slots: IntArray): List<Airport> = emptyList()
    override suspend fun runwaysFor(airportId: Int): List<Runway> = emptyList()
    override fun prepareNameIndex(index: AirportIndex) = Unit
    override fun nameIndexOrNull(): AirportNameIndex? = null
}

private class PaneFleetRepository(private val fleet: List<AircraftSpec>) : FleetRepository {
    override fun observeFleet(): Flow<List<AircraftSpec>> = flowOf(fleet)
    override fun observeNotFlownCount(): Flow<Int> = flowOf(fleet.count { !it.flown })
    override suspend fun fleet(): List<AircraftSpec> = fleet
    override suspend fun byId(id: Int): AircraftSpec? = fleet.firstOrNull { it.id == id }
    override suspend fun count(): Int = fleet.size
    override suspend fun setFlown(id: Int, flown: Boolean, on: LocalDate) = Unit
    override suspend fun markAllNotFlown() = Unit
    override suspend fun add(spec: AircraftSpec): Int = 0
    override suspend fun update(spec: AircraftSpec) = Unit
    override suspend fun delete(spec: AircraftSpec) = Unit
    override suspend fun restoreDefaults(): Int = 0
    override suspend fun seedIfEmpty(): Int = 0
}

private object NoWeather : WeatherRepository {
    override suspend fun fetch(stations: List<String>): Map<String, Metar> = emptyMap()
}

private val toKennedy = Destination.RouteDetail(
    departureIcao = "EHAM",
    destinationIcao = "KJFK",
    aircraftId = 7,
    distanceNm = 3163,
)

private val toHeathrow = Destination.RouteDetail(
    departureIcao = "EHAM",
    destinationIcao = "EGLL",
    aircraftId = 7,
    distanceNm = 200,
)

/**
 * The pane's ViewModel: a selection is an identity, a load is its content, and
 * the two must not be allowed to disagree.
 *
 * The defect the cancellation tests exist for: choosing three routes quickly
 * used to leave three reads racing, and the pane settled on whichever database
 * call finished last rather than on the route the user was looking at.
 */
class RouteDetailPaneViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(gates: Map<String, CompletableDeferred<Unit>> = emptyMap()) = RouteDetailPaneViewModel(
        RouteDetailLoader(
            airportRepository = GatedAirportRepository(listOf(eham, kjfk, egll), gates),
            fleetRepository = PaneFleetRepository(listOf(boeing)),
            worldOutlineLoader = { WorldOutline.Empty },
            weatherRepository = NoWeather,
        ),
    )

    @Test
    fun `select publishes the route at once and the loaded detail after`() = runTest(dispatcher) {
        val model = viewModel()

        model.select(toKennedy)

        // Synchronously: the codes and the distance came with the selection, so
        // the pane can head itself before any database read has run.
        val immediate = model.state.value.shouldNotBeNull()
        immediate.route shouldBe toKennedy
        immediate.detail.distanceNm shouldBe 3163
        immediate.detail.departure.shouldBeNull()

        advanceUntilIdle()

        val loaded = model.state.value.shouldNotBeNull()
        loaded.route shouldBe toKennedy
        loaded.detail.departure shouldBe eham
        loaded.detail.destination shouldBe kjfk
        loaded.detail.aircraft shouldBe boeing
        loaded.detail.loading shouldBe false
    }

    @Test
    fun `a second selection cancels the first load, which never lands`() = runTest(dispatcher) {
        val kennedyGate = CompletableDeferred<Unit>()
        val model = viewModel(gates = mapOf("KJFK" to kennedyGate))

        model.select(toKennedy)
        advanceUntilIdle()
        // Held in flight on the destination lookup.
        model.state.value.shouldNotBeNull().detail.destination.shouldBeNull()

        model.select(toHeathrow)
        advanceUntilIdle()
        model.state.value.shouldNotBeNull().route shouldBe toHeathrow
        model.state.value.shouldNotBeNull().detail.destination shouldBe egll

        // The first read completes late. Had its coroutine still been alive it
        // would now publish Kennedy over Heathrow.
        kennedyGate.complete(Unit)
        advanceUntilIdle()

        val settled = model.state.value.shouldNotBeNull()
        settled.route shouldBe toHeathrow
        settled.detail.destination shouldBe egll
    }

    @Test
    fun `clear empties the pane and a load in flight cannot refill it`() = runTest(dispatcher) {
        val kennedyGate = CompletableDeferred<Unit>()
        val model = viewModel(gates = mapOf("KJFK" to kennedyGate))

        model.select(toKennedy)
        advanceUntilIdle()

        model.clear()
        model.state.value.shouldBeNull()

        kennedyGate.complete(Unit)
        advanceUntilIdle()
        model.state.value.shouldBeNull()
    }

    @Test
    fun `reselecting the same route is a fresh load, not a no-op`() = runTest(dispatcher) {
        val model = viewModel()
        model.select(toKennedy)
        advanceUntilIdle()

        model.select(toKennedy)

        // Back to the selection-only state: the pane's identity is unchanged, so
        // the cross-fade keyed on it does not fire, and the content re-reads.
        model.state.value.shouldNotBeNull().detail.departure.shouldBeNull()
        advanceUntilIdle()
        model.state.value.shouldNotBeNull().detail.departure shouldBe eham
    }
}
