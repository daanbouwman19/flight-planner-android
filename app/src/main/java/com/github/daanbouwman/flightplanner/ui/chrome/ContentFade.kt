package com.github.daanbouwman.flightplanner.ui.chrome

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Fades the content out where it passes under the status bar.
 *
 * The bars are transparent and nothing is painted behind them — that is the
 * invariant, and this does not break it: it **erases** the top of the content
 * rather than covering it, so no pixel is added behind the clock. A scrim would
 * read as an opaque bar the moment a card slid under it; a card that dissolves
 * into the ground reads as depth.
 *
 * It exists because of where a fling *stops*. Content passing under the clock is
 * the point of an edge-to-edge list; content parked under it is a card whose ETE
 * figure the battery icon is sitting on, and it stays that way until the user
 * scrolls again. Shared by every full-bleed list — Plan, Fleet, and Logbook/Stats
 * through [com.github.daanbouwman.flightplanner.ui.profile.ProfileScreen] — since
 * every one of them can stop mid-scroll with a card or a sticky header under the
 * clock.
 *
 * `CompositingStrategy.Offscreen` is what makes `BlendMode.DstIn` mean "erase" —
 * without it the blend applies against whatever is already on the window, which
 * is the ground, and the fade turns into a smear.
 */
fun Modifier.fadeUnderStatusBar(height: Dp): Modifier =
    // No inset, no layer: nothing to fade under, nothing to composite.
    //
    // **The layer's cost is still unmeasured.** An A/B that appeared to clear it was
    // void: the installs between pairs failed silently — a Windows adb handed a
    // POSIX path — so both halves measured the same APK. Wrapping a scrolling list
    // in an offscreen layer is exactly the shape of change that usually does cost
    // frames, so it stays on P2's list until a macrobenchmark says otherwise.
    if (height <= 0.dp) {
        this
    } else {
        this
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithCache {
                val fadeHeight = height.toPx()
                val brush = Brush.verticalGradient(
                    0f to Color.Transparent,
                    1f to Color.Black,
                    startY = 0f,
                    endY = fadeHeight,
                )
                onDrawWithContent {
                    drawContent()
                    drawRect(
                        brush = brush,
                        size = Size(size.width, fadeHeight),
                        blendMode = BlendMode.DstIn,
                    )
                }
            }
    }
