package com.github.daanbouwman.flightplanner.ui.airport

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.github.daanbouwman.flightplanner.R
import com.github.daanbouwman.flightplanner.core.designsystem.components.EmptyState
import com.github.daanbouwman.flightplanner.core.designsystem.components.RunwayDiagram
import com.github.daanbouwman.flightplanner.core.designsystem.components.SkeletonCard
import com.github.daanbouwman.flightplanner.core.designsystem.theme.asChartFigure
import com.github.daanbouwman.flightplanner.model.Airport
import com.github.daanbouwman.flightplanner.model.Runway
import com.github.daanbouwman.flightplanner.ui.asFigure
import com.github.daanbouwman.flightplanner.ui.chrome.MaxContentWidth
import com.github.daanbouwman.flightplanner.ui.chrome.WideMaxContentWidth
import com.github.daanbouwman.flightplanner.ui.chrome.isCompactHeight
import com.github.daanbouwman.flightplanner.ui.detail.RunwayLine
import com.github.daanbouwman.flightplanner.ui.detail.WeatherReservedHeight
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
                onFlyFromHere = { onFlyFromHere(state.airport) },
            )
        }
    }
}

@Composable
private fun AirportDetailBody(
    airport: Airport,
    runways: List<Runway>,
    onFlyFromHere: () -> Unit,
) {
    Column {
        Text(text = airport.icao, style = MaterialTheme.typography.headlineSmall.asChartFigure())
        Text(text = airport.name, style = MaterialTheme.typography.titleMedium)
        val facts = listOfNotNull(
            listOfNotNull(airport.municipality, airport.country).joinToString(", ").ifBlank { null },
            stringResource(R.string.route_detail_elevation, airport.elevationFt.asFigure())
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

    RunwayDiagram(runways = runways, modifier = Modifier.fillMaxWidth())

    if (runways.isEmpty()) {
        Text(
            text = stringResource(R.string.plan_runway_value, airport.longestRunwayFt.asFigure()),
            style = MaterialTheme.typography.labelMedium.asChartFigure(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            runways.forEach { runway -> RunwayLine(runway = runway, requiredRunwayFt = 0) }
        }
    }

    AirportWeatherBlock()

    Button(onClick = onFlyFromHere, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.airport_detail_fly_from_here))
    }
}

/**
 * Where the weather will be — the same "reserve the space, say what's
 * missing" shape as [com.github.daanbouwman.flightplanner.ui.detail.RouteDetailContent]'s
 * `WeatherBlock`, not shared with it: four lines, and the copy differs
 * ("this airport" rather than "both fields").
 */
@Composable
private fun AirportWeatherBlock() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.route_detail_weather),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth().heightIn(min = WeatherReservedHeight),
        ) {
            Text(
                text = stringResource(R.string.airport_detail_weather_pending),
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(12.dp),
            )
        }
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
            modifier = Modifier.padding(16.dp),
        )
    }
}
