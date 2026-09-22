package com.github.daanbouwman.flightplanner.watch

import android.content.Context
import android.util.Log
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.github.daanbouwman.flightplanner.core.designsystem.theme.ThemeChoice
import com.github.daanbouwman.flightplanner.handoff.WatchThemeChoice
import com.github.daanbouwman.flightplanner.handoff.WatchThemeState
import com.github.daanbouwman.flightplanner.handoff.WatchThemeSync
import com.github.daanbouwman.flightplanner.ui.runCatchingCancellable
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Tells the watch which look the user has chosen, so the two stay in sync.
 *
 * Composed by `MainActivity` beside `PublishWidgetPreview` and
 * `RefreshWidgetsOnFleetChange`, for the same reason all three sit there:
 * *after* the app's own content, so none of it precedes the first frame. This
 * is the only place `:app` touches the Data Layer, and it touches it on a
 * background dispatcher — the cold-start budget in CLAUDE.md is why there is no
 * `Application.onCreate` work and no eager `@Singleton` here.
 *
 * ### Why the phone pushes rather than the watch pulling
 *
 * The setting lives on the phone, so the phone is the only side that knows when
 * it changed. A `DataItem` is replicated and then **persists on the watch**, so
 * a push made while the watch was off or out of range is still there the next
 * time the watch app opens; the watch never has to ask, and never has to be
 * connected at the moment the user flips the switch. [WatchThemeSync] has the
 * rest of that argument.
 *
 * A write with nothing paired is not an error worth telling anyone about: the
 * Data Layer stores the item locally and syncs it if a watch ever appears. It
 * is logged rather than surfaced, because a phone user who owns no watch must
 * never see a word about one.
 */
@Composable
fun PublishThemeToWatch(themeChoice: ThemeChoice) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    val state = remember(themeChoice, systemDark) { watchThemeStateOf(themeChoice, systemDark) }

    LaunchedEffect(state) {
        withContext(Dispatchers.IO) {
            runCatchingCancellable { publish(context, state) }
                .onFailure { failure -> Log.i(TAG, "Could not publish the theme to the watch", failure) }
        }
    }
}

/**
 * The phone's choice as the watch's.
 *
 * An exhaustive `when` rather than `WatchThemeChoice.valueOf(name)` on purpose:
 * it is the compiler, not a test, that then refuses a new `ThemeChoice` constant
 * with no watch counterpart. The other direction — a constant added or renamed
 * on the watch's side — is `WatchThemeContractTest`.
 */
internal fun watchThemeStateOf(themeChoice: ThemeChoice, systemDark: Boolean): WatchThemeState =
    WatchThemeState(
        choice = when (themeChoice) {
            ThemeChoice.SYSTEM -> WatchThemeChoice.SYSTEM
            ThemeChoice.LIGHT -> WatchThemeChoice.LIGHT
            ThemeChoice.DARK -> WatchThemeChoice.DARK
            ThemeChoice.COCKPIT -> WatchThemeChoice.COCKPIT
            ThemeChoice.CHART -> WatchThemeChoice.CHART
        },
        systemDark = systemDark,
    )

/**
 * Blocking, and deliberately so: it runs on [Dispatchers.IO], and awaiting the
 * task there is what turns a failed write into something the catch above can
 * log. Awaiting it without blocking would mean `kotlinx-coroutines-play-services`,
 * a second dependency for one call.
 *
 * **The timeout is not belt and braces.** A blocked `Tasks.await` cannot be
 * interrupted by cancelling the coroutine around it, so without a bound a
 * wedged Play Services would hold an IO thread for the life of the process.
 * The bound turns that into a logged failure and a thread returned; the write
 * is attempted again the next time the setting changes or the app opens.
 *
 * `setUrgent` because the wearer may well be looking at the watch: without it
 * the Data Layer may sit on the change for up to half an hour.
 */
private fun publish(context: Context, state: WatchThemeState) {
    val request = PutDataMapRequest.create(WatchThemeSync.PATH).apply {
        dataMap.putString(WatchThemeSync.KEY_CHOICE, state.choice.name)
        dataMap.putBoolean(WatchThemeSync.KEY_SYSTEM_DARK, state.systemDark)
    }.asPutDataRequest().setUrgent()

    Tasks.await(Wearable.getDataClient(context).putDataItem(request), TIMEOUT_SECONDS, TimeUnit.SECONDS)
}

private const val TIMEOUT_SECONDS = 10L

private const val TAG = "PublishThemeToWatch"
