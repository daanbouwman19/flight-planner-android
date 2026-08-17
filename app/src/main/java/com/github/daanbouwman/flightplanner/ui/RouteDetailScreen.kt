package com.github.daanbouwman.flightplanner.ui

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.daanbouwman.flightplanner.R
import com.github.daanbouwman.flightplanner.core.designsystem.components.EmptyState
import com.github.daanbouwman.flightplanner.core.designsystem.motion.FlightMotion
import com.github.daanbouwman.flightplanner.core.designsystem.motion.rememberReduceMotion
import com.github.daanbouwman.flightplanner.navigation.Destination
import kotlinx.coroutines.CancellationException

/**
 * Placeholder for the route detail screen. Phase C fills it in; what is real
 * here is the argument round trip and the back behaviour.
 *
 * Back is progressive rather than binary. As the gesture is dragged the screen
 * shrinks, rounds and drifts away from the swiped edge, so the user can see how
 * far they have committed and can change their mind — releasing under the
 * threshold springs it back rather than snapping. This matters most on the
 * screen a user enters and leaves constantly, which detail is.
 *
 * The spring that settles a cancelled gesture is the design system's `spatial`
 * token, so it is the same physics as every other movement in the app; :app
 * never writes a spec of its own.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteDetailScreen(
    route: Destination.RouteDetail,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backProgress = remember { Animatable(0f) }
    var swipeEdge by remember { mutableIntStateOf(BackEventCompat.EDGE_LEFT) }
    val settle = FlightMotion.spatial<Float>()

    // With animations off, the gesture still works and still commits — it simply
    // does not draw the peek. Reduce-motion disables the effect, not the feature.
    val reduceMotion = rememberReduceMotion()

    PredictiveBackHandler { events ->
        try {
            events.collect { event ->
                swipeEdge = event.swipeEdge
                if (!reduceMotion) backProgress.snapTo(event.progress)
            }
            onBack()
        } catch (cancellation: CancellationException) {
            // The gesture was abandoned, not the coroutine: this callback is
            // still live, so the screen can be sprung back into place here.
            backProgress.animateTo(0f, settle)
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                val progress = backProgress.value
                if (progress > 0f) {
                    val scale = 1f - MAX_SCALE_DELTA * progress
                    scaleX = scale
                    scaleY = scale
                    alpha = 1f - MAX_ALPHA_DELTA * progress
                    translationX = when (swipeEdge) {
                        BackEventCompat.EDGE_LEFT -> maxShift.toPx()
                        else -> -maxShift.toPx()
                    } * progress
                    shape = RoundedCornerShape(maxCornerRadius * progress)
                    clip = true
                }
            },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            R.string.route_detail_title,
                            route.departureIcao,
                            route.destinationIcao,
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
        // No navigation suite is shown over a detail screen, so unlike the
        // section scaffolds this one owns the bottom inset as well.
        contentWindowInsets = WindowInsets.safeDrawing
            .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
    ) { contentPadding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(contentPadding),
            contentAlignment = Alignment.Center,
        ) {
            EmptyState(
                title = stringResource(R.string.route_detail_empty_title),
                message = stringResource(
                    R.string.route_detail_empty_message,
                    route.distanceNm,
                ),
            )
        }
    }
}

/** How far the screen shrinks at full drag. Matches the platform's peek. */
private const val MAX_SCALE_DELTA = 0.10f

/** Enough fade to read as leaving, not enough to lose the content. */
private const val MAX_ALPHA_DELTA = 0.25f

private val maxShift = 24.dp
private val maxCornerRadius = 28.dp
