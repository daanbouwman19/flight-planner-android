package com.github.daanbouwman.flightplanner.ui.plan

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.daanbouwman.flightplanner.model.Airport
import com.github.daanbouwman.flightplanner.navigation.Destination
import com.github.daanbouwman.flightplanner.ui.chrome.LocalNavAnimatedVisibilityScope
import com.github.daanbouwman.flightplanner.ui.chrome.LocalSharedTransitionScope
import com.github.daanbouwman.flightplanner.ui.detail.RouteDetailPane
import com.github.daanbouwman.flightplanner.ui.detail.RouteDetailPaneViewModel
import kotlinx.coroutines.launch

/**
 * The Plan section, in however many panes the window has room for.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun PlanRoute(
    onOpenSettings: () -> Unit,
    onOpenAirports: () -> Unit,
    onOpenRoute: (RouteRow) -> Unit,
    onOpenAirport: (Airport) -> Unit,
    viewModel: PlanViewModel,
    modifier: Modifier = Modifier,
) {
    val twoPanes = calculatePaneScaffoldDirective(currentWindowAdaptiveInfoV2())
        .maxHorizontalPartitions > 1

    if (!twoPanes) {
        PlanScreen(
            onOpenRoute = onOpenRoute,
            onOpenSettings = onOpenSettings,
            onOpenAirports = onOpenAirports,
            viewModel = viewModel,
            modifier = modifier,
        )
        return
    }

    val navigator = rememberListDetailPaneScaffoldNavigator<String>()
    val paneViewModel: RouteDetailPaneViewModel = hiltViewModel()
    val detail by paneViewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val paneSnackbarHostState = remember { SnackbarHostState() }

    CompositionLocalProvider(
        LocalSharedTransitionScope provides null,
        LocalNavAnimatedVisibilityScope provides null,
    ) {
        ListDetailPaneScaffold(
            directive = navigator.scaffoldDirective,
            value = navigator.scaffoldValue,
            modifier = modifier,
            listPane = {
                AnimatedPane {
                    PlanScreen(
                        onOpenRoute = { row ->
                            val route = row.toDestination()
                            paneViewModel.select(route)
                            scope.launch {
                                navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, route.key())
                            }
                        },
                        onOpenSettings = onOpenSettings,
                        onOpenAirports = onOpenAirports,
                        viewModel = viewModel,
                    )
                }
            },
            detailPane = {
                AnimatedPane {
                    RouteDetailPane(
                        state = detail,
                        snackbarHostState = paneSnackbarHostState,
                        onMarkFlown = { route ->
                            viewModel.markFlown(
                                departureIcao = route.departureIcao,
                                destinationIcao = route.destinationIcao,
                                aircraftId = route.aircraftId,
                            )
                        },
                        onFlownConfirmed = paneViewModel::clear,
                        onOpenAirport = onOpenAirport,
                    )
                }
            },
        )
    }
}

private fun RouteRow.toDestination(): Destination.RouteDetail = Destination.RouteDetail(
    departureIcao = departure.icao,
    destinationIcao = destination.icao,
    aircraftId = aircraft.id,
    distanceNm = distanceNm,
)

private fun Destination.RouteDetail.key(): String =
    "$departureIcao>$destinationIcao@$aircraftId"
