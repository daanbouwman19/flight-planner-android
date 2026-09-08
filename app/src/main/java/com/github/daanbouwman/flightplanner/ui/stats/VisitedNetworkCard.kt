package com.github.daanbouwman.flightplanner.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.daanbouwman.flightplanner.R
import com.github.daanbouwman.flightplanner.core.designsystem.components.ModeOption
import com.github.daanbouwman.flightplanner.core.designsystem.components.ModeSelector
import com.github.daanbouwman.flightplanner.core.designsystem.components.WorldMapCoastAlpha
import com.github.daanbouwman.flightplanner.core.designsystem.components.WorldMapLandAlpha
import com.github.daanbouwman.flightplanner.core.designsystem.components.toPath
import com.github.daanbouwman.flightplanner.core.designsystem.theme.asChartFigure
import com.github.daanbouwman.flightplanner.feature.globe.ui.GlobeAttribution
import com.github.daanbouwman.flightplanner.feature.globe.ui.GlobeCameraControls
import com.github.daanbouwman.flightplanner.feature.globe.ui.GlobeImagery
import com.github.daanbouwman.flightplanner.feature.globe.ui.GlobeNetwork
import com.github.daanbouwman.flightplanner.feature.globe.ui.GlobeNetworkSurface
import com.github.daanbouwman.flightplanner.feature.globe.ui.GlobeNode
import com.github.daanbouwman.flightplanner.feature.globe.ui.rememberGlobeAvailable
import com.github.daanbouwman.flightplanner.feature.globe.ui.rememberGlobeControls
import com.github.daanbouwman.flightplanner.routing.MapFrame
import com.github.daanbouwman.flightplanner.routing.WorldOutline

/** Which projection the visited network is drawn in. */
private enum class NetworkView { Flat, Globe }

/**
 * The visited network: every airport the logbook has been to, and every leg
 * between — flat, or on the sphere.
 *
 * ### Why the sphere is offered at all, and why it is not the default
 *
 * A fitted rectangle is the right drawing for a logbook that fits in one region:
 * it spends every pixel on the part of the world that has been flown, and the
 * distortion over a few thousand miles is not visible. It stops being the right
 * drawing the moment the set spans hemispheres — the frame widens to hold both,
 * the airports collapse into two clusters at the edges, and the arc between them
 * becomes a line across an empty middle that is nothing like the path a flight
 * takes. On a sphere that same pair is one short arc over the pole.
 *
 * So the toggle is not a novelty setting: it is the choice between the projection
 * that is honest about *density* and the one that is honest about *distance*.
 * **Flat is first** because most logbooks are regional and the flat map is the
 * cheaper, sharper, immediately-readable answer for them; the globe is a step
 * taken deliberately by somebody whose network has outgrown a rectangle.
 *
 * The toggle is **absent** on a device with no renderer, following 3B: a control
 * that opens nothing is worse than no control, and the flat map is not a fallback
 * there — it is simply the map.
 */
@Composable
fun VisitedNetworkCard(
    visitedAirports: List<VisitedAirport>,
    visitedLegs: List<VisitedLeg>,
    outline: WorldOutline,
    modifier: Modifier = Modifier,
) {
    val globeAvailable = rememberGlobeAvailable()
    var view by rememberSaveable { mutableStateOf(NetworkView.Flat) }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            // Vertical only: the globe band below breaks the horizontal inset,
            // so the padding is applied per child rather than to the column.
            modifier = Modifier.padding(vertical = 16.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.stats_section_network),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.2.sp,
                )
                Text(
                    text = stringResource(
                        R.string.stats_network_summary_format,
                        visitedAirports.size,
                        visitedLegs.size,
                    ),
                    style = MaterialTheme.typography.labelSmall.asChartFigure(),
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            if (globeAvailable) {
                Spacer(Modifier.height(10.dp))
                ModeSelector(
                    options = listOf(
                        ModeOption(
                            label = stringResource(R.string.stats_network_mode_flat),
                            contentDescription =
                                stringResource(R.string.stats_network_mode_flat_description),
                        ),
                        ModeOption(
                            label = stringResource(R.string.stats_network_mode_globe),
                            contentDescription =
                                stringResource(R.string.stats_network_mode_globe_description),
                        ),
                    ),
                    selectedIndex = view.ordinal,
                    onSelect = { index -> view = NetworkView.entries[index] },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            Spacer(Modifier.height(12.dp))

            if (globeAvailable && view == NetworkView.Globe) {
                GlobeNetworkBand(visitedAirports, visitedLegs, outline)
            } else {
                FlatNetworkMap(
                    visitedAirports = visitedAirports,
                    visitedLegs = visitedLegs,
                    outline = outline,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .height(FlatMapHeight)
                        .clip(MaterialTheme.shapes.medium),
                )
            }
        }
    }
}

