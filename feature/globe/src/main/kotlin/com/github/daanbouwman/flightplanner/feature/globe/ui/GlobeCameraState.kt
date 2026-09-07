package com.github.daanbouwman.flightplanner.feature.globe.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.VectorConverter
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Velocity
import com.github.daanbouwman.flightplanner.feature.globe.math.GlobeCamera
import com.github.daanbouwman.flightplanner.feature.globe.math.GlobeViewport
import com.github.daanbouwman.flightplanner.feature.globe.math.MAX_ALTITUDE
import com.github.daanbouwman.flightplanner.feature.globe.math.MAX_TILT
import com.github.daanbouwman.flightplanner.feature.globe.math.MIN_ALTITUDE
import com.github.daanbouwman.flightplanner.feature.globe.math.ScreenPoint
import com.github.daanbouwman.flightplanner.feature.globe.math.Vec3
import com.github.daanbouwman.flightplanner.feature.globe.math.latLonToWorld
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The camera, as something a screen can read and a gesture can move — and the
 * **only** owner of anything that moves it over time.
 *
 * ### One motion at a time
 *
 * A spring to a target and a fling after a pan are both motion, and there is at
 * most one of it. The fling used to be a local `Animatable` inside the surface's
 * pointer handler, which meant `stop()` could not stop it, a second flick ran a
 * second decay concurrently, and tapping ± during a fling had two animations
 * writing the camera. Every move now goes through [animateTo] or [fling], each of
 * which begins with [cancelMotion], and a touch-down calls [cancelMotion] directly
 * — non-suspending, so it takes effect on the frame the finger lands.
 *
 * ### The viewport lives here
 *
 * The fling re-anchors at the viewport centre every frame and the zoom pins a
 * screen point, so both need the box the globe is drawn in. The surface writes
 * [viewport] whenever it changes rather than threading it through every call,
 * which is what let the pointer handler stop restarting on every resize.
 *
 * ### The floor is injected
 *
 * Interactive zoom clamps to `[zoomFloor(viewport), MAX_ALTITUDE]` rather than
 * to the camera's own [MIN_ALTITUDE] — a deliberate divergence from `camera.rs`,
 * whose scroll handler clamps to plain `MIN_ALTITUDE` (637 m above the surface).
 * The desktop app has imagery to that depth; the keyless fallback here does not,
 * and a camera allowed below the sharpest tile the provider has just enlarges
 * pixels. The floor is a function of the viewport because "the altitude at which
 * the finest level is one texel per pixel" depends on how many pixels there are.
 *
 * ### Zoom is animated in log space
 *
 * Altitude runs from a ten-thousandth of a radius to nine radii — five orders of
 * magnitude — so a linear interpolation between two altitudes spends almost all
 * of its time at the far end and then arrives with a lurch. Every zoom here
 * animates `ln(altitude)`, which makes a step from 4.0 to 2.0 feel exactly like
 * a step from 0.02 to 0.01, because it is the same gesture.
 *
 * ### Longitude and bearing take the short way round
 *
 * Both are angles that wrap, and both are interpolated by unwrapping the target
 * to within half a turn of the start first. Without it, re-fitting a route from
 * 179° east to 179° west travels the long way across the whole planet — which
 * is not a bug that shows up in a test, only in a transition that goes the
 * wrong way in front of somebody.
 */
