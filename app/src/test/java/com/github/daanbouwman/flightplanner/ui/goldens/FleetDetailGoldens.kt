package com.github.daanbouwman.flightplanner.ui.goldens

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.daanbouwman.flightplanner.ui.fleet.FleetDetailContent
import com.github.daanbouwman.flightplanner.ui.fleet.FleetDetailUiState
import com.github.daanbouwman.flightplanner.ui.plan.PlanPreviewData
import org.junit.Test
import org.robolectric.annotation.Config

/** One airframe's detail — the 777 — then the skeleton and the missing-airframe state. */
class FleetDetailGoldens : GoldenSuite(subject = "fleet-detail", primaryState = "populated") {

    @Composable
    override fun Primary() = Detail(FleetDetailUiState(aircraft = PlanPreviewData.boeing, loading = false))

    @Test
    @Config(qualifiers = GoldenQualifiers.COMPACT)
    fun loading() = captureState("loading") { Detail(FleetDetailUiState(loading = true)) }

    @Test
    @Config(qualifiers = GoldenQualifiers.COMPACT)
    fun notFound() = captureState("not-found") { Detail(FleetDetailUiState(loading = false)) }

    @Composable
    private fun Detail(state: FleetDetailUiState) = FleetDetailContent(
        state = state,
        onToggleFlown = {},
        onSave = { _, _, _ -> },
        onGenerateRoutes = {},
        modifier = Modifier.padding(16.dp),
    )
}
