package com.github.daanbouwman.flightplanner.ui.fleet

import androidx.compose.runtime.Immutable
import com.github.daanbouwman.flightplanner.model.AircraftSpec

/** Which pool of the fleet the list is drawn from. */
enum class FleetMode { All, Flown, NotFlown }

/** One category's worth of airframes, sorted within the group by manufacturer then variant. */
@Immutable
data class FleetGroup(val category: String, val aircraft: List<AircraftSpec>)

/** Where the fleet read has got to. */
enum class FleetStatus { Loading, Ready }

/** Everything the Fleet screen renders. */
@Immutable
data class FleetUiState(
    val groups: List<FleetGroup> = emptyList(),
    val mode: FleetMode = FleetMode.All,
    /** The whole fleet's size, independent of [mode] — what a filter is a filter *of*. */
    val totalCount: Int = 0,
    val flownCount: Int = 0,
    val notFlownCount: Int = 0,
    /**
     * Every distinct [AircraftSpec.category] in the fleet, sorted — independent
     * of [mode], so switching to "Flown" does not narrow what the add-aircraft
     * form can offer. Backs the category picker in [AddAircraftSheet].
     */
    val categories: List<String> = emptyList(),
    val status: FleetStatus = FleetStatus.Loading,
) {
    /** A finished read that found no aircraft at all — the fleet itself is empty. */
    val isEmpty: Boolean
        get() = status == FleetStatus.Ready && totalCount == 0

    /**
     * A finished read of a non-empty fleet where [mode] excluded everything —
     * distinct from [isEmpty]: "Flown" with nothing flown yet is a filter with
     * no matches, not a fleet with no aircraft.
     */
    val isNoMatch: Boolean
        get() = status == FleetStatus.Ready && totalCount > 0 && groups.isEmpty()
}
