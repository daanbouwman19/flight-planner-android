package com.github.daanbouwman.flightplanner.feature.globe.math

import io.kotest.matchers.floats.shouldBeGreaterThan
import io.kotest.matchers.floats.shouldBeLessThan
import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * The Rust original's camera suite, ported case for case.
 *
 * These are the assertions that make it safe to trust the CPU projection —
 * which the labels, the quadtree and every gesture run on. They are kept in the
 * reference's own order and with its own cases so a future change can be diffed
 * against `src/gui/components/globe/camera.rs` rather than argued about.
 */
class GlobeCameraTest {

    private val viewport = GlobeViewport(width = 800f, height = 800f)

    private fun camera() = GlobeCamera(
        centerLat = 0f,
        centerLon = 0f,
        altitude = 2f,
        bearing = 0f,
        tilt = 0f,
    )

    @Test
    fun `nadir projects to the viewport centre at zero tilt`() {
        val camera = camera()
        val nadir = latLonToWorld(0f, 0f)
        val screen = assertNotNull(camera.worldToScreen(nadir, viewport))
        val center = ScreenPoint(viewport.centerX, viewport.centerY)
        (screen - center).length() shouldBeLessThan 0.5f

        val off = assertNotNull(camera.worldToScreen(latLonToWorld(30f, 30f), viewport))
        (off - center).length() shouldBeGreaterThan 10f
    }

    @Test
    fun `panTo pins a world point to a target, at zero tilt and tilted`() {
        val worldPoint = latLonToWorld(30f, 20f)
        val target = ScreenPoint(450f, 350f)
        val panned = camera().panTo(worldPoint, target, viewport)
        val projected = assertNotNull(panned.worldToScreen(worldPoint, viewport))
        (projected - target).length() shouldBeLessThan 1f

        val tiltedPoint = latLonToWorld(10f, 15f)
        val tiltedTarget = ScreenPoint(420f, 380f)
        val tilted = camera().copy(tilt = 0.4f).panTo(tiltedPoint, tiltedTarget, viewport)
        val tiltedProjected = assertNotNull(tilted.worldToScreen(tiltedPoint, viewport))
        (tiltedProjected - tiltedTarget).length() shouldBeLessThan 1f
    }

    @Test
    fun `screenToWorld round-trips back to the same pixel`() {
        val camera = GlobeCamera(
            centerLat = 10f,
            centerLon = 20f,
            altitude = 2f,
            bearing = 0.2f,
            tilt = 0.3f,
        )
        val screen = ScreenPoint(410f, 390f)
        val world = assertNotNull(camera.screenToWorld(screen, viewport))
        val back = assertNotNull(camera.worldToScreen(world, viewport))
        (back - screen).length() shouldBeLessThan 1e-2f
    }

    @Test
    fun `tilting north brings a northern point toward the centre`() {
        val north = latLonToWorld(40f, 0f)
        val center = ScreenPoint(viewport.centerX, viewport.centerY)

        val flat = assertNotNull(camera().worldToScreen(north, viewport))
        val tilted = assertNotNull(camera().copy(tilt = 0.5f).worldToScreen(north, viewport))

        (tilted - center).length() shouldBeLessThan (flat - center).length()
    }

    @Test
    fun `facing value at the nadir is one`() {
        val fv = camera().facingValue(latLonToWorld(0f, 0f))
        kotlin.math.abs(fv - 1f) shouldBeLessThan 1e-4f
    }

    @Test
    fun `bearing rotation moves where north lands on screen`() {
        val north = latLonToWorld(10f, 0f)
        val flat = assertNotNull(
            camera().copy(tilt = 0.4f, bearing = 0f).worldToScreen(north, viewport),
        )
        val rotated = assertNotNull(
            camera().copy(tilt = 0.4f, bearing = 0.5f).worldToScreen(north, viewport),
        )
        (flat - rotated).length() shouldBeGreaterThan 1f
    }

    @Test
    fun `the fast basis variants match the slow ones`() {
        val camera = GlobeCamera(
            centerLat = 15f,
            centerLon = -30f,
            altitude = 1.5f,
            bearing = 0.3f,
            tilt = 0.2f,
        )
        val basis = camera.computeBasis()
        val points = listOf(
            latLonToWorld(0f, 0f),
            latLonToWorld(15f, -30f),
            latLonToWorld(-45f, 90f),
        )
        for (w in points) {
            val slow = camera.rotate(w)
            val fast = rotateFast(basis, w)
            kotlin.math.abs(slow.x - fast.x) shouldBeLessThan 1e-5f
            kotlin.math.abs(slow.y - fast.y) shouldBeLessThan 1e-5f
            kotlin.math.abs(slow.z - fast.z) shouldBeLessThan 1e-5f
            kotlin.math.abs(camera.facingValue(w) - facingValueFast(basis, w)) shouldBeLessThan 1e-5f
        }
    }

    @Test
    fun `a drag starting outside the disc still grabs the globe`() {
        val camera = camera()
        val outside = ScreenPoint(10f, 10f)
        // Off the disc entirely: the unclamped ray misses.
        kotlin.test.assertNull(camera.screenToWorld(outside, viewport))
        // Clamped, it lands on the limb — a unit vector, so on the sphere.
        val clamped = camera.screenToWorldClamped(outside, viewport)
        kotlin.math.abs(clamped.length() - 1f) shouldBeLessThan 1e-3f
    }
}
