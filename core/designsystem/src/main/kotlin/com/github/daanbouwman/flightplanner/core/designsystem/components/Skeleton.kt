package com.github.daanbouwman.flightplanner.core.designsystem.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.github.daanbouwman.flightplanner.core.designsystem.motion.LocalReduceMotion
import com.github.daanbouwman.flightplanner.core.designsystem.theme.FlightPlannerTheme

/**
 * A placeholder block that shimmers while real content loads.
 *
 * Skeletons beat a spinner for list content because they say *where* the content
 * will be, so the layout does not jump when it arrives. They are worse than a
 * spinner when the shape is unknown — do not skeleton something whose size you
 * are guessing at.
 *
 * ### Reduce motion
 *
 * When the system animator duration scale is zero the shimmer is **switched off
 * entirely**, not shortened. This is the case the general "Compose shortens
 * animations for you" rule does not cover: an [infiniteRepeatable] has no
 * duration to shorten, so left alone it would keep pulsing forever on exactly the
 * device whose owner asked for less movement. The block stays visible at the
 * shimmer's midpoint alpha, so the layout is unchanged and only the motion stops.
 */
@Composable
fun SkeletonBox(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.small,
) {
    // Read, not resolved: FlightPlannerTheme owns the one ContentObserver.
    val reduceMotion = LocalReduceMotion.current
    val color = MaterialTheme.colorScheme.onSurface

    // A State<Float>, deliberately not a Float. Unwrapping it here would make the
    // shimmer a composition-scope read, recomposing every box on every frame of
    // the 900 ms cycle; `drawBehind` below reads it in the draw scope instead, so
    // a frame costs a redraw and nothing else. On a loading list of thirty boxes
    // that is the difference between the skeleton being free and it being the
    // most expensive thing on screen.
    val alpha: State<Float> = if (reduceMotion) {
        remember { mutableFloatStateOf(MidAlpha) }
    } else {
        val transition = rememberInfiniteTransition(label = "skeleton")
        transition.animateFloat(
            initialValue = MinAlpha,
            targetValue = MaxAlpha,
            animationSpec = infiniteRepeatable(
                // A duration, not a spring: this is a metronome, and a spring
                // would make the pulse uneven. It is also the one animation in
                // the app that is deliberately slow -- a fast shimmer reads as
                // an error condition rather than as waiting.
                animation = tween(durationMillis = ShimmerPeriodMillis),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "skeletonAlpha",
        )
    }

    Box(
        modifier = modifier
            // A skeleton has no meaning to announce; without this each block is
            // an unlabelled node that TalkBack stops on while the screen loads.
            .clearAndSetSemantics { }
            .clip(shape)
            .drawBehind { drawRect(color = color.copy(alpha = alpha.value)) },
    )
}

/**
 * The loading stand-in for one route or airport card.
 *
 * Three bars in the proportions of a real card — a title, a subtitle and a short
 * value line — so the list does not reflow when the data lands.
 */
@Composable
fun SkeletonCard(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SkeletonBox(modifier = Modifier.fillMaxWidth(0.55f).height(20.dp))
        SkeletonBox(modifier = Modifier.fillMaxWidth(0.80f).height(14.dp))
        SkeletonBox(modifier = Modifier.fillMaxWidth(0.35f).height(14.dp))
    }
}

private const val MinAlpha = 0.08f
private const val MaxAlpha = 0.20f
private const val MidAlpha = (MinAlpha + MaxAlpha) / 2f
private const val ShimmerPeriodMillis = 900

@LightDarkPreview
@Composable
private fun SkeletonCardPreview() {
    FlightPlannerTheme(dynamicColor = false) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SkeletonCard()
            SkeletonCard()
        }
    }
}
