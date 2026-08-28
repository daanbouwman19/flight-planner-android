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
    /** Nautical/feet/knots vs. kilometres/metres/km-h. See [UnitSystem]. */
    val unitSystem: UnitSystem = UnitSystem.AVIATION,
    /**
     * Restricts route generation to airports with a real ICAO code.
     *
     * Defaults **off**, matching [com.github.daanbouwman.flightplanner.routing.RouteGenerator]'s
     * own default — enabling this is an opt-in narrowing of the pool, not a
     * correction to previous behaviour.
     */
    val icaoOnly: Boolean = false,
    /** Which service resolves METAR/flight-rules data. See [WeatherProvider]. */
    val weatherProvider: WeatherProvider = WeatherProvider.NOAA,
    /**
     * Masked in the UI. Null or blank means "no key set", which keeps the
     * app on NOAA regardless of [weatherProvider] — an explicit AVWX choice
     * with no key must read as "no weather", not silently fall back.
     */
    val avwxApiKey: String? = null,
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
 * An interface — like `AirportRepository`, `FleetRepository` and
 * `LogbookRepository` in `:core:database` — so that `PlanViewModel` (icaoOnly)
 * and any other consumer can be unit-tested against a fake rather than a real
 * DataStore, which needs a `Context`. See `di/SettingsModule.kt` for the `@Binds`.
 */
interface SettingsRepository {
    val settings: StateFlow<AppSettings?>
    fun setThemeChoice(choice: ThemeChoice)
    fun setDynamicColour(enabled: Boolean)
    fun setUnitSystem(system: UnitSystem)
    fun setIcaoOnly(enabled: Boolean)
    fun setWeatherProvider(provider: WeatherProvider)
    /** A blank or null [key] clears the stored value. */
    fun setAvwxApiKey(key: String?)
}

/**
 * The read is a single small file and it runs in parallel with the airport
 * index, so it costs nothing measurable against the cold-start budget.
 */
@Singleton
internal class DefaultSettingsRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:ApplicationScope private val scope: CoroutineScope,
) : SettingsRepository {
    private val store = context.settingsStore

    override val settings: StateFlow<AppSettings?> = store.data
        // A corrupt or unreadable preferences file must not take the app down
        // over a colour scheme; the defaults are a perfectly good app.
        .catch { failure -> if (failure is IOException) emit(emptyPreferences()) else throw failure }
        .map { preferences ->
            AppSettings(
                themeChoice = preferences[THEME]?.let(::themeChoiceOf) ?: ThemeChoice.SYSTEM,
                dynamicColour = preferences[DYNAMIC_COLOUR] ?: true,
                unitSystem = preferences[UNIT_SYSTEM]?.let(::unitSystemOf) ?: UnitSystem.AVIATION,
                icaoOnly = preferences[ICAO_ONLY] ?: false,
                weatherProvider = preferences[WEATHER_PROVIDER]?.let(::weatherProviderOf) ?: WeatherProvider.NOAA,
                avwxApiKey = preferences[AVWX_API_KEY],
            )
        }
        .stateIn(scope, SharingStarted.Eagerly, null)

    override fun setThemeChoice(choice: ThemeChoice) {
        scope.launch { store.edit { it[THEME] = choice.name } }
    }

    override fun setDynamicColour(enabled: Boolean) {
        scope.launch { store.edit { it[DYNAMIC_COLOUR] = enabled } }
    }

    override fun setUnitSystem(system: UnitSystem) {
        scope.launch { store.edit { it[UNIT_SYSTEM] = system.name } }
    }

    override fun setIcaoOnly(enabled: Boolean) {
        scope.launch { store.edit { it[ICAO_ONLY] = enabled } }
    }

    override fun setWeatherProvider(provider: WeatherProvider) {
        scope.launch { store.edit { it[WEATHER_PROVIDER] = provider.name } }
    }

    override fun setAvwxApiKey(key: String?) {
        scope.launch {
            store.edit {
                if (key.isNullOrBlank()) it.remove(AVWX_API_KEY) else it[AVWX_API_KEY] = key
            }
        }
    }

    /** Tolerates a stored name that no longer exists, e.g. after a rename. */
    private fun themeChoiceOf(name: String): ThemeChoice =
        ThemeChoice.entries.firstOrNull { it.name == name } ?: ThemeChoice.SYSTEM

    /** Tolerates a stored name that no longer exists, e.g. after a rename. */
    private fun unitSystemOf(name: String): UnitSystem =
        UnitSystem.entries.firstOrNull { it.name == name } ?: UnitSystem.AVIATION

    /** Tolerates a stored name that no longer exists, e.g. after a rename. */
    private fun weatherProviderOf(name: String): WeatherProvider =
        WeatherProvider.entries.firstOrNull { it.name == name } ?: WeatherProvider.NOAA

    private companion object {
        val THEME = stringPreferencesKey("theme_choice")
        val DYNAMIC_COLOUR = booleanPreferencesKey("dynamic_colour")
        val UNIT_SYSTEM = stringPreferencesKey("unit_system")
        val ICAO_ONLY = booleanPreferencesKey("icao_only")
        val WEATHER_PROVIDER = stringPreferencesKey("weather_provider")
        val AVWX_API_KEY = stringPreferencesKey("avwx_api_key")
    }
}
