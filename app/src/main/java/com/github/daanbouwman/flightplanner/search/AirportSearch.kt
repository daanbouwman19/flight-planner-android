package com.github.daanbouwman.flightplanner.search

import com.github.daanbouwman.flightplanner.core.database.airport.NameIndexState
import com.github.daanbouwman.flightplanner.core.database.repository.AirportRepository
import com.github.daanbouwman.flightplanner.index.AirportIndexProvider
import com.github.daanbouwman.flightplanner.model.Airport
import com.github.daanbouwman.flightplanner.routing.AirportIndex
import com.github.daanbouwman.flightplanner.routing.AirportSlotSearch
import com.github.daanbouwman.flightplanner.ui.plan.SearchScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

/**
 * Ranked airport search, moved out of [com.github.daanbouwman.flightplanner.ui.plan.PlanViewModel]
 * once a second screen needed it: [com.github.daanbouwman.flightplanner.ui.logbook.LogbookViewModel]'s
 * add-flight sheet needs the same ranked search twice over (departure and
 * destination), which would otherwise be the third copy of the debounce,
 * re-rank-on-index-ready and largest-airports-when-blank logic `PlanViewModel`
 * already carries once.
 *
 * The query lives in the caller's `StateFlow`, not here, for the same reason
 * `PlanPickerSheet`'s own KDoc gives: the ranked scan survives recomposition
 * and can be exercised without Compose.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
fun rankedAirportResults(
    scope: CoroutineScope,
    query: StateFlow<String>,
    indexProvider: AirportIndexProvider,
    airportRepository: AirportRepository,
    defaultDispatcher: CoroutineDispatcher,
    stopTimeoutMillis: Long = STOP_TIMEOUT_MILLIS,
): StateFlow<List<Airport>> {
    // Re-emitted when the name index becomes available, so a query typed
    // during the build is re-ranked the moment names land rather than
    // staying code-only forever — see PlanViewModel's own `airportSearches`
    // for the fuller rationale this was moved from verbatim.
    val searches = combine(
        query,
        airportRepository.nameIndexState.map { it is NameIndexState.Ready }.distinctUntilChanged(),
    ) { text, namesReady -> text to namesReady }
        .distinctUntilChanged { (lastQuery, _), (text, namesReady) ->
            lastQuery == text && (!namesReady || text.isBlank())
        }
        .map { (text, _) -> text }

    return searches
        .debounce { if (it.isBlank()) 0L else SEARCH_DEBOUNCE_MILLIS }
        .mapLatest { searchAirports(it, indexProvider, airportRepository, defaultDispatcher) }
        .stateIn(scope, SharingStarted.WhileSubscribed(stopTimeoutMillis), emptyList())
}

/** Whether ranked-by-name search is available, and if not, why — see [SearchScope]. */
fun airportSearchScope(
    scope: CoroutineScope,
    airportRepository: AirportRepository,
    stopTimeoutMillis: Long = STOP_TIMEOUT_MILLIS,
): StateFlow<SearchScope> =
    airportRepository.nameIndexState
        .map { state ->
            when (state) {
                is NameIndexState.Ready -> SearchScope.Full
                is NameIndexState.Failed -> SearchScope.NamesUnavailable
                NameIndexState.Idle, NameIndexState.Building -> SearchScope.NamesLoading
            }
        }
        .stateIn(scope, SharingStarted.WhileSubscribed(stopTimeoutMillis), SearchScope.NamesLoading)

private suspend fun searchAirports(
    query: String,
    indexProvider: AirportIndexProvider,
    airportRepository: AirportRepository,
    defaultDispatcher: CoroutineDispatcher,
): List<Airport> {
    val index = runCatchingCancellable { indexProvider.get() }.getOrNull() ?: return emptyList()
    val slots = if (query.isBlank()) {
        suggestionSlots(index)
    } else {
        // Null when the name index has not finished building, which degrades
        // search to codes only rather than blocking a keystroke on a
        // full-table read — see AirportRepository.prepareNameIndex.
        val names = airportRepository.nameIndexOrNull()
        withContext(defaultDispatcher) {
            AirportSlotSearch.rank(
                query = query,
                codes = index.codes,
                size = index.size,
                names = names?.names,
                municipalities = names?.municipalities,
            )
        }
    }
    return runCatchingCancellable { airportRepository.airportsForSlots(index, slots) }.getOrDefault(emptyList())
}

/**
 * What to show before anything has been typed: the largest airports.
 *
 * The index is sorted ascending by runway length, so the longest runways are
 * the last slots — which is why this walks backwards rather than taking the
 * head.
 */
private fun suggestionSlots(index: AirportIndex): IntArray {
    val count = minOf(AirportSlotSearch.DEFAULT_LIMIT, index.size)
    return IntArray(count) { index.size - 1 - it }
}

/** [runCatching] that lets cancellation through — see `PlanViewModel` for why. */
private inline fun <T> runCatchingCancellable(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (failure: Throwable) {
    Result.failure(failure)
}

/**
 * Default for [rankedAirportResults]/[airportSearchScope]'s `stopTimeoutMillis`.
 * Every current caller passes its own `STOP_TIMEOUT_MILLIS` explicitly instead
 * of relying on this — the same value, but named where retuning a ViewModel's
 * own constant is guaranteed to reach these flows too.
 */
private const val STOP_TIMEOUT_MILLIS = 5_000L

/**
 * Typing pause before a search runs, shared by every caller — below roughly
 * this the scan restarts more often than it can finish on a mid-range device,
 * and above it the list visibly lags the keyboard. Not caller-configurable
 * like [STOP_TIMEOUT_MILLIS]: it is a property of the scan itself, not of
 * whichever screen happens to be asking for one.
 */
private const val SEARCH_DEBOUNCE_MILLIS = 120L
