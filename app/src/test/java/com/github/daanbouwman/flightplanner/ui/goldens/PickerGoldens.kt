package com.github.daanbouwman.flightplanner.ui.goldens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.daanbouwman.flightplanner.ui.picker.AircraftResults
import com.github.daanbouwman.flightplanner.ui.picker.AirportResults
import com.github.daanbouwman.flightplanner.ui.picker.NoResults
import com.github.daanbouwman.flightplanner.ui.picker.SearchScopeNotice
import com.github.daanbouwman.flightplanner.ui.plan.PlanPreviewData
import com.github.daanbouwman.flightplanner.ui.plan.SearchScope
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * The picker sheet's contents: airport rows, airframe rows, the empty result and
 * the two degraded-search notices. The sheet itself is a separate window and is
 * not captured; these are what it shows.
 */
class PickerGoldens : GoldenSuite(subject = "picker", primaryState = "airport-results") {

    @Composable
    override fun Primary() = AirportResults(
        query = "AMS",
        airports = listOf(PlanPreviewData.schiphol, PlanPreviewData.kennedy, PlanPreviewData.haneda),
        onPick = {},
    )

    @Test
    @Config(qualifiers = GoldenQualifiers.COMPACT)
    fun aircraftResults() = captureState("aircraft-results") {
        AircraftResults(query = "", aircraft = listOf(PlanPreviewData.boeing, PlanPreviewData.cessna), onPick = {})
    }

    @Test
    @Config(qualifiers = GoldenQualifiers.COMPACT)
    fun noResults() = captureState("no-results") { NoResults(query = "EHZZ") }

    @Test
    @Config(qualifiers = GoldenQualifiers.COMPACT)
    fun scopeNotice() = captureState("scope-notice") {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SearchScopeNotice(SearchScope.NamesLoading, onRetry = {})
            SearchScopeNotice(SearchScope.NamesUnavailable, onRetry = {})
        }
    }
}
