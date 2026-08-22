package com.github.daanbouwman.flightplanner.search

import com.github.daanbouwman.flightplanner.model.AircraftSpec
import com.github.daanbouwman.flightplanner.routing.AircraftSearchItem
import com.github.daanbouwman.flightplanner.routing.SearchCandidate
import com.github.daanbouwman.flightplanner.routing.SearchQuery
import com.github.daanbouwman.flightplanner.routing.SearchScorer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

/**
 * Ranked aircraft search over the fleet, shared the same way
 * [rankedAirportResults] is — moved here once
 * [com.github.daanbouwman.flightplanner.ui.logbook.LogbookViewModel]'s
 * add-flight sheet needed the identical scan
 * [com.github.daanbouwman.flightplanner.ui.plan.PlanViewModel] already had for
 * its aircraft picker.
 *
 * A blank query returns the fleet as-is; otherwise [SearchScorer] ranks it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun rankedAircraftResults(
    scope: CoroutineScope,
    query: StateFlow<String>,
    fleet: StateFlow<List<AircraftSpec>>,
    defaultDispatcher: CoroutineDispatcher,
    stopTimeoutMillis: Long,
): StateFlow<List<AircraftSpec>> =
    combine(query, fleet) { text, airframes -> text to airframes }
        .mapLatest { (text, airframes) -> searchAircraft(text, airframes, defaultDispatcher) }
        .stateIn(scope, SharingStarted.WhileSubscribed(stopTimeoutMillis), emptyList())

private suspend fun searchAircraft(
    query: String,
    airframes: List<AircraftSpec>,
    defaultDispatcher: CoroutineDispatcher,
): List<AircraftSpec> = withContext(defaultDispatcher) {
    if (query.isBlank()) return@withContext airframes
    // The row carries its own spec, so a ranked result is the airframe
    // itself. Matching back by type code would be wrong as well as slow: an
    // ICAO *type* code is shared by every variant of a model, so two A320s in
    // the fleet are indistinguishable by it.
    SearchScorer.rank(airframes.map(::FleetRow), query).map { it.spec }
}

/** Adapts an airframe to the shared scorer without copying it. */
private class FleetRow(val spec: AircraftSpec) : SearchCandidate {
    private val item = AircraftSearchItem.from(spec)

    override fun searchScore(query: SearchQuery): Int = item.searchScore(query)
}
