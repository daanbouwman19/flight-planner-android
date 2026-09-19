package com.github.daanbouwman.flightplanner

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.daanbouwman.flightplanner.core.database.airport.AirportAssetInstaller
import com.github.daanbouwman.flightplanner.core.designsystem.theme.FlightPlannerTheme
import com.github.daanbouwman.flightplanner.index.AirportIndexProvider
import com.github.daanbouwman.flightplanner.launch.LaunchIntents
import com.github.daanbouwman.flightplanner.launch.LaunchViewModel
import com.github.daanbouwman.flightplanner.settings.AppSettings
import com.github.daanbouwman.flightplanner.settings.SettingsRepository
import com.github.daanbouwman.flightplanner.startup.splashShouldHold
import com.github.daanbouwman.flightplanner.ui.FlightPlannerApp
import com.github.daanbouwman.flightplanner.ui.LocalUnitSystem
import com.github.daanbouwman.flightplanner.widget.PublishWidgetPreview
import com.github.daanbouwman.flightplanner.widget.RefreshWidgetsOnFleetChange
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var airportIndexProvider: AirportIndexProvider

    @Inject
    lateinit var airportAssetInstaller: AirportAssetInstaller

    @Inject
    lateinit var settingsRepository: SettingsRepository

    /**
     * Where an arriving Intent's request waits for the NavHost. See
     * [LaunchViewModel] for why it is a ViewModel and not a field here.
     */
    private val launch: LaunchViewModel by viewModels()

    @OptIn(ExperimentalComposeUiApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        enableEdgeToEdge()
        // Hilt injects during super.onCreate, so the provider is only safe to
        // read after this line — hence the keep-on-screen condition below it.
        super.onCreate(savedInstanceState)

        // Only a *fresh* activity reads its Intent. After process death the
        // system hands the same launch Intent back with a saved state, and the
        // NavController restores the stack that Intent already produced — so
        // parsing it again would replay a shortcut on top of its own result.
        if (savedInstanceState == null) {
            launch.offer(LaunchIntents.parse(intent))
        }

        // The index build and the database install were started in
        // Application.onCreate and normally settle inside single-digit
        // milliseconds, so this condition usually never holds the splash at all.
        // The rule itself — which of the three waits the deadline bounds, and
        // why the install is the one it does not — is `splashShouldHold`, kept
        // pure so it is tested rather than trusted. In short: the deadline caps
        // the index and the stored theme, because for them it is what turns a
        // corrupt asset into skeletons instead of an infinite splash; the
        // install is waited out, because past the deadline the first DAO
        // request would block the main thread for the rest of the copy behind
        // a half-drawn Plan, and `Failed` counts as settled so a broken asset
        // still releases it.
        val deadline = SystemClock.uptimeMillis() + SPLASH_HOLD_MILLIS
        splashScreen.setKeepOnScreenCondition {
            splashShouldHold(
                indexSettled = airportIndexProvider.isSettled,
                installSettled = airportAssetInstaller.isSettled,
                settingsLoaded = settingsRepository.settings.value != null,
                now = SystemClock.uptimeMillis(),
                deadline = deadline,
            )
        }

        setContent {
            val settings by settingsRepository.settings.collectAsStateWithLifecycle()
            val resolved = settings ?: AppSettings()
            CompositionLocalProvider(LocalUnitSystem provides resolved.unitSystem) {
                FlightPlannerTheme(
                    themeChoice = resolved.themeChoice,
                    dynamicColor = resolved.dynamicColour,
                ) {
                    // Publishes every `Modifier.testTag` in the tree as an
                    // accessibility resource id, which is the only way UiAutomator
                    // — and therefore `:macrobenchmark` — can address a Compose node
                    // by name rather than by guessing at the first scrollable thing
                    // it finds. It adds a string to nodes that already carry
                    // semantics and changes nothing a user can observe.
                    FlightPlannerApp(
                        modifier = Modifier.semantics { testTagsAsResourceId = true },
                        launchRequests = launch,
                    )
                    // After the app, so it composes after the first frame's
                    // content and never ahead of it.
                    PublishWidgetPreview()
                    RefreshWidgetsOnFleetChange()
                }
            }
        }
    }

    /**
     * A widget tap or a shortcut while the app is already running. The
     * manifest's `singleTask` is what routes it here rather than to a second
     * instance; `setIntent` keeps `getIntent()` truthful for anything that
     * asks later.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        launch.offer(LaunchIntents.parse(intent))
    }

    private companion object {
        /**
         * Long enough to absorb a cold read of the index asset off slow storage,
         * short enough that a user never experiences it as a wait.
         */
        const val SPLASH_HOLD_MILLIS = 800L
    }
}
