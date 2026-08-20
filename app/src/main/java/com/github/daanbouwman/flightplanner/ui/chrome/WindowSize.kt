package com.github.daanbouwman.flightplanner.ui.chrome

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The window's own size, in dp.
 *
 * `NavigationSuiteScaffold` adapts the navigation affordance by itself, and for a
 * while that was mistaken for the app being adaptive. It is not: a bar or a rail
 * is one decision, and the *content* has its own. In landscape on a phone the
 * Plan screen showed exactly one card — the navigation bar taking 22 % of a
 * 360 dp-tall window, the header over half of what was left — because the layout
 * was authored once, for portrait.
 *
 * Read from `LocalWindowInfo` rather than from the configuration: it reports the
 * window rather than the display, so it is already correct in split screen and in
 * a freeform window, where a configuration-derived answer is not.
 */
@Composable
fun windowHeightDp(): Dp = with(LocalDensity.current) {
    LocalWindowInfo.current.containerSize.height.toDp()
}

@Composable
fun windowWidthDp(): Dp = with(LocalDensity.current) {
    LocalWindowInfo.current.containerSize.width.toDp()
}

/**
 * True when the window is too short to spend height the way a portrait phone
 * can.
 *
 * The threshold is Material's own compact-height boundary. Every phone in
 * landscape is below it, and so is a phone in a split-screen half.
 */
@Composable
fun isCompactHeight(): Boolean = windowHeightDp() < CompactHeightMax

/**
 * The most a single column of route cards should ever be.
 *
 * Beyond this the line length stops helping — a filter field 390 dp wide to say
 * "Any", a card whose map is mostly ocean because the aspect went with it — so
 * the extra width becomes margin and the column stays a column. A grid would use
 * it better and is a bigger change than the one this bound exists to make.
 */
val MaxContentWidth: Dp = 640.dp

/**
 * A wider bound for screens that can use it — the detail screen and the logbook,
 * where the density is lower and the extra width doesn't break the layout.
 */
val WideMaxContentWidth: Dp = 840.dp

private val CompactHeightMax: Dp = 480.dp
