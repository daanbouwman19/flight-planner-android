package com.github.daanbouwman.flightplanner.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.daanbouwman.flightplanner.R
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

@Composable
private fun AirportHighlightRow(
    label: String,
    airport: AirportCount?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            Text(
                text = "—",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
