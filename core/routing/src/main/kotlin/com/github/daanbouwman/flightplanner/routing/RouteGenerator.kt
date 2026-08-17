package com.github.daanbouwman.flightplanner.routing

import com.github.daanbouwman.flightplanner.model.AircraftSpec
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/** How many routes a batch produces, matching the desktop app's `GENERATE_AMOUNT`. */
const val DEFAULT_ROUTE_BATCH: Int = 50

/** Which airframes a batch may draw from. */
sealed interface RouteMode {
    /** Any airframe in the fleet. */
    data object AllAircraft : RouteMode

    /** Only airframes not yet flown. */
    data object NotFlownOnly : RouteMode

    /** One specific airframe. */
    data class Specific(val aircraftId: Int) : RouteMode
}

data class RouteRequest(
    val mode: RouteMode,
    val fleet: List<AircraftSpec>,
    val amount: Int = DEFAULT_ROUTE_BATCH,
    /** When set, every route departs from this airport. */
    val lockedDepartureIcao: String? = null,
    /** Suppresses trivially short hops. Absent in the desktop app. */
    val minDistanceNm: Int? = null,
    val requireHardSurface: Boolean = false,
    /** Restricts both ends to airports with a real ICAO code. */
    val icaoOnly: Boolean = false,
)

/** A generated route. Slots refer to the [AirportIndex] it was generated from. */
data class GeneratedRoute(
    val aircraft: AircraftSpec,
    val departureSlot: Int,
    val destinationSlot: Int,
    val distanceNm: Int,
    val departureRunwayFt: Int,
    val destinationRunwayFt: Int,
)

data class RouteGeneratorConfig(
    /**
     * Above this range, rejection sampling beats a spatial scan: enough of the
     * planet is reachable that a random candidate is usually in range.
     */
    val rejectionSamplingThresholdNm: Int = 500,
    val rejectionSamplingAttempts: Int = 128,
)

/**
 * Generates flyable routes.
 *
 * Ported from the desktop application's `routes.rs` and `airport.rs`, including
 * its hybrid destination search, but with two genuine bugs from the original
 * fixed — see [randomDestination].
 */
