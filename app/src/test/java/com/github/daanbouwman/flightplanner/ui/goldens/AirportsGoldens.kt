package com.github.daanbouwman.flightplanner.ui.goldens

import androidx.compose.runtime.Composable
import com.github.daanbouwman.flightplanner.ui.airports.AirportsContent
import com.github.daanbouwman.flightplanner.ui.airports.AirportsUiState
import com.github.daanbouwman.flightplanner.ui.plan.PlanPreviewData
import com.github.daanbouwman.flightplanner.ui.plan.SearchScope
import org.junit.Test
import org.robolectric.annotation.Config

/** The Airports browse screen: the largest-airports suggestions, and the three ways a search ends. */
class AirportsGoldens : GoldenSuite(subject = "airports", primaryState = "suggestions") {

    @Composable
    override fun Primary() = Airports(
        AirportsUiState(
            airports = listOf(PlanPreviewData.schiphol, PlanPreviewData.kennedy, PlanPreviewData.haneda),
        ),
    )

    @Test
    @Config(qualifiers = GoldenQualifiers.COMPACT)
    fun random() = captureState("random") {
        Airports(AirportsUiState(airports = listOf(PlanPreviewData.innsbruck, PlanPreviewData.kennedy), isRandomBatch = true))
    }

    @Test
    @Config(qualifiers = GoldenQualifiers.COMPACT)
    fun empty() = captureState("empty") {
        Airports(AirportsUiState(query = "ZZZZ"))
    }

    @Test
    @Config(qualifiers = GoldenQualifiers.COMPACT)
    fun degradedSearch() = captureState("degraded-search") {
        Airports(
            AirportsUiState(
                query = "AMS",
                airports = listOf(PlanPreviewData.schiphol),
                searchScope = SearchScope.NamesUnavailable,
            ),
        )
    }

    @Composable
    private fun Airports(state: AirportsUiState) = AirportsContent(
        state = state,
        onQueryChange = {},
        onRandomize = {},
        onRetrySearch = {},
        onOpenAirport = {},
    )
}
