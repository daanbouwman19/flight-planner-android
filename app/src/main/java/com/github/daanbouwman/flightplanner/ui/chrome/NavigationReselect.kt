package com.github.daanbouwman.flightplanner.ui.chrome

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * "The user tapped the navigation item for the section they are already in."
 *
 * There is nowhere to navigate, so the tap would otherwise do nothing at all —
 * `navigateToTopLevel` on the current destination is a `launchSingleTop` no-op. The
 * established meaning of that tap on Android is *take me back to the top*, and this
 * app needs it more than most: the Plan screen's controls are the first item of its
 * list, so from row sixty the way back to them is a long scroll or this.
 *
 * ### An event, not a state
 *
 * A `SharedFlow` with no replay, for the same reason the swipe actions act on an
 * event rather than on a state: a state says "a scroll to the top is wanted" and
 * keeps saying it, so a screen that composes later — coming back from a route
 * detail, say — reads the old request and jumps somewhere the user did not ask for.
 * An event with no replay is delivered to whoever is listening at the time and to
 * nobody afterwards.
 */
@Stable
class NavigationReselect {

    private val events = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** Observed by whichever screen is on top. */
    val reselections: SharedFlow<Unit> = events.asSharedFlow()

    /** Called by the navigation suite when the active section's item is tapped. */
    fun request() {
        events.tryEmit(Unit)
    }
}

/**
 * The reselect signal for the current window, provided by
 * [com.github.daanbouwman.flightplanner.ui.FlightPlannerApp].
 */
val LocalNavigationReselect = staticCompositionLocalOf { NavigationReselect() }

/**
 * Scrolls [listState] back to the top when the user taps the navigation item for the
 * section this list belongs to. One line per scrolling screen.
 */
@Composable
fun ScrollToTopOnReselect(
    listState: LazyListState,
    reselect: NavigationReselect = LocalNavigationReselect.current,
) {
    LaunchedEffect(reselect, listState) {
        reselect.reselections.collect {
            // Animated rather than snapped: the point is to show the user where they
            // went, and Compose already shortcuts the distance when the target is far
            // enough away that animating it honestly would take all day.
            listState.animateScrollToItem(0)
        }
    }
}
