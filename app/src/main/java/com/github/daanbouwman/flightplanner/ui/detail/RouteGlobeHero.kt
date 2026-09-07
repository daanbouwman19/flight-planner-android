package com.github.daanbouwman.flightplanner.ui.detail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.daanbouwman.flightplanner.core.designsystem.components.RouteMap
import com.github.daanbouwman.flightplanner.feature.globe.ui.GlobeAttribution
import com.github.daanbouwman.flightplanner.feature.globe.ui.GlobeCameraControls
import com.github.daanbouwman.flightplanner.feature.globe.ui.GlobeImagery
import com.github.daanbouwman.flightplanner.feature.globe.ui.GlobeRoute
import com.github.daanbouwman.flightplanner.feature.globe.ui.GlobeSurface
import com.github.daanbouwman.flightplanner.feature.globe.ui.rememberGlobeControls
import com.github.daanbouwman.flightplanner.routing.GeoArc
import com.github.daanbouwman.flightplanner.routing.WorldOutline
import com.github.daanbouwman.flightplanner.ui.chrome.SharedRouteKeys
import com.github.daanbouwman.flightplanner.ui.chrome.sharedRouteElement
import com.github.daanbouwman.flightplanner.ui.chrome.windowHeightDp

/**
 * The deep hero: the globe claiming the top of the route detail screen.
 *
 * ### Why 44% of the window and not C3's 220 dp
 *
 * A sphere needs room to read as a sphere. At 220 dp on a 900 dp window the
 * globe is a disc in a letterbox, and everything Phase G added — the limb, the
 * arc bending over the curve, the terminator — is happening in a strip too
 * shallow to show any of it.
 *
 * It runs **under the status bar**, which is the other half of the same idea.
 * The window is edge to edge and the bars are empty, so the imagery simply
 * continues up past the clock. That is the app's own invariant applied to the
 * one surface in it that is a photograph.
 *
 * ### What is *not* on the glass, and why that is a departure
 *
 * The concept moves DIST and BRG onto the sphere as translucent chips. **They
 * are not here**, and that is a recorded divergence rather than an omission.
 *
 * The concept was drawn against `design-mirror`'s simplified route detail, which
 * still carries the row of four `ValueChip`s under the hero — so "move DIST and
 * BRG onto the sphere" was moving them *from* somewhere. In the real screen that
 * row does not exist: [RouteDetailContent]'s spine already puts every figure at
 * the point on the leg where it is true, with the distance set large as the
 * figure the screen exists to state and counting up once as the eye lands on it.
 * A DIST chip on the glass would be that same number, smaller, a few dp above
 * itself.
 *
 * The concept's *rule* survives; only its instance moves. "The figures the arc
 * is about sit next to the arc" is satisfied by the arc being drawn directly
 * over the spine that states them — and the immersive screen, which has no
 * spine, does carry the plate of figures the concept draws for it.
 *
 * This is the same judgement the screen already made once when it deleted those
 * two chips. Reinstating them on the glass would be losing that argument to a
 * mock of an older layout.
 */
