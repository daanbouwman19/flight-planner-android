@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.github.daanbouwman.flightplanner.startup

import com.github.daanbouwman.flightplanner.core.database.airport.AirportDao
import com.github.daanbouwman.flightplanner.core.database.airport.AirportDisplayRow
import com.github.daanbouwman.flightplanner.core.database.airport.AirportEntity
import com.github.daanbouwman.flightplanner.core.database.airport.AirportNameRow
import com.github.daanbouwman.flightplanner.core.database.airport.DatasetMetaDao
import com.github.daanbouwman.flightplanner.core.database.airport.DatasetMetaEntity
import com.github.daanbouwman.flightplanner.core.database.airport.IndexLoadTiming
import com.github.daanbouwman.flightplanner.core.database.airport.LoadedIndex
import com.github.daanbouwman.flightplanner.core.database.airport.RunwayDao
import com.github.daanbouwman.flightplanner.core.database.airport.RunwayEntity
import com.github.daanbouwman.flightplanner.core.database.user.AircraftDao
import com.github.daanbouwman.flightplanner.core.database.user.AircraftEntity
import com.github.daanbouwman.flightplanner.feature.globe.FilamentReport
import com.github.daanbouwman.flightplanner.feature.globe.GlobeStatus
import com.github.daanbouwman.flightplanner.model.AirportSizeClass
import com.github.daanbouwman.flightplanner.model.DatasetMetaKeys
import com.github.daanbouwman.flightplanner.routing.AirportIndex
import com.github.daanbouwman.flightplanner.routing.AirportIndexBuilder
import com.github.daanbouwman.flightplanner.startup.CheckResult.Status
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * A small real index, dense enough that the generator finds routes for the
 * fleet below: the route-generation check runs the real `RouteGenerator`.
 */
private data class Field(val id: Int, val icao: String, val lat: Double, val lon: Double, val runwayFt: Int)

private val fields = listOf(
    Field(1, "EHAM", 52.31, 4.76, 12467),
    Field(2, "EGLL", 51.47, -0.45, 12799),
    Field(3, "EDDF", 50.03, 8.57, 13123),
    Field(4, "LFPG", 49.01, 2.55, 13829),
    Field(5, "EKCH", 55.62, 12.66, 11811),
)

private val index: AirportIndex = AirportIndexBuilder(fields.size).apply {
    fields.forEach { field ->
        add(
            id = field.id,
            icao = field.icao,
            latitude = field.lat,
            longitude = field.lon,
            longestRunway = field.runwayFt,
            packedFlags = AirportIndex.packFlags(
                hasIcao = true,
                hardSurface = true,
                lighting = true,
                sizeClass = AirportSizeClass.LARGE,
            ),
        )
    }
}.build()

private fun aircraft(id: Int, range: Int) = AircraftEntity(
    id = id,
    manufacturer = "Boeing",
    variant = "737-$id",
    icaoCode = "B73$id",
    flown = false,
    rangeNm = range,
    category = "Jet",
    cruiseSpeedKt = 450,
    dateFlown = null,
    takeoffDistanceM = 2000,
)

/** Counts only; the check reads nothing else from this DAO. */
private class FakeAirportDao(private val airports: Int, private val withIcao: Int = airports) : AirportDao {
    override suspend fun count(): Int = airports
    override suspend fun countWithIcao(): Int = withIcao
    override suspend fun loadAllForIndex(): List<AirportEntity> = error("not read by the self-check")
    override suspend fun findByIcao(icao: String): AirportEntity? = null
    override suspend fun findById(id: Int): AirportEntity? = null
    override suspend fun displayRowsByIds(ids: List<Int>): List<AirportDisplayRow> = emptyList()
    override suspend fun displayRowsByIcao(icaos: List<String>): List<AirportDisplayRow> = emptyList()
    override suspend fun nameRows(): List<AirportNameRow> = emptyList()
}

private class FakeRunwayDao(private val ends: Int) : RunwayDao {
    override suspend fun count(): Int = ends
    override suspend fun forAirport(airportId: Int): List<RunwayEntity> = emptyList()
    override suspend fun countOrphans(): Int = 0
}

