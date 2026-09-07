package com.github.daanbouwman.flightplanner.feature.globe.ui

import com.github.daanbouwman.flightplanner.feature.globe.math.GlobeCamera
import com.github.daanbouwman.flightplanner.feature.globe.math.GlobeViewport
import com.github.daanbouwman.flightplanner.feature.globe.math.MAX_ALTITUDE
import com.github.daanbouwman.flightplanner.feature.globe.math.MAX_TILT
import com.github.daanbouwman.flightplanner.feature.globe.math.MIN_ALTITUDE
import com.github.daanbouwman.flightplanner.feature.globe.math.ScreenPoint
import io.kotest.matchers.floats.shouldBeGreaterThan
import io.kotest.matchers.floats.shouldBeLessThan
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * The synchronous half of [GlobeCameraState]: the injected floor, and where a
 * zoom pins. The springs and the fling need a frame clock and are not here.
 *
 * 1080×2340 throughout — the shipping viewport, whose focal length is 2026 px
 * at the 60° field of view. At altitude 2 the disc is 716 px across its radius;
 * at altitude 9, the opening state, 204 px.
 */
class GlobeCameraStateTest {

    private val viewport = GlobeViewport(1080f, 2340f)

    private fun state(
        initial: GlobeCamera,
        floor: (GlobeViewport) -> Float = { MIN_ALTITUDE },
    ): GlobeCameraState =
        GlobeCameraState(initial, CoroutineScope(Dispatchers.Unconfined), floor) {}
            .also { it.viewport = viewport }

    /** A pixel [px] below the viewport centre. */
    private fun below(px: Float) = ScreenPoint(viewport.centerX, viewport.centerY + px)

    /** The disc's radius in pixels at tilt 0: `f / √(d² − 1)`. */
    private fun discRadiusPx(altitude: Float): Float {
        val d = 1f + altitude
        return GlobeCamera(altitude = altitude).focalPixels(viewport.height) / sqrt(d * d - 1f)
    }

    /** Degrees of arc from the nadir to the horizon at [altitude]. */
    private fun horizonDegrees(altitude: Float): Float = acos(1f / (1f + altitude)) * RAD_TO_DEG

    private fun ScreenPoint.distanceTo(o: ScreenPoint): Float = (this - o).length()

    // --- the floor ------------------------------------------------------------------

    @Test
    fun `zoomBy never goes below the injected floor`() {
        val s = state(GlobeCamera(altitude = 1f), floor = { 0.5f })
        s.zoomBy(0.1f, null)
        s.camera.altitude shouldBe 0.5f
        // And zooming back out from the floor is unhindered.
        s.zoomBy(2f, null)
        s.camera.altitude shouldBe 1f
    }

    @Test
    fun `steppedZoom respects the floor`() {
        val s = state(GlobeCamera(altitude = 1f), floor = { 0.5f })
        s.steppedZoom(-10).altitude shouldBe 0.5f
        s.steppedZoom(-1).altitude shouldBe 0.5f
        abs(s.steppedZoom(1).altitude - 2f) shouldBeLessThan 1e-5f
    }

    @Test
    fun `the floor is read from the viewport the state has`() {
        val s = state(
            GlobeCamera(altitude = 4f),
            floor = { box -> if (box.height > 2000f) 1f else 0.25f },
        )
        s.zoomBy(0.01f, null)
        s.camera.altitude shouldBe 1f
        s.viewport = GlobeViewport(1080f, 1030f)
        s.zoomBy(0.01f, null)
        s.camera.altitude shouldBe 0.25f
    }

    @Test
    fun `the ceiling holds, and a floor outside the camera's range is held inside it`() {
        val s = state(GlobeCamera(altitude = 5f), floor = { 0f })
        s.zoomBy(100f, null)
        s.camera.altitude shouldBe MAX_ALTITUDE
        s.zoomBy(0f, null)
        s.camera.altitude shouldBe MIN_ALTITUDE
    }

    @Test
    fun `at the floor a pinch on the sky does not turn the globe`() {
        val s = state(GlobeCamera(altitude = 2f), floor = { 2f })
        val before = s.camera
        s.zoomBy(0.5f, below(900f))
        s.camera shouldBe before
    }

    // --- pinning on the disc -----------------------------------------------------

