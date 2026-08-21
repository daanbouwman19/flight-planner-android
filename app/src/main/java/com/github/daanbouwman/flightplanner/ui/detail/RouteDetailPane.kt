package com.github.daanbouwman.flightplanner.ui.detail

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.daanbouwman.flightplanner.R
import com.github.daanbouwman.flightplanner.core.designsystem.components.DevicePreviews
import com.github.daanbouwman.flightplanner.core.designsystem.components.EmptyState
import com.github.daanbouwman.flightplanner.core.designsystem.components.LightDarkPreview
import com.github.daanbouwman.flightplanner.core.designsystem.motion.FlightMotion
import com.github.daanbouwman.flightplanner.core.designsystem.theme.FlightPlannerTheme
import com.github.daanbouwman.flightplanner.core.designsystem.theme.withTabularFigures
import com.github.daanbouwman.flightplanner.model.Airport
import com.github.daanbouwman.flightplanner.model.AirportSizeClass
import com.github.daanbouwman.flightplanner.navigation.Destination
import com.github.daanbouwman.flightplanner.ui.chrome.MaxContentWidth
import com.github.daanbouwman.flightplanner.ui.chrome.ScreenBottomGutter
import com.github.daanbouwman.flightplanner.ui.chrome.ScreenCompactTopGutter
import com.github.daanbouwman.flightplanner.ui.chrome.ScreenHorizontalGutter
import com.github.daanbouwman.flightplanner.ui.chrome.ScreenTopGutter
import com.github.daanbouwman.flightplanner.ui.chrome.WideMaxContentWidth
import com.github.daanbouwman.flightplanner.ui.chrome.isCompactHeight
import com.github.daanbouwman.flightplanner.ui.chrome.rememberContentInsets

/**
 * The detail pane: a route, or an invitation to pick one.
 *
 * Shared between Plan and Logbook on wide screens. It carries a heading of its
 * own rather than an app bar. An app bar implies a screen you can leave, and
 * this pane is not somewhere you went — it is the other half of the screen you
 * are already on.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteDetailPane(
    state: RouteDetailPaneState?,
    snackbarHostState: SnackbarHostState,
    onMarkFlown: (Destination.RouteDetail) -> Boolean,
    onFlownConfirmed: () -> Unit,
    modifier: Modifier = Modifier,
    /** Whether the route is already in the logbook (disables Mark as Flown). */
    alreadyFlown: Boolean = false,
) {
    val contentInsets = rememberContentInsets()

    Scaffold(
        modifier = modifier.then(contentInsets.modifier),
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom),
                ),
            )
        },
        // The pane computes remaining insets directly via ContentInsets so that
        // content padding handles top status bar and bottom gesture bar insets.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.surface,
    ) { _ ->
        val insets = contentInsets.asPaddingValues()
        val layoutDirection = LocalLayoutDirection.current
        val compactHeight = isCompactHeight()
        val contentPadding = PaddingValues(
            start = insets.calculateStartPadding(layoutDirection) + ScreenHorizontalGutter,
            end = insets.calculateEndPadding(layoutDirection) + ScreenHorizontalGutter,
            top = insets.calculateTopPadding() + if (compactHeight) ScreenCompactTopGutter else ScreenTopGutter,
            bottom = insets.calculateBottomPadding() + ScreenBottomGutter,
        )

        // Keyed on the **selection**, not on what has been loaded from it. One
        // selection publishes three times — the codes alone, then the airports,
        // then the runways — so a key read off the loaded state is absent on the
        // first publish and the panel cross-fades twice per tap, through a blank
        // skeleton, which is the smear this key exists to prevent. The route is
        // known the instant the user taps and never changes; the rest is content.
        //
        // The transform is read outside the spec lambda, which is not composable —
        // the same reason the navigation graph resolves its transitions up front.
        val paneTransform = FlightMotion.paneContent()
        AnimatedContent(
            targetState = state,
            transitionSpec = { paneTransform },
            contentKey = { it?.route?.key() },
            label = "route detail pane",
        ) { current ->
            RouteDetailPaneContent(
                state = current,
                contentPadding = contentPadding,
                snackbarHostState = snackbarHostState,
                onMarkFlown = onMarkFlown,
                onFlownConfirmed = onFlownConfirmed,
                alreadyFlown = alreadyFlown,
            )
        }
    }
}

