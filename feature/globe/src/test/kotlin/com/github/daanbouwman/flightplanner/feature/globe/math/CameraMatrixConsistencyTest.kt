package com.github.daanbouwman.flightplanner.feature.globe.math

import io.kotest.matchers.floats.shouldBeLessThan
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The CPU projection and the matrices handed to Filament must agree.
 *
 * This is the test UI-PLAN's G1 names, and it is the one that lets the globe
 * keep two projections at all. Imagery is transformed by the GPU from
 * [CameraMatrices]; the DEP/DEST labels, tile selection and every gesture run on
 * [GlobeCamera.project]. A disagreement between them does not crash — it shows
 * up as a label a few pixels off its own dot, or a drag that slides — so it has
 * to be caught here rather than looked for on a device.
 *
 * "Sub-pixel" is taken literally: the tolerance is a tenth of a pixel across a
 * 1440-pixel viewport, which is tight enough that a transposed matrix, a
 * dropped aspect term or a sign error on the handedness flip all fail, and loose
 * enough to absorb the difference between the float basis and the double
 * projection.
 */
class CameraMatrixConsistencyTest {

    private val tolerancePx = 0.1f

    private val viewports = listOf(
        GlobeViewport(1080f, 1440f),
        GlobeViewport(1440f, 1080f),
        GlobeViewport(1080f, 1080f),
    )

    private val cameras = listOf(
        GlobeCamera(),
        GlobeCamera(centerLat = 52.31f, centerLon = 4.76f, altitude = 0.6f),
        GlobeCamera(centerLat = -33.9f, centerLon = 151.2f, altitude = 3f, bearing = 1.2f),
        GlobeCamera(centerLat = 40.64f, centerLon = -73.78f, altitude = 0.05f, tilt = 1.0f),
        GlobeCamera(centerLat = 71f, centerLon = -179f, altitude = 8f, bearing = -2.4f, tilt = 0.7f),
        GlobeCamera(centerLat = 0f, centerLon = 0f, altitude = MIN_ALTITUDE),
    )

    private val probes = listOf(
        0f to 0f,
        52.31f to 4.76f,
        -33.9f to 151.2f,
        40.64f to -73.78f,
        84f to 12f,
        -84f to -170f,
        12f to 179.5f,
    )

    @Test
    fun `every visible probe projects to the same pixel both ways`() {
        var compared = 0
        for (viewport in viewports) {
            for (camera in cameras) {
                for ((lat, lon) in probes) {
                    val world = latLonToWorld(lat, lon)
                    val cpu = camera.worldToScreen(world, viewport)
                    val gpu = CameraMatrices.projectThroughMatrices(camera, viewport, world)

                    if (cpu == null) {
                        assertNull(
                            gpu,
                            "CPU rejected $lat/$lon as behind the camera but the matrices did not",
                        )
                        continue
                    }
                    val throughMatrices = assertNotNull(
                        gpu,
                        "matrices rejected $lat/$lon as behind the camera but the CPU did not",
                    )
                    (cpu - throughMatrices).length() shouldBeLessThan tolerancePx
                    compared++
                }
            }
        }
        // A guard against the whole grid silently culling: a test that compares
        // nothing passes and looks identical to one that compares everything.
        assertTrue(compared > 40, "only $compared points were actually compared")
    }

    /**
     * The near and far planes have to contain the sphere, or the GPU clips
     * geometry the CPU happily projects — which reads as the globe being sliced
     * open rather than as a projection disagreement.
     */
    @Test
    fun `the frustum contains the whole visible sphere at every altitude`() {
        for (camera in cameras) {
            val near = CameraMatrices.nearPlane(camera)
            val far = CameraMatrices.farPlane(camera)
            assertTrue(near > 0.0, "near plane must be positive, was $near")
            assertTrue(
                near <= camera.altitude.toDouble() + 1e-9,
                "near plane $near is beyond the closest sphere point ${camera.altitude}",
            )
            assertTrue(
                far >= camera.distance.toDouble() + 1.06,
                "far plane $far clips the far limb and the atmosphere shell",
            )
        }
    }

    @Test
    fun `the model matrix is an orthonormal frame at the camera position`() {
        for (camera in cameras) {
            val m = CameraMatrices.model(camera)
            val right = Vec3(m[0], m[1], m[2])
            val up = Vec3(m[4], m[5], m[6])
            val back = Vec3(m[8], m[9], m[10])
            val position = Vec3(m[12], m[13], m[14])

            kotlin.math.abs(right.length() - 1f) shouldBeLessThan 1e-4f
            kotlin.math.abs(up.length() - 1f) shouldBeLessThan 1e-4f
            kotlin.math.abs(back.length() - 1f) shouldBeLessThan 1e-4f
            kotlin.math.abs(right dot up) shouldBeLessThan 1e-4f
            kotlin.math.abs(right dot back) shouldBeLessThan 1e-4f
            kotlin.math.abs(up dot back) shouldBeLessThan 1e-4f

            // Right-handed, the way Filament expects: right × up = back.
            val cross = right cross up
            (cross - back).length() shouldBeLessThan 1e-3f

            kotlin.math.abs(position.length() - camera.distance) shouldBeLessThan 1e-3f
            kotlin.math.abs(m[3]) shouldBeLessThan 1e-6f
            kotlin.math.abs(m[15] - 1f) shouldBeLessThan 1e-6f
        }
    }
}
