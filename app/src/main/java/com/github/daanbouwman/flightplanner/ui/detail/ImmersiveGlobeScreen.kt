package com.github.daanbouwman.flightplanner.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.daanbouwman.flightplanner.R
import com.github.daanbouwman.flightplanner.core.designsystem.components.ValueChip
import com.github.daanbouwman.flightplanner.core.designsystem.theme.SystemBarsOverMedia
import com.github.daanbouwman.flightplanner.core.designsystem.theme.withTabularFigures
import com.github.daanbouwman.flightplanner.feature.globe.ui.GlobeAttribution
import com.github.daanbouwman.flightplanner.feature.globe.ui.GlobeCameraControls
import com.github.daanbouwman.flightplanner.feature.globe.ui.GlobeControlButton
import com.github.daanbouwman.flightplanner.feature.globe.ui.GlobeImagery
import com.github.daanbouwman.flightplanner.feature.globe.ui.GlobeRoute
import com.github.daanbouwman.flightplanner.feature.globe.ui.GlobeSurface
import com.github.daanbouwman.flightplanner.feature.globe.ui.rememberGlobeControls
import com.github.daanbouwman.flightplanner.navigation.Destination
import com.github.daanbouwman.flightplanner.ui.asBearing
import com.github.daanbouwman.flightplanner.ui.distanceText

/**
 * The globe with the window to itself — the screen the hero pushes into.
 *
 * ### What changes, and what deliberately does not
 *
 * The sphere does not morph across the transition, and could not: a
 * `SurfaceView` is a hardware layer, not a view Compose can put in a shared
 * element overlay. What actually happens is better than a morph would have been
 * anyway — the **camera keeps its state**, because it lives on
 * `GlobeSession` rather than in either screen, so the frame around the globe
 * grows while the globe itself holds perfectly still. The chrome fades on an
 * effects spring while the box grows on a spatial one, which is the pairing
 * `RouteCard`'s entrance already uses.
 *
 * ### Why the figures come back here
 *
 * The route detail's spine says everything this plate says, at more length and
 * in the right place, which is why the deep hero carries no chips — see
 * [DeepGlobeHero]. Here there is no spine and no page: the whole window is a
 * sphere, and without one line of figures on it the screen would be a picture of
 * a planet with no idea which flight it is about. So the route reduces to one
 * plate: the pair, the airframe, and the three figures the arc is about.
 *
 * That is not the concept being applied inconsistently. It is the same rule —
 * *the figures the arc is about sit next to the arc* — reaching a different
 * answer because the arc is somewhere else.
 */
@Composable
fun ImmersiveGlobeScreen(
    route: Destination.RouteDetail,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RouteDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val controls = rememberGlobeControls()

    // Full-bleed photograph under the status bar the moment it exists — the
    // one surface in the app that is edge-to-edge imagery throughout. Unlike
    // the deep hero there is no scroll term: the sphere either has imagery or
    // it does not.
    var hasImagery by remember { mutableStateOf(false) }
    SystemBarsOverMedia(active = hasImagery)

    val globeRoute = remember(state.arc, route) {
        state.arc?.let {
            GlobeRoute(
                departureIcao = route.departureIcao,
                destinationIcao = route.destinationIcao,
                arcLats = it.lats,
                arcLons = it.lons,
            )
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        if (globeRoute == null) return@Surface

        GlobeSurface(
            route = globeRoute,
            controls = controls,
            // The collapse button and the camera stack, plus whatever the
            // system is drawing above them. A label that rises behind either
            // fades rather than moving off its airport.
            topChromeInset = WindowInsets.safeDrawing.asPaddingValues()
                .calculateTopPadding() + GlassGutter + ImmersiveControlSize,
            onImageryVisible = { hasImagery = it },
            modifier = Modifier.fillMaxSize(),
            overlay = {
                // Every control takes the safe-drawing inset itself rather than
                // the container taking it once. A container that outlives its
                // children and carries an inset padding paints a bar-height
                // strip of its own background — which over a globe is exactly
                // the opaque status bar this app spent Phase B+ removing.
                GlobeControlButton(
                    iconRes = com.github.daanbouwman.flightplanner.feature.globe.R.drawable
                        .ic_globe_fullscreen_exit,
                    contentDescription = stringResource(
                        com.github.daanbouwman.flightplanner.feature.globe.R.string.globe_collapse,
                    ),
                    onClick = onCollapse,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(GlassGutter),
                )

                GlobeCameraControls(
                    onZoomIn = controls::zoomIn,
                    onZoomOut = controls::zoomOut,
                    onResetNorth = if (controls.isRotated) controls::resetNorth else null,
                    onRefit = controls::refit,
                    bearingDegrees = controls.bearingDegrees,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(GlassGutter),
                )

                // The credit rides above the plate rather than in the corner
                // under it. Both want the bottom-left, and the plate is full
                // width, so the corner is not free - a licence notice half
                // behind a panel is not a licence notice. One column, no
                // background of its own, so the inset padding it takes paints
                // nothing across the gesture bar.
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(horizontal = GlassGutter)
                        .padding(bottom = PlateBottomGutter),
                    verticalArrangement = Arrangement.spacedBy(GlassGutter),
                ) {
                    GlobeAttribution(attribution = GlobeImagery.attribution)
                    RoutePlate(route = route, state = state)
                }
            },
        )
    }
}

/**
 * The whole route as one line of figures, on a plate over the sphere.
 *
 * A `Surface` at `surfaceContainer` rather than a translucent `Box`: it needs a
 * shape from the scale and a content colour that follows it, and the chips
 * inside are already translucent so the plate reads as a layer either way.
 */
@Composable
private fun RoutePlate(
    route: Destination.RouteDetail,
    state: RouteDetailUiState,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.largeIncreased,
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = PlateAlpha),
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    text = stringResource(
                        R.string.route_detail_title_spoken,
                        route.departureIcao,
                        route.destinationIcao,
                    ),
                    style = MaterialTheme.typography.titleLarge.withTabularFigures(),
                )
                Text(
                    text = state.aircraft?.displayName.orEmpty(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ValueChip(
                    label = stringResource(R.string.plan_chip_distance),
                    value = distanceText(state.distanceNm),
                    containerAlpha = ChipAlpha,
                )
                state.initialBearingDeg?.let {
                    ValueChip(
                        label = stringResource(R.string.route_detail_bearing_label),
                        value = it.asBearing(),
                        containerAlpha = ChipAlpha,
                    )
                }
                state.flightTime?.let {
                    ValueChip(
                        label = stringResource(R.string.plan_chip_time),
                        value = it.format(),
                        containerAlpha = ChipAlpha,
                    )
                }
            }
        }
    }
}

/** The globe module’s own control size, mirrored so the inset can be worked out. */
private val ImmersiveControlSize = 44.dp

private val GlassGutter: Dp = 12.dp

/** Clear of the gesture handle, so the plate is not where a back swipe starts. */
private val PlateBottomGutter: Dp = 24.dp

private const val PlateAlpha = 0.78f

private const val ChipAlpha = 0.5f

