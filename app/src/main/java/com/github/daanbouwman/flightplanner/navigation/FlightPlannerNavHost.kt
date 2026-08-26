package com.github.daanbouwman.flightplanner.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.toRoute
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import com.github.daanbouwman.flightplanner.core.designsystem.motion.FlightMotion
import com.github.daanbouwman.flightplanner.ui.chrome.ProvideSharedRouteScopes
import com.github.daanbouwman.flightplanner.startup.StartupCheckScreen
import com.github.daanbouwman.flightplanner.ui.RouteDetailScreen
import com.github.daanbouwman.flightplanner.ui.airport.AirportDetailScreen
import com.github.daanbouwman.flightplanner.ui.airports.AirportsScreen
import com.github.daanbouwman.flightplanner.ui.fleet.FleetDetailScreen
import com.github.daanbouwman.flightplanner.ui.fleet.FleetRoute
import com.github.daanbouwman.flightplanner.ui.fleet.toFleetDetailDestination
import com.github.daanbouwman.flightplanner.model.AircraftSpec
import com.github.daanbouwman.flightplanner.model.Airport
import com.github.daanbouwman.flightplanner.ui.profile.ProfileScreen
import com.github.daanbouwman.flightplanner.ui.profile.ProfileSegment
import com.github.daanbouwman.flightplanner.ui.logbook.LogbookRoute
import com.github.daanbouwman.flightplanner.ui.logbook.LogbookRow
import com.github.daanbouwman.flightplanner.ui.settings.LicencesScreen
import com.github.daanbouwman.flightplanner.ui.settings.SettingsScreen
import com.github.daanbouwman.flightplanner.ui.stats.StatsRoute
import com.github.daanbouwman.flightplanner.ui.plan.PlanRoute
import com.github.daanbouwman.flightplanner.ui.plan.PlanViewModel

/**
 * The whole graph. Screens are placeholders until their phase builds them; the
 * shape of the graph is what is being established here.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
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

    // Plan and its detail share elements, so they get the un-scaled pair. See
    // FlightMotion.sharedEnter: an overlay-rendered shared element does not
    // inherit the container's scale, so the two disagree while both are running.
    val sharedEnter = FlightMotion.sharedEnter()
    val sharedExit = FlightMotion.sharedExit()

    // One layout around the whole graph, because a shared element's two halves
    // live on two destinations and both have to be inside it. It costs nothing
    // where nothing is shared: the layout only does work while a transition with
    // a matched key is running.
    SharedTransitionLayout {
        val sharedTransitionScope = this

        NavHost(
            navController = navController,
            startDestination = Destination.PlanGraph,
            modifier = modifier,
            enterTransition = { enter },
            exitTransition = { exit },
            popEnterTransition = { enter },
            popExitTransition = { exit },
        ) {

            // Settings left the navigation bar on phones, so every section's app
            // bar carries the way to it. One lambda, defined once, rather than a
            // parameter threaded through each screen's own navigation logic.
            val openSettings = { navController.navigate(Destination.Settings) { launchSingleTop = true } }

            // Plan and its detail share one graph, and therefore one `PlanViewModel`.
            // The detail screen marks a route flown by calling the list's own
            // ViewModel, which is what keeps a single undo: without the shared scope
            // it would need a second copy of the two writes and their reversal.
            navigation<Destination.PlanGraph>(startDestination = Destination.Plan) {
                composable<Destination.Plan>(
                    enterTransition = { sharedEnter },
                    exitTransition = { sharedExit },
                    popEnterTransition = { sharedEnter },
                    popExitTransition = { sharedExit },
                ) { entry ->
                    ProvideSharedRouteScopes(sharedTransitionScope, this) {
                        // `PlanRoute`, not `PlanScreen`: it is the one that decides
                        // whether this window has room for a detail pane beside the
                        // list, and only navigates when it does not.
                        PlanRoute(
                            onOpenSettings = openSettings,
                            onOpenAirports = {
                                navController.navigate(Destination.Airports) { launchSingleTop = true }
                            },
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
                            onOpenAirport = { airport -> navController.navigateToAirportDetail(airport.id) },
                            viewModel = hiltViewModel(navController.planGraphEntry(entry)),
                        )
                    }
                }

                composable<Destination.RouteDetail>(
                    enterTransition = { sharedEnter },
                    exitTransition = { sharedExit },
                    popEnterTransition = { sharedEnter },
                    popExitTransition = { sharedExit },
                ) { entry ->
                    val detail = entry.toRoute<Destination.RouteDetail>()
                    val planViewModel: PlanViewModel = hiltViewModel(navController.planGraphEntry(entry))
                    ProvideSharedRouteScopes(sharedTransitionScope, this) {
                        RouteDetailScreen(
                            route = detail,
                            onBack = { navController.popBackStack() },
                            onMarkFlown = {
                                planViewModel.markFlown(
                                    departureIcao = detail.departureIcao,
                                    destinationIcao = detail.destinationIcao,
                                    aircraftId = detail.aircraftId,
                                )
                            },
                            onOpenAirport = { airport -> navController.navigateToAirportDetail(airport.id) },
                        )
                    }
                }
            }
            composable<Destination.Fleet> { entry ->
                val planViewModel: PlanViewModel = hiltViewModel(navController.planGraphEntry(entry))
                FleetRoute(
                    onOpenSettings = openSettings,
                    onOpenAircraft = { aircraft ->
                        navController.navigate(aircraft.toFleetDetailDestination()) { launchSingleTop = true }
                    },
                    onGenerateRoutes = { aircraft -> navController.generateRoutesFor(aircraft, planViewModel) },
                )
            }
            composable<Destination.FleetDetail> { entry ->
                val planViewModel: PlanViewModel = hiltViewModel(navController.planGraphEntry(entry))
                FleetDetailScreen(
                    onBack = { navController.popBackStack() },
                    onGenerateRoutes = { aircraft -> navController.generateRoutesFor(aircraft, planViewModel) },
                )
            }
            composable<Destination.Airports> {
                AirportsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenAirport = { airport -> navController.navigateToAirportDetail(airport.id) },
                )
            }
            composable<Destination.AirportDetail> { entry ->
                val planViewModel: PlanViewModel = hiltViewModel(navController.planGraphEntry(entry))
                AirportDetailScreen(
                    onBack = { navController.popBackStack() },
                    onFlyFromHere = { airport -> navController.flyFromHere(airport, planViewModel) },
                )
            }
            composable<Destination.Logbook> {
                LogbookRoute(
                    onOpenSettings = openSettings,
                    onOpenRoute = { row ->
                        navController.navigateToDetail(
                            Destination.RouteDetail(
                                departureIcao = row.departureIcao,
                                destinationIcao = row.arrivalIcao,
                                aircraftId = row.aircraftId,
                                distanceNm = row.distanceNm ?: 0,
                                alreadyFlown = true,
                            ),
                        )
                    },
                    onOpenAirport = { airport -> navController.navigateToAirportDetail(airport.id) },
                )
            }
            composable<Destination.Stats> {
                StatsRoute(
                    onOpenSettings = openSettings,
                    onOpenRoute = { leg ->
                        navController.navigateToDetail(
                            Destination.RouteDetail(
                                departureIcao = leg.departureIcao,
                                destinationIcao = leg.arrivalIcao,
                                aircraftId = leg.aircraftId,
                                distanceNm = leg.distanceNm,
                                alreadyFlown = true,
                            ),
                        )
                    },
                    onOpenAircraft = { aircraft ->
                        navController.navigate(aircraft.toFleetDetailDestination()) { launchSingleTop = true }
                    },
                    onPlanFlight = {
                        navController.navigateToTopLevel(TopLevelDestination.PLAN)
                    },
                )
            }
            composable<Destination.Settings> {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenSelfCheck = { navController.navigate(Destination.SelfCheck) },
                    onOpenLicences = { navController.navigate(Destination.Licences) },
                )
            }

            // Kept whole rather than restyled: it is a diagnostic, and its value is
            // that it reports exactly what it reported before the UI existed.
            composable<Destination.SelfCheck> { StartupCheckScreen() }

            composable<Destination.Licences> {
                LicencesScreen(onBack = { navController.popBackStack() })
            }
        }
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
 * "Open this airport", from Airports browse and from an airport code on Route
 * detail (which the Logbook's detail pane reuses).
 */
