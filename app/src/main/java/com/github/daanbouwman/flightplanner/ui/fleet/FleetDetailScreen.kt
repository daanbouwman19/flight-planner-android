package com.github.daanbouwman.flightplanner.ui.fleet

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.daanbouwman.flightplanner.R
import com.github.daanbouwman.flightplanner.model.AircraftSpec
import com.github.daanbouwman.flightplanner.navigation.Destination
import androidx.compose.ui.unit.dp

/**
 * One airframe, full screen — the destination a phone navigates to.
 *
 * No `PredictiveBackHandler` here, for the same reason
 * [com.github.daanbouwman.flightplanner.ui.RouteDetailScreen] has none: the
 * `NavHost` already owns the back gesture, and a local handler would consume
 * it before the host could seek the pop transition to the gesture's progress.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FleetDetailScreen(
    onBack: () -> Unit,
    onGenerateRoutes: (AircraftSpec) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FleetDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(state.aircraft?.displayName.orEmpty()) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing
            .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
    ) { contentPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.TopCenter,
        ) {
            FleetDetailContent(
                state = state,
                onToggleFlown = viewModel::toggleFlown,
                onSave = { rangeNm, cruiseSpeedKt, takeoffDistanceMeters ->
                    state.aircraft?.let { aircraft ->
                        viewModel.update(
                            aircraft.copy(
                                rangeNm = rangeNm,
                                cruiseSpeedKt = cruiseSpeedKt,
                                takeoffDistanceMeters = takeoffDistanceMeters,
                            ),
                        )
                    }
                },
                onGenerateRoutes = { state.aircraft?.let(onGenerateRoutes) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

internal fun AircraftSpec.toFleetDetailDestination(): Destination.FleetDetail =
    Destination.FleetDetail(airframeId = id)
