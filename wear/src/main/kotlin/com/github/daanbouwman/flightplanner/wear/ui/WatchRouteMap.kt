package com.github.daanbouwman.flightplanner.wear.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp
import com.github.daanbouwman.flightplanner.routing.GeoArc
import com.github.daanbouwman.flightplanner.routing.MapFrame
import com.github.daanbouwman.flightplanner.routing.ProjectedRings
import com.github.daanbouwman.flightplanner.routing.WorldOutline

/**
 * The route, drawn over the world, filling the whole face.
 *
 * ### Why this is not `:core:designsystem`'s `RouteMap`
 *
 * `RouteMapScene` on the phone imports no Material and would very nearly
 * compile here — it is a `Canvas` over `:core:routing` geometry, and the
 * home-screen widget already shares it. But it lives in `:core:designsystem`,
 * which is built on `androidx.compose.material3`: depending on it would pull the
 * phone's Material library, pinned to an alpha for the Expressive surface, into
 * a watch APK, which is exactly the coupling that module's pin is meant to
 * prevent.
 *
 * So the **geometry** is shared, all of it — [MapFrame]'s projection, its
 * bounding-box rejection and window clipping, [WorldOutline], the sampled great
 * circle — and only the *drawing* is written again, which is fifty lines and is
 * genuinely different anyway: a round face wants a map that bleeds past the
 * bezel, where a rectangular card wants one that fits inside it. The right time
 * to reconsider is when a second watch screen needs it, at which point the scene
 * builder belongs in a Compose-but-not-Material module both can see.
 *
 * ### The palette
 *
 * Every ink here is dark enough to carry the face's text on top of it without a
 * scrim. A scrim over a map is what the phone's Plan screen was corrected away
 * from (see CLAUDE.md on the system bars), and on a round face it would show as
 * a band across the middle of the circle. Choosing dark inks instead costs
 * nothing: this map is a ground for the route, not a chart to read place names
 * off.
 */
@Composable
internal fun WatchRouteMap(
    arc: GeoArc,
    outline: WorldOutline,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        if (arc.size < 2 || size.minDimension <= 0f) return@Canvas

        // The map is drawn larger than the face and centred, so it runs off
        // every edge of the circle rather than stopping at a visible square.
        // The design canvas calls this the map zoom and sets it at 1.3.
        val extent = size.minDimension * MAP_ZOOM
        val inset = (size.minDimension - extent) / 2f

        val frame = MapFrame.forRoute(
            lats = arc.lats,
            lons = arc.lons,
            aspect = 1.0,
            // The airport codes sit across the top of the face, so the route is
            // framed below them rather than behind them.
            topInsetFraction = TOP_INSET_FRACTION,
        )
        val land = frame.projectOutline(outline, margin = OUTLINE_MARGIN)

        translate(left = inset, top = inset) {
            // Even-odd, so a ring enclosed by another — the Caspian, the Great
            // Lakes — is a hole rather than more land.
            drawPath(
                path = land.fill.toPath(extent, close = true).apply { fillType = PathFillType.EvenOdd },
                color = LandFill,
            )
            drawPath(
                path = land.coast.toPath(extent, close = false),
                color = Coast,
                style = Stroke(width = COAST_STROKE_DP.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
            drawRoute(frame.project(arc.lats, arc.lons), extent)
        }
    }
}

/** The great circle and its two ends, cased so they read over any coastline. */
private fun DrawScope.drawRoute(projected: FloatArray, extent: Float) {
    val path = Path().apply {
        moveTo(projected[0] * extent, projected[1] * extent)
        for (point in 1 until projected.size / 2) {
            lineTo(projected[point * 2] * extent, projected[point * 2 + 1] * extent)
        }
    }
    val width = ROUTE_STROKE_DP.dp.toPx()
    val casing = CASING_DP.dp.toPx()

    drawPath(path, Casing, style = Stroke(width + 2f * casing, cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawPath(path, Route, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))

    val departure = Offset(projected[0] * extent, projected[1] * extent)
    val destination = Offset(projected[projected.size - 2] * extent, projected[projected.size - 1] * extent)
    val radius = ENDPOINT_RADIUS_DP.dp.toPx()
    for (end in listOf(departure, destination)) {
        drawCircle(Casing, radius = radius + casing, center = end)
        drawCircle(Route, radius = radius, center = end)
    }
}

/**
 * A `Path` over rings held as interleaved `x, y` fractions of the canvas.
 *
 * The same walk `:core:designsystem` does over the same structure: one `Path`
 * for all the rings, nothing allocated per point.
 */
private fun ProjectedRings.toPath(extent: Float, close: Boolean): Path {
    val path = Path()
    for (ring in 0 until ringCount) {
        val from = ringStart[ring]
        val to = ringStart[ring + 1]
        if (to - from < 2) continue
        path.moveTo(points[from * 2] * extent, points[from * 2 + 1] * extent)
        for (point in from + 1 until to) {
            path.lineTo(points[point * 2] * extent, points[point * 2 + 1] * extent)
        }
        if (close) path.close()
    }
    return path
}

/** Land, barely lifted off black: a ground for the route, not a chart. */
private val LandFill = Color(0xFF16202B)
private val Coast = Color(0xFF32485E)

/** `WearBrandColorScheme.primary`, named here because a `DrawScope` has no theme. */
private val Route = Color(0xFFADC6FF)

/** The face's own background, so a line crossing a coast stays one line. */
private val Casing = Color(0xFF000000)

private const val MAP_ZOOM = 1.3f
private const val TOP_INSET_FRACTION = 0.22
private const val OUTLINE_MARGIN = 0.08
private const val COAST_STROKE_DP = 1.0f
private const val ROUTE_STROKE_DP = 2.5f
private const val CASING_DP = 1.5f
private const val ENDPOINT_RADIUS_DP = 3.0f