class RouteGenerator(
    private val index: AirportIndex,
    private val config: RouteGeneratorConfig = RouteGeneratorConfig(),
    /**
     * Where a batch runs. Defaults to [Dispatchers.Default], which is what it
     * should be in production — the work is pure CPU.
     *
     * It is a parameter so that a caller under test can substitute a scheduler it
     * controls. Hardcoding the dispatcher makes generation invisible to
     * `advanceUntilIdle`, so a ViewModel test can only assert the state *before*
     * the batch lands and has to fall back to sleeping and hoping.
     */
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {

    /**
     * Per-airframe values hoisted out of the per-route loop.
     *
     * The runway binary search in particular is pure waste to repeat: with a
     * fleet smaller than the batch size, the same airframe is drawn many times.
     */
    private data class Candidate(
        val aircraft: AircraftSpec,
        val startSlot: Int,
        val threshold: Float,
        val maxLatDeltaRad: Float,
    )

    /**
     * Generates up to [RouteRequest.amount] routes.
     *
     * Note this deliberately does *not* fan out across coroutines the way the
     * desktop version fans out across rayon. One route is a binary search plus
     * at most a few hundred cached-trigonometry comparisons; a whole batch of
     * fifty completes well inside a millisecond, so dispatching fifty coroutines
     * would cost more than the work itself. The batch runs on
     * [Dispatchers.Default] as a single unit and checks for cancellation between
     * routes.
     */
    suspend fun generate(request: RouteRequest, random: Random = Random.Default): List<GeneratedRoute> =
        withContext(dispatcher) {
            val fleet = request.eligibleFleet()
            if (fleet.isEmpty() || request.amount <= 0 || index.size == 0) return@withContext emptyList()

            val lockedSlot = request.lockedDepartureIcao?.let { icao ->
                val slot = index.slotOf(icao)
                // An unknown departure yields nothing rather than silently
                // falling back to random departures, which would quietly ignore
                // what the user asked for.
                if (slot < 0) return@withContext emptyList()
                slot
            } ?: -1

            val candidates = fleet.map { it.toCandidate() }
            val routes = ArrayList<GeneratedRoute>(request.amount)

            repeat(request.amount) {
                coroutineContext.ensureActive()
                val candidate = candidates[random.nextInt(candidates.size)]
                generateOne(candidate, lockedSlot, request, random)?.let(routes::add)
            }
            routes
        }

    private fun RouteRequest.eligibleFleet(): List<AircraftSpec> = when (mode) {
        RouteMode.AllAircraft -> fleet
        RouteMode.NotFlownOnly -> fleet.filterNot { it.flown }
        is RouteMode.Specific -> fleet.filter { it.id == mode.aircraftId }
    }

    private fun AircraftSpec.toCandidate() = Candidate(
        aircraft = this,
        startSlot = index.firstSlotWithRunway(requiredRunwayFt),
        threshold = GreatCircle.thresholdFor(rangeNm),
        maxLatDeltaRad = GreatCircle.maxLatitudeDeltaRad(rangeNm),
    )

    private fun generateOne(
        candidate: Candidate,
        lockedSlot: Int,
        request: RouteRequest,
        random: Random,
    ): GeneratedRoute? {
        val departure = if (lockedSlot >= 0) {
            // A locked departure is honoured even if the airframe cannot really
            // depart from it; the UI surfaces the runway lengths so the user can
            // see the conflict rather than being handed an empty list.
            lockedSlot
        } else {
            pickDeparture(candidate, request, random) ?: return null
        }

        val destination = randomDestination(candidate, departure, request, random)
        if (destination < 0) return null

        val distance = GreatCircle.distanceNm(index, departure, destination)
        if (request.minDistanceNm != null && distance < request.minDistanceNm) return null

        return GeneratedRoute(
            aircraft = candidate.aircraft,
            departureSlot = departure,
            destinationSlot = destination,
            distanceNm = distance,
            departureRunwayFt = index.longestRunwayFt[departure],
            destinationRunwayFt = index.longestRunwayFt[destination],
        )
    }

    /** Uniformly picks a departure that satisfies the airframe and the filters. */
    private fun pickDeparture(candidate: Candidate, request: RouteRequest, random: Random): Int? {
        val start = candidate.startSlot
        if (start >= index.size) return null

        // Without extra filters the whole tail is eligible, so one draw suffices.
        if (!request.requireHardSurface && !request.icaoOnly) {
            return start + random.nextInt(index.size - start)
        }

        // With filters, sample a bounded number of times before giving up. The
        // eligible fraction is high in practice, so this converges quickly.
        repeat(FILTERED_DEPARTURE_ATTEMPTS) {
            val slot = start + random.nextInt(index.size - start)
            if (slot.satisfies(request)) return slot
        }
        return null
    }

    /**
     * Finds a destination in range, using the desktop app's hybrid strategy.
     *
     * For long-range aircraft, rejection sampling over the runway-filtered tail
     * is O(1) expected and beats enumerating a large neighbourhood. For short
     * range — or when sampling fails — it falls back to a latitude/longitude
     * window query, choosing uniformly by reservoir sampling so nothing is
     * allocated.
     *
     * Three defects in the original are fixed here:
     *
     *  1. **Antimeridian.** The Rust version builds the longitude window as
     *     `[lon - r, lon + r]` and hands it straight to the R-tree, with no
     *     wrapping. Near ±180° that window is half empty, so destinations from
     *     places like Fiji or western Alaska are silently skewed.
     *     [LatBandIndex.forEachInWindow] splits a wrapping window in two.
     *  2. **Poles.** The original divides the longitude radius by `cos(lat)`
     *     clamped to 0.001, which near the pole produces a half-width of up to
     *     1000°. Here a half-width that reaches 180° simply disables the
     *     longitude test, leaving the exact distance check to do the work.
     *  3. **Window too narrow.** The original scales by the *departure's*
     *     cosine, but a destination nearer the pole spans more longitude for the
     *     same ground distance, so corner destinations were being excluded. See
     *     [scanForDestination].
     *
     * @return a destination slot, or -1.
     */
    private fun randomDestination(
        candidate: Candidate,
        departureSlot: Int,
        request: RouteRequest,
        random: Random,
    ): Int {
        val start = candidate.startSlot
        if (start >= index.size) return -1

        val eligibleCount = index.size - start

        if (candidate.aircraft.rangeNm >= config.rejectionSamplingThresholdNm) {
            repeat(config.rejectionSamplingAttempts) {
                val slot = start + random.nextInt(eligibleCount)
                if (slot != departureSlot &&
                    abs(index.latRad[departureSlot] - index.latRad[slot]) <= candidate.maxLatDeltaRad &&
                    GreatCircle.withinThreshold(index, departureSlot, slot, candidate.threshold) &&
                    slot.satisfies(request)
                ) {
                    return slot
                }
            }
        }

        return scanForDestination(candidate, departureSlot, request, random)
    }

    /** Latitude-band scan with reservoir sampling; allocation-free. */
    private fun scanForDestination(
        candidate: Candidate,
        departureSlot: Int,
        request: RouteRequest,
        random: Random,
    ): Int {
        val range = candidate.aircraft.rangeNm
        val latHalfWidth = range / 60.0
        val depLat = index.latDeg[departureSlot]
        val depLon = index.lonDeg[departureSlot]

        // One degree of longitude shrinks as cos(latitude), and a destination
        // may sit a whole latHalfWidth closer to the pole than the departure —
        // so the bound has to use the *widest* latitude the window reaches, not
        // the departure's own. Using cos(depLat) makes the window marginally too
        // narrow and silently drops corner destinations, which is a defect the
        // desktop app has. Near the pole, widen to "everything" rather than
        // dividing by ~zero and let the exact distance test do the work.
        val worstLat = min(abs(depLat) + latHalfWidth, 90.0)
        val cosWorst = cos(Math.toRadians(worstLat))
        val lonHalfWidth = if (cosWorst < 1e-3) 180.0 else min(max(latHalfWidth / cosWorst, latHalfWidth), 180.0)

        val start = candidate.startSlot
        var chosen = -1
        var seen = 0

        index.bands.forEachInWindow(
            depLat - latHalfWidth,
            depLat + latHalfWidth,
            depLon,
            lonHalfWidth,
        ) { slot ->
            if (slot >= start &&
                slot != departureSlot &&
                GreatCircle.withinThreshold(index, departureSlot, slot, candidate.threshold) &&
                slot.satisfies(request)
            ) {
                // Reservoir sampling of size one: every qualifying slot has an
                // equal chance without collecting them into a list first.
                seen++
                if (random.nextInt(seen) == 0) chosen = slot
            }
        }
        return chosen
    }

    private fun Int.satisfies(request: RouteRequest): Boolean {
        if (request.icaoOnly && !index.hasIcaoCode(this)) return false
        if (request.requireHardSurface && !index.hasHardSurface(this)) return false
        return true
    }

    private companion object {
        const val FILTERED_DEPARTURE_ATTEMPTS = 64
    }
}
