package com.github.daanbouwman.flightplanner.ui.detail

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.github.daanbouwman.flightplanner.R
import com.github.daanbouwman.flightplanner.core.designsystem.components.RouteMap
import com.github.daanbouwman.flightplanner.core.designsystem.motion.FlightMotion
import com.github.daanbouwman.flightplanner.core.designsystem.motion.LocalReduceMotion
import com.github.daanbouwman.flightplanner.core.designsystem.theme.asChartFigure
import com.github.daanbouwman.flightplanner.core.designsystem.theme.withTabularFigures
import com.github.daanbouwman.flightplanner.model.Airport
import com.github.daanbouwman.flightplanner.model.Runway
import com.github.daanbouwman.flightplanner.model.SurfaceKind
import com.github.daanbouwman.flightplanner.navigation.Destination
import com.github.daanbouwman.flightplanner.ui.asBearing
import com.github.daanbouwman.flightplanner.ui.asFigure
import com.github.daanbouwman.flightplanner.ui.chrome.MaxContentWidth
import com.github.daanbouwman.flightplanner.ui.chrome.WideMaxContentWidth
import com.github.daanbouwman.flightplanner.ui.chrome.SharedRouteKeys
import com.github.daanbouwman.flightplanner.ui.chrome.isCompactHeight
import com.github.daanbouwman.flightplanner.ui.chrome.sharedRouteElement
import com.github.daanbouwman.flightplanner.ui.coordinates
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Everything a route detail says, with no opinion about where it is being said.
 *
 * It is a file of its own because it has **two hosts**: the full-screen
 * `RouteDetailScreen`, which a phone navigates to, and the detail pane of the
 * `ListDetailPaneScaffold` the Plan screen hosts when the window is wide enough
 * for two panes. Those differ in every way that is *not* content — one has an app
 * bar and predictive back, the other sits beside the list it was selected from —
 * so the content takes no `NavController`, no back handler and no scaffold. It
 * takes state and callbacks, and the two hosts supply what they mean.
 *
 * [onFlownConfirmed] is the one callback whose meaning genuinely differs, which
 * is why it is not called `onBack`: the screen pops itself, and the pane clears
 * its selection and stays exactly where it is.
 */