/**
 * The same network on the sphere, as a full-bleed band across the card.
 *
 * ### Why it breaks the card's inset and takes square corners
 *
 * The globe is a `SurfaceView` composited **below** the window, and a Compose
 * `clip` is a render-node clip on the Compose view — it never reaches the
 * surface. Rounded corners on this box would therefore be rounded in a preview
 * and square on a device, which is the exact failure mode this project's
 * screenshot rule exists to catch. Rather than ship corners that are a lie, the
 * globe is presented the way full-bleed media is presented: edge to edge, square,
 * spanning the card.
 *
 * It is also taller than the flat map. A sphere in a 180 dp letterbox is a disc
 * with its poles cropped; the flat map is fitted to its box and the globe is not,
 * so the globe needs a box closer to square before the curvature reads.
 */
@Composable
private fun GlobeNetworkBand(
    visitedAirports: List<VisitedAirport>,
    visitedLegs: List<VisitedLeg>,
    outline: WorldOutline,
) {
    val controls = rememberGlobeControls()
    val network = remember(visitedAirports, visitedLegs) {
        GlobeNetwork(
            nodes = visitedAirports.map { airport ->
                GlobeNode(
                    icao = airport.icao,
                    latitude = airport.latitude,
                    longitude = airport.longitude,
                    visits = airport.visitCount,
                )
            },
            legs = visitedLegs.map { leg -> leg.arc.lats to leg.arc.lons },
        )
    }

    GlobeNetworkSurface(
        network = network,
        controls = controls,
        // A band inside the Stats list. A vertical one-finger drag over it
        // scrolls the list, as it does over every other card; sideways and
        // two-finger gestures still turn the globe.
        nestedVerticalScroll = true,
        // ...and this globe is *embedded*: a `LazyColumn` item inside a rounded
        // card, rather than a full-bleed hero that owns its box. Two things
        // follow, and both are bugs when they are missing — it is drawn by a
        // `TextureView` so Compose can actually clip it, and the session gets a
        // longer teardown grace so scrolling past the card does not rebuild the
        // engine and the 32 MB atlas. See `GlobeTextureView`.
        embedded = true,
        modifier = Modifier
            .fillMaxWidth()
            .height(GlobeBandHeight),
        content = {
            // What the globe crossfades in over while the first tiles land. The
            // same drawing the Flat mode shows, so the swap is a projection
            // changing rather than the panel emptying and refilling.
            FlatNetworkMap(
                visitedAirports = visitedAirports,
                visitedLegs = visitedLegs,
                outline = outline,
                modifier = Modifier.fillMaxSize(),
            )
        },
        overlay = {
            GlobeCameraControls(
                onZoomIn = controls::zoomIn,
                onZoomOut = controls::zoomOut,
                onResetNorth = if (controls.isRotated) controls::resetNorth else null,
                onRefit = controls::refit,
                bearingDegrees = controls.bearingDegrees,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(GlassGutter),
            )
            GlobeAttribution(
                attribution = GlobeImagery.attribution,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(GlassGutter),
            )
        },
    )
}

