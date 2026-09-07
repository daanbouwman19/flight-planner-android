package com.github.daanbouwman.flightplanner.feature.globe.math

import kotlin.math.max
import kotlin.math.tan

/**
 * The two matrices Filament is handed, derived from a [GlobeCamera].
 *
 * ### Why this is a separate file with a test of its own
 *
 * The globe runs **two projections at once**. Imagery, the arc and the markers
 * are transformed by the GPU from these matrices; tile selection, the DEP/DEST
 * labels, hit-testing and every gesture run on [GlobeCamera.project] on the CPU.
 * They are two independent pieces of arithmetic that have to agree, and when
 * they do not the failure is not a crash — it is a label sitting a few pixels
 * off the dot it names, or a drag that slides because the point under the finger
 * was computed against a different frustum than the one drawn.
 *
 * So the derivation lives here, in one place, next to the note explaining it,
 * and `CameraMatrixConsistencyTest` projects the same world points both ways and
 * demands sub-pixel agreement. That test is the reason it is safe to keep the
 * CPU path at all.
 *
 * ### The handedness change, stated once
 *
 * [GlobeCamera.rotate] returns `(right, up, look)` with **look pointing into the
 * scene**, so its z is a positive depth. Filament's camera space is OpenGL's:
 * the camera looks down **−z**. Everything in this file is that one sign flip
 * and its consequences — the model matrix's third column is `−look`, and the
 * projection divides by `−z`.
 *
 * Both matrices are **column-major**, which is what Filament's `setCustomProjection`
 * and `setModelMatrix` expect and also what `Matrix.frustumM` and friends in the
 * platform produce. Row-major here would compile, run, and draw a sphere that is
 * subtly and inexplicably wrong.
 */
internal object CameraMatrices {

    /**
     * Near plane, as a fraction of the camera's height above the surface.
     *
     * The nearest point of the sphere is exactly [GlobeCamera.altitude] away
     * along the nadir, and no visible point is nearer at any tilt, so half of
     * that clears the geometry with room to spare while keeping the near plane
     * as far out as it can go — which is what buys depth precision. A fixed
     * small near plane would crush the depth buffer at high altitude and let the
     * arc z-fight with the sphere it is drawn just above.
     */
    private const val NEAR_FRACTION = 0.5

    /**
     * Guard against a degenerate near plane, and **nothing else**.
     *
     * It was `1e-4`, which is the same order as [MIN_ALTITUDE] itself
     * (`1.00017e-4`, about 638 m). At the zoom floor the guard therefore beat
     * the `0.5 × altitude` rule and put the near plane **1.7e-8 radii — 0.11 mm
     * — in front of the surface**, while a float32 position on the unit sphere
     * quantises at one ulp of 1.0, or `1.19e-7` radii ≈ 0.76 m. Every vertex was
     * a coin flip against the plane, so the imagery came apart into scattered
     * slivers with the backdrop sphere showing through the holes. It only
     * appeared at the very bottom of the zoom range, which is why it survived a
     * keyless build: NASA GIBS floors the camera at 0.10 radii, six hundred
     * kilometres up, where the guard never binds.
     *
     * Nine orders of magnitude below the working minimum of
     * `0.5 × MIN_ALTITUDE ≈ 5e-5`, so it cannot bind again; it exists only so a
     * future altitude of exactly zero could not produce a singular matrix.
     */
    private const val MIN_NEAR = 1e-9

    /**
     * How far past the camera's own distance the far plane sits.
     *
     * The furthest point of the unit sphere is `distance + 1` away; the
     * atmosphere shell adds a few percent more. Two is that with slack, and
     * being generous costs nothing here because the near plane is doing the
     * precision work.
     */
    private const val FAR_MARGIN = 2.0

    fun nearPlane(camera: GlobeCamera): Double =
        max(MIN_NEAR, camera.altitude.toDouble() * NEAR_FRACTION)

    fun farPlane(camera: GlobeCamera): Double = camera.distance.toDouble() + FAR_MARGIN

    /**
     * The perspective projection, column-major, matching [GlobeCamera.project]
     * exactly.
     *
     * Derivation, so the agreement is a consequence rather than a coincidence.
     * [GlobeCamera.project] computes `sx = cx + f·x/depth` with
     * `f = (height/2) / tan(fovY/2)`. In normalised device coordinates that is
     * `ndcX = (sx − cx)/(width/2) = x / (depth · tan(fovY/2) · aspect)`, and
     * likewise `ndcY = y / (depth · tan(fovY/2))`. Those are precisely the two
     * scale terms of a textbook GL perspective matrix, so the two agree by
     * construction and the test only has to catch a typo.
     */
    fun projection(
        camera: GlobeCamera,
        viewport: GlobeViewport,
        near: Double = nearPlane(camera),
        far: Double = farPlane(camera),
    ): DoubleArray {
        val t = tan(camera.fovY.toDouble() * 0.5)
        val aspect = viewport.width.toDouble() / viewport.height.toDouble()
        val m = DoubleArray(16)
        m[0] = 1.0 / (t * aspect)
        m[5] = 1.0 / t
        m[10] = -(far + near) / (far - near)
        m[11] = -1.0
        m[14] = -(2.0 * far * near) / (far - near)
        return m
    }

    /**
     * The camera's model matrix — camera space to world space — column-major.
     *
     * Its columns are the basis Filament expects: screen-right, screen-up,
     * *backwards* (the negated look direction, because GL's camera +z points out
     * of the screen), and the camera's world position.
     */
    fun model(camera: GlobeCamera): FloatArray {
        val basis = camera.computeBasis()
        val back = -basis.look
        return floatArrayOf(
            basis.right.x, basis.right.y, basis.right.z, 0f,
            basis.up.x, basis.up.y, basis.up.z, 0f,
            back.x, back.y, back.z, 0f,
            basis.position.x, basis.position.y, basis.position.z, 1f,
        )
    }

    /**
     * Projects [world] the way the GPU will, in pixels — the mirror of
     * [GlobeCamera.worldToScreen] and the thing the consistency test compares
     * against.
     *
     * Not used at runtime. It exists so the test can walk the *matrices* rather
     * than re-deriving a projection of its own, which would only prove that two
     * hand-written formulas agree while the matrices handed to Filament went
     * unchecked.
     */
    fun projectThroughMatrices(
        camera: GlobeCamera,
        viewport: GlobeViewport,
        world: Vec3,
    ): ScreenPoint? {
        val basis = camera.computeBasis()
        val d = world - basis.position
        // The inverse of `model` applied to a world point: the model matrix is a
        // rigid transform, so its rotation inverts by transposition.
        val camX = (d dot basis.right).toDouble()
        val camY = (d dot basis.up).toDouble()
        val camZ = (d dot -basis.look).toDouble()

        val p = projection(camera, viewport)
        val clipX = p[0] * camX
        val clipY = p[5] * camY
        val clipW = -camZ
        if (clipW <= 1e-6) return null

        val ndcX = clipX / clipW
        val ndcY = clipY / clipW
        return ScreenPoint(
            (viewport.centerX + ndcX * viewport.width * 0.5).toFloat(),
            (viewport.centerY - ndcY * viewport.height * 0.5).toFloat(),
        )
    }
}
