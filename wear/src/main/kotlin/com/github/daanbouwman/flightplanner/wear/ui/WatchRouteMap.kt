package com.github.daanbouwman.flightplanner.wear.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import androidx.wear.compose.material3.MaterialTheme
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
 * Taken from the theme the phone published, not fixed here — see
 * [WatchMapPalette]. Whichever theme is on, every ink is quiet enough to carry
 * the face's text on top of it without a scrim. A scrim over a map is what the
 * phone's Plan screen was corrected away from (see CLAUDE.md on the system
 * bars), and on a round face it would show as a band across the middle of the
 * circle. Keeping the map a ground for the route rather than a chart to read
 * place names off is what makes that affordable.
 */
@Composable
internal fun WatchRouteMap(
    arc: GeoArc,
    outline: WorldOutline,
    palette: WatchMapPalette,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        if (arc.size < 2 || size.minDimension <= 0f) return@Canvas

        val extent = size.minDimension * MAP_ZOOM
        val inset = (size.minDimension - extent) / 2f

        val frame = MapFrame.forRoute(
            lats = arc.lats,
            lons = arc.lons,
            aspect = 1.0,
            // [MapFrame] fits a route to a rectangle, and this face is a circle.
            // The default 0.12 leaves the route spanning 9.7% to 90.3% of the
            // frame, which is inside the square and outside the *inscribed
            // circle* on any diagonal route — so the destination dot was drawn
            // off the glass and simply could not be seen. Solving for the worst
            // corner, the bottom one, lands at 0.284.
            paddingFraction = ROUTE_PADDING_FRACTION,
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
                color = palette.land,
            )
            drawPath(
                path = land.coast.toPath(extent, close = false),
                color = palette.coast,
                style = Stroke(width = COAST_STROKE_DP.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
            drawRoute(frame.project(arc.lats, arc.lons), extent, palette)
        }
    }
}

/** The great circle and its two ends, cased so they read over any coastline. */
private fun DrawScope.drawRoute(projected: FloatArray, extent: Float, palette: WatchMapPalette) {
    val path = Path().apply {
        moveTo(projected[0] * extent, projected[1] * extent)
        for (point in 1 until projected.size / 2) {
            lineTo(projected[point * 2] * extent, projected[point * 2 + 1] * extent)
        }
    }
    val width = ROUTE_STROKE_DP.dp.toPx()
    val casing = CASING_DP.dp.toPx()

    drawPath(path, palette.casing, style = Stroke(width + 2f * casing, cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawPath(path, palette.route, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))

    val departure = Offset(projected[0] * extent, projected[1] * extent)
    val destination = Offset(projected[projected.size - 2] * extent, projected[projected.size - 1] * extent)
    val radius = ENDPOINT_RADIUS_DP.dp.toPx()
    for (end in listOf(departure, destination)) {
        drawCircle(palette.casing, radius = radius + casing, center = end)
        drawCircle(palette.route, radius = radius, center = end)
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

/**
 * The four inks the map is drawn in, resolved once and handed down.
 *
 * A `DrawScope` has no theme, so these are read in composition and passed in
 * rather than looked up per frame. See [rememberWatchMapPalette] for where each
 * comes from.
 */
internal data class WatchMapPalette(
    val land: Color,
    val coast: Color,
    val route: Color,
    val casing: Color,
)

/**
 * The map's inks, as roles rather than values, so the map follows the theme the
 * phone published along with the rest of the face.
 *
 * - **Land** is `surfaceContainer`: the ground the plates on top of it also use,
 *   which is what makes a plate over land read as a plate rather than a hole.
 * - **The coastline** is `outline`, held well back. At full strength it competes
 *   with the route for the eye on a face this size; the route is the subject and
 *   the coast is there to say roughly where in the world it is.
 * - **The route** is `primary`, the one saturated colour any of these themes has.
 * - **The casing** is the face's own `background`, which is why a route crossing
 *   a coastline still reads as one line: the casing is a gap in the map, not a
 *   dark outline that would look wrong the moment the theme went light.
 */
@Composable
internal fun rememberWatchMapPalette(): WatchMapPalette {
    val scheme = MaterialTheme.colorScheme
    return remember(scheme) {
        WatchMapPalette(
            land = scheme.surfaceContainer,
            coast = scheme.outline.copy(alpha = COAST_ALPHA),
            route = scheme.primary,
            casing = scheme.background,
        )
    }
}

/** How far back the coastline sits. See [rememberWatchMapPalette]. */
private const val COAST_ALPHA = 0.45f

/**
 * No longer a zoom, and the endpoints are why.
 *
 * It was 1.3 so the world outline's clip edge fell outside the face rather than
 * showing as a straight seam across the map. But the route is projected through
 * the same frame, so scaling the frame past the face scaled the route past it
 * too: at 1.3 the route's own extremes mapped to -2.4% and 102.4% of the face,
 * which put one or both endpoint dots off the screen.
 *
 * [OUTLINE_MARGIN] already carries the outline 8% beyond the frame, so at 1.0
 * the clip edge is still off-screen and the seam stays hidden. The zoom was
 * buying nothing the margin was not already paying for.
 */
private const val MAP_ZOOM = 1.0f

/** See the note at [MapFrame.forRoute] above: what it takes to fit a circle. */
private const val ROUTE_PADDING_FRACTION = 0.284
private const val TOP_INSET_FRACTION = 0.22
private const val OUTLINE_MARGIN = 0.08
private const val COAST_STROKE_DP = 1.0f
private const val ROUTE_STROKE_DP = 2.5f
private const val CASING_DP = 1.5f
private const val ENDPOINT_RADIUS_DP = 3.0f
