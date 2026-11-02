package com.github.daanbouwman.flightplanner.core.designsystem.components

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.github.daanbouwman.flightplanner.aircraftart.AircraftShape
import com.github.daanbouwman.flightplanner.mapscene.RouteMapLayer
import com.github.daanbouwman.flightplanner.mapscene.drawRouteMap
import com.github.daanbouwman.flightplanner.mapscene.routeMapScene
import com.github.daanbouwman.flightplanner.routing.GeoArc
import com.github.daanbouwman.flightplanner.routing.WorldOutline

/**
 * A route map as three alpha masks, one per `RouteMapLayer`.
 *
 * Every pixel is white; only the alpha carries information — the land at
 * [WorldMapLandAlpha], the coast at [WorldMapCoastAlpha], the casing and the
 * route opaque. Whoever displays them supplies the colour, which is the whole
 * point: Glance tints a bitmap with a colour *provider* that resolves to the
 * light or dark palette at display time, so a widget drawn this way follows a
 * night-mode switch and a wallpaper change that a pre-coloured bitmap would
 * miss until its next update.
 *
 * Here rather than beside the scene in `:core:mapscene` because the two alphas
 * are this design system's figures for how quiet a map is, not geometry.
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
    aircraft: AircraftShape? = null,
): RouteMapLayers? {
    if (widthPx <= 0 || heightPx <= 0) return null
    val scene = routeMapScene(arc, outline, widthPx.toFloat(), heightPx.toFloat(), density, topInsetPx, aircraft) ?: return null
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
