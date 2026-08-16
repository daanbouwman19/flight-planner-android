package com.github.daanbouwman.flightplanner.startup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.daanbouwman.flightplanner.core.database.airport.AirportDao
import com.github.daanbouwman.flightplanner.core.database.airport.AirportIndexLoader
import com.github.daanbouwman.flightplanner.core.database.airport.DatasetMetaDao
import com.github.daanbouwman.flightplanner.core.database.airport.RunwayDao
import com.github.daanbouwman.flightplanner.core.database.user.AircraftDao
import com.github.daanbouwman.flightplanner.core.database.user.FleetSeeder
import com.github.daanbouwman.flightplanner.core.database.user.toSpec
import com.github.daanbouwman.flightplanner.feature.globe.FilamentProbe
import com.github.daanbouwman.flightplanner.model.DatasetMetaKeys
import com.github.daanbouwman.flightplanner.routing.AirportIndex
import com.github.daanbouwman.flightplanner.routing.RouteGenerator
import com.github.daanbouwman.flightplanner.routing.RouteMode
import com.github.daanbouwman.flightplanner.routing.RouteRequest
import com.github.daanbouwman.flightplanner.startup.CheckResult.Status
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.random.Random
import kotlin.system.measureTimeMillis

/**
 * Exercises the whole stack on the device and reports what worked.
 *
 * The point is to make the first install prove something. Each check below
 * covers a failure that cannot be reproduced on a development machine:
 * extracting the prepackaged database and passing Room's identity-hash
 * validation, loading three separate sets of native libraries under ARM, and
 * running the route generator on the real 24,000-airport dataset rather than a
 * synthetic fixture.
 */
@HiltViewModel
class StartupCheckViewModel @Inject constructor(
    private val airportDao: AirportDao,
    private val runwayDao: RunwayDao,
    private val datasetMetaDao: DatasetMetaDao,
    private val aircraftDao: AircraftDao,
    private val fleetSeeder: FleetSeeder,
    private val indexLoader: AirportIndexLoader,
) : ViewModel() {

    private val _uiState = MutableStateFlow(StartupUiState())
    val uiState: StateFlow<StartupUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { runChecks() }
    }

    private fun report(name: String, status: Status, detail: String) {
        _uiState.update { it.copy(checks = it.checks + CheckResult(name, status, detail)) }
    }

    private suspend fun runChecks() {
        val index = checkAirportDatabase()
        val fleetSize = checkFleetSeeding()
        checkRouteGeneration(index, fleetSize)
        checkFilament()
        _uiState.update { it.copy(finished = true) }
    }

    /**
     * Opening this database is the single riskiest moment in the app's life:
     * the asset has to be extracted from the APK and then accepted by Room,
     * whose identity-hash check is unforgiving and fails on every device at once
     * when it fails at all.
     */
    private suspend fun checkAirportDatabase(): AirportIndex? {
        return try {
            var airports = 0
            var runways = 0
            val millis = measureTimeMillis {
                airports = airportDao.count()
                runways = runwayDao.count()
            }
            if (airports == 0) {
                report("Airport database", Status.FAIL, "opened but contains no airports")
                return null
            }

            val tier = datasetMetaDao.value(DatasetMetaKeys.FILTER_TIER) ?: "unknown"
            val modified = datasetMetaDao.value(DatasetMetaKeys.UPSTREAM_MODIFIED) ?: "unknown"
            val withIcao = airportDao.countWithIcao()
            report(
                "Airport database",
                Status.PASS,
                "%,d airports (%,d with ICAO), %,d runway ends in %d ms\ntier %s, snapshot %s"
                    .format(airports, withIcao, runways, millis, tier, modified),
            )

            buildIndex(airports)
        } catch (t: Throwable) {
            report("Airport database", Status.FAIL, t.message ?: t::class.java.simpleName)
            null
        }
    }

    private suspend fun buildIndex(expected: Int): AirportIndex? = try {
        val loaded = indexLoader.load()
        val timing = loaded.timing
        report(
            "In-memory index",
            if (timing.totalMillis <= INDEX_BUDGET_MILLIS) Status.PASS else Status.WARN,
            "%,d airports in %d ms\n  read %d ms, decode %d ms".format(
                timing.airports, timing.totalMillis, timing.readMillis, timing.decodeMillis,
            ),
        )
        loaded.index
    } catch (t: Throwable) {
        report("In-memory index", Status.FAIL, t.message ?: t::class.java.simpleName)
        null
    }

    private suspend fun checkFleetSeeding(): Int = try {
        val inserted = fleetSeeder.seedIfEmpty()
        val total = aircraftDao.count()
        report(
            "Fleet",
            if (total > 0) Status.PASS else Status.FAIL,
            if (inserted > 0) "seeded $inserted aircraft from the bundled CSV" else "$total aircraft already present",
        )
        total
    } catch (t: Throwable) {
        report("Fleet", Status.FAIL, t.message ?: t::class.java.simpleName)
        0
    }

    /** Generates real routes from the real dataset, at both extremes of the fleet. */
    private suspend fun checkRouteGeneration(index: AirportIndex?, fleetSize: Int) {
        if (index == null || fleetSize == 0) {
            report("Route generation", Status.FAIL, "skipped: database or fleet unavailable")
            return
        }
        try {
            val fleet = aircraftDao.all().map { it.toSpec() }
            val generator = RouteGenerator(index)
            val shortest = fleet.minBy { it.rangeNm }
            val longest = fleet.maxBy { it.rangeNm }

            val lines = StringBuilder()
            var ok = true
            for (aircraft in listOf(shortest, longest)) {
                var routes: List<com.github.daanbouwman.flightplanner.routing.GeneratedRoute>
                val request = RouteRequest(RouteMode.AllAircraft, listOf(aircraft), amount = 50)
                val millis = measureTimeMillis {
                    routes = generator.generate(request, Random(20260816))
                }
                if (routes.isEmpty()) {
                    ok = false
                    lines.appendLine("${aircraft.displayName}: no routes")
                    continue
                }
                val example = routes.first()
                lines.appendLine(
                    "%s (%,d NM)\n  %d routes in %d ms, e.g. %s to %s, %,d NM".format(
                        aircraft.displayName,
                        aircraft.rangeNm,
                        routes.size,
                        millis,
                        index.icaoOf(example.departureSlot),
                        index.icaoOf(example.destinationSlot),
                        example.distanceNm,
                    ),
                )
            }
            report("Route generation", if (ok) Status.PASS else Status.FAIL, lines.toString().trim())
        } catch (t: Throwable) {
            report("Route generation", Status.FAIL, t.message ?: t::class.java.simpleName)
        }
    }

    /**
     * Reported as a warning rather than a failure when Vulkan is unavailable:
     * Filament's OpenGL backend is a perfectly good fallback for this workload,
     * so it is information, not breakage.
     */
    private suspend fun checkFilament() {
        val probe = withContext(Dispatchers.Default) { FilamentProbe.run() }
        val status = when {
            probe.error != null -> Status.FAIL
            probe.vulkanActive -> Status.PASS
            else -> Status.WARN
        }
        report("Filament (3D globe)", status, probe.summary())
    }

    private companion object {
        /**
         * Loading the prebuilt blob is a file read plus a dozen array copies and
         * measures in single-digit milliseconds. The budget is set close to that
         * rather than to what a user would notice, so that reintroducing *any*
         * per-airport work on the startup path shows up here immediately instead
         * of being absorbed silently.
         */
        const val INDEX_BUDGET_MILLIS = 40L
    }
}
