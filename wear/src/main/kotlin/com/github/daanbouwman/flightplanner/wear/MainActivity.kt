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
import com.github.daanbouwman.flightplanner.wear.theme.WatchThemeViewModel
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
 *
 * The theme is the phone's, read over the Data Layer — see [WatchThemeViewModel]
 * for why the first frame is drawn in the default rather than waited for.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val feed: WatchRouteFeedViewModel by viewModels()

    /**
     * The look the phone published. Held in a ViewModel of its own rather than
     * folded into [feed] because the two answer different questions and settle
     * at different times: the theme wraps the whole face, including the loading
     * and unavailable states the feed is still deciding between.
     */
    private val appearance: WatchThemeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val theme by appearance.theme.collectAsStateWithLifecycle()
            WearFlightPlannerTheme(state = theme) {
                val state by feed.state.collectAsStateWithLifecycle()
                RouteFeedScreen(
                    state = state,
                    handoffs = feed.handoffs,
                    onPageSettled = feed::onPageSettled,
                    onOpenOnPhone = feed::openOnPhone,
                    // The face is drawn edge to edge over the theme's own
                    // background, which every theme but Chart takes to true
                    // black on this OLED display — see `WearFlightPlannerTheme`.
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                )
            }
        }
    }
}
