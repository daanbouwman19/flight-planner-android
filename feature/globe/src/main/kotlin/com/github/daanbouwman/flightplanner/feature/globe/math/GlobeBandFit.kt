package com.github.daanbouwman.flightplanner.feature.globe.math

import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * The camera for a globe **embedded in a card**, where the frame is part of the
 * picture.
 *
 * ### Whole planet, or a clean window — never a sphere cut by its frame
 *
 * [GlobeFit.framePoints] answers one question: how far out must the camera be
 * for every airport to be inside the box. On the immersive screen that is the
 * whole question. In a band inside a Stats card it is not, because the band has
 * edges a reader can see, and where the sphere's limb falls against them is a
 * design fact the fit knows nothing about. The visited-network band shipped with
 * the sphere touching both sides of the card and cropped flat by its bottom
 * edge: a disc a few percent larger than its frame, which reads as a planet that
 * did not fit rather than as either of the two things a framed sphere can
 * honestly be.
 *
 * Those two things are the regimes this settles between:
 *
 *  - **A window.** The limb is outside every corner of the band, so the band is
 *    a rectangle of imagery — a region, seen from above — and nothing says
 *    "sphere" but the curve of the arcs. The fit's own camera is kept.
 *  - **The whole planet.** The disc sits inside the band with [insetPx] of the
 *    card showing on the short axis, and the network across its face. The
 *    camera is pulled back (or in) to exactly the distance that makes the disc
 *    that size.
 *
 * The line between them is the band's **half-diagonal**: a disc whose radius is
 * under it leaves a corner of the band showing space past the limb, which is
 * the cut-sphere look. [CORNER_CLEARANCE] holds the limb a little past the
 * corner, so the atmosphere haze drawn along it is out of the frame too.
 *
 * ### Why the height is not solved here
 *
 * It could be — the disc radius is a closed form of the camera distance and the
 * focal length — but the focal length is proportional to the band's height, and
 * the fit's vertical constraint is too, so the band height and the camera would
 * be a fixed point to iterate for. The host sidesteps that: it sizes the band
 * as a square on the card's width (capped for tall windows), so the disc filling
 * the short axis is the disc filling the band, and this only has to settle the
 * camera for the box it is handed.
 */
internal object GlobeBandFit {

    /**
     * How far past the band's corner the limb must be for the band to read as a
     * window rather than as a cropped disc. Six percent of the half-diagonal is
     * about 14 dp on a 328 dp band — the atmosphere's width and a little.
     */
    private const val CORNER_CLEARANCE = 1.06f

    /**
     * [fitted] — the camera [GlobeFit.framePoints] chose for [viewport] —
     * settled into one of the two regimes above. Centre, bearing and tilt are
     * kept; only the altitude moves, and only when the disc would otherwise be
     * cut by the frame.
     *
     * @param insetPx how much of the card shows between the disc and the band's
     *   edge on the short axis, when the whole planet is shown.
     */
    fun settle(fitted: GlobeCamera, viewport: GlobeViewport, insetPx: Float): GlobeCamera {
        if (viewport.width < 1f || viewport.height < 1f) return fitted

        val focal = focalLength(viewport, fitted.fovY)
        val disc = discRadiusPx(fitted.distance, focal)
        val halfDiagonal = hypot(viewport.width, viewport.height) / 2f
        if (disc >= halfDiagonal * CORNER_CLEARANCE) return fitted

        val fill = min(viewport.width, viewport.height) / 2f - insetPx
        if (fill <= 0f) return fitted

        // Inverting discRadiusPx: r = f / sqrt(d² − 1)  ⇒  d = sqrt(1 + (f / r)²).
        val ratio = focal / fill
        val distance = sqrt(1f + ratio * ratio)
        return fitted.copy(altitude = (distance - 1f).coerceIn(MIN_ALTITUDE, MAX_ALTITUDE))
    }

    /**
     * The radius, in pixels, of the sphere's disc as seen top-down from
     * [distance] with focal length [focal].
     *
     * The limb is where the ray from the camera is tangent to the unit sphere,
     * at `sin β = 1 / d`; on screen that is `f · tan β = f / sqrt(d² − 1)`. From
     * the surface itself the disc is the whole sky.
     */
    fun discRadiusPx(distance: Float, focal: Float): Float =
        if (distance <= 1f) Float.POSITIVE_INFINITY else focal / sqrt(distance * distance - 1f)

    /** The same focal length [GlobeFit] and the projection use: half the height over `tan(fovY / 2)`. */
    fun focalLength(viewport: GlobeViewport, fovY: Float): Float =
        (viewport.height * 0.5f) / tan(fovY * 0.5f)
}
