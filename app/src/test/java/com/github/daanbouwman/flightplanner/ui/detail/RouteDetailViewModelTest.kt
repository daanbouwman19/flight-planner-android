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
    name = "$icao airport",
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

private fun runway(airportId: Int, ident: String) = Runway(
    id = airportId * 10 + ident.hashCode().mod(10),
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

private fun metar(station: String) = Metar(
    station = station,
    raw = "$station 121225Z 24012KT 9999 FEW040 18/09 Q1015",
    flightRules = FlightRules.VFR,
)

/**
 * Airports and runways, each read gated on demand so a stage of the load can be
 * held in flight while the state before it is inspected, and each read failing
 * on demand so the guards can be exercised.
 */
private class StagedAirportRepository(
    private val airports: List<Airport>,
    private val runways: Map<Int, List<Runway>> = emptyMap(),
    val runwayGate: CompletableDeferred<Unit> = CompletableDeferred(Unit),
    private val airportFailure: Throwable? = null,
    private val runwayFailure: Throwable? = null,
) : AirportRepository {
    override val nameIndexState: StateFlow<NameIndexState> = MutableStateFlow(NameIndexState.Idle)

    override suspend fun findByIcao(icao: String): Airport? {
        airportFailure?.let { throw it }
        return airports.firstOrNull { it.icao == icao }
    }

    override suspend fun runwaysFor(airportId: Int): List<Runway> {
        runwayGate.await()
        runwayFailure?.let { throw it }
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

private class FixedFleetRepository(private val fleet: List<AircraftSpec>) : FleetRepository {
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

private class StagedWeatherRepository(
    private val reports: Map<String, Metar>,
    val gate: CompletableDeferred<Unit> = CompletableDeferred(Unit),
    private val failure: Throwable? = null,
) : WeatherRepository {
    override suspend fun fetch(stations: List<String>): Map<String, Metar> {
        gate.await()
        failure?.let { throw it }
        return reports.filterKeys { it in stations }
    }
}

private val toKennedy = Destination.RouteDetail(
    departureIcao = "EHAM",
    destinationIcao = "KJFK",
    aircraftId = 7,
    distanceNm = 3163,
)

/**
 * The full-screen detail's ViewModel: three publishes in a fixed order, and a
 * loader that has failed underneath it leaves a legible screen rather than
 * taking the process down.
 *
 * The publishes are pinned one at a time by holding the runway read and the
 * weather fetch at gates, because on the JVM every fake read completes in the
 * same dispatch and a `StateFlow` would otherwise only ever show the last.
 */
class RouteDetailViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        airports: AirportRepository,
        weather: WeatherRepository = StagedWeatherRepository(emptyMap()),
        outline: () -> WorldOutline = { WorldOutline.Empty },
    ) = RouteDetailViewModel(
        route = toKennedy,
        loader = RouteDetailLoader(
            airportRepository = airports,
            fleetRepository = FixedFleetRepository(listOf(boeing)),
            worldOutlineLoader = { outline() },
            weatherRepository = weather,
        ),
    )

    @Test
    fun `the state leads with the arguments before anything has been read`() = runTest(dispatcher) {
        val model = viewModel(StagedAirportRepository(listOf(eham, kjfk)))

        // Synchronously, before the load has run: the distance came with the
        // route, and the screen is still a skeleton.
        val initial = model.state.value
        initial.distanceNm shouldBe 3163
        initial.loading shouldBe true
        initial.departure.shouldBeNull()
    }

    @Test
    fun `it publishes three times, in order - airports, then runways, then weather`() = runTest(dispatcher) {
        val airports = StagedAirportRepository(
            airports = listOf(eham, kjfk),
            runways = mapOf(1 to listOf(runway(1, "18R"), runway(1, "36L")), 2 to listOf(runway(2, "04L"))),
            runwayGate = CompletableDeferred(),
        )
        val weather = StagedWeatherRepository(
            reports = mapOf("EHAM" to metar("EHAM"), "KJFK" to metar("KJFK")),
            gate = CompletableDeferred(),
        )
        val model = viewModel(airports, weather)

        // First publish: the airports, the airframe and the leg's figures, with
        // the skeleton gone - and nothing the two later reads supply.
        advanceUntilIdle()
        val loaded = model.state.value
        loaded.loading shouldBe false
        loaded.departure shouldBe eham
        loaded.destination shouldBe kjfk
        loaded.aircraft shouldBe boeing
        loaded.flightTime.shouldNotBeNull()
        loaded.arc.shouldNotBeNull()
        loaded.initialBearingDeg.shouldNotBeNull()
        loaded.departureRunways.shouldBeEmpty()
        loaded.departureMetar.shouldBeNull()

        // Second publish: the runway lists, still no weather.
        airports.runwayGate.complete(Unit)
        advanceUntilIdle()
        val withRunways = model.state.value
        withRunways.departureRunways shouldHaveSize 2
        withRunways.destinationRunways shouldHaveSize 1
        withRunways.departureMetar.shouldBeNull()
        withRunways.destinationMetar.shouldBeNull()

        // Third publish: the weather, with everything before it intact.
        weather.gate.complete(Unit)
        advanceUntilIdle()
        val settled = model.state.value
        settled.departureMetar shouldBe metar("EHAM")
        settled.destinationMetar shouldBe metar("KJFK")
        settled.departureRunways shouldHaveSize 2
        settled.departure shouldBe eham
    }

    @Test
    fun `a throwing airport read degrades to an empty detail with the airframe intact`() = runTest(dispatcher) {
        val model = viewModel(
            StagedAirportRepository(listOf(eham, kjfk), airportFailure = IOException("database mid-install")),
        )

        advanceUntilIdle()

        val state = model.state.value
        // Nothing propagated out of the launch: the state settled, and settled
        // as "not loading" - a skeleton that never resolves is the worse state.
        state.loading shouldBe false
        state.departure.shouldBeNull()
        state.destination.shouldBeNull()
        state.arc.shouldBeNull()
        state.initialBearingDeg.shouldBeNull()
        // The fleet read was independent and still landed.
        state.aircraft shouldBe boeing
        state.distanceNm shouldBe 3163
    }

    @Test
    fun `a throwing runway read keeps the loaded airports and leaves the lists empty`() = runTest(dispatcher) {
        val model = viewModel(
            StagedAirportRepository(listOf(eham, kjfk), runwayFailure = IllegalStateException("corrupt page")),
        )

        advanceUntilIdle()

        val state = model.state.value
        state.departure shouldBe eham
        state.destination shouldBe kjfk
        state.departureRunways.shouldBeEmpty()
        state.destinationRunways.shouldBeEmpty()
        state.loading shouldBe false
    }

    @Test
    fun `a throwing weather fetch leaves the detail unweathered, not missing`() = runTest(dispatcher) {
        val airports = StagedAirportRepository(listOf(eham, kjfk), runways = mapOf(1 to listOf(runway(1, "18R"))))
        val model = viewModel(airports, StagedWeatherRepository(emptyMap(), failure = IllegalArgumentException("bad URL")))

        advanceUntilIdle()

        val state = model.state.value
        state.departure shouldBe eham
        state.departureRunways shouldHaveSize 1
        state.departureMetar.shouldBeNull()
        state.destinationMetar.shouldBeNull()
    }

    @Test
    fun `a throwing outline loader still leaves the airports and draws no coast`() = runTest(dispatcher) {
        val model = viewModel(
            StagedAirportRepository(listOf(eham, kjfk)),
            outline = { throw IOException("asset missing") },
        )

        advanceUntilIdle()

        val state = model.state.value
        state.departure shouldBe eham
        state.outline shouldBe WorldOutline.Empty
        state.loading shouldBe false
    }
}
