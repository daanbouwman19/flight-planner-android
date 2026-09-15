package com.github.daanbouwman.flightplanner.routing

import com.github.daanbouwman.flightplanner.model.AircraftSpec
import com.github.daanbouwman.flightplanner.model.FlightRecord
import com.github.daanbouwman.flightplanner.model.FlightStatistics

/** How many times one ICAO appeared in the role being counted. */
data class AirportTally(
    val icao: String,
    val count: Int,
)

/**
 * The one-pass statistics with their identities intact: the winning
 * [FlightRecord]s rather than `"EHAM to KJFK"`, the winning ICAO with its count
 * rather than the code alone, and the most-flown aircraft *id* rather than a
 * name that needs a fleet to resolve.
 *
 * [FlightStatistics] is the desktop-shaped projection of this and is produced
 * from it by [FlightStatisticsCalculator.calculate]; the dashboard maps it onto
 * its own UI shapes. Every tie-break documented on [FlightStatisticsCalculator]
 * applies unchanged, because both are the same scan.
 *
 * The defaults are the empty-log answer and line up with [FlightStatistics]'s,
 * so an empty log projects to `FlightStatistics()` without a special case.
 */
data class DetailedFlightStatistics(
    val totalFlights: Int = 0,
    val totalDistanceNm: Int = 0,
    val averageFlightDistanceNm: Double = 0.0,
    val mostFlownAircraftId: Int? = null,
    val longestFlight: FlightRecord? = null,
    val shortestFlight: FlightRecord? = null,
    val favoriteDepartureAirport: AirportTally? = null,
    val favoriteArrivalAirport: AirportTally? = null,
    val mostVisitedAirport: AirportTally? = null,
)

/**
 * Computes logbook statistics in one pass, porting the desktop app's
 * `StatsAccumulator`.
 *
 * This is the implementation the app ships, not a test-only reference.
 * `StatsViewModel` calls [calculateDetailed] on the timeframe-filtered log and
 * maps the result onto the dashboard's shapes; `FlightLogDao` has no aggregate
 * queries beyond a row count, so nothing else computes these figures and there
 * is no second implementation for this one to drift from. [calculate] is the
 * desktop-shaped view of the same pass — formatted legs, an aircraft name
 * resolved through a lookup — and exists so the port can be asserted against
 * the desktop app's `FlightStatistics` field for field.
 *
 * ## The tie-breaks are the specification
 *
 * Every "most" and "favourite" figure has to resolve ties somehow, and the
 * desktop app's choices are observable behaviour that this port must agree
 * with. They are reproduced here exactly, once, for both entry points:
 *
 *  - **Most-visited / favourite airport:** highest count wins; on a tie the
 *    **alphabetically first** ICAO wins.
 *  - **Most-flown aircraft:** highest count wins; on a tie the **lowest aircraft
 *    id** wins.
 *  - **Shortest flight** keeps the **first** record of an equal-distance pair.
 *  - **Longest flight** keeps the **last** record of an equal-distance pair.
 *
 * That last asymmetry looks like a bug and is not. It falls out of Rust's
 * `min_by_key` returning the first extreme and `max_by_key` returning the last,
 * which the original comments call out explicitly (`use <` for min, `use >=` for
 * max). Two different logged flights of identical length therefore report
 * different winners depending on which end of the range is asked about. Do not
 * "fix" it to be symmetric: the desktop app is the behavioural reference, and
 * the dashboard would then silently disagree with it on every duplicate
 * distance.
 *
 * A missing [FlightRecord.distanceNm] counts as `0`, matching the desktop's
 * `unwrap_or(0)`, so a flight logged without a distance is the shortest flight
 * rather than being skipped.
 *
 * ICAO codes are counted exactly as the record stores them, as the desktop
 * does. The app writes them from the airport database, which holds them
 * upper-case, so no normalisation is needed here and none is applied.
 */
object FlightStatisticsCalculator {

