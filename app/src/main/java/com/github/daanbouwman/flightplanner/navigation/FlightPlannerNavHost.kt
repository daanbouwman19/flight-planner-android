package com.github.daanbouwman.flightplanner.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.github.daanbouwman.flightplanner.core.designsystem.motion.FlightMotion
import com.github.daanbouwman.flightplanner.startup.StartupCheckScreen
import com.github.daanbouwman.flightplanner.ui.AirportsScreen
import com.github.daanbouwman.flightplanner.ui.FleetScreen
import com.github.daanbouwman.flightplanner.ui.LogbookScreen
import com.github.daanbouwman.flightplanner.ui.RouteDetailScreen
import com.github.daanbouwman.flightplanner.ui.SettingsScreen
import com.github.daanbouwman.flightplanner.ui.StatsScreen
import com.github.daanbouwman.flightplanner.ui.plan.PlanScreen

/**
 * The whole graph. Screens are placeholders until their phase builds them; the
 * shape of the graph is what is being established here.
 */
@Composable
fun FlightPlannerNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    // Resolved once, outside the transition lambdas: those lambdas are not
    // composable, so a motion token — which is — has to be read here and closed
    // over. This is also the only place :app decides how a screen change looks,
    // and it decides by naming a token rather than by writing a spec.
    val enter = FlightMotion.navEnter()
    val exit = FlightMotion.navExit()

    NavHost(
        navController = navController,
        startDestination = Destination.Plan,
        modifier = modifier,
        enterTransition = { enter },
        exitTransition = { exit },
        popEnterTransition = { enter },
        popExitTransition = { exit },
    ) {
        // Settings left the navigation bar, so every section's app bar carries the
        // way to it. One lambda, defined once, rather than a parameter threaded
        // through each screen's own navigation logic.
        //
        // A plain `navigate`, *not* `navigateToTopLevel`. That helper pops back to
        // the start destination, which is right for a bar item — switching tabs
        // should not stack them up — and wrong here now that Settings is reached
        // from within a section: it would throw that section away, so back from
        // Settings landed on Plan instead of on the screen the user opened it from.
        val openSettings = { navController.navigate(Destination.Settings) { launchSingleTop = true } }

        composable<Destination.Plan> {
            PlanScreen(
                onOpenSettings = openSettings,
                onOpenRoute = { row ->
                    navController.navigateToDetail(
                        Destination.RouteDetail(
                            departureIcao = row.departure.icao,
                            destinationIcao = row.destination.icao,
                            aircraftId = row.aircraft.id,
                            distanceNm = row.distanceNm,
                        ),
                    )
                },
            )
        }
        composable<Destination.Logbook> { LogbookScreen(onOpenSettings = openSettings) }
        composable<Destination.Fleet> { FleetScreen(onOpenSettings = openSettings) }
        composable<Destination.Airports> { AirportsScreen(onOpenSettings = openSettings) }
        composable<Destination.Stats> { StatsScreen(onOpenSettings = openSettings) }
        composable<Destination.Settings> {
            SettingsScreen(onOpenSelfCheck = { navController.navigate(Destination.SelfCheck) })
        }

        composable<Destination.RouteDetail> { entry ->
            RouteDetailScreen(
                route = entry.toRoute<Destination.RouteDetail>(),
                onBack = { navController.popBackStack() },
            )
        }

        // Kept whole rather than restyled: it is a diagnostic, and its value is
        // that it reports exactly what it reported before the UI existed.
        composable<Destination.SelfCheck> { StartupCheckScreen() }
    }
}

/**
 * Switches top-level destination the way a navigation bar is expected to
 * behave: one entry per section on the back stack, each section's scroll
 * position and state preserved, and back from anywhere returning to the start
 * destination rather than walking every tab visited.
 */
fun NavHostController.navigateToTopLevel(destination: TopLevelDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun NavHostController.navigateToDetail(detail: Destination.RouteDetail) {
    navigate(detail) { launchSingleTop = true }
}

/**
 * Whether [destination] is the section currently shown.
 *
 * It asks the hierarchy rather than the leaf so that a nested destination inside
 * a section still lights up that section's bar item.
 */
fun NavDestination?.isIn(destination: TopLevelDestination): Boolean =
    this?.hierarchy?.any { it.hasRoute(destination.route::class) } == true
