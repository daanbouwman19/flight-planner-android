package com.github.daanbouwman.flightplanner.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.daanbouwman.flightplanner.navigation.Destination
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The route shown in the detail *pane*, beside the list it was chosen from.
 *
 * The same job [RouteDetailViewModel] does for the full screen, minus the one
 * thing a pane cannot have: navigation arguments. A pane is not a destination, so
 * there is no `SavedStateHandle` to read a route out of — the selection arrives
 * from the list beside it, and can change many times without anything navigating.
 * That is the whole reason the loading lives in [RouteDetailLoader] rather than in
 * either ViewModel.
 *
 * A null state means nothing is selected yet, which is a real state here and
 * cannot happen on the full screen: a pane exists before it has been given
 * anything to show.
 */
@HiltViewModel
class RouteDetailPaneViewModel @Inject constructor(
    private val loader: RouteDetailLoader,
) : ViewModel() {

    private val _state = MutableStateFlow<RouteDetailUiState?>(null)
    val state: StateFlow<RouteDetailUiState?> = _state.asStateFlow()

    /**
     * The load in flight, cancelled when a different route is chosen.
     *
     * Without this, choosing three routes quickly leaves three reads racing and
     * the pane settles on whichever database call happened to finish last, which
     * is not necessarily the one the user is looking at.
     */
    private var loading: Job? = null

    fun select(route: Destination.RouteDetail) {
        loading?.cancel()
        // The distance came with the selection, so the pane can state it while
        // the airports are still being read — exactly as the full screen does.
        _state.value = RouteDetailUiState(distanceNm = route.distanceNm)
        loading = viewModelScope.launch {
            val loaded = loader.load(route)
            _state.value = loaded
            _state.value = loader.withRunways(loaded)
        }
    }

    /** Empties the pane — used when the selected route leaves the list. */
    fun clear() {
        loading?.cancel()
        _state.value = null
    }
}
