package com.github.daanbouwman.flightplanner.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.github.daanbouwman.flightplanner.core.designsystem.theme.ThemeChoice
import com.github.daanbouwman.flightplanner.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** What the user has chosen about how the app looks. */
data class AppSettings(
    val themeChoice: ThemeChoice = ThemeChoice.SYSTEM,
    /**
     * Whether the scheme is derived from the wallpaper.
     *
     * Defaults **on**, which is the platform's own expectation — but it is now a
     * choice the user can reverse, which is the point. Before this existed the
     * brand scheme was unreachable: the app always called the theme with its
     * defaults, so the avgas-blue palette and the Cockpit theme were code nobody
     * could ever see.
     */
    val dynamicColour: Boolean = true,
)

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Reads and writes the appearance settings.
 *
 * Held as a [StateFlow] with a **nullable** value on purpose: null means "not
 * read yet", and `MainActivity` holds the splash screen for it. Without that
 * distinction the first frame would draw with the defaults and then flip to the
 * user's actual theme a few milliseconds later — a light flash in front of
 * someone who chose Cockpit precisely so they would not get one.
 *
 * The read is a single small file and it runs in parallel with the airport
 * index, so it costs nothing measurable against the cold-start budget.
 */
@Singleton
class SettingsRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    private val store = context.settingsStore

    val settings: StateFlow<AppSettings?> = store.data
        // A corrupt or unreadable preferences file must not take the app down
        // over a colour scheme; the defaults are a perfectly good app.
        .catch { failure -> if (failure is IOException) emit(emptyPreferences()) else throw failure }
        .map { preferences ->
            AppSettings(
                themeChoice = preferences[THEME]?.let(::themeChoiceOf) ?: ThemeChoice.SYSTEM,
                dynamicColour = preferences[DYNAMIC_COLOUR] ?: true,
            )
        }
        .stateIn(scope, SharingStarted.Eagerly, null)

    fun setThemeChoice(choice: ThemeChoice) {
        scope.launch { store.edit { it[THEME] = choice.name } }
    }

    fun setDynamicColour(enabled: Boolean) {
        scope.launch { store.edit { it[DYNAMIC_COLOUR] = enabled } }
    }

    /** Tolerates a stored name that no longer exists, e.g. after a rename. */
    private fun themeChoiceOf(name: String): ThemeChoice =
        ThemeChoice.entries.firstOrNull { it.name == name } ?: ThemeChoice.SYSTEM

    private companion object {
        val THEME = stringPreferencesKey("theme_choice")
        val DYNAMIC_COLOUR = booleanPreferencesKey("dynamic_colour")
    }
}
