package com.github.daanbouwman.flightplanner.ui.stats

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.daanbouwman.flightplanner.model.AircraftSpec

/**
 * Route composable hosting the Stats Dashboard.
 */
@Composable
fun StatsRoute(
    onOpenSettings: () -> Unit,
    onOpenRoute: (LegStat) -> Unit,
    onOpenAircraft: (AircraftSpec) -> Unit,
    onPlanFlight: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: StatsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val worldOutline by viewModel.worldOutline.collectAsStateWithLifecycle()

    StatsScreen(
        uiState = uiState,
        onSelectTimeframe = viewModel::setTimeframe,
        onSelectMetric = viewModel::setChartMetric,
        onOpenSettings = onOpenSettings,
        onOpenRoute = onOpenRoute,
        onOpenAircraft = onOpenAircraft,
        onPlanFlight = onPlanFlight,
        worldOutline = worldOutline,
        modifier = modifier,
    )
}

