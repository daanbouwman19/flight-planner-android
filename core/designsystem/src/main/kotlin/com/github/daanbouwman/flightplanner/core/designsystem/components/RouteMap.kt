package com.github.daanbouwman.flightplanner.core.designsystem.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import kotlin.math.sqrt
import com.github.daanbouwman.flightplanner.core.designsystem.theme.FlightPlannerTheme
import com.github.daanbouwman.flightplanner.routing.GeoArc
import com.github.daanbouwman.flightplanner.routing.MapFrame
import com.github.daanbouwman.flightplanner.routing.ProjectedRings
import com.github.daanbouwman.flightplanner.routing.RouteArc
import com.github.daanbouwman.flightplanner.routing.WorldOutline

/**
 * A route drawn on the piece of the world it crosses.
 *
 * The sparkline this replaces proved a route had a *shape*; it could not say
 * where that shape was, so a bowed arc over the Pacific and one over the
 * Atlantic drew identically. Putting land under the curve turns it into a place,
 * which is most of what a route is.
 *
 * ### The map is texture, not imagery
 *
 * Land is a fill at 8 % of `onSurface` and its coast a stroke at 16 %. That is
 * enough for a silhouette to be recognisable and far too little to compete with
 * the figures drawn over it — which is the point: a photograph or a
 * full-contrast map behind dense text needs a scrim to stay readable, and a
 * design that needs no scrim is simpler than one hiding behind a gradient. The
 * route is the only saturated thing here, so at 2.5 dp of `primary` it reads as
 * the subject immediately.
 *
 * ### Cased lines
 *
 * Every line is drawn twice: a wider stroke in the card's own colour first, then
 * the line on top. That is the technique aeronautical and road charts use to keep
 * a route legible wherever it crosses something else, and it is what stops the
 * arc from disappearing into a coastline it happens to run along.
 *
 * ### Geometry is built once per size, not per frame
 *
 * `drawWithCache` rebuilds the paths when the arc, the outline or the canvas
 * changes and never while scrolling. The expensive part — spherical
 * interpolation — happened once per route on a background dispatcher, well
 * before this composable saw it; what is left here is a multiply and an add per
 * point.
 *
 * The map carries no information the card does not state in text, so it is
 * hidden from accessibility services outright.
 */
