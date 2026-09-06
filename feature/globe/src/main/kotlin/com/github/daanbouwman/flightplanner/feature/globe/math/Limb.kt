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
     * Projects the limb into [out] as `x, y` pairs and returns how many points
     * were written.
     *
     * Points behind the camera are skipped, which happens at high tilt when
     * zoomed in — the far side of the silhouette is then genuinely off the back
     * of the view, and the run of points is correctly open rather than closed.
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

        var count = 0
        for (i in 0 until SAMPLES) {
            val t = i * (2.0 * PI / SAMPLES)
            val w = ringCenter + e1 * (rho * cos(t).toFloat()) + e2 * (rho * sin(t).toFloat())
            val p = camera.project(rotateFast(basis, w), viewport) ?: continue
            out[count * 2] = p.x
            out[count * 2 + 1] = p.y
            count++
        }
        return count
    }
}
