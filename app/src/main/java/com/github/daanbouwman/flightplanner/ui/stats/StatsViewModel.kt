package com.github.daanbouwman.flightplanner.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.daanbouwman.flightplanner.core.database.repository.AirportRepository
import com.github.daanbouwman.flightplanner.core.database.repository.FleetRepository
import com.github.daanbouwman.flightplanner.core.database.repository.LogbookRepository
import com.github.daanbouwman.flightplanner.di.DefaultDispatcher
import com.github.daanbouwman.flightplanner.model.Airport
import com.github.daanbouwman.flightplanner.model.FlightRecord
import com.github.daanbouwman.flightplanner.routing.AirportTally
import com.github.daanbouwman.flightplanner.routing.FlightStatisticsCalculator
import com.github.daanbouwman.flightplanner.routing.WorldOutline
import com.github.daanbouwman.flightplanner.world.WorldOutlineLoader
import com.github.daanbouwman.flightplanner.ui.STOP_TIMEOUT_MILLIS
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

/**
 * The dashboard figures that depend on the logbook, fleet and timeframe — but
 * not on [ChartMetric], which only picks a column on an already-computed chart.
 * Kept separate from [StatsUiState] so toggling the chart metric never re-runs
 * the DB lookup and grouping below.
 */
private sealed interface DashboardResult {
    data object Empty : DashboardResult

    data class Data(
        val timeframe: StatsTimeframe,
        val totalDistanceNm: Int,
        val earthCircumferences: Double,
        val totalFlights: Int,
        val averageDistanceNm: Double,
        val longestFlight: LegStat?,
        val shortestFlight: LegStat?,
        val monthlyActivity: List<MonthlyActivity>,
        val topAircraft: List<TopAircraftStat>,
        val favoriteDeparture: AirportCount?,
        val favoriteArrival: AirportCount?,
        val mostVisitedAirport: AirportCount?,
        val visitedAirports: List<VisitedAirport>,
        val visitedLegs: List<VisitedLeg>,
    ) : DashboardResult
}

/** Drives the Stats Dashboard. */
@HiltViewModel
class StatsViewModel @Inject constructor(
    private val logbookRepository: LogbookRepository,
    private val fleetRepository: FleetRepository,
    private val airportRepository: AirportRepository,
    private val worldOutlineLoader: WorldOutlineLoader,
    @param:DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : ViewModel() {

    val worldOutline: StateFlow<WorldOutline> = flow { emit(worldOutlineLoader.load()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), WorldOutline.Empty)

    private val _timeframe = MutableStateFlow(StatsTimeframe.ALL_TIME)
    val timeframe: StateFlow<StatsTimeframe> = _timeframe

    private val _chartMetric = MutableStateFlow(ChartMetric.FLIGHTS)
    val chartMetric: StateFlow<ChartMetric> = _chartMetric

    private val dashboard: Flow<DashboardResult> = combine(
        logbookRepository.observeAll(),
        fleetRepository.observeFleet(),
        _timeframe,
    ) { records, fleet, selectedTimeframe ->
        if (records.isEmpty()) {
            return@combine DashboardResult.Empty
        }

        val today = LocalDate.now()
        val filtered = StatsGrouping.filterByTimeframe(records, selectedTimeframe, today)

        // Totals, extremes and favourites come from the ported calculator so the
        // desktop's tie-breaks exist in one place; see its KDoc for the rules.
        val statistics = FlightStatisticsCalculator.calculateDetailed(filtered)
        val earthCircumferences = statistics.totalDistanceNm / EARTH_CIRCUMFERENCE_NM

        val neededIcaos = filtered.flatMap { listOf(it.departureIcao, it.arrivalIcao) }
            .map { it.trim().uppercase() }
            .distinct()
        val airports = airportRepository.airportsByIcao(neededIcaos)
        val airportMap = airports.associateBy { it.icao }

        val monthlyActivity = StatsGrouping.buildMonthlyActivity(filtered, selectedTimeframe, today)
        val topAircraft = StatsGrouping.computeTopAircraft(filtered, fleet)
        val (visitedAirports, visitedLegs) = StatsGrouping.buildVisitedNetwork(filtered, airportMap)

        DashboardResult.Data(
            timeframe = selectedTimeframe,
            totalDistanceNm = statistics.totalDistanceNm,
            earthCircumferences = earthCircumferences,
            totalFlights = statistics.totalFlights,
            averageDistanceNm = statistics.averageFlightDistanceNm,
            longestFlight = statistics.longestFlight?.toLegStat(),
            shortestFlight = statistics.shortestFlight?.toLegStat(),
            monthlyActivity = monthlyActivity,
            topAircraft = topAircraft,
            favoriteDeparture = statistics.favoriteDepartureAirport?.toAirportCount(airportMap),
            favoriteArrival = statistics.favoriteArrivalAirport?.toAirportCount(airportMap),
            mostVisitedAirport = statistics.mostVisitedAirport?.toAirportCount(airportMap),
            visitedAirports = visitedAirports,
            visitedLegs = visitedLegs,
        )
    }
        .flowOn(defaultDispatcher)

    val uiState: StateFlow<StatsUiState> = combine(dashboard, _chartMetric) { result, selectedMetric ->
        when (result) {
            DashboardResult.Empty -> StatsUiState.Empty
            is DashboardResult.Data -> StatsUiState.Success(
                timeframe = result.timeframe,
                chartMetric = selectedMetric,
                totalDistanceNm = result.totalDistanceNm,
                earthCircumferences = result.earthCircumferences,
                totalFlights = result.totalFlights,
                averageDistanceNm = result.averageDistanceNm,
                longestFlight = result.longestFlight,
                shortestFlight = result.shortestFlight,
                monthlyActivity = result.monthlyActivity,
                topAircraft = result.topAircraft,
                favoriteDeparture = result.favoriteDeparture,
                favoriteArrival = result.favoriteArrival,
                mostVisitedAirport = result.mostVisitedAirport,
                visitedAirports = result.visitedAirports,
                visitedLegs = result.visitedLegs,
            )
        }
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), StatsUiState.Loading)

    fun setTimeframe(timeframe: StatsTimeframe) {
        _timeframe.value = timeframe
    }

    fun setChartMetric(metric: ChartMetric) {
        _chartMetric.value = metric
    }
}

/** A missing distance reads as `0`, the same as the calculator counted it. */
private fun FlightRecord.toLegStat() = LegStat(
    departureIcao = departureIcao,
    arrivalIcao = arrivalIcao,
    aircraftId = aircraftId,
    distanceNm = distanceNm ?: 0,
)

/**
 * Airport names are a database concern and stay out of `:core:routing`, so the
 * tally is named here. The lookup key is normalised the same way the
 * repository query above was, so a name is found for any record the query
 * found an airport for.
 */
private fun AirportTally.toAirportCount(airportMap: Map<String, Airport>) = AirportCount(
    icao = icao,
    name = airportMap[icao.trim().uppercase()]?.name,
    count = count,
)