@Composable
fun RouteMap(
    arc: GeoArc,
    outline: WorldOutline,
    modifier: Modifier = Modifier,
    landColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = LandAlpha),
    coastColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = CoastAlpha),
    routeColor: Color = MaterialTheme.colorScheme.primary,
    casingColor: Color = MaterialTheme.colorScheme.surfaceContainer,
) {
    Box(
        modifier = modifier
            .clearAndSetSemantics { }
            .drawWithCache {
                if (arc.size < 2 || size.minDimension <= 0f) return@drawWithCache onDrawBehind { }

                val frame = MapFrame.forRoute(
                    lats = arc.lats,
                    lons = arc.lons,
                    aspect = (size.width / size.height).toDouble(),
                )

                // A margin, so a coast just off the card still contributes the
                // segment that enters it, and a stroke's trimmed end falls
                // outside the visible area rather than inside it.
                val land = frame.projectOutline(outline, margin = OutlineMargin)

                // Two paths from the same clip, because they are drawn
                // differently: the fill is a polygon whose boundary runs along
                // the window's edge, and stroking that would draw a hairline box
                // around the card. The coast is the real coastline, trimmed and
                // left open.
                val landPath = land.fill.toPath(size.width, size.height, close = true).apply {
                    // Even-odd, so a ring enclosed by another — the Caspian, the
                    // Great Lakes — is a hole rather than more land.
                    fillType = PathFillType.EvenOdd
                }
                val coastPath = land.coast.toPath(size.width, size.height, close = false)

                // Nothing but ocean, or the middle of a continent: with no coast in
                // the window the card is a flat wash, which reads as a failed load
                // rather than as a place. A graticule says "this is the world, and
                // you are looking at a part of it with no coastline in it" for the
                // price of a few lines at a third of the coast's contrast.
                val graticule = if (land.coast.isEmpty) frame.graticule() else null

                val projected = frame.project(arc.lats, arc.lons)
                val routePath = Path().apply {
                    moveTo(projected[0] * size.width, projected[1] * size.height)
                    for (i in 1 until projected.size / 2) {
                        lineTo(projected[i * 2] * size.width, projected[i * 2 + 1] * size.height)
                    }
                }
                val graticulePath = graticule?.toPath(size.width, size.height, close = false)

                // The arrowhead sits at the middle sample and points along the two
                // samples either side of it, so it follows the curve rather than the
                // straight line between the ends.
                val midpoint = projected.size / 4
                val arrow = arrowPath(projected, midpoint, size.width, size.height, ArrowLengthDp.dp.toPx())
                val departure = Offset(projected[0] * size.width, projected[1] * size.height)
                val destination = Offset(
                    projected[projected.size - 2] * size.width,
                    projected[projected.size - 1] * size.height,
                )

                val coastWidth = CoastStrokeDp.dp.toPx()
                val routeWidth = RouteStrokeDp.dp.toPx()
                val casingWidth = routeWidth + 2f * CasingDp.dp.toPx()
                val endpointRadius = EndpointRadiusDp.dp.toPx()
                val endpointStroke = EndpointStrokeDp.dp.toPx()

                onDrawBehind {
                    drawPath(landPath, landColor)
                    graticulePath?.let {
                        drawPath(
                            path = it,
                            color = landColor,
                            style = Stroke(width = coastWidth, cap = StrokeCap.Round),
                        )
                    }
                    drawPath(
                        path = coastPath,
                        color = coastColor,
                        style = Stroke(width = coastWidth, join = StrokeJoin.Round, cap = StrokeCap.Round),
                    )

                    // Casing first, then the line: round joins and caps are what
                    // make the pair read as one ribbon instead of as a stack of
                    // segments with mitred corners showing through.
                    drawPath(
                        path = routePath,
                        color = casingColor,
                        style = Stroke(width = casingWidth, join = StrokeJoin.Round, cap = StrokeCap.Round),
                    )
                    drawPath(
                        path = routePath,
                        color = routeColor,
                        style = Stroke(width = routeWidth, join = StrokeJoin.Round, cap = StrokeCap.Round),
                    )

                    // The arrowhead, cased like everything else. It is what makes
                    // direction readable at a glance: the codes are pinned to the
                    // card's edges, so on a westbound leg the departure code sits
                    // on the left while its marker sits on the right, and a 4 dp
                    // ring against a 4 dp dot is too fine a distinction to carry
                    // that alone.
                    arrow?.let {
                        drawPath(
                            path = it,
                            color = casingColor,
                            style = Stroke(
                                width = 2f * CasingDp.dp.toPx(),
                                join = StrokeJoin.Round,
                                cap = StrokeCap.Round,
                            ),
                        )
                        drawPath(path = it, color = routeColor)
                    }

                    // Departure hollow, destination filled: the chart convention
                    // for "from here to there", which the arrowhead now states
                    // outright rather than implying.
                    drawCircle(
                        color = casingColor,
                        radius = endpointRadius + CasingDp.dp.toPx(),
                        center = departure,
                        style = Stroke(width = endpointStroke + 2f * CasingDp.dp.toPx()),
                    )
                    drawCircle(
                        color = routeColor,
                        radius = endpointRadius,
                        center = departure,
                        style = Stroke(width = endpointStroke),
                    )
                    drawCircle(
                        color = casingColor,
                        radius = endpointRadius + CasingDp.dp.toPx(),
                        center = destination,
                    )
                    drawCircle(color = routeColor, radius = endpointRadius, center = destination)
                }
            },
    )
}

/**
 * Land, at 8 % of `onSurface`.
 *
 * Chosen against a mid-contrast map that reads unmistakably as a map — and needs
 * a scrim under every figure on the card to stay legible. Text keeps essentially
 * its full contrast against this.
 */
private const val LandAlpha = 0.08f

