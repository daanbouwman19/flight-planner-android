package com.github.daanbouwman.flightplanner.mapscene

import androidx.compose.ui.graphics.Path
import com.github.daanbouwman.flightplanner.routing.ProjectedRings
import com.github.daanbouwman.flightplanner.routing.WorldOutline
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.sqrt

// The ink every route map draws with: the phone's route card, its detail hero,
// the Network card, the home-screen widget and the watch face. `:core:designsystem`'s
// `NetworkMap` is the same map with more legs on it, and it used to re-literalise
// these numbers in `:app` — where they drifted: a 1.5 dp leg under a 2.5 dp route,
// a casing half as wide, a margin twice as wide. The watch then wrote them a third
// time. One set of figures, one place to retune them.
//
// Public where `:core:designsystem` draws from them across the module boundary
// (NetworkMap and the node scale); internal where only the scene builder does.

/** The coastline, and the ocean graticule where there is one. */
const val CoastStrokeDp = 1f

/** The route, and every leg of the network. */
const val RouteStrokeDp = 2.5f

/** Half-width of the casing under each line, per side. */
const val CasingDp = 1.5f

/** Half-length of the direction arrowhead. */
const val ArrowLengthDp = 5f

/** The arrowhead's half-width as a fraction of its length: a narrow, chart-like head. */
internal const val ArrowHalfWidth = 0.62f

/** The chart convention's endpoint marker; see [RouteMapEndpoints.Chart]. */
const val EndpointRadiusDp = 4f
internal const val EndpointStrokeDp = 2f

/** Extra window projected around the card, as a fraction of each span. */
const val OutlineMargin = 0.05

/**
 * Below this projected chord between a leg's two ends, the ends are one place.
 *
 * At 24 dp the arrowhead, its casing and the two endpoint markers overlap into
 * a mark that cannot be read, and a mark that cannot be read is worse than a
 * leg with no stated direction. The route card draws such a hop as a single
 * ring; `NetworkMap` keeps its per-node dots and drops the head. One threshold,
 * so the plan card and the network card agree on what "short" is.
 */
const val MinArrowChordDp = 24f

/**
 * The aircraft on a route map, measured along the longer of its span and its
 * length, so a wide glider and a long airliner take the same room.
 *
 * Chosen by eye on the design canvas, on the phone and on the watch alike, over
 * the 20 dp first proposed.
 */
internal const val PlaneLengthDp = 24f

/**
 * How many [PlaneLengthDp] of projected chord a leg needs before its aircraft
 * replaces the arrowhead: room for the plane and for both end markers clear of
 * its wings — 24 dp, plus 4 dp of marker, 1.5 dp of casing and 2 dp of air at
 * each end, is 39 dp, or 1.6 plane lengths. Shorter, and the ends sit under the
 * wings, so the arrowhead stays.
 *
 * It was 3.6 first, to keep the leg mostly visible either side of the plane,
 * and that hid the plane on any leg under about 250 NM — which on a device read
 * as "some aircraft have no icon". The plane is what the user is looking for;
 * the line either side of it only has to reach the markers.
 */
internal const val MinPlaneChords = 1.6f

/**
 * Walks projected rings into a `Path`, scaling the fractions to pixels.
 *
 * Public so anything drawing a [WorldOutline] — the scene builder and
 * `NetworkMap` alike — shares this conversion rather than reimplementing it.
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
 * Public: `NetworkMap` puts the same head on every leg of the visited network.
 *
 * @return null when the arc is too short to have a direction at all.
 */
fun arrowPath(
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
 * Where an aircraft sits on a projected leg, in the pixels of the canvas the
 * leg was projected for.
 *
 * The outline's box is centred on the arc at [index] and turned so the nose
 * points along the track there — taken from the samples either side, as
 * [arrowPath] takes it, so the plane follows the curve, not the chord. It is
 * scaled so the longer of [spanCm] and [lengthCm] is [lengthPx].
 *
 * Numbers rather than a Path: the outline itself is the parsed, shared one
 * (see [silhouettePath]), placed by transforming the canvas, and the decision
 * is then testable without a renderer.
 *
 * @return null when the leg is shorter than [MinPlaneChords] plane lengths end
 *   to end, or has no heading at [index].
 */
internal fun planePlacement(
    projected: FloatArray,
    index: Int,
    width: Float,
    height: Float,
    spanCm: Int,
    lengthCm: Int,
    lengthPx: Float,
): PlanePlacement? {
    val count = projected.size / 2
    if (count < 3 || index <= 0 || index >= count - 1) return null
    if (spanCm <= 0 || lengthCm <= 0 || lengthPx <= 0f) return null
    if (projectedChord(projected, width, height) < MinPlaneChords * lengthPx) return null

    val dx = (projected[(index + 1) * 2] - projected[(index - 1) * 2]) * width
    val dy = (projected[(index + 1) * 2 + 1] - projected[(index - 1) * 2 + 1]) * height
    if (sqrt(dx * dx + dy * dy) < 1e-3f) return null

    return PlanePlacement(
        x = projected[index * 2] * width,
        y = projected[index * 2 + 1] * height,
        // Clockwise from up, because the outline is drawn nose up and y grows
        // downwards: a due-east leg is 90, a due-west one -90.
        degrees = Math.toDegrees(atan2(dx.toDouble(), -dy.toDouble())).toFloat(),
        scale = lengthPx / maxOf(spanCm, lengthCm),
        pivotX = spanCm / 2f,
        pivotY = lengthCm / 2f,
    )
}

/**
 * An aircraft's place on a route map: its box centre at ([x], [y]) in canvas
 * pixels, turned [degrees] clockwise from nose-up, at [scale] pixels per
 * centimetre of the real aircraft. ([pivotX], [pivotY]) is the box centre in
 * the outline's own centimetres.
 *
 * Public because [RouteMapScene.plane] is: a surface that draws through the
 * scene can say — and its own tests can pin — whether its face carries the
 * aircraft, at its own size and density.
 */
data class PlanePlacement(
    val x: Float,
    val y: Float,
    val degrees: Float,
    val scale: Float,
    val pivotX: Float,
    val pivotY: Float,
)

/**
 * The straight-line distance in pixels between a projected leg's two ends.
 *
 * The chord, not the arc's length: what decides whether two markers and a
 * head collide is how far apart the *ends* land on the canvas, and a bowed
 * arc between two close ends is still two close ends. Compared against
 * [MinArrowChordDp] by both maps.
 */
fun projectedChord(projected: FloatArray, width: Float, height: Float): Float {
    if (projected.size < 4) return 0f
    return hypot(
        (projected[projected.size - 2] - projected[0]) * width,
        (projected[projected.size - 1] - projected[1]) * height,
    )
}
