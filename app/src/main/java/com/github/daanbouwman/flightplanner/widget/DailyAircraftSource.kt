package com.github.daanbouwman.flightplanner.widget

import com.github.daanbouwman.flightplanner.core.database.repository.FleetRepository
import com.github.daanbouwman.flightplanner.model.AircraftSpec
import com.github.daanbouwman.flightplanner.routing.dailyAircraft
import com.github.daanbouwman.flightplanner.settings.UnitSystem
import com.github.daanbouwman.flightplanner.ui.asFigure
import com.github.daanbouwman.flightplanner.ui.distanceUnitSuffix
import com.github.daanbouwman.flightplanner.ui.ftToDisplayLength
import com.github.daanbouwman.flightplanner.ui.lengthUnitSuffix
import com.github.daanbouwman.flightplanner.ui.nmToDisplayDistance
import com.github.daanbouwman.flightplanner.ui.runCatchingCancellable
import java.time.LocalDate
import javax.inject.Inject

/** What the "Aircraft of the day" widget has to show. */
sealed interface AircraftState {

    /**
     * Today's airframe, already worded for the widget.
     *
     * The figures are formatted here, in the unit the user chose, for the same
     * reason [ChallengeState.Ready]'s are: Glance has no composition locals of
     * ours to read a unit from.
     */
    data class Ready(
        /** The day this airframe belongs to — printed on the card. */
        val date: LocalDate,
        /** `"Boeing 787-9"` — [AircraftSpec.displayName]. */
        val name: String,
        /** The aircraft type code, `"B789"`. The card's anchor line. */
        val typeCode: String,
        /** Reported, never used to pick — see [dailyAircraft]. */
        val flown: Boolean,
        val rangeText: String,
        /**
         * The runway this airframe needs, or **null when it names no takeoff
         * distance at all** — [AircraftSpec.requiredRunwayFt] answers 0 there,
         * and a card reading `RWY 0 ft` would state a requirement the airframe
         * does not have. Gliders in the seed fleet are exactly this case.
         */
        val runwayText: String?,
        /** Where a tap goes. */
        val airframeId: Int,
    ) : AircraftState

    /** No airframes, even after seeding — nothing to show. */
    data object FleetEmpty : AircraftState

    /** The fleet could not be read. Tapping opens the app to sort it out. */
    data object Unavailable : AircraftState
}

/**
 * Computes [AircraftState] for a day.
 *
 * Every read is guarded, for the reason [DailyChallengeSource] explains: an
 * exception inside `provideGlance` leaves Glance's error layout on the home
 * screen until the next update, so the honest answer to a failed read is
 * [AircraftState.Unavailable].
 *
 * **It reads the fleet and nothing else.** No airport index, no world outline,
 * no route generation and so no dispatcher to run one on — the day's airframe
 * is a pick from a list of ~116 rows. That is the whole reason this widget can
 * exist beside the challenge one without doubling what a home screen costs at
 * midnight.
 *
 * The primary constructor takes its two reads as functions and is `internal`,
 * so `DailyAircraftSourceTest` composes it against a plain list; the `@Inject`
 * secondary maps the graph's types onto them, as [DailyChallengeSource] does.
 */
class DailyAircraftSource internal constructor(
    private val fleet: suspend () -> List<AircraftSpec>,
    private val seedFleet: suspend () -> Unit,
) {

    @Inject
    constructor(fleetRepository: FleetRepository) : this(
        fleet = fleetRepository::fleet,
        seedFleet = { fleetRepository.seedIfEmpty() },
    )

    suspend fun load(date: LocalDate, unit: UnitSystem): AircraftState {
        val airframes = runCatchingCancellable { fleetOrSeeded() }.getOrElse { return AircraftState.Unavailable }
        // `dailyAircraft` is null for an empty fleet and for nothing else, so
        // this one branch is the whole of the empty case — there is no separate
        // `isEmpty` check above it to fall out of step with.
        val airframe = dailyAircraft(airframes, date) ?: return AircraftState.FleetEmpty

        return AircraftState.Ready(
            date = date,
            name = airframe.displayName,
            typeCode = airframe.icaoCode,
            flown = airframe.flown,
            rangeText = "${nmToDisplayDistance(airframe.rangeNm, unit).asFigure()} ${distanceUnitSuffix(unit)}",
            runwayText = airframe.requiredRunwayFt
                .takeIf { it > 0 }
                ?.let { "${ftToDisplayLength(it, unit).asFigure()} ${lengthUnitSuffix(unit)}" },
            airframeId = airframe.id,
        )
    }

    /**
     * The fleet, seeded first if it is empty — as [DailyChallengeSource] does —
     * so a widget placed before the app was ever opened is not dead until it is.
     */
    private suspend fun fleetOrSeeded(): List<AircraftSpec> {
        val current = fleet()
        if (current.isNotEmpty()) return current
        seedFleet()
        return fleet()
    }
}
