package com.github.daanbouwman.flightplanner.routing

import com.github.daanbouwman.flightplanner.model.AircraftSpec
import java.time.LocalDate
import kotlin.random.Random

/**
 * How many draws one day's challenge gets before giving up.
 *
 * [RouteGenerator.generate] consumes its `Random` sequentially and drops the
 * attempts that find no destination, so a single draw can legitimately come
 * back empty for a short-range airframe in a sparse region. Eight attempts
 * make an empty day vanishingly unlikely while keeping the answer
 * deterministic: attempt *n* depends only on attempts 1 to *n*−1, so the first
 * survivor is the same on every device that asks.
 */
const val DAILY_CHALLENGE_ATTEMPTS: Int = 8

/**
 * The seed for [date]'s challenge — its epoch day, as a `Long`.
 *
 * `toEpochDay()` is a `Long` and must stay one; narrowing it to an `Int` would
 * still be deterministic and would still be a different sequence from the one
 * every other device draws.
 */
fun dailyChallengeSeed(date: LocalDate): Long = date.toEpochDay()

/**
 * One route for [date], the same for everyone with the same fleet.
 *
 * This is the "Today's challenge" widget's whole algorithm. The determinism
 * rests on three things: the seed is the date alone; the fleet is sorted by id
 * so the generator's candidate array does not depend on the order a DAO
 * returned it in; and the mode is [RouteMode.AllAircraft], so marking an
 * airframe flown during the day does not change the day's route. Adding or
 * removing an airframe *does* change it — the candidate set is different — and
 * that is accepted rather than worked around: the challenge is a property of
 * the fleet you have.
 *
 * Null when the fleet is empty or every attempt failed; the caller says so on
 * the widget rather than inventing a route.
 */
suspend fun RouteGenerator.dailyChallenge(
    fleet: List<AircraftSpec>,
    date: LocalDate,
    icaoOnly: Boolean = false,
): GeneratedRoute? {
    if (fleet.isEmpty()) return null
    return generate(
        RouteRequest(
            mode = RouteMode.AllAircraft,
            fleet = fleet.sortedBy { it.id },
            amount = DAILY_CHALLENGE_ATTEMPTS,
            icaoOnly = icaoOnly,
        ),
        Random(dailyChallengeSeed(date)),
    ).firstOrNull()
}
