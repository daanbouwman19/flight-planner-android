package com.github.daanbouwman.flightplanner.mapscene

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.github.daanbouwman.flightplanner.aircraftart.AircraftShape
import com.github.daanbouwman.flightplanner.routing.GeoArc
import com.github.daanbouwman.flightplanner.routing.MapFrame
import com.github.daanbouwman.flightplanner.routing.WorldOutline

/**
 * Everything a route map draws, built once for one canvas size.
 *
 * Three surfaces draw this one picture, and none of them can share a
 * composition: the phone's `RouteMap` builds it inside `drawWithCache`, the
 * home-screen widget renders it into bitmaps for Glance, and the watch face
 * builds it on a background dispatcher a few pages ahead of the swipe that shows
 * it. All three go through [routeMapScene] and [drawRouteMap], so they are one
 * map to the pixel — one set of widths, one arrowhead, one rule for a short hop,
 * one rule for when the aircraft fits — differing only in what [RouteMapStyle]
 * says a surface's shape requires.
 *
 * Positions are in pixels of the canvas the scene was built for; build another
 * for another size. It holds no colour, so a theme change redraws a scene and
 * never rebuilds one, and nothing in it is mutated after it is built, so it can
 * be handed from the thread that built it to the one that draws it.
 */
class RouteMapScene internal constructor(
    internal val landPath: Path,
    internal val coastPath: Path,
    internal val graticulePath: Path?,
    internal val routePath: Path,
    internal val arrow: Path?,
    /** The aircraft's outline (shared, in centimetres); drawn at [plane] when both are set. */
    internal val planePath: Path?,
    /**
     * Where the aircraft sits, or null when there is none or the leg has no room
     * for it — see [MinPlaneChords]. When set, [arrow] is null.
     */
    val plane: PlanePlacement?,
    internal val departure: Offset,
    internal val destination: Offset,
    internal val hop: Offset,
    /**
     * True when the two ends are drawn as one ring: a [RouteMapEndpoints.Chart]
     * leg whose ends land closer than [MinArrowChordDp]. Always false for
     * [RouteMapEndpoints.Dots], which never draws the ring.
     */
    internal val shortHop: Boolean,
    internal val endpoints: RouteMapEndpoints,
    internal val coastWidth: Float,
    internal val routeWidth: Float,
    internal val casingWidth: Float,
    internal val casing: Float,
    internal val endpointRadius: Float,
    internal val endpointStroke: Float,
)

/**
 * Builds the scene for a canvas of [width] × [height] pixels, or null when
 * there is nothing to draw — fewer than two arc samples, or no canvas.
 *
 * Pure apart from the `Path`s it allocates, and safe on any thread. On a long
 * leg most of the time goes to [MapFrame.projectOutline] and to walking its
 * output into two paths — tens of thousands of points over the 1:50m outline —
 * which is why the watch calls this ahead of time rather than in a draw.
 *
 * @param topInsetPx how much of the top of the map belongs to text printed
 *   over it; the route is framed below it. See `RouteMap` in `:core:designsystem`.
 * @param aircraft drawn on the leg in place of the arrowhead when the leg has
 *   room for it — at least [MinPlaneChords] plane lengths between its ends.
 * @param style what this surface does differently; the default is the phone's
 *   route card.
 */
