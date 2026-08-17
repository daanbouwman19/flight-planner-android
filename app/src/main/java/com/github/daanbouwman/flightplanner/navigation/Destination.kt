package com.github.daanbouwman.flightplanner.navigation

import kotlinx.serialization.Serializable

/**
 * Every place the app can be, as data rather than as a string.
 *
 * Type-safe navigation means a destination is a value: `navigate(Plan)` instead
 * of `navigate("plan")`, and arguments arrive as a typed object instead of a
 * bundle read key by key. The compiler then catches a renamed argument, which a
 * route string never can.
 *
 * The hierarchy is sealed for two reasons. It makes [TopLevel] — the set that
 * appears in the navigation bar — a type rather than a convention, so a
 * destination cannot accidentally be treated as top level. And it makes the
 * eventual move to Navigation 3 mechanical: that library's back stack is a list
 * of exactly this kind of sealed key, so the routes survive the migration and
 * only the host changes.
 */
@Serializable
sealed interface Destination {

    /** The destinations reachable from the navigation bar, rail or drawer. */
    @Serializable
    sealed interface TopLevel : Destination

    @Serializable
    data object Plan : TopLevel

    @Serializable
    data object Logbook : TopLevel

    @Serializable
    data object Fleet : TopLevel

    @Serializable
    data object Airports : TopLevel

    @Serializable
    data object Stats : TopLevel

    @Serializable
    data object Settings : TopLevel

    /**
     * A single generated route.
     *
     * It carries the route rather than an id because a generated route has no
     * identity to look up — it exists only in the batch that produced it — so the
     * arguments have to be enough to redraw the screen after process death.
     */
    @Serializable
    data class RouteDetail(
        val departureIcao: String,
        val destinationIcao: String,
        val aircraftId: Int,
        val distanceNm: Int,
    ) : Destination

    /**
     * The on-device self-check, kept from the pre-UI milestone.
     *
     * It is the only thing that proves the prepackaged database, the native
     * libraries and the route generator all work on a real device, so it stays —
     * behind Settings rather than as the launch screen.
     */
    @Serializable
    data object SelfCheck : Destination
}
