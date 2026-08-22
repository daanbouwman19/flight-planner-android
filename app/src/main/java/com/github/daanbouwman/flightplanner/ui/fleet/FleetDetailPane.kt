package com.github.daanbouwman.flightplanner.ui.fleet

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.daanbouwman.flightplanner.R
import com.github.daanbouwman.flightplanner.core.designsystem.components.EmptyState
import com.github.daanbouwman.flightplanner.core.designsystem.motion.FlightMotion
import com.github.daanbouwman.flightplanner.model.AircraftSpec
import com.github.daanbouwman.flightplanner.ui.chrome.ScreenBottomGutter
import com.github.daanbouwman.flightplanner.ui.chrome.ScreenCompactTopGutter
import com.github.daanbouwman.flightplanner.ui.chrome.ScreenHorizontalGutter
import com.github.daanbouwman.flightplanner.ui.chrome.ScreenTopGutter
import com.github.daanbouwman.flightplanner.ui.chrome.isCompactHeight
import com.github.daanbouwman.flightplanner.ui.chrome.rememberContentInsets

/**
 * The detail pane: one airframe, or an invitation to pick one.
 *
 * Follows [com.github.daanbouwman.flightplanner.ui.detail.RouteDetailPane]'s
 * shape exactly — a heading of its own rather than an app bar, since this is
 * the other half of the screen the user is already on, not somewhere they
 * went.
 *
 * A `Surface`, not a `Scaffold`: content padding here comes entirely from
 * [rememberContentInsets], so a scaffold's own padding parameter would have
 * to be taken and ignored — precisely the shape `PlanScreen` and
 * `LogbookScreen` already reject their own scaffolds for. `Surface` rather
 * than a plain `Box` because it still needs to do what `Scaffold`'s
 * `containerColor` did: set `LocalContentColor` from the surface colour, so
 * unset-colour text and icons inside stay correctly toned if this colour
 * scheme's `surface`/`background` roles ever diverge (they are identical
 * today, which is why a bare `Box` would have compiled and looked right).
 */
@Composable
fun FleetDetailPane(
    state: FleetDetailPaneState?,
    onToggleFlown: (AircraftSpec) -> Unit,
    onSave: (AircraftSpec, rangeNm: Int, cruiseSpeedKt: Int, takeoffDistanceMeters: Int?) -> Unit,
    onGenerateRoutes: (AircraftSpec) -> Unit,
    modifier: Modifier = Modifier,
) {
    val contentInsets = rememberContentInsets()

    Surface(
        modifier = modifier
            .then(contentInsets.modifier)
            .fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        val insets = contentInsets.asPaddingValues()
        val layoutDirection = LocalLayoutDirection.current
        val compactHeight = isCompactHeight()
        val contentPadding = PaddingValues(
            start = insets.calculateStartPadding(layoutDirection) + ScreenHorizontalGutter,
            end = insets.calculateEndPadding(layoutDirection) + ScreenHorizontalGutter,
            top = insets.calculateTopPadding() + if (compactHeight) ScreenCompactTopGutter else ScreenTopGutter,
            bottom = insets.calculateBottomPadding() + ScreenBottomGutter,
        )

        val paneTransform = FlightMotion.paneContent()
        AnimatedContent(
            targetState = state,
            transitionSpec = { paneTransform },
            contentKey = { it?.airframeId },
            label = "fleet detail pane",
        ) { current ->
            FleetDetailPaneContent(
                state = current,
                contentPadding = contentPadding,
                onToggleFlown = onToggleFlown,
                onSave = onSave,
                onGenerateRoutes = onGenerateRoutes,
            )
        }
    }
}

@Composable
private fun FleetDetailPaneContent(
    state: FleetDetailPaneState?,
    contentPadding: PaddingValues,
    onToggleFlown: (AircraftSpec) -> Unit,
    onSave: (AircraftSpec, rangeNm: Int, cruiseSpeedKt: Int, takeoffDistanceMeters: Int?) -> Unit,
    onGenerateRoutes: (AircraftSpec) -> Unit,
) {
    if (state == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            contentAlignment = Alignment.Center,
        ) {
            EmptyState(
                title = stringResource(R.string.fleet_detail_pane_empty_title),
                message = stringResource(R.string.fleet_detail_pane_empty_message),
                icon = painterResource(R.drawable.ic_nav_fleet),
            )
        }
        return
    }

    val aircraft = state.aircraft
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // No separate heading here, unlike RouteDetailPane: FleetDetailContent's
        // own first block already states the airframe's name — Route detail's
        // content opens with the hero map instead, which is why that pane needs
        // a heading of its own and this one would only be repeating it.
        FleetDetailContent(
            state = FleetDetailUiState(aircraft = aircraft, loading = false),
            onToggleFlown = { aircraft?.let(onToggleFlown) },
            onSave = { rangeNm, cruiseSpeedKt, takeoffDistanceMeters ->
                aircraft?.let { onSave(it, rangeNm, cruiseSpeedKt, takeoffDistanceMeters) }
            },
            onGenerateRoutes = { aircraft?.let(onGenerateRoutes) },
        )
    }
}
