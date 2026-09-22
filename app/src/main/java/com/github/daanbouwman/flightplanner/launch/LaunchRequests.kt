package com.github.daanbouwman.flightplanner.launch

import com.github.daanbouwman.flightplanner.handoff.WatchRoute
import com.github.daanbouwman.flightplanner.navigation.Destination
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * What the NavHost sees of an arriving [LaunchRequest]: the one pending, a way
 * to mark it done, and the lookup [LaunchRequest.LastRoute] needs.
 *
 * An interface so that `FlightPlannerApp` and `FlightPlannerNavHost` can be
 * composed in a preview or a test with [NoLaunchRequests] and never know that
 * an Activity or a ViewModel exists. [LaunchViewModel] is the real one.
 */
interface LaunchRequests {

    /** The request waiting to be acted on, or null. */
    val pending: StateFlow<LaunchRequest?>

    /**
     * Marks [request] handled.
     *
     * Compare-and-set: it clears the pending slot only if [request] is still
     * what is in it, so a newer request that arrived through `onNewIntent`
     * while the older one was being acted on is not thrown away with it.
     */
    fun consume(request: LaunchRequest)

    /**
     * The route [LaunchRequest.LastRoute] should open, or null for "there is
     * none" — in which case the caller lands on Plan.
     */
    suspend fun resolveLastRoute(): Destination.RouteDetail?

    /**
     * [route] as a destination this app can navigate to, or null when its
     * airframe matches nothing in the fleet.
     *
     * Resolved here rather than in the Intent for the same reason
     * [resolveLastRoute] is: the sender cannot know the answer. A watch names
     * an airframe by display name and type code because its own ids are
     * positions in a CSV — see [LaunchRequest.OpenWatchRoute] — and only this
     * side knows what Room made of the fleet.
     */
    suspend fun resolveWatchRoute(route: WatchRoute): Destination.RouteDetail?
}

/** Nothing ever arrives. For previews, tests and any host without an Activity. */
object NoLaunchRequests : LaunchRequests {
    override val pending: StateFlow<LaunchRequest?> = MutableStateFlow(null)
    override fun consume(request: LaunchRequest) = Unit
    override suspend fun resolveLastRoute(): Destination.RouteDetail? = null
    override suspend fun resolveWatchRoute(route: WatchRoute): Destination.RouteDetail? = null
}
