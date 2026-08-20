package com.github.daanbouwman.flightplanner.ui.logbook

import com.github.daanbouwman.flightplanner.model.AircraftSpec
import com.github.daanbouwman.flightplanner.model.FlightRecord
import com.github.daanbouwman.flightplanner.routing.GreatCircle
import java.time.LocalDate
import java.time.YearMonth

/**
 * Resolves one logged flight against the fleet, or drops it.
 *
 * The only way this returns null is a date Room could not have produced —
 * every write path in the app stores `LocalDate.now().toString()`, so a parse
 * failure here means a row from outside the app's own writes. Excluding it is
 * better than crashing the screen over one bad row.
 *
 * A missing [AircraftSpec] — the airframe was since deleted from the fleet —
 * is not the same kind of problem: the flight still happened, so the row
 * survives with [LogbookRow.aircraftDisplayName] left null for the screen to
 * fall back on. [com.github.daanbouwman.flightplanner.routing.GreatCircle.flightTime]
 * already falls back to the default cruise speed when none is known, so a
 * missing aircraft still gets a flight time rather than none at all.
 */
fun FlightRecord.toLogbookRowOrNull(aircraftById: Map<Int, AircraftSpec>): LogbookRow? {
    val parsedDate = runCatching { LocalDate.parse(date) }.getOrNull() ?: return null
    val aircraft = aircraftById[aircraftId]
    return LogbookRow(
        id = id,
        departureIcao = departureIcao,
        arrivalIcao = arrivalIcao,
        date = parsedDate,
        aircraftId = aircraftId,
        aircraftDisplayName = aircraft?.displayName,
        distanceNm = distanceNm,
        flightTime = distanceNm?.let {
            GreatCircle.flightTime(distanceNm = it.toDouble(), cruiseSpeedKt = aircraft?.cruiseSpeedKt ?: 0)
        },
    )
}

/**
 * Groups rows by month, newest month first.
 *
 * Correct only because [this] arrives newest-first, date descending — the
 * order [com.github.daanbouwman.flightplanner.core.database.repository.LogbookRepository.observeAll]
 * returns. `groupBy` is backed by a `LinkedHashMap`, so it preserves the order
 * keys are first seen in and the relative order of the rows within each group
 * — which is what makes one pass enough, with no separate sort.
 */
fun List<LogbookRow>.groupByMonth(): List<LogbookMonthGroup> =
    groupBy { YearMonth.from(it.date) }.map { (month, rows) -> LogbookMonthGroup(key = month, rows = rows) }

/**
 * Flights, nautical miles and hours logged in [year].
 *
 * A flight with no recorded distance still counts toward
 * [LogbookSummary.flights] — it happened — but contributes nothing to
 * [LogbookSummary.distanceNm] or [LogbookSummary.minutesFlown], which have
 * nothing to sum for it.
 */
fun List<LogbookRow>.summarizeYear(year: Int): LogbookSummary {
    var flights = 0
    var distanceNm = 0
    var minutesFlown = 0
    for (row in this) {
        if (row.date.year != year) continue
        flights++
        val distance = row.distanceNm ?: continue
        distanceNm += distance
        row.flightTime?.let { minutesFlown += it.hours * 60 + it.minutes }
    }
    return LogbookSummary(flights = flights, distanceNm = distanceNm, minutesFlown = minutesFlown)
}
