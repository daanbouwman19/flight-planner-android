package com.github.daanbouwman.flightplanner.feature.globe.math

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The globe's silhouette, projected — the edge the rim and the atmosphere are
 * drawn along.
 *
 * ### Why the true limb rather than a circle
 *
 * At zero tilt the globe's outline on screen really is a circle around the
 * viewport centre, and drawing one would be a line of code. The moment the view
 * tilts it stops being one: the silhouette becomes an ellipse whose centre is
 * not the viewport's, and at high tilt part of it leaves the screen entirely.
 * A circle would then be a ring floating away from the planet it is supposed to
 * be the edge of.
 *
 * So the limb is computed as what it is — the circle of sphere points at grazing
 * angle from the camera, which lies at height `1/r` from the centre on the nadir
 * axis with radius `sqrt(1 − 1/r²)` — and projected point by point. That is
 * exact under any bearing and tilt, and it is the same derivation the Rust
 * original uses.
 *
 * ### Why it is drawn in Compose rather than by the renderer
 *
 * The rim's colour is the theme's `outline` and the glow's is its `primary`.
 * Keeping the whole thing on the Compose side means those values are read where
 * every other themed colour in the app is read, instead of becoming two more
 * material uniforms to keep in step. It also costs nothing: 64 points projected
 * once per frame is less work than the arc already does.
 */
internal object Limb {

    /** Samples around the limb. Sixty-four is smooth at any size a phone has. */
    const val SAMPLES: Int = 64

    /**
     * Projects the limb into [out] and returns how many of the [SAMPLES] points
     * are in front of the camera.
     *
     * [out] must hold `SAMPLES * 2` floats and is written **in full**, one
     * `x, y` pair per sample in angular order. A sample behind the camera plane
     * — which happens at high tilt zoomed in, when the far side of the
     * silhouette is genuinely off the back of the view — is written as
     * `(NaN, NaN)` rather than skipped.
     *
     * The NaN gap is the point: the culled samples form one contiguous arc of
     * the ring, and a consumer that only saw the survivors *compacted* could not
     * tell the run was broken and drew a straight segment straight across the
     * gap — the chord over the planet the rim and its glow used to show under
     * tilt. A consumer walks the array, lifts its pen on a NaN, and closes the
     * path only when the return value is [SAMPLES] (nothing was culled).
     */
    fun projectInto(
        camera: GlobeCamera,
        basis: CameraBasis,
        viewport: GlobeViewport,
        out: FloatArray,
    ): Int {
        val r = camera.distance
        val axis = basis.facingUnit
        val rho = sqrt(max(0f, 1f - 1f / (r * r)))
        val ringCenter = axis * (1f / r)

        // Any vector not parallel to the axis gives a starting tangent; picking
        // by which component is small is what keeps the cross product stable.
        val helper = if (abs(axis.x) < 0.9f) Vec3(1f, 0f, 0f) else Vec3(0f, 1f, 0f)
        val e1 = (axis cross helper).normalize()
        val e2 = axis cross e1

        var visible = 0
        for (i in 0 until SAMPLES) {
            val t = i * (2.0 * PI / SAMPLES)
            val w = ringCenter + e1 * (rho * cos(t).toFloat()) + e2 * (rho * sin(t).toFloat())
            val p = camera.project(rotateFast(basis, w), viewport)
            if (p == null) {
                out[i * 2] = Float.NaN
                out[i * 2 + 1] = Float.NaN
            } else {
                out[i * 2] = p.x
                out[i * 2 + 1] = p.y
                visible++
            }
        }
        return visible
    }

    /**
     * Whether the top of the projected disc reaches up past [statusStripPx].
     *
     * [points] and [visible] come straight from [projectInto]. A caller uses
     * this to decide whether the *system* should draw its status-bar glyphs
     * light: the imagery genuinely reaches under the clock only while the sphere
     * does, and past a long-range camera the disc retreats and what is up there
     * is the backdrop's space colour instead — which in a light theme is the
     * page colour, where light glyphs are wrong.
     */
    fun discReachesTop(points: FloatArray, visible: Int, statusStripPx: Float): Boolean {
        if (visible < 3 || statusStripPx <= 0f) return false
        var topY = Float.MAX_VALUE
        var i = 1
        while (i < points.size) {
            val y = points[i]
            if (!y.isNaN() && y < topY) topY = y
            i += 2
        }
        return topY <= statusStripPx
    }
}
