package com.github.daanbouwman.flightplanner.core.designsystem.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.github.daanbouwman.flightplanner.core.designsystem.R
import com.github.daanbouwman.flightplanner.core.designsystem.motion.FlightMotion
import com.github.daanbouwman.flightplanner.core.designsystem.theme.FlightPlannerTheme

/** Which edge a swipe action is anchored to. */
enum class SwipeActionSide { Start, End }

/**
 * What sits behind a row while it is being swiped away.
 *
 * Shared rather than written per screen because a swipe is a *grammar*: the
 * user learns once that dragging right confirms and dragging left discards, and
 * that lesson only holds if the plan list and the logbook look and behave
 * identically. Two independently written backgrounds drift within a release.
 *
 * The icon scales up as the threshold approaches, which is the only feedback
 * available before the gesture completes — without it the user cannot tell
 * whether they have dragged far enough, and the usual result is a half-swipe
 * that springs back and reads as the app ignoring them.
 *
 * Colours are passed in rather than derived from the action, because "confirm"
 * and "destroy" are the caller's semantics and the scheme roles that express
 * them differ per screen. There is deliberately no hardcoded green: a
 * mark-as-flown action belongs to `tertiary`/`primary` in whatever theme is
 * active, including the near-black Cockpit one, and a fixed hue would be the
 * one element on screen that ignores it.
 *
 * The whole background is hidden from accessibility services: a swipe is a
 * pointer gesture, screen-reader users reach these actions through the row's
 * custom actions instead, and an announced decoration between rows would be
 * noise.
 */
@Composable
fun SwipeActionBackground(
    side: SwipeActionSide,
    icon: Painter,
    label: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    /** How far the card has travelled, 0 at rest and 1 fully swiped. */
    progress: Float = 1f,
    /** True once releasing would commit the action. */
    committed: Boolean = false,
) {
    val drag = progress.coerceIn(0f, 1f)

    // Read straight from the gesture, not through a spring. A spring here lags
    // the finger by its settling time, and the whole point of this surface is to
    // be attached to the finger — the card moves, and the thing revealed behind
    // it has to move with it or the two read as unrelated layers.
    val reveal = (drag * RevealRate).coerceIn(0f, 1f)

    // The one thing that *is* sprung: crossing the commit threshold. It is a
    // discrete event rather than a continuous one, so it gets a discrete
    // response — the icon swells and settles, which is the visual half of the
    // haptic that fires at the same moment.
    val commitScale by animateFloatAsState(
        targetValue = if (committed) 1f else 0f,
        animationSpec = FlightMotion.spatialFast(),
        label = "swipeActionCommit",
    )
    val scale = RestingScale + (1f - RestingScale) * reveal + CommitOvershoot * commitScale

    Box(
        modifier = modifier
            .fillMaxSize()
            .clearAndSetSemantics { }
            .clip(MaterialTheme.shapes.largeIncreased)
            // Fades up with the drag rather than appearing at full strength the
            // instant a finger moves. At rest it is invisible, so a list at rest
            // has no coloured bands hiding under its rows.
            .graphicsLayer { alpha = reveal }
            .background(containerColor)
            .padding(horizontal = 20.dp),
        contentAlignment = if (side == SwipeActionSide.Start) Alignment.CenterStart else Alignment.CenterEnd,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (side == SwipeActionSide.End) {
                Text(text = label, style = MaterialTheme.typography.labelLarge, color = contentColor)
            }
            Icon(
                painter = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier
                    .size(24.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    },
            )
            if (side == SwipeActionSide.Start) {
                Text(text = label, style = MaterialTheme.typography.labelLarge, color = contentColor)
            }
        }
    }
}

@LightDarkPreview
@Composable
private fun SwipeActionBackgroundPreview() {
    FlightPlannerTheme(dynamicColor = false) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SwipeActionBackground(
                side = SwipeActionSide.Start,
                icon = painterResource(R.drawable.ic_ds_check),
                label = "Mark flown",
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.height(72.dp),
                progress = 1f,
            )
            SwipeActionBackground(
                side = SwipeActionSide.End,
                icon = painterResource(R.drawable.ic_ds_shuffle),
                label = "Replace",
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.height(72.dp),
                progress = 0.4f,
            )
        }
    }
}

/** How fast the surface reaches full strength: opaque by two thirds of the drag. */
private const val RevealRate = 1.5f

/** Icon size at the very start of a drag, so growth is legible. */
private const val RestingScale = 0.7f

/** Extra scale once the gesture is committal. */
private const val CommitOvershoot = 0.15f
