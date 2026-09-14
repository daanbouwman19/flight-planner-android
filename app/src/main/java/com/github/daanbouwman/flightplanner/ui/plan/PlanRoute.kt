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
import androidx.compose.runtime.LaunchedEffect
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
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val paneSnackbarHostState = remember { SnackbarHostState() }

    // The selection lives in two places with two lifetimes, and this keeps them
    // agreeing. The navigator's content key is saveable, so it comes back after
    // process death; the pane ViewModel's state does not, so a restored key used
    // to sit over an empty pane until the user tapped something. And the list
    // regenerates under the pane on every mode, departure or airframe change,
    // so a pane could keep showing a route that no longer existed in the batch
    // beside it — marking it flown would then fail with "no longer in the list"
    // for a route the user was looking at. Re-selecting from the list when the
    // key is there and the pane is empty covers the first; clearing when the
    // shown route has left the list covers the second. Keyed on the route, not
    // the whole pane state, which republishes three times per selection.
    val selectedKey = navigator.currentDestination?.contentKey
    val shownRoute = detail?.route
    LaunchedEffect(selectedKey, shownRoute, uiState.routes) {
        if (shownRoute != null) {
            if (uiState.routes.none { it.toDestination().key() == shownRoute.key() }) paneViewModel.clear()
        } else if (selectedKey != null) {
            uiState.routes.firstOrNull { it.toDestination().key() == selectedKey }
                ?.let { paneViewModel.select(it.toDestination()) }
        }
    }

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
