package com.github.daanbouwman.flightplanner.feature.globe.math

import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.floats.shouldBeGreaterThan
import io.kotest.matchers.floats.shouldBeLessThan
import io.kotest.matchers.shouldBe
import kotlin.math.hypot
import kotlin.test.Test

/**
 * What a reader sees of the sphere in an embedded band: either a window of
 * imagery with the limb out of sight, or the whole planet sitting inside the
 * card with a margin — never a disc a few percent larger than its frame.
 */
class GlobeBandFitTest {

    /** The Stats band on a 360 dp phone at 3×: the card's width, square. */
    private val square = GlobeViewport(width = 984f, height = 984f)

    /** The same band on a wide window, where the short axis is the height. */
    private val wide = GlobeViewport(width = 2000f, height = 700f)

    private val inset = 36f

    private fun GlobeCamera.discRadius(viewport: GlobeViewport): Float =
        GlobeBandFit.discRadiusPx(distance, GlobeBandFit.focalLength(viewport, fovY))

    /** A camera whose disc has exactly [radius] on [viewport]. */
    private fun cameraWithDisc(radius: Float, viewport: GlobeViewport): GlobeCamera {
        val focal = GlobeBandFit.focalLength(viewport, DEFAULT_FOV_Y)
        val ratio = focal / radius
        return GlobeCamera(centerLat = 48f, centerLon = -30f, altitude = kotlin.math.sqrt(1f + ratio * ratio) - 1f)
    }

    @Test
    fun `a regional fit whose limb is far outside the band is a window and is kept`() {
        // Europe only: the fit parks the camera close, the disc is many times the band.
        val fitted = GlobeCamera(centerLat = 52f, centerLon = 10f, altitude = 0.25f)

        GlobeBandFit.settle(fitted, square, inset) shouldBe fitted
    }

    /**
     * The shipped defect: the transatlantic network's fit gave a disc about 8 %
     * wider than the band, touching both card edges and cut flat at the bottom.
     */
    @Test
    fun `a disc barely larger than the band is pulled back to the whole planet`() {
        val fitted = cameraWithDisc(radius = square.width / 2f * 1.08f, viewport = square)

        val settled = GlobeBandFit.settle(fitted, square, inset)

        settled.altitude shouldBeGreaterThan fitted.altitude
        settled.discRadius(square) shouldBe (square.width / 2f - inset plusOrMinus 0.5f)
    }

    @Test
    fun `a disc that would leave a corner of the band showing space is pulled back too`() {
        // Larger than the half-width, smaller than the half-diagonal: the middle of
        // each edge is imagery, the corners are not.
        val halfDiagonal = hypot(square.width, square.height) / 2f
        val fitted = cameraWithDisc(radius = halfDiagonal * 0.95f, viewport = square)

        GlobeBandFit.settle(fitted, square, inset).discRadius(square) shouldBe
            (square.width / 2f - inset plusOrMinus 0.5f)
    }

    @Test
    fun `a disc clear of every corner is a window and is kept`() {
        val halfDiagonal = hypot(square.width, square.height) / 2f
        val fitted = cameraWithDisc(radius = halfDiagonal * 1.10f, viewport = square)

        GlobeBandFit.settle(fitted, square, inset) shouldBe fitted
    }

    @Test
    fun `a fit that ran out to the far limit is brought in until the planet fills the band`() {
        // An antipodal pair: framePoints runs to MAX_DISTANCE, and the planet would
        // be a small disc in the middle of the card.
        val fitted = GlobeCamera(altitude = MAX_ALTITUDE)

        val settled = GlobeBandFit.settle(fitted, square, inset)

        settled.altitude shouldBeLessThan fitted.altitude
        settled.discRadius(square) shouldBe (square.width / 2f - inset plusOrMinus 0.5f)
    }

    @Test
    fun `on a wide band the planet fills the short axis`() {
        val fitted = GlobeCamera(altitude = MAX_ALTITUDE)

        GlobeBandFit.settle(fitted, wide, inset).discRadius(wide) shouldBe
            (wide.height / 2f - inset plusOrMinus 0.5f)
    }

    @Test
    fun `only the altitude moves`() {
        val fitted = cameraWithDisc(radius = square.width / 2f * 1.08f, viewport = square)

        val settled = GlobeBandFit.settle(fitted, square, inset)

        settled.centerLat shouldBe fitted.centerLat
        settled.centerLon shouldBe fitted.centerLon
        settled.bearing shouldBe fitted.bearing
        settled.tilt shouldBe fitted.tilt
    }

    @Test
    fun `an unmeasured viewport is passed through`() {
        val fitted = GlobeCamera(altitude = 1f)

        GlobeBandFit.settle(fitted, GlobeViewport(1f, 1f), inset) shouldBe fitted
        GlobeBandFit.settle(fitted, GlobeViewport(0f, 0f), inset) shouldBe fitted
    }

    @Test
    fun `the disc radius is the limb's tangent`() {
        // From twice the radius the limb is 30° off the axis: r = f · tan 30°.
        val focal = 1000f
        GlobeBandFit.discRadiusPx(distance = 2f, focal = focal) shouldBe
            (focal * kotlin.math.tan(Math.toRadians(30.0)).toFloat() plusOrMinus 0.01f)
        GlobeBandFit.discRadiusPx(distance = 1f, focal = focal) shouldBe Float.POSITIVE_INFINITY
    }
}
