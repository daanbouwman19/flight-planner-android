package com.github.daanbouwman.flightplanner.feature.globe.math

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** Vertical field of view: 60 degrees. */
internal const val DEFAULT_FOV_Y: Float = (PI / 3.0).toFloat()

/** Camera just above the surface at this distance from the globe centre. */
internal const val MIN_DISTANCE: Float = 1.0001f

/** Whole globe with breathing room. */
internal const val MAX_DISTANCE: Float = 10.0f

/** Minimum height above the unit-sphere surface. */
internal const val MIN_ALTITUDE: Float = MIN_DISTANCE - 1f

/** Maximum height above the unit-sphere surface. */
internal const val MAX_ALTITUDE: Float = MAX_DISTANCE - 1f

/** Furthest the view may lean from top-down. */
internal const val MAX_TILT: Float = (PI / 2.5).toFloat()

/** A point on screen, in pixels, origin top-left. */
internal data class ScreenPoint(val x: Float, val y: Float) {
    operator fun minus(o: ScreenPoint): ScreenPoint = ScreenPoint(x - o.x, y - o.y)
    fun length(): Float = hypot(x, y)
}

/**
 * The rectangle the globe is drawn into, in pixels.
 *
 * Its own type rather than `androidx.compose.ui.geometry.Rect` because this file
 * and everything it serves is pure JVM — the whole point of G1 is that the
 * camera and the quadtree can be unit-tested in milliseconds without an Android
 * runtime under them.
 */
internal data class GlobeViewport(val width: Float, val height: Float) {
    val centerX: Float get() = width * 0.5f
    val centerY: Float get() = height * 0.5f

    fun contains(p: ScreenPoint): Boolean =
        p.x >= 0f && p.y >= 0f && p.x <= width && p.y <= height
}

/**
 * Pre-computed camera basis vectors, valid for one camera state.
 *
 * Computed once per frame with [GlobeCamera.computeBasis] and then passed to
 * [rotateFast] and [facingValueFast], which is what keeps the per-vertex path
 * free of `sin`/`cos`. A quadtree traversal calls those a few thousand times per
 * frame; recomputing the basis inside each would be four transcendentals per
 * call for a value that cannot have changed.
 */
internal class CameraBasis(
    val right: Vec3,
    val up: Vec3,
    val look: Vec3,
    val position: Vec3,
    /** `position / |position|` — reduces the back-face test to one dot product. */
    val facingUnit: Vec3,
)

/** Rotate a world point into camera space using a pre-computed basis. */
internal fun rotateFast(basis: CameraBasis, w: Vec3): Vec3 {
    val d = w - basis.position
    return Vec3(d dot basis.right, d dot basis.up, d dot basis.look)
}

/** Back-face culling value using a pre-computed basis (a single dot product). */
internal fun facingValueFast(basis: CameraBasis, w: Vec3): Float = w dot basis.facingUnit

/** The unit-sphere point at [latDeg] / [lonDeg]. */
internal fun latLonToWorld(latDeg: Float, lonDeg: Float): Vec3 {
    val lat = latDeg * DEG_TO_RAD
    val lon = lonDeg * DEG_TO_RAD
    val cosLat = cos(lat)
    return Vec3(cosLat * sin(lon), sin(lat), cosLat * cos(lon))
}

private const val DEG_TO_RAD: Float = (PI / 180.0).toFloat()
private const val RAD_TO_DEG: Float = (180.0 / PI).toFloat()

/**
 * Where the globe is being looked at from, as five numbers rather than a matrix.
 *
 * Ported field for field from `src/gui/components/globe/camera.rs` in the Rust
 * desktop app, which is the behavioural reference for this whole phase. The
 * parameterisation is what makes the rest of the globe tractable: a quadtree
 * needs an altitude to derive a horizon angle from, a fit needs a centre to
 * aim at, and a gesture needs a bearing and a tilt to add to — none of which
 * survive being stored as a 4×4.
 *
 * The matrices Filament is handed are *derived* from this, in
 * [CameraMatrices], and `CameraMatrixConsistencyTest` asserts that the CPU
 * [project] here and those matrices agree to well under a pixel. That test is
 * the load-bearing one: labels, hit-testing and tile selection all run on this
 * projection while the imagery runs on the GPU's, and any disagreement between
 * them shows up as a label sliding off its own dot.
 *
 * @property centerLat latitude of the screen-centre nadir, degrees
 * @property centerLon longitude of the screen-centre nadir, degrees
 * @property altitude camera height above the unit-sphere surface
 * @property bearing 0 = north up, positive = clockwise, radians
 * @property tilt 0 = top-down, up to [MAX_TILT], radians
 * @property fovY vertical field of view, radians
 */
