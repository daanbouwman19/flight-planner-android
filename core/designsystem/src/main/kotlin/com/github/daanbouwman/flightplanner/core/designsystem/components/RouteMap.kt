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
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.daanbouwman.flightplanner.aircraftart.AircraftShape
import com.github.daanbouwman.flightplanner.core.designsystem.theme.FlightPlannerTheme
import com.github.daanbouwman.flightplanner.mapscene.drawRouteMap
import com.github.daanbouwman.flightplanner.mapscene.routeMapScene
import com.github.daanbouwman.flightplanner.routing.GeoArc
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
 * Below `MinArrowChordDp` between the projected ends, the two markers and the
 * arrowhead would overlap into one blob that reads as a rendering fault. A hop
 * that short is drawn as its cased line — which is all the direction it has
 * room to state — under one hollow ring at its midpoint: the chart mark for "a
 * place", standing for both ends at once. The same threshold decides whether
 * a network leg carries an arrowhead, so the two maps agree on what "short" is.
 *
 * ### The aircraft flies the leg
 *
 * Given an [aircraft], the map draws its outline at the arc's midpoint, nose
 * along the track, `PlaneLengthDp` long and cased like the line — in place of
 * the arrowhead, which it makes redundant: a nose states direction as plainly
 * as a head does, and names the type besides. It needs room. Below
 * `MinPlaneChords` plane lengths between the projected ends the two markers
 * would sit under its wings, so the arrowhead stays; below `MinArrowChordDp`
 * the ring stays, exactly as without an aircraft.
 *
 * ### The drawing is shared with the watch
 *
 * The scene this draws — every path, width, marker and threshold named above —
 * is built and drawn by `:core:mapscene`, which is Compose graphics with no
 * Material in it, so the watch face can draw the same map without taking this
 * module's alpha-pinned `material3`. This composable is the phone's binding of
 * it: the colour roles, the `clipRect`, and `RouteMapStyle.Card`, the default.
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
    aircraft: AircraftShape? = null,
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
                // are what the home-screen widget and the watch face share with
                // this card, so the three cannot drift. See RouteMapScene.kt in
                // `:core:mapscene`.
                val scene = routeMapScene(
                    arc = arc,
                    outline = outline,
                    width = size.width,
                    height = size.height,
                    density = this,
                    topInsetPx = topInset.toPx(),
                    aircraft = aircraft,
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
 * world outline — `NetworkMap`, the widget's masks — so every map in the app
 * reads as one system rather than several independently-tuned ones. The strokes,
 * casing and markers those maps draw with are `:core:mapscene`'s, shared with
 * the watch; these two alphas are the phone's own, because the watch's map
 * takes its inks from its own theme's roles.
 */
const val WorldMapLandAlpha = 0.08f

/** The coast, at twice the fill, which is what makes a silhouette recognisable. */
const val WorldMapCoastAlpha = 0.16f

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
