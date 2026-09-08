package com.github.daanbouwman.flightplanner.feature.globe.math

import io.kotest.matchers.shouldBe
import kotlin.test.Test

/**
 * [GlobeCamera.coversStatusStrip] — whether the sphere is really under the clock.
 *
 * The host that asks for light status-bar glyphs has only box geometry to go on:
 * "is the hero still tall enough". That stays true after the sphere has retreated
 * from the top of the box, and what is painted up there is then `GlobeInk.space`,
 * which **is** `colorScheme.surface` in a light theme — light glyphs on a
 * near-white page.
 *
 * ### What the first version of this got wrong, stated accurately
 *
 * It scanned the projected limb for the global minimum `y` and compared that to
 * the strip. A minimum over `y` says nothing about `x`, so **an apex above the
 * strip is not the strip being covered** — and the disc reaches the top of the
 * window well before it is wide enough to span it. That is the false positive,
 * and [anApexAboveTheStripIsNotCoverage] is it, measured.
 *
 * The review that found this described the case as a tilted, rotated ellipse
 * whose apex swings off-axis. **A sweep of altitudes 0.6–2 × bearings 0.5–2.2 ×
 * tilts 0.4–`MAX_TILT` found no such camera**, so that story is not what makes
 * the defect reachable; the plain narrow-disc case above is. The correction is
 * recorded rather than quietly dropped, because the fix is the same either way
 * and the reason it was made should be the true one.
 */
class StatusStripCoverageTest {

    private val viewport = GlobeViewport(width = 1080f, height = 2340f)
    private val strip = 64f

    @Test
    fun `a sphere filling the view covers the strip`() {
        val camera = GlobeCamera(centerLat = 20f, centerLon = 5f, altitude = 0.05f)
        camera.coversStatusStrip(viewport, strip) shouldBe true
    }

    @Test
    fun `a globe small in the middle of the view does not`() {
        // The whole planet as a disc with page all around it — the case where
        // `imageryCovers` still says the box is covered.
        val camera = GlobeCamera(centerLat = 20f, centerLon = 5f, altitude = MAX_ALTITUDE)
        camera.coversStatusStrip(viewport, strip) shouldBe false
    }

    /** The false positive the old min-`y` predicate produced, as a measurement. */
    @Test
    fun anApexAboveTheStripIsNotCoverage() {
        val camera = GlobeCamera(centerLat = 20f, centerLon = 5f, altitude = 1f)
        val points = FloatArray(Limb.SAMPLES * 2)
        val visible = Limb.projectInto(camera, camera.computeBasis(), viewport, points)

        // The limb's own apex is above the strip — exactly what the old
        // predicate keyed on, and it would have reported coverage here.
        val apexY = (0 until Limb.SAMPLES)
            .map { points[it * 2 + 1] }
            .filter { !it.isNaN() }
            .min()
        (visible >= 3) shouldBe true
        (apexY <= strip) shouldBe true

        // ...and the strip is not covered, because the disc is narrower than the
        // window and its ends are space.
        camera.coversStatusStrip(viewport, strip) shouldBe false
    }

    /** A turn alone does not change the answer: the disc is still the disc. */
    @Test
    fun `a turned camera close in still covers the strip`() {
        val camera = GlobeCamera(
            centerLat = 20f,
            centerLon = 5f,
            altitude = 0.05f,
            bearing = 1.1f,
        )
        camera.coversStatusStrip(viewport, strip) shouldBe true
    }

    /**
     * Leaning toward the horizon uncovers the strip, and that is correct.
     *
     * At tilt 0.9 rad the horizon is inside the window, so the top of the screen
     * really is sky — `GlobeInk.space` — even though the camera is 0.05 above the
     * surface and the sphere fills the bottom of the view. Light glyphs there
     * would be the same mistake in a new place, so the predicate says no. Written
     * down because the expectation that a close camera always covers the strip is
     * the obvious one and it is wrong.
     */
    @Test
    fun `leaning toward the horizon uncovers the strip`() {
        val camera = GlobeCamera(
            centerLat = 20f,
            centerLon = 5f,
            altitude = 0.05f,
            bearing = 1.1f,
            tilt = 0.9f,
        )
        camera.coversStatusStrip(viewport, strip) shouldBe false
    }

    @Test
    fun `a zero strip is never covered`() {
        GlobeCamera(altitude = 0.05f).coversStatusStrip(viewport, 0f) shouldBe false
    }

    @Test
    fun `a degenerate viewport is never covered`() {
        GlobeCamera(altitude = 0.05f).coversStatusStrip(GlobeViewport(0f, 0f), strip) shouldBe false
    }
}