private fun NavHostController.navigateToAirportDetail(airportId: Int) {
    navigate(Destination.AirportDetail(airportId = airportId)) { launchSingleTop = true }
}

/**
 * "Generate routes for this aircraft", from Fleet.
 *
 * Reuses Plan's own selection rather than a fresh navigation argument:
 * [PlanViewModel.setAircraft] both sets the aircraft and switches the mode to
 * [com.github.daanbouwman.flightplanner.ui.plan.PlanMode.SelectedAircraft] in
 * one call — the same thing the aircraft picker's own selection does — so
 * Plan is left exactly as if the user had picked this airframe there
 * themselves. Landing back on [Destination.Plan] rather than pushing a new
 * copy of it is what makes the bottom bar highlight Plan again for free — its
 * selection is derived from the current back stack entry, not tracked
 * separately.
 */
private fun NavHostController.generateRoutesFor(aircraft: AircraftSpec, planViewModel: PlanViewModel) {
    planViewModel.setAircraft(aircraft)
    popBackStack(Destination.Plan, inclusive = false)
}

/**
 * "Fly from here", from Airport detail.
 *
 * Mirrors [generateRoutesFor]: reuses Plan's own [PlanViewModel.setDeparture]
 * rather than a fresh navigation argument, and lands back on
 * [Destination.Plan] by popping — which [generateRoutesFor] already shows
 * works regardless of which screen pushed the destination being popped from
 * (it is called from both [Destination.Fleet] and [Destination.FleetDetail]
 * today), since [Destination.Plan] is the launch destination and stays on
 * the back stack once visited.
 */
private fun NavHostController.flyFromHere(airport: Airport, planViewModel: PlanViewModel) {
    planViewModel.setDeparture(airport)
    popBackStack(Destination.Plan, inclusive = false)
}

/**
 * The back stack entry the Plan section's ViewModel is scoped to.
 *
 * `remember`ed against the calling entry so the lookup happens once per
 * destination rather than on every recomposition — `getBackStackEntry` walks the
 * stack, and the list screen recomposes on every batch.
 */
@Composable
private fun NavHostController.planGraphEntry(entry: NavBackStackEntry): NavBackStackEntry =
    remember(entry) { getBackStackEntry(Destination.PlanGraph) }

/**
 * Whether [destination] is the section currently shown.
 *
 * It asks the hierarchy rather than the leaf so that a nested destination inside
 * a section still lights up that section's bar item.
 */
fun NavDestination?.isIn(destination: TopLevelDestination): Boolean =
    this?.hierarchy?.any { it.hasRoute(destination.route::class) } == true
