package com.github.daanbouwman.flightplanner.routing

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Samples a great circle and flattens it into something a sparkline can draw.
 *
 * This lives in `:core:routing` rather than next to the composable that draws
 * it for the reason every other algorithm here does: the hard parts — spherical
 * interpolation, the ±180° seam, the degenerate cases — are pure functions of
 * six numbers, and they are worth unit-testing in milliseconds rather than
 * eyeballing in a preview.
 *
 * The output is deliberately **normalised rather than measured in pixels**, so
 * the drawing code is a multiply and the geometry is computed once per route on
 * a background dispatcher instead of once per frame per visible card.
 */
object RouteArc {

    /**
     * Points per arc.
     *
     * A great circle drawn on an equirectangular projection is a shallow curve,
     * not a wiggle, so this only has to be dense enough that the polyline reads
     * as smooth at card size. Twenty-four segments is imperceptible from a
     * straight-line-per-degree rendering at any size this is drawn at, and it
     * keeps a fifty-route batch to 2,400 floats.
     */
    const val DEFAULT_SAMPLES: Int = 24

    /** Below this angular separation the two endpoints are treated as one point. */
    private const val DEGENERATE_RADIANS = 1e-7

    /**
     * The great circle between two airports, projected equirectangularly and
     * normalised into the unit box, as interleaved `x, y` pairs.
     *
     * `x` runs west to east and `y` runs **north to south**, i.e. `y = 0` is the
     * northernmost point, because that is the orientation a canvas draws in and
     * flipping it here saves every caller from remembering to.
     *
     * ### Why one scale for both axes
     *
     * Both axes are divided by the *same* number — the larger of the two spans —
     * rather than each being stretched to fill its own axis. Independent
     * normalisation would make every route fill the box, so a due-east hop and a
     * polar crossing would draw the identical shape and the sparkline would
     * convey nothing beyond "a route exists". Sharing the scale means the
     * dominant axis spans the full box, the other is centred in proportion, and
     * the curve's bow is real: a route that bends is a route that bends.
     *
     * It is still a *sparkline*, not a map — the projection has no aspect
     * correction by latitude, so a high-latitude route looks wider than it flies.
     * Correcting that would squash exactly the routes whose curvature is the most
     * interesting thing about them.
     *
     * ### Degenerate cases
     *
     * Two airports at the same point, or exactly antipodal, have no unique great
     * circle between them. Both fall back to interpolating the endpoints
     * linearly, which for the coincident case collapses to a dot in the centre
     * of the box and for the antipodal case draws *a* valid shortest path rather
     * than refusing to draw anything.
     */
    fun normalisedPath(
        depLat: Double,
        depLon: Double,
        destLat: Double,
        destLon: Double,
        samples: Int = DEFAULT_SAMPLES,
    ): FloatArray {
        val count = samples.coerceAtLeast(2)
        val lats = DoubleArray(count)
        val lons = DoubleArray(count)
        sampleInto(depLat, depLon, destLat, destLon, lats, lons)
        unwrapLongitudes(lons)
        return normalise(lats, lons)
    }

    /**
     * Fills [lats] and [lons] with points spaced evenly *along the great circle*
     * from departure to destination, in degrees, inclusive of both ends.
     *
     * Split out from [normalisedPath] so that the spherical interpolation can be
     * asserted against real distances — a decorative curve and a true great
     * circle are indistinguishable once both have been squashed into a unit box,
     * which is exactly the mistake this separation exists to make catchable.
     *
     * Longitudes are raw: they may step across the ±180° seam. Call
     * [unwrapLongitudes] before drawing.
     */
    internal fun sampleInto(
        depLat: Double,
        depLon: Double,
        destLat: Double,
        destLon: Double,
        lats: DoubleArray,
        lons: DoubleArray,
    ) {
        val count = lats.size

        val lat1 = Math.toRadians(depLat)
        val lon1 = Math.toRadians(depLon)
        val lat2 = Math.toRadians(destLat)
        val lon2 = Math.toRadians(destLon)

        val cosLat1 = cos(lat1)
        val cosLat2 = cos(lat2)
        val x1 = cosLat1 * cos(lon1)
        val y1 = cosLat1 * sin(lon1)
        val z1 = sin(lat1)
        val x2 = cosLat2 * cos(lon2)
        val y2 = cosLat2 * sin(lon2)
        val z2 = sin(lat2)

        val dot = (x1 * x2 + y1 * y2 + z1 * z2).coerceIn(-1.0, 1.0)
        val omega = atan2(sqrt(1.0 - dot * dot), dot)
        val sinOmega = sin(omega)
        // Coincident and antipodal both leave sin(omega) at zero, where the slerp
        // weights below divide by nothing useful. Linear interpolation of the
        // endpoints is the honest fallback for both.
        val slerpable = omega > DEGENERATE_RADIANS && abs(sinOmega) > DEGENERATE_RADIANS

        for (i in 0 until count) {
            val f = i.toDouble() / (count - 1)
            if (slerpable) {
                val a = sin((1.0 - f) * omega) / sinOmega
                val b = sin(f * omega) / sinOmega
                val px = a * x1 + b * x2
                val py = a * y1 + b * y2
                val pz = a * z1 + b * z2
                lats[i] = Math.toDegrees(atan2(pz, sqrt(px * px + py * py)))
                lons[i] = Math.toDegrees(atan2(py, px))
            } else {
                lats[i] = depLat + (destLat - depLat) * f
                // Interpolate along the shorter way round, so a fallback path
                // near the seam does not cross the whole planet to get there.
                lons[i] = depLon + longitudeDelta(depLon, destLon) * f
            }
        }
    }

    /**
     * Removes the ±180° discontinuity by making each longitude continuous with
     * its predecessor.
     *
     * Without this, a route crossing the antimeridian produces a sample at
     * +179.9 followed by one at -179.9 and the polyline draws a horizontal line
     * back across the entire projection — the single most visible way to get a
     * world-map arc wrong.
     */
    internal fun unwrapLongitudes(lons: DoubleArray) {
        for (i in 1 until lons.size) {
            lons[i] = lons[i - 1] + longitudeDelta(lons[i - 1], lons[i])
        }
    }

    private fun normalise(lats: DoubleArray, lons: DoubleArray): FloatArray {
        var minLat = lats[0]
        var maxLat = lats[0]
        var minLon = lons[0]
        var maxLon = lons[0]
        for (i in 1 until lats.size) {
            if (lats[i] < minLat) minLat = lats[i]
            if (lats[i] > maxLat) maxLat = lats[i]
            if (lons[i] < minLon) minLon = lons[i]
            if (lons[i] > maxLon) maxLon = lons[i]
        }

        val span = max(maxLon - minLon, maxLat - minLat)
        // A zero span is the coincident-airports case; any non-zero divisor puts
        // every point at the centre, which is what a dot wants.
        val scale = if (span > 1e-9) span else 1.0
        val midLat = (minLat + maxLat) / 2.0
        val midLon = (minLon + maxLon) / 2.0

        val out = FloatArray(lats.size * 2)
        for (i in lats.indices) {
            out[i * 2] = (0.5 + (lons[i] - midLon) / scale).toFloat()
            // Negated: latitude grows north, canvas y grows down.
            out[i * 2 + 1] = (0.5 - (lats[i] - midLat) / scale).toFloat()
        }
        return out
    }
}
