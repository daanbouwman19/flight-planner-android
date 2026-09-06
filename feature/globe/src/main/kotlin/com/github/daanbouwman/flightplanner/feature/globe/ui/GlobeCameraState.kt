package com.github.daanbouwman.flightplanner.feature.globe.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.github.daanbouwman.flightplanner.feature.globe.math.GlobeCamera
import com.github.daanbouwman.flightplanner.feature.globe.math.GlobeViewport
import com.github.daanbouwman.flightplanner.feature.globe.math.MAX_ALTITUDE
import com.github.daanbouwman.flightplanner.feature.globe.math.MAX_TILT
import com.github.daanbouwman.flightplanner.feature.globe.math.MIN_ALTITUDE
import com.github.daanbouwman.flightplanner.feature.globe.math.ScreenPoint
import com.github.daanbouwman.flightplanner.feature.globe.math.Vec3
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.ln

/**
 * The camera, as something a screen can read and a gesture can move.
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
internal class GlobeCameraState(initial: GlobeCamera, private val onChange: (GlobeCamera) -> Unit) {

    var camera: GlobeCamera by mutableStateOf(initial)
        private set

    /** Drives every camera animation; a new one cancels whatever was running. */
    private val progress = Animatable(1f)

    private var animationStart: GlobeCamera = initial
    private var animationTarget: GlobeCamera = initial

    /** True while a spring is carrying the camera, so a touch can interrupt it. */
    val isAnimating: Boolean get() = progress.isRunning

    fun set(next: GlobeCamera) {
        camera = next
        onChange(next)
    }

    /** Cancels any running animation and leaves the camera where it is. */
    suspend fun stop() {
        if (progress.isRunning) progress.stop()
    }

    /**
     * Springs the camera to [target].
     *
     * One `Animatable` over a progress fraction rather than five over the
     * individual fields: the five have to arrive together, and five independent
     * springs — even with the same spec — do not, because each one's duration
     * follows from its own distance. A camera whose zoom settles before its
     * centre does reads as two separate moves.
     */
    suspend fun animateTo(target: GlobeCamera, spec: AnimationSpec<Float>) {
        animationStart = camera
        animationTarget = target.copy(
            centerLon = unwrapNear(camera.centerLon, target.centerLon, 360f),
            bearing = unwrapNear(camera.bearing, target.bearing, (2 * PI).toFloat()),
        )
        progress.snapTo(0f)
        progress.animateTo(1f, spec) {
            camera = interpolate(animationStart, animationTarget, value)
            onChange(camera)
        }
    }

    /** Moves the camera so [anchor] sits under [target], the drag primitive. */
    fun panAnchor(anchor: Vec3, target: ScreenPoint, viewport: GlobeViewport) {
        set(camera.panTo(anchor, target, viewport))
    }

    /**
     * Multiplies the altitude by [factor], keeping [focus] under the same pixel.
     *
     * A factor below one zooms in. Pinning the focus is what makes a pinch feel
     * like the globe rather than like a slider: the point between the fingers
     * stays between the fingers.
     */
    fun zoomBy(factor: Float, focus: ScreenPoint?, viewport: GlobeViewport) {
        val pinned = focus?.let { f -> camera.screenToWorld(f, viewport)?.let { f to it } }
        var next = camera.copy(
            altitude = (camera.altitude * factor).coerceIn(MIN_ALTITUDE, MAX_ALTITUDE),
        )
        if (pinned != null) next = next.panTo(pinned.second, pinned.first, viewport)
        set(next)
    }

    fun rotateBy(radians: Float) {
        set(camera.copy(bearing = camera.bearing + radians))
    }

    fun tiltBy(radians: Float) {
        set(camera.copy(tilt = (camera.tilt + radians).coerceIn(0f, MAX_TILT)))
    }

    /** The camera this one would be if it were zoomed [steps] notches. */
    fun steppedZoom(steps: Int): GlobeCamera = camera.copy(
        altitude = (camera.altitude * exp(ZOOM_STEP_LN * steps)).coerceIn(MIN_ALTITUDE, MAX_ALTITUDE),
    )

    /** North up and top down, keeping position and zoom. */
    fun uprightCamera(): GlobeCamera = camera.copy(bearing = 0f, tilt = 0f)

    companion object {
        /**
         * One notch of the ± controls and of a double-tap, in log-altitude.
         *
         * `ln(2)` — a notch halves or doubles the height above the surface,
         * which is the same step a map's zoom level means and the step a user
         * already has an expectation about.
         */
        val ZOOM_STEP_LN: Float = ln(2f)

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
