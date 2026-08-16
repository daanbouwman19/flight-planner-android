package com.github.daanbouwman.flightplanner.model

/** One logged flight. */
data class FlightRecord(
    val id: Long,
    val departureIcao: String,
    val arrivalIcao: String,
    val aircraftId: Int,
    /** ISO-8601 date. */
    val date: String,
    val distanceNm: Int?,
)

/**
 * Aggregate statistics over the logbook.
 *
 * The nine fields mirror the desktop app's `FlightStatistics` exactly, including
 * its tie-breaking rules, which are reproduced in the calculator rather than
 * left to chance:
 *  - "most"/"favourite" ties resolve to the alphabetically first key
 *  - [shortestFlight] keeps the first minimum encountered, [longestFlight] the last maximum
 */
data class FlightStatistics(
    val totalFlights: Int = 0,
    val totalDistanceNm: Int = 0,
    val mostFlownAircraft: String? = null,
    val mostVisitedAirport: String? = null,
    val averageFlightDistanceNm: Double = 0.0,
    val longestFlight: String? = null,
    val shortestFlight: String? = null,
    val favoriteDepartureAirport: String? = null,
    val favoriteArrivalAirport: String? = null,
)