private class FakeDatasetMetaDao(private val values: Map<String, String>) : DatasetMetaDao {
    override suspend fun value(key: String): String? = values[key]
    override suspend fun all(): List<DatasetMetaEntity> = values.map { (k, v) -> DatasetMetaEntity(k, v) }
}

private class FakeAircraftDao(private val rows: List<AircraftEntity>) : AircraftDao {
    override fun observeAll(): Flow<List<AircraftEntity>> = flowOf(rows)
    override suspend fun all(): List<AircraftEntity> = rows
    override suspend fun byId(id: Int): AircraftEntity? = rows.firstOrNull { it.id == id }
    override suspend fun count(): Int = rows.size
    override fun observeNotFlownCount(): Flow<Int> = flowOf(rows.count { !it.flown })
    override suspend fun insertAll(rows: List<AircraftEntity>) = Unit
    override suspend fun upsert(row: AircraftEntity): Long = 0
    override suspend fun update(row: AircraftEntity) = Unit
    override suspend fun delete(row: AircraftEntity) = Unit
    override suspend fun setFlown(id: Int, flown: Boolean, dateFlown: String?) = Unit
    override suspend fun markAllNotFlown() = Unit
    override suspend fun deleteSeeded() = Unit
    override suspend fun deleteAll() = Unit
}

private val vulkanReport = FilamentReport(
    support = GlobeStatus.Available,
    nativeLibraryLoaded = true,
    activeBackend = "VULKAN",
    requestedBackend = "VULKAN",
    featureLevel = "FEATURE_LEVEL_3",
)

private val openGlReport = vulkanReport.copy(activeBackend = "OPENGL")

private val quickTiming = IndexLoadTiming(readMillis = 3, decodeMillis = 4, airports = fields.size)

/**
 * The self-check's ViewModel against fakes. The case it exists for: a database
 * that cannot be opened must become a FAIL row on the screen whose job is to
 * report exactly that, rather than fail the ViewModel's construction and take
 * the screen with it — which is why the DAOs are deferred (`Lazy` in Hilt's
 * graph, a function here) and resolved inside the check.
 */
class StartupCheckViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        airportDao: () -> AirportDao = { FakeAirportDao(airports = fields.size) },
        runwayDao: () -> RunwayDao = { FakeRunwayDao(ends = 10) },
        fleet: List<AircraftEntity> = listOf(aircraft(1, range = 300), aircraft(2, range = 2900)),
        seeded: Int = 0,
        loadIndex: suspend () -> LoadedIndex = { LoadedIndex(index, quickTiming) },
        probe: () -> FilamentReport = { vulkanReport },
    ) = StartupCheckViewModel(
        airportDao = airportDao,
        runwayDao = runwayDao,
        datasetMetaDao = {
            FakeDatasetMetaDao(mapOf(DatasetMetaKeys.FILTER_TIER to "medium", DatasetMetaKeys.UPSTREAM_MODIFIED to "2026-08-01"))
        },
        aircraftDao = FakeAircraftDao(fleet),
        seedFleet = { seeded },
        loadIndex = loadIndex,
        probeFilament = probe,
        ioDispatcher = dispatcher,
        defaultDispatcher = dispatcher,
    )

    private fun StartupUiState.row(name: String): CheckResult? = checks.firstOrNull { it.name == name }

    @Test
    fun `a healthy device passes all five checks and the headline counts nothing`() = runTest(dispatcher) {
        val model = viewModel()
        model.uiState.value.finished shouldBe false

        advanceUntilIdle()

        val state = model.uiState.value
        state.finished shouldBe true
        state.checks shouldHaveSize 5
        state.checks.map { it.name } shouldBe listOf(
            "Airport database", "In-memory index", "Fleet", "Route generation", "Filament (3D globe)",
        )
        state.checks.all { it.status == Status.PASS } shouldBe true
        state.failures shouldBe 0
        state.warnings shouldBe 0
        state.row("Airport database").shouldNotBeNull().detail shouldContain "5 airports (5 with ICAO), 10 runway ends"
        state.row("Airport database").shouldNotBeNull().detail shouldContain "tier medium, snapshot 2026-08-01"
        state.row("Fleet").shouldNotBeNull().detail shouldBe "2 aircraft already present"
        state.row("Route generation").shouldNotBeNull().detail shouldContain "Boeing 737-1 (300 NM)"
        // The range is `%,d` in the device locale, so its grouping is not asserted.
        state.row("Route generation").shouldNotBeNull().detail shouldContain "Boeing 737-2 ("
        state.row("Route generation").shouldNotBeNull().detail shouldContain "50 routes in"
    }

    @Test
    fun `a database that cannot be opened is a FAIL row, and the checks after it still run`() = runTest(dispatcher) {
        // Construction must not throw: resolving the DAO is the thing that
        // fails, and it is deferred into the check for exactly this reason.
        val model = viewModel(airportDao = { throw IllegalStateException("airport database install failed") })

        advanceUntilIdle()

        val state = model.uiState.value
        state.finished shouldBe true
        val database = state.row("Airport database").shouldNotBeNull()
        database.status shouldBe Status.FAIL
        database.detail shouldBe "airport database install failed"
        // No index without a database, and no routes without an index - but
        // the fleet and the renderer are independent and are still reported.
        state.row("In-memory index").shouldBeNull()
        state.row("Fleet").shouldNotBeNull().status shouldBe Status.PASS
        state.row("Route generation").shouldNotBeNull().status shouldBe Status.FAIL
        state.row("Route generation").shouldNotBeNull().detail shouldBe "skipped: database or fleet unavailable"
        state.row("Filament (3D globe)").shouldNotBeNull().status shouldBe Status.PASS
        state.failures shouldBe 2
    }

    @Test
    fun `a database that opens empty is a failure, not a pass with zeros`() = runTest(dispatcher) {
        val model = viewModel(airportDao = { FakeAirportDao(airports = 0) })

        advanceUntilIdle()

        val database = model.uiState.value.row("Airport database").shouldNotBeNull()
        database.status shouldBe Status.FAIL
        database.detail shouldBe "opened but contains no airports"
        model.uiState.value.row("In-memory index").shouldBeNull()
    }

    @Test
    fun `a slow index load is a warning, and the OpenGL fallback is a note rather than a failure`() = runTest(dispatcher) {
        val model = viewModel(
            loadIndex = { LoadedIndex(index, IndexLoadTiming(readMillis = 30, decodeMillis = 30, airports = fields.size)) },
            probe = { openGlReport },
        )

        advanceUntilIdle()

        val state = model.uiState.value
        state.row("In-memory index").shouldNotBeNull().status shouldBe Status.WARN
        state.row("Filament (3D globe)").shouldNotBeNull().status shouldBe Status.WARN
        // The headline's inputs: two notes, nothing failed.
        state.failures shouldBe 0
        state.warnings shouldBe 2
    }

    @Test
    fun `a device the globe has ruled out fails the renderer check whatever the backend says`() = runTest(dispatcher) {
        val model = viewModel(
            probe = { vulkanReport.copy(support = GlobeStatus.NoRenderer, activeBackend = null) },
        )

        advanceUntilIdle()

        val filament = model.uiState.value.row("Filament (3D globe)").shouldNotBeNull()
        filament.status shouldBe Status.FAIL
        filament.detail shouldContain "no renderer"
    }

    @Test
    fun `an empty fleet fails its check and skips route generation`() = runTest(dispatcher) {
        val model = viewModel(fleet = emptyList(), seeded = 0)

        advanceUntilIdle()

        val state = model.uiState.value
        state.row("Fleet").shouldNotBeNull().status shouldBe Status.FAIL
        state.row("Route generation").shouldNotBeNull().detail shouldBe "skipped: database or fleet unavailable"
    }

    @Test
    fun `a fleet seeded on this run says so`() = runTest(dispatcher) {
        val model = viewModel(seeded = 2)

        advanceUntilIdle()

        model.uiState.value.row("Fleet").shouldNotBeNull().detail shouldBe "seeded 2 aircraft from the bundled CSV"
    }
}
