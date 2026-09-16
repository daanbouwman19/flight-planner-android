package com.github.daanbouwman.flightplanner.ui.goldens

import androidx.compose.runtime.Composable
import com.github.daanbouwman.flightplanner.ui.plan.PlanFailure
import com.github.daanbouwman.flightplanner.ui.plan.PlanMode
import com.github.daanbouwman.flightplanner.ui.plan.PlanPreviewContent
import com.github.daanbouwman.flightplanner.ui.plan.PlanPreviewData
import com.github.daanbouwman.flightplanner.ui.plan.PlanStatus
import com.github.daanbouwman.flightplanner.ui.plan.PlanUiState
import org.junit.Test
import org.robolectric.annotation.Config

/** The Plan screen: three route cards under the header, and every state the previews enumerate. */
class PlanGoldens : GoldenSuite(subject = "plan", primaryState = "routes") {

    @Composable
    override fun Primary() = PlanPreviewContent(
        PlanUiState(routes = PlanPreviewData.batch, status = PlanStatus.Ready, notFlownCount = 12),
    )

    @Test
    @Config(qualifiers = GoldenQualifiers.COMPACT)
    fun filters() = captureState("filters") {
        PlanPreviewContent(
            PlanUiState(
                mode = PlanMode.SelectedAircraft,
                lockedDeparture = PlanPreviewData.schiphol,
                selectedAircraft = PlanPreviewData.boeing,
                routes = PlanPreviewData.batch,
                status = PlanStatus.Ready,
            ),
        )
    }

    @Test
    @Config(qualifiers = GoldenQualifiers.COMPACT)
    fun generating() = captureState("generating") {
        PlanPreviewContent(PlanUiState(status = PlanStatus.Generating))
    }

    @Test
    @Config(qualifiers = GoldenQualifiers.COMPACT)
    fun endReached() = captureState("end-reached") {
        PlanPreviewContent(PlanUiState(routes = PlanPreviewData.batch, status = PlanStatus.EndReached))
    }

    @Test
    @Config(qualifiers = GoldenQualifiers.COMPACT)
    fun noMatches() = captureState("no-matches") {
        PlanPreviewContent(PlanUiState(routes = emptyList(), status = PlanStatus.Ready))
    }

    @Test
    @Config(qualifiers = GoldenQualifiers.COMPACT)
    fun awaitingAircraft() = captureState("awaiting-aircraft") {
        PlanPreviewContent(PlanUiState(mode = PlanMode.SelectedAircraft, selectedAircraft = null))
    }

    @Test
    @Config(qualifiers = GoldenQualifiers.COMPACT)
    fun indexFailed() = captureState("index-failed") {
        PlanPreviewContent(PlanUiState(status = PlanStatus.Failed(PlanFailure.IndexUnavailable)))
    }

    @Test
    @Config(qualifiers = GoldenQualifiers.COMPACT)
    fun emptyFleet() = captureState("empty-fleet") {
        PlanPreviewContent(PlanUiState(status = PlanStatus.Failed(PlanFailure.FleetEmpty)))
    }
}
