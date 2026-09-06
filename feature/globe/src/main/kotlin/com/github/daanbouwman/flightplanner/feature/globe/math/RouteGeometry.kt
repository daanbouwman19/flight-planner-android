package com.github.daanbouwman.flightplanner.feature.globe.math

import kotlin.math.acos
import kotlin.math.sin

/**
 * The great circle as points on the unit sphere, and the ribbon drawn along it.
 *
 * ### The slerp is not re-implemented here
 *
 * `:core:routing`'s [com.github.daanbouwman.flightplanner.routing.RouteArc]
 * already samples a great circle, in degrees, with the ±180° seam handled and a
 * test suite asserting the samples land on real distances rather than on a
 * decorative curve. The globe wants the same curve in Cartesian form, so this
 * converts rather than re-derives — a second spherical interpolation in the same
 * app is a second place for the seam to be got wrong, and the flat `RouteMap`
 * and the globe would then disagree about where a route goes.
 *
 * The Rust original does slerp here because its desktop map had no equivalent.
 * That is the divergence, and it is in the direction of having less arithmetic.
 */
internal object RouteGeometry {


    /**
     * How far above the surface the arc floats, as a fraction of the radius.
     *
     * Small enough to read as lying on the sphere and large enough to clear the
     * depth buffer's resolution at every altitude the camera reaches. Without it
     * the arc z-fights the backdrop it is drawn against and stipples.
     */
    const val ARC_RADIUS: Float = 1.0015f

    /** Where the DEP and DEST markers sit, just above the arc so they cap it. */
    const val MARKER_RADIUS: Float = 1.002f

    /** Converts a sampled arc in degrees into unit-sphere points. */
    fun toWorldPoints(lats: DoubleArray, lons: DoubleArray): Array<Vec3> =
        Array(lats.size) { latLonToWorld(lats[it].toFloat(), lons[it].toFloat()) }

    /**
     * The angular length of the leg, radians — what [GlobeFit] frames and what
     * decides whether the two ends can share one label plate.
     */
    fun separationRadians(a: Vec3, b: Vec3): Float = acos((a dot b).coerceIn(-1f, 1f))

    /**
     * Spherical interpolation between two unit vectors.
     *
     * Only used for the label-collision case in
     * [com.github.daanbouwman.flightplanner.feature.globe.ui.GlobeLabels], which
     * needs the true midpoint of the leg to anchor a merged plate to. Returns
     * [a] when the two coincide, which is the degenerate case rather than an
     * error.
     */
    fun slerp(a: Vec3, b: Vec3, f: Float): Vec3 {
        val theta = separationRadians(a, b)
        if (theta < 1e-4f) return a
        val sinTheta = sin(theta)
        return a * (sin((1f - f) * theta) / sinTheta) + b * (sin(f * theta) / sinTheta)
    }

    /**
     * Builds the arc as a triangle strip whose width is constant **on screen**.
     *
     * ### Why a ribbon and not a line
     *
     * `GL_LINE_STRIP` with a width above 1 is unreliable on mobile GPUs — the
     * specification permits a driver to support exactly one pixel, and several
     * do. A three-pixel route drawn that way is three pixels on the machine it
     * was developed on and a hairline on a phone, with nothing in the API to
     * report it. So the arc is real geometry: two vertices per sample, offset
     * either side of the curve.
     *
     * ### Why the offset is computed here rather than in a shader
     *
     * The offset direction has to be perpendicular to the arc *as projected*,
     * and the width has to be constant in pixels rather than in world units, so
     * both depend on the camera. Doing it on the CPU costs one pass over 256
     * points per frame — a few microseconds — and keeps the material a plain
     * unlit vertex-coloured one that the arc, the markers and the atmosphere can
     * all share. A vertex shader could do it, at the cost of a second material
     * and of the camera basis becoming shader uniforms that then have to be kept
     * in step with the CPU projection.
     *
     * Points behind the horizon are dropped rather than offset, which splits the
     * strip into runs; [ribbonInto] writes a degenerate-triangle bridge between
     * runs so the whole arc is still one draw call.
     *
     * @return the number of vertices written into [out]
     */
    fun ribbonInto(
        arcs: List<Array<Vec3>>,
        camera: GlobeCamera,
        basis: CameraBasis,
        viewport: GlobeViewport,
        halfWidthPx: Float,
        out: RibbonBuffer,
    ): Int {
        out.reset()
        val threshold = camera.cullThreshold()
        val focal = camera.focalPixels(viewport.height)

        for (points in arcs) {
            if (points.size < 2) continue
            appendArc(points, camera, basis, viewport, halfWidthPx, threshold, focal, out)
        }
        return out.vertexCount
    }

