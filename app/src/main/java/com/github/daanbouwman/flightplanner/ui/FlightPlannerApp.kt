package com.github.daanbouwman.flightplanner.ui

import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteItem
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.material3.adaptive.navigationsuite.rememberNavigationSuiteScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.github.daanbouwman.flightplanner.navigation.FlightPlannerNavHost
import com.github.daanbouwman.flightplanner.navigation.TopLevelDestination
import com.github.daanbouwman.flightplanner.navigation.isIn
import com.github.daanbouwman.flightplanner.navigation.navigateToTopLevel
import com.github.daanbouwman.flightplanner.ui.chrome.AppChromeState
import com.github.daanbouwman.flightplanner.ui.chrome.LocalAppChromeState
import com.github.daanbouwman.flightplanner.ui.chrome.LocalNavigationReselect
import com.github.daanbouwman.flightplanner.ui.chrome.NavigationReselect

/**
 * The application shell: navigation affordance plus the graph it drives.
 *
 * `NavigationSuiteScaffold` is what makes one shell serve a phone, a foldable
 * and a tablet. It picks a bottom bar, a collapsed rail or an expanded rail from
 * the current window size, so there is no width branching here and no second
 * layout to keep in step — the only decisions this file makes are *whether* to
 * show navigation at all, and whether it is currently out of the way.
 *
 * ### Hiding the navigation bar
 *
 * The signal comes from whichever screen is on top, through [AppChromeState],
 * because it is a screen's scrolling that earns the height back. The scaffold's own
 * `state` does the animating and — importantly — stops consuming the bottom inset
 * while it is away, so the screen underneath gets that edge back without either of
 * them hard-coding who owns it.
 *
 * **Only a bottom bar hides.** A rail costs width, not height, so hiding it would
 * take nothing back from a list that is already as tall as the window and would
 * leave a tablet with no navigation for the sake of nothing.
 */
@Composable
fun FlightPlannerApp(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    // A detail screen is not a section, so it gets the whole window. Recomputing
    // the adaptive type on every recomposition is cheap; suppressing it while off
    // a top-level destination is what keeps the bar from sliding under a sheet.
    val onTopLevel = TopLevelDestination.entries.any { currentDestination.isIn(it) }
    // The V2 form of the adaptive info is the one that reports the large and
    // extra-large width classes; the original is deprecated for stopping at
    // "expanded", which would put a tablet and a desktop-sized window on the
    // same rail.
    val adaptiveType =
        NavigationSuiteScaffoldDefaults.navigationSuiteType(currentWindowAdaptiveInfoV2())
    val navigationSuiteType = if (onTopLevel) adaptiveType else NavigationSuiteType.None

    val chrome = remember { AppChromeState() }
    val reselect = remember { NavigationReselect() }
    val suiteState = rememberNavigationSuiteScaffoldState()

    // Only the horizontal forms give height back. `NavigationSuiteType` is not an
    // enum, so this is a comparison against the two bar types rather than a `when`
    // the compiler can check — if a third bar type appears, it has to be added here.
    val isBar = navigationSuiteType == NavigationSuiteType.ShortNavigationBarCompact ||
        navigationSuiteType == NavigationSuiteType.ShortNavigationBarMedium

    LaunchedEffect(chrome.visible, isBar, suiteState) {
        if (chrome.visible || !isBar) suiteState.show() else suiteState.hide()
    }

    // Any change of destination puts the chrome back. A section arrived at with its
    // navigation already hidden — because the section it was reached from had been
    // scrolled — is a screen the user cannot leave.
    LaunchedEffect(currentDestination?.route) { chrome.show() }

    CompositionLocalProvider(
        LocalAppChromeState provides chrome,
        LocalNavigationReselect provides reselect,
    ) {
        NavigationSuiteScaffold(
            navigationItems = {
                TopLevelDestination.inBar.forEach { destination ->
                    val selected = currentDestination.isIn(destination)
                    val label = stringResource(destination.labelRes)
                    NavigationSuiteItem(
                        selected = selected,
                        // Tapping the section you are already in navigates nowhere —
                        // `navigateToTopLevel` is a `launchSingleTop` no-op there — so
                        // it means "back to the top" instead, which is what the tap
                        // means everywhere else on Android.
                        onClick = {
                            if (selected) {
                                reselect.request()
                            } else {
                                navController.navigateToTopLevel(destination)
                            }
                        },
                        icon = {
                            Icon(
                                painter = painterResource(destination.iconRes),
                                // The label is shown alongside in every suite type
                                // this app uses, so describing the icon too would
                                // make TalkBack read the destination twice.
                                contentDescription = null,
                            )
                        },
                        label = { Text(label) },
                    )
                }
            },
            modifier = modifier,
            navigationSuiteType = navigationSuiteType,
            state = suiteState,
        ) {
            FlightPlannerNavHost(navController = navController)
        }
    }
}
