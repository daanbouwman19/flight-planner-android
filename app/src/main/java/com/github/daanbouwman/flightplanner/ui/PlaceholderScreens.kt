package com.github.daanbouwman.flightplanner.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.github.daanbouwman.flightplanner.R

/*
 * Placeholders, one per not-yet-built section, each the real destination the
 * navigation graph routes to. They exist so the shell can be navigated, measured
 * and screenshot before the screen is built; phases D–E replace the bodies
 * without touching the graph.
 *
 * Plan and Settings are gone from here — both are real screens now, in the
 * `plan` and `ui.settings` packages.
 */

@Composable
fun LogbookScreen(onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    PlaceholderScaffold(
        title = stringResource(R.string.destination_logbook),
        emptyTitle = stringResource(R.string.logbook_empty_title),
        emptyMessage = stringResource(R.string.logbook_empty_message),
        modifier = modifier,
        onOpenSettings = onOpenSettings,
    )
}

@Composable
fun FleetScreen(onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    PlaceholderScaffold(
        title = stringResource(R.string.destination_fleet),
        emptyTitle = stringResource(R.string.fleet_empty_title),
        emptyMessage = stringResource(R.string.fleet_empty_message),
        modifier = modifier,
        onOpenSettings = onOpenSettings,
    )
}

@Composable
fun AirportsScreen(onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    PlaceholderScaffold(
        title = stringResource(R.string.destination_airports),
        emptyTitle = stringResource(R.string.airports_empty_title),
        emptyMessage = stringResource(R.string.airports_empty_message),
        modifier = modifier,
        onOpenSettings = onOpenSettings,
    )
}

@Composable
fun StatsScreen(onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    PlaceholderScaffold(
        title = stringResource(R.string.destination_stats),
        emptyTitle = stringResource(R.string.stats_empty_title),
        emptyMessage = stringResource(R.string.stats_empty_message),
        modifier = modifier,
        onOpenSettings = onOpenSettings,
    )
}
