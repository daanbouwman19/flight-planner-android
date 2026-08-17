package com.github.daanbouwman.flightplanner.ui.plan

import androidx.compose.runtime.Immutable
import com.github.daanbouwman.flightplanner.model.AircraftSpec
import com.github.daanbouwman.flightplanner.model.Airport
import com.github.daanbouwman.flightplanner.routing.FlightTime

/**
 * Which airframes a batch may draw from, as the *screen* understands it.
 *
 * Deliberately not `routing.RouteMode`, even though the three cases line up.
 * `RouteMode.Specific` carries an aircraft id because the generator cannot run
 * without one; this enum does not, because "the user has tapped *This aircraft*
 * but has not picked one yet" is a real, reachable UI state that the generator
 * has no representation for and must never be handed. Keeping them separate is
 * what makes that gap somewhere the screen can put a prompt instead of somewhere
 * an empty list comes from.
 */
enum class PlanMode { Any, NotFlown, SelectedAircraft }

/** Why a batch could not be produced at all. Rendered by the screen, so no prose here. */
enum class PlanFailure {
    /** The airport index failed to load; nothing in the app can generate. */
    IndexUnavailable,

    /** The fleet is empty, so there is no airframe to plan for. */
    FleetEmpty,

    Unknown,
}

/** Where the current batch has got to. */
sealed interface PlanStatus {

    /** Nothing has been generated yet. The screen invites the user to start. */
    data object Idle : PlanStatus

    /** The first batch of a new selection is in flight; no rows are shown. */
    data object Generating : PlanStatus

    /** Rows are on screen and more are being appended beneath them. */
    data object Appending : PlanStatus

    data object Ready : PlanStatus

    data class Failed(val reason: PlanFailure) : PlanStatus
}

/**
 * One generated route, with everything the card draws already resolved.
 *
 * A plain class with identity-based equality rather than a `data class`. Two
 * reasons, both about the list:
 *
 * The [arc] is a `FloatArray`, and array equality is referential, so a generated
 * `equals` would silently be identity-based for that field anyway — better to
 * say so than to leave a reader believing the comparison is structural.
 *
 * More importantly, [id] is already a perfect identity: it is minted once per
 * generated route and never reused, and two rows are the same row exactly when
 * their ids match. Comparing eight fields to answer a question one field answers
 * is work Compose does on every recomposition of a fifty-row list.
 */
@Immutable
class RouteRow(
    val id: Long,
    val aircraft: AircraftSpec,
    val departure: Airport,
    val destination: Airport,
    val distanceNm: Int,
    val flightTime: FlightTime,
    val departureRunwayFt: Int,
    val destinationRunwayFt: Int,
    /**
     * The great circle, projected and normalised, ready for
     * `RouteSparkline`. Computed once here rather than per frame in the
     * composable — see that component for why.
     */
    val arc: FloatArray,
) {
    /**
     * True when the airframe needs more runway than the departure offers.
     *
     * Only reachable with a locked departure: the generator honours a locked
     * departure even where the aircraft cannot really use it, so that the user
     * sees the conflict rather than an unexplained empty list. Reported per end
     * rather than for the route as a whole, because "one of these two airports
     * is too short" is not actionable — which one is.
     */
    val departureRunwayTooShort: Boolean
        get() = aircraft.requiredRunwayFt > 0 && departureRunwayFt < aircraft.requiredRunwayFt

    /** As [departureRunwayTooShort], for the destination. */
    val destinationRunwayTooShort: Boolean
        get() = aircraft.requiredRunwayFt > 0 && destinationRunwayFt < aircraft.requiredRunwayFt

    override fun equals(other: Any?): Boolean = other is RouteRow && other.id == id

    override fun hashCode(): Int = id.hashCode()
}

/** Everything the Plan screen renders. */
@Immutable
data class PlanUiState(
    val mode: PlanMode = PlanMode.Any,
    val lockedDeparture: Airport? = null,
    val selectedAircraft: AircraftSpec? = null,
    val notFlownCount: Int = 0,
    val routes: List<RouteRow> = emptyList(),
    val status: PlanStatus = PlanStatus.Idle,
) {
    /**
     * The user chose "this aircraft" without choosing one. Generation is held
     * back until they do, and the screen says so — the alternative is a batch
     * that silently comes back empty.
     */
    val awaitingAircraftChoice: Boolean
        get() = mode == PlanMode.SelectedAircraft && selectedAircraft == null

    /**
     * A finished batch that produced nothing. Distinct from [PlanStatus.Idle]:
     * the app did the work and the filters excluded everything, which is a
     * different message and a different next step.
     */
    val isEmptyResult: Boolean
        get() = status is PlanStatus.Ready && routes.isEmpty()
}

/** One-shot things the screen reacts to but does not store. */
sealed interface PlanEvent {

    /** A route was logged. Carries what the undo action needs to describe itself. */
    data class FlightLogged(val departureIcao: String, val destinationIcao: String) : PlanEvent
}
