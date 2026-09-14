package com.github.daanbouwman.flightplanner.routing

/**
 * Longitude conventions for a *set* of places, where [RouteArc] settles them for
 * one leg.
 *
 * A route is unwrapped by walking it: each sample is made continuous with the
 * one before, so a Pacific crossing runs past 180° instead of jumping to −180°.
 * A network has no walk — it is a bag of airports — and fitting a frame to
 * their raw longitudes did to Fiji and Samoa exactly what the walk exists to
 * prevent: two fields 800 NM apart at +177° and −172° framed a window 349° wide
 * with the whole planet between them, and the leg joining them left one edge
 * of the card as a diagonal and re-entered at the other.
 *
 * ### The seam goes where the airports are not
 *
 * Every set of longitudes divides the circle into gaps, and the widest gap is
 * the one that can hold the seam. Sorting the longitudes, finding that gap and
 * shifting everything west of it up by a turn puts the set on the shortest arc
 * that contains all of it — 10° for Fiji–Samoa, and unchanged for a network
 * that never goes near the seam, whose widest gap is the empty ocean the seam
 * is already in. It is the same choice [RouteArc.unwrapLongitudes] makes for
 * two points, made for *n*.
 *
 * ### Legs follow the nodes
 *
 * An arc's own unwrap is relative to its departure, which may now sit a turn
 * away from where the frame put that airport. [arcShifts] gives the whole
 * turns that bring each end of an arc into the frame's convention. For a
 * network narrower than a hemisphere those are one and the same shift — see
 * the proof in the KDoc there. For a wider one they can differ, and the arc is
 * then drawn once at each: it leaves one side of the card and enters the other,
 * which is what the same leg does on a wall map.
 */
object NetworkFraming {

    /**
     * [lons] shifted by whole turns onto the shortest arc of longitude that
     * contains all of them. Input order is kept; each value comes back
     * normalised to `[−180, 180)` and then shifted up by 360° if it lies west
     * of the widest gap. Empty in, empty out.
     */
    fun unwrapLongitudes(lons: DoubleArray): DoubleArray {
        if (lons.isEmpty()) return DoubleArray(0)
        val normalised = DoubleArray(lons.size) { normalise(lons[it]) }
        val sorted = normalised.sortedArray()

        // The gap that wraps from the eastmost value round to the westmost is a
        // candidate like any other; it wins for any set that does not straddle
        // the seam, which is why such a set is returned as it came.
        var widest = sorted.first() + 360.0 - sorted.last()
        var start = sorted.first()
        for (i in 0 until sorted.size - 1) {
            val gap = sorted[i + 1] - sorted[i]
            if (gap > widest) {
                widest = gap
                start = sorted[i + 1]
            }
        }

        return DoubleArray(normalised.size) {
            val lon = normalised[it]
            if (lon < start) lon + 360.0 else lon
        }
    }

    /**
     * [lon] moved by whole turns to the value nearest [reference], which need not
     * itself be inside `[−180, 180]` — a frame centred at 183° is an ordinary
     * caller.
     */
    fun nearestTurn(lon: Double, reference: Double): Double =
        lon + Math.rint((reference - lon) / 360.0) * 360.0

    /**
     * The whole-turn shifts, in degrees, at which [arc] has to be drawn so that
     * it appears in a frame centred on [centreLon].
     *
     * One shift when both ends land in the same turn — the usual case, and the
     * *only* case for a network whose unwrapped span is at most 180°: the arc's
     * destination is within 180° of its departure (it was sampled the short way
     * round), the departure is within half the span of the centre once shifted,
     * and the destination's own node is too, so the two candidate positions
     * for the destination would have to be 360° apart while both lying within
     * `span / 2 + 180°` of the centre, which needs `span > 180°`.
     *
     * Two shifts when they do differ: the arc is then drawn at each, so the part
     * leaving one side of the frame and the part entering the other are both
     * there.
     */
    fun arcShifts(arc: GeoArc, centreLon: Double): DoubleArray {
        val departure = nearestTurn(arc.departureLon, centreLon) - arc.departureLon
        val destination = nearestTurn(arc.destinationLon, centreLon) - arc.destinationLon
        return if (departure == destination) doubleArrayOf(departure) else doubleArrayOf(departure, destination)
    }

    /**
     * Into `[−180, 180)`. A value already there is returned untouched — the
     * arithmetic below perturbs the last bit, and a network nowhere near the
     * seam must come back exactly as it went in.
     */
    private fun normalise(lon: Double): Double {
        if (lon >= -180.0 && lon < 180.0) return lon
        var value = (lon + 180.0) % 360.0
        if (value < 0.0) value += 360.0
        return value - 180.0
    }
}
