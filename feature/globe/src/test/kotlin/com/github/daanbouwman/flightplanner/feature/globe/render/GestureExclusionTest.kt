package com.github.daanbouwman.flightplanner.feature.globe.render

import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.test.assertNull

/**
 * Where the back gesture is kept off the sphere, and where it is deliberately
 * let through. Numbers are px; the cap stands in for 200 dp at some density.
 */
class GestureExclusionTest {

    @Test
    fun `a short view below its chrome is excluded whole`() {
        // The compact landscape hero: 132 dp tall, the app bar over the top.
        GestureExclusion.forSurface(width = 1000, height = 400, topChromePx = 100, maxBandPx = 600) shouldBe
            GestureExclusion(left = 0, top = 100, right = 1000, bottom = 400)
    }

    @Test
    fun `a tall view gets a capped band centred below the chrome`() {
        // The immersive screen: 2400 px tall, 300 px of collapse-button row,
        // 600 px of cap. Available 2100, so the band starts 750 below the chrome.
        GestureExclusion.forSurface(width = 1080, height = 2400, topChromePx = 300, maxBandPx = 600) shouldBe
            GestureExclusion(left = 0, top = 1050, right = 1080, bottom = 1650)
    }

    @Test
    fun `the band never reaches into the chrome, so back still works beside the controls`() {
        val band = GestureExclusion.forSurface(width = 1080, height = 1000, topChromePx = 250, maxBandPx = 600)!!
        (band.top >= 250) shouldBe true
        // And never below the view either.
        (band.bottom <= 1000) shouldBe true
    }

    @Test
    fun `no chrome means the band starts at the top of the view`() {
        // The Stats band: no chrome, shorter than the cap.
        GestureExclusion.forSurface(width = 1080, height = 780, topChromePx = 0, maxBandPx = 800) shouldBe
            GestureExclusion(left = 0, top = 0, right = 1080, bottom = 780)
    }

    @Test
    fun `nothing is excluded for an empty view, a chrome that covers it, or no allowance`() {
        assertNull(GestureExclusion.forSurface(width = 0, height = 400, topChromePx = 0, maxBandPx = 600))
        assertNull(GestureExclusion.forSurface(width = 1080, height = 0, topChromePx = 0, maxBandPx = 600))
        assertNull(GestureExclusion.forSurface(width = 1080, height = 400, topChromePx = 400, maxBandPx = 600))
        assertNull(GestureExclusion.forSurface(width = 1080, height = 400, topChromePx = 900, maxBandPx = 600))
        assertNull(GestureExclusion.forSurface(width = 1080, height = 400, topChromePx = 0, maxBandPx = 0))
    }
}
