package com.github.daanbouwman.flightplanner.core.designsystem.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.github.daanbouwman.flightplanner.routing.GeoArc
import com.github.daanbouwman.flightplanner.routing.MapFrame
import com.github.daanbouwman.flightplanner.routing.WorldOutline

/**
 * Everything [RouteMap] draws, built once for one canvas size.
 *
 * The composable used to build this inside `drawWithCache` and draw it in the
 * same lambda. It is a class now because a second consumer needs the identical
 * picture with no composition to draw it in: the home-screen widget, which
 * Glance renders from bitmaps. Both go through [drawRouteMap], so the card on
 * the Plan screen and the card on the home screen are the same map to the
 * pixel — one set of widths, one arrowhead, one rule for a short hop.
 *
 * Positions are in pixels of the canvas the scene was built for; build another
 * for another size.
 */
class RouteMapScene internal constructor(
    internal val landPath: Path,
    internal val coastPath: Path,
    internal val graticulePath: Path?,
    internal val routePath: Path,
    internal val arrow: Path?,
    internal val departure: Offset,
    internal val destination: Offset,
    internal val hop: Offset,
    /** True when the two ends are drawn as one ring; see [RouteMap]'s KDoc. */
    val shortHop: Boolean,
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
 * @param topInsetPx how much of the top of the map belongs to text printed
 *   over it; the route is framed below it. See [RouteMap].
 */
fun routeMapScene(
    arc: GeoArc,
    outline: WorldOutline,
    width: Float,
    height: Float,
    density: Density,
    topInsetPx: Float = 0f,
): RouteMapScene? = with(density) {
    if (arc.size < 2 || width <= 0f || height <= 0f) return null

    val frame = MapFrame.forRoute(
        lats = arc.lats,
        lons = arc.lons,
        aspect = (width / height).toDouble(),
        // Clamped so a map shorter than its inset — a card squeezed by a huge
        // font scale — degrades to a symmetric frame rather than to no frame.
        topInsetFraction = (topInsetPx / height).toDouble().coerceIn(0.0, MaxTopInsetFraction),
    )

    // A margin, so a coast just off the card still contributes the segment that
    // enters it, and a stroke's trimmed end falls outside the visible area.
    val land = frame.projectOutline(outline, margin = OutlineMargin)

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
    val graticulePath = if (land.coast.isEmpty) frame.graticule().toPath(width, height, close = false) else null

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
    val shortHop = projectedChord(projected, width, height) < MinArrowChordDp.dp.toPx()
    val arrow = if (shortHop) null else arrowPath(projected, midpoint, width, height, ArrowLengthDp.dp.toPx())

    val routeWidth = RouteStrokeDp.dp.toPx()
    val casing = CasingDp.dp.toPx()
    RouteMapScene(
        landPath = landPath,
        coastPath = coastPath,
        graticulePath = graticulePath,
        routePath = routePath,
        arrow = arrow,
        departure = Offset(projected[0] * width, projected[1] * height),
        destination = Offset(projected[projected.size - 2] * width, projected[projected.size - 1] * height),
        hop = Offset(projected[midpoint * 2] * width, projected[midpoint * 2 + 1] * height),
        shortHop = shortHop,
        coastWidth = CoastStrokeDp.dp.toPx(),
        routeWidth = routeWidth,
        casingWidth = routeWidth + 2f * casing,
        casing = casing,
        endpointRadius = EndpointRadiusDp.dp.toPx(),
        endpointStroke = EndpointStrokeDp.dp.toPx(),
    )
}

/**
 * The three inks a route map is made of.
 *
 * [RouteMap] draws all three interleaved — every line cased, then inked. A
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

    /** The route, its arrowhead and its endpoint markers. */
    Route,
}

/**
 * Draws [scene] into this scope. With every layer selected this is exactly
 * what the route card paints; with one, only that layer's primitives.
 *
 * The caller clips: the scene is projected with a margin and routinely paints
 * past the canvas, and where that clip has to live differs between a
 * composable (a `clipRect` in the draw call, to avoid a layer) and a bitmap
 * (the bitmap's own edge).
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

    if (scene.shortHop) {
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

/**
 * A route map as three alpha masks, one per [RouteMapLayer].
 *
 * Every pixel is white; only the alpha carries information — the land at
 * [WorldMapLandAlpha], the coast at [WorldMapCoastAlpha], the casing and the
 * route opaque. Whoever displays them supplies the colour, which is the whole
 * point: Glance tints a bitmap with a colour *provider* that resolves to the
 * light or dark palette at display time, so a widget drawn this way follows a
 * night-mode switch and a wallpaper change that a pre-coloured bitmap would
 * miss until its next update.
 */
class RouteMapLayers(
    val land: ImageBitmap,
    val casing: ImageBitmap,
    val route: ImageBitmap,
)

/**
 * Renders [RouteMapLayers] for a canvas of [widthPx] × [heightPx], or null
 * when the scene is empty. Off-screen, on whatever thread calls it; the cost is
 * three fills of a small bitmap and belongs off the main thread.
 */
fun renderRouteMapLayers(
    arc: GeoArc,
    outline: WorldOutline,
    widthPx: Int,
    heightPx: Int,
    density: Density,
    topInsetPx: Float = 0f,
): RouteMapLayers? {
    if (widthPx <= 0 || heightPx <= 0) return null
    val scene = routeMapScene(arc, outline, widthPx.toFloat(), heightPx.toFloat(), density, topInsetPx) ?: return null
    fun mask(layer: RouteMapLayer): ImageBitmap {
        val bitmap = ImageBitmap(widthPx, heightPx)
        CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(bitmap), Size(widthPx.toFloat(), heightPx.toFloat())) {
            drawRouteMap(
                scene = scene,
                landColor = Color.White.copy(alpha = WorldMapLandAlpha),
                coastColor = Color.White.copy(alpha = WorldMapCoastAlpha),
                routeColor = Color.White,
                casingColor = Color.White,
                layers = setOf(layer),
            )
        }
        return bitmap
    }
    return RouteMapLayers(
        land = mask(RouteMapLayer.Land),
        casing = mask(RouteMapLayer.Casing),
        route = mask(RouteMapLayer.Route),
    )
}

/**
 * The most of a map's height the route will give up to text over it.
 *
 * A route card at font scale 2.0 is a tall card, not a small map, so this is
 * rarely reached — it exists so a degenerate size degrades to a symmetric
 * frame rather than to a frame with no room left for the route.
 */
private const val MaxTopInsetFraction = 0.5