    /**
     * Appends one arc to the strip, starting a fresh run.
     *
     * Every run — a new arc, or the far side of the horizon within one arc —
     * is joined to what came before with degenerate triangles, so the whole
     * network stays a single primitive however many legs are in it. A leg per
     * draw call would put a logbook with two hundred flights at two hundred
     * draw calls a frame.
     */
    @Suppress("LongParameterList")
    private fun appendArc(
        points: Array<Vec3>,
        camera: GlobeCamera,
        basis: CameraBasis,
        viewport: GlobeViewport,
        halfWidthPx: Float,
        threshold: Float,
        focal: Float,
        out: RibbonBuffer,
    ) {
        var previousVisible = false
        for (i in points.indices) {
            val p = points[i]
            if (facingValueFast(basis, p) <= threshold) {
                previousVisible = false
                continue
            }
            val cam = rotateFast(basis, p)
            if (cam.z <= 1e-6f) {
                previousVisible = false
                continue
            }

            // The along-arc tangent, from the neighbouring samples, in world space.
            val before = points[if (i == 0) 0 else i - 1]
            val after = points[if (i == points.lastIndex) points.lastIndex else i + 1]
            val tangent = (after - before).normalize()

            // Perpendicular to both the tangent and the surface normal is the
            // direction that widens the ribbon while keeping it on the sphere.
            val side = (tangent cross p).normalize()

            // World units per pixel at this point's depth, so the ribbon is the
            // same weight near the limb as at the nadir.
            val worldPerPixel = cam.z / focal
            val offset = side * (halfWidthPx * worldPerPixel)

            val onArc = p * ARC_RADIUS
            if (!previousVisible && out.vertexCount > 0) {
                // Bridge the gap with degenerate triangles: repeat the last
                // vertex and the first of the new run, so the strip stays one
                // primitive and the connecting triangles have zero area.
                out.repeatLast()
                out.push(onArc - offset)
            }
            out.push(onArc - offset)
            out.push(onArc + offset)
            previousVisible = true
        }
    }
}

/**
 * The triangle strip [RouteGeometry.ribbonInto] writes, as one flat array.
 *
 * **Fixed, and it refuses to overrun rather than growing.** The array is handed
 * straight to a Filament vertex buffer of exactly [capacityVertices], so a strip
 * that grew past it would throw on upload - which is what happened the first time
 * the visited network fed a whole logbook through a budget sized for one leg.
 * Refusing instead means the last few arcs of an unusually large network are
 * simply not drawn, on a frame that was already asking for more than the scene
 * had agreed to hold.
 */
internal class RibbonBuffer(val capacityVertices: Int) {
    val positions: FloatArray = FloatArray(capacityVertices * 3)
    var vertexCount: Int = 0
        private set

    fun reset() {
        vertexCount = 0
    }

    fun push(v: Vec3) {
        if (vertexCount >= capacityVertices) return
        val i = vertexCount * 3
        positions[i] = v.x
        positions[i + 1] = v.y
        positions[i + 2] = v.z
        vertexCount++
    }

    /** Duplicates the last vertex, for the degenerate bridge between runs. */
    fun repeatLast() {
        if (vertexCount == 0 || vertexCount >= capacityVertices) return
        val last = (vertexCount - 1) * 3
        val i = vertexCount * 3
        positions[i] = positions[last]
        positions[i + 1] = positions[last + 1]
        positions[i + 2] = positions[last + 2]
        vertexCount++
    }
}
