package com.github.daanbouwman.flightplanner.ui.goldens

import androidx.compose.runtime.Composable
import com.github.daanbouwman.flightplanner.ui.fleet.FleetMode
import com.github.daanbouwman.flightplanner.ui.fleet.FleetPreviewContent
import com.github.daanbouwman.flightplanner.ui.fleet.FleetStatus
import com.github.daanbouwman.flightplanner.ui.fleet.FleetUiState
import com.github.daanbouwman.flightplanner.ui.fleet.groupByCategory
import com.github.daanbouwman.flightplanner.ui.fleet.previewFleet
import org.junit.Test
import org.robolectric.annotation.Config

/** The Fleet list grouped by category, then empty, filtered to nothing, and loading. */
class FleetGoldens : GoldenSuite(subject = "fleet", primaryState = "populated") {

    @Composable
    override fun Primary() = FleetPreviewContent(
        FleetUiState(
            groups = previewFleet.groupByCategory(),
            mode = FleetMode.All,
            totalCount = previewFleet.size,
            flownCount = 1,
            notFlownCount = 1,
            status = FleetStatus.Ready,
        ),
    )

    @Test
    @Config(qualifiers = GoldenQualifiers.COMPACT)
    fun empty() = captureState("empty") {
        FleetPreviewContent(FleetUiState(status = FleetStatus.Ready))
    }

    @Test
    @Config(qualifiers = GoldenQualifiers.COMPACT)
    fun noMatch() = captureState("no-match") {
        FleetPreviewContent(FleetUiState(mode = FleetMode.Flown, totalCount = 2, status = FleetStatus.Ready))
    }

    @Test
    @Config(qualifiers = GoldenQualifiers.COMPACT)
    fun loading() = captureState("loading") {
        FleetPreviewContent(FleetUiState(status = FleetStatus.Loading))
    }
}
