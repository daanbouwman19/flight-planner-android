package com.github.daanbouwman.flightplanner.ui.goldens

import androidx.compose.runtime.Composable
import com.github.daanbouwman.flightplanner.routing.WorldOutline
import com.github.daanbouwman.flightplanner.ui.plan.rememberPreviewWorldOutline
import com.github.daanbouwman.flightplanner.ui.stats.StatsScreen
import com.github.daanbouwman.flightplanner.ui.stats.StatsUiState
import com.github.daanbouwman.flightplanner.ui.stats.previewStatsSuccess
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * Statistics: the hero distance, the activity chart, the visited network and the
 * airport highlights, then the skeleton. The network card offers its Globe mode
 * only when `rememberGlobeAvailable()` says so, which under Robolectric it does
 * not — the flat map is what these show, by the seam [GoldenSuite] documents.
 */
class StatsGoldens : GoldenSuite(subject = "stats", primaryState = "success") {

    @Composable
    override fun Primary() = Stats(previewStatsSuccess, rememberPreviewWorldOutline())

    @Test
    @Config(qualifiers = GoldenQualifiers.COMPACT)
    fun loading() = captureState("loading") {
        Stats(StatsUiState.Loading, WorldOutline.Empty)
    }

    @Composable
    private fun Stats(state: StatsUiState, outline: WorldOutline) = StatsScreen(
        uiState = state,
        onSelectTimeframe = {},
        onSelectMetric = {},
        onOpenSettings = {},
        onOpenRoute = {},
        onOpenAircraft = {},
        onPlanFlight = {},
        worldOutline = outline,
    )
}
