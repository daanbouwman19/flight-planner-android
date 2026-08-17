package com.github.daanbouwman.flightplanner.ui.plan

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.daanbouwman.flightplanner.R
import com.github.daanbouwman.flightplanner.core.designsystem.components.CompactWidthPreview
import com.github.daanbouwman.flightplanner.core.designsystem.components.LightDarkPreview
import com.github.daanbouwman.flightplanner.core.designsystem.theme.FlightPlannerTheme
import com.github.daanbouwman.flightplanner.model.AircraftSpec
import com.github.daanbouwman.flightplanner.model.Airport

/**
 * The two filters that replace the desktop application's 250-pixel sidebar.
 *
 * Each chip states its current value rather than its name — "EHAM", not
 * "Departure: EHAM" — because the value is what the user is scanning for and
 * the leading icon already says which filter it is. Unset, the chip states the
 * default in words ("Any departure"), which is both the label and the
 * explanation of what tapping it will change.
 *
 * The two chips split the width evenly and fill the row. Sized to their content
 * they left a ragged right edge under a mode selector that spans the whole
 * width, so the block above the list read as two unrelated things rather than as
 * one set of controls. Equal halves line their outer edges up with the selector
 * and with each other, and the pair grows and shrinks as one.
 */
@Composable
fun PlanFilterChips(
    lockedDeparture: Airport?,
    selectedAircraft: AircraftSpec?,
    onPickDeparture: () -> Unit,
    onPickAircraft: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            modifier = Modifier.weight(1f),
            selected = lockedDeparture != null,
            onClick = onPickDeparture,
            label = {
                Text(
                    text = lockedDeparture?.icao ?: stringResource(R.string.plan_filter_any_departure),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            leadingIcon = {
                Icon(
                    painter = painterResource(R.drawable.ic_plan_departure),
                    contentDescription = stringResource(R.string.plan_filter_departure_label),
                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                )
            },
        )
        FilterChip(
            modifier = Modifier.weight(1f),
            selected = selectedAircraft != null,
            onClick = onPickAircraft,
            label = {
                Text(
                    text = selectedAircraft?.displayName ?: stringResource(R.string.plan_filter_any_aircraft),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            leadingIcon = {
                Icon(
                    painter = painterResource(R.drawable.ic_plan_aircraft),
                    contentDescription = stringResource(R.string.plan_filter_aircraft_label),
                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                )
            },
        )
    }
}

@LightDarkPreview
@CompactWidthPreview
@Composable
private fun PlanFilterChipsPreview() {
    FlightPlannerTheme(dynamicColor = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Unset and set, because the set state carries an aircraft name
                // long enough to be the thing that overflows.
                PlanFilterChips(
                    lockedDeparture = null,
                    selectedAircraft = null,
                    onPickDeparture = {},
                    onPickAircraft = {},
                )
                PlanFilterChips(
                    lockedDeparture = PlanPreviewData.schiphol,
                    selectedAircraft = PlanPreviewData.boeing,
                    onPickDeparture = {},
                    onPickAircraft = {},
                )
            }
        }
    }
}
