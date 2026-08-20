package com.github.daanbouwman.flightplanner.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.github.daanbouwman.flightplanner.R

/*
 * The one screen still waiting on its phase. Plan, Settings and Profile (whose
 * Logbook segment is real as of D1) are gone from here — Airports is gone for
 * good, dropped from the navigation bar entirely rather than replaced; Fleet
 * is what remains until Phase D builds it.
 */

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
