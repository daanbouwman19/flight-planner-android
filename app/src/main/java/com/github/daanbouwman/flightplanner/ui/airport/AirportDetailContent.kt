package com.github.daanbouwman.flightplanner.ui.airport

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.daanbouwman.flightplanner.R
import com.github.daanbouwman.flightplanner.core.designsystem.components.DiagramWind
import com.github.daanbouwman.flightplanner.core.designsystem.components.EmptyState
import com.github.daanbouwman.flightplanner.core.designsystem.components.RunwayDiagram
import com.github.daanbouwman.flightplanner.core.designsystem.components.SkeletonCard
import com.github.daanbouwman.flightplanner.core.designsystem.components.SkyProfileHeight
import com.github.daanbouwman.flightplanner.core.designsystem.theme.asChartFigure
import com.github.daanbouwman.flightplanner.model.Airport
import com.github.daanbouwman.flightplanner.model.Metar
import com.github.daanbouwman.flightplanner.model.Runway
import com.github.daanbouwman.flightplanner.ui.chrome.MaxContentWidth
import com.github.daanbouwman.flightplanner.ui.chrome.WideMaxContentWidth
import com.github.daanbouwman.flightplanner.ui.chrome.isCompactHeight
import com.github.daanbouwman.flightplanner.ui.lengthText
import com.github.daanbouwman.flightplanner.ui.detail.AirportLinks
import com.github.daanbouwman.flightplanner.ui.detail.MetarPanel
import com.github.daanbouwman.flightplanner.ui.detail.RunwayLine
import com.github.daanbouwman.flightplanner.core.designsystem.components.CompactWidthPreview
import com.github.daanbouwman.flightplanner.core.designsystem.components.LightDarkPreview
import com.github.daanbouwman.flightplanner.core.designsystem.theme.FlightPlannerTheme
import com.github.daanbouwman.flightplanner.ui.plan.PlanPreviewData

/**
 * Everything the Airport detail screen says, with no opinion about where it
 * is being said — mirroring [com.github.daanbouwman.flightplanner.ui.fleet.FleetDetailContent]'s
 * shape, since this screen has the same single-host structure (no pane, no
 * shared undo state to coordinate with a list beside it).
 */
@Composable
fun AirportDetailContent(
    state: AirportDetailUiState,
    onFlyFromHere: (Airport) -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.widthIn(max = if (isCompactHeight()) MaxContentWidth else WideMaxContentWidth),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        when {
            state.loading -> SkeletonCard()
            state.airport == null -> EmptyState(
                title = stringResource(R.string.airport_detail_not_found_title),
                message = stringResource(R.string.airport_detail_not_found_message),
            )
            else -> AirportDetailBody(
                airport = state.airport,
                runways = state.runways,
                metar = state.metar,
                onFlyFromHere = { onFlyFromHere(state.airport) },
                snackbarHostState = snackbarHostState,
            )
        }
    }
}

@Composable
private fun AirportDetailBody(
    airport: Airport,
    runways: List<Runway>,
    metar: Metar?,
    onFlyFromHere: () -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    Column {
        Text(text = airport.icao, style = MaterialTheme.typography.headlineSmall.asChartFigure())
        Text(text = airport.name, style = MaterialTheme.typography.titleMedium)
        val facts = listOfNotNull(
            listOfNotNull(airport.municipality, airport.country).joinToString(", ").ifBlank { null },
            stringResource(R.string.route_detail_elevation, lengthText(airport.elevationFt))
                .takeIf { airport.elevationFt != 0 },
        )
        if (facts.isNotEmpty()) {
            Text(
                text = facts.joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    RunwayDiagram(
        runways = runways,
        // The wind belongs on the diagram, not only in the weather panel below:
        // a direction in degrees has to be compared against a runway heading, and
        // in the same frame as the runways that comparison stops being arithmetic.
        wind = metar?.let {
            val speed = it.windSpeedKt
            if (speed == null) null else DiagramWind(
                directionFromDeg = it.windDirectionDeg,
                speedKt = speed,
                gustKt = it.windGustKt,
                variable = it.windVariable,
            )
        },
        modifier = Modifier.fillMaxWidth(),
    )

    if (runways.isEmpty()) {
        Text(
            text = stringResource(R.string.plan_runway_value, lengthText(airport.longestRunwayFt)),
            style = MaterialTheme.typography.labelMedium.asChartFigure(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            runways.forEach { runway -> RunwayLine(runway = runway, requiredRunwayFt = 0) }
        }
    }

    AirportLinks(airport = airport, snackbarHostState = snackbarHostState)

    AirportWeatherBlock(icao = airport.icao, metar = metar)

    Button(onClick = onFlyFromHere, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.airport_detail_fly_from_here))
    }
}

/**
 * Where the weather is — the same shape as
 * [com.github.daanbouwman.flightplanner.ui.detail.RouteDetailContent]'s
 * `WeatherBlock`, sharing its `MetarPanel`: one airport instead of two.
 */
@Composable
private fun AirportWeatherBlock(icao: String, metar: Metar?) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.route_detail_weather),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        MetarPanel(
            icao = icao,
            metar = metar,
            modifier = Modifier.fillMaxWidth(),
            // The full hero here: one airport, one scene, and the screen whose
            // whole subject this is.
            sceneHeight = SkyProfileHeight.AirportDetail,
        )
    }
}

@LightDarkPreview
@CompactWidthPreview
@Composable
private fun AirportDetailContentPreview() {
    FlightPlannerTheme(dynamicColor = false) {
        AirportDetailContent(
            state = AirportDetailUiState(
                airport = PlanPreviewData.schiphol,
                runways = PlanPreviewData.schipholRunways,
                loading = false,
            ),
            onFlyFromHere = {},
            snackbarHostState = remember { SnackbarHostState() },
            modifier = Modifier.padding(16.dp),
        )
    }
}

@LightDarkPreview
@Composable
private fun AirportDetailContentLoadingPreview() {
    FlightPlannerTheme(dynamicColor = false) {
        AirportDetailContent(
            state = AirportDetailUiState(loading = true),
            onFlyFromHere = {},
            snackbarHostState = remember { SnackbarHostState() },
            modifier = Modifier.padding(16.dp),
        )
    }
}

@LightDarkPreview
@Composable
private fun AirportDetailContentNotFoundPreview() {
    FlightPlannerTheme(dynamicColor = false) {
        AirportDetailContent(
            state = AirportDetailUiState(loading = false),
            onFlyFromHere = {},
            snackbarHostState = remember { SnackbarHostState() },
            modifier = Modifier.padding(16.dp),
        )
    }
}