fun routeMapScene(
    arc: GeoArc,
    outline: WorldOutline,
    width: Float,
    height: Float,
    density: Density,
    topInsetPx: Float = 0f,
    aircraft: AircraftShape? = null,
    style: RouteMapStyle = RouteMapStyle.Card,
): RouteMapScene? = with(density) {
    if (arc.size < 2 || width <= 0f || height <= 0f) return null

    val frame = MapFrame.forRoute(
        lats = arc.lats,
        lons = arc.lons,
        aspect = (width / height).toDouble(),
        paddingFraction = style.paddingFraction,
        // Clamped so a map shorter than its inset — a card squeezed by a huge
        // font scale — degrades to a symmetric frame rather than to no frame.
        topInsetFraction = (topInsetPx / height).toDouble().coerceIn(0.0, MaxTopInsetFraction),
    )

    // A margin, so a coast just off the card still contributes the segment that
    // enters it, and a stroke's trimmed end falls outside the visible area.
    val land = frame.projectOutline(outline, margin = style.outlineMargin)

    // Two paths from the same clip, because they are drawn differently: the fill
    // is a polygon whose boundary runs along the window's edge, and stroking that
    // would draw a hairline box around the card. The coast is the real coastline,
    // trimmed and left open.
    val landPath = land.fill.toPath(width, height, close = true).apply {
        // Even-odd, so a ring enclosed by another — the Caspian, the Great
        // Lakes — is a hole rather than more land.
        fillType = PathFillType.EvenOdd
    }
    val coastPath = land.coast.toPath(width, height, close = false)

    // Nothing but ocean, or the middle of a continent: with no coast in the
    // window the card is a flat wash, which reads as a failed load rather than
    // as a place. A graticule says "this is the world, and you are looking at
    // a part of it with no coastline in it".
    val graticulePath = if (style.oceanGraticule && land.coast.isEmpty) {
        frame.graticule().toPath(width, height, close = false)
    } else {
        null
    }

    val projected = frame.project(arc.lats, arc.lons)
    val routePath = Path().apply {
        moveTo(projected[0] * width, projected[1] * height)
        for (i in 1 until projected.size / 2) {
            lineTo(projected[i * 2] * width, projected[i * 2 + 1] * height)
        }
    }

    // The arrowhead sits at the middle sample and points along the two samples
    // either side of it, so it follows the curve rather than the chord.
    val midpoint = projected.size / 4
    val chart = style.endpoints == RouteMapEndpoints.Chart
    // Too short for two markers and a head: the geometry, whatever the style
    // then does about it. Only the chart style collapses it to one ring.
    val tooShort = projectedChord(projected, width, height) < MinArrowChordDp.dp.toPx()
    val shortHop = chart && tooShort
    // The aircraft takes the arrowhead's place where the leg has room for it;
    // a nose says which way as plainly as a head does.
    val plane = if (aircraft == null || tooShort) {
        null
    } else {
        planePlacement(
            projected = projected,
            index = midpoint,
            width = width,
            height = height,
            spanCm = aircraft.spanCm,
            lengthCm = aircraft.lengthCm,
            lengthPx = PlaneLengthDp.dp.toPx(),
        )
    }
    val arrow = if (!chart || tooShort || plane != null) {
        null
    } else {
        arrowPath(projected, midpoint, width, height, ArrowLengthDp.dp.toPx())
    }

    val routeWidth = RouteStrokeDp.dp.toPx()
    val casing = CasingDp.dp.toPx()
    RouteMapScene(
        landPath = landPath,
        coastPath = coastPath,
        graticulePath = graticulePath,
        routePath = routePath,
        arrow = arrow,
        planePath = if (plane != null && aircraft != null) silhouettePath(aircraft) else null,
        plane = plane,
        departure = Offset(projected[0] * width, projected[1] * height),
        destination = Offset(projected[projected.size - 2] * width, projected[projected.size - 1] * height),
        hop = Offset(projected[midpoint * 2] * width, projected[midpoint * 2 + 1] * height),
        shortHop = shortHop,
        endpoints = style.endpoints,
        coastWidth = CoastStrokeDp.dp.toPx(),
        routeWidth = routeWidth,
        casingWidth = routeWidth + 2f * casing,
        casing = casing,
        endpointRadius = when (val ends = style.endpoints) {
            RouteMapEndpoints.Chart -> EndpointRadiusDp.dp.toPx()
            is RouteMapEndpoints.Dots -> ends.radiusDp.dp.toPx()
        },
        endpointStroke = EndpointStrokeDp.dp.toPx(),
    )
}

/**
 * The three inks a route map is made of.
 *
 * [drawRouteMap] draws all three interleaved — every line cased, then inked. A
 * consumer that has to colour them *after* drawing, as the widget does with
 * Glance's tint, asks for one at a time and stacks the results in this order.
 * Drawn one layer at a time the casing under the arrowhead sits under the
 * route line rather than cutting it, which is the one visible difference.
 */
enum class RouteMapLayer {
    /** Land fill, coast stroke and the ocean graticule. */
    Land,

    /** The wider stroke under every line and marker, in the surface's colour. */
    Casing,

    /** The route, its arrowhead or aircraft, and its endpoint markers. */
    Route,
}

/**
 * Draws [scene] into this scope. With every layer selected this is exactly
 * what the route card paints; with one, only that layer's primitives.
 *
 * The caller clips, if anything needs clipping: the scene is projected with a
 * margin and routinely paints past the canvas, and where that clip has to live
 * differs between a composable (a `clipRect` in the draw call, to avoid a
 * layer), a bitmap (the bitmap's own edge) and a round watch face (the page's
 * own circular clip).
 */
