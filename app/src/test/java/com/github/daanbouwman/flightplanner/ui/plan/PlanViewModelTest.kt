@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.github.daanbouwman.flightplanner.ui.plan

import com.github.daanbouwman.flightplanner.core.database.airport.AirportNameIndex
import com.github.daanbouwman.flightplanner.core.database.airport.NameIndexState
import com.github.daanbouwman.flightplanner.core.database.airport.IndexLoadTiming
import com.github.daanbouwman.flightplanner.core.database.repository.AirportRepository
import com.github.daanbouwman.flightplanner.core.database.repository.FleetRepository
import com.github.daanbouwman.flightplanner.core.database.repository.LogbookRepository
import com.github.daanbouwman.flightplanner.core.designsystem.theme.ThemeChoice
import com.github.daanbouwman.flightplanner.index.AirportIndexProvider
import com.github.daanbouwman.flightplanner.index.IndexState
import com.github.daanbouwman.flightplanner.model.AircraftSpec
import com.github.daanbouwman.flightplanner.model.Airport
import com.github.daanbouwman.flightplanner.model.AirportSizeClass
import com.github.daanbouwman.flightplanner.model.FlightRecord
import com.github.daanbouwman.flightplanner.model.Metar
import com.github.daanbouwman.flightplanner.model.Runway
import com.github.daanbouwman.flightplanner.routing.AirportIndex
import com.github.daanbouwman.flightplanner.routing.AirportIndexBuilder
import com.github.daanbouwman.flightplanner.routing.WorldOutline
import com.github.daanbouwman.flightplanner.settings.AppSettings
import com.github.daanbouwman.flightplanner.settings.SettingsRepository
import com.github.daanbouwman.flightplanner.settings.UnitSystem
import com.github.daanbouwman.flightplanner.settings.WeatherProvider
import com.github.daanbouwman.flightplanner.weather.WeatherRepository
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
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

/**
 * A small synthetic world, dense enough that the generator always finds routes.
 *
 * Real coordinates rather than invented ones, because the generator's
 * destination search is a genuine distance test and a fixture of airports at
 * (0,0), (0,1), (0,2) exercises none of it.
 */
private data class Fixture(val airports: List<Airport>) {

    val index: AirportIndex = AirportIndexBuilder(airports.size).apply {
        airports.forEach { airport ->
            add(
                id = airport.id,
                icao = airport.icao,
                latitude = airport.latitude,
                longitude = airport.longitude,
                longestRunway = airport.longestRunwayFt,
                packedFlags = AirportIndex.packFlags(
                    hasIcao = airport.hasIcaoCode,
                    hardSurface = true,
                    lighting = true,
                    sizeClass = airport.sizeClass,
                ),
            )
        }
    }.build()
}

private fun airport(
    id: Int,
    icao: String,
    name: String,
    lat: Double,
    lon: Double,
    runway: Int,
    hasIcaoCode: Boolean = true,
) = Airport(
    id = id,
    icao = icao,
    name = name,
    latitude = lat,
    longitude = lon,
    elevationFt = 0,
    country = "NL",
    municipality = name,
    sizeClass = AirportSizeClass.LARGE,
    longestRunwayFt = runway,
    runwayCount = 2,
    hasHardSurface = true,
    hasIcaoCode = hasIcaoCode,
)

private val fixture = Fixture(
    listOf(
        airport(1, "EHAM", "Amsterdam", 52.31, 4.76, 12467),
        airport(2, "EGLL", "London", 51.47, -0.45, 12799),
        airport(3, "EDDF", "Frankfurt", 50.03, 8.57, 13123),
        airport(4, "LFPG", "Paris", 49.01, 2.55, 13829),
        airport(5, "EKCH", "Copenhagen", 55.62, 12.66, 11811),
    ),
)

/**
 * As [fixture], plus one local-code airfield with no real ICAO code — kept
 * separate from [fixture] so this doesn't change what any other test in this
 * file generates from.
 */
private val icaoMixedFixture = Fixture(
    fixture.airports + airport(6, "EH99", "Hilversum", 52.18, 5.19, 12000, hasIcaoCode = false),
)

private fun spec(id: Int, code: String, range: Int, flown: Boolean = false) = AircraftSpec(
    id = id,
    manufacturer = "Boeing",
    variant = "737-$id",
    icaoCode = code,
    flown = flown,
    rangeNm = range,
    category = "Jet",
    cruiseSpeedKt = 450,
    dateFlown = if (flown) "2024-03-01" else null,
    takeoffDistanceMeters = 2000,
)

