@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.github.daanbouwman.flightplanner.ui.logbook

import com.github.daanbouwman.flightplanner.core.database.repository.FleetRepository
import com.github.daanbouwman.flightplanner.core.database.repository.LogbookRepository
import com.github.daanbouwman.flightplanner.model.AircraftSpec
import com.github.daanbouwman.flightplanner.model.FlightRecord
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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

private class FakeLogbookRepository(initial: List<FlightRecord> = emptyList()) : LogbookRepository {
    val state = MutableStateFlow(initial)

    override fun observeAll(): Flow<List<FlightRecord>> = state
    override fun observeCount(): Flow<Int> = state.map { it.size }
    override suspend fun all(): List<FlightRecord> = state.value
    override suspend fun page(limit: Int, offset: Int): List<FlightRecord> = state.value.drop(offset).take(limit)
    override suspend fun add(record: FlightRecord): Long {
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

    override fun observeFleet(): Flow<List<AircraftSpec>> = state
    override fun observeNotFlownCount(): Flow<Int> = state.map { fleet -> fleet.count { !it.flown } }
    override suspend fun fleet(): List<AircraftSpec> = state.value
    override suspend fun byId(id: Int): AircraftSpec? = state.value.firstOrNull { it.id == id }
    override suspend fun count(): Int = state.value.size
    override suspend fun setFlown(id: Int, flown: Boolean, on: LocalDate) = Unit
    override suspend fun markAllNotFlown() = Unit
    override suspend fun add(spec: AircraftSpec): Int = spec.id
    override suspend fun update(spec: AircraftSpec) = Unit
    override suspend fun delete(spec: AircraftSpec) = Unit
    override suspend fun restoreDefaults(): Int = 0
    override suspend fun seedIfEmpty(): Int = 0
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
            defaultDispatcher = dispatcher,
        )
        // uiState is WhileSubscribed; collecting from backgroundScope keeps it hot
        // for the test and cancels it with the test, same as PlanViewModelTest.
        backgroundScope.launch(dispatcher) { model.uiState.collect {} }
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
}
