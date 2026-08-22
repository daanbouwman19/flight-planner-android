@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.github.daanbouwman.flightplanner.ui.logbook

import com.github.daanbouwman.flightplanner.core.database.airport.AirportNameIndex
import com.github.daanbouwman.flightplanner.core.database.airport.IndexLoadTiming
import com.github.daanbouwman.flightplanner.core.database.airport.NameIndexState
import com.github.daanbouwman.flightplanner.core.database.repository.AirportRepository
import com.github.daanbouwman.flightplanner.core.database.repository.FleetRepository
import com.github.daanbouwman.flightplanner.core.database.repository.LogbookRepository
import com.github.daanbouwman.flightplanner.index.AirportIndexProvider
import com.github.daanbouwman.flightplanner.index.IndexState
import com.github.daanbouwman.flightplanner.model.AircraftSpec
import com.github.daanbouwman.flightplanner.model.Airport
import com.github.daanbouwman.flightplanner.model.AirportSizeClass
import com.github.daanbouwman.flightplanner.model.FlightRecord
import com.github.daanbouwman.flightplanner.model.Runway
import com.github.daanbouwman.flightplanner.routing.AirportIndex
import com.github.daanbouwman.flightplanner.routing.AirportIndexBuilder
import com.github.daanbouwman.flightplanner.routing.GreatCircle
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
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

private fun spec(id: Int, cruiseSpeedKt: Int = 450) = AircraftSpec(
    id = id,
    manufacturer = "Boeing",
    variant = "737-800",
    icaoCode = "B738",
    flown = true,
    rangeNm = 3000,
    category = "Jet",
    cruiseSpeedKt = cruiseSpeedKt,
    dateFlown = "2026-01-01",
    takeoffDistanceMeters = 2000,
)

