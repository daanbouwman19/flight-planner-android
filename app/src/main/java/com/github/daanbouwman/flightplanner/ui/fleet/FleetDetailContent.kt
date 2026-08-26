package com.github.daanbouwman.flightplanner.ui.fleet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.daanbouwman.flightplanner.R
import com.github.daanbouwman.flightplanner.core.designsystem.components.EmptyState
import com.github.daanbouwman.flightplanner.core.designsystem.components.SkeletonCard
import com.github.daanbouwman.flightplanner.core.designsystem.motion.FlightMotion
import com.github.daanbouwman.flightplanner.core.designsystem.theme.asChartFigure
import com.github.daanbouwman.flightplanner.model.AircraftSpec
import com.github.daanbouwman.flightplanner.ui.distanceText
import com.github.daanbouwman.flightplanner.ui.lengthText
import com.github.daanbouwman.flightplanner.ui.speedText
import com.github.daanbouwman.flightplanner.ui.chrome.MaxContentWidth
import com.github.daanbouwman.flightplanner.ui.chrome.WideMaxContentWidth
import com.github.daanbouwman.flightplanner.ui.chrome.isCompactHeight
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Everything a Fleet detail says, with no opinion about where it is being
 * said.
 *
 * Takes no `NavController` and no scaffold, for the same reason
 * [com.github.daanbouwman.flightplanner.ui.detail.RouteDetailContent] does:
 * it has two hosts, [FleetDetailScreen] on a phone and [FleetDetailPane] on a
 * wide window, and only the chrome around it differs.
 *
 * ### The hero is the envelope, not a list of facts
 *
 * A route's detail page is built on a spine because the distance and the
 * estimate belong to the *leg between two places*, not to either place alone.
 * An airframe's equivalent is its envelope — range, cruise and the runway it
 * needs — three numbers that together say what this aircraft can fly, the
 * same way "3,010 NM · 6,900 ft" does one line at a time in Plan's own flight
 * strip. So they are the hero here: one surface, one name, three figures —
 * not three small chips lost in a page of plain text rows.
 */
@Composable
fun FleetDetailContent(
    state: FleetDetailUiState,
    onToggleFlown: () -> Unit,
    onSave: (rangeNm: Int, cruiseSpeedKt: Int, takeoffDistanceMeters: Int?) -> Unit,
    onGenerateRoutes: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.widthIn(max = if (isCompactHeight()) MaxContentWidth else WideMaxContentWidth),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        when {
            state.loading -> SkeletonCard()
            state.aircraft == null -> EmptyState(
                title = stringResource(R.string.fleet_detail_not_found_title),
                message = stringResource(R.string.fleet_detail_not_found_message),
            )
            else -> FleetDetailBody(
                aircraft = state.aircraft,
                onToggleFlown = onToggleFlown,
                onSave = onSave,
                onGenerateRoutes = onGenerateRoutes,
            )
        }
    }
}

@Composable
private fun FleetDetailBody(
    aircraft: AircraftSpec,
    onToggleFlown: () -> Unit,
    onSave: (rangeNm: Int, cruiseSpeedKt: Int, takeoffDistanceMeters: Int?) -> Unit,
    onGenerateRoutes: () -> Unit,
) {
    // Keyed on the id: switching to a different airframe in the two-pane
    // layout must close a stale edit sheet rather than let it save onto the
    // wrong aircraft.
    var showEditSheet by rememberSaveable(aircraft.id) { mutableStateOf(false) }

    EnvelopeHero(aircraft = aircraft, modifier = Modifier.fillMaxWidth())

    FlownRow(aircraft = aircraft, onToggleFlown = onToggleFlown)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onGenerateRoutes, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.fleet_detail_action_generate))
        }
        OutlinedButton(onClick = { showEditSheet = true }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.fleet_detail_action_edit))
        }
    }

    if (showEditSheet) {
        EditEnvelopeSheet(
            aircraft = aircraft,
            onSave = { rangeNm, cruiseSpeedKt, takeoffDistanceMeters ->
                onSave(rangeNm, cruiseSpeedKt, takeoffDistanceMeters)
                showEditSheet = false
            },
            onDismiss = { showEditSheet = false },
        )
    }
}

/**
 * Identity and envelope, as one surface — the equivalent weight
 * [com.github.daanbouwman.flightplanner.ui.detail.RouteDetailContent]'s hero
 * map carries for a route. `largeIncreased` matches that hero's own shape, so
 * the two detail screens read as the same family of page.
 */
@Composable
private fun EnvelopeHero(aircraft: AircraftSpec, modifier: Modifier = Modifier) {
    Surface(
        shape = MaterialTheme.shapes.largeIncreased,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "${aircraft.category} · ${aircraft.icaoCode}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = aircraft.displayName,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(20.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                val range = FlightMotion.rememberCountUp(aircraft.rangeNm)
                EnvelopeStat(
                    label = stringResource(R.string.fleet_detail_range),
                    value = distanceText(range),
                )
                EnvelopeStat(
                    label = stringResource(R.string.fleet_detail_cruise),
                    value = speedText(aircraft.cruiseSpeedKt),
                )
                EnvelopeStat(
                    label = stringResource(R.string.fleet_detail_takeoff),
                    value = lengthText(aircraft.requiredRunwayFt),
                )
            }
        }
    }
}

@Composable
private fun EnvelopeStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.Start) {
        Text(text = value, style = MaterialTheme.typography.headlineSmall.asChartFigure())
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Flown status, in the list's own vocabulary — the same [FilterChip] grammar
 * [FleetRowCard] uses, rather than a settings-style switch. One selection
 * language across the whole screen: filled and nothing else means "on".
 */
@Composable
private fun FlownRow(aircraft: AircraftSpec, onToggleFlown: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FilterChip(
            selected = aircraft.flown,
            onClick = onToggleFlown,
            label = {
                Text(
                    stringResource(
                        if (aircraft.flown) R.string.fleet_detail_flown else R.string.fleet_detail_not_flown,
                    ),
                )
            },
        )
        aircraft.dateFlown?.let { iso ->
            val formatted = runCatching {
                LocalDate.parse(iso).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG))
            }.getOrNull()
            if (formatted != null) {
                Text(
                    text = stringResource(R.string.fleet_detail_flown_on, formatted),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