    @Test
    fun `zoomBy keeps the point under the focus under it`() {
        val s = state(GlobeCamera(altitude = 2f))
        val focus = ScreenPoint(700f, 1400f)
        val under = assertNotNull(s.camera.screenToWorld(focus, viewport))
        s.zoomBy(0.5f, focus)
        s.camera.altitude shouldBe 1f
        assertNotNull(s.camera.worldToScreen(under, viewport)).distanceTo(focus) shouldBeLessThan 1f
    }

    @Test
    fun `zoomBy without a focus is about the centre`() {
        val s = state(GlobeCamera(centerLat = 10f, centerLon = 20f, altitude = 2f))
        s.zoomBy(0.5f, null)
        s.camera shouldBe GlobeCamera(centerLat = 10f, centerLon = 20f, altitude = 1f)
    }

    @Test
    fun `near the edge the centre swings to keep the point under the fingers`() {
        val s = state(GlobeCamera(altitude = 2f))
        // 700 of the disc's 716 px: the last 2 % of the radius is 10° of arc.
        val focus = below(700f)
        val under = assertNotNull(s.camera.screenToWorld(focus, viewport))
        s.zoomBy(0.95f, focus)
        assertNotNull(s.camera.worldToScreen(under, viewport)).distanceTo(focus) shouldBeLessThan 2f
        // Several degrees for a 5 % pinch is the correct answer here, not a defect.
        s.camera.centerLat shouldBeLessThan -5f
        s.camera.centerLat shouldBeGreaterThan -10f
    }

    @Test
    fun `a zoom-out that carries the focus off the disc holds the point just inside the rim`() {
        val s = state(GlobeCamera(altitude = 2f))
        val focus = below(700f)
        val under = assertNotNull(s.camera.screenToWorld(focus, viewport))
        s.zoomBy(1.05f, focus)
        // The disc is now 690 px; the focus is off it, and the point is held at 98 %.
        val held = 0.98f * discRadiusPx(s.camera.altitude)
        val at = assertNotNull(s.camera.worldToScreen(under, viewport))
        abs(at.distanceTo(ScreenPoint(viewport.centerX, viewport.centerY)) - held) shouldBeLessThan 2f
        abs(s.camera.centerLat) shouldBeLessThan 3f
    }

    @Test
    fun `a tilted, turned camera still pins the focus`() {
        val s = state(GlobeCamera(altitude = 2f, bearing = 0.7f, tilt = 0.6f))
        val focus = ScreenPoint(700f, 1500f)
        val under = assertNotNull(s.camera.screenToWorld(focus, viewport))
        s.zoomBy(0.9f, focus)
        assertNotNull(s.camera.worldToScreen(under, viewport)).distanceTo(focus) shouldBeLessThan 2f
    }

    @Test
    fun `a double-tap near the edge pins the tapped point over the halving`() {
        val s = state(GlobeCamera(altitude = 2f))
        val focus = below(650f)
        val under = assertNotNull(s.camera.screenToWorld(focus, viewport))
        val target = s.steppedZoom(-1, focus)
        target.altitude shouldBe 1f
        assertNotNull(target.worldToScreen(under, viewport)).distanceTo(focus) shouldBeLessThan 2f
    }

    // --- pinning on the sky --------------------------------------------------------

    @Test
    fun `the raw solve the sky case replaces swings the camera`() {
        // Kept as the measurement behind GlobeCameraState.zoomedBy: what
        // `panTo(screenToWorldClamped(focus), focus)` does when the focus is on
        // the sky. At altitude 2 the disc is 716 px; the focus is 900 px out.
        val focus = below(900f)
        val fromTwo = GlobeCamera(altitude = 2f)
        val rim = fromTwo.screenToWorldClamped(focus, viewport)
        abs(fromTwo.copy(altitude = 1.9f).panTo(rim, focus, viewport).centerLat) shouldBeGreaterThan 10f

        val fromNine = GlobeCamera(altitude = 9f)
        val far = below(600f)
        abs(fromNine.copy(altitude = 8.55f).panTo(fromNine.screenToWorldClamped(far, viewport), far, viewport).centerLat) shouldBeGreaterThan 30f
    }

