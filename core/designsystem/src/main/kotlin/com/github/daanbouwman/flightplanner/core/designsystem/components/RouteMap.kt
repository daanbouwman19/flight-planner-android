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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.hypot
import kotlin.math.sqrt
import com.github.daanbouwman.flightplanner.core.designsystem.theme.FlightPlannerTheme
import com.github.daanbouwman.flightplanner.routing.GeoArc
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
 * ### The route stays out from under the text
 *
 * [topInset] is how much of the top of the map belongs to something printed
 * over it — the route card's title line. The route is framed below it (see
 * [com.github.daanbouwman.flightplanner.routing.MapFrame.forRoute]); land still runs through it, since the band is part of
 * the map, but an endpoint no longer sits on the title's baseline and an arc
 * no longer runs under the letters. Zero where nothing is printed over the map.
 *
 * ### A short hop is a ring, not a smudge
 *
 * Below [MinArrowChordDp] between the projected ends, the two markers and the
 * arrowhead would overlap into one blob that reads as a rendering fault. A hop
 * that short is drawn as its cased line — which is all the direction it has
 * room to state — under one hollow ring at its midpoint: the chart mark for "a
 * place", standing for both ends at once. The same threshold decides whether
 * a network leg carries an arrowhead, so the two maps agree on what "short" is.
 *
 * The map carries no information the card does not state in text, so it is
 * hidden from accessibility services outright.
 */
@Composable
fun RouteMap(
    arc: GeoArc,
    outline: WorldOutline,
    modifier: Modifier = Modifier,
    topInset: Dp = 0.dp,
    landColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = WorldMapLandAlpha),
    coastColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = WorldMapCoastAlpha),
    routeColor: Color = MaterialTheme.colorScheme.primary,
    casingColor: Color = MaterialTheme.colorScheme.surfaceContainer,
) {
    Box(
        modifier = modifier
            .clearAndSetSemantics { }
            .drawWithCache {
                // The geometry — frame, paths, markers, widths — is one object,
                // built here and drawn below; `routeMapScene` and `drawRouteMap`
                // are what the home-screen widget shares with this card, so the
                // two cannot drift. See RouteMapScene.kt.
                val scene = routeMapScene(
                    arc = arc,
                    outline = outline,
                    width = size.width,
                    height = size.height,
                    density = this,
                    topInsetPx = topInset.toPx(),
                ) ?: return@drawWithCache onDrawBehind { }

                onDrawBehind {
                    // **The map crops itself.** Everything in the scene is
                    // projected with a margin — see `OutlineMargin` — so a coast
                    // just off the window still contributes the segment that
                    // enters it, and a trimmed stroke ends outside the visible
                    // area rather than inside it. That means this component
                    // routinely paints beyond its own bounds and, until now,
                    // relied on whatever card or surface contained it to crop
                    // the overspill.
                    //
                    // That assumption held everywhere except the one place it
                    // mattered: a shared-element transition renders the map in an
                    // overlay, where **no ancestor clip applies at all**, so the
                    // margin and every off-window coastline painted across the
                    // whole screen while the map flew between the card and the
                    // detail's hero.
                    //
                    // `clipRect` rather than `Modifier.clipToBounds()`: the
                    // modifier is a `graphicsLayer`, which would add an offscreen
                    // layer per card to a list Phase P spent its time keeping
                    // smooth. This is a clip on the canvas that is already being
                    // drawn into, and it costs nothing.
                    clipRect {
                        drawRouteMap(
                            scene = scene,
                            landColor = landColor,
                            coastColor = coastColor,
                            routeColor = routeColor,
                            casingColor = casingColor,
                        )
                    }
                }
            },
    )
}

/**
 * Land, at 8 % of `onSurface`.
 *
 * Chosen against a mid-contrast map that reads unmistakably as a map — and needs
 * a scrim under every figure on the card to stay legible. Text keeps essentially
 * its full contrast against this. Shared with any other composable drawing a
 * world outline (see [ProjectedRings.toPath]) so every map in the app reads as
 * one system rather than several independently-tuned ones.
 */
const val WorldMapLandAlpha = 0.08f

/** The coast, at twice the fill, which is what makes a silhouette recognisable. */
const val WorldMapCoastAlpha = 0.16f

// The ink every map in this module draws with. Internal rather than private
// because [NetworkMap] is the same map with more legs on it, and it used to
// re-literalise these numbers in `:app` — where they drifted: a 1.5 dp leg under
// a 2.5 dp route, a casing half as wide, a margin twice as wide. One set of
// figures, one place to retune them.
internal const val CoastStrokeDp = 1f
internal const val RouteStrokeDp = 2.5f

/** Half-width of the casing under each line, per side. */
internal const val CasingDp = 1.5f

/** Half-length of the direction arrowhead. */
internal const val ArrowLengthDp = 5f

/** The arrowhead's half-width as a fraction of its length: a narrow, chart-like head. */
internal const val ArrowHalfWidth = 0.62f

internal const val EndpointRadiusDp = 4f
internal const val EndpointStrokeDp = 2f

/** Extra window projected around the card, as a fraction of each span. */
internal const val OutlineMargin = 0.05

/**
 * Below this projected chord between a leg's two ends, the ends are one place.
 *
 * At 24 dp the arrowhead, its casing and the two endpoint markers overlap into
 * a mark that cannot be read, and a mark that cannot be read is worse than a
 * leg with no stated direction. [RouteMap] draws such a hop as a single ring;
 * [NetworkMap] keeps its per-node dots and drops the head. One threshold, so
 * the plan card and the network card agree on what "short" is.
 */
internal const val MinArrowChordDp = 24f

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
 * Public so any composable drawing a [WorldOutline] — [RouteMap] included —
 * shares this conversion rather than reimplementing it.
 *
 * @param close true for the filled polygons, false for the coastline: an open
 *   polyline that gets closed grows a straight segment from its last point back
 *   to its first, straight across the card.
 */
fun ProjectedRings.toPath(width: Float, height: Float, close: Boolean): Path {
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
 * Internal: [NetworkMap] puts the same head on every leg of the visited network.
 *
 * @return null when the arc is too short to have a direction at all.
 */
internal fun arrowPath(
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

/**
 * The straight-line distance in pixels between a projected leg's two ends.
 *
 * The chord, not the arc's length: what decides whether two markers and a
 * head collide is how far apart the *ends* land on the canvas, and a bowed
 * arc between two close ends is still two close ends. Compared against
 * [MinArrowChordDp] by both maps.
 */
internal fun projectedChord(projected: FloatArray, width: Float, height: Float): Float {
    if (projected.size < 4) return 0f
    return hypot(
        (projected[projected.size - 2] - projected[0]) * width,
        (projected[projected.size - 1] - projected[1]) * height,
    )
}
