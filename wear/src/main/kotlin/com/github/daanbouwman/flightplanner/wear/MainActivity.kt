package com.github.daanbouwman.flightplanner.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.MaterialTheme
import com.github.daanbouwman.flightplanner.wear.route.WatchRouteFeedViewModel
import com.github.daanbouwman.flightplanner.wear.ui.RouteFeedScreen
import com.github.daanbouwman.flightplanner.wear.ui.theme.WearFlightPlannerTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * The watch app's only screen.
 *
 * No splash screen and no keep-on-screen condition, unlike `:app`. The phone
 * holds its splash until the index and the stored theme settle, because it has
 * both and a user who would otherwise see a half-drawn Plan; here the face has
 * a loading state of its own that says what it is doing, which on a screen this
 * size is the better answer than a held splash the wearer cannot dismiss.
 *
 * No `enableEdgeToEdge` either: a Wear activity is already the whole round
 * display, and there are no system bars to draw behind.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val feed: WatchRouteFeedViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            WearFlightPlannerTheme {
                val state by feed.state.collectAsStateWithLifecycle()
                RouteFeedScreen(
                    state = state,
                    handoffs = feed.handoffs,
                    onPageSettled = feed::onPageSettled,
                    onOpenOnPhone = feed::openOnPhone,
                    // The face is drawn edge to edge over the theme's background,
                    // which on this OLED display is true black — see
                    // `WearBrandColorScheme`.
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                )
            }
        }
    }
}