private fun record(id: Long, date: String, aircraftId: Int = 1, distanceNm: Int? = 500) = FlightRecord(
    id = id,
    departureIcao = "EHAM",
    arrivalIcao = "EGLL",
    aircraftId = aircraftId,
    date = date,
    distanceNm = distanceNm,
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

/** A small synthetic world for the add-flight sheet's search flows — same shape as PlanViewModelTest's own fixture. */
private val airports = listOf(
    airport(1, "EHAM", "Amsterdam", 52.31, 4.76),
    airport(2, "EGLL", "London", 51.47, -0.45),
    airport(3, "KJFK", "New York", 40.64, -73.78),
)

private val airportIndex: AirportIndex = AirportIndexBuilder(airports.size).apply {
    airports.forEach { a ->
        add(
            id = a.id,
            icao = a.icao,
            latitude = a.latitude,
            longitude = a.longitude,
            longestRunway = a.longestRunwayFt,
            packedFlags = AirportIndex.packFlags(hasIcao = true, hardSurface = true, lighting = true, sizeClass = a.sizeClass),
        )
    }
}.build()

private class FakeLogbookRepository(initial: List<FlightRecord> = emptyList()) : LogbookRepository {
    val state = MutableStateFlow(initial)

    /** Set to make [add] throw, so the failure path can be exercised. */
    var addShouldFail = false

    override fun observeAll(): Flow<List<FlightRecord>> = state
    override fun observeCount(): Flow<Int> = state.map { it.size }
    override suspend fun all(): List<FlightRecord> = state.value
    override suspend fun page(limit: Int, offset: Int): List<FlightRecord> = state.value.drop(offset).take(limit)
    override suspend fun add(record: FlightRecord): Long {
        if (addShouldFail) error("simulated write failure")
        state.value = state.value + record
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

    /** How many times [setFlown] has been called — pins that add-flight never calls it. */
    var setFlownCalls = 0
        private set

    override fun observeFleet(): Flow<List<AircraftSpec>> = state
    override fun observeNotFlownCount(): Flow<Int> = state.map { fleet -> fleet.count { !it.flown } }
    override suspend fun fleet(): List<AircraftSpec> = state.value
    override suspend fun byId(id: Int): AircraftSpec? = state.value.firstOrNull { it.id == id }
    override suspend fun count(): Int = state.value.size
    override suspend fun setFlown(id: Int, flown: Boolean, on: LocalDate) {
        setFlownCalls++
    }
    override suspend fun markAllNotFlown() = Unit
    override suspend fun add(spec: AircraftSpec): Int = spec.id
    override suspend fun update(spec: AircraftSpec) = Unit
    override suspend fun delete(spec: AircraftSpec) = Unit
    override suspend fun restoreDefaults(): Int = 0
    override suspend fun seedIfEmpty(): Int = 0
}

private class FakeAirportRepository : AirportRepository {
    override val nameIndexState: StateFlow<NameIndexState> = MutableStateFlow(NameIndexState.Idle)

    override suspend fun findByIcao(icao: String): Airport? = airports.firstOrNull { it.icao == icao }
    override suspend fun findById(id: Int): Airport? = airports.firstOrNull { it.id == id }
    override suspend fun airportsByIds(ids: List<Int>): List<Airport> = airports.filter { it.id in ids }
    override suspend fun airportsByIdMap(ids: List<Int>): Map<Int, Airport> =
        airports.filter { it.id in ids }.associateBy { it.id }

    override suspend fun airportsByIcao(icaos: List<String>): List<Airport> =
        icaos.mapNotNull { code -> airports.firstOrNull { it.icao == code } }

    override suspend fun airportsForSlots(index: AirportIndex, slots: IntArray): List<Airport> =
        airportsByIds(slots.map { index.ids[it] })

    override suspend fun runwaysFor(airportId: Int): List<Runway> = emptyList()
    override fun prepareNameIndex(index: AirportIndex) = Unit
    override fun nameIndexOrNull(): AirportNameIndex? = null
}

private class TestIndexProvider(private val index: AirportIndex) : AirportIndexProvider {
    override val state: StateFlow<IndexState> = MutableStateFlow(
        IndexState.Ready(index, IndexLoadTiming(readMillis = 0, decodeMillis = 0, airports = index.size)),
    )

    override suspend fun get(): AirportIndex = index
    override fun warm() = Unit
    override fun retry() = Unit
    override val readyOrNull: AirportIndex? get() = index
    override val isSettled: Boolean get() = true
}

class LogbookViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun logbookTest(
        records: List<FlightRecord> = emptyList(),
        fleet: List<AircraftSpec> = listOf(spec(1)),
        body: suspend TestScope.(LogbookViewModel, FakeLogbookRepository, FakeFleetRepository) -> Unit,
    ) = runTest(dispatcher) {
        val logbook = FakeLogbookRepository(records)
        val fleetRepository = FakeFleetRepository(fleet)
        val model = LogbookViewModel(
            logbookRepository = logbook,
            fleetRepository = fleetRepository,
            airportRepository = FakeAirportRepository(),
            indexProvider = TestIndexProvider(airportIndex),
            defaultDispatcher = dispatcher,
        )
        // uiState and the add-flight search flows are all WhileSubscribed;
        // collecting from backgroundScope keeps them hot for the test and
        // cancels them with the test, same as PlanViewModelTest.
        backgroundScope.launch(dispatcher) { model.uiState.collect {} }
        backgroundScope.launch(dispatcher) { model.addFlightAircraftResults.collect {} }
        backgroundScope.launch(dispatcher) { model.addFlightDepartureResults.collect {} }
        backgroundScope.launch(dispatcher) { model.addFlightDestinationResults.collect {} }
        backgroundScope.launch(dispatcher) { model.addFlightSearchScope.collect {} }
        advanceUntilIdle()
        body(model, logbook, fleetRepository)
    }

    @Test
    fun `starts loading and resolves to ready once the flows have emitted`() = logbookTest { model, _, _ ->
        model.uiState.value.status shouldBe LogbookStatus.Ready
    }

    @Test
    fun `an empty logbook is ready with no groups`() = logbookTest { model, _, _ ->
        model.uiState.value.isEmpty shouldBe true
    }

    @Test
    fun `rows join their aircraft's display name from the fleet`() = logbookTest(
        records = listOf(record(1, "2026-05-01")),
    ) { model, _, _ ->
        val row = model.uiState.value.groups.single().rows.single()
        row.aircraftDisplayName shouldBe "Boeing 737-800"
    }

    @Test
    fun `a flight whose aircraft is not in the fleet still appears`() = logbookTest(
        records = listOf(record(1, "2026-05-01", aircraftId = 99)),
        fleet = emptyList(),
    ) { model, _, _ ->
        val row = model.uiState.value.groups.single().rows.single()
        row.aircraftDisplayName shouldBe null
    }

    @Test
    fun `uiState recomputes when the logbook gains a flight`() = logbookTest { model, logbook, _ ->
        model.uiState.value.groups.isEmpty() shouldBe true

        logbook.state.value = logbook.state.value + record(1, "2026-05-01")
        advanceUntilIdle()

        model.uiState.value.groups.single().rows.single().id shouldBe 1L
    }

    @Test
    fun `uiState recomputes when the fleet changes`() = logbookTest(
        records = listOf(record(1, "2026-05-01")),
        fleet = emptyList(),
    ) { model, _, fleetRepository ->
        model.uiState.value.groups.single().rows.single().aircraftDisplayName shouldBe null

        fleetRepository.state.value = listOf(spec(1))
        advanceUntilIdle()

        model.uiState.value.groups.single().rows.single().aircraftDisplayName shouldBe "Boeing 737-800"
    }

    @Test
    fun `the summary reflects only this year's flights`() = logbookTest(
        records = listOf(
            record(1, "${LocalDate.now().year}-01-01", distanceNm = 400),
            record(2, "2019-01-01", distanceNm = 900),
        ),
    ) { model, _, _ ->
        model.uiState.value.summary.flights shouldBe 1
        model.uiState.value.summary.distanceNm shouldBe 400
    }

    @Test
    fun `addFlight inserts one record with the live great-circle distance and does not mark the aircraft flown`() =
        logbookTest { model, logbook, fleetRepository ->
            val departure = airports[0]
            val destination = airports[2]
            val expectedDistance = GreatCircle.distanceNm(
                departure.latitude,
                departure.longitude,
                destination.latitude,
                destination.longitude,
            )

            model.addFlight(spec(1), departure, destination, LocalDate.of(2026, 1, 5), expectedDistance)
            advanceUntilIdle()

            logbook.state.value shouldBe listOf(
                FlightRecord(
                    id = 0,
                    departureIcao = departure.icao,
                    arrivalIcao = destination.icao,
                    aircraftId = 1,
                    date = "2026-01-05",
                    distanceNm = expectedDistance,
                ),
            )
            // The invariant this test exists to pin: a manual add-flight is only
            // a logbook write, never fleetRepository.setFlown — see LogbookViewModel.addFlight.
            fleetRepository.setFlownCalls shouldBe 0
        }

    @Test
    fun `a failed write emits FlightAddFailed and inserts nothing`() = logbookTest { model, logbook, _ ->
        logbook.addShouldFail = true

        model.addFlight(spec(1), airports[0], airports[1], LocalDate.now(), distanceNm = 100)
        advanceUntilIdle()

        // The events channel is conflated, so the value sent above is still
        // buffered — first() reads it without needing a collector racing the
        // producer coroutine.
        model.events.first() shouldBe LogbookEvent.FlightAddFailed
        logbook.state.value shouldBe emptyList()
    }

    @Test
    fun `the departure and destination search flows are independent`() = logbookTest { model, _, _ ->
        model.setAddFlightDepartureQuery("EHAM")
        model.setAddFlightDestinationQuery("KJFK")
        advanceUntilIdle()

        model.addFlightDepartureResults.value.map { it.icao } shouldBe listOf("EHAM")
        model.addFlightDestinationResults.value.map { it.icao } shouldBe listOf("KJFK")
    }
}
