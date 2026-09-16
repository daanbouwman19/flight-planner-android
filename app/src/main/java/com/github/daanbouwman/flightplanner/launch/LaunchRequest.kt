package com.github.daanbouwman.flightplanner.launch

import com.github.daanbouwman.flightplanner.navigation.Destination

/**
 * Something the outside world asked the app to do on arrival.
 *
 * The app has two front doors besides the launcher icon: the "Today's
 * challenge" widget and the three static shortcuts. Each of them starts
 * `MainActivity` with an Intent, and this is that Intent's meaning once
 * [LaunchIntents.parse] has read it. It is a value rather than a navigation
 * call because the thing that receives the Intent (the Activity) and the thing
 * that can act on it (the NavHost, once it exists) are composed at different
 * times — see `LaunchViewModel`, which holds the request between the two.
 */
sealed interface LaunchRequest {

    /** Open one route's detail — the widget's tap. */
    data class OpenRoute(val route: Destination.RouteDetail) : LaunchRequest

    /** Land on Plan and generate a fresh batch. */
    data object GenerateRoutes : LaunchRequest

    /** Land on Logbook with the add-flight sheet open. */
    data object LogFlight : LaunchRequest

    /**
     * Re-open the route the user last looked at.
     *
     * Resolved at arrival rather than in the Intent, because a static shortcut
     * is fixed XML and cannot know which route that is. See
     * [LaunchRequests.resolveLastRoute].
     */
    data object LastRoute : LaunchRequest
}
