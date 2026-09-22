package com.github.daanbouwman.flightplanner.wear.route

import com.github.daanbouwman.flightplanner.model.AircraftSpec
import com.github.daanbouwman.flightplanner.routing.AirportIndex
import com.github.daanbouwman.flightplanner.routing.RouteGenerator
import com.github.daanbouwman.flightplanner.routing.RouteMode
import com.github.daanbouwman.flightplanner.routing.RouteRequest
import com.github.daanbouwman.flightplanner.routing.WorldOutline
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * An endless supply of routes to swipe through.
 *
 * The design is one route per screen and a swipe up for the next one, which is
 * a list with no end: a wearer who keeps flicking should keep getting routes,
 * not run out after fifty and find a dead stop. So this hands out batches and
 * the caller asks for another before the wearer reaches the bottom of the one
 * it has — see [WatchRouteFeedViewModel.onPageSettled].
 *
 * A batch is [BATCH_SIZE] rather than `:core:routing`'s `DEFAULT_ROUTE_BATCH` of
 * 50. Fifty is what the desktop app's *list* shows at once and what the phone's
 * Plan screen fills; here the wearer sees one at a time and a batch is only a
 * buffer, so a smaller one holds fewer sampled arcs in memory for the same
 * uninterrupted swipe. Generation itself is not the reason — a batch of fifty
 * completes inside a millisecond either way.
 *
 * Pure, and free of Android: the ViewModel owns the state and this owns the
 * rules, so the rules can be tested in milliseconds against a two-airport index.
 */
class WatchRouteFeed(
    private val index: AirportIndex,
    private val fleet: List<AircraftSpec>,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {

    private val generator = RouteGenerator(index, dispatcher = dispatcher)

    /**
     * The next batch, already worded for the face.
     *
     * `icaoOnly` is on: an airport with no real ICAO code is a strip with a
     * local identifier, which reads as noise in 30 sp type on a round screen and
     * is not what someone flicking through routes on a wrist is looking for. The
     * phone leaves the choice to the user; the watch does not have the room to
     * ask, so it takes the stricter reading.
     *
     * Empty when the fleet is empty or no route could be built, which the caller
     * treats as "nothing to show" rather than retrying forever.
     */
    suspend fun nextBatch(): List<WatchRouteCard> =
        generator.generate(
            RouteRequest(
                mode = RouteMode.AllAircraft,
                fleet = fleet,
                amount = BATCH_SIZE,
                icaoOnly = true,
            ),
        ).map { it.toCard(index) }

    companion object {
        /** Routes per batch. See the class KDoc for why it is not 50. */
        const val BATCH_SIZE: Int = 20

        /**
         * How close to the end of the loaded routes the wearer gets before the
         * next batch is asked for.
         *
         * Four pages of headroom against a batch of twenty: far enough that the
         * generate finishes long before the wearer arrives — it is sub-millisecond
         * work — and short enough that the list does not grow a batch ahead of
         * where anyone will ever look.
         */
        const val PREFETCH_MARGIN: Int = 4
    }
}

/** What the face is showing. */
sealed interface WatchRouteFeedState {

    /** The assets are being read and the first batch generated. */
    data object Loading : WatchRouteFeedState

    /** Routes to swipe through. Never empty — an empty batch is [Unavailable]. */
    data class Ready(val routes: List<WatchRouteCard>, val outline: WorldOutline) : WatchRouteFeedState

    /**
     * Nothing to show: an asset that would not read, or a fleet that produced no
     * route at all. Both are the same thing to the wearer — the watch cannot
     * plan right now — and both are fixed on the phone, so they share a state
     * rather than splitting a screen nobody can act on differently.
     */
    data object Unavailable : WatchRouteFeedState
}
