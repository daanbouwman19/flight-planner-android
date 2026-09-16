package com.github.daanbouwman.flightplanner.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import com.github.daanbouwman.flightplanner.launch.LaunchRequest
import com.github.daanbouwman.flightplanner.launch.LaunchRequests
import com.github.daanbouwman.flightplanner.launch.NoLaunchRequests
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
import com.github.daanbouwman.flightplanner.feature.globe.ui.rememberGlobeAvailable
import com.github.daanbouwman.flightplanner.ui.RouteDetailScreen
import com.github.daanbouwman.flightplanner.ui.detail.ImmersiveGlobeScreen
import com.github.daanbouwman.flightplanner.ui.airport.AirportDetailScreen
import com.github.daanbouwman.flightplanner.ui.airports.AirportsScreen
import com.github.daanbouwman.flightplanner.ui.fleet.FleetDetailScreen
import com.github.daanbouwman.flightplanner.ui.fleet.FleetRoute
import com.github.daanbouwman.flightplanner.ui.fleet.toFleetDetailDestination
import com.github.daanbouwman.flightplanner.model.AircraftSpec
import com.github.daanbouwman.flightplanner.model.Airport
import com.github.daanbouwman.flightplanner.ui.profile.ProfileScreen
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
    launchRequests: LaunchRequests = NoLaunchRequests,
) {
    val launchRequest by launchRequests.pending.collectAsStateWithLifecycle()

    // Resolved once, outside the transition lambdas: those lambdas are not
    // composable, so a motion token — which is — has to be read here and closed
    // over. This is also the only place :app decides how a screen change looks,
    // and it decides by naming a token rather than by writing a spec.
    val enter = FlightMotion.navEnter()
    val exit = FlightMotion.navExit()

    // Settings is opened and left as a unit from a section's app bar, so it slides
    // in from the trailing edge and — the part the design asks for — slides back
    // out across it. Only the Plan→Settings and Settings→Plan slots get this; the
    // Settings→Licences pair stays on the fade, which is the direction the slide
    // would point the wrong way.
    val lateralEnter = FlightMotion.lateralEnter()
    val lateralExit = FlightMotion.lateralExit()

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
                    // **Gated on the other side of the navigation, not applied
                    // to all four unconditionally.** Plan shares an element
                    // with exactly one destination — the route card growing
                    // into RouteDetail's face — and `sharedExit`'s fast spring
                    // was tuned entirely in terms of that element. Applied to
                    // every navigation away from Plan it also retuned Settings,
                    // Fleet, Airports, Logbook and Stats, none of which share
                    // anything with it. See [sharesRouteFace].
                    enterTransition = { if (initialState.sharesRouteFace()) sharedEnter else enter },
                    exitTransition = { if (targetState.sharesRouteFace()) sharedExit else exit },
                    popEnterTransition = { if (initialState.sharesRouteFace()) sharedEnter else enter },
                    popExitTransition = { if (targetState.sharesRouteFace()) sharedExit else exit },
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
                    // 3B: the control is absent rather than disabled on a device
                    // with no renderer, because a control that opens nothing is
                    // worse than no control. Nothing else on the screen says so.
                    val globeAvailable = rememberGlobeAvailable()
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
                            onOpenImmersiveGlobe = if (globeAvailable) {
                                { navController.navigateToImmersiveGlobe(detail) }
                            } else {
                                null
                            },
                        )
                    }
                }

                composable<Destination.ImmersiveGlobe>(
                    // The chrome fades while the box grows, which is the pairing
                    // the route card’s own entrance uses. The sphere itself does
                    // not travel: a SurfaceView cannot be a shared element, so
                    // the camera simply keeps its state across the change and the
                    // globe holds still while the frame around it changes size.
                    enterTransition = { sharedEnter },
                    exitTransition = { sharedExit },
                    popEnterTransition = { sharedEnter },
                    popExitTransition = { sharedExit },
                ) { entry ->
                    val immersive = entry.toRoute<Destination.ImmersiveGlobe>()
                    val detailEntry = navController.routeDetailEntry(entry)
                    val detailRoute = Destination.RouteDetail(
                        departureIcao = immersive.departureIcao,
                        destinationIcao = immersive.destinationIcao,
                        aircraftId = immersive.aircraftId,
                        distanceNm = immersive.distanceNm,
                    )
                    // **The detail screen's own ViewModel, not a second one.**
                    // Resolved against this entry it would be a fresh instance,
                    // and its `init` re-runs the whole load — including the METAR
                    // fetch, which is a network request against a quota'd key, per
                    // fullscreen toggle, for an arc the screen behind it already
                    // has. Sharing the instance also means the camera and the
                    // figures cannot disagree across the transition.
                    if (detailEntry != null) {
                        ImmersiveGlobeScreen(
                            route = detailRoute,
                            onCollapse = { navController.popBackStack() },
                            viewModel = hiltViewModel(detailEntry),
                        )
                    } else {
                        // Only reachable if this destination is ever entered
                        // without the detail below it — a deep link, today.
                        ImmersiveGlobeScreen(
                            route = detailRoute,
                            onCollapse = { navController.popBackStack() },
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
                    // The "Log a flight" shortcut. The request stays pending
                    // until the screen has actually opened the sheet, which is
                    // the one-shot boundary a nav argument could not give it —
                    // an argument is restored with the back stack and would
                    // re-open the sheet after every rotation.
                    openAddFlight = launchRequest is LaunchRequest.LogFlight,
                    onAddFlightOpened = { launchRequests.consume(LaunchRequest.LogFlight) },
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
            composable<Destination.Settings>(
                enterTransition = { lateralEnter },
                popExitTransition = { lateralExit },
            ) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenSelfCheck = { navController.navigate(Destination.SelfCheck) },
                    onOpenLicences = { navController.navigate(Destination.Licences) },
                )
            }

            // Kept whole rather than restyled: it is a diagnostic, and its value is
            // that it reports exactly what it reported before the UI existed.
            composable<Destination.SelfCheck> {
                StartupCheckScreen(onBack = { navController.popBackStack() })
            }

            composable<Destination.Licences> {
                LicencesScreen(onBack = { navController.popBackStack() })
            }
        }

        // After `NavHost`, so the graph is set by the time this first composes.
        LaunchRequestConsumer(navController, launchRequests, launchRequest)
    }
}

