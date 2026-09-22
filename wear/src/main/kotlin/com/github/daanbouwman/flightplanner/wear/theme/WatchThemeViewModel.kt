package com.github.daanbouwman.flightplanner.wear.theme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.daanbouwman.flightplanner.handoff.WatchThemeState
import com.github.daanbouwman.flightplanner.handoff.WatchThemeSync
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Holds the theme across the face's recompositions and its screen going off.
 *
 * The state starts at [WatchThemeSync.Unsynced] rather than at a null "not read
 * yet", which is the opposite of what the phone does — there, `MainActivity`
 * holds the splash screen until the stored theme settles, precisely so the first
 * frame is not drawn in the wrong colours. A watch has no splash to hold and a
 * face that cannot be held back, so the honest thing is to draw the default
 * immediately and recolour when the item arrives a few milliseconds later. The
 * default is dark and so is every theme but Chart, so in practice there is
 * nothing to see.
 *
 * Composed against the internal seam constructor in tests, so no Play Services
 * and no paired phone — the same shape as `WatchRouteFeedViewModel`.
 */
@HiltViewModel
class WatchThemeViewModel internal constructor(theme: Flow<WatchThemeState>) : ViewModel() {

    @Inject
    constructor(source: WatchThemeSource) : this(theme = source.theme)

    /**
     * [SharingStarted.Eagerly] because this is one small flow behind the whole
     * app: dropping the Data Layer listener every time the wearer glances away
     * and re-reading on the way back would cost more than holding it.
     */
    val theme: StateFlow<WatchThemeState> =
        theme.stateIn(viewModelScope, SharingStarted.Eagerly, WatchThemeSync.Unsynced)
}
