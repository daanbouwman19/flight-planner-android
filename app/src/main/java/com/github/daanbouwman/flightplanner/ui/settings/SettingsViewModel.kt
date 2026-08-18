package com.github.daanbouwman.flightplanner.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.daanbouwman.flightplanner.core.designsystem.theme.ThemeChoice
import com.github.daanbouwman.flightplanner.settings.AppSettings
import com.github.daanbouwman.flightplanner.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * The appearance settings, as the screen needs them.
 *
 * The repository publishes `null` while the stored preferences are still being
 * read — a distinction `MainActivity` needs, because it holds the splash for it.
 * A settings *screen* has no use for it: by the time anyone navigates here the
 * read has long finished, and a screen that renders "unknown" for a frame is
 * worse than one that renders the defaults. So the null is resolved here, once.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = repository.settings
        .map { it ?: AppSettings() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = repository.settings.value ?: AppSettings(),
        )

    fun setThemeChoice(choice: ThemeChoice) = repository.setThemeChoice(choice)

    fun setDynamicColour(enabled: Boolean) = repository.setDynamicColour(enabled)

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
