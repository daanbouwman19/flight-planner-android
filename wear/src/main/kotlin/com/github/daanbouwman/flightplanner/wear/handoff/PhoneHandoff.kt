package com.github.daanbouwman.flightplanner.wear.handoff

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.wear.remote.interactions.RemoteActivityHelper
import com.github.daanbouwman.flightplanner.handoff.WatchRoute
import com.github.daanbouwman.flightplanner.handoff.WatchRouteLink
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

/** What came of asking the phone to open a route. */
enum class HandoffResult {
    /** The phone was asked. Whether the wearer picks it up is not ours to know. */
    Sent,

    /** No reachable phone, or it refused. The wearer is told; nothing is retried. */
    Failed,
}

/**
 * Opens a route in the phone app.
 *
 * ### Why a link and not a Data Layer message
 *
 * Both would work, and the Data Layer is the more capable of the two — it could
 * carry the phone's real fleet back to the watch, which would remove the
 * airframe-matching that `WatchRouteLink` exists to paper over. It also costs a
 * `WearableListenerService` on the phone, a Play-services dependency on both
 * sides, capability discovery and a node to address.
 *
 * A link costs one `<intent-filter>` on the phone and nothing else. The phone's
 * companion app is what carries it across, and `:app` gains no new dependency at
 * all — which matters, because `:app` is the thing with a 500 ms cold-start
 * budget and this is a feature it will use once per tap on a different device.
 * When the watch needs to *read* something from the phone rather than push to
 * it, that is the moment to add the Data Layer, and this class is where the
 * choice is written down so it can be revisited rather than rediscovered.
 *
 * ### What the phone does with it
 *
 * `MainActivity` receives `ACTION_VIEW` on the `flightplanner://route/…` URI,
 * `LaunchIntents.parse` turns it back into a `WatchRoute`, and the airframe is
 * matched against whatever fleet that phone is carrying — see
 * `LaunchRequest.OpenWatchRoute`.
 */
@Singleton
class PhoneHandoff @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    private val remote by lazy { RemoteActivityHelper(context) }

    /**
     * Asks the paired phone to open [route], returning what came of it.
     *
     * `CATEGORY_BROWSABLE` and a data URI are what `RemoteActivityHelper`
     * requires of an Intent it is asked to start elsewhere; it rejects anything
     * else outright. Failures are caught rather than propagated because every
     * one of them means the same thing to the wearer — the phone did not get it
     * — and none of them is worth taking the app down for: the phone can be out
     * of range, asleep, or simply not have the app installed.
     *
     * Cancellation is rethrown rather than reported as a failure. It is a
     * `CancellationException` and so would be caught by the clause below, but it
     * means the wearer swiped away mid-tap, not that the phone refused — and
     * swallowing it would leave the cancelled coroutine running on to post a
     * message about a handoff nobody is waiting for.
     */
    suspend fun open(route: WatchRoute): HandoffResult {
        val intent = Intent(Intent.ACTION_VIEW)
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .setData(Uri.parse(WatchRouteLink.build(route)))
        return try {
            remote.startRemoteActivity(intent)
            HandoffResult.Sent
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            Log.w(TAG, "Could not open the route on the phone", failure)
            HandoffResult.Failed
        }
    }

    private companion object {
        const val TAG = "PhoneHandoff"
    }
}
