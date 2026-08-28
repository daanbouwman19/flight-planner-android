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
import io.kotest.matchers.collections.shouldHaveSize
import com.github.daanbouwman.flightplanner.routing.RouteArc
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import java.time.LocalDate
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

private fun runway(id: Int, airportId: Int, ident: String, length: Int) = Runway(
    id = id,
    airportId = airportId,
    ident = ident,
    trueHeadingDeg = 183.0,
    lengthFt = length,
    widthFt = 197,
    surface = "ASP",
    surfaceKind = SurfaceKind.HARD,
    latitude = null,
    longitude = null,
    elevationFt = 0,
    lighted = true,
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

private class FakeAirportRepository(
    private val airports: List<Airport>,
    private val runways: Map<Int, List<Runway>> = emptyMap(),
) : AirportRepository {
    var runwayQueries = 0
        private set

    override val nameIndexState: StateFlow<NameIndexState> = MutableStateFlow(NameIndexState.Idle)

    override suspend fun findByIcao(icao: String): Airport? = airports.firstOrNull { it.icao == icao }
    override suspend fun findById(id: Int): Airport? = airports.firstOrNull { it.id == id }
    override suspend fun airportsByIds(ids: List<Int>): List<Airport> = airports.filter { it.id in ids }
    override suspend fun airportsByIdMap(ids: List<Int>): Map<Int, Airport> =
        airportsByIds(ids).associateBy { it.id }
    override suspend fun airportsByIcao(icaos: List<String>): List<Airport> =
        airports.filter { it.icao in icaos }
    override suspend fun airportsForSlots(index: AirportIndex, slots: IntArray): List<Airport> =
        emptyList()

    override suspend fun runwaysFor(airportId: Int): List<Runway> {
        runwayQueries++
        return runways[airportId].orEmpty()
    }

    override fun prepareNameIndex(index: AirportIndex) = Unit
    override fun nameIndexOrNull(): AirportNameIndex? = null
}

private class FakeFleetRepository(private val fleet: List<AircraftSpec>) : FleetRepository {
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

/** Returns a fixed [Metar] for whichever stations [known] names, nothing for the rest. */
private class FakeWeatherRepository(private val known: Map<String, Metar> = emptyMap()) : WeatherRepository {
    var fetchCalls = 0
        private set

    override suspend fun fetch(stations: List<String>): Map<String, Metar> {
        fetchCalls++
        return stations.mapNotNull { station -> known[station]?.let { station to it } }.toMap()
    }
}

private fun metar(station: String) = Metar(station = station, raw = "$station RAW METAR", flightRules = FlightRules.VFR)

class RouteDetailLoaderTest {

    private val route = Destination.RouteDetail(
        departureIcao = "EHAM",
        destinationIcao = "KJFK",
        aircraftId = 7,
        distanceNm = 2847,
    )

    private fun loader(
        airports: List<Airport> = listOf(eham, kjfk),
        runways: Map<Int, List<Runway>> = emptyMap(),
        weather: FakeWeatherRepository = FakeWeatherRepository(),
        repository: FakeAirportRepository = FakeAirportRepository(airports, runways),
    ) = Triple(
        repository,
        weather,
        RouteDetailLoader(
            airportRepository = repository,
            fleetRepository = FakeFleetRepository(listOf(boeing)),
            worldOutlineLoader = { WorldOutline.Empty },
            weatherRepository = weather,
        ),
    )

    @Test
    fun `the first load resolves both ends, the arc and both bearings`() = runTest {
        val (repository, _, loader) = loader()

        val state = loader.load(route)

        state.departure shouldBe eham
        state.destination shouldBe kjfk
        state.aircraft shouldBe boeing
        state.loading shouldBe false
        state.arc.shouldNotBeNull().lats.size shouldBe RouteArc.CARD_SAMPLES
        state.initialBearingDeg shouldBe 291
        state.finalBearingDeg shouldBe 229
        state.requiredRunwayFt shouldBe boeing.requiredRunwayFt
        state.flightTime?.format() shouldBe "6:11"

        // The point of the split: the first load must not have read a runway.
        repository.runwayQueries shouldBe 0
    }

    @Test
    fun `the runway lists arrive in a second call, one query per end`() = runTest {
        val (repository, _, loader) = loader(
            runways = mapOf(
                1 to listOf(runway(1, 1, "18R", 12467), runway(2, 1, "06", 11329)),
                2 to listOf(runway(3, 2, "04L", 12079)),
            ),
        )

        val withRunways = loader.withRunways(loader.load(route))

        withRunways.departureRunways shouldHaveSize 2
        withRunways.destinationRunways shouldHaveSize 1
        repository.runwayQueries shouldBe 2
    }

    @Test
    fun `an airport missing from the dataset leaves the arc and the bearings absent`() = runTest {
        // Not a crash and not an error screen: the codes came through the
        // navigation arguments, so the title still draws. Only what needs two
        // positions is missing.
        val (_, _, loader) = loader(airports = listOf(eham))

        val state = loader.load(route)

        state.departure shouldBe eham
        state.destination.shouldBeNull()
        state.arc.shouldBeNull()
        state.initialBearingDeg.shouldBeNull()
        state.finalBearingDeg.shouldBeNull()
        state.loading shouldBe false
    }

    @Test
    fun `a missing airport is asked no runway questions`() = runTest {
        val (repository, _, loader) = loader(airports = listOf(eham))

        loader.withRunways(loader.load(route))

        repository.runwayQueries shouldBe 1
    }

    @Test
    fun `weather resolves both ends in one fetch call`() = runTest {
        val weather = FakeWeatherRepository(mapOf("EHAM" to metar("EHAM"), "KJFK" to metar("KJFK")))
        val (_, _, loader) = loader(weather = weather)

        val state = loader.withWeather(loader.load(route))

        state.departureMetar?.station shouldBe "EHAM"
        state.destinationMetar?.station shouldBe "KJFK"
        weather.fetchCalls shouldBe 1
    }

    @Test
    fun `a station with no report leaves that end's weather absent, not the whole call failing`() = runTest {
        val weather = FakeWeatherRepository(mapOf("EHAM" to metar("EHAM")))
        val (_, _, loader) = loader(weather = weather)

        val state = loader.withWeather(loader.load(route))

        state.departureMetar?.station shouldBe "EHAM"
        state.destinationMetar.shouldBeNull()
    }

    @Test
    fun `an airport missing from the dataset is asked no weather question for that end`() = runTest {
        val weather = FakeWeatherRepository(mapOf("EHAM" to metar("EHAM")))
        val (_, _, loader) = loader(airports = listOf(eham), weather = weather)

        val state = loader.withWeather(loader.load(route))

        state.departureMetar?.station shouldBe "EHAM"
        state.destinationMetar.shouldBeNull()
    }
}
