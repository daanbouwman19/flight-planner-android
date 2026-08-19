package com.github.daanbouwman.flightplanner.ui.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.daanbouwman.flightplanner.ui.asFigure
import com.github.daanbouwman.flightplanner.ui.chrome.SharedRouteKeys
import com.github.daanbouwman.flightplanner.ui.chrome.isCompactHeight
import com.github.daanbouwman.flightplanner.ui.chrome.sharedRouteElement
import com.github.daanbouwman.flightplanner.R
import com.github.daanbouwman.flightplanner.core.designsystem.components.CompactWidthPreview
import com.github.daanbouwman.flightplanner.core.designsystem.components.FlightRulesBadge
import com.github.daanbouwman.flightplanner.core.designsystem.components.LightDarkPreview
import com.github.daanbouwman.flightplanner.core.designsystem.components.RouteMap
import com.github.daanbouwman.flightplanner.core.designsystem.components.ValueChip
import com.github.daanbouwman.flightplanner.core.designsystem.theme.FlightPlannerTheme
import com.github.daanbouwman.flightplanner.core.designsystem.theme.asChartFigure
import com.github.daanbouwman.flightplanner.core.designsystem.theme.withTabularFigures
import com.github.daanbouwman.flightplanner.model.FlightRules
import com.github.daanbouwman.flightplanner.routing.WorldOutline

/**
 * One generated route, drawn on the piece of the world it crosses.
 *
 * The card used to be three stacked rows with a 120 dp sparkline between the two
 * codes. The sparkline proved a route had a *shape*; it could not say where that
 * shape was, so an arc over the Pacific and one over the Atlantic drew
 * identically. Now the map is the card's background, full bleed, and everything
 * the card says is drawn over it — which is the difference between a curve and a
 * place, and most of what a route card is for.
 *
 * ### The map is a background, so the content has to stay foreground
 *
 * Land is 8 % of `onSurface` and its coast 16 %, which is faint enough that text
 * over it keeps essentially its full contrast — that is what makes a scrim
 * unnecessary, and a design that needs no scrim is simpler than one hiding behind
 * a gradient. The chips are the one translucent thing on the card, at 70 %, so
 * the coastline passes faintly behind their figures and the content reads as a
 * layer over the map rather than a panel bolted to it. Text itself is never
 * translucent: a figure at 70 % is just a figure that is harder to read.
 *
 * ### Content is bottom-weighted
 *
 * The airframe is an eyebrow at the top, the codes and their runways sit on the
 * card's lower third, and the figures close it. That leaves the upper half of the
 * card to the map, which is where a route's shape and the coast around it are
 * legible; packing the content into a vertical centre band would have left the
 * map showing only at the edges, where it says nothing.
 *
 * ### The height is a minimum, not a fixed size
 *
 * 180 dp is what makes about two cards fill a 360 × 780 dp phone: at 150 dp a
 * long route shows a band of ocean and little else, and at 220 dp the list drops
 * to a card and a half per screen, which is slow to scan. It is a floor rather
 * than a height because at font scale 2.0 the content is taller than that, and a
 * card that clips its own runway figures to protect a map is the wrong way round.
 */
