package com.github.daanbouwman.flightplanner.ui.goldens

import androidx.compose.runtime.Composable
import com.github.daanbouwman.flightplanner.core.designsystem.theme.ThemeChoice
import com.github.daanbouwman.flightplanner.settings.AppSettings
import com.github.daanbouwman.flightplanner.settings.WeatherProvider
import com.github.daanbouwman.flightplanner.ui.settings.SettingsContent
import com.github.daanbouwman.flightplanner.ui.settings.previewDatasetInfo
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * Settings at its defaults, with Cockpit chosen (the dynamic-colour switch goes
 * inert and says why), and with AVWX chosen (the key field appears). The globe
 * line reads "no renderer" under Robolectric; see [GoldenSuite].
 */
class SettingsGoldens : GoldenSuite(subject = "settings", primaryState = "defaults") {

    @Composable
    override fun Primary() = Settings(AppSettings())

    @Test
    @Config(qualifiers = GoldenQualifiers.COMPACT)
    fun cockpitSelected() = captureState("cockpit-selected") {
        Settings(AppSettings(themeChoice = ThemeChoice.COCKPIT))
    }

    @Test
    @Config(qualifiers = GoldenQualifiers.COMPACT)
    fun avwx() = captureState("avwx") {
        Settings(AppSettings(weatherProvider = WeatherProvider.AVWX, avwxApiKey = "0123456789abcdef"))
    }

    @Composable
    private fun Settings(settings: AppSettings) = SettingsContent(
        settings = settings,
        datasetInfo = previewDatasetInfo,
        onBack = {},
        onOpenSelfCheck = {},
        onOpenLicences = {},
        onThemeChoice = {},
        onDynamicColour = {},
        onUnitSystem = {},
        onIcaoOnly = {},
        onWeatherProvider = {},
        onAvwxApiKey = {},
    )
}
