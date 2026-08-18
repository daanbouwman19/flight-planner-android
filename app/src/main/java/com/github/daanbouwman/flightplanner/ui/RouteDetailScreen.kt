package com.github.daanbouwman.flightplanner.ui

import com.github.daanbouwman.flightplanner.ui.asFigure
import com.github.daanbouwman.flightplanner.core.designsystem.components.RouteMap
import com.github.daanbouwman.flightplanner.core.designsystem.components.ValueChip
import com.github.daanbouwman.flightplanner.model.Airport
import com.github.daanbouwman.flightplanner.ui.detail.RouteDetailUiState
import com.github.daanbouwman.flightplanner.ui.detail.RouteDetailViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
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
    viewModel: RouteDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
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
        RouteDetailContent(
            state = state,
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

/**
 * The route, at the size a route deserves when it is the only thing on screen.
 *
 * Deliberately the same facts the card carries, not more: the airframe, the two
 * ends with their full names and runways, the two figures, and the map — which is
 * the one element that gains from the room, because at card size it shows a coast
 * and here it shows the crossing. Phase C adds what needs a network or a
 * calculation the app cannot do yet: bearings, per-runway detail, weather.
 *
 * What it does **not** do is repeat the card's actions. Mark flown and Replace
 * belong to the list — they change what the list holds, and their undo lives
 * there — so a second copy here would be a second undo path to keep correct.
 */

@Composable
private fun RouteDetailContent(state: RouteDetailUiState, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        state.aircraft?.let { aircraft ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = aircraft.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = aircraft.category,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        state.arc?.let { arc ->
            Surface(
                shape = MaterialTheme.shapes.largeIncreased,
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                RouteMap(
                    arc = arc,
                    outline = state.outline,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(MapHeight),
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ValueChip(
                label = stringResource(R.string.plan_chip_distance),
                value = stringResource(R.string.plan_value_nautical_miles, state.distanceNm.asFigure()),
                modifier = Modifier.weight(1f),
            )
            ValueChip(
                label = stringResource(R.string.plan_chip_time),
                value = state.flightTime?.format() ?: EmptyFigure,
                modifier = Modifier.weight(1f),
            )
        }

        AirportBlock(
            label = stringResource(R.string.route_detail_departure),
            airport = state.departure,
            loading = state.loading,
        )
        AirportBlock(
            label = stringResource(R.string.route_detail_destination),
            airport = state.destination,
            loading = state.loading,
        )
    }
}

/**
 * One end of the route: what it is called, where it is, and what it offers.
 *
 * The name is here rather than on the card for the reason it left the card — at
 * 360 dp between two codes it truncated to "Stangland A...", and a truncated name
 * is worse than an absent one. Here there is a line to itself for it.
 */

@Composable
private fun AirportBlock(label: String, airport: Airport?, loading: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = airport?.icao ?: EmptyFigure,
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = when {
                airport != null -> listOfNotNull(airport.name, airport.municipality)
                    .distinct()
                    .joinToString(" · ")
                loading -> stringResource(R.string.route_detail_loading_airport)
                else -> stringResource(R.string.route_detail_missing_airport)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        airport?.let {
            Text(
                text = stringResource(R.string.plan_runway_value, it.longestRunwayFt.asFigure()),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** What a figure reads as before its airport has been read. */
private const val EmptyFigure = "—"

/** Taller than a card's map: this one is the subject rather than a background. */
private val MapHeight = 220.dp

/** How far the screen shrinks at full drag. Matches the platform's peek. */
private const val MAX_SCALE_DELTA = 0.10f

/** Enough fade to read as leaving, not enough to lose the content. */
private const val MAX_ALPHA_DELTA = 0.25f

private val maxShift = 24.dp
private val maxCornerRadius = 28.dp
