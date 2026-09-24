package com.github.daanbouwman.flightplanner.wear.tile

import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.github.daanbouwman.flightplanner.wear.MainActivity
import com.github.daanbouwman.flightplanner.wear.R
import com.github.daanbouwman.flightplanner.wear.route.WatchRouteFeedViewModel
import com.github.daanbouwman.flightplanner.wear.theme.WatchThemeSource
import com.github.daanbouwman.flightplanner.wear.ui.theme.schemeFor
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.ZonedDateTime
import javax.inject.Inject

/**
 * "Today's challenge" as a Wear OS tile: one route a day, the same all day,
 * and a tap that opens the app on it.
 *
 * The watch's counterpart to the phone's `ChallengeWidget`, drawn from the same
 * `dailyChallenge` in `:core:routing` — see [WatchChallengeSource] for what is
 * computed and [challengeTileLayout] for what is drawn. What it adds is only
 * what a tile needs: a future to answer in, a freshness that runs to midnight,
 * and a theme read at request time.
 *
 * **The theme is read per request, not listened to.** [WatchThemeSource]'s
 * first value is the `DataItem` the phone last published, replicated onto the
 * watch, so a request picks up a change the next time the tile is shown. A
 * listener that pushed an update the moment the phone's theme flipped would
 * need something alive to hold it, and nothing on the watch is — which is the
 * whole point of a tile. The cost is that a tile already on screen keeps its
 * old colours until it is next requested.
 *
 * No resources: the tile is text on a background, so its resources are the
 * empty set, versioned once.
 */
@AndroidEntryPoint
class ChallengeTileService : TileService() {

    @Inject
    lateinit var challenges: WatchChallengeSource

    @Inject
    lateinit var themes: WatchThemeSource

    /**
     * Where requests run. Main, because the tile builders are cheap and every
     * read below moves itself off the main thread; supervised, so one failed
     * request does not cancel the next.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> =
        future { tile(requestParams) }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest,
    ): ListenableFuture<ResourceBuilders.Resources> = future {
        ResourceBuilders.Resources.Builder().setVersion(RESOURCES_VERSION).build()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun tile(request: RequestBuilders.TileRequest): TileBuilders.Tile {
        val now = ZonedDateTime.now()
        val challenge = challenges.forDate(now.toLocalDate())
        val inks = TileInks.from(schemeFor(themes.theme.first()))

        val layout = challengeTileLayout(
            challenge = challenge,
            text = TileText(
                label = getString(R.string.tile_challenge_label),
                unavailable = getString(R.string.tile_challenge_unavailable),
                description = (challenge as? TileChallenge.Ready)?.card?.let { card ->
                    getString(
                        R.string.tile_challenge_description_route,
                        card.departureIcao,
                        card.destinationIcao,
                        card.distanceText,
                        card.aircraftName,
                    )
                },
            ),
            inks = inks,
            screenWidthDp = request.deviceConfiguration.screenWidthDp,
            clickable = openApp(leadWithChallenge = challenge is TileChallenge.Ready),
        )

        return TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setFreshnessIntervalMillis(millisUntilNextDay(now))
            .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(layout))
            .build()
    }

    /**
     * The tap: open the app, on the challenge when there is one.
     *
     * With no challenge the tap still opens the app — the face has its own
     * unavailable state, which says more than a tile has room to.
     */
    private fun openApp(leadWithChallenge: Boolean): ModifiersBuilders.Clickable {
        val activity = ActionBuilders.AndroidActivity.Builder()
            .setPackageName(packageName)
            .setClassName(MainActivity::class.java.name)
            .addKeyToExtraMapping(
                WatchRouteFeedViewModel.EXTRA_LEAD_WITH_CHALLENGE,
                ActionBuilders.AndroidBooleanExtra.Builder().setValue(leadWithChallenge).build(),
            )
            .build()
        return ModifiersBuilders.Clickable.Builder()
            .setId(CLICK_OPEN)
            .setOnClick(ActionBuilders.LaunchAction.Builder().setAndroidActivity(activity).build())
            .build()
    }

    /**
     * A coroutine as the future a tile answers with.
     *
     * The future being cancelled — the system gave up on the request — cancels
     * the coroutine; the coroutine failing fails the future, which the system
     * shows as its own error state until the next request.
     *
     * A failure is handed to the future and **not** rethrown: an exception
     * escaping a `launch` reaches the default handler, which on Android ends
     * the process — a crash for what the future already reports.
     */
    private fun <T : Any> future(block: suspend () -> T): ListenableFuture<T> =
        CallbackToFutureAdapter.getFuture<T> { completer ->
            val job = scope.launch {
                try {
                    completer.set(block())
                } catch (cancellation: CancellationException) {
                    completer.setCancelled()
                    throw cancellation
                } catch (failure: Exception) {
                    completer.setException(failure)
                }
            }
            completer.addCancellationListener({ job.cancel() }, Runnable::run)
            "ChallengeTileService request"
        }

    private companion object {
        /** Bumped only if the tile ever gains an image resource. */
        const val RESOURCES_VERSION = "1"

        const val CLICK_OPEN = "open"
    }
}
