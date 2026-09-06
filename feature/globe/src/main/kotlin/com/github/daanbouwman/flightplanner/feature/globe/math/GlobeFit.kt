package com.github.daanbouwman.flightplanner.feature.globe.math

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tan

/**
 * Where the camera starts, and where "re-fit" puts it back.
 *
 * The camera is aimed at the middle of whatever it is framing and pulled back
 * until every point of it is inside the viewport, so the first frame answers the
 * question the screen is about — *how far is this, and across what* — without
 * the user touching anything.
 *
 * ### Why this takes a viewport, when the reference does not
 *
 * The Rust original solves the fit from the half-angle and `fovY` alone, and
 * that was ported here unchanged. It is wrong on a phone, and by a lot. `fovY`
 * is the *vertical* field of view; the horizontal one is
 * `atan(tan(fovY / 2) · width / height)`, which on the immersive screen — a
 * window a little over twice as tall as it is wide — is about half of it. The
 * aspect-blind fit put both airports off the left and right edges of the screen
 * whose entire purpose is to show the route bigger, and left the deep hero with
 * under a degree of margin on an ordinary leg, which is why a label plate hanging
 * beside its dot was being sliced by the hero's own clip.
 *
 * So the solve below is exact and takes the box it is solving for. For a point at
 * angular distance `α` from the centre of the disc, seen from distance `d`, the
 * ray to it leaves the camera at `β` where `sin(α + β) = d · sin β`. Inverting
 * that for the `β` a given screen radius subtends is one line, and it is the
 * whole of [distanceToFit].
 *
 * ### Why it is not a bounding-box solve either
 *
 * A great circle between two airports is a single arc with no width, and fitting
 * a box to the *arc* would zoom hard into a north-south leg and leave a
 * transatlantic one tiny. What is fitted is the set of points that must be
 * visible — two ends, or every airport in the logbook — and the direction each
 * one lies in on screen, which is what lets a north-south leg use the tall axis
 * of a portrait window instead of the narrow one.
 */
internal object GlobeFit {

    /**
     * How much of each half-axis is kept clear of the outermost point.
     *
     * A dot is not the thing that has to fit — a label plate hangs beside it, and
     * on the immersive screen a control plate sits in each top corner. 18% of the
     * half-width is about 37 dp on a 411 dp window, which is a plate.
     *
     * This replaces the reference's `INITIAL_ZOOM_FACTOR`, a multiplier on the
     * camera's *distance offset* documented as leaving the ends "comfortably
     * inside the limb". A distance multiplier is not a margin: what it buys on
     * screen depends on the aspect ratio and on how far out the camera already
     * was, which is why it bought nothing at all on the immersive screen.
     */
    private const val EDGE_MARGIN = 0.18f

    /**
     * The camera that frames the leg from [depLat]/[depLon] to
     * [destLat]/[destLon] in [viewport], north up and top down.
     *
     * Bearing and tilt are zero on purpose. A fit that also chose an orientation
     * would be making a second claim — *and this is the interesting way round to
     * look at it* — which no data here supports.
     *
     * **This is [framePoints] over the two ends, and nothing else.** It used to be
     * a second implementation, and the copy drifted: it took the centre as the
     * average of the two latitudes and a shortest-way longitude, which is a point
     * on the sphere that can be most of a radian off the great circle it is meant
     * to be framing. Past roughly 12,000 km that put both airport plates outside
     * the viewport in the opening frame, and past about 138° of arc it put the arc
     * itself behind the limb — on legs the seeded A350-1000 and 777-200LR can fly.
     * The comment excusing it claimed the two midpoints "differ only for a leg
     * spanning most of the planet, where the camera is pulled back far enough that
     * the difference is under a pixel"; both halves of that were false.
     */
    fun frameRoute(
        depLat: Double,
        depLon: Double,
        destLat: Double,
        destLon: Double,
        viewport: GlobeViewport,
    ): GlobeCamera = framePoints(
        lats = doubleArrayOf(depLat, destLat),
        lons = doubleArrayOf(depLon, destLon),
        viewport = viewport,
    )

    /**
     * The camera that frames every one of [lats]/[lons] in [viewport].
     *
     * The centre is the **normalised sum of the unit vectors**, not the average of
     * the degrees. Averaging degrees is wrong twice over: across the antimeridian
     * it lands in the wrong ocean, and near a pole it is not even meaningful. The
     * vector mean has neither problem and is three lines — and for two points it
     * is the midpoint of the great circle between them, which is the whole of
     * [frameRoute].
     *
     * The distance is the furthest any point needs, so a logbook confined to
     * Europe zooms in and one spanning two hemispheres pulls back to show the
     * whole planet, which is the entire argument for drawing it on a sphere.
     */
    fun framePoints(lats: DoubleArray, lons: DoubleArray, viewport: GlobeViewport): GlobeCamera {
        if (lats.isEmpty() || viewport.height < 1f || viewport.width < 1f) return GlobeCamera()

        var sx = 0f
        var sy = 0f
        var sz = 0f
        for (i in lats.indices) {
            val w = latLonToWorld(lats[i].toFloat(), lons[i].toFloat())
            sx += w.x
            sy += w.y
            sz += w.z
        }
        val sum = Vec3(sx, sy, sz)
        // A set with no mean direction — an exactly antipodal pair, or a ring
        // around the planet. There is no centre that sees all of it, so centre on
        // the first point and let the solve below run out to MAX_DISTANCE.
        val centre = if (sum.length() < DEGENERATE_MEAN) {
            latLonToWorld(lats[0].toFloat(), lons[0].toFloat())
        } else {
            sum.normalize()
        }

        val (east, north) = tangentFrame(centre)

        var distance = MIN_DISTANCE
        for (i in lats.indices) {
            val p = latLonToWorld(lats[i].toFloat(), lons[i].toFloat())
            val alpha = acos((centre dot p).coerceIn(-1f, 1f))
            // Which way this point lies from the centre, on screen. North is
            // screen-up because the fit sets bearing to zero.
            val tangential = p - centre * (centre dot p)
            val phi = if (tangential.length() < DEGENERATE_MEAN) {
                0f
            } else {
                atan2(tangential dot east, tangential dot north)
            }
            distance = max(distance, distanceToFit(alpha, phi, viewport))
        }

        val altitude = (distance - 1f)
            .coerceIn(sharpestAltitude(viewport), MAX_ALTITUDE)

        return GlobeCamera(
            centerLat = asin(centre.y.coerceIn(-1f, 1f)) * RAD_TO_DEG,
            centerLon = atan2(centre.x, centre.z) * RAD_TO_DEG,
            altitude = altitude,
        )
    }

