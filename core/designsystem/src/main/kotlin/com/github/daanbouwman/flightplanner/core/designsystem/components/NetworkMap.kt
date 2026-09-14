package com.github.daanbouwman.flightplanner.core.designsystem.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.github.daanbouwman.flightplanner.core.designsystem.theme.FlightPlannerTheme
import com.github.daanbouwman.flightplanner.routing.GeoArc
import com.github.daanbouwman.flightplanner.routing.MapFrame
import com.github.daanbouwman.flightplanner.routing.RouteArc
import com.github.daanbouwman.flightplanner.routing.WorldOutline
import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * One airport on the visited network: where it is, and how often it has been
 * flown to or from.
 *
 * A geometry type rather than the app's `VisitedAirport`, for the reason
 * [RouteMap] takes a [GeoArc] and not a route: this module knows the shape of
 * things and nothing about where they were read from.
 */
@Immutable
class NetworkNode(
    val latitude: Double,
    val longitude: Double,
    /** How many movements touched this airport. Sizes the dot; see [NetworkMap]. */
    val visits: Int,
)

/**
 * Every airport the logbook has been to, and every leg between — the same map
 * as [RouteMap], with more on it.
 *
 * ### It is [RouteMap]'s ink, not a second map
 *
 * The first version of this lived in `:app` and wrote its own numbers: a 1.5 dp
 * leg beside the route card's 2.5 dp one, a casing half as wide, legs at 80 %
 * alpha under fully opaque dots, a margin twice the size, no arrowheads and no
 * graticule. Side by side on the Stats screen the two maps read as a sketch of
 * each other. Every stroke here is drawn from the same constants [RouteMap]
 * draws from — the coast, the cased leg, the arrowhead, the endpoint — so the
 * plan card and the network card are one map at two scales.
 *
 * ### What a network adds
 *
 * - **Direction.** A leg is an unordered pair of airports, but each was flown
 *   *first* one way, and that is the way its [GeoArc] runs; the arrowhead at
 *   its midpoint says so. A leg whose projected chord is shorter than
 *   [MinArrowChordDp] drops the head rather than stacking it on its own
 *   endpoints — the same threshold the route card applies to a short hop.
 * - **Size means count.** A dot's radius runs from [NodeMinRadiusDp] — the
 *   route card's own endpoint — to [NodeMaxRadiusDp] as the *square root* of
 *   `visits / maxVisits`, exactly as the globe's `GlobeNodes` does, so the
 *   dot's **area** is proportional to the count it stands for. Scaling the
 *   radius linearly would make a field visited nine times look nine times the
 *   size, which is the bubble-chart error an eye reads as area whether or not
 *   it was drawn as one.
 * - **A graticule when there is no coast.** A network wholly inland, or wholly
 *   at sea, would otherwise be dots on a flat wash that reads as a failed load.
 *
 * Every leg's casing is drawn before any leg's line, so where two legs cross
 * the network reads as one drawing rather than as a stack where the last leg
 * cuts the ones beneath it.
 *
 * The frame is fitted to the **nodes**, not the arcs: a great circle bows
 * poleward of its endpoints, and fitting the arcs would frame empty ocean
 * north of a transatlantic network to hold the top of the bow. The 12 % of
 * padding [MapFrame.forRoute] keeps clear covers the overshoot on every leg
 * the app draws.
 *
 * Semantics are cleared: the section heading beside this already states how
 * many airports and how many routes, and a screen reader has no use for the
 * drawing of them.
 */
