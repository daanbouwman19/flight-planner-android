package com.github.daanbouwman.flightplanner.mapscene

import com.github.daanbouwman.flightplanner.routing.MapFrame

/**
 * What one surface's route map does differently from another's, and nothing else.
 *
 * The phone's route card and the watch face draw one map: the same projection,
 * the same inks (see RouteMapGeometry.kt), the same draw order, the same rule for
 * when the aircraft fits. What genuinely differs is how the frame meets the
 * surface's *shape* and how the two ends are marked, and those are the only
 * things this carries. Anything that is a matter of taste rather than of shape
 * — a stroke width, the casing, the plane's size — stays a shared constant, so
 * retuning it moves both devices or neither.
 *
 * The default is the phone's card exactly; [Card] names it.
 */
class RouteMapStyle(
    /**
     * How much of each span is kept clear around the route; see
     * [MapFrame.forRoute]. The phone's 12 % fits a rectangle; a round face needs
     * far more, because a rectangle's corners are off the glass.
     */
    val paddingFraction: Double = MapFrame.PADDING_FRACTION,
    /**
     * Extra window projected around the canvas, as a fraction of each span, so a
     * coast just off the edge still contributes the segment that enters it and a
     * trimmed stroke ends outside the visible area. Every point inside it is
     * projected, filled and stroked, so it is paid for in vertices.
     */
    val outlineMargin: Double = OutlineMargin,
    /** How the two ends are marked, and whether a direction mark goes with them. */
    val endpoints: RouteMapEndpoints = RouteMapEndpoints.Chart,
    /**
     * Whether a window with no coast in it draws a graticule. On a card, a flat
     * wash reads as a failed load and the grid says "this is the world"; where
     * the ground is the whole screen, the grid is clutter.
     */
    val oceanGraticule: Boolean = true,
) {
    companion object {
        /**
         * The phone's route card, and everything that draws the same map: the
         * detail hero, the Network card's ink and the home-screen widget.
         */
        val Card: RouteMapStyle = RouteMapStyle()
    }
}

/** How a route map marks the two ends of a leg. */
sealed interface RouteMapEndpoints {

    /**
     * The aeronautical-chart convention: a hollow ring at the departure, a filled
     * dot at the destination, each of radius [EndpointRadiusDp], the ring drawn
     * [EndpointStrokeDp] wide. Where there is no aircraft, an arrowhead at the
     * arc's midpoint states the direction outright; a leg whose ends land closer
     * than [MinArrowChordDp] is one ring at its midpoint instead, because two
     * markers and a head that close overlap into a blob.
     */
    data object Chart : RouteMapEndpoints

    /**
     * Both ends the same filled dot of radius [radiusDp], each cased with a filled disc
     * of the surface's colour, and never an arrowhead or a short-hop ring: the
     * surface states the direction some other way — the watch prints the two
     * codes with a chevron between them across the top of the face.
     */
    data class Dots(val radiusDp: Float) : RouteMapEndpoints
}
