package com.github.daanbouwman.flightplanner.ui.stats

import com.github.daanbouwman.flightplanner.model.AircraftSpec
import com.github.daanbouwman.flightplanner.model.Airport
import com.github.daanbouwman.flightplanner.model.FlightRecord
import com.github.daanbouwman.flightplanner.routing.RouteArc
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Pure grouping and ranking logic behind the Stats Dashboard. See [StatsUiState] for the shapes it produces. */
object StatsGrouping {

    private val MonthLabelFormatter = DateTimeFormatter.ofPattern("MMM", Locale.US)

    /**
     * Filters logbook records by the specified timeframe.
     */
    fun filterByTimeframe(
        records: List<FlightRecord>,
        timeframe: StatsTimeframe,
        today: LocalDate = LocalDate.now(),
    ): List<FlightRecord> {
        if (records.isEmpty()) return emptyList()
        return when (timeframe) {
            StatsTimeframe.ALL_TIME -> records
            StatsTimeframe.THIS_YEAR -> {
                val currentYear = today.year
                records.filter { record ->
                    parseRecordDate(record.date)?.year == currentYear
                }
            }
            StatsTimeframe.PAST_12_MONTHS -> {
                val startYearMonth = YearMonth.from(today.minusMonths(11))
                val endYearMonth = YearMonth.from(today)
                records.filter { record ->
                    val date = parseRecordDate(record.date) ?: return@filter false
                    val ym = YearMonth.from(date)
                    !ym.isBefore(startYearMonth) && !ym.isAfter(endYearMonth)
                }
            }
        }
    }

    /**
     * Builds monthly activity buckets for the bar chart.
     * Always fills missing months so the timeline is continuous.
     */
    fun buildMonthlyActivity(
        records: List<FlightRecord>,
        timeframe: StatsTimeframe,
        today: LocalDate = LocalDate.now(),
    ): List<MonthlyActivity> {
        val monthsRange = when (timeframe) {
            StatsTimeframe.THIS_YEAR -> {
                val year = today.year
                (1..12).map { month -> YearMonth.of(year, month) }
            }
            StatsTimeframe.PAST_12_MONTHS -> {
                (11 downTo 0).map { offset -> YearMonth.from(today.minusMonths(offset.toLong())) }
            }
            StatsTimeframe.ALL_TIME -> {
                if (records.isEmpty()) {
                    (11 downTo 0).map { offset -> YearMonth.from(today.minusMonths(offset.toLong())) }
                } else {
                    val parsedDates = records.mapNotNull { parseRecordDate(it.date) }
                    if (parsedDates.isEmpty()) {
                        (11 downTo 0).map { offset -> YearMonth.from(today.minusMonths(offset.toLong())) }
                    } else {
                        val minDate = parsedDates.minOrNull() ?: today
                        val startYm = YearMonth.from(minDate)
                        val endYm = YearMonth.from(today)
                        generateYearMonths(startYm, endYm)
                    }
                }
            }
        }

        val grouped = HashMap<YearMonth, Pair<Int, Int>>()
        for (record in records) {
            val date = parseRecordDate(record.date) ?: continue
            val ym = YearMonth.from(date)
            val current = grouped[ym] ?: Pair(0, 0)
            val distance = record.distanceNm ?: 0
            grouped[ym] = Pair(current.first + 1, current.second + distance)
        }

        return monthsRange.map { ym ->
            val stats = grouped[ym] ?: Pair(0, 0)
            MonthlyActivity(
                yearMonth = ym,
                monthLabel = ym.format(MonthLabelFormatter),
                flightCount = stats.first,
                distanceNm = stats.second,
            )
        }
    }

    /**
     * Ranks top aircraft by flight count, breaking ties by lowest aircraft ID.
     */
    fun computeTopAircraft(
        records: List<FlightRecord>,
        fleet: List<AircraftSpec>,
        limit: Int = 5,
    ): List<TopAircraftStat> {
        if (records.isEmpty() || fleet.isEmpty()) return emptyList()
        val fleetById = fleet.associateBy(AircraftSpec::id)

        val flightCounts = HashMap<Int, Int>()
        val distanceTotals = HashMap<Int, Int>()

        for (record in records) {
            flightCounts[record.aircraftId] = (flightCounts[record.aircraftId] ?: 0) + 1
            distanceTotals[record.aircraftId] =
                (distanceTotals[record.aircraftId] ?: 0) + (record.distanceNm ?: 0)
        }

        return flightCounts.entries
            .mapNotNull { (aircraftId, count) ->
                val spec = fleetById[aircraftId] ?: return@mapNotNull null
                TopAircraftStat(
                    aircraft = spec,
                    flightCount = count,
                    totalDistanceNm = distanceTotals[aircraftId] ?: 0,
                )
            }
            .sortedWith(
                compareByDescending<TopAircraftStat> { it.flightCount }
                    .thenBy { it.aircraft.id },
            )
            .take(limit)
    }

