package com.github.daanbouwman.flightplanner.ui.chrome

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

/**
 * Whether the app's chrome is currently shown.
 *
 * In practice that means the navigation suite, and the reason this state exists a
 * level above the screens is that the suite lives in
 * [com.github.daanbouwman.flightplanner.ui.FlightPlannerApp] — a screen scrolling
 * its own list cannot hide it, so the signal has to travel *up*.
 *
 * A screen drives it with [rememberChromeScrollConnection] and nothing else should
 * call [hide]: the rule about when the chrome may leave is deliberately in one
 * place, so that two lists cannot disagree about it.
 *
 * A screen's own title and controls are **not** chrome and are not driven from
 * here. The Plan screen tried that and it could not be made to feel right; they are
 * the first item of its list instead, which moves with the finger by construction.
 *
 * The default instance behind [LocalAppChromeState] is inert in the useful sense:
 * it starts visible and no one animates against it, so a preview or a test that
 * never provides one renders a screen with its chrome up.
 */
@Stable
class AppChromeState {

    /** Whether the chrome should currently be shown. Starts up. */
    var visible: Boolean by mutableStateOf(true)
        private set

    fun show() {
        visible = true
    }

    fun hide() {
        visible = false
    }
}

/**
 * The chrome signal for the current window.
 *
 * Provided once by [com.github.daanbouwman.flightplanner.ui.FlightPlannerApp] so
 * that the navigation suite and whichever screen is on top read the same value.
 */
val LocalAppChromeState = staticCompositionLocalOf { AppChromeState() }

/**
 * Drives [chrome] from a scrolling list, and returns the connection to attach
 * above that list with `Modifier.nestedScroll`.
 *
 * ### Why a threshold rather than a per-pixel offset
 *
 * A bar that tracks the finger one-to-one is the better feel, and it is what the
 * Plan screen's own header does — by being a list item rather than a bar at all.
 * The navigation suite cannot do that: it lives a level above the screen and it
 * animates as a whole, so the choice here is only *when* to send it away.
 *
 * A threshold is the right answer for that. The navigation bar is not part of what
 * the user is reading, so it should ignore a nudge and go on a deliberate scroll —
 * which is what every mail app has already taught everyone.
 *
 * Downward and upward travel are deliberately asymmetric: [HideThreshold] is a
 * decision, [RevealThreshold] is barely more than jitter. Getting the navigation
 * back must never feel like work.
 *
 * ### The way this could trap the user
 *
 * An empty state, an error, or a list of one card cannot be scrolled up, so a
 * hidden navigation bar would have no gesture that could bring it back. Any time
 * the list is at its top — which includes "cannot scroll at all" — the chrome is
 * put back up. That is handled here rather than left to each screen, because a
 * screen that forgets it leaves the app with no navigation at all.
 *
 * @param listState the list whose scrolling drives the chrome
 * @param chrome the state to drive, normally the one from [LocalAppChromeState]
 */
@Composable
fun rememberChromeScrollConnection(
    listState: LazyListState,
    chrome: AppChromeState = LocalAppChromeState.current,
): NestedScrollConnection {
    val density = LocalDensity.current
    val hidePx = with(density) { HideThreshold.toPx() }
    val revealPx = with(density) { RevealThreshold.toPx() }

    val connection = remember(chrome, listState, hidePx, revealPx) {
        object : NestedScrollConnection {

            /**
             * Distance travelled since the last direction change. Negative is
             * towards the end of the list.
             *
             * Reset on every reversal, which is what makes this measure *sustained*
             * travel rather than net travel: a drag down and back up leaves nothing
             * behind, so it cannot add up to a hide over a minute of browsing.
             */
            private var travel = 0f

            // Post-scroll, so this reads what the list actually consumed. Pre-scroll
            // reports what the finger asked for, which at either end of the list is
            // a drag that moved nothing — and a bar that hides while the content is
            // held still by overscroll is a bar that hides for no reason.
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                val dy = consumed.y
                when {
                    dy < 0f -> {
                        if (travel > 0f) travel = 0f
                        travel += dy
                        if (travel <= -hidePx) {
                            travel = 0f
                            chrome.hide()
                        }
                    }

                    dy > 0f -> {
                        if (travel < 0f) travel = 0f
                        travel += dy
                        if (travel >= revealPx) {
                            travel = 0f
                            chrome.show()
                        }
                    }
                }
                // Nothing is consumed here: this observes the scroll, it does not
                // take part in it. Consuming would make the list scroll less far
                // than the finger moved.
                return Offset.Zero
            }
        }
    }

    LaunchedEffect(chrome, listState) {
        snapshotFlow { !listState.canScrollBackward }
            .distinctUntilChanged()
            .filter { it }
            .collect { chrome.show() }
    }

    // Leaving a scrolling screen puts the chrome back. Without this, opening a
    // route detail from a scrolled list and coming back to a *different* section
    // would arrive with no navigation bar.
    DisposableEffect(chrome) {
        onDispose { chrome.show() }
    }

    return connection
}

/**
 * Sustained downward travel before the chrome leaves.
 *
 * About a third of a route card: far enough that nudging the list to read a
 * clipped figure keeps the navigation, short enough that a deliberate scroll
 * reclaims the height immediately.
 */
private val HideThreshold = 48.dp

/** Upward travel before the chrome returns. Small enough to be "immediately", large enough not to be jitter. */
private val RevealThreshold = 6.dp
