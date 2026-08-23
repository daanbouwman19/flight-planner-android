package com.github.daanbouwman.flightplanner.ui.stats

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.github.daanbouwman.flightplanner.R
import com.github.daanbouwman.flightplanner.model.AircraftSpec
import com.github.daanbouwman.flightplanner.routing.GeoArc
import java.time.YearMonth

/** Earth's equatorial circumference in nautical miles (40,075 km / 1.852). */
const val EARTH_CIRCUMFERENCE_NM = 21638.8

/** Timeframe filter for flight statistics. */
enum class StatsTimeframe(@param:StringRes val labelRes: Int) {
    ALL_TIME(R.string.stats_timeframe_all_time),
    THIS_YEAR(R.string.stats_timeframe_this_year),
    PAST_12_MONTHS(R.string.stats_timeframe_past_12_months),
}

/** Metric to display on the monthly activity chart. */
enum class ChartMetric(@param:StringRes val labelRes: Int) {
    FLIGHTS(R.string.stats_metric_flights),
    DISTANCE(R.string.stats_metric_distance),
}

/** One month of activity for the bar chart. */
data class MonthlyActivity(
    val yearMonth: YearMonth,
    val monthLabel: String,
    val flightCount: Int,
    val distanceNm: Int,
)

/** Summary of a single route leg for shortest/longest cards. */
data class LegStat(
    val departureIcao: String,
    val arrivalIcao: String,
    val aircraftId: Int,
    val distanceNm: Int,
) {
    val legDisplayName: String get() = "$departureIcao → $arrivalIcao"
}

/** An aircraft in the top-aircraft ranking. */
data class TopAircraftStat(
    val aircraft: AircraftSpec,
    val flightCount: Int,
    val totalDistanceNm: Int,
)

/** An airport ranking entry with movement count. */
data class AirportCount(
    val icao: String,
    val name: String?,
    val count: Int,
)

/** Visited airport marker for the 2D network map. */
data class VisitedAirport(
    val icao: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val visitCount: Int,
)

/** Visited flight leg for the 2D network map. */
data class VisitedLeg(
    val departureIcao: String,
    val arrivalIcao: String,
    val fromLat: Double,
    val fromLon: Double,
    val toLat: Double,
    val toLon: Double,
    val arc: GeoArc,
)

/** UI state for the Stats Dashboard. */
sealed interface StatsUiState {

    /** Logbook and fleet data are loading. */
    data object Loading : StatsUiState

    /** No flights have been logged yet. */
    data object Empty : StatsUiState

    /** The calculated flight statistics dashboard. */
    @Immutable
    data class Success(
        val timeframe: StatsTimeframe,
        val chartMetric: ChartMetric,
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
    ) : StatsUiState
}
