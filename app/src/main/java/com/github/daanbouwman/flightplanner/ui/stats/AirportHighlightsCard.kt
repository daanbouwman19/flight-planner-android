package com.github.daanbouwman.flightplanner.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.daanbouwman.flightplanner.R
import com.github.daanbouwman.flightplanner.core.designsystem.components.CompactWidthPreview
import com.github.daanbouwman.flightplanner.core.designsystem.components.LightDarkPreview
import com.github.daanbouwman.flightplanner.core.designsystem.theme.FlightPlannerTheme
import com.github.daanbouwman.flightplanner.core.designsystem.theme.asChartFigure

/**
 * Highlights for favorite departure, arrival, and most visited airports.
 */
@Composable
fun AirportHighlightsCard(
    favoriteDeparture: AirportCount?,
    favoriteArrival: AirportCount?,
    mostVisitedAirport: AirportCount?,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.stats_section_airport_highlights),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.2.sp,
            )

            AirportHighlightRow(
                label = stringResource(R.string.stats_favorite_departure),
                airport = favoriteDeparture,
            )
            AirportHighlightRow(
                label = stringResource(R.string.stats_favorite_arrival),
                airport = favoriteArrival,
            )
            AirportHighlightRow(
                label = stringResource(R.string.stats_most_visited_airport),
                airport = mostVisitedAirport,
            )
        }
    }
}

/**
 * One label-left, value-right row that a long airport name cannot break.
 *
 * `SpaceBetween` with two unconstrained texts was the defect: the value
 * measured at its full width ("Riga International Airport · 1" is wider than
 * the card), took every pixel, and the label was laid out flush against it
 * with the gap gone and the name ellipsised — "Favorite departureRiga
 * International Airport …". The label now keeps its own width (a weight that
 * does not fill), the value takes what is left after a fixed gutter, and a
 * name that still does not fit wraps to a second line rather than losing its
 * tail, because the tail of an airport name is often the part that says which
 * one it is.
 */
@Composable
private fun AirportHighlightRow(
    label: String,
    airport: AirportCount?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.width(LabelValueGap))
        if (airport != null) {
            Text(
                text = stringResource(
                    R.string.stats_airport_count_format,
                    airport.name ?: airport.icao,
                    airport.count,
                ),
                style = MaterialTheme.typography.bodyMedium.asChartFigure(),
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.End,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        } else {
            Text(
                text = "—",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** The least that may separate a label from its value. */
private val LabelValueGap = 12.dp

@LightDarkPreview
@CompactWidthPreview
@Composable
private fun AirportHighlightsCardPreview() {
    FlightPlannerTheme(dynamicColor = false) {
        AirportHighlightsCard(
            // The phone's own fixture, which is what broke the row.
            favoriteDeparture = AirportCount("EVRA", "Riga International Airport", 1),
            favoriteArrival = AirportCount("EHTW", "Twente Airport", 1),
            mostVisitedAirport = null,
            modifier = Modifier.padding(16.dp),
        )
    }
}