/**
 * One route's worth of pane, or the empty invitation.
 *
 * Split out of [RouteDetailPane] so that `AnimatedContent` composes a whole copy
 * of it per route: the outgoing copy keeps showing the route it was given while
 * it fades, rather than re-rendering with the incoming route's data half way
 * through its own exit.
 */
@Composable
private fun RouteDetailPaneContent(
    state: RouteDetailPaneState?,
    contentPadding: PaddingValues,
    snackbarHostState: SnackbarHostState,
    onMarkFlown: (Destination.RouteDetail) -> Boolean,
    onFlownConfirmed: () -> Unit,
    alreadyFlown: Boolean,
) {
    if (state == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            contentAlignment = Alignment.Center,
        ) {
            EmptyState(
                title = stringResource(R.string.route_detail_pane_empty_title),
                message = stringResource(R.string.route_detail_pane_empty),
                icon = painterResource(R.drawable.ic_route_placeholder),
            )
        }
        return
    }

    // Both come from the selection, so the heading states the real codes from
    // the first frame rather than waiting for a database read to name them.
    val route = state.route
    val detail = state.detail
    // Centred, because the content caps its own line length and a pane on a
    // desktop-sized window is wider than that cap. Left-aligned, the spine
    // would sit against the divider with a hand's width of nothing to its
    // right.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(
                R.string.route_detail_title_spoken,
                route.departureIcao,
                route.destinationIcao,
            ),
            style = MaterialTheme.typography.titleLarge.withTabularFigures(),
            modifier = Modifier
                .widthIn(max = if (isCompactHeight()) MaxContentWidth else WideMaxContentWidth)
                .fillMaxWidth(),
        )

        RouteDetailContent(
            route = route,
            state = detail,
            onMarkFlown = { onMarkFlown(route) },
            onFlownConfirmed = onFlownConfirmed,
            snackbarHostState = snackbarHostState,
            // The pane cross-fades between routes; staging each block in on
            // top of that would be two motions for one change.
            animateEntrance = false,
            alreadyFlown = alreadyFlown,
        )
    }
}

/**
 * A stable identity for the scaffold navigator.
 */
private fun Destination.RouteDetail.key(): String =
    "$departureIcao>$destinationIcao@$aircraftId"

@LightDarkPreview
@DevicePreviews
@Composable
private fun RouteDetailPaneEmptyPreview() {
    FlightPlannerTheme(dynamicColor = false) {
        RouteDetailPane(
            state = null,
            snackbarHostState = remember { SnackbarHostState() },
            onMarkFlown = { false },
            onFlownConfirmed = {},
        )
    }
}

@LightDarkPreview
@DevicePreviews
@Composable
private fun RouteDetailPanePreview() {
    FlightPlannerTheme(dynamicColor = false) {
        val route = Destination.RouteDetail(
            departureIcao = "EHAM",
            destinationIcao = "KJFK",
            aircraftId = 1,
            distanceNm = 3163,
        )
        val departure = Airport(
            id = 1,
            icao = "EHAM",
            name = "Amsterdam Airport Schiphol",
            latitude = 52.3086,
            longitude = 4.7639,
            elevationFt = -11,
            country = "NL",
            municipality = "Amsterdam",
            sizeClass = AirportSizeClass.LARGE,
            longestRunwayFt = 12467,
            runwayCount = 6,
            hasHardSurface = true,
            hasIcaoCode = true,
        )
        val destination = Airport(
            id = 2,
            icao = "KJFK",
            name = "John F Kennedy International Airport",
            latitude = 40.6398,
            longitude = -73.7789,
            elevationFt = 13,
            country = "US",
            municipality = "New York",
            sizeClass = AirportSizeClass.LARGE,
            longestRunwayFt = 14511,
            runwayCount = 4,
            hasHardSurface = true,
            hasIcaoCode = true,
        )
        RouteDetailPane(
            state = RouteDetailPaneState(
                route = route,
                detail = RouteDetailUiState(
                    departure = departure,
                    destination = destination,
                    distanceNm = 3163,
                    loading = false,
                ),
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onMarkFlown = { false },
            onFlownConfirmed = {},
        )
    }
}
