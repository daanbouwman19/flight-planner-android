package com.github.daanbouwman.flightplanner.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.daanbouwman.flightplanner.core.designsystem.theme.withTabularFigures
import com.github.daanbouwman.flightplanner.model.Airport

/**
 * One airport, as a pickable row: ICAO in tabular figures, name/municipality/
 * country beneath it, longest runway trailing.
 *
 * Shared between the departure/destination picker ([com.github.daanbouwman.flightplanner.ui.picker.PlanPickerSheet])
 * and the Airports browse screen — both rank the same [Airport] list the same
 * way, so they show it the same way. `internal`, not `private`: this is the
 * one place either screen needs, promoted out of the picker rather than
 * duplicated.
 */
@Composable
internal fun AirportRow(airport: Airport, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ListItem(
        modifier = modifier.clickable(onClick = onClick),
        supportingContent = {
            Text(
                text = listOfNotNull(airport.name, airport.municipality, airport.country)
                    .joinToString(" · "),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            Text(
                text = lengthText(airport.longestRunwayFt),
                style = MaterialTheme.typography.labelMedium.withTabularFigures(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    ) {
        Text(
            text = airport.icao,
            style = MaterialTheme.typography.titleMedium.withTabularFigures(),
        )
    }
}

/** A label over a group of rows below it — "Largest airports", "Random 50". */
@Composable
internal fun SuggestionsHeader(label: String, modifier: Modifier = Modifier) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}