/**
 * The world's coastlines, the visited airports, and the legs between them, fitted
 * into the box.
 *
 * Semantics are cleared: the figures beside the section heading already state how
 * many airports and how many routes, and a screen reader has no use for the
 * drawing of them.
 */
@Composable
private fun FlatNetworkMap(
    visitedAirports: List<VisitedAirport>,
    visitedLegs: List<VisitedLeg>,
    outline: WorldOutline,
    modifier: Modifier = Modifier,
) {
    val landColor = MaterialTheme.colorScheme.onSurface.copy(alpha = WorldMapLandAlpha)
    val coastColor = MaterialTheme.colorScheme.onSurface.copy(alpha = WorldMapCoastAlpha)
    val routeColor = MaterialTheme.colorScheme.primary
    val casingColor = MaterialTheme.colorScheme.surfaceContainer

    Box(
        modifier = modifier
            .clearAndSetSemantics { }
            .drawWithCache {
                if (size.minDimension <= 0f) return@drawWithCache onDrawBehind { }

                val lats: DoubleArray
                val lons: DoubleArray
                if (visitedAirports.isNotEmpty()) {
                    lats = visitedAirports.map { it.latitude }.toDoubleArray()
                    lons = visitedAirports.map { it.longitude }.toDoubleArray()
                } else {
                    lats = doubleArrayOf(-30.0, 60.0)
                    lons = doubleArrayOf(-120.0, 120.0)
                }

                val frame = MapFrame.forRoute(
                    lats = lats,
                    lons = lons,
                    aspect = (size.width / size.height).toDouble(),
                )

                val land = frame.projectOutline(outline, margin = 0.1)
                val landPath = land.fill.toPath(size.width, size.height, close = true).apply {
                    fillType = PathFillType.EvenOdd
                }
                val coastPath = land.coast.toPath(size.width, size.height, close = false)

                val projectedLegs = visitedLegs.map { leg ->
                    val pts = frame.project(leg.arc.lats, leg.arc.lons)
                    val path = Path().apply {
                        if (pts.size >= 4) {
                            moveTo(pts[0] * size.width, pts[1] * size.height)
                            for (i in 2 until pts.size step 2) {
                                lineTo(pts[i] * size.width, pts[i + 1] * size.height)
                            }
                        }
                    }
                    path
                }

                val projectedAirports = visitedAirports.map { airport ->
                    Offset(
                        x = frame.x(airport.longitude) * size.width,
                        y = frame.y(airport.latitude) * size.height,
                    )
                }

                onDrawBehind {
                    clipRect {
                        drawPath(landPath, color = landColor)
                        drawPath(
                            coastPath,
                            color = coastColor,
                            style = Stroke(
                                width = 1.dp.toPx(),
                                cap = StrokeCap.Butt,
                                join = StrokeJoin.Bevel,
                            ),
                        )

                        for (legPath in projectedLegs) {
                            drawPath(
                                legPath,
                                color = casingColor,
                                style = Stroke(
                                    width = 3.dp.toPx(),
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round,
                                ),
                            )
                            drawPath(
                                legPath,
                                color = routeColor.copy(alpha = 0.8f),
                                style = Stroke(
                                    width = 1.5.dp.toPx(),
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round,
                                ),
                            )
                        }

                        for (point in projectedAirports) {
                            drawCircle(
                                color = casingColor,
                                radius = 4.dp.toPx(),
                                center = point,
                            )
                            drawCircle(
                                color = routeColor,
                                radius = 2.5.dp.toPx(),
                                center = point,
                            )
                        }
                    }
                }
            },
    )
}

private val FlatMapHeight: Dp = 180.dp

/** Taller than the flat map: a sphere needs a box closer to square to read as one. */
private val GlobeBandHeight: Dp = 260.dp

private val GlassGutter: Dp = 12.dp