@Composable
fun RouteDetailContent(
    route: Destination.RouteDetail,
    state: RouteDetailUiState,
    onMarkFlown: () -> Boolean,
    onFlownConfirmed: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    /**
     * Whether the blocks stage themselves in on first composition.
     *
     * True on the screen, where arriving *is* the event and the stagger is the
     * only thing announcing it. False in the pane, where the host already
     * cross-fades one route's content out and the next one's in — staging on top
     * of that is two motions for one change, and they agree with each other no
     * better here than a staggered hero agreed with a shared element.
     */
    animateEntrance: Boolean = true,
    /**
     * Whether the route is already recorded in the logbook.
     *
     * When true, the "Mark as flown" button is shown in its marked state from the
     * start and cannot be tapped.
     */
    alreadyFlown: Boolean = false,
) {
    // From the arguments rather than from the loaded state: the shared element
    // has to be matchable on the first frame, and the airports arrive a query
    // later. The codes were in the navigation arguments all along.
    val faceKey = SharedRouteKeys.face(route.departureIcao, route.destinationIcao, route.aircraftId)
    val aircraftKey =
        SharedRouteKeys.aircraft(route.departureIcao, route.destinationIcao, route.aircraftId)

    Column(
        // The line-length cap lives **here**, not in either host, because it is a
        // property of the content rather than of where the content is shown: a
        // runway row whose fields end up further apart than they are tall is
        // unreadable in a landscape window and in a tablet's detail pane alike,
        // and the second host would otherwise have had to remember the lesson the
        // first one learned. Hosts decide margins and alignment; the content
        // decides how wide a line of it may get.
        modifier = modifier.widthIn(
            max = if (isCompactHeight()) MaxContentWidth else WideMaxContentWidth
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // ### The hero holds its bounds before it has anything to draw
        //
        // **A shared element can only travel to something that is already there.**
        // This screen is entered with two ICAO codes and reads its airports from
        // the database, so `arc` is null for the first frames of the navigation.
        // The container is therefore unconditional: it carries the shared key and
        // reserves the hero's exact bounds from the first frame, and the map fades
        // into it when the query returns. Gated on `arc`, there would be nothing
        // for the card's map to fly to, and the hero would appear abruptly at full
        // width the moment the airports landed.
        //
        // Neither this nor the eyebrow is staggered, either. They are the elements
        // that arrive by *travelling*; fading them in as well animates one thing
        // twice, and the two animations do not agree. The stagger starts below
        // them, where nothing has a counterpart on the card.
        val heroShape = MaterialTheme.shapes.largeIncreased
        Surface(
            shape = heroShape,
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier
                .fillMaxWidth()
                // Shorter when the window is short, for the reason the card is:
                // in landscape a 220 dp hero plus the app bar is the entire
                // window, so every fact on the screen sits below the fold behind
                // a map that is mostly ocean. The compact figure is the card's
                // own, because it is the same judgement — the map stays a
                // recognisable place at 132 dp, and nothing else here would
                // survive being squeezed at all.
                .height(if (isCompactHeight()) CompactMapHeight else MapHeight)
                // The shape again, for the overlay a shared element is drawn in:
                // this `Surface` clips its own content, but the clip is an
                // ancestor's and an ancestor's clip does not follow the element
                // into that overlay. Without it the hero flies as a rectangle and
                // squares off at whichever end of the navigation it is arriving
                // at.
                .sharedRouteElement(faceKey, remeasure = false, clipShape = heroShape),
        ) {
            state.arc?.let { arc ->
                RouteMap(
                    arc = arc,
                    outline = state.outline,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = state.aircraft?.displayName.orEmpty(),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .weight(1f)
                    .sharedRouteElement(aircraftKey),
            )
            Text(
                text = state.aircraft?.category.orEmpty(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // The spine's three rows carry no spacing between them: the rail has to
        // run unbroken from the ring to the dot, and a gap between rows is a gap
        // in the rail. Each block pads its own bottom instead.
        Column(modifier = Modifier.fillMaxWidth()) {
            AirportBlock(
                marker = SpineMarker.Ring,
                rail = Rail.FromMarker,
                label = stringResource(R.string.route_detail_departure),
                airport = state.departure,
                runways = state.departureRunways,
                requiredRunwayFt = state.requiredRunwayFt,
                headingLabel = state.initialBearingDeg?.let {
                    stringResource(R.string.route_detail_heading_out, it.asBearing())
                },
                loading = state.loading,
                snackbarHostState = snackbarHostState,
                modifier = Modifier.enterStaggered(0, animateEntrance),
            )

            LegBlock(state = state, modifier = Modifier.enterStaggered(1, animateEntrance))

            AirportBlock(
                marker = SpineMarker.Dot,
                rail = Rail.ToMarker,
                label = stringResource(R.string.route_detail_destination),
                airport = state.destination,
                runways = state.destinationRunways,
                requiredRunwayFt = state.requiredRunwayFt,
                headingLabel = state.finalBearingDeg?.let {
                    stringResource(R.string.route_detail_heading_in, it.asBearing())
                },
                loading = state.loading,
                snackbarHostState = snackbarHostState,
                modifier = Modifier.enterStaggered(2, animateEntrance),
            )
        }

        WeatherBlock(modifier = Modifier.enterStaggered(3, animateEntrance))

        ActionsBlock(
            state = state,
            onMarkFlown = onMarkFlown,
            onFlownConfirmed = onFlownConfirmed,
            snackbarHostState = snackbarHostState,
            modifier = Modifier.enterStaggered(4, animateEntrance),
            alreadyFlown = alreadyFlown,
        )
    }
}

/** What the spine draws where a block meets it. */
private enum class SpineMarker { Ring, Dot, Tick }

/** How much rail a spine row draws: down from its marker, up to it, or straight through. */
private enum class Rail { FromMarker, ToMarker, Through }

/**
 * One row of the spine: a marker and a rail segment in the gutter, content beside.
 *
 * `IntrinsicSize.Min` is what lets the gutter know how tall the row is — the
 * standard measurement for a rule running alongside content it does not size. It
 * costs a second measure pass over three rows on a screen that is not a list,
 * which is the case where that is affordable.
 *
 * The marker lines up with the first line of the content because the gutter's
 * marker sits half a `headlineSmall` line down, and the code beside it is set in
 * that style. That holds at every font scale without a hard-coded offset.
 */
@Composable
private fun SpineRow(
    marker: SpineMarker,
    rail: Rail,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val railColor = MaterialTheme.colorScheme.outlineVariant
    val markerColor = MaterialTheme.colorScheme.primary
    val headHeight = with(LocalDensity.current) {
        MaterialTheme.typography.headlineSmall.lineHeight.toDp()
    }

    Row(modifier = modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Canvas(modifier = Modifier.width(SpineGutter).fillMaxHeight()) {
            val x = size.width / 2f
            val markerY = headHeight.toPx() / 2f
            val radius = MarkerRadius.toPx()

            val from = if (rail == Rail.ToMarker) 0f else markerY
            val to = if (rail == Rail.ToMarker) markerY else size.height
            val start = if (rail == Rail.Through) 0f else from
            drawLine(
                color = railColor,
                start = Offset(x, start),
                end = Offset(x, to),
                strokeWidth = RailWidth.toPx(),
            )

            val centre = Offset(x, markerY)
            when (marker) {
                // Hollow ring and filled dot, exactly as RouteMap marks the two
                // ends of the arc. The page reuses the map's legend rather than
                // inventing a second one.
                SpineMarker.Ring -> drawCircle(
                    color = markerColor,
                    radius = radius,
                    center = centre,
                    style = Stroke(width = MarkerStroke.toPx()),
                )
                SpineMarker.Dot -> drawCircle(color = markerColor, radius = radius, center = centre)
                // The leg is not a place, so it is a tick across the rail rather
                // than an endpoint on it: the rail passes through.
                SpineMarker.Tick -> drawLine(
                    color = markerColor,
                    start = Offset(x - radius, markerY),
                    end = Offset(x + radius, markerY),
                    strokeWidth = MarkerStroke.toPx(),
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f).padding(bottom = SpineRowSpacing),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            content = content,
        )
    }
}

/**
 * One end of the route: what it is called, where it is, and what it offers.
 *
 * The name is here rather than on the card for the reason it left the card — at
 * 360 dp between two codes it truncated to "Stangland A...", and a truncated name
 * is worse than an absent one. Here there is a line to itself for it.
 *
 * The heading on this block is the one belonging to *this* end: the departure
 * states the heading the leg leaves on, the destination the heading it arrives
 * on. On EHAM–KJFK those differ by 62°, so one figure on a shared chip row would
 * be wrong for one of the two ends every time.
 */
@Composable
private fun AirportBlock(
    marker: SpineMarker,
    rail: Rail,
    label: String,
    airport: Airport?,
    runways: List<Runway>,
    requiredRunwayFt: Int,
    headingLabel: String?,
    loading: Boolean,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    SpineRow(marker = marker, rail = rail, modifier = modifier) {
        Text(
            text = airport?.icao ?: EmptyFigure,
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = when {
                airport != null -> airport.name
                loading -> stringResource(R.string.route_detail_loading_airport)
                else -> stringResource(R.string.route_detail_missing_airport)
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        airport?.let {
            // The elevation is dropped at exactly zero, on 823 of 24,321
            // airports. OurAirports leaves the field blank where nobody
            // published one and the ETL writes that as 0, so the two cases are
            // indistinguishable here — and an inland strip stated as sea level
            // is a wrong figure on an instrument, which is worse than a missing
            // one. A genuinely sea-level field loses a line that said nothing
            // surprising.
            val facts = listOfNotNull(
                it.municipality,
                stringResource(R.string.route_detail_elevation, it.elevationFt.asFigure())
                    .takeIf { _ -> it.elevationFt != 0 },
            )
            if (facts.isNotEmpty()) {
                Text(
                    text = facts.joinToString(FactSeparator),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            headingLabel?.let { heading ->
                Text(
                    text = heading,
                    style = MaterialTheme.typography.labelMedium.asChartFigure(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Runways(runways = runways, airport = it, requiredRunwayFt = requiredRunwayFt)
            AirportLinks(airport = it, snackbarHostState = snackbarHostState)
        }
    }
}

/**
 * Every runway end at a field, collapsed to the longest until asked.
 *
 * Not collapsed to save ink. An airport with ten ends and one with two would
 * otherwise move everything below by some 400 dp, so the leg's own figures would
 * be on the first screenful for one route and not for the next, and the page
 * would have no fixed shape. Collapsed, it has one.
 *
 * An empty list means the second query has not landed yet rather than that the
 * field has no runways: the ETL admits no airport without at least one open
 * runway of known length. Until it lands, the denormalised longest figure the
 * airport already carries stands in, so the block never shows a hole.
 */
@Composable
private fun Runways(runways: List<Runway>, airport: Airport, requiredRunwayFt: Int) {
    if (runways.isEmpty()) {
        Text(
            text = stringResource(R.string.plan_runway_value, airport.longestRunwayFt.asFigure()),
            style = MaterialTheme.typography.labelMedium.asChartFigure(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    var expanded by rememberSaveable(airport.id) { mutableStateOf(false) }
    val shown = if (expanded) runways else runways.take(1)

    shown.forEach { runway -> RunwayLine(runway = runway, requiredRunwayFt = requiredRunwayFt) }

    if (runways.size > 1) {
        val remaining = runways.size - 1
        Text(
            text = if (expanded) {
                stringResource(R.string.route_detail_runways_fewer)
            } else {
                pluralStringResource(R.plurals.route_detail_runways_more, remaining, remaining)
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clickable { expanded = !expanded }
                .padding(vertical = 4.dp),
        )
    }
}

/**
 * One runway end, set as a chart line rather than as the desktop's sentence.
 *
 * The desktop writes `Runway: 18R, heading: 183.00, length: 12467 ft, …` into a
 * table cell, which reads well in prose and badly in a stack of six. Here it is
 * the fields alone, separated the way the rest of the app separates facts on one
 * line, in tabular figures so the lengths stay comparable down the block.
 *
 * The separator is a middot rather than the run of spaces this first had:
 * repeated spaces in a string resource are folded to one, so the columns that
 * looked right in the source arrived as `197 ft ASP` on the device.
 *
 * **The surface is the normalised kind, not the raw column — a divergence from
 * the desktop, which prints the column.** That column is free text and the
 * dataset spells one surface at least five ways: `ASP` (19,738 rows), `ASPH`,
 * `ASPH-G`, `Asphalt` and `PEM` are all asphalt, and a route between two ordinary
 * airports really does show `ASP` at one end and `ASPHALT` at the other. It is
 * also junk on thousands of rows — `X`, `N`, `G`, `C`, and 529 rows of empty
 * string. The ETL already resolved all of that into [SurfaceKind]; this shows
 * what it resolved, and says nothing at all where the answer is unknown.
 *
 * **A deliberate divergence:** `format_runway` also prints each runway's own
 * elevation. That is the field elevation on almost every runway of almost every
 * airport, so it is stated once on the block above instead of six times here.
 */
@Composable
private fun RunwayLine(runway: Runway, requiredRunwayFt: Int) {
    val tooShort = requiredRunwayFt > 0 && runway.lengthFt < requiredRunwayFt
    val tooShortLabel = stringResource(R.string.route_detail_runway_short_note)

    // Assembled from the fields that are known rather than through one format
    // string, because several of them are routinely absent: a grass strip
    // publishes no true heading, and the surface column is empty on 529 rows.
    // A positional format would render those as a stray separator.
    val line = listOfNotNull(
        runway.ident,
        runway.trueHeadingDeg?.let { it.toInt().mod(360).asBearing() },
        // 4,451 of the 58,221 runway ends publish no width, and the ETL writes
        // that as zero — so a positional format states `3,444 × 0 ft`, which is
        // not a narrow runway, it is a runway whose width nobody recorded.
        if (runway.widthFt > 0) {
            stringResource(
                R.string.route_detail_runway_size,
                runway.lengthFt.asFigure(),
                runway.widthFt.asFigure(),
            )
        } else {
            stringResource(R.string.route_detail_runway_length, runway.lengthFt.asFigure())
        },
        surfaceLabel(runway.surfaceKind),
        stringResource(R.string.route_detail_runway_lit).takeIf { runway.lighted },
    ).joinToString(FactSeparator)

    Text(
        text = line,
        style = MaterialTheme.typography.labelMedium.asChartFigure(),
        // Colour carries the warning rather than a separate icon, exactly as it
        // does on the card. The description says it in words for anyone the
        // colour does not reach.
        color = if (tooShort) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.semantics {
            if (tooShort) contentDescription = "$line. $tooShortLabel"
        },
    )
}

/**
 * The word for a runway surface, or null where the dataset does not know.
 *
 * `UNKNOWN` returns null rather than "Unknown" so the field leaves the line
 * entirely: on a runway line, "Unknown" is a word that occupies the space where a
 * fact goes and says nothing that the absence would not.
 */
@Composable
private fun surfaceLabel(kind: SurfaceKind): String? = when (kind) {
    SurfaceKind.HARD -> stringResource(R.string.surface_hard)
    SurfaceKind.GRASS -> stringResource(R.string.surface_grass)
    SurfaceKind.GRAVEL -> stringResource(R.string.surface_gravel)
    SurfaceKind.DIRT -> stringResource(R.string.surface_dirt)
    SurfaceKind.SAND -> stringResource(R.string.surface_sand)
    SurfaceKind.WATER -> stringResource(R.string.surface_water)
    SurfaceKind.SNOW_ICE -> stringResource(R.string.surface_snow_ice)
    SurfaceKind.UNKNOWN -> null
}

/**
 * The coordinates, and the two things a user does with an airport's position.
 *
 * Both are the desktop's: a copyable coordinate label and a Google Maps link, on
 * the airport rather than on the route, because a position is a property of a
 * field.
 */
@Composable
private fun AirportLinks(airport: Airport, snackbarHostState: SnackbarHostState) {
    val scope = rememberCoroutineScope()
    val noHandler = stringResource(R.string.route_detail_no_app)
    val launcher = rememberRouteActionLauncher(scope) {
        scope.launch { snackbarHostState.showSnackbar(noHandler) }
    }
    val position = coordinates(airport.latitude, airport.longitude)
    val copyLabel = stringResource(R.string.route_detail_action_copy_coordinates)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // A text button's own content padding would inset these two by 12 dp,
        // so the coordinates would start to the right of every other line in the
        // block and the block would look as though it had a second margin. The
        // vertical padding stays: it is what keeps the touch target reachable.
        val edgeToEdge = PaddingValues(horizontal = 0.dp, vertical = 8.dp)
        TextButton(
            onClick = { launcher.copy(copyLabel, position) },
            contentPadding = edgeToEdge,
        ) {
            Text(
                text = position,
                style = MaterialTheme.typography.labelMedium.asChartFigure(),
            )
        }
        TextButton(
            onClick = { launcher.open(RouteLinks.googleMaps(airport.latitude, airport.longitude)) },
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(stringResource(R.string.route_detail_action_map))
        }
    }
}

/**
 * The leg itself, on the spine between the two ends it belongs to.
 *
 * The distance counts up once, because it is the figure the screen exists to
 * state. The cruise speed underneath is the desktop's hover text — "Based on
 * cruise speed of 460 kts" — made visible, since a phone has no hover and an
 * estimate with no stated basis invites more trust than it has earned.
 */
@Composable
private fun LegBlock(state: RouteDetailUiState, modifier: Modifier = Modifier) {
    SpineRow(marker = SpineMarker.Tick, rail = Rail.Through, modifier = modifier) {
        val distance = FlightMotion.rememberCountUp(state.distanceNm)
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = stringResource(R.string.plan_value_nautical_miles, distance.asFigure()),
                style = MaterialTheme.typography.headlineSmall.asChartFigure(),
            )
            Text(
                text = state.flightTime?.format() ?: EmptyFigure,
                style = MaterialTheme.typography.titleLarge.asChartFigure(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        state.flightTime?.let { time ->
            Text(
                text = stringResource(
                    R.string.route_detail_cruise,
                    time.effectiveSpeedKt.toInt().asFigure(),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Where the weather will be.
 *
 * It states what is missing and where it will appear rather than drawing an empty
 * pair of chips, because an unexplained blank reads as something that broke. The
 * block also holds roughly the height two decoded reports will occupy, so when
 * the weather phase fills it the page does not jump — reserving the space from
 * the start is what makes "fade in, never reflow" free rather than a separate
 * piece of work later.
 */
@Composable
private fun WeatherBlock(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.route_detail_weather),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            // A floor on the container rather than a fixed height on the text.
            // Fixed, the message sat in the top of a box with a band of nothing
            // under it, which reads as content that failed to load — the exact
            // impression a placeholder exists to avoid.
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = WeatherReservedHeight),
        ) {
            Text(
                text = stringResource(R.string.route_detail_weather_pending),
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(12.dp),
            )
        }
    }
}

/**
 * What a user does with a route once they have read it.
 *
 * Mark as flown is the app's own action and is filled; the other four hand the
 * route to something else and are outlined. Plain buttons in a `FlowRow` rather
 * than a `ButtonGroup` or a `FloatingToolbar`: both are Expressive surface that
 * `:app` may not import directly, and the first is the one that crashes when its
 * items fill the width.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActionsBlock(
    state: RouteDetailUiState,
    onMarkFlown: () -> Boolean,
    onFlownConfirmed: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    alreadyFlown: Boolean = false,
) {
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val noHandler = stringResource(R.string.route_detail_no_app)
    val launcher = rememberRouteActionLauncher(scope) {
        scope.launch { snackbarHostState.showSnackbar(noHandler) }
    }

    val departure = state.departure
    val destination = state.destination
    val aircraft = state.aircraft
    val summary = if (departure != null && destination != null && aircraft != null) {
        RouteLinks.summary(aircraft, departure.icao, destination.icao, state.distanceNm)
    } else {
        null
    }

    // One state rather than two independent booleans — `marked` plus a second
    // `justConfirmed` flag once fixed a real bug here (an already-flown route
    // handing itself back ~500 ms after opening, because `marked` is also
    // seeded `true` when a Logbook entry arrives already flown) but left the
    // fix enforced only by a comment: nothing stopped a future caller from
    // setting `marked = true` from some other path and forgetting the second
    // flag, reintroducing the exact bug. A three-way state makes that omission
    // a compile-time-visible choice instead.
    //
    // `rememberSaveable`, so a rotation inside the confirmation window does not
    // reset it. Without that, the screen came back reading "Mark as flown" for a
    // route it had already logged, the hand-back effect below never fired
    // again, and the user was left on a detail screen that had quietly
    // finished its business.
    var flownState by rememberSaveable(alreadyFlown) {
        mutableStateOf(if (alreadyFlown) FlownState.AlreadyFlown else FlownState.NotMarked)
    }
    val unavailable = stringResource(R.string.route_detail_mark_flown_unavailable)
    val needsIcao = stringResource(R.string.route_detail_simbrief_needs_icao)
    val copyLabel = stringResource(R.string.route_detail_action_copy)
    val shareTitle = stringResource(R.string.route_detail_action_share_title)

    // The confirmation is shown here and the undo is offered back on the list, so
    // the screen holds still long enough to be read before it hands over.
    //
    // **The lifecycle check is not defensive tidiness; without it this pops the
    // back stack twice.** A destination stays composed for the length of its exit
    // transition, so if the user taps the app bar's back arrow — or swipes back —
    // during the half-second wait, the first pop removes this screen and *this
    // effect is still alive* to fire the second. That second pop takes Plan with
    // it, and Plan is the start destination of both its own graph and the root:
    // the back stack empties and the app is left showing nothing at all, with no
    // navigation bar to escape by. Once the screen is no longer resumed, the
    // journey it was going to make has already been made by someone else.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(flownState) {
        if (flownState != FlownState.JustConfirmed) return@LaunchedEffect
        delay(FlightMotion.EmphasisMillis.toLong())
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) onFlownConfirmed()
    }

    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MarkFlownButton(
            marked = flownState != FlownState.NotMarked,
            onClick = {
                if (onMarkFlown()) {
                    // The one haptic on this screen. A commitment landed —
                    // everything else here is browsing or handing the route to
                    // another app, and a phone that buzzes at those is a phone
                    // whose buzz stops meaning anything.
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    flownState = FlownState.JustConfirmed
                } else {
                    scope.launch { snackbarHostState.showSnackbar(unavailable) }
                }
            },
        )

        summary?.let { text ->
            OutlinedButton(onClick = { launcher.copy(copyLabel, text) }) {
                Text(stringResource(R.string.route_detail_action_copy))
            }
        }

        if (departure != null && destination != null) {
            OutlinedButton(
                onClick = { launcher.open(RouteLinks.skyVector(departure.icao, destination.icao)) },
            ) {
                Text(stringResource(R.string.route_detail_action_skyvector))
            }

            OutlinedButton(
                onClick = {
                    val time = state.flightTime
                    if (!RouteLinks.simBriefAccepts(departure, destination) ||
                        aircraft == null ||
                        time == null
                    ) {
                        scope.launch { snackbarHostState.showSnackbar(needsIcao) }
                    } else {
                        launcher.open(
                            RouteLinks.simBrief(aircraft, departure.icao, destination.icao, time),
                        )
                    }
                },
            ) {
                Text(stringResource(R.string.route_detail_action_simbrief))
            }
        }

        summary?.let { text ->
            OutlinedButton(onClick = { launcher.share(text, shareTitle) }) {
                Text(stringResource(R.string.route_detail_action_share))
            }
        }
    }
}

/**
 * Where [ActionsBlock]'s mark-flown button stands, and — for [JustConfirmed]
 * only — whether the hand-back effect should run.
 *
 * [AlreadyFlown] and [JustConfirmed] render identically (the button disabled,
 * reading "Marked as flown"); the distinction exists solely to answer "did the
 * user just do this, or did it arrive this way" for that one effect, without
 * a second field that could say something different from the first.
 */
private enum class FlownState { NotMarked, AlreadyFlown, JustConfirmed }

/**
 * Mark as flown, and the state after it.
 *
 * It stays tappable when the route has left the list, so that tapping can say
 * *why* — a button that is merely grey answers nothing, and "generate this route
 * again" is something the user can act on. Once marked it is disabled, which is
 * the desktop popup's behaviour and is true for the half-second before the screen
 * hands back to the list.
 */
@Composable
private fun MarkFlownButton(marked: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = !marked,
    ) {
        Text(
            stringResource(
                if (marked) {
                    R.string.route_detail_action_marked_flown
                } else {
                    R.string.route_detail_action_mark_flown
                },
            ),
        )
    }
}

/**
 * The entrance: each section rises and fades into place, one stagger step behind
 * the one above it.
 *
 * The stagger is the design system's, so this screen arrives at the same rhythm a
 * batch of route cards does. Under reduce motion everything is simply in place on
 * the first frame — switched off, not shortened.
 */
@Composable
private fun Modifier.enterStaggered(index: Int, enabled: Boolean): Modifier {
    val reduceMotion = LocalReduceMotion.current
    val off = reduceMotion || !enabled
    val spec = FlightMotion.spatial<Float>()
    val progress = remember { Animatable(if (off) 1f else 0f) }

    LaunchedEffect(off) {
        if (off) {
            progress.snapTo(1f)
            return@LaunchedEffect
        }
        delay(FlightMotion.enterDelayMillis(index).toLong())
        progress.animateTo(1f, spec)
    }

    // No layer at all when there is nothing to animate. At alpha 1 this is a
    // render node rather than an offscreen buffer — `CompositingStrategy.Auto`
    // only allocates one below full opacity — so the saving is a node and a draw
    // wrapper per block, not a saveLayer. Small, but free: the pane recomposes
    // these on every selection and none of them will ever move.
    if (off) return this

    return graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * EnterRise.toPx()
    }
}
/** What a figure reads as before its airport has been read. */
private const val EmptyFigure = "—"

/** Between facts on one line, as on the desktop's popup. */
private const val FactSeparator = " · "

/** Taller than a card's map: this one is the subject rather than a background. */
private val MapHeight = 220.dp

/** The hero in a short window — a phone in landscape, or a split-screen half. */
private val CompactMapHeight = 132.dp

/** The spine's gutter — the rail plus the space that keeps text off it. */
private val SpineGutter = 24.dp

/** Hairline. Any heavier and the rail competes with the content it is organising. */
private val RailWidth = 1.dp

private val MarkerRadius = 5.dp
private val MarkerStroke = 2.dp

/** Between one spine block and the next. Paid by the block, so the rail stays unbroken. */
private val SpineRowSpacing = 20.dp

/** How far a section rises into place. Enough to read as arriving, not as sliding. */
private val EnterRise = 12.dp

/** A floor, not a height: roughly what one decoded report will occupy. */
private val WeatherReservedHeight = 72.dp