/** Hands out a prebuilt index, or fails on demand so the error path can be asserted. */
private class TestIndexProvider(
    private val index: AirportIndex?,
) : AirportIndexProvider {

    override val state: StateFlow<IndexState> = MutableStateFlow(
        index?.let { IndexState.Ready(it, IndexLoadTiming(readMillis = 0, decodeMillis = 0, airports = it.size)) }
            ?: IndexState.Failed(IllegalStateException("no index")),
    )

    override suspend fun get(): AirportIndex = index ?: error("index unavailable")

    override fun warm() = Unit
    override fun retry() = Unit
    override val readyOrNull: AirportIndex? get() = index
    override val isSettled: Boolean get() = true
}

private class FakeFleetRepository(initial: List<AircraftSpec>) : FleetRepository {
    private val state = MutableStateFlow(initial)

    override fun observeFleet(): Flow<List<AircraftSpec>> = state
    override fun observeNotFlownCount(): Flow<Int> = state.map { fleet -> fleet.count { !it.flown } }
    override suspend fun fleet(): List<AircraftSpec> = state.value
    override suspend fun byId(id: Int): AircraftSpec? = state.value.firstOrNull { it.id == id }
    override suspend fun count(): Int = state.value.size

    override suspend fun setFlown(id: Int, flown: Boolean, on: LocalDate) {
        state.value = state.value.map {
            if (it.id == id) it.copy(flown = flown, dateFlown = if (flown) on.toString() else null) else it
        }
    }

    override suspend fun markAllNotFlown() {
        state.value = state.value.map { it.copy(flown = false, dateFlown = null) }
    }

    override suspend fun add(spec: AircraftSpec): Int = spec.id
    override suspend fun update(spec: AircraftSpec) = Unit
    override suspend fun delete(spec: AircraftSpec) = Unit
    override suspend fun restoreDefaults(): Int = 0
    override suspend fun seedIfEmpty(): Int = 0
}

private class FakeLogbookRepository : LogbookRepository {
    val records = mutableListOf<FlightRecord>()
    private var nextId = 1L

    override fun observeAll(): Flow<List<FlightRecord>> = MutableStateFlow(records.toList())
    override fun observeCount(): Flow<Int> = MutableStateFlow(records.size)
    override suspend fun all(): List<FlightRecord> = records.toList()
    override suspend fun page(limit: Int, offset: Int): List<FlightRecord> = records.drop(offset).take(limit)

    override suspend fun add(record: FlightRecord): Long {
        val id = nextId++
        records += record.copy(id = id)
        return id
    }

    override suspend fun delete(record: FlightRecord) {
        records.removeAll { it.id == record.id }
    }

    override suspend fun arrivalsForAircraft(aircraftId: Int): Set<String> =
        records.filter { it.aircraftId == aircraftId }.map { it.arrivalIcao }.toSet()

    override suspend fun clear() = records.clear()
}

private class FakeAirportRepository(private val world: Fixture) : AirportRepository {
    var displayQueries = 0
        private set

    /**
     * Backing for [nameIndexState], so a test can decide when — and whether —
     * ranked search becomes available. `Idle` to start with, which is what the
     * real repository reports until something asks it to build.
     */
    private val nameIndex = MutableStateFlow<NameIndexState>(NameIndexState.Idle)

    /** How many times [prepareNameIndex] has been asked for a build. */
    var prepareCalls = 0
        private set

    override val nameIndexState: StateFlow<NameIndexState> = nameIndex

    /** Moves the name index to [state], as a real background build would. */
    fun settleNameIndex(state: NameIndexState) {
        nameIndex.value = state
    }

    override suspend fun findByIcao(icao: String): Airport? = world.airports.firstOrNull { it.icao == icao }
    override suspend fun findById(id: Int): Airport? = world.airports.firstOrNull { it.id == id }

    override suspend fun airportsByIds(ids: List<Int>): List<Airport> {
        val byId = airportsByIdMap(ids)
        return ids.mapNotNull(byId::get)
    }

    override suspend fun airportsByIdMap(ids: List<Int>): Map<Int, Airport> {
        displayQueries++
        return world.airports.filter { it.id in ids }.associateBy { it.id }
    }

    override suspend fun airportsByIcao(icaos: List<String>): List<Airport> =
        icaos.mapNotNull { code -> world.airports.firstOrNull { it.icao == code } }

