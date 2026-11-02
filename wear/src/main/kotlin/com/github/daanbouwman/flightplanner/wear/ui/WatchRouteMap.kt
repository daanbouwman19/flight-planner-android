package com.github.daanbouwman.flightplanner.wear.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.wear.compose.material3.MaterialTheme
import com.github.daanbouwman.flightplanner.mapscene.drawRouteMap
import com.github.daanbouwman.flightplanner.routing.WorldOutline
import com.github.daanbouwman.flightplanner.wear.map.WatchFace
import com.github.daanbouwman.flightplanner.wear.map.WatchMapStyle
import com.github.daanbouwman.flightplanner.wear.map.WatchSceneCache
import com.github.daanbouwman.flightplanner.wear.map.WatchSceneSource
import com.github.daanbouwman.flightplanner.wear.route.WatchRouteCard

/**
 * The route, drawn over the world, filling the whole face.
 *
 * ### The phone's map, through a module both can see
 *
 * This used to be a second copy of the phone's route map — its own path walk,
 * its own plane placement, its own silhouette cache, fifty lines of drawing —
 * because the phone's lived in `:core:designsystem`, which is built on the
 * phone's alpha-pinned `androidx.compose.material3`, and a watch APK must never
 * carry that. The copy was correct, and it was also where the watch fell behind:
 * Phase P3 moved the phone's coastline to bevel joins and butt caps, measured
 * as free at 1 dp, and the watch kept stroking a few thousand round joins a
 * frame.
 *
 * The scene now lives in `:core:mapscene`, which is Compose graphics and no
 * Material at all, and both devices draw through it: the same [drawRouteMap],
 * the same widths, the same plane and the same rule for when it fits, the same
 * stroke for the coast. What the watch does differently is one value,
 * [WatchMapStyle] — a frame padded for a circle, a margin that barely leaves the
 * face, two filled dots and no arrowhead, no graticule — and its inks, below.
 *
 * ### The scene is built before the page is drawn
 *
 * The 1:50m outline put ~40,000 points under a long-haul face, and building
 * them into paths inside the draw cost the first frame of every swipe ~20 ms
 * of UI thread on the watch. So this draws with `drawWithCache`, and takes its
 * scene from [scenes] — in the app, the ViewModel's [WatchSceneCache], which
 * builds the pages around the settled one on a background dispatcher. On a miss
 * the cache builds the scene right here, with the same function and the same
 * inputs, so the face looks the same whichever thread built it and never
 * appears late. The [WatchFace] key is worked out from this canvas's own size
 * and density, which are the ones the face reported up front.
 *
 * The palette is read in the draw, not in the cache block: a scene holds no
 * colour, so a theme change redraws the face and never rebuilds its scene.
 *
 * ### The map is drawn once per page, not once per frame
 *
 * A page turn is `AnimatedPage` scaling the outgoing and incoming pages through
 * a `graphicsLayer`, at a new scale every frame. Drawn straight onto the page,
 * a long-haul face's 15,000–64,000 fill and coast vertices were converted again
 * by RenderThread at every one of those scales: 44–48 ms of RenderThread a frame
 * on the faces past 30,000, and 20–26 fps through the swipe, while the GPU took
 * ~6–8 ms. At rest nothing changes and nothing is converted, which is
 * why only a swipe suffered, and why it suffered more the further out the face
 * was zoomed.
 *
 * So the map draws into an offscreen layer of its own
 * ([CompositingStrategy.Offscreen], ahead of the `drawWithCache`), and a turn
 * scales that finished image instead: ~4.7 ms of RenderThread a frame on the
 * same faces, and 60 fps on every zoom, dragged or flicked. The layer is drawn
 * when its page is first drawn, and again only when the map's own drawing
 * changes — a new scene, a new palette — never because the page around it
 * moved.
 *
 * Only the map, because the text over it is cheap to draw as vectors at any
 * scale: a layer around the whole page held the same 60 fps and resampled the
 * codes' glyph edges visibly soft mid-turn. And always, rather than only while
 * the pager scrolls. Either way a swipe draws the incoming page's map into a new
 * layer, since the pager never draws a page before it appears; but a layer made
 * only while the pager scrolls must also draw the map already on screen into
 * one again, at every swipe's start — at p90, a flick's slowest frame took 61 ms
 * with it to 37 with this — and a layer the map always has needs no pager state
 * threaded down to it.
 *
 * The cost is declared, and small: mid-turn everything the map draws is a
 * resample of its 1:1 image, which shows as a faint softening of the coast and
 * the route's edge while the page is scaled; at rest the layer is drawn 1:1,
 * within 5/255 of drawing straight to the page.
 * See WEAR-PLAN.md, *Swipe performance*.
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
 *
 * The scene paints past the canvas by its margin, and the layer cuts that off
 * at the canvas's square: an offscreen layer holds only its own bounds. Nothing
 * visible goes with it. The canvas is the whole face — `RouteFace` gives it
 * `fillMaxSize()`, on a page that fills the 480 × 480 display — and
 * `AnimatedPage` clips each page to the circle inscribed in that square, inside
 * its scale, so whatever lay past the square was already off the glass, at rest
 * and mid-turn alike.
 */
@Composable
internal fun WatchRouteMap(
    route: WatchRouteCard,
    outline: WorldOutline,
    palette: WatchMapPalette,
    scenes: WatchSceneSource,
    modifier: Modifier = Modifier,
) {
    val inks by rememberUpdatedState(palette)
    Spacer(
        modifier = modifier
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            .drawWithCache {
                // The face is a square the map fills; on a canvas that is not one,
                // the map is the square in its top-left corner, as it always was.
                val face = WatchFace(extentPx = size.minDimension.toInt(), density = density)
                val scene = scenes.scene(route, outline, face) ?: return@drawWithCache onDrawBehind { }
                onDrawBehind {
                    drawRouteMap(
                        scene = scene,
                        landColor = inks.land,
                        coastColor = inks.coast,
                        routeColor = inks.route,
                        casingColor = inks.casing,
                    )
                }
            },
    )
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