@Composable
fun NetworkMap(
    nodes: List<NetworkNode>,
    /** Each leg once, sampled in the direction it was first flown. */
    legs: List<GeoArc>,
    outline: WorldOutline,
    modifier: Modifier = Modifier,
    landColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = WorldMapLandAlpha),
    coastColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = WorldMapCoastAlpha),
    routeColor: Color = MaterialTheme.colorScheme.primary,
    casingColor: Color = MaterialTheme.colorScheme.surfaceContainer,
) {
    Box(
        modifier = modifier
            .clearAndSetSemantics { }
            .drawWithCache {
                if (nodes.isEmpty() || size.minDimension <= 0f) return@drawWithCache onDrawBehind { }

                val lats = DoubleArray(nodes.size) { nodes[it].latitude }
                val lons = DoubleArray(nodes.size) { nodes[it].longitude }
                val frame = MapFrame.forRoute(
                    lats = lats,
                    lons = lons,
                    aspect = (size.width / size.height).toDouble(),
                )

                val land = frame.projectOutline(outline, margin = OutlineMargin)
                val landPath = land.fill.toPath(size.width, size.height, close = true).apply {
                    fillType = PathFillType.EvenOdd
                }
                val coastPath = land.coast.toPath(size.width, size.height, close = false)
                val graticulePath = if (land.coast.isEmpty) {
                    frame.graticule().toPath(size.width, size.height, close = false)
                } else {
                    null
                }

                val arrowLength = ArrowLengthDp.dp.toPx()
                val minArrowChord = MinArrowChordDp.dp.toPx()
                val legPaths = ArrayList<Path>(legs.size)
                val arrowPaths = ArrayList<Path>(legs.size)
                for (leg in legs) {
                    if (leg.size < 2) continue
                    val projected = frame.project(leg.lats, leg.lons)
                    legPaths += Path().apply {
                        moveTo(projected[0] * size.width, projected[1] * size.height)
                        for (i in 1 until projected.size / 2) {
                            lineTo(projected[i * 2] * size.width, projected[i * 2 + 1] * size.height)
                        }
                    }
                    val chord = hypot(
                        (projected[projected.size - 2] - projected[0]) * size.width,
                        (projected[projected.size - 1] - projected[1]) * size.height,
                    )
                    if (chord >= minArrowChord) {
                        arrowPath(projected, projected.size / 4, size.width, size.height, arrowLength)
                            ?.let(arrowPaths::add)
                    }
                }

                val maxVisits = nodes.maxOf { it.visits }.coerceAtLeast(1).toFloat()
                val minRadius = NodeMinRadiusDp.dp.toPx()
                val maxRadius = NodeMaxRadiusDp.dp.toPx()
                val centres = Array(nodes.size) { i ->
                    Offset(
                        x = frame.x(nodes[i].longitude) * size.width,
                        y = frame.y(nodes[i].latitude) * size.height,
                    )
                }
                val radii = FloatArray(nodes.size) { i ->
                    minRadius + (maxRadius - minRadius) * sqrt(nodes[i].visits.coerceAtLeast(0) / maxVisits)
                }

                val coastWidth = CoastStrokeDp.dp.toPx()
                val routeWidth = RouteStrokeDp.dp.toPx()
                val casing = CasingDp.dp.toPx()
                val casingStroke = Stroke(
                    width = routeWidth + 2f * casing,
                    join = StrokeJoin.Round,
                    cap = StrokeCap.Round,
                )
                val routeStroke = Stroke(width = routeWidth, join = StrokeJoin.Round, cap = StrokeCap.Round)
                val arrowCasing = Stroke(width = 2f * casing, join = StrokeJoin.Round, cap = StrokeCap.Round)

                onDrawBehind {
                    // The map crops itself, for the reason RouteMap gives: it
                    // projects past its own bounds by design.
                    clipRect {
                        drawPath(landPath, landColor)
                        graticulePath?.let {
                            drawPath(it, color = landColor, style = Stroke(width = coastWidth, cap = StrokeCap.Round))
                        }
                        drawPath(
                            path = coastPath,
                            color = coastColor,
                            style = Stroke(width = coastWidth, join = StrokeJoin.Bevel, cap = StrokeCap.Butt),
                        )

                        for (path in legPaths) drawPath(path, casingColor, style = casingStroke)
                        for (path in legPaths) drawPath(path, routeColor, style = routeStroke)

                        for (path in arrowPaths) {
                            drawPath(path, casingColor, style = arrowCasing)
                            drawPath(path, routeColor)
                        }

                        // Cased dots, largest first, so a hub's casing never
                        // erases the small field beside it.
                        val order = radii.indices.sortedByDescending { radii[it] }
                        for (i in order) {
                            drawCircle(color = casingColor, radius = radii[i] + casing, center = centres[i])
                        }
                        for (i in order) {
                            drawCircle(color = routeColor, radius = radii[i], center = centres[i])
                        }
                    }
                }
            },
    )
}

/** A field visited once: the route card's own endpoint, so the two maps agree. */
internal const val NodeMinRadiusDp = EndpointRadiusDp

/** The most-visited field in the set. */
internal const val NodeMaxRadiusDp = 9f

/**
 * Below this projected chord a leg carries no arrowhead.
 *
 * At 24 dp the head, its casing and the two endpoint dots would overlap into one
 * mark, and a mark that cannot be read is worse than a leg with no stated
 * direction — the neighbour it connects to still says where it goes.
 */
internal const val MinArrowChordDp = 24f

@LightDarkPreview
@Composable
private fun NetworkMapPreview() {
    FlightPlannerTheme(dynamicColor = false) {
        Box(
            modifier = Modifier
                .padding(16.dp)
                .width(328.dp)
                .height(180.dp),
        ) {
            NetworkMap(
                nodes = PreviewNodes,
                legs = PreviewLegs,
                outline = PreviewIsland,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** A hub flown from six times, four fields reached once or twice. */
private val PreviewNodes = listOf(
    NetworkNode(52.31, 4.76, visits = 6),
    NetworkNode(51.48, -0.46, visits = 2),
    NetworkNode(48.11, 16.57, visits = 1),
    NetworkNode(41.30, 2.08, visits = 2),
    NetworkNode(60.19, 11.10, visits = 1),
)

private val PreviewLegs = listOf(
    RouteArc.sampleGeographic(52.31, 4.76, 51.48, -0.46, samples = 32),
    RouteArc.sampleGeographic(52.31, 4.76, 48.11, 16.57, samples = 32),
    RouteArc.sampleGeographic(41.30, 2.08, 52.31, 4.76, samples = 32),
    RouteArc.sampleGeographic(52.31, 4.76, 60.19, 11.10, samples = 32),
)

/** One invented landmass, as RouteMap's preview has: layer order and weight, not geography. */
private val PreviewIsland = WorldOutline(
    lon = floatArrayOf(-5f, 20f, 25f, 10f, -5f),
    lat = floatArrayOf(44f, 42f, 58f, 62f, 44f),
    ringStart = intArrayOf(0, 5),
)
