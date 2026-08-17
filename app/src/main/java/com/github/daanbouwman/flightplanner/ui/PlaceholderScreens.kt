package com.github.daanbouwman.flightplanner.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.github.daanbouwman.flightplanner.R
import com.github.daanbouwman.flightplanner.navigation.Destination

/*
 * Placeholders, one per section, each the real destination the navigation graph
 * routes to. They exist so the shell can be navigated, measured and screenshot
 * before any screen is built; phases B–E replace the bodies without touching the
 * graph.
 */

@Composable
fun PlanScreen(
    onOpenSampleRoute: (Destination.RouteDetail) -> Unit,
    modifier: Modifier = Modifier,
) {
    PlaceholderScaffold(
        title = stringResource(R.string.destination_plan),
        emptyTitle = stringResource(R.string.plan_empty_title),
        // The desktop app's wording, kept deliberately.
        emptyMessage = stringResource(R.string.plan_empty_message),
        modifier = modifier,
        actionLabel = stringResource(R.string.plan_empty_action),
        onAction = { onOpenSampleRoute(SAMPLE_ROUTE) },
    )
}

@Composable
fun LogbookScreen(modifier: Modifier = Modifier) {
    PlaceholderScaffold(
        title = stringResource(R.string.destination_logbook),
        emptyTitle = stringResource(R.string.logbook_empty_title),
        emptyMessage = stringResource(R.string.logbook_empty_message),
        modifier = modifier,
    )
}

@Composable
fun FleetScreen(modifier: Modifier = Modifier) {
    PlaceholderScaffold(
        title = stringResource(R.string.destination_fleet),
        emptyTitle = stringResource(R.string.fleet_empty_title),
        emptyMessage = stringResource(R.string.fleet_empty_message),
        modifier = modifier,
    )
}

@Composable
fun AirportsScreen(modifier: Modifier = Modifier) {
    PlaceholderScaffold(
        title = stringResource(R.string.destination_airports),
        emptyTitle = stringResource(R.string.airports_empty_title),
        emptyMessage = stringResource(R.string.airports_empty_message),
        modifier = modifier,
    )
}

@Composable
fun StatsScreen(modifier: Modifier = Modifier) {
    PlaceholderScaffold(
        title = stringResource(R.string.destination_stats),
        emptyTitle = stringResource(R.string.stats_empty_title),
        emptyMessage = stringResource(R.string.stats_empty_message),
        modifier = modifier,
    )
}

@Composable
fun SettingsScreen(
    onOpenSelfCheck: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PlaceholderScaffold(
        title = stringResource(R.string.destination_settings),
        emptyTitle = stringResource(R.string.settings_empty_title),
        emptyMessage = stringResource(R.string.settings_empty_message),
        modifier = modifier,
        actionLabel = stringResource(R.string.settings_self_check_action),
        onAction = onOpenSelfCheck,
    )
}

/**
 * A real pair of ICAO codes rather than lorem ipsum, so the detail placeholder
 * exercises argument passing — including the process-death path — instead of
 * only proving that a screen opens.
 */
private val SAMPLE_ROUTE = Destination.RouteDetail(
    departureIcao = "EHAM",
    destinationIcao = "KJFK",
    aircraftId = 0,
    distanceNm = 3158,
)
