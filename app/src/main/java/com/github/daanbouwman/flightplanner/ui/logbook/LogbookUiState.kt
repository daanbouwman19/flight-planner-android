package com.github.daanbouwman.flightplanner.ui.logbook

import androidx.compose.runtime.Immutable
import com.github.daanbouwman.flightplanner.routing.FlightTime
import java.time.LocalDate
import java.time.YearMonth

/**
 * One logged flight, ready for a row to draw.
 *
 * A `data class`, deliberately **not** [com.github.daanbouwman.flightplanner.ui.plan.RouteRow]'s
 * identity-on-[id]-only pattern. That pattern is safe for `RouteRow` because a
 * route's id is minted fresh per generated batch and never reused with
 * different content. [id] here is the stable `FlightRecord.id`, and this
 * row's *other* fields can legitimately change across recomputes without the
 * id changing — [aircraftDisplayName] and [flightTime] are resolved against
 * the fleet, not the flight record, so an id-only `equals()` would let a real
 * content change (a renamed or newly-resolved airframe) be silently
 * conflated away by `LogbookViewModel.uiState`'s `StateFlow`, which skips
 * emitting a value that `equals()` the one it already holds.
 */
@Immutable
data class LogbookRow(
    val id: Long,
    val departureIcao: String,
    val arrivalIcao: String,
    val date: LocalDate,
    val aircraftId: Int,
    /** Null when the airframe has since been deleted from the fleet. The flight still happened. */
    val aircraftDisplayName: String?,
    val distanceNm: Int?,
    val flightTime: FlightTime?,
)

/** One month's rows. [LogbookRow.groupByMonth] is what produces these, newest month first. */
@Immutable
data class LogbookMonthGroup(val key: YearMonth, val rows: List<LogbookRow>)

/** Flights, nautical miles and hours logged in one calendar year. See [summarizeYear]. */
@Immutable
data class LogbookSummary(
    val flights: Int = 0,
    val distanceNm: Int = 0,
    val minutesFlown: Int = 0,
)

/** Where the logbook read has got to. */
enum class LogbookStatus { Loading, Ready }

/** Everything the Logbook segment of Profile renders. */
@Immutable
data class LogbookUiState(
    val groups: List<LogbookMonthGroup> = emptyList(),
    val summary: LogbookSummary = LogbookSummary(),
    val status: LogbookStatus = LogbookStatus.Loading,
) {
    /**
     * A finished read that found nothing. Distinct from [LogbookStatus.Loading]:
     * the log has genuinely never had a flight added to it, which is a
     * different message than "still reading".
     */
    val isEmpty: Boolean
        get() = status == LogbookStatus.Ready && groups.isEmpty()
}
