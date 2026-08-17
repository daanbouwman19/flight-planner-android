package com.github.daanbouwman.flightplanner.routing

import com.github.daanbouwman.flightplanner.model.AircraftSpec
import com.github.daanbouwman.flightplanner.model.FlightRecord
import com.github.daanbouwman.flightplanner.model.FlightStatistics

/**
 * Computes logbook statistics in one pass, porting the desktop app's
 * `StatsAccumulator`.
 *
 * This is the *reference* implementation. Production reads the same numbers from
 * SQL aggregates in `FlightLogDao`, which stay reactive and never load the whole
 * log into memory; this exists so those aggregates can be cross-checked against
 * something small enough to read in full.
 *
 * ## The tie-breaks are the specification
 *
 * Every "most" and "favourite" figure has to resolve ties somehow, and the
 * desktop app's choices are observable behaviour that the two implementations
 * must agree on. They are reproduced here exactly:
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
 * "fix" it to be symmetric without also changing `FlightLogDao` and the desktop
 * app — otherwise the reference and the production path silently disagree on
 * every duplicate distance.
 *
 * A missing [FlightRecord.distanceNm] counts as `0`, matching the desktop's
 * `unwrap_or(0)`, so a flight logged without a distance is the shortest flight
 * rather than being skipped.
 */
object FlightStatisticsCalculator {

    /**
     * @param records the logbook, in any order.
     * @param aircraftLookup resolves an aircraft id to its spec; return `null`
     *   for an id no longer in the fleet, which leaves
     *   [FlightStatistics.mostFlownAircraft] null rather than inventing a name.
     */
    fun calculate(
        records: List<FlightRecord>,
        aircraftLookup: (Int) -> AircraftSpec?,
    ): FlightStatistics {
        if (records.isEmpty()) return FlightStatistics()

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

        val mostFlownAircraftId = aircraftCounts.bestKey()

        return FlightStatistics(
            totalFlights = records.size,
            totalDistanceNm = totalDistance,
            mostFlownAircraft = mostFlownAircraftId?.let(aircraftLookup)?.displayName,
            mostVisitedAirport = airportCounts.bestKey(),
            averageFlightDistanceNm = totalDistance.toDouble() / records.size,
            longestFlight = longest?.leg(),
            shortestFlight = shortest?.leg(),
            favoriteDepartureAirport = departureCounts.bestKey(),
            favoriteArrivalAirport = arrivalCounts.bestKey(),
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

    /**
     * The key with the highest count, ties broken by the **smallest** key.
     *
     * Written as an explicit scan rather than `maxByOrNull` because the tie-break
     * is the whole point: `maxByOrNull` returns the *first* maximum in iteration
     * order, and a `HashMap`'s iteration order is not something to build
     * behaviour on.
     */
    private fun <K : Comparable<K>> Map<K, Int>.bestKey(): K? {
        var bestKey: K? = null
        var bestCount = 0
        for ((key, count) in this) {
            val current = bestKey
            if (current == null || count > bestCount || (count == bestCount && key < current)) {
                bestKey = key
                bestCount = count
            }
        }
        return bestKey
    }
}