    /**
     * The distance at which a point `alpha` radians from the centre of the disc,
     * lying in screen direction `phi`, sits just inside the margin.
     *
     * `phi` is measured from screen-up, so `sin` is the horizontal component and
     * `cos` the vertical. The screen radius available in that direction is
     * whichever half-axis it runs out of first — which is what makes a
     * north-south leg in a portrait window use the tall axis.
     *
     * The two branches are the two regimes. While the point is inside the field of
     * view, `sin(α + β) = d · sin β` inverts to `d = sin(α + β) / sin β`. Past
     * `α + β = π/2` the ray at `β` has stopped meeting the sphere and the limit is
     * the limb itself, where `cos α = 1 / d`. They agree exactly at the boundary,
     * so the result is continuous in `α`.
     */
    private fun distanceToFit(alpha: Float, phi: Float, viewport: GlobeViewport): Float {
        val focal = (viewport.height * 0.5f) / tan(DEFAULT_FOV_Y * 0.5f)
        val horizontal = abs(sin(phi))
        val vertical = abs(cos(phi))
        val radiusPx = min(
            if (horizontal > AXIS_EPSILON) {
                viewport.width * 0.5f * (1f - EDGE_MARGIN) / horizontal
            } else {
                Float.MAX_VALUE
            },
            if (vertical > AXIS_EPSILON) {
                viewport.height * 0.5f * (1f - EDGE_MARGIN) / vertical
            } else {
                Float.MAX_VALUE
            },
        )
        val beta = atan(radiusPx / focal)
        return when {
            beta < AXIS_EPSILON -> MAX_DISTANCE
            alpha + beta <= HALF_PI -> sin(alpha + beta) / sin(beta)
            alpha < HALF_PI -> 1f / cos(alpha)
            // More than a hemisphere away from the centre: no camera sees it.
            else -> MAX_DISTANCE
        }
    }

    /**
     * The lowest altitude at which the imagery is still at least as sharp as the
     * screen — one texel of the deepest available tile per pixel.
     *
     * **This is a floor on the fit, not on the camera.** A pinch may go closer;
     * what may not happen is that opening a route *hands* the reader an upsampled
     * photograph. Below this height the picture stops gaining detail and only
     * gains blur, so framing a short leg tighter buys nothing and costs the one
     * thing the globe is for.
     *
     * It is why a 71 NM leg does not fill the hero. That used to be the doing of an
     * `inverseZoom` clamped to 8, which pinned **every** leg under about 776 NM to
     * one identical camera — including every route the seeded ATR 42-600 can fly,
     * since its range is 703 NM. The clamp is gone; this is the honest constraint
     * that was hiding behind it, and unlike the 8 it moves when the imagery does.
     */
    private fun sharpestAltitude(viewport: GlobeViewport): Float {
        val focal = (viewport.height * 0.5f) / tan(DEFAULT_FOV_Y * 0.5f)
        return (Quadtree.FINEST_TEXEL_RADIANS * focal).coerceIn(MIN_ALTITUDE, MAX_ALTITUDE)
    }

    /**
     * East and north unit tangents at [centre].
     *
     * At a pole the two are not defined — every direction is south — and any
     * orthonormal pair does, because a fit centred on a pole is symmetric about it.
     */
    private fun tangentFrame(centre: Vec3): Pair<Vec3, Vec3> {
        val cosLat = kotlin.math.sqrt(max(0f, 1f - centre.y * centre.y))
        if (cosLat < AXIS_EPSILON) return Vec3(1f, 0f, 0f) to Vec3(0f, 0f, 1f)
        val sinLon = centre.x / cosLat
        val cosLon = centre.z / cosLat
        val sinLat = centre.y
        return Vec3(cosLon, 0f, -sinLon) to Vec3(-sinLat * sinLon, cosLat, -sinLat * cosLon)
    }

    /** Below this, a vector sum has no usable direction. */
    private const val DEGENERATE_MEAN = 1e-4f

    /** Guards a division by a component that is effectively zero. */
    private const val AXIS_EPSILON = 1e-4f

    private const val HALF_PI = (Math.PI / 2.0).toFloat()

    private const val RAD_TO_DEG = (180.0 / Math.PI).toFloat()
}