@Stable
internal class GlobeCameraState(
    initial: GlobeCamera,
    private val scope: CoroutineScope,
    private val zoomFloor: (GlobeViewport) -> Float = { MIN_ALTITUDE },
    private val onChange: (GlobeCamera) -> Unit,
) {

    var camera: GlobeCamera by mutableStateOf(initial)
        private set

    /** The box the globe is drawn in. Written by the surface; read by every screen-space move. */
    var viewport: GlobeViewport by mutableStateOf(GlobeViewport(1f, 1f))

    /** Drives every spring; a new one cancels whatever was running. */
    private val progress = Animatable(1f)

    private var animationStart: GlobeCamera = initial
    private var animationTarget: GlobeCamera = initial

    /** The one motion — spring or fling — in flight, if any. */
    private var motion: Job? = null

    /** True while a spring or a fling is carrying the camera, so a touch can interrupt it. */
    val isAnimating: Boolean get() = motion?.isActive == true

    fun set(next: GlobeCamera) {
        camera = next
        onChange(next)
    }

    /**
     * Stops whatever is moving the camera and leaves it where it is.
     *
     * Not suspending, on purpose: it is called from a pointer handler on the
     * frame a finger lands, and a `launch { stop() }` would have let the fling
     * write one more frame under that finger.
     */
    fun cancelMotion() {
        motion?.cancel()
        motion = null
    }

    /**
     * Springs the camera to [target], cancelling any motion already under way.
     *
     * One `Animatable` over a progress fraction rather than five over the
     * individual fields: the five have to arrive together, and five independent
     * springs — even with the same spec — do not, because each one's duration
     * follows from its own distance. A camera whose zoom settles before its
     * centre does reads as two separate moves.
     */
    fun animateTo(target: GlobeCamera, spec: AnimationSpec<Float>) {
        cancelMotion()
        animationStart = camera
        animationTarget = target.copy(
            centerLon = unwrapNear(camera.centerLon, target.centerLon, 360f),
            bearing = unwrapNear(camera.bearing, target.bearing, (2 * PI).toFloat()),
        )
        motion = scope.launch {
            progress.snapTo(0f)
            progress.animateTo(1f, spec) {
                set(interpolate(animationStart, animationTarget, value))
            }
        }
    }

    /**
     * Momentum after a pan, cancelling any motion already under way.
     *
     * The anchor is re-taken at the viewport centre every frame rather than kept
     * from the release. A fixed anchor works while it is on screen and then lies:
     * once it passes behind the limb, solving for it sends the camera somewhere
     * it was never asked to go.
     */
    fun fling(velocity: Velocity, decay: DecayAnimationSpec<Offset>) {
        cancelMotion()
        if (velocity.x == 0f && velocity.y == 0f) return
        motion = scope.launch {
            val travel = Animatable(Offset.Zero, Offset.VectorConverter)
            var last = Offset.Zero
            travel.animateDecay(
                initialVelocity = Offset(velocity.x, velocity.y),
                animationSpec = decay,
            ) {
                val delta = value - last
                last = value
                if (delta == Offset.Zero) return@animateDecay
                val box = viewport
                val center = ScreenPoint(box.centerX, box.centerY)
                val anchor = camera.screenToWorldClamped(center, box)
                set(camera.panTo(anchor, ScreenPoint(center.x + delta.x, center.y + delta.y), box))
            }
        }
    }

    /**
     * Moves the camera so [anchor] sits under [target], the drag primitive.
     *
     * The target is held inside the disc and the solve is verified — see
     * [pinned] — so a finger that drags a grabbed point out past the limb stops
     * it at the limb rather than asking the Newton solve for a camera that does
     * not exist. When no camera holds, the camera stays where it is for that
     * event; a drag that has left the disc has nothing to say until it returns.
     */
    fun panAnchor(anchor: Vec3, target: ScreenPoint) {
        val box = viewport
        val held = camera.heldOnDisc(target, box)
        val solved = camera.panTo(anchor, held, box)
        if (solved.holds(anchor, held, box)) set(solved)
    }

    /**
     * Multiplies the altitude by [factor], keeping [focus] under the same pixel.
     *
     * A factor below one zooms in. Pinning the focus is what makes a pinch feel
     * like the globe rather than like a slider: the point between the fingers
     * stays between the fingers. See [zoomedBy] for what happens when the focus
     * is on the sky, or the zoom carries the pinned point off the disc.
     */
    fun zoomBy(factor: Float, focus: ScreenPoint?) {
        set(zoomedBy(factor, focus))
    }

    /**
     * The camera this one would be after [zoomBy] — the double-tap springs to it.
     *
     * ### On the disc: pinned, held inside the rim, and verified
     *
     * The world point under the focus is solved back under it by `panTo`, with
     * the target pixel held to [DISC_HOLD] of the zoomed disc's radius — a
     * zoom-out shrinks the disc and can leave the focus off it, and the
     * projection's derivative is zero at the limb, where a Newton step is
     * unbounded. The solved camera is accepted only if it really shows the
     * pinned point there, on the near side, within [PIN_TOLERANCE_PX]; failing
     * that the solve is retried from a camera centred on the point, whose
     * Jacobian is well conditioned, and failing that too the zoom is about the
     * centre for this event. Near the edge the correct answer swings the centre
     * several degrees for a 5 % pinch, because the last 2 % of the disc's radius
     * holds 10° of arc; that is what keeping a point under the fingers costs
     * there, and it is what every globe does.
     *
     * ### On the sky: the nearest limb point stays at the limb
     *
     * A focus off the disc used to zoom about the centre, and at the zoomed-out
     * opening altitude the disc is 17 % of the viewport height, so that is
     * where the first pinch landed. The intent here is the same as on the disc
     * — the surface point nearest the fingers stays put — but the naive form,
     * `panTo(screenToWorldClamped(focus), focus)`, was measured at 1080×2340 to
     * throw the camera 22.6° of latitude on one 5 % zoom-in from altitude 2,
     * and 61.3° from altitude 9: a zoom-in shrinks the horizon, so the old limb
     * point is now behind it, and no camera shows a point that is behind the
     * horizon at a pixel that is off the disc. `GlobeCameraStateTest` keeps
     * that measurement as a test of the raw solve.
     *
     * So the sky case is not solved but computed: the centre turns toward the
     * limb point by exactly the amount the horizon shrank (away from it, by the
     * amount it grew, on a zoom-out), which keeps that point on the limb —
     * 0.70° per 5 % step at altitude 2, 0.26° at altitude 9, bounded by
     * construction. Once the growing disc reaches the fingers the focus is on
     * it and the pinned form above takes over, with no jump between the two:
     * the pinned point *is* the limb point at that moment.
     *
     * The divergence from `camera.rs` is deliberate: its scroll zoom is about
     * the cursor and has no sky case because a desktop cursor is rarely there.
     */
    fun zoomedBy(factor: Float, focus: ScreenPoint?): GlobeCamera {
        val box = viewport
        val zoomed = camera.copy(altitude = clampAltitude(camera.altitude * factor))
        if (focus == null || zoomed.altitude == camera.altitude) return zoomed
        val under = camera.screenToWorld(focus, box)
        if (under != null) return pinned(zoomed, under, zoomed.heldOnDisc(focus, box), box) ?: zoomed
        // Normalised because the clamped ray can miss a tilted disc and return
        // its closest approach, which is just off the sphere.
        val rim = camera.screenToWorldClamped(focus, box).normalize()
        return zoomed.turnedToward(rim, camera.horizonAngle() - zoomed.horizonAngle())
    }

    /**
     * This camera with its centre carried [radians] along the great circle from
     * the centre toward [point] — away from it when negative. Analytic, so the
     * move is bounded by [radians] whatever the geometry, which is what the
     * limb needs and a solve cannot promise.
     */
    private fun GlobeCamera.turnedToward(point: Vec3, radians: Float): GlobeCamera {
        val n = latLonToWorld(centerLat, centerLon)
        val along = point - n * (n dot point)
        if (along.length() < 1e-6f) return this
        val t = along.normalize()
        val turned = n * cos(radians) + t * sin(radians)
        return copy(
            centerLat = (asin(turned.y.coerceIn(-1f, 1f)) * RAD_TO_DEG).coerceIn(-85f, 85f),
            centerLon = atan2(turned.x, turned.z) * RAD_TO_DEG,
        )
    }

    /**
     * [zoomed] panned so that [pin] sits at [target], or null when no camera
     * that this solve can find does — see [zoomedBy] for why that happens.
     */
    private fun pinned(zoomed: GlobeCamera, pin: Vec3, target: ScreenPoint, box: GlobeViewport): GlobeCamera? {
        // From where the centre-anchored zoom left things: a small correction
        // whenever the pin is well inside the disc, which is the common case.
        val direct = zoomed.panTo(pin, target, box)
        if (direct.holds(pin, target, box)) return direct
        // From the pin's own nadir, where the projection's slope is greatest.
        // Two passes of Newton, because the start is deliberately far off.
        val fromNadir = zoomed.copy(
            centerLat = asin(pin.y.coerceIn(-1f, 1f)) * RAD_TO_DEG,
            centerLon = atan2(pin.x, pin.z) * RAD_TO_DEG,
        )
        val retried = fromNadir.panTo(pin, target, box).panTo(pin, target, box)
        return retried.takeIf { it.holds(pin, target, box) }
    }

    /** Whether this camera really shows [pin] at [target]: on the near side, and within tolerance. */
    private fun GlobeCamera.holds(pin: Vec3, target: ScreenPoint, box: GlobeViewport): Boolean {
        // A point past the horizon still projects to a pixel — an occluded one.
        if (facingValue(pin) < 1f / distance) return false
        val at = worldToScreen(pin, box) ?: return false
        return (at - target).length() <= PIN_TOLERANCE_PX
    }

    /**
     * [point] pulled to [DISC_HOLD] of this camera's disc radius when it lies
     * outside that, unchanged when inside — the same image-plane clamp
     * `screenToWorldClamped` applies, for the pixel rather than the ray.
     */
    private fun GlobeCamera.heldOnDisc(point: ScreenPoint, box: GlobeViewport): ScreenPoint {
        val f = focalPixels(box.height)
        val ix = (point.x - box.centerX) / f
        val iy = (point.y - box.centerY) / f
        // Limb boundary at tilt 0: ix² + iy² ≤ 1 / (r² − 1).
        val limbR2 = 1f / max(1e-6f, distance * distance - 1f)
        val hold2 = limbR2 * DISC_HOLD * DISC_HOLD
        val r2 = ix * ix + iy * iy
        if (r2 <= hold2) return point
        val s = sqrt(hold2 / r2)
        return ScreenPoint(box.centerX + ix * s * f, box.centerY + iy * s * f)
    }

    fun rotateBy(radians: Float) {
        set(camera.copy(bearing = camera.bearing + radians))
    }

    /**
     * Turns the globe under two fingers that twisted by [clockwiseRadians] on
     * screen, so the line between them keeps its content.
     *
     * The bearing is the compass direction of screen-up — positive puts north
     * *left* of up, which is the picture turned anticlockwise — so a clockwise
     * twist of the fingers is a **decrease** in bearing. The old handler added
     * the twist and turned the globe against the hand.
     */
    fun twistBy(clockwiseRadians: Float) = rotateBy(-clockwiseRadians)

    fun tiltBy(radians: Float) {
        set(camera.copy(tilt = (camera.tilt + radians).coerceIn(0f, MAX_TILT)))
    }

    /**
     * The camera this one would be if it were zoomed [steps] notches — about the
     * centre, or about [focus] when given, which is what a double-tap wants.
     */
    fun steppedZoom(steps: Int, focus: ScreenPoint? = null): GlobeCamera =
        zoomedBy(exp(ZOOM_STEP_LN * steps), focus)

    /** North up and top down, keeping position and zoom. */
    fun uprightCamera(): GlobeCamera = camera.copy(bearing = 0f, tilt = 0f)

    /** Into `[zoomFloor(viewport), MAX_ALTITUDE]`; the floor itself is held inside the camera's own range. */
    private fun clampAltitude(altitude: Float): Float {
        val floor = zoomFloor(viewport).coerceIn(MIN_ALTITUDE, MAX_ALTITUDE)
        return altitude.coerceIn(floor, MAX_ALTITUDE)
    }

    companion object {
        /**
         * One notch of the ± controls and of a double-tap, in log-altitude.
         *
         * `ln(2)` — a notch halves or doubles the height above the surface,
         * which is the same step a map's zoom level means and the step a user
         * already has an expectation about.
         */
        val ZOOM_STEP_LN: Float = ln(2f)

        /**
         * How far inside the disc a pinned pixel is held, as a fraction of the
         * disc radius. At 0.98 the pinned point sits about 10° of arc inside the
         * horizon at altitude 2, where the projection still moves ~3 px per
         * degree of camera centre; at the limb itself it moves none, and a
         * Newton step there is unbounded. See [zoomedBy].
         */
        private const val DISC_HOLD = 0.98f

        /** How close the pinned point must land for the solve to be believed. A pinch is not a sub-pixel gesture. */
        private const val PIN_TOLERANCE_PX = 2f

        private const val RAD_TO_DEG: Float = (180.0 / PI).toFloat()

        private fun interpolate(from: GlobeCamera, to: GlobeCamera, t: Float) = GlobeCamera(
            centerLat = lerp(from.centerLat, to.centerLat, t),
            centerLon = lerp(from.centerLon, to.centerLon, t),
            // Log space: see the class note.
            altitude = exp(lerp(ln(from.altitude.coerceAtLeast(MIN_ALTITUDE)), ln(to.altitude.coerceAtLeast(MIN_ALTITUDE)), t)),
            bearing = lerp(from.bearing, to.bearing, t),
            tilt = lerp(from.tilt, to.tilt, t),
            fovY = from.fovY,
        )

        private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

        /** Brings [target] within half a [turn] of [from], so the move is the short one. */
        private fun unwrapNear(from: Float, target: Float, turn: Float): Float {
            var delta = (target - from) % turn
            if (delta > turn / 2f) delta -= turn
            if (delta < -turn / 2f) delta += turn
            return from + delta
        }
    }
}
