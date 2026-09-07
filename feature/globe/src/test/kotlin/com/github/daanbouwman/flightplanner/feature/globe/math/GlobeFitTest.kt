package com.github.daanbouwman.flightplanner.feature.globe.math

import io.kotest.matchers.floats.shouldBeGreaterThan
import io.kotest.matchers.floats.shouldBeLessThan
import io.kotest.matchers.shouldBe
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * What the fit has to be true of, on the box it is fitting.
 *
 * `GlobeFit` had no test file, and every defect a later review found in it —
 * the aspect-blind solve, the degree midpoint, the 2.5 distance cap, the clamp
 * that pinned every short leg to one camera — was invisible to `./gradlew build`
 * and visible in a screenshot. The assertions below are the ones that would have
 * caught each of them, so they are written against **what a reader sees** —
 * "both ends are inside the viewport" — rather than against the numbers the
 * implementation happens to produce.
 */
class GlobeFitTest {

    private companion object {
        /** The keyless fallback's ceiling and the keyed provider's — see `TileProvider`. */
        const val KEYLESS_MAX_LOD = 8
        const val KEYED_MAX_LOD = 18
    }

    /** The deep hero on a 411 dp phone: nearly square. */
    private val hero = GlobeViewport(width = 1080f, height = 1066f)

    /** The immersive screen on the same phone: a little over twice as tall. */
    private val immersive = GlobeViewport(width = 1080f, height = 2200f)

    // Two ends of an ordinary long leg, roughly east-west: Noyabrsk to Ronneby.
    private val usro = 63.1833 to 75.2700
    private val esdf = 56.2667 to 15.2656

    /** Where a lat/lon lands on screen for this camera, or null if it is not on it. */
    private fun GlobeCamera.screen(lat: Double, lon: Double, viewport: GlobeViewport) =
        project(rotate(latLonToWorld(lat.toFloat(), lon.toFloat())), viewport)

    private fun GlobeCamera.framesBoth(
        a: Pair<Double, Double>,
        b: Pair<Double, Double>,
        viewport: GlobeViewport,
    ) {
        val pa = assertNotNull(screen(a.first, a.second, viewport), "departure is behind the camera")
        val pb = assertNotNull(screen(b.first, b.second, viewport), "destination is behind the camera")
        viewport.contains(pa) shouldBe true
        viewport.contains(pb) shouldBe true
    }

    // ---- the aspect-blind solve -------------------------------------------------

    @Test
    fun `an east-west leg is inside the hero`() {
        GlobeFit.frameRoute(usro.first, usro.second, esdf.first, esdf.second, hero)
            .framesBoth(usro, esdf, hero)
    }

    @Test
    fun `the same leg is inside the immersive screen, which is half as wide for its focal length`() {
        GlobeFit.frameRoute(usro.first, usro.second, esdf.first, esdf.second, immersive)
            .framesBoth(usro, esdf, immersive)
    }

    @Test
    fun `a taller, narrower window is fitted from further out`() {
        val short = GlobeFit.frameRoute(usro.first, usro.second, esdf.first, esdf.second, hero)
        val tall = GlobeFit.frameRoute(usro.first, usro.second, esdf.first, esdf.second, immersive)
        tall.altitude shouldBeGreaterThan short.altitude
    }

    @Test
    fun `a north-south leg uses the tall axis of a portrait window`() {
        // Same angular length as the east-west case, turned ninety degrees. A fit
        // that took the narrower half-angle for every direction would pull back
        // as far as the east-west leg needs; this one should not have to.
        val northSouth = GlobeFit.frameRoute(0.0, 0.0, 29.8, 0.0, immersive)
        val eastWest = GlobeFit.frameRoute(0.0, 0.0, 0.0, 29.8, immersive)
        northSouth.framesBoth(0.0 to 0.0, 29.8 to 0.0, immersive)
        northSouth.altitude shouldBeLessThan eastWest.altitude
    }

    // ---- the degree midpoint ----------------------------------------------------

    @Test
    fun `a very long leg is centred on the arc, not on the average of the degrees`() {
        // KJFK to WSSS: about 138 degrees of arc. The degree midpoint is most of a
        // radian off the great circle, which used to put the whole route behind
        // the limb in the opening frame.
        val kjfk = 40.6398 to -73.7789
        val wsss = 1.3502 to 103.9944
        val camera = GlobeFit.frameRoute(kjfk.first, kjfk.second, wsss.first, wsss.second, hero)

        val centre = latLonToWorld(camera.centerLat, camera.centerLon)
        val mid = (latLonToWorld(kjfk.first.toFloat(), kjfk.second.toFloat()) +
            latLonToWorld(wsss.first.toFloat(), wsss.second.toFloat())).normalize()
        // The true arc midpoint, to within a rounding error.
        abs(centre dot mid) shouldBeGreaterThan 0.9999f

        camera.framesBoth(kjfk, wsss, hero)
    }

