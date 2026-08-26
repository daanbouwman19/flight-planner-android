package com.github.daanbouwman.flightplanner.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.daanbouwman.flightplanner.core.database.airport.DatasetMetaDao
import com.github.daanbouwman.flightplanner.core.designsystem.theme.ThemeChoice
import com.github.daanbouwman.flightplanner.model.DatasetMetaKeys
import com.github.daanbouwman.flightplanner.settings.AppSettings
import com.github.daanbouwman.flightplanner.settings.SettingsRepository
import com.github.daanbouwman.flightplanner.settings.UnitSystem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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
    private val datasetMetaDao: DatasetMetaDao,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = repository.settings
        .map { it ?: AppSettings() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = repository.settings.value ?: AppSettings(),
        )

    private val _datasetInfo = MutableStateFlow<DatasetInfo?>(null)

    /** The shipped dataset's provenance, read once — it never changes within a process. */
    val datasetInfo: StateFlow<DatasetInfo?> = _datasetInfo.asStateFlow()

    init {
        viewModelScope.launch {
            // One query for all five rows, not one query per key: the table
            // is small enough that this was never about row count, only about
            // not paying four SQL round trips for what one already returns.
            val meta = datasetMetaDao.all().associate { it.key to it.value }
            _datasetInfo.value = DatasetInfo(
                source = meta[DatasetMetaKeys.SOURCE] ?: "unknown",
                upstreamModified = meta[DatasetMetaKeys.UPSTREAM_MODIFIED] ?: "unknown",
                airportCount = meta[DatasetMetaKeys.AIRPORT_COUNT]?.toIntOrNull() ?: 0,
                runwayCount = meta[DatasetMetaKeys.RUNWAY_COUNT]?.toIntOrNull() ?: 0,
            )
        }
    }

    fun setThemeChoice(choice: ThemeChoice) = repository.setThemeChoice(choice)

    fun setDynamicColour(enabled: Boolean) = repository.setDynamicColour(enabled)

    fun setUnitSystem(system: UnitSystem) = repository.setUnitSystem(system)

    fun setIcaoOnly(enabled: Boolean) = repository.setIcaoOnly(enabled)

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

/** The shipped airport dataset's provenance, for the About section. */
data class DatasetInfo(
    val source: String,
    val upstreamModified: String,
    val airportCount: Int,
    val runwayCount: Int,
)
