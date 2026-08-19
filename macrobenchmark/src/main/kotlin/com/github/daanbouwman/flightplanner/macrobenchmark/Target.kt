package com.github.daanbouwman.flightplanner.macrobenchmark

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until

/** The app under measurement. Only its `benchmark` variant is ever installed from here. */
const val TARGET_PACKAGE = "com.github.daanbouwman.flightplanner"

/**
 * The route list's handle, repeated from `PlanScreen.kt`.
 *
 * This module measures the app's APK rather than compiling against it, so the
 * string cannot be shared. If it drifts, [awaitRouteList] times out rather than
 * failing to compile.
 */
private const val ROUTE_LIST_TAG = "plan:routeList"

/**
 * How long the first batch may take to appear.
 *
 * Generous on purpose: the Plan screen generates fifty routes on launch, off the
 * main thread, behind the airport index. A cold, uncompiled first run is the slow
 * case and it is also the run this module most wants to measure, so the timeout
 * has to clear it by a wide margin or the instrument would fail exactly where the
 * problem is.
 */
private const val LIST_TIMEOUT_MILLIS = 15_000L

/**
 * Waits for the route list and returns it, configured for flinging.
 *
 * Failing loudly here is deliberate. The alternative — falling back to whatever
 * scrollable object UiAutomator finds first — is how a benchmark ends up
 * reporting a real number for the wrong surface.
 */
fun MacrobenchmarkScope.awaitRouteList(): UiObject2 {
    val list = device.wait(Until.findObject(By.res(ROUTE_LIST_TAG)), LIST_TIMEOUT_MILLIS)
        ?: error(
            "No object with resource id '$ROUTE_LIST_TAG' after ${LIST_TIMEOUT_MILLIS}ms. " +
                "Either the first batch never arrived, or PlanScreen's test tag was renamed " +
                "without renaming ROUTE_LIST_TAG here.",
        )

    // Without a gesture margin, a fling that starts at the very edge of the list
    // is taken by the system as a back gesture or a notification pull, and the
    // measured frames are the system's rather than the app's.
    list.setGestureMargin(device.displayWidth / 5)
    return list
}

/**
 * One down-and-back-up fling over the route list.
 *
 * **Down and back up, rather than repeatedly down**, because the Plan screen
 * appends a fresh batch of routes twenty rows from the end. Flinging in one
 * direction long enough reaches that trigger, and from there the fling is being
 * measured over rows that are arriving while it runs — a generation and an
 * entrance animation folded into a number that is supposed to describe a fling.
 * Turning round keeps it over rows that were already there.
 */
fun MacrobenchmarkScope.flingRouteList(list: UiObject2) {
    list.fling(Direction.DOWN)
    device.waitForIdle()
    list.fling(Direction.UP)
    device.waitForIdle()
}

/**
 * The route list, once its entrance animation has finished.
 *
 * A batch arrives with a staggered fade-and-rise — up to
 * `FlightMotion.EnterStaggerCap` × `EnterStaggerMillis` of delay and then a
 * spring on top of it. `waitForIdle` does not wait for a Compose animation, so
 * without an explicit settle the first frames of a fling are the tail of an
 * entrance, and the fling's own numbers are diluted by an animation that happens
 * once. This belongs in `setupBlock` only.
 */
fun MacrobenchmarkScope.awaitRouteListSettled() {
    awaitRouteList()
    device.waitForIdle()
    Thread.sleep(ENTRANCE_SETTLE_MILLIS)
}

/** Comfortably past the staggered entrance: 8 × 30 ms of delay plus a 500 ms spring. */
private const val ENTRANCE_SETTLE_MILLIS = 1_000L