@Composable
fun RouteCard(
    row: RouteRow,
    outline: WorldOutline,
    onClick: () -> Unit,
    onMarkFlown: () -> Unit,
    onReplace: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // One description for the whole card. Left to itself the card announces
    // eleven separate nodes — two codes, two names, two "N/A" chips and three
    // labelled figures — which is technically complete and unusable. Merging
    // them into a sentence is the difference between a list a screen-reader user
    // can skim and one they have to decode.
    val description = stringResource(
        R.string.plan_route_content_description,
        row.aircraft.displayName,
        row.departure.icao,
        row.departure.name,
        row.destination.icao,
        row.destination.name,
        row.distanceNm,
        row.flightTime.hours,
        row.flightTime.minutes,
    )

    // The two actions exist as swipes, and a swipe is not an action a screen
    // reader can perform. Declared here they become entries in TalkBack's actions
    // menu, on the node that already carries the row's description — so the
    // gesture stays exactly as it is and the same two actions become reachable
    // without it.
    val markFlownLabel = stringResource(R.string.plan_swipe_mark_flown)
    val replaceLabel = stringResource(R.string.plan_swipe_replace)

    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = description
                customActions = listOf(
                    CustomAccessibilityAction(markFlownLabel) { onMarkFlown(); true },
                    CustomAccessibilityAction(replaceLabel) { onReplace(); true },
                )
            },
        shape = MaterialTheme.shapes.largeIncreased,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        // Shorter when the window is: 180 dp of card in a 360 dp-tall landscape
        // window is half the screen for one row, and the map is the part that can
        // afford to lose height — it stays a recognisable place at 132 dp, where
        // the figures below it would not survive being squeezed at all.
        val cardHeight = if (isCompactHeight()) CompactCardHeight else CardHeight
        // `propagateMinConstraints`, without which the floor below is unreachable and
        // the weighted spacer is dead code. A `Box` relaxes its children's minimum
        // constraints by default, and a lazy list gives its items an unbounded
        // maximum height — so the content Column measured against `0..Infinity`,
        // where `fillMaxSize` does nothing and a weight resolves to zero space.
        // The card looked right anyway, because its content is 197 dp and the floor
        // is 180: there was never any slack to distribute. Measured with
        // `uiautomator dump` — raising the floor to 320 dp moved nothing until this
        // flag was set, and then put the whole 123 dp where the KDoc says it goes.
        Box(
            modifier = Modifier.fillMaxWidth().heightIn(min = cardHeight),
            propagateMinConstraints = true,
        ) {
            // `matchParentSize`, not `fillMaxSize`. The map has no content of its
            // own, so a fill modifier measures it against the constraints it is
            // given — which in a lazy list are unbounded vertically — and it
            // resolves to nothing at all. `matchParentSize` takes no part in
            // sizing the card and adopts whatever height the content settled on,
            // which is what a background is.
            // The map is what the detail screen's hero *is*, so it travels rather
            // than cross-fading — it is the element that makes the two screens
            // read as one thing at two sizes.
            //
            // The key is on a wrapper rather than on `RouteMap` itself, and the
            // wrapper is what carries `matchParentSize`. That modifier is parent
            // data — it tells this Box to measure the child at the card's own size
            // once the card has been sized — and a shared element wants to impose
            // the bounds it has animated to. Separated, each does one job, and this
            // side mirrors the detail's hero exactly: a container that holds the
            // bounds, a map that fills it.
            //
            // The overspill this used to paint mid-flight was `RouteMap`'s, not
            // this modifier's: the component drew past its own bounds and let the
            // `Card` crop it, and a shared element is rendered in an overlay where
            // no ancestor clip applies. It crops itself now.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .sharedRouteElement(
                        SharedRouteKeys.map(row.departure.icao, row.destination.icao, row.aircraft.id),
                        remeasure = false,
                    ),
            ) {
                RouteMap(
                    arc = row.arc,
                    outline = outline,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AircraftLine(row)
                // The gap between the eyebrow and the codes is the map's, and it
                // takes whatever height the card has left over.
                Box(modifier = Modifier.weight(1f))
                AirportLine(row)
                FactLine(row)
            }
        }
    }
}

