package com.github.daanbouwman.flightplanner.ui.chrome

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
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
 * ### The layer is the height of the inset, not of the list
 *
 * Erasing needs a layer: `BlendMode.DstIn` against the window blends with the
 * ground and turns the fade into a smear. The first version got one from
 * `CompositingStrategy.Offscreen` on the whole list, which renders every frame
 * of a fling to a full-screen texture and composites it back. Measured with
 * `flingBaselineProfile` against the same code with no fade at all (UI-PLAN
 * §4c), that cost 1.4 ms of frame overrun at P90 and 1.7 ms at P99 — GPU time,
 * almost invisible in the CPU figure — for a strip a few dozen dp tall.
 *
 * So the layer is now only that strip. The content is drawn twice with
 * complementary clips: once straight to the window below the strip, once into
 * a `saveLayer` bounded to the strip, where the gradient erases it. Drawing
 * twice is not rendering twice: each pass is clipped before it rasterises, so
 * the second costs the strip's pixels plus the recording of the list's
 * commands. Measured the same way it halves the bill — 0.7 ms over no fade at
 * P90 — and the rest is the price of the effect.
 */
fun Modifier.fadeUnderStatusBar(height: Dp): Modifier =
    // No inset, no layer: nothing to fade under, nothing to composite.
    if (height <= 0.dp) {
        this
    } else {
        this.drawWithCache {
            val fadeHeight = height.toPx()
            val strip = Rect(0f, 0f, size.width, fadeHeight)
            val brush = Brush.verticalGradient(
                0f to Color.Transparent,
                1f to Color.Black,
                startY = 0f,
                endY = fadeHeight,
            )
            val layerPaint = Paint()
            onDrawWithContent {
                // Everything below the strip, straight to the window.
                clipRect(top = fadeHeight) { this@onDrawWithContent.drawContent() }
                // The strip, through a layer its own height, then erased.
                drawIntoCanvas { canvas ->
                    canvas.saveLayer(strip, layerPaint)
                    clipRect(bottom = fadeHeight) { this@onDrawWithContent.drawContent() }
                    drawRect(
                        brush = brush,
                        size = Size(size.width, fadeHeight),
                        blendMode = BlendMode.DstIn,
                    )
                    canvas.restore()
                }
            }
        }
    }