    override suspend fun airportsForSlots(index: AirportIndex, slots: IntArray): List<Airport> =
        airportsByIds(slots.map { index.ids[it] })

    override suspend fun runwaysFor(airportId: Int): List<Runway> = emptyList()
    override fun prepareNameIndex(index: AirportIndex) {
        prepareCalls++
    }
    override fun nameIndexOrNull(): AirportNameIndex? = null
}

/** Backed by a plain [MutableStateFlow] rather than a real DataStore, which needs a `Context`. */
private class FakeSettingsRepository(initial: AppSettings? = AppSettings()) : SettingsRepository {
    private val state = MutableStateFlow(initial)

    override val settings: StateFlow<AppSettings?> = state

    fun update(transform: (AppSettings) -> AppSettings) {
        state.value = transform(state.value ?: AppSettings())
    }

    override fun setThemeChoice(choice: ThemeChoice) = update { it.copy(themeChoice = choice) }
    override fun setDynamicColour(enabled: Boolean) = update { it.copy(dynamicColour = enabled) }
    override fun setUnitSystem(system: UnitSystem) = update { it.copy(unitSystem = system) }
    override fun setIcaoOnly(enabled: Boolean) = update { it.copy(icaoOnly = enabled) }
    override fun setWeatherProvider(provider: WeatherProvider) = update { it.copy(weatherProvider = provider) }
    override fun setAvwxApiKey(key: String?) = update { it.copy(avwxApiKey = key) }
}

/** Never resolves anything — Plan's weather pipeline is exercised in its own test file, not here. */
private class FakeWeatherRepository : WeatherRepository {
    override suspend fun fetch(stations: List<String>): Map<String, Metar> = emptyMap()
}

class PlanViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    /**
     * Runs a test with the exposed flows actually collected.
     *
     * `uiState` and the two search flows are `WhileSubscribed`, so with no
     * collector they never leave their initial value and every assertion against
     * `.value` reads a state the ViewModel is not in — a test harness that lies
     * in the direction of "nothing happened". Collecting from
     * [TestScope.backgroundScope] keeps them hot for the test and cancels them
     * with it, so nothing outlives the scheduler it was dispatched on.
     */
    private fun planTest(
        fleet: List<AircraftSpec> = listOf(spec(1, "B738", 3000), spec(2, "A320", 2800, flown = true)),
        logbook: FakeLogbookRepository = FakeLogbookRepository(),
        airports: FakeAirportRepository = FakeAirportRepository(fixture),
        index: AirportIndex? = fixture.index,
        fleetRepository: FakeFleetRepository = FakeFleetRepository(fleet),
        settingsRepository: FakeSettingsRepository = FakeSettingsRepository(),
        body: suspend TestScope.(PlanViewModel) -> Unit,
    ) = runTest(dispatcher) {
        val model = PlanViewModel(
            indexProvider = TestIndexProvider(index),
            fleetRepository = fleetRepository,
            logbookRepository = logbook,
            airportRepository = airports,
            worldOutlineLoader = { WorldOutline.Empty },
            settingsRepository = settingsRepository,
            weatherRepository = FakeWeatherRepository(),
            defaultDispatcher = dispatcher,
        )
        backgroundScope.launch(dispatcher) { model.uiState.collect {} }
        backgroundScope.launch(dispatcher) { model.airportResults.collect {} }
        backgroundScope.launch(dispatcher) { model.aircraftResults.collect {} }
        backgroundScope.launch(dispatcher) { model.searchScope.collect {} }
        advanceUntilIdle()
        body(model)
    }

    @Test
    fun `generates a first batch on open without being asked`() = planTest { model ->
        // The Plan screen is the launch destination and its purpose is a list of
        // routes; opening to an empty one asks the user to press a button for the
        // thing they opened the app for. planTest already advanced the scheduler.
        val state = model.uiState.value
        state.status shouldBe PlanStatus.Ready
        state.routes.isNotEmpty() shouldBe true
    }

    @Test
    fun `generate replaces the batch on screen`() = planTest { model ->
        val first = model.uiState.value.routes

        model.generate()
        advanceUntilIdle()

        val second = model.uiState.value
        second.status shouldBe PlanStatus.Ready
        second.routes.isNotEmpty() shouldBe true
        // A new batch, not an appended one — generate is replace, load-more appends.
        second.routes.size shouldBe first.size
        (second.routes.first().id != first.first().id) shouldBe true
    }

    @Test
    fun `changing the mode regenerates and honours the filter`() = planTest { model ->
        model.generate()
        advanceUntilIdle()
        val first = model.uiState.value.routes

        model.setMode(PlanMode.NotFlown)
        advanceUntilIdle()

        val second = model.uiState.value
        second.status shouldBe PlanStatus.Ready
        // A fresh batch, not the previous one carried across the mode change.
        (second.routes.first().id != first.first().id) shouldBe true
        // Not-flown may only draw from airframes that have never been flown.
        second.routes.all { !it.aircraft.flown } shouldBe true
    }

    @Test
    fun `enabling ICAO-only in settings regenerates the list live and excludes local-code airports`() {
        val settingsRepository = FakeSettingsRepository()
        planTest(
            index = icaoMixedFixture.index,
            airports = FakeAirportRepository(icaoMixedFixture),
            settingsRepository = settingsRepository,
        ) { model ->
            // Sanity check first: across a few unfiltered batches, the local-code
            // airfield (id 6, EH99, no ICAO code) does turn up — otherwise the
            // exclusion asserted below would pass vacuously, having proven nothing.
            val seenBeforeFilter = buildSet {
                repeat(3) {
                    model.generate()
                    advanceUntilIdle()
                    model.uiState.value.routes.forEach { add(it.departure.id); add(it.destination.id) }
                }
            }
            (6 in seenBeforeFilter) shouldBe true

            val beforeToggle = model.uiState.value.routes

            // No explicit generate()/setMode() call — the point is that toggling
            // the *setting* alone, through the reactive combine(selection, icaoOnly)
            // path in PlanViewModel's init, is enough to regenerate the list.
            settingsRepository.setIcaoOnly(true)
            advanceUntilIdle()

            val afterToggle = model.uiState.value
            afterToggle.status shouldBe PlanStatus.Ready
            (afterToggle.routes.first().id != beforeToggle.first().id) shouldBe true
            afterToggle.routes.none { it.departure.id == 6 || it.destination.id == 6 } shouldBe true
            afterToggle.routes.all { it.departure.hasIcaoCode && it.destination.hasIcaoCode } shouldBe true
        }
    }

    @Test
    fun `load more appends beneath the existing rows`() = planTest { model ->
        model.generate()
        advanceUntilIdle()
        val firstBatch = model.uiState.value.routes
        val firstId = firstBatch.first().id

        model.loadMore()
        advanceUntilIdle()

        val grown = model.uiState.value
        grown.status shouldBe PlanStatus.Ready
        (grown.routes.size > firstBatch.size) shouldBe true
        // The original rows stay put. An append that reorders is a list that
        // jumps under a thumb mid-scroll.
        grown.routes.first().id shouldBe firstId
    }

    @Test
    fun `load more is dropped while a batch is already in flight`() = planTest { model ->
        model.generate()
        advanceUntilIdle()
        val before = model.uiState.value.routes.size

        // Three requests without letting the scheduler run in between. Only the
        // first is accepted; without the guard, scrolling near the end grows the
        // list without bound.
        model.loadMore()
        model.loadMore()
        model.loadMore()
        advanceUntilIdle()

        model.uiState.value.routes.size shouldBe before * 2
    }

    @Test
    fun `this-aircraft mode with no airframe waits instead of generating nothing`() = planTest { model ->
        model.generate()
        advanceUntilIdle()

        model.setMode(PlanMode.SelectedAircraft)
        advanceUntilIdle()

        val state = model.uiState.value
        state.awaitingAircraftChoice shouldBe true
        state.routes.shouldHaveSize(0)
        // Idle, not Failed and not an empty Ready: the app has not tried and
        // failed, it is waiting for one more decision.
        state.status shouldBe PlanStatus.Idle
    }

    @Test
    fun `choosing an airframe switches mode and plans only for it`() = planTest { model ->
        model.generate()
        advanceUntilIdle()

        model.setAircraft(spec(1, "B738", 3000))
        advanceUntilIdle()

        val state = model.uiState.value
        state.mode shouldBe PlanMode.SelectedAircraft
        state.status shouldBe PlanStatus.Ready
        state.routes.all { it.aircraft.id == 1 } shouldBe true
    }

    @Test
    fun `marking flown writes to the logbook and to the fleet`() {
        val logbook = FakeLogbookRepository()
        val fleetRepository = FakeFleetRepository(listOf(spec(1, "B738", 3000)))
        planTest(logbook = logbook, fleetRepository = fleetRepository) { model ->
            model.generate()
            advanceUntilIdle()

            val row = model.uiState.value.routes.first()
            model.markFlown(row)
            advanceUntilIdle()

            // Both halves. Writing only the aircraft flag leaves a fleet that
            // disagrees with its own history.
            logbook.records.shouldHaveSize(1)
            logbook.records.single().departureIcao shouldBe row.departure.icao
            logbook.records.single().arrivalIcao shouldBe row.destination.icao
            logbook.records.single().distanceNm shouldBe row.distanceNm
            fleetRepository.byId(1)!!.flown shouldBe true
            model.uiState.value.routes.contains(row) shouldBe false
        }
    }

    @Test
    fun `undo restores the row, deletes the record and puts the old date back`() {
        val logbook = FakeLogbookRepository()
        // Already flown, on a date that must survive the round trip.
        val fleetRepository = FakeFleetRepository(listOf(spec(1, "B738", 3000, flown = true)))
        planTest(logbook = logbook, fleetRepository = fleetRepository) { model ->
            model.generate()
            advanceUntilIdle()

            val row = model.uiState.value.routes.first()
            val position = model.uiState.value.routes.indexOf(row)
            model.markFlown(row)
            advanceUntilIdle()

            model.undoMarkFlown()
            advanceUntilIdle()

            logbook.records.shouldHaveSize(0)
            val restored = fleetRepository.byId(1)!!
            restored.flown shouldBe true
            // The original date, not today's — an aircraft first flown in 2024
            // must not come back stamped with the date of the undo.
            restored.dateFlown shouldBe "2024-03-01"
            model.uiState.value.routes.indexOf(row) shouldBe position
        }
    }

    @Test
    fun `undo of a never-flown airframe clears the flag again`() {
        val fleetRepository = FakeFleetRepository(listOf(spec(1, "B738", 3000, flown = false)))
        planTest(fleetRepository = fleetRepository) { model ->
            model.generate()
            advanceUntilIdle()

            model.markFlown(model.uiState.value.routes.first())
            advanceUntilIdle()
            model.undoMarkFlown()
            advanceUntilIdle()

            val restored = fleetRepository.byId(1)!!
            restored.flown shouldBe false
            restored.dateFlown shouldBe null
        }
    }

    @Test
    fun `replace swaps one row in place and leaves its neighbours alone`() = planTest { model ->
        model.generate()
        advanceUntilIdle()

        val before = model.uiState.value.routes
        val target = before[2]
        model.replace(target)
        advanceUntilIdle()

        val after = model.uiState.value.routes
        after shouldHaveSize before.size
        after.contains(target) shouldBe false
        // Only the swiped card moves, so only the swiped card animates.
        after[1].id shouldBe before[1].id
        after[3].id shouldBe before[3].id
    }

    @Test
    fun `replace keeps the airframe and the departure and changes only the destination`() = planTest { model ->
        model.generate()
        advanceUntilIdle()

        val before = model.uiState.value.routes
        // Every row, not one: the point is that replace narrows the request to
        // the row it was handed, and a batch generated under "Any" has many
        // departures and many airframes in it, so one sample proves little.
        before.forEachIndexed { position, target ->
            model.replace(target)
            advanceUntilIdle()

            val replacement = model.uiState.value.routes[position]
            replacement.aircraft.id shouldBe target.aircraft.id
            replacement.departure.icao shouldBe target.departure.icao
            replacement.destination.icao shouldNotBe target.destination.icao
        }
    }

    @Test
    fun `an empty fleet is a failure, not an empty list`() {
        planTest(fleet = emptyList(), fleetRepository = FakeFleetRepository(emptyList())) { model ->
            model.generate()
            advanceUntilIdle()

            model.uiState.value.status shouldBe PlanStatus.Failed(PlanFailure.FleetEmpty)
        }
    }

    @Test
    fun `an unavailable index is reported rather than swallowed`() {
        planTest(index = null) { model ->
            model.generate()
            advanceUntilIdle()

            model.uiState.value.status shouldBe PlanStatus.Failed(PlanFailure.IndexUnavailable)
        }
    }

    @Test
    fun `airport display rows are fetched in one query per batch`() {
        val airports = FakeAirportRepository(fixture)
        planTest(airports = airports) { model ->
            // The picker's blank-query suggestions have already run one query by
            // now, so measure the delta rather than the total.
            val before = airports.displayQueries
            model.generate()
            advanceUntilIdle()

            // Fifty routes touch a hundred airport ends. Fetching per row would be
            // a hundred round trips for one screen; the batch is one call.
            (airports.displayQueries - before) shouldBe 1
            model.uiState.value.routes.isNotEmpty() shouldBe true
        }
    }

    @Test
    fun `every row carries a drawable arc and a real flight time`() = planTest { model ->
        model.generate()
        advanceUntilIdle()

        model.uiState.value.routes.forEach { row ->
            (row.arc.size >= 4) shouldBe true
            row.arc.lats.none { it.isNaN() } shouldBe true
            row.arc.lons.none { it.isNaN() } shouldBe true
            // 450 kt cruise over hundreds of miles: never zero for a real leg.
            (row.flightTime.hours > 0 || row.flightTime.minutes > 0) shouldBe true
        }
    }

    @Test
    fun `aircraft search distinguishes variants that share a type code`() {
        // Two airframes with the same ICAO *type* code. Recovering a ranked result
        // by that code cannot tell them apart, which silently drops one of them.
        val fleet = listOf(
            spec(1, "B738", 3000).copy(variant = "737-800"),
            spec(2, "B738", 3200).copy(variant = "737-800BCF"),
        )
        planTest(fleet = fleet, fleetRepository = FakeFleetRepository(fleet)) { model ->
            model.setAircraftQuery("B738")
            advanceUntilIdle()

            model.aircraftResults.value.map { it.id } shouldBe listOf(1, 2)
        }
    }

    @Test
    fun `the departure picker suggests the largest airports before anything is typed`() = planTest { model ->
        // The index is sorted ascending by runway length, so the useful default is
        // the tail read backwards, not the head.
        model.airportResults.value.first().icao shouldBe "LFPG"
    }

    @Test
    fun `the departure picker matches codes without a name index`() = planTest { model ->
        model.setAirportQuery("EG")
        advanceUntilIdle()
        model.airportResults.value.map { it.icao } shouldBe listOf("EGLL")

        // No name index is built in this fixture, so a name-only query finds
        // nothing rather than falling over.
        model.setAirportQuery("London")
        advanceUntilIdle()
        model.airportResults.value.shouldHaveSize(0)
    }

    @Test
    fun `search scope follows the name index, and retry asks for a rebuild`() {
        val airports = FakeAirportRepository(fixture)
        return planTest(airports = airports) { model ->
            // `init` asks for the build once, off the startup path. Until it
            // lands, a keystroke matches ICAO codes and nothing else.
            airports.prepareCalls shouldBe 1
            model.searchScope.value shouldBe SearchScope.NamesLoading

            airports.settleNameIndex(NameIndexState.Building)
            advanceUntilIdle()
            // Idle and Building are the same thing from the picker's side: names
            // do not rank yet, and something is already fixing that.
            model.searchScope.value shouldBe SearchScope.NamesLoading

            airports.settleNameIndex(NameIndexState.Failed(IllegalStateException("no rows")))
            advanceUntilIdle()
            // Distinct from loading, because it does not resolve on its own —
            // which is the whole reason the picker offers a Retry for this state
            // and not for the one above.
            model.searchScope.value shouldBe SearchScope.NamesUnavailable

            model.retryNameSearch()
            advanceUntilIdle()
            airports.prepareCalls shouldBe 2
        }
    }

    /*
     * `SearchScope.Full` is deliberately not asserted here. Reaching it needs a
     * `NameIndexState.Ready`, which carries an `AirportNameIndex` whose
     * constructor and builder are both `internal` to `:core:database` — as is
     * the repository that builds one — so this module cannot make one, and
     * widening that API for a test would be a worse trade than the gap. The
     * `when` behind the mapping is exhaustive over a sealed interface, so a
     * state cannot be silently dropped from it.
     *
     * The same gap covers the other half of that transition: `airportSearches`
     * re-emits the current query when the index turns Ready, so the list stops
     * being ICAO-only at the same moment the notice above it goes away. That
     * edge is unreachable from here for the reason above, and unobservable even
     * if it were — `FakeAirportRepository.nameIndexOrNull()` returns null, so a
     * re-ranked search would return exactly what the first one did. Covering it
     * needs an instrumented test in `:core:database` against a real Room build.
     */
}
