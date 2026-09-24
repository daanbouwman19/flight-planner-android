package com.github.daanbouwman.flightplanner.wear.tile

import android.util.Log
import com.github.daanbouwman.flightplanner.model.AircraftSpec
import com.github.daanbouwman.flightplanner.routing.AirportIndex
import com.github.daanbouwman.flightplanner.wear.data.WearAirportData
import com.github.daanbouwman.flightplanner.wear.route.WatchRouteCard
import com.github.daanbouwman.flightplanner.wear.route.WatchRouteFeed
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** What the tile has to show. */
sealed interface TileChallenge {

    /** The day's route, already worded — the same card the face draws. */
    data class Ready(val date: LocalDate, val card: WatchRouteCard) : TileChallenge

    /**
     * No route today: an asset that would not read, an empty fleet, or eight
     * attempts that all failed. One state, as on the face, because the wearer
     * cannot act on the difference from a tile.
     */
    data object Unavailable : TileChallenge
}

/**
 * Today's challenge for the tile, computed once a day.
 *
 * The system asks a tile for its layout whenever it is scrolled to, not once a
 * day, and every answer here would otherwise decode the 1.5 MB index again to
 * produce the same route. So a [TileChallenge.Ready] is kept against its date
 * and handed back until the date moves. `@Singleton` so the memo outlives the
 * tile service, which the system unbinds and destroys between requests.
 *
 * A failure is **not** kept: the next request tries again, which is what a
 * transient read failure wants and costs nothing a permanent one does not
 * already cost.
 *
 * Nothing here may throw, for the same reason `:app`'s widget sources may not:
 * a tile whose request fails shows the system's error state until the next
 * refresh, and [TileChallenge.Unavailable] at least says what is going on.
 *
 * The primary constructor takes its reads as functions and is `internal`, so
 * `WatchChallengeSourceTest` composes it against an in-memory index — the shape
 * `WatchRouteFeedViewModel` has, for the same reason.
 */
@Singleton
class WatchChallengeSource internal constructor(
    private val loadIndex: suspend () -> AirportIndex,
    private val loadFleet: suspend () -> List<AircraftSpec>,
    private val generatorDispatcher: CoroutineDispatcher,
) {

    @Inject
    constructor(data: WearAirportData) : this(
        loadIndex = data::index,
        loadFleet = data::fleet,
        generatorDispatcher = Dispatchers.Default,
    )

    /** Held so two requests racing on a cold cache decode the index once. */
    private val lock = Mutex()

    private var cached: TileChallenge.Ready? = null

    suspend fun forDate(date: LocalDate): TileChallenge = lock.withLock {
        cached?.takeIf { it.date == date }?.let { return it }

        val index = read { loadIndex() } ?: return TileChallenge.Unavailable
        val fleet = read { loadFleet() } ?: return TileChallenge.Unavailable
        if (fleet.isEmpty()) return TileChallenge.Unavailable

        val card = read { WatchRouteFeed(index, fleet, generatorDispatcher).challengeFor(date) }
            ?: return TileChallenge.Unavailable
        TileChallenge.Ready(date, card).also { cached = it }
    }

    /** Runs [block], returning null if it threw. Cancellation is rethrown. */
    private inline fun <T> read(block: () -> T): T? = try {
        block()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Throwable) {
        Log.w(TAG, "A challenge read failed", failure)
        null
    }

    private companion object {
        const val TAG = "WatchChallengeSource"
    }
}
