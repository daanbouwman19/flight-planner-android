@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.github.daanbouwman.flightplanner.ui.detail

import com.github.daanbouwman.flightplanner.core.database.airport.AirportNameIndex
import com.github.daanbouwman.flightplanner.core.database.airport.NameIndexState
import com.github.daanbouwman.flightplanner.core.database.repository.AirportRepository
import com.github.daanbouwman.flightplanner.core.database.repository.FleetRepository
import com.github.daanbouwman.flightplanner.model.AircraftSpec
import com.github.daanbouwman.flightplanner.model.Airport
import com.github.daanbouwman.flightplanner.model.AirportSizeClass
import com.github.daanbouwman.flightplanner.model.FlightRules
import com.github.daanbouwman.flightplanner.model.Metar
import com.github.daanbouwman.flightplanner.model.Runway
import com.github.daanbouwman.flightplanner.model.SurfaceKind
import com.github.daanbouwman.flightplanner.navigation.Destination
import com.github.daanbouwman.flightplanner.routing.AirportIndex
import com.github.daanbouwman.flightplanner.routing.WorldOutline
import com.github.daanbouwman.flightplanner.weather.WeatherRepository
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
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
import java.io.IOException
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
    /** Runways per airport id; the read waits at [runwayGate] first. */
    private val runways: Map<Int, List<Runway>> = emptyMap(),
    val runwayGate: CompletableDeferred<Unit> = CompletableDeferred(Unit),
    /** Thrown by every airport lookup, to exercise the loader's guards. */
    private val airportFailure: Throwable? = null,
) : AirportRepository {
    override val nameIndexState: StateFlow<NameIndexState> = MutableStateFlow(NameIndexState.Idle)

    override suspend fun findByIcao(icao: String): Airport? {
        gates[icao]?.await()
        airportFailure?.let { throw it }
        return airports.firstOrNull { it.icao == icao }
    }

    override suspend fun runwaysFor(airportId: Int): List<Runway> {
        runwayGate.await()
        return runways[airportId].orEmpty()
    }

    override suspend fun findById(id: Int): Airport? = airports.firstOrNull { it.id == id }
    override suspend fun airportsByIds(ids: List<Int>): List<Airport> = airports.filter { it.id in ids }
    override suspend fun airportsByIdMap(ids: List<Int>): Map<Int, Airport> = airportsByIds(ids).associateBy { it.id }
    override suspend fun airportsByIcao(icaos: List<String>): List<Airport> = airports.filter { it.icao in icaos }
    override suspend fun airportsForSlots(index: AirportIndex, slots: IntArray): List<Airport> = emptyList()
    override fun prepareNameIndex(index: AirportIndex) = Unit
    override fun nameIndexOrNull(): AirportNameIndex? = null
}

private fun runway(airportId: Int, ident: String) = Runway(
    id = airportId * 10 + ident.length,
    airportId = airportId,
    ident = ident,
    trueHeadingDeg = 183.0,
    lengthFt = 12467,
    widthFt = 148,
    surface = "ASP",
    surfaceKind = SurfaceKind.HARD,
    latitude = null,
    longitude = null,
    elevationFt = 0,
    lighted = true,
)

/** Weather held at a gate, so the third publish can be told apart from the second. */
private class GatedWeatherRepository(
    private val reports: Map<String, Metar>,
    val gate: CompletableDeferred<Unit> = CompletableDeferred(Unit),
) : WeatherRepository {
    override suspend fun fetch(stations: List<String>): Map<String, Metar> {
        gate.await()
        return reports.filterKeys { it in stations }
    }
}

private fun metar(station: String) = Metar(
    station = station,
    raw = "$station 121225Z 24012KT 9999 FEW040 18/09 Q1015",
    flightRules = FlightRules.VFR,
)

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

    private fun viewModel(
        gates: Map<String, CompletableDeferred<Unit>> = emptyMap(),
        airports: AirportRepository = GatedAirportRepository(listOf(eham, kjfk, egll), gates),
        weather: WeatherRepository = NoWeather,
        remembered: MutableList<Destination.RouteDetail> = mutableListOf(),
    ) = RouteDetailPaneViewModel(
        loader = RouteDetailLoader(
            airportRepository = airports,
            fleetRepository = PaneFleetRepository(listOf(boeing)),
            worldOutlineLoader = { WorldOutline.Empty },
            weatherRepository = weather,
        ),
        rememberLastRoute = { remembered += it },
    )

    @Test
    fun `each selection is recorded as the last route, clearing is not`() = runTest(dispatcher) {
        val remembered = mutableListOf<Destination.RouteDetail>()
        val model = viewModel(remembered = remembered)

        model.select(toKennedy)
        model.clear()
        val toHeathrow = toKennedy.copy(destinationIcao = "EGLL")
        model.select(toHeathrow)
        advanceUntilIdle()

        remembered shouldBe listOf(toKennedy, toHeathrow)
    }

    @Test
    fun `a selection publishes three times - airports, then runways, then weather - under one identity`() =
        runTest(dispatcher) {
            val airports = GatedAirportRepository(
                airports = listOf(eham, kjfk, egll),
                runways = mapOf(1 to listOf(runway(1, "18R"), runway(1, "36L")), 2 to listOf(runway(2, "04L"))),
                runwayGate = CompletableDeferred(),
            )
            val weather = GatedWeatherRepository(
                reports = mapOf("EHAM" to metar("EHAM"), "KJFK" to metar("KJFK")),
                gate = CompletableDeferred(),
            )
            val model = viewModel(airports = airports, weather = weather)

            model.select(toKennedy)
            advanceUntilIdle()
            val loaded = model.state.value.shouldNotBeNull()
            loaded.route shouldBe toKennedy
            loaded.detail.departure shouldBe eham
            loaded.detail.loading shouldBe false
            loaded.detail.departureRunways.shouldBeEmpty()
            loaded.detail.departureMetar.shouldBeNull()

            airports.runwayGate.complete(Unit)
            advanceUntilIdle()
            val withRunways = model.state.value.shouldNotBeNull()
            withRunways.route shouldBe toKennedy
            withRunways.detail.departureRunways shouldHaveSize 2
            withRunways.detail.destinationRunways shouldHaveSize 1
            withRunways.detail.departureMetar.shouldBeNull()

            weather.gate.complete(Unit)
            advanceUntilIdle()
            val settled = model.state.value.shouldNotBeNull()
            settled.route shouldBe toKennedy
            settled.detail.departureMetar shouldBe metar("EHAM")
            settled.detail.destinationMetar shouldBe metar("KJFK")
            settled.detail.departureRunways shouldHaveSize 2
        }

    @Test
    fun `a throwing airport read degrades the pane instead of propagating out of the selection`() =
        runTest(dispatcher) {
            val model = viewModel(
                airports = GatedAirportRepository(
                    airports = listOf(eham, kjfk),
                    airportFailure = IOException("database mid-install"),
                ),
            )

            model.select(toKennedy)
            advanceUntilIdle()

            // The identity is intact and the content settled as "loaded, empty":
            // the pane heads itself from the selection and shows no skeleton
            // forever, and nothing escaped the launch.
            val state = model.state.value.shouldNotBeNull()
            state.route shouldBe toKennedy
            state.detail.loading shouldBe false
            state.detail.departure.shouldBeNull()
            state.detail.destination.shouldBeNull()
            state.detail.aircraft shouldBe boeing
            state.detail.distanceNm shouldBe 3163

            // And the pane is still usable: the next selection loads normally.
            model.clear()
            model.state.value.shouldBeNull()
        }

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
