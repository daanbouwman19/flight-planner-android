package com.github.daanbouwman.flightplanner.ui.airports

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.daanbouwman.flightplanner.core.database.repository.AirportRepository
import com.github.daanbouwman.flightplanner.di.DefaultDispatcher
import com.github.daanbouwman.flightplanner.index.AirportIndexProvider
import com.github.daanbouwman.flightplanner.model.Airport
import com.github.daanbouwman.flightplanner.routing.RandomAirportSample
import com.github.daanbouwman.flightplanner.search.airportSearchScope
import com.github.daanbouwman.flightplanner.search.rankedAirportResults
import com.github.daanbouwman.flightplanner.ui.plan.SearchScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

private const val TAG = "AirportsViewModel"

/** What the Airports browse screen shows: the field's own text, the list beneath it, and why. */
data class AirportsUiState(
    val query: String = "",
    val airports: List<Airport> = emptyList(),
    /** True when [airports] is a Random-50 batch rather than suggestions or ranked matches. */
    val isRandomBatch: Boolean = false,
    val searchScope: SearchScope = SearchScope.Full,
)

/**
 * The Airports browse screen (E1): ranked type-ahead over the whole airport
 * index, plus a "Random" action that samples a fresh batch of
 * [RandomAirportSample.BATCH_SIZE] on every tap.
 *
 * The type-ahead is [rankedAirportResults], the same function
 * [com.github.daanbouwman.flightplanner.ui.plan.PlanViewModel] and
 * [com.github.daanbouwman.flightplanner.ui.logbook.LogbookViewModel] already
 * call for their own airport pickers — a blank query already returns the
 * largest airports, which doubles as this screen's browse list. Random mode
 * is a separate batch layered over it: typing exits random mode, and a fresh
 * tap of Random replaces whatever batch was showing rather than caching it,
 * matching the desktop reference's own `RandomAirports` mode.
 */
@HiltViewModel
class AirportsViewModel @Inject constructor(
    private val indexProvider: AirportIndexProvider,
    private val airportRepository: AirportRepository,
    @param:DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val randomBatch = MutableStateFlow<List<Airport>?>(null)

    private val ranked: StateFlow<List<Airport>> = rankedAirportResults(
        viewModelScope,
        query,
        indexProvider,
        airportRepository,
        defaultDispatcher,
        stopTimeoutMillis = STOP_TIMEOUT_MILLIS,
    )

    private val scope: StateFlow<SearchScope> =
        airportSearchScope(viewModelScope, airportRepository, stopTimeoutMillis = STOP_TIMEOUT_MILLIS)

    val state: StateFlow<AirportsUiState> = combine(query, ranked, randomBatch, scope) {
            currentQuery, rankedList, random, searchScope ->
        AirportsUiState(
            query = currentQuery,
            airports = random ?: rankedList,
            isRandomBatch = random != null,
            searchScope = searchScope,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), AirportsUiState())

    /**
     * The in-flight [randomize] coroutine, if any.
     *
     * [randomize] suspends across an index load, a background sample and a
     * database query before it writes anything — long enough that a query
     * typed in the meantime would otherwise race it: the stale batch would
     * land *after* the typed query, silently overwriting both the query and
     * the search results it was showing. Cancelling here, from both
     * [setQuery] and a fresh [randomize] call, is what keeps only the most
     * recent intent able to write [query]/[randomBatch].
     */
    private var randomizeJob: Job? = null

    fun setQuery(text: String) {
        randomizeJob?.cancel()
        randomBatch.value = null
        query.value = text
    }

    /** Samples a fresh batch of [RandomAirportSample.BATCH_SIZE] airports, replacing any prior batch. */
    fun randomize() {
        randomizeJob?.cancel()
        randomizeJob = viewModelScope.launch {
            val index = runCatchingCancellable { indexProvider.get() }
                .onFailure { Log.e(TAG, "Loading the airport index failed", it) }
                .getOrNull() ?: return@launch

            val slots = withContext(defaultDispatcher) {
                RandomAirportSample.sampleSlots(index.size, RandomAirportSample.BATCH_SIZE)
            }
            query.value = ""
            randomBatch.value = runCatchingCancellable { airportRepository.airportsForSlots(index, slots) }
                .onFailure { Log.e(TAG, "Sampling random airports failed", it) }
                .getOrNull()
        }
    }

    /** As [com.github.daanbouwman.flightplanner.ui.plan.PlanViewModel.retryNameSearch]. */
    fun retryNameSearch() {
        viewModelScope.launch {
            runCatchingCancellable { airportRepository.prepareNameIndex(indexProvider.get()) }
                .onFailure { Log.w(TAG, "Retrying the airport name index failed", it) }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

/** [runCatching] that lets cancellation through. See [com.github.daanbouwman.flightplanner.ui.plan.PlanViewModel] for why. */
private inline fun <T> runCatchingCancellable(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (failure: Throwable) {
    Result.failure(failure)
}
