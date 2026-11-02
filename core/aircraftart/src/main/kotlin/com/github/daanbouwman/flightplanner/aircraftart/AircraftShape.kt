package com.github.daanbouwman.flightplanner.aircraftart

/**
 * One aircraft's outline seen from directly above, nose up.
 *
 * [pathData] is SVG path syntax — the subset `M`, `m`, `l` and `z`, integer
 * operands — in **centimetres of the real aircraft**, with the origin at the
 * top-left of the outline's bounding box. So x runs `0..spanCm` from the left
 * wing tip as seen from above and y runs `0..lengthCm` from the nose, and an
 * outline drawn at one scale for every aircraft is drawn to true relative size
 * without being told anything else. Rings are filled even-odd: an outline is
 * clockwise and a hole anticlockwise, but the fill does not depend on it.
 *
 * Both Android's `PathParser` and the browser read this string as it is.
 */
class AircraftShape(
    /** Stable identifier, e.g. `a320_cfm_sl`. */
    val id: String,
    /** "Airbus A320 CFM, sharklets". */
    val label: String,
    val spanCm: Int,
    val lengthCm: Int,
    val provenance: Provenance,
    val pathData: String,
) {
    val spanM: Double get() = spanCm / 100.0
    val lengthM: Double get() = lengthCm / 100.0

    /** Width over height of the bounding box: above 1 for nearly everything with wings. */
    val aspectRatio: Float get() = spanCm.toFloat() / lengthCm

    enum class Provenance {
        /** Traced from the flight simulator's own exterior model of this aircraft. */
        Traced,

        /** Traced from a sibling's model and altered as the manufacturer altered it — the 737-600 from the -700. */
        Derived,

        /** Drawn from published dimensions where no model could be read. */
        Drawn,

        /** A generic outline standing in for an aircraft with none of its own. */
        Fallback,
    }

    override fun toString() = "AircraftShape($id)"
}
