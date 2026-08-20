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

    /**
     * The Plan section: the list, and the detail of a route in it.
     *
     * A graph rather than two sibling destinations because they share state that
     * has to outlive the navigation between them. `PlanViewModel` is scoped to
     * this entry, so the detail screen can mark a route flown through the same
     * instance that owns the list and its undo — one write path instead of two
     * that must be kept in step. It is not itself a [TopLevel]: the bar selects
     * [Plan], and the graph is only where the two screens' shared lifetime lives.
     */
    @Serializable
    data object PlanGraph : Destination

    @Serializable
    data object Plan : TopLevel

    @Serializable
    data object Fleet : TopLevel

    /**
     * Your flying history and records.
     */
    @Serializable
    data object Logbook : TopLevel

    /**
     * Summaries and analytics drawn from your logbook.
     */
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
        val alreadyFlown: Boolean = false,
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