@Composable
private fun AircraftLine(row: RouteRow) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = row.aircraft.displayName,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .sharedRouteElement(
                    SharedRouteKeys.aircraft(row.departure.icao, row.destination.icao, row.aircraft.id),
                ),
        )
        Text(
            text = row.aircraft.category,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The two ends, at the two edges.
 *
 * Anchoring the codes to their endpoints on the map was considered and rejected:
 * it looks right in a single mockup and collides unpredictably in a list, because
 * the endpoints land somewhere different on every card. At the edges they are in
 * the same two places on every row, which is what makes a column of cards
 * scannable — and the map already says which end is which, with a hollow ring for
 * the departure and a filled dot for the destination.
 *
 * The airport *names* are deliberately absent. They were here, and on a 360 dp
 * window — which is what a 1080 × 2340 phone at 480 dpi actually is — every one of
 * them truncated to "Stangland A...", which is worse than absent. The name
 * belongs to the detail screen, where there is room to read it.
 */
@Composable
private fun AirportLine(row: RouteRow) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        AirportEnd(
            icao = row.departure.icao,
            runwayFt = row.departureRunwayFt,
            runwayTooShort = row.departureRunwayTooShort,
            alignment = Alignment.Start,
            textAlign = TextAlign.Start,
            sharedKey = SharedRouteKeys.departure(
                row.departure.icao,
                row.destination.icao,
                row.aircraft.id,
            ),
        )
        Box(modifier = Modifier.weight(1f))
        AirportEnd(
            icao = row.destination.icao,
            runwayFt = row.destinationRunwayFt,
            runwayTooShort = row.destinationRunwayTooShort,
            alignment = Alignment.End,
            textAlign = TextAlign.End,
            sharedKey = SharedRouteKeys.destination(
                row.departure.icao,
                row.destination.icao,
                row.aircraft.id,
            ),
        )
    }
}

@Composable
private fun AirportEnd(
    icao: String,
    runwayFt: Int,
    runwayTooShort: Boolean,
    alignment: Alignment.Horizontal,
    textAlign: TextAlign,
    sharedKey: String,
) {
    val tooShortLabel = stringResource(R.string.plan_runway_short)
    Column(
        horizontalAlignment = alignment,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        FlightRulesSlot(rules = FlightRules.UNKNOWN, alignment = alignment)
        Text(
            text = icao,
            style = MaterialTheme.typography.headlineSmall.withTabularFigures(),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            textAlign = textAlign,
            modifier = Modifier.sharedRouteElement(sharedKey),
        )
        Text(
            text = stringResource(R.string.plan_runway_value, runwayFt.asFigure()),
            style = MaterialTheme.typography.labelSmall.asChartFigure(),
            // Colour carries the warning rather than a separate icon, so the
            // figure and the judgement of it are the same glyphs. The
            // description says it in words for anyone the colour does not reach.
            color = if (runwayTooShort) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
            textAlign = textAlign,
            modifier = Modifier.semantics {
                if (runwayTooShort) contentDescription = tooShortLabel
            },
        )
    }
}

/**
 * Holds the flight category's space whether or not there is one to show.
 *
 * Above the code rather than below the runway figure, which is where it used to
 * sit. Weather is a property of the *airport*, so it belongs with the code, and
 * putting it above means the two things that grow later — a badge here and a
 * METAR line in the detail view — grow away from the figures rather than pushing
 * them into the chips.
 *
 * Phase F resolves these. The height is reserved now so the badge can fade in
 * then without shifting anything: F6's "never a layout jump" is only free if the
 * space was there from the start. Nothing is drawn in the meantime, because two
 * placeholder pills per card is a lot of ink to spend on the absence of
 * information.
 */
@Composable
private fun FlightRulesSlot(rules: FlightRules, alignment: Alignment.Horizontal) {
    Box(
        modifier = Modifier.defaultMinSize(minHeight = FlightRulesSlotHeight),
        contentAlignment = if (alignment == Alignment.Start) Alignment.CenterStart else Alignment.CenterEnd,
    ) {
        if (rules != FlightRules.UNKNOWN) FlightRulesBadge(rules = rules)
    }
}

