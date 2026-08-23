@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.github.daanbouwman.flightplanner.ui.stats

import com.github.daanbouwman.flightplanner.core.database.airport.AirportNameIndex
import com.github.daanbouwman.flightplanner.core.database.airport.NameIndexState
import com.github.daanbouwman.flightplanner.core.database.repository.AirportRepository
import com.github.daanbouwman.flightplanner.core.database.repository.FleetRepository
import com.github.daanbouwman.flightplanner.core.database.repository.LogbookRepository
import com.github.daanbouwman.flightplanner.model.AircraftSpec
import com.github.daanbouwman.flightplanner.model.Airport
import com.github.daanbouwman.flightplanner.model.AirportSizeClass
import com.github.daanbouwman.flightplanner.model.FlightRecord
import com.github.daanbouwman.flightplanner.model.Runway
import com.github.daanbouwman.flightplanner.routing.AirportIndex
import com.github.daanbouwman.flightplanner.routing.WorldOutline
import com.github.daanbouwman.flightplanner.world.WorldOutlineLoader
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import java.time.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

private fun spec(id: Int, name: String = "737-800") = AircraftSpec(
    id = id,
    manufacturer = "Boeing",
    variant = name,
    icaoCode = "B738",
    flown = true,
    rangeNm = 3000,
    category = "Jet",
    cruiseSpeedKt = 450,
    dateFlown = "2026-01-01",
    takeoffDistanceMeters = 2000,
)

private fun airport(id: Int, icao: String, name: String, lat: Double, lon: Double) = Airport(
    id = id,
    icao = icao,
    name = name,
    latitude = lat,
    longitude = lon,
    elevationFt = 0,
    country = "NL",
    municipality = name,
    sizeClass = AirportSizeClass.LARGE,
    longestRunwayFt = 12000,
    runwayCount = 2,
    hasHardSurface = true,
    hasIcaoCode = true,
)

private class FakeLogbookRepository(initial: List<FlightRecord> = emptyList()) : LogbookRepository {
    val state = MutableStateFlow(initial)
    override fun observeAll(): Flow<List<FlightRecord>> = state
    override fun observeCount(): Flow<Int> = state.map { it.size }
    override suspend fun all(): List<FlightRecord> = state.value
    override suspend fun page(limit: Int, offset: Int): List<FlightRecord> = state.value.drop(offset).take(limit)
    override suspend fun add(record: FlightRecord): Long {
        state.value = listOf(record) + state.value
        return record.id
    }
    override suspend fun delete(record: FlightRecord) {
        state.value = state.value.filterNot { it.id == record.id }
    }
    override suspend fun arrivalsForAircraft(aircraftId: Int): Set<String> =
        state.value.filter { it.aircraftId == aircraftId }.map { it.arrivalIcao }.toSet()
    override suspend fun clear() {
        state.value = emptyList()
    }
}

private class FakeFleetRepository(initial: List<AircraftSpec> = emptyList()) : FleetRepository {
    val state = MutableStateFlow(initial)
    override fun observeFleet(): Flow<List<AircraftSpec>> = state
    override fun observeNotFlownCount(): Flow<Int> = state.map { fleet -> fleet.count { !it.flown } }
    override suspend fun fleet(): List<AircraftSpec> = state.value
    override suspend fun byId(id: Int): AircraftSpec? = state.value.firstOrNull { it.id == id }
    override suspend fun count(): Int = state.value.size
    override suspend fun setFlown(id: Int, flown: Boolean, on: LocalDate) {}
    override suspend fun markAllNotFlown() {}
    override suspend fun add(spec: AircraftSpec): Int = spec.id
    override suspend fun update(spec: AircraftSpec) {}
    override suspend fun delete(spec: AircraftSpec) {}
    override suspend fun restoreDefaults(): Int = 0
    override suspend fun seedIfEmpty(): Int = 0
}