@Composable
fun DeepGlobeHero(
    departureIcao: String,
    destinationIcao: String,
    arc: GeoArc?,
    outline: WorldOutline,
    /** Completes the shared key the plan card carries. See the [RouteMap] below. */
    aircraftId: Int,
    modifier: Modifier = Modifier,
    /**
     * How much of the hero the screen’s own app bar sits over.
     *
     * The hero deliberately runs up under the bar, so the top of it is covered
     * by chrome that is opaque enough to hide a label whole. The globe fades a
     * label that rises into this strip rather than nudging it clear — see
     * `GlobeLabels`.
     */
    topChromeInset: Dp = 0.dp,
) {
    val controls = rememberGlobeControls()

    Box(modifier = modifier.height(heroHeight())) {
        val globeRoute = remember(arc, departureIcao, destinationIcao) {
            arc?.let {
                GlobeRoute(
                    departureIcao = departureIcao,
                    destinationIcao = destinationIcao,
                    arcLats = it.lats,
                    arcLons = it.lons,
                )
            }
        }

        if (globeRoute == null || arc == null) {
            // Before the airports have come back from the database. The hero
            // holds its bounds from the first frame — a shared element can only
            // travel to something that is already there — and fills in when the
            // query returns.
            Surface(
                // It carries the key as well, because the sentence above is only
                // true if it does: a card arriving while the airport query is
                // still in flight - a configuration change, a process restore -
                // would otherwise find nothing to travel to and pop in at full
                // width.
                modifier = Modifier
                    .fillMaxSize()
                    .sharedRouteElement(
                        SharedRouteKeys.face(departureIcao, destinationIcao, aircraftId),
                        remeasure = false,
                    ),
                shape = RectangleShape,
                color = MaterialTheme.colorScheme.surfaceContainer,
                content = {},
            )
            return@Box
        }

        GlobeSurface(
            route = globeRoute,
            controls = controls,
            topChromeInset = topChromeInset,
            // The hero sits inside the detail screen's own vertical scroll. A
            // one-finger drag that starts out vertical belongs to the page — the
            // reader is scrolling past a globe, not spinning it — while a
            // sideways drag, and anything with two fingers, is the globe's. The
            // immersive screen owns its window and does not make this trade.
            nestedVerticalScroll = true,
            modifier = Modifier.fillMaxSize(),
            content = {
                // C3's still map, and the frame the globe crossfades in over. On
                // a device with no renderer it is all there is, and nothing is
                // drawn to say so.
                //
                // **It is also what the plan card's face flies to.** The sphere
                // cannot be a shared element — a `SurfaceView` is a hardware
                // layer, and the overlay a shared element is drawn in is not
                // somewhere a hole punch can go — so the key goes on the map
                // underneath it instead. The card's map grows into the hero and
                // the globe crossfades in over it, which is the crossfade C3
                // already asks for, now with somewhere to start; going back, the
                // map lifts off the sphere and shrinks into the card.
                RouteMap(
                    arc = arc,
                    outline = outline,
                    modifier = Modifier
                        .fillMaxSize()
                        .sharedRouteElement(
                            SharedRouteKeys.face(departureIcao, destinationIcao, aircraftId),
                            remeasure = false,
                        ),
                )
            },
            overlay = {
                // Bottom right for the camera stack, bottom left for the credit:
                // the two corners furthest from where a thumb rests while
                // reading the page, and the two the arc is least likely to cross.
                GlobeCameraControls(
                    onZoomIn = controls::zoomIn,
                    onZoomOut = controls::zoomOut,
                    onResetNorth = if (controls.isRotated) controls::resetNorth else null,
                    onRefit = controls::refit,
                    bearingDegrees = controls.bearingDegrees,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(GlassGutter),
                )
                GlobeAttribution(
                    attribution = GlobeImagery.attribution,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(GlassGutter),
                )
            },
        )
    }
}

/**
 * How tall the hero is, as a fraction of the window.
 *
 * A fraction rather than a dp, so it holds on a 640 dp phone and a 1,200 dp
 * tablet alike: the point is how much of the *view* the planet occupies, which
 * is a proportion and not a length.
 *
 * The short-window case falls back to the still hero's own compact height. In
 * landscape, 44% of a 400 dp window is a globe above nothing at all — every fact
 * on the screen would sit below the fold behind a sphere that is mostly ocean.
 * That is the judgement the still hero already makes at that width, so this is a
 * floor on usable content rather than a floor on the globe.
 */
@Composable
private fun heroHeight(): Dp {
    val window = windowHeightDp()
    return if (window < ShortWindowMax) CompactHeroHeight else window * HeroFraction
}

private const val HeroFraction = 0.44f

private val ShortWindowMax: Dp = 480.dp

/** The still hero's own compact figure, kept so the two agree at that width. */
private val CompactHeroHeight: Dp = 132.dp

private val GlassGutter: Dp = 12.dp