/** The coast, at twice the fill, which is what makes a silhouette recognisable. */
private const val CoastAlpha = 0.16f

private const val CoastStrokeDp = 1f
private const val RouteStrokeDp = 2.5f

/** Half-width of the casing under each line, per side. */
private const val CasingDp = 1.5f

/** Half-length of the direction arrowhead. */
private const val ArrowLengthDp = 5f

/** The arrowhead's half-width as a fraction of its length: a narrow, chart-like head. */
private const val ArrowHalfWidth = 0.62f

private const val EndpointRadiusDp = 4f
private const val EndpointStrokeDp = 2f

/** Extra window projected around the card, as a fraction of each span. */
private const val OutlineMargin = 0.05

@LightDarkPreview
@Composable
private fun RouteMapPreview() {
    FlightPlannerTheme(dynamicColor = false) {
        Box(
            modifier = Modifier
                .padding(16.dp)
                .width(328.dp)
                .height(180.dp),
        ) {
            RouteMap(
                arc = RouteArc.sampleGeographic(52.31, 4.76, 35.55, 139.78, samples = RouteArc.CARD_SAMPLES),
                // The preview has no asset to read, so it draws one invented
                // island: what these previews are for is the layer order and the
                // weight of each line, and the real coast is judged on a device.
                outline = PreviewOutline,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private val PreviewOutline = WorldOutline(
    lon = floatArrayOf(60f, 120f, 130f, 90f, 60f),
    lat = floatArrayOf(20f, 25f, 60f, 65f, 20f),
    ringStart = intArrayOf(0, 5),
)

/**
 * Walks projected rings into a `Path`, scaling the fractions to pixels.
 *
 * @param close true for the filled polygons, false for the coastline: an open
 *   polyline that gets closed grows a straight segment from its last point back
 *   to its first, straight across the card.
 */
private fun ProjectedRings.toPath(width: Float, height: Float, close: Boolean): Path {
    val path = Path()
    for (ring in 0 until ringCount) {
        val from = ringStart[ring]
        val to = ringStart[ring + 1]
        if (to - from < 2) continue
        path.moveTo(points[from * 2] * width, points[from * 2 + 1] * height)
        for (i in from + 1 until to) {
            path.lineTo(points[i * 2] * width, points[i * 2 + 1] * height)
        }
        if (close) path.close()
    }
    return path
}

/**
 * A filled arrowhead sitting on the arc at [index], pointing the way the route
 * runs there.
 *
 * Drawn as a triangle rather than as `MaterialShapes.Arrow`: at eight pixels a
 * side, a rounded polygon's corner radii eat most of the tip, and the tip is the
 * entire signal. The direction comes from the samples *either side* of the
 * midpoint, so it follows the curve rather than the chord between the ends.
 *
 * @return null when the arc is too short to have a direction at all.
 */
private fun arrowPath(
    projected: FloatArray,
    index: Int,
    width: Float,
    height: Float,
    length: Float,
): Path? {
    val count = projected.size / 2
    if (count < 3 || index <= 0 || index >= count - 1) return null

    val x = projected[index * 2] * width
    val y = projected[index * 2 + 1] * height
    val dx = (projected[(index + 1) * 2] - projected[(index - 1) * 2]) * width
    val dy = (projected[(index + 1) * 2 + 1] - projected[(index - 1) * 2 + 1]) * height
    val magnitude = sqrt(dx * dx + dy * dy)
    // A route whose midpoint samples coincide has no heading to draw — a
    // departure and destination at the same airport, which the generator can
    // produce with a locked departure.
    if (magnitude < 1e-3f) return null

    val ux = dx / magnitude
    val uy = dy / magnitude
    // Perpendicular, for the two trailing corners.
    val px = -uy
    val py = ux
    val half = length * ArrowHalfWidth

    return Path().apply {
        moveTo(x + ux * length, y + uy * length)
        lineTo(x - ux * length * 0.4f + px * half, y - uy * length * 0.4f + py * half)
        lineTo(x - ux * length * 0.4f - px * half, y - uy * length * 0.4f - py * half)
        close()
    }
}
