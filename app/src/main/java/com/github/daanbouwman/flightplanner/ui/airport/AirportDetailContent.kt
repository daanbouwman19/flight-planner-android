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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
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
import com.github.daanbouwman.flightplanner.model.FlightRules
import com.github.daanbouwman.flightplanner.model.Metar
import com.github.daanbouwman.flightplanner.model.Runway
import com.github.daanbouwman.flightplanner.routing.SurfaceWind
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
import kotlin.math.roundToInt

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

    // The wind belongs on the diagram, not only in the weather panel below:
    // a direction in degrees has to be compared against a runway heading, and
    // in the same frame as the runways that comparison stops being arithmetic.
    val wind = metar?.let {
        val speed = it.windSpeedKt
        if (speed == null) null else DiagramWind(
            directionFromDeg = it.windDirectionDeg,
            speedKt = speed,
            gustKt = it.windGustKt,
            variable = it.windVariable,
        )
    }
    // The ends the diagram draws, and the one the wind favours among them —
    // the same call the diagram itself makes, so the words and the picture
    // cannot name different ends.
    val diagrammed = remember(runways) { runways.filter { it.trueHeadingDeg != null } }
    val favouredIdent = remember(diagrammed, wind) {
        SurfaceWind.favouredEnd(
            runwayHeadingsDeg = diagrammed.map { requireNotNull(it.trueHeadingDeg).roundToInt() },
            windFromDeg = wind?.directionFromDeg?.takeIf { wind.hasDirection },
            windSpeedKt = wind?.speedKt,
        )?.let { diagrammed[it].ident }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // The diagram is a square of its own width, and its detail stops
        // improving well before a phone's. Uncapped it was 328 dp on a compact
        // window -- a third of the screen for a drawing that reads fully at
        // 280 -- and 840 dp on a wide one, where this column is allowed to be
        // that wide. Capped and centred, it is the same size everywhere.
        RunwayDiagram(
            runways = runways,
            contentDescription = runwayDiagramDescription(diagrammed, favouredIdent),
            wind = wind,
            modifier = Modifier
                .widthIn(max = RunwayDiagramMaxSize)
                .fillMaxWidth(),
        )
        // The legend for the halo and the bold ident: both are conventions the
        // diagram invented, and a reader who has not met them sees a strip lit
        // for no stated reason. Absent, not blank, when the wind decides nothing.
        if (favouredIdent != null) {
            Text(
                text = stringResource(R.string.airport_runway_favoured_caption, favouredIdent),
                style = MaterialTheme.typography.bodySmall.asChartFigure(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

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
 * The runway diagram in words, for the screen reader the drawing cannot reach:
 * which ends are drawn and, when the wind decides one, which end it favours.
 * Ends with no published heading are not named — they are not in the picture,
 * and the runway list below names them.
 */
@Composable
private fun runwayDiagramDescription(diagrammed: List<Runway>, favouredIdent: String?): String {
    if (diagrammed.isEmpty()) return stringResource(R.string.airport_runway_diagram_empty)
    val ends = pluralStringResource(
        R.plurals.airport_runway_diagram_description,
        diagrammed.size,
        diagrammed.size,
        diagrammed.joinToString(", ") { it.ident },
    )
    return if (favouredIdent == null) {
        ends
    } else {
        stringResource(R.string.airport_runway_diagram_favoured, ends, favouredIdent)
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

/**
 * Where the diagram stops growing. Chosen by eye against Schiphol's six
 * runways: the idents are still legible at `labelSmall` and the lanes still
 * separate, and past it the drawing only gets emptier.
 */
private val RunwayDiagramMaxSize = 280.dp

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

/** With a wind: the halo, the bold ident and the caption that explains them. */
@LightDarkPreview
@CompactWidthPreview
@Composable
private fun AirportDetailContentWindPreview() {
    FlightPlannerTheme(dynamicColor = false) {
        AirportDetailContent(
            state = AirportDetailUiState(
                airport = PlanPreviewData.schiphol,
                runways = PlanPreviewData.schipholRunways,
                metar = Metar(
                    station = "EHAM",
                    raw = "EHAM 121325Z 27012KT 9999 FEW040 18/09 Q1015",
                    flightRules = FlightRules.VFR,
                    windDirectionDeg = 270,
                    windSpeedKt = 12,
                ),
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
