package com.github.daanbouwman.flightplanner.feature.globe.math

import kotlin.math.nextUp
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The near plane must clear the surface by more than float32 can wobble a vertex.
 *
 * The globe's mesh carries **absolute unit-sphere positions in float32**, so one
 * ulp of 1.0 — `1.19e-7` radii, about 76 cm — is the finest a vertex can be
 * placed no matter how deep the imagery goes. If the near plane comes within a
 * few ulps of the surface, each vertex is a coin flip against it: triangles are
 * clipped at random, the imagery comes apart into scattered slivers and the
 * backdrop sphere shows through the holes.
 *
 * That shipped. `MIN_NEAR` was `1e-4`, the same order as [MIN_ALTITUDE] itself,
 * so at the bottom of the zoom range the guard beat the `0.5 × altitude` rule
 * and left **0.11 mm** of clearance against 76 cm of quantisation. It was
 * invisible on the keyless build, whose imagery floors the camera 600 km up, and
 * appeared the moment an ArcGIS key let the camera reach the deck.
 *
 * So this asserts the property rather than the constant: at every altitude the
 * camera can legally hold, the near plane sits far enough in front of the
 * nearest point of the sphere that float32 noise cannot cross it.
 */
class CameraNearPlaneTest {

    /** One ulp of a unit-sphere coordinate: the finest a vertex position can be placed. */
    private val positionUlp = (1f.nextUp() - 1f).toDouble()

    /**
     * How many ulps of clearance count as safe.
     *
     * A vertex carries error from its own rounding and from the camera-relative
     * subtraction the GPU does against a float32 translation of magnitude one, so
     * a few ulps are always in play; a hundred is that with room, and it still
     * leaves the near plane far enough out to keep the depth buffer useful.
     */
    private val safetyUlps = 100

    private val altitudes = listOf(
        MIN_ALTITUDE,
        MIN_ALTITUDE * 1.5f,
        2e-4f,
        1e-3f,
        0.01f,
        0.1f,
        0.5f,
        2f,
        MAX_ALTITUDE,
    )

    @Test
    fun `the near plane never approaches the surface within float32 noise`() {
        for (altitude in altitudes) {
            val camera = GlobeCamera(altitude = altitude)
            val near = CameraMatrices.nearPlane(camera)
            // The closest point of the unit sphere to the camera is the nadir,
            // exactly `altitude` away — no tilt brings anything nearer.
            val clearance = altitude.toDouble() - near
            assertTrue(
                clearance > positionUlp * safetyUlps,
                "at altitude $altitude the near plane is $near, leaving $clearance radii of " +
                    "clearance — under $safetyUlps ulps ($positionUlp radii each), so vertices " +
                    "can be clipped at random",
            )
        }
    }

    @Test
    fun `the near plane stays a fixed fraction of the altitude across the range`() {
        // The guard must never bind again: if it does, the near plane stops
        // tracking the altitude and the failure above comes back at some depth.
        for (altitude in altitudes) {
            val near = CameraMatrices.nearPlane(GlobeCamera(altitude = altitude))
            assertTrue(
                near == altitude.toDouble() * 0.5,
                "at altitude $altitude the near plane is $near, not half the altitude — " +
                    "MIN_NEAR is binding again",
            )
        }
    }

    @Test
    fun `the frustum stays ordered and finite at the deck`() {
        val camera = GlobeCamera(altitude = MIN_ALTITUDE)
        val near = CameraMatrices.nearPlane(camera)
        val far = CameraMatrices.farPlane(camera)
        assertTrue(near > 0.0 && near.isFinite(), "near plane is $near")
        assertTrue(far > near, "far plane $far is not beyond near plane $near")

        val projection = CameraMatrices.projection(camera, GlobeViewport(1080f, 2424f))
        assertTrue(projection.all { it.isFinite() }, "projection has a non-finite term")
    }
}
