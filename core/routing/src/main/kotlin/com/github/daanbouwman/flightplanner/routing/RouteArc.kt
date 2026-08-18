package com.github.daanbouwman.flightplanner.routing

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Samples a great circle into the points a route card draws.
 *
 * This lives in `:core:routing` rather than next to the composable that draws
 * it for the reason every other algorithm here does: the hard parts — spherical
 * interpolation, the ±180° seam, the degenerate cases — are pure functions of
 * six numbers, and they are worth unit-testing in milliseconds rather than
 * eyeballing in a preview.
 *
 * The output is **geography, not pixels**: degrees on a sphere, sampled once per
 * route on a background dispatcher. Turning them into a card is [MapFrame]'s job,
 * because the window they are drawn through depends on a canvas nobody has
 * measured yet at this point.
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

    /**
     * Points per arc drawn across a full-bleed route card.
     *
     * [DEFAULT_SAMPLES] was chosen for a 120 dp sparkline, where 24 chords are
     * imperceptible. Across a card at 3× — around 1,000 px — those same 24 chords
     * are 40 px each and the arc reads as faceted. The fix is sampling, not an
     * anti-aliasing flag: Compose already draws the path anti-aliased, and a
     * smoothly drawn polyline is still a polyline.
     *
     * A fixed number rather than one derived from the canvas, because sampling is
     * spherical interpolation and it runs on a background dispatcher, where the
     * canvas has not been measured yet. 128 puts a chord at about 8 px on the
     * widest card this app draws, and a batch of fifty routes at 200 KB, which is
     * the trade being made.
     */
    const val CARD_SAMPLES: Int = 128

    /** Below this angular separation the two endpoints are treated as one point. */
    private const val DEGENERATE_RADIANS = 1e-7

    /**
     * A great circle as geography — degrees, unwrapped, not yet projected.
     *
     * Degrees rather than a self-normalised box: normalising an arc into its own
     * unit box discards where on Earth it is, so a second layer drawn from the
     * same coordinates — a coastline — has nothing to line up with. A [MapFrame]
     * built from these points projects both through one window.
     *
     * Longitudes are already unwrapped, so a Pacific crossing runs past 180°
     * rather than jumping to −180°. Latitudes and longitudes are parallel arrays
     * for the reason everything here is: the next step walks them as columns.
     */
    fun sampleGeographic(
        depLat: Double,
        depLon: Double,
        destLat: Double,
        destLon: Double,
        samples: Int = DEFAULT_SAMPLES,
    ): GeoArc {
        val count = samples.coerceAtLeast(2)
        val lats = DoubleArray(count)
        val lons = DoubleArray(count)
        sampleInto(depLat, depLon, destLat, destLon, lats, lons)
        unwrapLongitudes(lons)
        return GeoArc(lats, lons)
    }

    /**
     * Fills [lats] and [lons] with points spaced evenly *along the great circle*
     * from departure to destination, in degrees, inclusive of both ends.
     *
     * Split out from [sampleGeographic] so that the spherical interpolation can be
     * asserted against real distances — a decorative curve and a true great circle
     * are indistinguishable once both have been drawn small, which is exactly the
     * mistake this separation exists to make catchable.
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
}

/**
 * A sampled great circle in degrees, with longitudes unwrapped past the seam.
 *
 * @see RouteArc.sampleGeographic
 */
class GeoArc(val lats: DoubleArray, val lons: DoubleArray) {
    init {
        require(lats.size == lons.size) { "${lats.size} latitudes against ${lons.size} longitudes" }
    }

    val size: Int get() = lats.size

    val departureLat: Double get() = lats.first()
    val departureLon: Double get() = lons.first()
    val destinationLat: Double get() = lats.last()
    val destinationLon: Double get() = lons.last()
}