/**
 * Distance and time, in two equal columns.
 *
 * Equal columns rather than content-sized chips: content-sized ones give every
 * card a different ragged edge, so scrolling a list of them is a column of shapes
 * that never line up. Fixing the columns means DIST always starts where DIST
 * started on the row above, and the eye can run down one figure without
 * re-finding it — the same reason the numerals are tabular.
 *
 * They stop at two thirds of the card rather than filling it. A pair of chips
 * spanning the full width would wall the map off along a straight horizontal
 * line, and the coast has to be able to run past them for the card to read as one
 * surface with a map on it rather than as a map with a panel over it.
 */
@Composable
private fun FactLine(row: RouteRow) {
    val distance = stringResource(R.string.plan_chip_distance) to
        stringResource(R.string.plan_value_nautical_miles, row.distanceNm.asFigure())
    val time = stringResource(R.string.plan_chip_time) to row.flightTime.format()

    // Past a certain size two chips cannot share a phone's width at all: at scale
    // 2.0 "DIST 5,111 NM" becomes a ribbon of fragments, and the figure the card
    // exists to state is the one thing that must stay one line. Stacked, each chip
    // has the whole width and reads normally. The threshold is where the wrap
    // starts on the narrowest window the app supports, not a round number.
    val stacked = LocalDensity.current.fontScale >= StackChipsAtFontScale
    if (stacked) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ValueChip(distance.first, distance.second, Modifier.fillMaxWidth(), ChipContainerAlpha)
            ValueChip(time.first, time.second, Modifier.fillMaxWidth(), ChipContainerAlpha)
        }
        return
    }

    // Two equal columns across the full width. An earlier version left a third of
    // the row empty so the map ran out from under the chips rather than ending on
    // a straight edge; on a 360 dp phone at the *default* font scale that left
    // 122 dp a chip, and "DIST 2,847 NM" wrapped to two lines inside it. The
    // chips are translucent, so the coast passes behind them anyway — the gap was
    // buying an effect the alpha already provides, and paying for it in the one
    // thing on the card that must not wrap.
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ValueChip(distance.first, distance.second, Modifier.weight(1f), ChipContainerAlpha)
        ValueChip(time.first, time.second, Modifier.weight(1f), ChipContainerAlpha)
    }
}

/**
 * Tall enough for a flight-rules chip, so reserving it costs no later reflow.
 */
private val FlightRulesSlotHeight = 24.dp

/** See the class KDoc: a floor, not a fixed height. */
private val CardHeight = 180.dp

/** The floor in a short window — a phone in landscape, or a split-screen half. */
private val CompactCardHeight = 132.dp

/** Enough container to hold a figure, little enough to let a coastline through. */
private const val ChipContainerAlpha = 0.7f

/**
 * Font scale at which the two figure chips stop sharing a line.
 *
 * Measured rather than chosen: with the chips across the full width of a 360 dp
 * window, the longest figure the app produces — "DIST 5,111 NM" — fits up to 1.2
 * and wraps at 1.3.
 */
private const val StackChipsAtFontScale = 1.3f

@LightDarkPreview
@CompactWidthPreview
@Composable
private fun RouteCardPreview() {
    FlightPlannerTheme(dynamicColor = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            val outline = rememberPreviewWorldOutline()
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Two places side by side: a long haul over Siberia and a
                // transatlantic leg. If the projection ever stops following its
                // input, these two stop differing and the preview says so.
                RouteCard(PlanPreviewData.longHaul, outline, onClick = {}, onMarkFlown = {}, onReplace = {})
                RouteCard(PlanPreviewData.transatlantic, outline, onClick = {}, onMarkFlown = {}, onReplace = {})
            }
        }
    }
}

/** The one state that never appears while clicking around: a runway too short for the airframe. */
@LightDarkPreview
@Composable
private fun RouteCardRunwayTooShortPreview() {
    FlightPlannerTheme(dynamicColor = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            RouteCard(
                row = PlanPreviewData.runwayTooShort,
                outline = rememberPreviewWorldOutline(),
                onClick = {},
                onMarkFlown = {},
                onReplace = {},
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}