    @Test
    fun `a pinch on the sky turns the globe toward the fingers by exactly the horizon shrink`() {
        val s = state(GlobeCamera(altitude = 2f))
        val focus = below(900f)
        assertNull(s.camera.screenToWorld(focus, viewport))
        val rim = s.camera.screenToWorldClamped(focus, viewport)

        s.zoomBy(0.95f, focus)
        val shrink = horizonDegrees(2f) - horizonDegrees(1.9f)
        // South of the nadir, so toward it is a smaller latitude.
        abs(s.camera.centerLat + shrink) shouldBeLessThan 0.02f
        abs(s.camera.centerLon) shouldBeLessThan 1e-3f
        // And the limb point is on the new horizon, not behind it.
        abs(s.camera.facingValue(rim) - 1f / s.camera.distance) shouldBeLessThan 1e-3f
    }

    @Test
    fun `a pinch out on the sky turns the globe away by the horizon growth`() {
        val s = state(GlobeCamera(altitude = 2f))
        s.zoomBy(1.05f, below(900f))
        val growth = horizonDegrees(2.1f) - horizonDegrees(2f)
        abs(s.camera.centerLat - growth) shouldBeLessThan 0.02f
    }

    @Test
    fun `a double-tap on the sky zooms about the nearest limb point`() {
        val s = state(GlobeCamera(altitude = 2f))
        val target = s.steppedZoom(-1, below(900f))
        target.altitude shouldBe 1f
        abs(target.centerLat + (horizonDegrees(2f) - horizonDegrees(1f))) shouldBeLessThan 0.05f
    }

    @Test
    fun `every pinch step from the opening altitude is bounded by the horizon shrink`() {
        val s = state(GlobeCamera(altitude = 9f))
        val focus = below(600f)
        var steps = 0
        while (s.camera.screenToWorld(focus, viewport) == null && steps < 60) {
            val before = s.camera
            s.zoomBy(0.97f, focus)
            val shrink = horizonDegrees(before.altitude) - horizonDegrees(s.camera.altitude)
            abs(s.camera.centerLat - before.centerLat) shouldBeLessThan shrink + 0.02f
            abs(s.camera.centerLon) shouldBeLessThan 1e-3f
            steps++
        }
        // The disc reached the fingers well before the loop's cap; from here the
        // pinned form takes over.
        (steps > 20 && steps < 60) shouldBe true
        s.camera.centerLat shouldBeLessThan 0f
    }

    // --- the drag ------------------------------------------------------------------

    @Test
    fun `a drag inside the disc puts the anchor under the finger`() {
        val s = state(GlobeCamera(altitude = 2f))
        val anchor = s.camera.screenToWorldClamped(below(500f), viewport)
        s.panAnchor(anchor, below(530f))
        assertNotNull(s.camera.worldToScreen(anchor, viewport)).distanceTo(below(530f)) shouldBeLessThan 1f
    }

    @Test
    fun `a drag past the limb stops at the rim instead of swinging the camera`() {
        val s = state(GlobeCamera(altitude = 2f))
        val anchor = s.camera.screenToWorldClamped(below(710f), viewport)
        // The raw solve for the same drag: 27° of latitude for 20 px of finger.
        abs(s.camera.panTo(anchor, below(730f), viewport).centerLat) shouldBeGreaterThan 20f

        s.panAnchor(anchor, below(730f))
        abs(s.camera.centerLat) shouldBeLessThan 5f
        val held = 0.98f * discRadiusPx(2f)
        val at = assertNotNull(s.camera.worldToScreen(anchor, viewport))
        abs(at.distanceTo(ScreenPoint(viewport.centerX, viewport.centerY)) - held) shouldBeLessThan 2f
    }

    // --- the small moves -----------------------------------------------------------

    @Test
    fun `a clockwise twist is a decrease in bearing`() {
        val s = state(GlobeCamera(bearing = 1f))
        s.twistBy(0.25f)
        abs(s.camera.bearing - 0.75f) shouldBeLessThan 1e-6f
    }

    @Test
    fun `tilt is held between level and the maximum`() {
        val s = state(GlobeCamera(tilt = 0.1f))
        s.tiltBy(-1f)
        s.camera.tilt shouldBe 0f
        s.tiltBy(10f)
        s.camera.tilt shouldBe MAX_TILT
    }

    @Test
    fun `upright keeps position and altitude`() {
        val s = state(GlobeCamera(centerLat = 5f, centerLon = 6f, altitude = 3f, bearing = 1f, tilt = 0.5f))
        s.uprightCamera() shouldBe GlobeCamera(centerLat = 5f, centerLon = 6f, altitude = 3f)
    }

    private companion object {
        const val RAD_TO_DEG: Float = (180.0 / PI).toFloat()
    }
}
