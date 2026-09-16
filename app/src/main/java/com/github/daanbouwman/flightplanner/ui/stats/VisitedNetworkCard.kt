package com.github.daanbouwman.flightplanner.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.daanbouwman.flightplanner.R
import com.github.daanbouwman.flightplanner.core.designsystem.components.CompactWidthPreview
import com.github.daanbouwman.flightplanner.core.designsystem.components.LightDarkPreview
import com.github.daanbouwman.flightplanner.core.designsystem.components.ModeOption
import com.github.daanbouwman.flightplanner.core.designsystem.components.ModeSelector
import com.github.daanbouwman.flightplanner.core.designsystem.components.NetworkMap
import com.github.daanbouwman.flightplanner.core.designsystem.components.NetworkNode
import com.github.daanbouwman.flightplanner.core.designsystem.theme.FlightPlannerTheme
import com.github.daanbouwman.flightplanner.core.designsystem.theme.asChartFigure
import com.github.daanbouwman.flightplanner.feature.globe.ui.GlobeImagery
import com.github.daanbouwman.flightplanner.feature.globe.ui.GlobeNetwork
import com.github.daanbouwman.flightplanner.feature.globe.ui.GlobeNetworkSurface
import com.github.daanbouwman.flightplanner.feature.globe.ui.GlobeNode
import com.github.daanbouwman.flightplanner.feature.globe.ui.GlobeRefitControl
import com.github.daanbouwman.flightplanner.feature.globe.ui.rememberGlobeAvailable
import com.github.daanbouwman.flightplanner.feature.globe.ui.rememberGlobeControls
import com.github.daanbouwman.flightplanner.routing.RouteArc
import com.github.daanbouwman.flightplanner.routing.WorldOutline
import com.github.daanbouwman.flightplanner.ui.chrome.windowHeightDp

/** Which projection the visited network is drawn in. */
private enum class NetworkView { Flat, Globe }

/**
 * The visited network: every airport the logbook has been to, and every leg
 * between — flat, or on the sphere.
 *
 * ### Why the sphere is offered at all, and why it is not the default
 *
 * A fitted rectangle is the right drawing for a logbook that fits in one region:
 * it spends every pixel on the part of the world that has been flown, and the
 * distortion over a few thousand miles is not visible. It stops being the right
 * drawing the moment the set spans hemispheres — the frame widens to hold both,
 * the airports collapse into two clusters at the edges, and the arc between them
 * becomes a line across an empty middle that is nothing like the path a flight
 * takes. On a sphere that same pair is one short arc over the pole.
 *
 * So the toggle is not a novelty setting: it is the choice between the projection
 * that is honest about *density* and the one that is honest about *distance*.
 * **Flat is first** because most logbooks are regional and the flat map is the
 * cheaper, sharper, immediately-readable answer for them; the globe is a step
 * taken deliberately by somebody whose network has outgrown a rectangle.
 *
 * The toggle is **absent** on a device with no renderer, following 3B: a control
 * that opens nothing is worse than no control, and the flat map is not a fallback
 * there — it is simply the map.
 *
 * ### The card's padding is per child
 *
 * The flat map is inset like every other card body. The globe band is not: it
 * runs to the card's edges and is the card's last child, so it takes the card's
 * own rounded corners and the card ends where the band does — see
 * [GlobeNetworkBand] for why it used to end in a strip of bare card instead.
 */
