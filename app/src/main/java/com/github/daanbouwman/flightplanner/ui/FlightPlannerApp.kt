package com.github.daanbouwman.flightplanner.ui

import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteItem
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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

/**
 * The application shell: navigation affordance plus the graph it drives.
 *
 * `NavigationSuiteScaffold` is what makes one shell serve a phone, a foldable
 * and a tablet. It picks a bottom bar, a collapsed rail or an expanded rail from
 * the current window size, so there is no width branching here and no second
 * layout to keep in step — the only decision this file makes is *whether* to
 * show navigation at all.
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

    NavigationSuiteScaffold(
        navigationItems = {
            TopLevelDestination.inBar.forEach { destination ->
                val selected = currentDestination.isIn(destination)
                val label = stringResource(destination.labelRes)
                NavigationSuiteItem(
                    selected = selected,
                    onClick = { navController.navigateToTopLevel(destination) },
                    icon = {
                        Icon(
                            painter = painterResource(destination.iconRes),
                            // The label is shown alongside in every suite type this
                            // app uses, so describing the icon too would make
                            // TalkBack read the destination twice.
                            contentDescription = null,
                        )
                    },
                    label = { Text(label) },
                )
            }
        },
        modifier = modifier,
        navigationSuiteType = navigationSuiteType,
    ) {
        FlightPlannerNavHost(navController = navController)
    }
}
