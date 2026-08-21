package com.github.daanbouwman.flightplanner.ui.fleet

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.daanbouwman.flightplanner.model.AircraftSpec
import kotlinx.coroutines.launch

/**
 * The Fleet section, in however many panes the window has room for.
 *
 * Mirrors [com.github.daanbouwman.flightplanner.ui.logbook.LogbookRoute]'s
 * shape: below the two-pane breakpoint it is just the list, navigating away on
 * a tap; above it, the list and [FleetDetailPane] share a
 * `ListDetailPaneScaffold`, with [FleetDetailPaneViewModel] holding the
 * selection.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun FleetRoute(
    onOpenSettings: () -> Unit,
    onOpenAircraft: (AircraftSpec) -> Unit,
    onGenerateRoutes: (AircraftSpec) -> Unit,
    modifier: Modifier = Modifier,
) {
    val twoPanes = calculatePaneScaffoldDirective(currentWindowAdaptiveInfoV2())
        .maxHorizontalPartitions > 1

    if (!twoPanes) {
        FleetScreen(
            onOpenAircraft = onOpenAircraft,
            onOpenSettings = onOpenSettings,
            modifier = modifier,
        )
        return
    }

    val navigator = rememberListDetailPaneScaffoldNavigator<String>()
    val paneViewModel: FleetDetailPaneViewModel = hiltViewModel()
    val listViewModel: FleetViewModel = hiltViewModel()
    val detail by paneViewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    ListDetailPaneScaffold(
        directive = navigator.scaffoldDirective,
        value = navigator.scaffoldValue,
        modifier = modifier,
        listPane = {
            AnimatedPane {
                FleetScreen(
                    onOpenAircraft = { aircraft ->
                        paneViewModel.select(aircraft)
                        scope.launch {
                            navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, aircraft.id.toString())
                        }
                    },
                    onOpenSettings = onOpenSettings,
                    viewModel = listViewModel,
                )
            }
        },
        detailPane = {
            AnimatedPane {
                FleetDetailPane(
                    state = detail,
                    onToggleFlown = listViewModel::toggleFlown,
                    onSave = { aircraft, rangeNm, cruiseSpeedKt, takeoffDistanceMeters ->
                        listViewModel.updateAircraft(
                            aircraft.copy(
                                rangeNm = rangeNm,
                                cruiseSpeedKt = cruiseSpeedKt,
                                takeoffDistanceMeters = takeoffDistanceMeters,
                            ),
                        )
                    },
                    onGenerateRoutes = onGenerateRoutes,
                )
            }
        },
    )
}