internal data class GlobeCamera(
    val centerLat: Float = 0f,
    val centerLon: Float = 0f,
    val altitude: Float = 2f,
    val bearing: Float = 0f,
    val tilt: Float = 0f,
    val fovY: Float = DEFAULT_FOV_Y,
) {

    /** Distance from the globe centre to the camera: `1 + altitude`. */
    val distance: Float get() = 1f + altitude

    /**
     * The camera's world-space basis.
     *
     * `right` is screen-right and is deliberately tilt-independent — tilting
     * pivots about it, so a tilt that changed it would also spin the view.
     * `look` points into the scene, `up` is screen-up after the tilt, and
     * `position` sits on the sphere of radius `1 + altitude`.
     */
    fun computeBasis(): CameraBasis {
        val lat = centerLat * DEG_TO_RAD
        val lon = centerLon * DEG_TO_RAD
        val sLat = sin(lat)
        val cLat = cos(lat)
        val sLon = sin(lon)
        val cLon = cos(lon)

        // Nadir: from the origin toward centerLat/centerLon on the sphere.
        val n = Vec3(cLat * sLon, sLat, cLat * cLon)

        // North and east tangents at the nadir.
        val northN = Vec3(-sLat * sLon, cLat, -sLat * cLon)
        val eastN = Vec3(cLon, 0f, -sLon)

        val sB = sin(bearing)
        val cB = cos(bearing)
        // Screen-up at tilt = 0, rotated by the bearing.
        val upBase = northN * cB + eastN * sB

        val right = (upBase cross n).normalize()

        val sT = sin(tilt)
        val cT = cos(tilt)

        val look = (n * -cT + upBase * sT).normalize()
        val up = (upBase * cT + n * sT).normalize()

        // Camera position on the sphere of radius r, placed along the look ray
        // through the nadir at [nadirDistance].
        val t = nadirDistance()
        val position = n * (1f + t * cT) + upBase * (-t * sT)

        return CameraBasis(
            right = right,
            up = up,
            look = look,
            position = position,
            facingUnit = position * (1f / distance),
        )
    }

    /**
     * How far the camera is from the point directly under it.
     *
     * Solved from `|P| = r` for `P` on the look ray through the nadir, which is
     * also why the nadir is what lands at the centre of the screen at every tilt:
     * [computeBasis] places the camera *on* that ray. Tilting therefore moves the
     * camera genuinely further from the ground it is looking at — at altitude
     * 0.01 this grows from 0.010 at nadir to 0.031 at `MAX_TILT` — which is real
     * geometry rather than an artefact, and is the reason a tilt-versus-nadir
     * detail comparison has to hold *this* constant rather than the altitude.
     */
    fun nadirDistance(): Float {
        val sT = sin(tilt)
        val cT = cos(tilt)
        val r = distance
        return -cT + sqrt(max(0f, r * r - sT * sT))
    }

    /** Focal length in pixels: half the viewport height over `tan(fovY / 2)`. */
    fun focalPixels(viewportHeight: Float): Float =
        (viewportHeight * 0.5f) / tan(fovY * 0.5f)

    /** Rotate a world point into camera space. Prefer [rotateFast] in a loop. */
    fun rotate(w: Vec3): Vec3 = rotateFast(computeBasis(), w)

    /**
     * Perspective-project a camera-space point to the screen.
     *
     * Returns null when the point is at or behind the camera plane, which the
     * caller must treat as "no position" rather than as a position off-screen —
     * a point behind the camera projects to a plausible-looking pixel with the
     * wrong sign.
     */
    fun project(camPoint: Vec3, viewport: GlobeViewport): ScreenPoint? {
        val depth = camPoint.z
        if (depth <= 1e-6f) return null
        val f = focalPixels(viewport.height)
        return ScreenPoint(
            viewport.centerX + f * camPoint.x / depth,
            viewport.centerY - f * camPoint.y / depth,
        )
    }

    fun worldToScreen(w: Vec3, viewport: GlobeViewport): ScreenPoint? =
        project(rotate(w), viewport)

    /** Back-face culling value: `dot(w, position / |position|)`. */
    fun facingValue(w: Vec3): Float = facingValueFast(computeBasis(), w)

    /**
     * Points whose [facingValue] is below this are behind the horizon.
     *
     * The margin widens the kept set just past the geometric horizon so limb
     * tiles alpha-fade out rather than popping off.
     */
    fun cullThreshold(): Float = 1f / distance - CULLING_FADE_MARGIN

    /**
     * Ray-sphere intersection from the camera, or null when the ray misses.
     *
     * This is what makes a drag feel like a hand on a globe rather than like a
     * scroll: the point under the finger is found once, and the camera is then
     * solved so that point stays under the finger.
     */
    fun screenToWorld(cursor: ScreenPoint, viewport: GlobeViewport): Vec3? {
        val f = focalPixels(viewport.height)
        val ix = (cursor.x - viewport.centerX) / f
        val iy = -(cursor.y - viewport.centerY) / f
        val basis = computeBasis()
        val dir = basis.right * ix + basis.up * iy + basis.look

        val a = dir dot dir
        val b = basis.position dot dir
        val c = (basis.position dot basis.position) - 1f

        val disc = b * b - a * c
        if (disc < 0f) return null
        val t = (-b - sqrt(disc)) / a
        if (t < 0f) return null
        return basis.position + dir * t
    }

    /**
     * Whether the sphere covers the whole strip the system draws its status-bar
     * glyphs over.
     *
     * The question a host actually needs answered before asking for light
     * glyphs: at a long-range camera the sphere retreats from the top of the
     * window and what is painted under the clock is `GlobeInk.space`, which **is**
     * `colorScheme.surface` in a light theme — light glyphs on a near-white page.
     *
     * Asked with the ray [screenToWorld] already casts, rather than off the
     * projected limb. The limb version scanned every sample for the global
     * minimum `y`, which ignores `x`: under tilt and bearing the silhouette is a
     * *rotated* ellipse whose apex can sit far to one side while the sphere at
     * top-centre, where the clock is, is nowhere near the strip. It also had a
     * silent cliff — fewer than three samples in front of the camera and it gave
     * up, even with the sphere filling the viewport. A ray-sphere intersection has
     * neither problem and is exact at any tilt and bearing.
     *
     * **Conservative on purpose.** Every sample must hit, because the two errors
     * are not equal: a wrongly-dark glyph over imagery is slightly less
     * contrasty, and a wrongly-light glyph over a white page is invisible.
     */
    fun coversStatusStrip(viewport: GlobeViewport, stripPx: Float): Boolean {
        if (stripPx <= 0f || viewport.width < 1f || viewport.height < 1f) return false
        // The strip's own corners and midpoints, top and bottom edges. The disc
        // is convex on screen, so a handful of samples across the rectangle is
        // enough to say the whole of it is covered.
        for (col in 0..STRIP_SAMPLES) {
            val x = viewport.width * col / STRIP_SAMPLES
            if (screenToWorld(ScreenPoint(x, 0f), viewport) == null) return false
            if (screenToWorld(ScreenPoint(x, stripPx), viewport) == null) return false
        }
        return true
    }

    /**
     * [screenToWorld] with the image-plane coordinates clamped to the limb, so a
     * drag that starts on the black outside the disc still grabs the globe.
     */
    fun screenToWorldClamped(cursor: ScreenPoint, viewport: GlobeViewport): Vec3 {
        val f = focalPixels(viewport.height)
        var ix = (cursor.x - viewport.centerX) / f
        var iy = -(cursor.y - viewport.centerY) / f

        val r = distance
        // Limb boundary at tilt 0: ix² + iy² ≤ 1 / (r² − 1).
        val limbR2 = 1f / max(1e-6f, r * r - 1f)
        val r2 = ix * ix + iy * iy
        if (r2 > limbR2) {
            val s = sqrt(limbR2 / r2)
            ix *= s
            iy *= s
        }

        val basis = computeBasis()
        val dir = basis.right * ix + basis.up * iy + basis.look
        val a = dir dot dir
        val b = basis.position dot dir
        val c = (basis.position dot basis.position) - 1f
        val disc = max(0f, b * b - a * c)
        val t = (-b - sqrt(disc)) / a
        return basis.position + dir * t
    }

    /**
     * The angle from the nadir axis beyond which sphere points are hidden.
     */
    fun horizonAngle(): Float = acos((1f / distance).coerceIn(-1f, 1f))

    /**
     * Returns a camera whose centre puts [worldPoint] at [targetScreen].
     *
     * Newton's method on the two centre angles, four iterations, exactly as the
     * Rust original does it. There is no closed form once tilt is non-zero — the
     * projection of a point is a genuinely non-linear function of the centre —
     * and four iterations converge to well under a pixel from any start a drag
     * can produce, because a drag's step is small.
     *
     * Tilt, bearing, altitude and field of view are left alone: this is a pan,
     * and a pan that quietly changed the zoom would be the classic globe bug
     * where the world creeps closer every time you drag it.
     */
    fun panTo(
        worldPoint: Vec3,
        targetScreen: ScreenPoint,
        viewport: GlobeViewport,
    ): GlobeCamera {
        var camera = this
        repeat(NEWTON_ITERATIONS) {
            val current = camera.worldToScreen(worldPoint, viewport) ?: return camera
            val err = current - targetScreen
            if (err.length() < 0.5f) return camera

            val sLat = camera.copy(centerLat = camera.centerLat + PAN_EPS_DEGREES)
                .worldToScreen(worldPoint, viewport) ?: return camera
            val dLat = ScreenPoint(
                (sLat.x - current.x) / PAN_EPS_DEGREES,
                (sLat.y - current.y) / PAN_EPS_DEGREES,
            )

            val sLon = camera.copy(centerLon = camera.centerLon + PAN_EPS_DEGREES)
                .worldToScreen(worldPoint, viewport) ?: return camera
            val dLon = ScreenPoint(
                (sLon.x - current.x) / PAN_EPS_DEGREES,
                (sLon.y - current.y) / PAN_EPS_DEGREES,
            )

            // Solve the 2×2 system dLat·Δlat + dLon·Δlon = −err.
            val det = dLat.x * dLon.y - dLat.y * dLon.x
            if (abs(det) < 1e-10f) return camera
            val nx = -err.x
            val ny = -err.y
            val deltaLat = (dLon.y * nx - dLon.x * ny) / det
            val deltaLon = (dLat.x * ny - dLat.y * nx) / det

            camera = camera.copy(
                centerLat = (camera.centerLat + deltaLat).coerceIn(-85f, 85f),
                centerLon = camera.centerLon + deltaLon,
            )
        }
        return camera
    }

    /** [centerLon] folded into (−180, 180], for display and for a stable state key. */
    fun normalizedLongitude(): Float {
        var lon = centerLon % 360f
        if (lon > 180f) lon -= 360f
        if (lon <= -180f) lon += 360f
        return lon
    }

    /** The compass heading the view is currently held at, in degrees 0..<360. */
    fun bearingDegrees(): Float {
        var deg = (bearing * RAD_TO_DEG) % 360f
        if (deg < 0f) deg += 360f
        return deg
    }

    /** Whether the view has been turned or leaned away from plain north-up. */
    fun isRotated(): Boolean = abs(bearing) > 1e-3f || abs(tilt) > 1e-3f

    companion object {
        /** Columns sampled across the status strip by [coversStatusStrip]. */
        private const val STRIP_SAMPLES = 4

        private const val CULLING_FADE_MARGIN = 0.3f
        private const val NEWTON_ITERATIONS = 4
        private const val PAN_EPS_DEGREES = 0.005f
    }
}