/**
 * Acts on an arriving [LaunchRequest] — a widget tap or a shortcut — once the
 * graph can be navigated.
 *
 * The PlanGraph entry is the readiness signal. It is the root start graph, and
 * [navigateToTopLevel] only ever pops *to* the start destination, so once the
 * host has a current entry at all the PlanGraph entry is on the stack for the
 * rest of the host's life; until then [planViewModel] is null and the effect
 * simply waits for it. The ViewModel is resolved in composition, not inside the
 * effect, because `hiltViewModel` is a composable — the same way every
 * `composable<…>` above reaches it.
 *
 * [LaunchRequest.LogFlight] is deliberately not consumed here: it is consumed by
 * `LogbookScreen` when the sheet opens, see `composable<Destination.Logbook>`.
 */
@Composable
private fun LaunchRequestConsumer(
    navController: NavHostController,
    launchRequests: LaunchRequests,
    request: LaunchRequest?,
) {
    val current by navController.currentBackStackEntryAsState()
    // Found once and cached, not re-keyed on `current`: per this function's own
    // KDoc, the PlanGraph entry never leaves the stack once found, so re-walking
    // it on every navigation elsewhere in the app — Fleet, Airports, Stats,
    // Settings, all of which change `current` — would repeat the lookup and
    // re-resolve `hiltViewModel` forever for an answer that cannot change.
    var planEntry by remember { mutableStateOf<NavBackStackEntry?>(null) }
    if (planEntry == null && current != null) {
        planEntry = runCatching { navController.getBackStackEntry(Destination.PlanGraph) }.getOrNull()
    }
    val planViewModel: PlanViewModel? = planEntry?.let { hiltViewModel(it) }

    LaunchedEffect(request, planViewModel) {
        if (request == null || planViewModel == null) return@LaunchedEffect
        when (request) {
            is LaunchRequest.OpenRoute -> {
                // Over Plan, not on top of wherever the user was: back from a
                // widget's route then lands on the place that generates more,
                // and the bar's selection follows the stack for free. Nothing
                // shares an element with it on a cold start, so RouteDetail's
                // shared transitions degrade to the plain fade.
                navController.navigateToTopLevel(TopLevelDestination.PLAN)
                navController.navigateToDetail(request.route)
                launchRequests.consume(request)
            }

            LaunchRequest.GenerateRoutes -> {
                navController.navigateToTopLevel(TopLevelDestination.PLAN)
                // On a cold start `PlanViewModel.init` has already begun a
                // batch; bumping the generation cancels it under `collectLatest`
                // for a sub-millisecond cost, which is cheaper than knowing.
                planViewModel.generate()
                launchRequests.consume(request)
            }

            LaunchRequest.LastRoute -> {
                val route = launchRequests.resolveLastRoute()
                navController.navigateToTopLevel(TopLevelDestination.PLAN)
                if (route != null) navController.navigateToDetail(route)
                launchRequests.consume(request)
            }

            LaunchRequest.LogFlight -> navController.navigateToTopLevel(TopLevelDestination.LOGBOOK)
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

/** "Show me this on the globe", from the route detail’s app bar. */
private fun NavHostController.navigateToImmersiveGlobe(detail: Destination.RouteDetail) {
    navigate(
        Destination.ImmersiveGlobe(
            departureIcao = detail.departureIcao,
            destinationIcao = detail.destinationIcao,
            aircraftId = detail.aircraftId,
            distanceNm = detail.distanceNm,
        ),
    ) { launchSingleTop = true }
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
 * The route detail entry under the immersive globe, if there is one.
 *
 * The same trick as [planGraphEntry], scoped one level tighter: it is how the
 * full-screen globe borrows the detail screen's ViewModel instead of building a
 * second one that repeats its queries. Nullable rather than assumed, because the
 * destination could one day be entered from a deep link with nothing beneath it,
 * and `getBackStackEntry` throws rather than returning null in that case.
 */
@Composable
private fun NavHostController.routeDetailEntry(entry: NavBackStackEntry): NavBackStackEntry? =
    remember(entry) {
        runCatching { getBackStackEntry<Destination.RouteDetail>() }.getOrNull()
    }

/**
 * Whether [destination] is the section currently shown.
 *
 * It asks the hierarchy rather than the leaf so that a nested destination inside
 * a section still lights up that section's bar item.
 */
fun NavDestination?.isIn(destination: TopLevelDestination): Boolean =
    this?.hierarchy?.any { it.hasRoute(destination.route::class) } == true

/**
 * Whether this back-stack entry is [Destination.RouteDetail] — the only
 * destination [Destination.Plan] shares an element with.
 *
 * Read from either side of a Plan transition: `targetState.sharesRouteFace()`
 * decides Plan's own exit, `initialState.sharesRouteFace()` its own enter. See
 * the KDoc on `composable<Destination.Plan>`'s transitions above.
 */
internal fun NavBackStackEntry.sharesRouteFace(): Boolean =
    destination.hasRoute(Destination.RouteDetail::class)
