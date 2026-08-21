package com.github.daanbouwman.flightplanner.ui.fleet

import com.github.daanbouwman.flightplanner.model.AircraftSpec

/** Narrows the fleet to the pool [mode] names. */
fun List<AircraftSpec>.filterByMode(mode: FleetMode): List<AircraftSpec> = when (mode) {
    FleetMode.All -> this
    FleetMode.Flown -> filter { it.flown }
    FleetMode.NotFlown -> filterNot { it.flown }
}

/**
 * Groups airframes by [AircraftSpec.category], each group sorted by manufacturer
 * then variant, groups themselves in category order.
 *
 * There is no fixed category enum — it is free text seeded from the bundled CSV,
 * same as the desktop app's own table — so grouping is a runtime property of the
 * data rather than a switch over a closed set. Sorting once before `groupBy`
 * (backed by a `LinkedHashMap`, so it preserves the order keys are first seen in
 * — the same trick [com.github.daanbouwman.flightplanner.ui.logbook.groupByMonth]
 * uses) makes one pass enough for both the group order and the row order within
 * each group.
 */
fun List<AircraftSpec>.groupByCategory(): List<FleetGroup> =
    sortedWith(compareBy({ it.category }, { it.manufacturer }, { it.variant }))
        .groupBy { it.category }
        .map { (category, aircraft) -> FleetGroup(category = category, aircraft = aircraft) }
