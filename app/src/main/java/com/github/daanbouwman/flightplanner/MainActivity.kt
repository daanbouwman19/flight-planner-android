package com.github.daanbouwman.flightplanner

import android.os.Bundle
import android.os.SystemClock
import com.github.daanbouwman.flightplanner.settings.AppSettings
import com.github.daanbouwman.flightplanner.settings.SettingsRepository
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.github.daanbouwman.flightplanner.core.designsystem.theme.FlightPlannerTheme
import com.github.daanbouwman.flightplanner.index.AirportIndexProvider
import com.github.daanbouwman.flightplanner.ui.FlightPlannerApp
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var airportIndexProvider: AirportIndexProvider

    @Inject
    lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        enableEdgeToEdge()
        // Hilt injects during super.onCreate, so the provider is only safe to
        // read after this line — hence the keep-on-screen condition below it.
        super.onCreate(savedInstanceState)

        // The index build was started in Application.onCreate and normally
        // finishes inside single-digit milliseconds, so this condition usually
        // never holds the splash at all. The deadline is what makes it safe: an
        // uncapped `!ready` is an infinite splash the first time the asset is
        // missing or corrupt. Past the cap the app appears and shows skeletons,
        // which is a worse first frame but a recoverable one.
        val deadline = SystemClock.uptimeMillis() + SPLASH_HOLD_MILLIS
        // Also waits for the stored theme. Without it the first frame draws with
        // the defaults and flips a few milliseconds later — a light flash in front
        // of someone who chose Cockpit precisely so they would not get one. It is
        // one small file read, running in parallel with the index, under the same
        // deadline.
        splashScreen.setKeepOnScreenCondition {
            (!airportIndexProvider.isSettled || settingsRepository.settings.value == null) &&
                SystemClock.uptimeMillis() < deadline
        }

        setContent {
            val settings by settingsRepository.settings.collectAsStateWithLifecycle()
            val resolved = settings ?: AppSettings()
            FlightPlannerTheme(
                themeChoice = resolved.themeChoice,
                dynamicColor = resolved.dynamicColour,
            ) {
                FlightPlannerApp()
            }
        }
    }

    private companion object {
        /**
         * Long enough to absorb a cold read of the index asset off slow storage,
         * short enough that a user never experiences it as a wait.
         */
        const val SPLASH_HOLD_MILLIS = 800L
    }
}