    @Test
    fun `a leg across the antimeridian is centred in the Pacific`() {
        // Anchorage to Tokyo. Averaging the two longitudes gives the Atlantic.
        val panc = 61.1744 to -149.9961
        val rjtt = 35.5533 to 139.7811
        val camera = GlobeFit.frameRoute(panc.first, panc.second, rjtt.first, rjtt.second, hero)
        abs(camera.centerLon) shouldBeGreaterThan 150f
        camera.framesBoth(panc, rjtt, hero)
    }

    // ---- the distance cap and the resolution floor -------------------------------

    @Test
    fun `a network spanning most of the planet pulls back past the old 2 point 5 cap`() {
        val lats = doubleArrayOf(64.0, -33.9, 35.6, -22.9)
        val lons = doubleArrayOf(-21.9, 151.2, 139.8, -43.2)
        val camera = GlobeFit.framePoints(lats, lons, hero)
        // 1.5 was the old ceiling on altitude; a set this wide needs more.
        camera.altitude shouldBeGreaterThan 1.5f
        camera.altitude shouldBeLessThan MAX_ALTITUDE + 1e-3f
    }

    @Test
    fun `a very short leg is not framed closer than the imagery is sharp`() {
        // 71 NM, the shortest kind of leg the app actually generates. With no
        // ceiling given the fit assumes the keyless provider's z8 pyramid.
        val camera = GlobeFit.frameRoute(-6.1256, 106.6559, -6.9, 107.6, hero)
        val focal = camera.focalPixels(hero.height)
        val sharpest = Quadtree.finestTexelRadians(KEYLESS_MAX_LOD) * focal
        camera.altitude shouldBeGreaterThan sharpest - 1e-4f
    }

    @Test
    fun `a keyed provider lets the same short leg be framed closer`() {
        // The floor is the imagery's, so a pyramid that publishes to z18 frames
        // the leg by its extent rather than by the z8 fallback's texel size.
        val keyless = GlobeFit.frameRoute(-6.1256, 106.6559, -6.9, 107.6, hero, maxLod = KEYLESS_MAX_LOD)
        val keyed = GlobeFit.frameRoute(-6.1256, 106.6559, -6.9, 107.6, hero, maxLod = KEYED_MAX_LOD)
        keyed.altitude shouldBeLessThan keyless.altitude
        keyed.framesBoth(-6.1256 to 106.6559, -6.9 to 107.6, hero)
    }

    @Test
    fun `two legs of different lengths below the old clamp get different cameras`() {
        // Every leg under about 776 NM used to resolve to one identical altitude.
        val shortLeg = GlobeFit.frameRoute(0.0, 0.0, 0.0, 1.0, immersive)
        val longerLeg = GlobeFit.frameRoute(0.0, 0.0, 0.0, 10.0, immersive)
        longerLeg.altitude shouldBeGreaterThan shortLeg.altitude
    }

    // ---- degenerate inputs -------------------------------------------------------

    @Test
    fun `a leg whose ends coincide still produces a usable camera`() {
        val camera = GlobeFit.frameRoute(51.5, -0.1, 51.5, -0.1, hero)
        camera.altitude shouldBeGreaterThan MIN_ALTITUDE
        camera.altitude shouldBeLessThan MAX_ALTITUDE + 1e-3f
        assertNotNull(camera.screen(51.5, -0.1, hero))
    }

    @Test
    fun `an antipodal pair does not produce a NaN camera`() {
        val camera = GlobeFit.frameRoute(0.0, 0.0, 0.0, 180.0, hero)
        camera.centerLat.isNaN() shouldBe false
        camera.centerLon.isNaN() shouldBe false
        camera.altitude.isNaN() shouldBe false
    }

    @Test
    fun `a fit centred on a pole is finite`() {
        val camera = GlobeFit.framePoints(
            doubleArrayOf(89.0, 89.0, 89.0),
            doubleArrayOf(0.0, 120.0, -120.0),
            hero,
        )
        camera.altitude.isNaN() shouldBe false
        camera.centerLat shouldBeGreaterThan 80f
    }

    @Test
    fun `an empty set and a degenerate viewport fall back rather than divide by zero`() {
        GlobeFit.framePoints(DoubleArray(0), DoubleArray(0), hero).altitude.isNaN() shouldBe false
        GlobeFit.frameRoute(0.0, 0.0, 10.0, 10.0, GlobeViewport(0f, 0f))
            .altitude.isNaN() shouldBe false
    }
}