@Composable
fun VisitedNetworkCard(
    visitedAirports: List<VisitedAirport>,
    visitedLegs: List<VisitedLeg>,
    outline: WorldOutline,
    modifier: Modifier = Modifier,
) {
    val globeAvailable = rememberGlobeAvailable()
    var view by rememberSaveable { mutableStateOf(NetworkView.Flat) }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        shape = MaterialTheme.shapes.large,
    ) {
        Column {
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.stats_section_network),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.2.sp,
                )
                Text(
                    text = stringResource(
                        R.string.stats_network_summary_format,
                        visitedAirports.size,
                        visitedLegs.size,
                    ),
                    style = MaterialTheme.typography.labelSmall.asChartFigure(),
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            if (globeAvailable) {
                Spacer(Modifier.height(10.dp))
                ModeSelector(
                    options = listOf(
                        ModeOption(
                            label = stringResource(R.string.stats_network_mode_flat),
                            contentDescription =
                                stringResource(R.string.stats_network_mode_flat_description),
                        ),
                        ModeOption(
                            label = stringResource(R.string.stats_network_mode_globe),
                            contentDescription =
                                stringResource(R.string.stats_network_mode_globe_description),
                        ),
                    ),
                    selectedIndex = view.ordinal,
                    onSelect = { index -> view = NetworkView.entries[index] },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            Spacer(Modifier.height(12.dp))

            if (globeAvailable && view == NetworkView.Globe) {
                GlobeNetworkBand(visitedAirports, visitedLegs, outline)
            } else {
                FlatNetworkMap(
                    visitedAirports = visitedAirports,
                    visitedLegs = visitedLegs,
                    outline = outline,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .height(FlatMapHeight)
                        .clip(MaterialTheme.shapes.medium),
                )
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

/**
 * The same network on the sphere, as a full-bleed band across the card, with
 * the imagery credit as a caption under it.
 *
 * ### The band is the card's width, and the sphere is fitted to that
 *
 * It was a fixed 260 dp letterbox, and what that produced on a phone was a
 * planet a few percent wider than the band: touching both card edges, cropped
 * flat by the band's bottom, with the card's 16 dp of bottom padding showing
 * under the cut as a strip of bare grey. The band is now **square on the card's
 * width** — capped at the same fraction of the window `DeepGlobeHero` takes, so
 * a tall tablet does not get a 600 dp planet — and `GlobeNetworkSurface` is told
 * the band has edges: its fit settles into either a clean window of imagery or
 * the whole planet inset [DiscInset] from the band's short axis, never a disc
 * cut by the frame. The band is the card's last drawn child, so the card's own
 * rounded corners clip it and there is no strip under it.
 *
 * Square corners were once argued for here on the grounds that a `SurfaceView`
 * cannot be clipped. That argument is stale: an *embedded* globe is drawn by a
 * `TextureView` precisely so Compose can clip it, and the card does.
 *
 * ### What is on the glass, and what is not
 *
 * The camera stack is gone. On the shipped band it sat on the EVRA–EHTW leg and
 * hid EHTW; the credit plate in the other corner covered the American airports.
 * A pinch zooms, a drag pans, and TalkBack reaches every camera move through
 * the surface's own custom actions, so the only control that had no other way
 * of being done was getting back — one small re-frame plate, top right, where
 * a network centred on the band never puts a dot. The credit is a caption under
 * the band, in the card, where it covers nothing; Esri's terms want it on the
 * map view, and directly beneath the imagery in the same card is where Esri's
 * own viewer puts it.
 *
 * Space — what the band paints where there is no planet — is the card's own
 * container colour, so the whole-planet view is a planet on the card rather
 * than a planet on a `surface`-coloured square cut into it.
 */
@Composable
private fun GlobeNetworkBand(
    visitedAirports: List<VisitedAirport>,
    visitedLegs: List<VisitedLeg>,
    outline: WorldOutline,
) {
    val controls = rememberGlobeControls()
    val network = remember(visitedAirports, visitedLegs) {
        GlobeNetwork(
            nodes = visitedAirports.map { airport ->
                GlobeNode(
                    icao = airport.icao,
                    latitude = airport.latitude,
                    longitude = airport.longitude,
                    visits = airport.visitCount,
                )
            },
            legs = visitedLegs.map { leg -> leg.arc.lats to leg.arc.lons },
        )
    }
    val maxBandHeight = globeBandMaxHeight()
    val refitDescription = stringResource(R.string.stats_network_refit)

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        GlobeNetworkSurface(
            network = network,
            controls = controls,
            // A band inside the Stats list. A vertical one-finger drag over it
            // scrolls the list, as it does over every other card; sideways and
            // two-finger gestures still turn the globe.
            nestedVerticalScroll = true,
            // ...and this globe is *embedded*: a `LazyColumn` item inside a rounded
            // card, rather than a full-bleed hero that owns its box. Two things
            // follow, and both are bugs when they are missing — it is drawn by a
            // `TextureView` so Compose can actually clip it, and the session gets a
            // longer teardown grace so scrolling past the card does not rebuild the
            // engine and the 32 MB atlas. See `GlobeTextureView`.
            embedded = true,
            discInset = DiscInset,
            spaceColor = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier
                .fillMaxWidth()
                .height(minOf(maxWidth, maxBandHeight)),
            content = {
                // What the globe crossfades in over while the first tiles land. The
                // same drawing the Flat mode shows, so the swap is a projection
                // changing rather than the panel emptying and refilling.
                FlatNetworkMap(
                    visitedAirports = visitedAirports,
                    visitedLegs = visitedLegs,
                    outline = outline,
                    modifier = Modifier.fillMaxSize(),
                )
            },
            overlay = {
                GlobeRefitControl(
                    onClick = controls::refit,
                    contentDescription = refitDescription,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(GlassGutter),
                )
            },
        )
    }

    ImageryCaption(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp, bottom = 12.dp),
    )
}

/**
 * The imagery provider's credit, as one caption line under the band.
 *
 * `labelSmall` in `onSurfaceVariant` — the weight of a photo credit, not of a
 * control. It may wrap to a second line at large font scales rather than lose
 * a data provider to an ellipsis: the credit is a licence term, and the list
 * of names is the term. Hidden from accessibility for the reason
 * `GlobeAttribution` is: a licence notice is not something a reader navigating
 * by TalkBack is looking for, and it is in reading order on the Licences screen.
 */
@Composable
private fun ImageryCaption(modifier: Modifier = Modifier) {
    val attribution = GlobeImagery.attribution
    val text = remember(attribution) {
        listOfNotNull(attribution.label, attribution.credit).joinToString(" · ")
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        modifier = modifier.clearAndSetSemantics { },
    )
}

/**
 * The most the band may be tall: `DeepGlobeHero`'s fraction of the window, so
 * the two globes in the app claim the same share of a tall screen, floored so a
 * landscape phone still gets a planet rather than a slot.
 */
@Composable
private fun globeBandMaxHeight(): Dp =
    maxOf(windowHeightDp() * BandWindowFraction, MinBandHeight)

/**
 * The visited network in the design system's map — [NetworkMap], which is
 * [com.github.daanbouwman.flightplanner.core.designsystem.components.RouteMap]'s
 * ink with more legs on it. This is only the translation from the Stats
 * screen's types to the geometry the map takes; every drawing decision is the
 * map's.
 *
 * There is no empty-network fallback: the card is composed only when there are
 * visited airports (see `StatsScreen`), and a drawing of nowhere for a state
 * that cannot occur was dead code.
 */
@Composable
private fun FlatNetworkMap(
    visitedAirports: List<VisitedAirport>,
    visitedLegs: List<VisitedLeg>,
    outline: WorldOutline,
    modifier: Modifier = Modifier,
) {
    val nodes = remember(visitedAirports) {
        visitedAirports.map { NetworkNode(it.latitude, it.longitude, it.visitCount) }
    }
    val legs = remember(visitedLegs) { visitedLegs.map { it.arc } }
    NetworkMap(nodes = nodes, legs = legs, outline = outline, modifier = modifier)
}

private val FlatMapHeight: Dp = 180.dp

/** The same share of the window `DeepGlobeHero` takes. */
private const val BandWindowFraction = 0.44f

/** Below this a planet is a slot; a landscape phone's 44 % would be about 170 dp. */
private val MinBandHeight: Dp = 240.dp

/** How much card shows between the whole planet's limb and the band's edge. */
private val DiscInset: Dp = 12.dp

private val GlassGutter: Dp = 12.dp

// ---- previews -----------------------------------------------------------------

/** The phone's own fixture: two flights, four fields, two legs, both continents. */
internal val PreviewAirports = listOf(
    VisitedAirport("EVRA", "Riga International Airport", 56.92, 23.97, 1),
    VisitedAirport("EHTW", "Twente Airport", 52.28, 6.89, 1),
    VisitedAirport("KOLD", "Old Town Municipal Airport", 44.95, -68.67, 1),
    VisitedAirport("KVGC", "Hamilton Municipal Airport", 43.02, -75.38, 1),
)

internal val PreviewLegs = listOf(
    previewLeg(PreviewAirports[0], PreviewAirports[1]),
    previewLeg(PreviewAirports[2], PreviewAirports[3]),
)

private fun previewLeg(from: VisitedAirport, to: VisitedAirport) = VisitedLeg(
    departureIcao = from.icao,
    arrivalIcao = to.icao,
    fromLat = from.latitude,
    fromLon = from.longitude,
    toLat = to.latitude,
    toLon = to.longitude,
    arc = RouteArc.sampleGeographic(from.latitude, from.longitude, to.latitude, to.longitude, samples = 32),
)

@LightDarkPreview
@CompactWidthPreview
@Composable
private fun VisitedNetworkCardFlatPreview() {
    FlightPlannerTheme(dynamicColor = false) {
        VisitedNetworkCard(
            visitedAirports = PreviewAirports,
            visitedLegs = PreviewLegs,
            // No asset in a preview: the graticule fallback stands in for the coast.
            outline = WorldOutline.Empty,
            modifier = Modifier.padding(16.dp),
        )
    }
}

/**
 * The globe band's layout with no renderer: the still map fills the band, the
 * re-frame plate sits top right and the credit captions it. A preview cannot
 * acquire a session, so the sphere itself is judged on a device.
 */
@LightDarkPreview
@CompactWidthPreview
@Composable
private fun VisitedNetworkCardGlobeBandPreview() {
    FlightPlannerTheme(dynamicColor = false) {
        Card(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            shape = MaterialTheme.shapes.large,
        ) {
            Column {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.stats_section_network),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.2.sp,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Spacer(Modifier.height(12.dp))
                GlobeNetworkBand(PreviewAirports, PreviewLegs, WorldOutline.Empty)
            }
        }
    }
}
