package com.github.daanbouwman.flightplanner.launch

import com.github.daanbouwman.flightplanner.handoff.WatchRoute
import com.github.daanbouwman.flightplanner.navigation.Destination

/**
 * Something the outside world asked the app to do on arrival.
 *
 * The app has four front doors besides the launcher icon: the "Today's
 * challenge" widget, the "Aircraft of the day" widget, the three static
 * shortcuts, and the watch. Each of them starts `MainActivity` with an Intent,
 * and this is
 * that Intent's meaning once [LaunchIntents.parse] has read it. It is a value
 * rather than a navigation call because the thing that receives the Intent (the
 * Activity) and the thing that can act on it (the NavHost, once it exists) are
 * composed at different times — see `LaunchViewModel`, which holds the request
 * between the two.
 */
sealed interface LaunchRequest {

    /** Open one route's detail — the challenge widget's tap. */
    data class OpenRoute(val route: Destination.RouteDetail) : LaunchRequest

    /**
     * Open a route the watch sent — a tap on the Wear app's route face.
     *
     * Carries a [WatchRoute] rather than a [Destination.RouteDetail] because
     * the watch cannot name an airframe the way the rest of the app does. A
     * `RouteDetail` identifies one by Room row id, and the watch has no Room:
     * its fleet is the bundled seed CSV, parsed in its own process, where the
     * ids are positions in a file and mean nothing here. So the link names the
     * airframe by what it *is* — display name, then type code — and the id is
     * looked up on arrival against whatever fleet this phone is carrying, the
     * same way [LastRoute] resolves a route the Intent could not carry.
     *
     * See [LaunchRequests.resolveWatchRoute], and `WatchRouteLink` in
     * `:core:handoff` for the wire format.
     */
    data class OpenWatchRoute(val route: WatchRoute) : LaunchRequest

    /**
     * Land on Fleet — the aircraft widget's tap — at [airframeId]'s detail.
     *
     * **Null means the fleet list itself**, which is what the widget sends when
     * it has no airframe to name: an empty fleet, or one it could not read.
     * Both are fixed on the Fleet screen, and landing there beats landing on a
     * detail for an id that may not exist. [OpenRoute] treats a missing id as
     * malformed instead, because a route without an airframe is not a route;
     * a fleet without one is simply the fleet.
     */
    data class OpenAircraft(val airframeId: Int?) : LaunchRequest

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