    /**
     * The single scan over the log. Everything else in this object is a
     * projection of its result.
     *
     * @param records the logbook, in any order.
     */
    fun calculateDetailed(records: List<FlightRecord>): DetailedFlightStatistics {
        if (records.isEmpty()) return DetailedFlightStatistics()

        var totalDistance = 0
        var minDistance = Int.MAX_VALUE
        var maxDistance = Int.MIN_VALUE
        var shortest: FlightRecord? = null
        var longest: FlightRecord? = null

        val aircraftCounts = HashMap<Int, Int>()
        val departureCounts = HashMap<String, Int>()
        val arrivalCounts = HashMap<String, Int>()
        val airportCounts = HashMap<String, Int>()

        for (record in records) {
            val distance = record.distanceNm ?: 0
            totalDistance += distance

            // Strict `<` keeps the first minimum; `>=` lets a later equal maximum
            // take over. See the class KDoc — this asymmetry is intentional.
            if (distance < minDistance) {
                minDistance = distance
                shortest = record
            }
            if (distance >= maxDistance) {
                maxDistance = distance
                longest = record
            }

            aircraftCounts.increment(record.aircraftId)
            departureCounts.increment(record.departureIcao)
            arrivalCounts.increment(record.arrivalIcao)
            // An airport counts once per leg it appears on, so a round trip
            // through it counts twice.
            airportCounts.increment(record.departureIcao)
            airportCounts.increment(record.arrivalIcao)
        }

        return DetailedFlightStatistics(
            totalFlights = records.size,
            totalDistanceNm = totalDistance,
            averageFlightDistanceNm = totalDistance.toDouble() / records.size,
            mostFlownAircraftId = aircraftCounts.bestEntry()?.first,
            longestFlight = longest,
            shortestFlight = shortest,
            favoriteDepartureAirport = departureCounts.bestTally(),
            favoriteArrivalAirport = arrivalCounts.bestTally(),
            mostVisitedAirport = airportCounts.bestTally(),
        )
    }

    /**
     * The desktop app's `FlightStatistics`, projected from [calculateDetailed].
     *
     * @param records the logbook, in any order.
     * @param aircraftLookup resolves an aircraft id to its spec; return `null`
     *   for an id no longer in the fleet, which leaves
     *   [FlightStatistics.mostFlownAircraft] null rather than inventing a name.
     */
    fun calculate(
        records: List<FlightRecord>,
        aircraftLookup: (Int) -> AircraftSpec?,
    ): FlightStatistics {
        val detailed = calculateDetailed(records)
        return FlightStatistics(
            totalFlights = detailed.totalFlights,
            totalDistanceNm = detailed.totalDistanceNm,
            mostFlownAircraft = detailed.mostFlownAircraftId?.let(aircraftLookup)?.displayName,
            mostVisitedAirport = detailed.mostVisitedAirport?.icao,
            averageFlightDistanceNm = detailed.averageFlightDistanceNm,
            longestFlight = detailed.longestFlight?.leg(),
            shortestFlight = detailed.shortestFlight?.leg(),
            favoriteDepartureAirport = detailed.favoriteDepartureAirport?.icao,
            favoriteArrivalAirport = detailed.favoriteArrivalAirport?.icao,
        )
    }

    /** Convenience overload that resolves ids against a fleet list. */
    fun calculate(records: List<FlightRecord>, fleet: List<AircraftSpec>): FlightStatistics {
        val byId = fleet.associateBy(AircraftSpec::id)
        return calculate(records) { byId[it] }
    }

    /** `"EHAM to KJFK"` — the desktop app's format for a named leg. */
    private fun FlightRecord.leg(): String = "$departureIcao to $arrivalIcao"

    private fun <K> HashMap<K, Int>.increment(key: K) {
        this[key] = (this[key] ?: 0) + 1
    }

    private fun Map<String, Int>.bestTally(): AirportTally? =
        bestEntry()?.let { (icao, count) -> AirportTally(icao, count) }

    /**
     * The entry with the highest count, ties broken by the **smallest** key.
     *
     * Written as an explicit scan rather than `maxByOrNull` because the tie-break
     * is the whole point: `maxByOrNull` returns the *first* maximum in iteration
     * order, and a `HashMap`'s iteration order is not something to build
     * behaviour on.
     */
    private fun <K : Comparable<K>> Map<K, Int>.bestEntry(): Pair<K, Int>? {
        var bestKey: K? = null
        var bestCount = 0
        for ((key, count) in this) {
            val current = bestKey
            if (current == null || count > bestCount || (count == bestCount && key < current)) {
                bestKey = key
                bestCount = count
            }
        }
        return bestKey?.let { it to bestCount }
    }
}
