package com.github.daanbouwman.flightplanner.macrobenchmark

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until

/**
 * The journey from a cold launch to the immersive globe, and the spin over it.
 *
 * Every string here is repeated from the app's own resources, for the reason
 * [awaitRouteList] gives about `ROUTE_LIST_TAG`: this module measures the APK
 * rather than compiling against it. Each one is a content description the app
 * puts on a control TalkBack has to be able to name anyway, so they are the
 * stable handles the app already commits to — not test tags added for this.
 */
private const val SHOW_GLOBE_DESCRIPTION = "Show the globe"
private const val OPEN_FULLSCREEN_DESCRIPTION = "Open the globe full screen"
private const val COLLAPSE_DESCRIPTION = "Leave the full-screen globe"

/**
 * The tail of a route card's merged content description
 * (`plan_route_content_description`): the one shape of sentence only a card has.
 */
private val ROUTE_CARD_DESCRIPTION =
    Regex(".*nautical\\s+miles, \\d+ hours \\d+ minutes", RegexOption.DOT_MATCHES_ALL).toPattern()

/** How long a control may take to appear once the screen it lives on is requested. */
private const val CONTROL_TIMEOUT_MILLIS = 10_000L

/**
 * How long the immersive globe is given to land its first imagery before the
 * spin starts.
 *
 * The spin's cost is the traversal over a populated atlas, and a globe that is
 * still fetching its pinned floor is a different — cheaper — workload: fewer
 * resident tiles, a coarser mesh. The tiles come over the network the first
 * time and from the 96 MB disk cache after, so this is generous for the first
 * iteration and idle time for the rest, and it is in `setupBlock` either way.
 */
private const val IMAGERY_SETTLE_MILLIS = 4_000L

/**
 * Opens the first route, switches its hero to the globe and takes it full screen.
 *
 * The immersive screen rather than the deep hero, for two reasons. It owns the
 * whole window, so every drag is the globe's — the hero sits in a vertical
 * scroll and hands near-vertical drags to the page, which would make half the
 * gestures below scroll a list instead. And it is the largest surface the
 * globe ever renders to, so the traversal it measures is the traversal at its
 * most expensive: the tile budget is spent against the biggest viewport.
 */
fun MacrobenchmarkScope.openImmersiveGlobe() {
    val list = awaitRouteList()
    // The first *card*, not the first clickable thing: the list's header row
    // carries the filter fields, which are clickable and open a sheet. A card
    // is the one node in the list whose merged description is a route sentence
    // — "…, N nautical miles, H hours M minutes" — so that is what it is found by.
    // Not `By.clickable(true)` as well: Compose reports the card's merged
    // description on a node *beside* the one carrying the click (checked with
    // `uiautomator dump`), and a click on the described node's centre lands on
    // the card regardless. Waited for, because the list exists — header only —
    // before the first batch of routes has been generated into it.
    val card = list.wait(Until.findObject(By.desc(ROUTE_CARD_DESCRIPTION)), CONTROL_TIMEOUT_MILLIS)
        ?: error("The route list has no route card to open after ${CONTROL_TIMEOUT_MILLIS}ms")
    card.click()

    awaitControl(SHOW_GLOBE_DESCRIPTION).click()
    awaitControl(OPEN_FULLSCREEN_DESCRIPTION).click()
    // The collapse control exists only on the immersive screen, so its arrival
    // is the screen's.
    awaitControl(COLLAPSE_DESCRIPTION)
    Thread.sleep(IMAGERY_SETTLE_MILLIS)
}

/**
 * Spins the globe: horizontal drags across the middle of the window, each one
 * released into a fling, alternating direction.
 *
 * Horizontal, and through the centre, so no drag starts in the system's back
 * strip at either edge or the status bar at the top. Alternating so the camera
 * does not run off to one side of the planet and stay looking at ocean, which
 * is a cheaper traversal than a coastline. The pause after each drag is the
 * fling: the decay is exactly the sustained camera motion the frame loop has
 * to keep up with, and cutting it off with the next drag would measure only
 * the first frames of each.
 */
fun MacrobenchmarkScope.spinGlobe() {
    val width = device.displayWidth
    val height = device.displayHeight
    val y = height / 2
    val from = width / 4
    val to = width * 3 / 4
    repeat(SPIN_DRAGS) { i ->
        if (i % 2 == 0) {
            device.swipe(from, y, to, y, SPIN_STEPS)
        } else {
            device.swipe(to, y, from, y, SPIN_STEPS)
        }
        Thread.sleep(FLING_SETTLE_MILLIS)
    }
}

private fun MacrobenchmarkScope.awaitControl(description: String): UiObject2 =
    device.wait(Until.findObject(By.desc(description)), CONTROL_TIMEOUT_MILLIS)
        ?: error(
            "No control described '$description' after ${CONTROL_TIMEOUT_MILLIS}ms. " +
                "Either the screen never arrived, or the app's string changed without this one following.",
        )

/** Six drags is about six seconds of camera motion per iteration. */
private const val SPIN_DRAGS = 6

/** UiAutomator steps are ~5 ms each: a 100 ms drag, quick enough to fling. */
private const val SPIN_STEPS = 20

/** Long enough for the fling to decay to rest; the frame loop settles after it. */
private const val FLING_SETTLE_MILLIS = 900L
