package com.github.daanbouwman.flightplanner.routing

import com.github.daanbouwman.flightplanner.model.AircraftSpec
import java.time.LocalDate
import kotlin.random.Random

/**
 * The multiplier that keeps this draw independent of [dailyChallengeSeed]'s.
 *
 * Both seeds start from the same epoch day, and `Random(n)` splits a `Long`
 * into the two words that start the sequence — so two streams seeded from the
 * same day would agree wherever their first draws happen to line up, and the
 * day's aircraft would shadow the day's challenge. Multiplying by the 64-bit
 * golden-ratio constant (`0x9E3779B97F4A7C15`, the mixer `SplittableRandom`
 * and Fibonacci hashing both use) spreads one increment of the epoch day
 * across the whole word, so consecutive days start unrelated sequences.
 *
 * Odd, so the multiplication is a bijection on `Long`: two days can never
 * collide on one seed.
 */
private const val GOLDEN_GAMMA: Long = -0x61C8864680B583EBL

/**
 * The seed for [date]'s aircraft — its epoch day, mixed. See [GOLDEN_GAMMA].
 *
 * Overflow is intended and is what makes this a mix rather than a scaling;
 * Kotlin's `Long` multiplication wraps, which is the defined behaviour this
 * relies on.
 */
fun dailyAircraftSeed(date: LocalDate): Long = date.toEpochDay() * GOLDEN_GAMMA

/**
 * One airframe for [date], the same for everyone with the same fleet.
 *
 * The "Aircraft of the day" widget's whole algorithm, and it rests on the same
 * two things [dailyChallenge] does: the seed is the date alone, and the fleet
 * is sorted by id first so the answer does not depend on the order a DAO
 * returned the rows in. Adding or removing an airframe changes the day's pick —
 * the candidate set is different — and that is accepted for the same reason it
 * is there: the pick is a property of the fleet you have.
 *
 * **The draw is uniform, and [AircraftSpec.flown] is reported rather than
 * weighted.** Biasing towards airframes you have never flown would be a nicer
 * nudge and a worse widget: marking the day's aircraft flown would change which
 * aircraft the day's aircraft *is*, so the card would swap under the user the
 * moment they acted on it. [dailyChallenge] protects the same property by
 * generating in `AllAircraft` mode.
 *
 * Null only when [fleet] is empty; unlike a route, a pick from a non-empty list
 * cannot fail.
 */
fun dailyAircraft(fleet: List<AircraftSpec>, date: LocalDate): AircraftSpec? {
    if (fleet.isEmpty()) return null
    val candidates = fleet.sortedBy { it.id }
    return candidates[Random(dailyAircraftSeed(date)).nextInt(candidates.size)]
}
