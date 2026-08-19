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
 * A selection and whatever has been read about it so far.
 *
 * The two travel together because the *selection* is the pane's identity and the
 * loaded detail is only its content. Keying a transition on the content instead
 * looked equivalent and was not: the first of three publishes has no airports yet,
 * so a key derived from them read `null>null@null` and every selection cross-faded
 * twice — out of the old route, into a blank skeleton headed " to ", and only then
 * into the new one. Three overlapping copies is precisely the smear the key exists
 * to avoid.
 *
 * [route] is known the instant the user taps, and never changes for a given
 * selection. That is what makes it an identity.
 */
data class RouteDetailPaneState(
    val route: Destination.RouteDetail,
    val detail: RouteDetailUiState,
)

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

    private val _state = MutableStateFlow<RouteDetailPaneState?>(null)
    val state: StateFlow<RouteDetailPaneState?> = _state.asStateFlow()

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
        _state.value = RouteDetailPaneState(route, RouteDetailUiState(distanceNm = route.distanceNm))
        loading = viewModelScope.launch {
            val loaded = loader.load(route)
            _state.value = RouteDetailPaneState(route, loaded)
            _state.value = RouteDetailPaneState(route, loader.withRunways(loaded))
        }
    }

    /** Empties the pane — used when the selected route leaves the list. */
    fun clear() {
        loading?.cancel()
        _state.value = null
    }
}