fun DrawScope.drawRouteMap(
    scene: RouteMapScene,
    landColor: Color,
    coastColor: Color,
    routeColor: Color,
    casingColor: Color,
    layers: Set<RouteMapLayer> = RouteMapLayer.entries.toSet(),
) {
    val land = RouteMapLayer.Land in layers
    val casing = RouteMapLayer.Casing in layers
    val route = RouteMapLayer.Route in layers

    if (land) {
        drawPath(scene.landPath, landColor)
        scene.graticulePath?.let {
            drawPath(path = it, color = landColor, style = Stroke(width = scene.coastWidth, cap = StrokeCap.Round))
        }
        // Bevel joins and butt caps, where the route below keeps round ones.
        // The coast is a few thousand segments redrawn every frame, and a round
        // join is an arc constructed at every vertex; at 1 dp and 16 % that
        // arc is sub-pixel and invisible, so it is pure cost.
        drawPath(
            path = scene.coastPath,
            color = coastColor,
            style = Stroke(width = scene.coastWidth, join = StrokeJoin.Bevel, cap = StrokeCap.Butt),
        )
    }

    // Casing first, then the line: round joins and caps are what make the pair
    // read as one ribbon instead of as a stack of segments with mitred corners.
    if (casing) {
        drawPath(
            path = scene.routePath,
            color = casingColor,
            style = Stroke(width = scene.casingWidth, join = StrokeJoin.Round, cap = StrokeCap.Round),
        )
    }
    if (route) {
        drawPath(
            path = scene.routePath,
            color = routeColor,
            style = Stroke(width = scene.routeWidth, join = StrokeJoin.Round, cap = StrokeCap.Round),
        )
    }

    // The arrowhead, cased like everything else. It is what makes direction
    // readable at a glance: the codes are pinned to the card's edges, so on a
    // westbound leg the departure code sits on the left while its marker sits
    // on the right, and a ring against a dot is too fine a distinction alone.
    scene.arrow?.let { head ->
        if (casing) {
            drawPath(
                path = head,
                color = casingColor,
                style = Stroke(width = 2f * scene.casing, join = StrokeJoin.Round, cap = StrokeCap.Round),
            )
        }
        if (route) drawPath(path = head, color = routeColor)
    }

    // The aircraft, where the arrowhead would be and cased the same way. The
    // canvas is moved, turned and scaled to it rather than the path transformed,
    // because the path is the one parsed outline every map and list shares; the
    // casing's width is divided by the scale for the same reason, so it comes
    // out at the line's own casing on screen.
    val planePath = scene.planePath
    val plane = scene.plane
    if (planePath != null && plane != null) {
        withTransform({
            translate(plane.x, plane.y)
            rotate(plane.degrees, pivot = Offset.Zero)
            scale(plane.scale, plane.scale, pivot = Offset.Zero)
            translate(-plane.pivotX, -plane.pivotY)
        }) {
            if (casing) {
                drawPath(
                    path = planePath,
                    color = casingColor,
                    style = Stroke(width = 2f * scene.casing / plane.scale, join = StrokeJoin.Round),
                )
            }
            if (route) drawPath(path = planePath, color = routeColor)
        }
    }

    if (scene.endpoints is RouteMapEndpoints.Dots) {
        // Two identical cased dots, each finished before the next is started —
        // casing, ink, then the other end. Where the two overlap, the
        // destination's casing cuts into the departure's dot, which is how the
        // watch face has always drawn them.
        drawCasedDot(scene, scene.departure, casingColor, routeColor, casing, route)
        drawCasedDot(scene, scene.destination, casingColor, routeColor, casing, route)
    } else if (scene.shortHop) {
        // One ring for both ends. The cased line under it is the hop; the ring
        // says "here" for a leg too short for "from here to there" to have room.
        if (casing) {
            drawCircle(
                color = casingColor,
                radius = scene.endpointRadius + scene.casing,
                center = scene.hop,
                style = Stroke(width = scene.endpointStroke + 2f * scene.casing),
            )
        }
        if (route) {
            drawCircle(
                color = routeColor,
                radius = scene.endpointRadius,
                center = scene.hop,
                style = Stroke(width = scene.endpointStroke),
            )
        }
    } else {
        // Departure hollow, destination filled: the chart convention for "from
        // here to there", which the arrowhead now states outright.
        if (casing) {
            drawCircle(
                color = casingColor,
                radius = scene.endpointRadius + scene.casing,
                center = scene.departure,
                style = Stroke(width = scene.endpointStroke + 2f * scene.casing),
            )
            drawCircle(color = casingColor, radius = scene.endpointRadius + scene.casing, center = scene.destination)
        }
        if (route) {
            drawCircle(
                color = routeColor,
                radius = scene.endpointRadius,
                center = scene.departure,
                style = Stroke(width = scene.endpointStroke),
            )
            drawCircle(color = routeColor, radius = scene.endpointRadius, center = scene.destination)
        }
    }
}

/** One [RouteMapEndpoints.Dots] marker: a filled disc of casing, then the dot on it. */
private fun DrawScope.drawCasedDot(
    scene: RouteMapScene,
    centre: Offset,
    casingColor: Color,
    routeColor: Color,
    casing: Boolean,
    route: Boolean,
) {
    if (casing) drawCircle(color = casingColor, radius = scene.endpointRadius + scene.casing, center = centre)
    if (route) drawCircle(color = routeColor, radius = scene.endpointRadius, center = centre)
}

/**
 * The most of a map's height the route will give up to text over it.
 *
 * A route card at font scale 2.0 is a tall card, not a small map, so this is
 * rarely reached — it exists so a degenerate size degrades to a symmetric
 * frame rather than to a frame with no room left for the route.
 */
private const val MaxTopInsetFraction = 0.5