    /**
     * Computes favorite departure, favorite arrival, and most visited airport.
     * Ties broken by alphabetically-first ICAO code.
     */
    fun computeAirportHighlights(
        records: List<FlightRecord>,
        airportMap: Map<String, Airport>,
    ): Triple<AirportCount?, AirportCount?, AirportCount?> {
        if (records.isEmpty()) return Triple(null, null, null)

        val departureCounts = HashMap<String, Int>()
        val arrivalCounts = HashMap<String, Int>()
        val totalVisits = HashMap<String, Int>()

        for (record in records) {
            val dep = record.departureIcao.trim().uppercase()
            val arr = record.arrivalIcao.trim().uppercase()

            departureCounts[dep] = (departureCounts[dep] ?: 0) + 1
            arrivalCounts[arr] = (arrivalCounts[arr] ?: 0) + 1
            totalVisits[dep] = (totalVisits[dep] ?: 0) + 1
            totalVisits[arr] = (totalVisits[arr] ?: 0) + 1
        }

        fun bestAirport(counts: Map<String, Int>): AirportCount? {
            var bestIcao: String? = null
            var bestCount = 0
            for ((icao, count) in counts) {
                val current = bestIcao
                if (current == null || count > bestCount || (count == bestCount && icao < current)) {
                    bestIcao = icao
                    bestCount = count
                }
            }
            return bestIcao?.let { icao ->
                AirportCount(
                    icao = icao,
                    name = airportMap[icao]?.name,
                    count = bestCount,
                )
            }
        }

        val favDeparture = bestAirport(departureCounts)
        val favArrival = bestAirport(arrivalCounts)
        val mostVisited = bestAirport(totalVisits)

        return Triple(favDeparture, favArrival, mostVisited)
    }

    /**
     * Finds the longest flight, keeping the last encounter on equal distance.
     */
    fun computeLongestFlight(records: List<FlightRecord>): LegStat? {
        if (records.isEmpty()) return null
        var maxDistance = Int.MIN_VALUE
        var longest: FlightRecord? = null

        for (record in records) {
            val distance = record.distanceNm ?: 0
            if (distance >= maxDistance) {
                maxDistance = distance
                longest = record
            }
        }

        return longest?.let {
            LegStat(
                departureIcao = it.departureIcao,
                arrivalIcao = it.arrivalIcao,
                aircraftId = it.aircraftId,
                distanceNm = it.distanceNm ?: 0,
            )
        }
    }

    /**
     * Finds the shortest flight, keeping the first encounter on equal distance.
     */
    fun computeShortestFlight(records: List<FlightRecord>): LegStat? {
        if (records.isEmpty()) return null
        var minDistance = Int.MAX_VALUE
        var shortest: FlightRecord? = null

        for (record in records) {
            val distance = record.distanceNm ?: 0
            if (distance < minDistance) {
                minDistance = distance
                shortest = record
            }
        }

        return shortest?.let {
            LegStat(
                departureIcao = it.departureIcao,
                arrivalIcao = it.arrivalIcao,
                aircraftId = it.aircraftId,
                distanceNm = it.distanceNm ?: 0,
            )
        }
    }

    /**
     * Builds visited airport coordinates and unique flight leg arcs for the 2D map.
     */
    fun buildVisitedNetwork(
        records: List<FlightRecord>,
        airportMap: Map<String, Airport>,
    ): Pair<List<VisitedAirport>, List<VisitedLeg>> {
        if (records.isEmpty() || airportMap.isEmpty()) return Pair(emptyList(), emptyList())

        val airportVisits = HashMap<String, Int>()
        val seenLegs = HashSet<Pair<String, String>>()
        val visitedLegs = ArrayList<VisitedLeg>()

        for (record in records) {
            val depIcao = record.departureIcao.trim().uppercase()
            val arrIcao = record.arrivalIcao.trim().uppercase()

            airportVisits[depIcao] = (airportVisits[depIcao] ?: 0) + 1
            airportVisits[arrIcao] = (airportVisits[arrIcao] ?: 0) + 1

            val legKey = if (depIcao < arrIcao) depIcao to arrIcao else arrIcao to depIcao
            if (seenLegs.add(legKey)) {
                val depAirport = airportMap[depIcao]
                val arrAirport = airportMap[arrIcao]
                if (depAirport != null && arrAirport != null) {
                    val arc = RouteArc.sampleGeographic(
                        depLat = depAirport.latitude,
                        depLon = depAirport.longitude,
                        destLat = arrAirport.latitude,
                        destLon = arrAirport.longitude,
                        samples = 32,
                    )
                    visitedLegs.add(
                        VisitedLeg(
                            departureIcao = depIcao,
                            arrivalIcao = arrIcao,
                            fromLat = depAirport.latitude,
                            fromLon = depAirport.longitude,
                            toLat = arrAirport.latitude,
                            toLon = arrAirport.longitude,
                            arc = arc,
                        ),
                    )
                }
            }
        }

        val visitedAirports = airportVisits.mapNotNull { (icao, count) ->
            val airport = airportMap[icao] ?: return@mapNotNull null
            VisitedAirport(
                icao = icao,
                name = airport.name,
                latitude = airport.latitude,
                longitude = airport.longitude,
                visitCount = count,
            )
        }

        return Pair(visitedAirports, visitedLegs)
    }

    fun parseRecordDate(dateString: String): LocalDate? =
        runCatching { LocalDate.parse(dateString) }.getOrNull()

    private fun generateYearMonths(start: YearMonth, end: YearMonth): List<YearMonth> {
        val result = ArrayList<YearMonth>()
        var current = start
        while (!current.isAfter(end)) {
            result.add(current)
            current = current.plusMonths(1)
        }
        return result
    }
}
