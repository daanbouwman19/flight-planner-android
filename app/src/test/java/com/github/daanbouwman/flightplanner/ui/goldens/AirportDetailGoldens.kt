package com.github.daanbouwman.flightplanner.ui.goldens

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.daanbouwman.flightplanner.ui.airport.AirportDetailContent
import com.github.daanbouwman.flightplanner.ui.airport.AirportDetailUiState
import com.github.daanbouwman.flightplanner.ui.plan.PlanPreviewData
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * Airport detail: Schiphol's runway diagram with a wind, the weather block, then
 * the skeleton and the not-found state. The METAR carries no observation time,
 * so nothing on the screen counts minutes since it.
 */
class AirportDetailGoldens : GoldenSuite(subject = "airport-detail", primaryState = "schiphol-wind") {

    @Composable
    override fun Primary() = Detail(
        AirportDetailUiState(
            airport = PlanPreviewData.schiphol,
            runways = PlanPreviewData.schipholRunways,
            metar = schipholVfrMetar,
            loading = false,
        ),
    )

    @Test
    @Config(qualifiers = GoldenQualifiers.COMPACT)
    fun loading() = captureState("loading") { Detail(AirportDetailUiState(loading = true)) }

    @Test
    @Config(qualifiers = GoldenQualifiers.COMPACT)
    fun notFound() = captureState("not-found") { Detail(AirportDetailUiState(loading = false)) }

    @Composable
    private fun Detail(state: AirportDetailUiState) = AirportDetailContent(
        state = state,
        onFlyFromHere = {},
        snackbarHostState = remember { SnackbarHostState() },
        modifier = Modifier.padding(16.dp),
    )
}
