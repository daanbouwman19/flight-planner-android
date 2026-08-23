package com.github.daanbouwman.flightplanner.ui.logbook

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
import com.github.daanbouwman.flightplanner.ui.profile.ProfileScreen
import com.github.daanbouwman.flightplanner.ui.profile.ProfileSegment
import kotlinx.coroutines.launch

/**
 * The Logbook section, in however many panes the window has room for.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun LogbookRoute(
    onOpenSettings: () -> Unit,
    onOpenRoute: (LogbookRow) -> Unit,
    onOpenAirport: (Airport) -> Unit,
    modifier: Modifier = Modifier,
) {
    val twoPanes = calculatePaneScaffoldDirective(currentWindowAdaptiveInfoV2())
        .maxHorizontalPartitions > 1

    if (!twoPanes) {
        ProfileScreen(
            segment = ProfileSegment.Logbook,
            onOpenSettings = onOpenSettings,
            onOpenRoute = onOpenRoute,
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
                    ProfileScreen(
                        segment = ProfileSegment.Logbook,
                        onOpenSettings = onOpenSettings,
                        onOpenRoute = { row ->
                            val route = row.toDestination()
                            paneViewModel.select(route)
                            scope.launch {
                                navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, route.key())
                            }
                        },
                    )
                }
            },
            detailPane = {
                AnimatedPane {
                    RouteDetailPane(
                        state = detail,
                        snackbarHostState = paneSnackbarHostState,
                        onMarkFlown = { false }, // Already flown
                        onFlownConfirmed = {},
                        onOpenAirport = onOpenAirport,
                        alreadyFlown = true,
                    )
                }
            },
        )
    }
}

private fun LogbookRow.toDestination(): Destination.RouteDetail = Destination.RouteDetail(
    departureIcao = departureIcao,
    destinationIcao = arrivalIcao,
    aircraftId = aircraftId,
    distanceNm = distanceNm ?: 0,
)

private fun Destination.RouteDetail.key(): String =
    "$departureIcao>$destinationIcao@$aircraftId"
