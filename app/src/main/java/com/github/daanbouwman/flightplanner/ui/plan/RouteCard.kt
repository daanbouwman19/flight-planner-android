package com.github.daanbouwman.flightplanner.ui.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.daanbouwman.flightplanner.R
import com.github.daanbouwman.flightplanner.core.designsystem.components.CompactWidthPreview
import com.github.daanbouwman.flightplanner.core.designsystem.components.FlightRulesBadge
import com.github.daanbouwman.flightplanner.core.designsystem.components.LightDarkPreview
import com.github.daanbouwman.flightplanner.core.designsystem.components.RouteSparkline
import com.github.daanbouwman.flightplanner.core.designsystem.components.ValueChip
import com.github.daanbouwman.flightplanner.core.designsystem.theme.FlightPlannerTheme
import com.github.daanbouwman.flightplanner.core.designsystem.theme.withTabularFigures
import com.github.daanbouwman.flightplanner.model.FlightRules

/**
 * One generated route.
 *
 * The layout is built around the fact that the eye scans a *column* of these:
 * the two ICAO codes sit at fixed positions on the left and right of a middle
 * band, so a finger-flick down the list reads as a list of routes rather than
 * as text that reflows per row. Every numeric uses tabular figures for the same
 * reason — a proportional `1` is narrower than a `0`, and in a column of
 * distances that shows up as the whole card twitching.
 *
 * ### Where the runway figure lives
 *
 * Under the airport it belongs to, not in the row of chips. Runway length is a
 * property of an *end*, not of a route, and "RWY 7,053 ft" in a chip row silently
 * meant the shorter of the two — a number you cannot act on, because it does not
 * say which end it describes. Split per end it answers the question a pilot
 * actually has, and the end that is too short for the airframe can say so in
 * place. It also empties the third chip slot, which is what left a half-width
 * chip stranded on a line of its own.
 *
 * ### The flight-rules slot
 *
 * Weather arrives in Phase F. Until then the slot is *reserved but empty* rather
 * than filled with a grey "N/A" pill: the height is held so that a resolved chip
 * fades in later without moving the codes, and nothing is drawn in the meantime,
 * because two placeholder pills per card is a lot of ink to spend on the absence
 * of information.
 */
@Composable
fun RouteCard(
    row: RouteRow,
    onClick: () -> Unit,
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

    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = description },
        shape = MaterialTheme.shapes.largeIncreased,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AircraftLine(row)
            AirportLine(row)
            FactLine(row)
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
            modifier = Modifier.weight(1f),
        )
        Text(
            text = row.aircraft.category,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The two ends and the arc between them.
 *
 * The airport *names* are deliberately not here. They were, and on a 360 dp
 * window — which is what a 1080 x 2340 phone at 480 dpi actually is — every one
 * of them truncated to "Stangland A...", which is worse than absent: it occupies
 * the width the sparkline needs in order to say nothing. B2 specifies the code
 * pair, and the name belongs to the detail screen, where there is room to read
 * it.
 */
@Composable
private fun AirportLine(row: RouteRow) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AirportEnd(
            icao = row.departure.icao,
            runwayFt = row.departureRunwayFt,
            runwayTooShort = row.departureRunwayTooShort,
            alignment = Alignment.Start,
        )
        RouteSparkline(
            points = row.arc,
            modifier = Modifier
                .weight(1f)
                .height(40.dp),
        )
        AirportEnd(
            icao = row.destination.icao,
            runwayFt = row.destinationRunwayFt,
            runwayTooShort = row.destinationRunwayTooShort,
            alignment = Alignment.End,
        )
    }
}

@Composable
private fun AirportEnd(
    icao: String,
    runwayFt: Int,
    runwayTooShort: Boolean,
    alignment: Alignment.Horizontal,
) {
    val tooShortLabel = stringResource(R.string.plan_runway_short)
    Column(
        horizontalAlignment = alignment,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = icao,
            style = MaterialTheme.typography.headlineSmall.withTabularFigures(),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
        Text(
            text = stringResource(R.string.plan_runway_value, runwayFt),
            style = MaterialTheme.typography.labelSmall.withTabularFigures(),
            // Colour carries the warning rather than a separate icon, so the
            // figure and the judgement of it are the same glyphs. The
            // description says it in words for anyone the colour does not reach.
            color = if (runwayTooShort) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
            modifier = Modifier.semantics {
                if (runwayTooShort) contentDescription = tooShortLabel
            },
        )
        FlightRulesSlot(rules = FlightRules.UNKNOWN)
    }
}

/**
 * Holds the flight category's space whether or not there is one to show.
 *
 * Phase F resolves these from a METAR. The height is reserved now so that the
 * chip can fade in then without shifting the codes above it — F6's "never a
 * layout jump" is only free if the space was there from the start.
 */
@Composable
private fun FlightRulesSlot(rules: FlightRules) {
    Box(
        modifier = Modifier.heightIn(min = FlightRulesSlotHeight),
        contentAlignment = Alignment.Center,
    ) {
        if (rules != FlightRules.UNKNOWN) FlightRulesBadge(rules = rules)
    }
}

/**
 * Distance and time, in two equal columns.
 *
 * Equal columns rather than content-sized chips: content-sized ones give every
 * card a different ragged edge, so scrolling a list of them is a column of
 * shapes that never line up. Fixing the columns means DIST always starts where
 * DIST started on the row above, and the eye can run down one figure without
 * re-finding it — the same reason the numerals are tabular.
 *
 * Two of them, now that the runway has moved to the airport ends, and two is
 * what fits: three equal columns give each chip about 93 dp on a 360 dp phone,
 * and "DIST 1,919 NM" needs more than that.
 */
@Composable
private fun FactLine(row: RouteRow) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ValueChip(
            label = stringResource(R.string.plan_chip_distance),
            value = stringResource(R.string.plan_value_nautical_miles, row.distanceNm),
            modifier = Modifier.weight(1f),
        )
        ValueChip(
            label = stringResource(R.string.plan_chip_time),
            value = row.flightTime.format(),
            modifier = Modifier.weight(1f),
        )
    }
}

/** Tall enough for a flight-rules chip, so reserving it costs no later reflow. */
private val FlightRulesSlotHeight = 24.dp

@LightDarkPreview
@CompactWidthPreview
@Composable
private fun RouteCardPreview() {
    FlightPlannerTheme(dynamicColor = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Two shapes side by side: a long haul that bows north and a
                // shorter westbound leg. If the sparkline ever stops reflecting
                // its input, these two stop differing and the preview says so.
                RouteCard(row = PlanPreviewData.longHaul, onClick = {})
                RouteCard(row = PlanPreviewData.transatlantic, onClick = {})
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
                onClick = {},
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}