private class FakeAirportRepository(private val airports: List<Airport>) : AirportRepository {
    override val nameIndexState: StateFlow<NameIndexState> = MutableStateFlow(NameIndexState.Idle)
    override suspend fun findByIcao(icao: String): Airport? = airports.firstOrNull { it.icao.equals(icao, ignoreCase = true) }
    override suspend fun findById(id: Int): Airport? = airports.firstOrNull { it.id == id }
    override suspend fun airportsByIds(ids: List<Int>): List<Airport> = airports.filter { it.id in ids }
    override suspend fun airportsByIdMap(ids: List<Int>): Map<Int, Airport> =
        airports.filter { it.id in ids }.associateBy { it.id }
    override suspend fun airportsByIcao(icaos: List<String>): List<Airport> {
        val set = icaos.map { it.uppercase() }.toSet()
        return airports.filter { it.icao.uppercase() in set }
    }
    override suspend fun airportsForSlots(index: AirportIndex, slots: IntArray): List<Airport> =
        airportsByIds(slots.map { index.ids[it] })
    override suspend fun runwaysFor(airportId: Int): List<Runway> = emptyList()
    override fun prepareNameIndex(index: AirportIndex) = Unit
    override fun nameIndexOrNull(): AirportNameIndex? = null
}

private class FakeWorldOutlineLoader : WorldOutlineLoader {
    override suspend fun load(): WorldOutline = WorldOutline.Empty
}

class StatsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initial_emptyLogbook_emitsEmptyState() = testScope.runTest {
        val logbook = FakeLogbookRepository(emptyList())
        val fleet = FakeFleetRepository(listOf(spec(1)))
        val airportRepo = FakeAirportRepository(emptyList())
        val viewModel = StatsViewModel(logbook, fleet, airportRepo, FakeWorldOutlineLoader(), testDispatcher)

        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.uiState.value shouldBe StatsUiState.Empty
    }

    @Test
    fun withFlights_emitsSuccessDashboardWithCalculatedMetrics() = testScope.runTest {
        val airports = listOf(
            airport(1, "EHAM", "Amsterdam", 52.31, 4.76),
            airport(2, "EGLL", "London", 51.47, -0.45),
            airport(3, "KJFK", "New York", 40.64, -73.78),
        )
        val aircraft = listOf(
            spec(1, "737-800"),
            spec(2, "A320"),
        )
        val records = listOf(
            FlightRecord(1, "EHAM", "EGLL", 1, "2026-08-01", 200),
            FlightRecord(2, "EGLL", "KJFK", 2, "2026-08-05", 3000),
            FlightRecord(3, "KJFK", "EHAM", 1, "2026-08-10", 3160),
        )

        val logbook = FakeLogbookRepository(records)
        val fleet = FakeFleetRepository(aircraft)
        val airportRepo = FakeAirportRepository(airports)
        val viewModel = StatsViewModel(logbook, fleet, airportRepo, FakeWorldOutlineLoader(), testDispatcher)

        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        state.shouldBeInstanceOf<StatsUiState.Success>()
        state.totalFlights shouldBe 3
        state.totalDistanceNm shouldBe 6360
        state.averageDistanceNm shouldBe 2120.0
        state.longestFlight?.departureIcao shouldBe "KJFK"
        state.longestFlight?.arrivalIcao shouldBe "EHAM"
        state.shortestFlight?.departureIcao shouldBe "EHAM"
        state.shortestFlight?.arrivalIcao shouldBe "EGLL"
        state.topAircraft.size shouldBe 2
        state.topAircraft.first().aircraft.id shouldBe 1
        state.topAircraft.first().flightCount shouldBe 2
        state.visitedAirports.size shouldBe 3
    }

    @Test
    fun timeframeAndMetricToggles_updateUiState() = testScope.runTest {
        val records = listOf(
            FlightRecord(1, "EHAM", "EGLL", 1, "2026-08-01", 200),
            FlightRecord(2, "EGLL", "KJFK", 1, "2024-01-01", 3000),
        )
        val logbook = FakeLogbookRepository(records)
        val fleet = FakeFleetRepository(listOf(spec(1)))
        val airportRepo = FakeAirportRepository(emptyList())
        val viewModel = StatsViewModel(logbook, fleet, airportRepo, FakeWorldOutlineLoader(), testDispatcher)

        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        val allTimeState = viewModel.uiState.value as StatsUiState.Success
        allTimeState.totalFlights shouldBe 2

        viewModel.setTimeframe(StatsTimeframe.THIS_YEAR)
        advanceUntilIdle()

        val thisYearState = viewModel.uiState.value as StatsUiState.Success
        thisYearState.totalFlights shouldBe 1
        thisYearState.totalDistanceNm shouldBe 200

        viewModel.setChartMetric(ChartMetric.DISTANCE)
        advanceUntilIdle()

        val distanceState = viewModel.uiState.value as StatsUiState.Success
        distanceState.chartMetric shouldBe ChartMetric.DISTANCE
    }
}

