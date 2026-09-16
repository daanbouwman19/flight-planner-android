package com.github.daanbouwman.flightplanner.ui.goldens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.daanbouwman.flightplanner.navigation.Destination
import com.github.daanbouwman.flightplanner.routing.WorldOutline
import com.github.daanbouwman.flightplanner.ui.detail.RouteDetailContent
import com.github.daanbouwman.flightplanner.ui.detail.RouteDetailPane
import com.github.daanbouwman.flightplanner.ui.detail.RouteDetailPaneState
import com.github.daanbouwman.flightplanner.ui.detail.RouteDetailUiState
import com.github.daanbouwman.flightplanner.ui.detail.RouteTitle
import com.github.daanbouwman.flightplanner.ui.plan.PlanPreviewData
import com.github.daanbouwman.flightplanner.ui.plan.rememberPreviewWorldOutline
import org.junit.Test
import org.robolectric.annotation.Config

/** EHAM → RJTT as the Plan card's long haul, loaded and then still loading. */
internal fun longHaulRoute() = Destination.RouteDetail(
    departureIcao = PlanPreviewData.longHaul.departure.icao,
    destinationIcao = PlanPreviewData.longHaul.destination.icao,
    aircraftId = PlanPreviewData.longHaul.aircraft.id,
    distanceNm = PlanPreviewData.longHaul.distanceNm,
)

@Composable
internal fun longHaulDetail(outline: WorldOutline) = with(PlanPreviewData.longHaul) {
    RouteDetailUiState(
        departure = departure,
        destination = destination,
        aircraft = aircraft,
        distanceNm = distanceNm,
        flightTime = flightTime,
        arc = arc,
        outline = outline,
        initialBearingDeg = 34,
        finalBearingDeg = 96,
        departureRunways = PlanPreviewData.schipholRunways,
        requiredRunwayFt = 9_000,
        loading = false,
    )
}

/**
 * The route detail page as the phone shows it, minus the globe: `hero = null`
 * draws the still map, which is also what a device without a renderer gets. No
 * METAR is attached, so no observation age is counted.
 */
class RouteDetailGoldens : GoldenSuite(subject = "route-detail", primaryState = "loaded") {

    @Composable
    override fun Primary() = Detail(longHaulDetail(rememberPreviewWorldOutline()))

    @Test
    @Config(qualifiers = GoldenQualifiers.COMPACT)
    fun loading() = captureState("loading") { Detail(RouteDetailUiState(loading = true)) }

    @Composable
    private fun Detail(state: RouteDetailUiState) {
        val route = longHaulRoute()
        Column {
            RouteTitle(route = route, modifier = Modifier.padding(horizontal = 16.dp))
            // The phone host pads the content by 16 dp on every side past its
            // scaffold insets; the title sits in the app bar above it.
            RouteDetailContent(
                route = route,
                state = state,
                onMarkFlown = { false },
                onFlownConfirmed = {},
                snackbarHostState = remember { SnackbarHostState() },
                onOpenAirport = {},
                modifier = Modifier.padding(16.dp),
                animateEntrance = false,
                hero = null,
            )
        }
    }
}

/** The same route in the list–detail pane, and the pane with nothing selected. */
class RouteDetailPaneGoldens : GoldenSuite(subject = "route-detail-pane", primaryState = "loaded") {

    @Composable
    override fun Primary() = Pane(
        RouteDetailPaneState(route = longHaulRoute(), detail = longHaulDetail(rememberPreviewWorldOutline())),
    )

    @Test
    @Config(qualifiers = GoldenQualifiers.COMPACT)
    fun empty() = captureState("empty") { Pane(null) }

    @Composable
    private fun Pane(state: RouteDetailPaneState?) = RouteDetailPane(
        state = state,
        snackbarHostState = remember { SnackbarHostState() },
        onMarkFlown = { false },
        onFlownConfirmed = {},
        onOpenAirport = {},
    )
}
