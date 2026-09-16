package com.github.daanbouwman.flightplanner.widget

import com.github.daanbouwman.flightplanner.core.database.repository.FleetRepository
import com.github.daanbouwman.flightplanner.di.DefaultDispatcher
import com.github.daanbouwman.flightplanner.index.AirportIndexProvider
import com.github.daanbouwman.flightplanner.model.AircraftSpec
import com.github.daanbouwman.flightplanner.navigation.Destination
import com.github.daanbouwman.flightplanner.routing.AirportIndex
import com.github.daanbouwman.flightplanner.routing.GeoArc
import com.github.daanbouwman.flightplanner.routing.GreatCircle
import com.github.daanbouwman.flightplanner.routing.RouteArc
import com.github.daanbouwman.flightplanner.routing.RouteGenerator
import com.github.daanbouwman.flightplanner.routing.dailyChallenge
import com.github.daanbouwman.flightplanner.settings.UnitSystem
import com.github.daanbouwman.flightplanner.ui.asFigure
import com.github.daanbouwman.flightplanner.ui.distanceUnitSuffix
import com.github.daanbouwman.flightplanner.ui.nmToDisplayDistance
import com.github.daanbouwman.flightplanner.ui.runCatchingCancellable
import kotlinx.coroutines.CoroutineDispatcher
import java.time.LocalDate
import javax.inject.Inject

/** What the widget has to show. */
sealed interface ChallengeState {

    /**
     * Today's route, already worded for the widget.
     *
     * Codes only, no airport names: the route card the app draws omits them
     * too, and reading names would mean the airport database, which on a
     * phone that has never opened the app is a 30 MB asset copy that must not
     * run inside a widget update. The figures are formatted here, in the unit
     * the user chose, because Glance has no composition locals of ours to
     * read a unit from.
     */
    data class Ready(
        /** The day this challenge belongs to — printed on the card. */
        val date: LocalDate,
        val departureIcao: String,
        val destinationIcao: String,
        val aircraftName: String,
        val distanceText: String,
        val eteText: String,
        /** The great circle between the ends, at the route card's sampling, for the map. */
        val arc: GeoArc,
        /** Where a tap goes. */
        val route: Destination.RouteDetail,
    ) : ChallengeState

    /** No airframes, even after seeding — nothing to fly a challenge in. */
    data object FleetEmpty : ChallengeState

    /** The index or the fleet could not be read. Tapping opens the app to sort it out. */
    data object Unavailable : ChallengeState
}

/**
 * Computes [ChallengeState] for a day.
 *
 * Every read is guarded. `provideGlance` runs in a receiver, and an exception
 * there leaves Glance's error layout on the home screen until the next
 * update — so nothing here may throw, and the honest answer to a failed read
 * is [ChallengeState.Unavailable], which at least still opens the app.
 *
 * The primary constructor takes its three reads as functions and is
 * `internal`, so `DailyChallengeSourceTest` composes it against an in-memory
 * index and a list; the `@Inject` secondary maps the graph's types onto them,
 * the same shape `StartupCheckViewModel` and `LaunchViewModel` have.
 */
class DailyChallengeSource internal constructor(
    private val index: suspend () -> AirportIndex,
    private val fleet: suspend () -> List<AircraftSpec>,
    private val seedFleet: suspend () -> Unit,
    private val dispatcher: CoroutineDispatcher,
) {

    @Inject
    constructor(
        indexProvider: AirportIndexProvider,
        fleetRepository: FleetRepository,
        @DefaultDispatcher dispatcher: CoroutineDispatcher,
    ) : this(
        index = indexProvider::get,
        fleet = fleetRepository::fleet,
        seedFleet = { fleetRepository.seedIfEmpty() },
        dispatcher = dispatcher,
    )

    suspend fun load(date: LocalDate, unit: UnitSystem, icaoOnly: Boolean): ChallengeState {
        val airports = runCatchingCancellable { index() }.getOrElse { return ChallengeState.Unavailable }
        val airframes = runCatchingCancellable { fleetOrSeeded() }.getOrElse { return ChallengeState.Unavailable }
        if (airframes.isEmpty()) return ChallengeState.FleetEmpty

        val route = RouteGenerator(airports, dispatcher = dispatcher)
            .dailyChallenge(airframes, date, icaoOnly)
            ?: return ChallengeState.Unavailable

        val departure = airports.icaoOf(route.departureSlot)
        val destination = airports.icaoOf(route.destinationSlot)
        return ChallengeState.Ready(
            date = date,
            departureIcao = departure,
            destinationIcao = destination,
            aircraftName = route.aircraft.displayName,
            distanceText = "${nmToDisplayDistance(route.distanceNm, unit).asFigure()} ${distanceUnitSuffix(unit)}",
            eteText = GreatCircle.flightTime(route.distanceNm.toDouble(), route.aircraft.cruiseSpeedKt).format(),
            arc = RouteArc.sampleGeographic(
                airports.latDegOf(route.departureSlot),
                airports.lonDegOf(route.departureSlot),
                airports.latDegOf(route.destinationSlot),
                airports.lonDegOf(route.destinationSlot),
                samples = RouteArc.CARD_SAMPLES,
            ),
            route = Destination.RouteDetail(
                departureIcao = departure,
                destinationIcao = destination,
                aircraftId = route.aircraft.id,
                distanceNm = route.distanceNm,
            ),
        )
    }

    /**
     * The fleet, seeded first if it is empty — as `PlanViewModel.prepare` does
     * — so a widget placed before the app was ever opened is not dead until
     * it is.
     */
    private suspend fun fleetOrSeeded(): List<AircraftSpec> {
        val current = fleet()
        if (current.isNotEmpty()) return current
        seedFleet()
        return fleet()
    }
}
