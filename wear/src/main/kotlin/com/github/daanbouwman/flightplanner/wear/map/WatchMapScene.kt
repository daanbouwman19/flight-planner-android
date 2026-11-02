package com.github.daanbouwman.flightplanner.wear.map

import androidx.compose.ui.unit.Density
import com.github.daanbouwman.flightplanner.mapscene.RouteMapEndpoints
import com.github.daanbouwman.flightplanner.mapscene.RouteMapScene
import com.github.daanbouwman.flightplanner.mapscene.RouteMapStyle
import com.github.daanbouwman.flightplanner.mapscene.routeMapScene
import com.github.daanbouwman.flightplanner.routing.MapFrame
import com.github.daanbouwman.flightplanner.routing.WorldOutline
import com.github.daanbouwman.flightplanner.wear.route.WatchRouteCard

/**
 * The face a scene is built for: the side of the square the map fills, in
 * pixels, and the density its dp resolve at.
 *
 * Everything a scene depends on besides the route and the outline. The face
 * reports it once, at measure time (see `RouteFeedScreen`), so the scenes can
 * be built before any page is drawn; the draw then works it out again from its
 * own size, and because both come from one layout at one density they are
 * equal — which is what makes a prefetched scene the one the draw asks for.
 */
internal data class WatchFace(val extentPx: Int, val density: Float)

/**
 * Builds [route]'s map for [face]: the one function both the prefetch and the
 * draw call, so a face built ahead of time and one built on a cache miss are
 * the same picture.
 *
 * The phone's [routeMapScene], with [WatchMapStyle] and the face's square.
 * Safe on any thread; see `WatchSceneCache` for which thread it normally runs on.
 */
internal fun watchRouteScene(route: WatchRouteCard, outline: WorldOutline, face: WatchFace): RouteMapScene? {
    val extent = face.extentPx.toFloat()
    return routeMapScene(
        arc = route.arc,
        outline = outline,
        width = extent,
        height = extent,
        density = Density(face.density),
        topInsetPx = TOP_INSET_FRACTION * extent,
        aircraft = route.shape,
        style = WatchMapStyle,
    )
}

/**
 * Where the face gets its scene from.
 *
 * An interface rather than the cache itself so `RouteFeedScreen` stays a
 * function of its arguments: the app passes the ViewModel's cache, and a
 * preview or a test passes [Direct], which builds on the spot.
 */
internal fun interface WatchSceneSource {
    fun scene(route: WatchRouteCard, outline: WorldOutline, face: WatchFace): RouteMapScene?

    companion object {
        /** No cache: every draw builds its scene. Identical output, paid for on the UI thread. */
        val Direct: WatchSceneSource = WatchSceneSource(::watchRouteScene)
    }
}

/**
 * What the watch face does differently from the phone's route card — and only
 * that. Everything else, the widths, the casing, the plane and its room rule,
 * the draw order, is the phone's, from `:core:mapscene`.
 *
 * - **The frame fits a circle**, so it is padded by [ROUTE_PADDING_FRACTION]
 *   rather than the card's 12 %.
 * - **The window barely overshoots the face**: [OUTLINE_MARGIN].
 * - **Both ends are filled dots, and there is no arrowhead or short-hop ring.**
 *   The direction is stated by the two codes and the chevron printed across
 *   the top of the face, which a card does not have room for; a head on the
 *   leg would say it a second time.
 * - **No ocean graticule.** The map is the whole face and the face's ground;
 *   a grid across a mid-Atlantic leg would be the busiest thing on it.
 */
internal val WatchMapStyle: RouteMapStyle = RouteMapStyle(
    paddingFraction = ROUTE_PADDING_FRACTION,
    outlineMargin = OUTLINE_MARGIN,
    endpoints = RouteMapEndpoints.Dots(radiusDp = ENDPOINT_RADIUS_DP),
    oceanGraticule = false,
)

/**
 * What it takes to fit the route inside a circle.
 *
 * [MapFrame] fits a route to a rectangle, and this face is a circle. The
 * default 0.12 leaves the route spanning 9.7 % to 90.3 % of the frame, which is
 * inside the square and outside the *inscribed circle* on any diagonal route —
 * so the destination dot was drawn off the glass and simply could not be seen.
 * Solving for the worst corner, the bottom one, lands at 0.284, verified across
 * six route geometries including a 2,944 NM diagonal.
 */
private const val ROUTE_PADDING_FRACTION = 0.284

/**
 * The airport codes sit across the top of the face, so the route is framed
 * below them rather than behind them. A fraction of the face, passed to the
 * scene as pixels.
 */
private const val TOP_INSET_FRACTION = 0.22f

/**
 * How far past the face the coastline is projected: 1 % of each span, about
 * 5 px on the 480 px face.
 *
 * It was 8 %, and that figure was a leftover. The map used to be zoomed 1.3×
 * past the face so the outline's clip edge fell off-screen rather than showing
 * as a straight seam; the zoom also pushed the route's endpoints off the glass,
 * so it went back to 1.0, and the 8 % margin stayed on to keep the seam hidden.
 * At 1.0 the visible face is the circle inscribed in the square canvas — the
 * page clips to a circle and the glass is round — which touches the square only
 * at the middle of each edge. Nothing beyond the square can be seen; the margin
 * only has to carry a clipped coastline far enough past the edge that its cut
 * end, and the fill's straight edge along the clip, never land where the circle
 * touches the square. One percent, about 5 px against a 1 dp stroke, does that
 * with room to spare. The other 7 % was being projected, filled and stroked on
 * every page for nothing: on the 1:50m outline, cutting it removed 22–36 % of
 * the fill and coast points a long-haul face emits — EGLL–KJFK 22 %, RJAA–KLAX
 * 28 %, PHNL–NZAA 36 %, from the counts `ProjectOutlineRegressionTest` pins at
 * both margins — with no visible change.
 */
private const val OUTLINE_MARGIN = 0.01

/** Both endpoint dots, a dp under the phone's chart marker, as the face has always drawn them. */
private const val ENDPOINT_RADIUS_DP = 3.0f
