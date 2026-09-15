package com.github.daanbouwman.flightplanner.feature.globe.ui

import io.kotest.matchers.shouldBe
import kotlin.test.Test

/**
 * [plateShiftPx] — the horizontal slide that keeps a DEP/DEST code inside the
 * surface without moving the dot it hangs from.
 *
 * The defect it exists for: a dot a few pixels inside the surface edge put half
 * its code outside it, and the globe's own clip cut the code in two. The slide
 * has to be exactly as large as the overhang plus the gutter, and no larger —
 * a code moved further than that is a code drifting away from its airport.
 */
class GlobeLabelsClampTest {

    private val surface = 1080f
    private val margin = 36f

    @Test
    fun `a code well inside the surface does not move`() {
        plateShiftPx(left = 400f, width = 130f, surfaceWidth = surface, marginPx = margin) shouldBe 0f
    }

    @Test
    fun `a code overhanging the left edge slides right by the overhang plus the gutter`() {
        // Centred, its left edge would be 20 px past the edge; it lands at the gutter.
        plateShiftPx(left = -20f, width = 130f, surfaceWidth = surface, marginPx = margin) shouldBe 56f
    }

    @Test
    fun `a code overhanging the right edge slides left`() {
        // Right edge at 1080 + 30; allowed right edge is 1080 - 36.
        plateShiftPx(left = 980f, width = 130f, surfaceWidth = surface, marginPx = margin) shouldBe -66f
    }

    @Test
    fun `exactly at the gutter is already in place`() {
        plateShiftPx(left = margin, width = 130f, surfaceWidth = surface, marginPx = margin) shouldBe 0f
        plateShiftPx(left = surface - margin - 130f, width = 130f, surfaceWidth = surface, marginPx = margin) shouldBe 0f
    }

    @Test
    fun `a code wider than the surface allows is left alone`() {
        // No slide satisfies the rule, and choosing one would only pick which end
        // to clip — so the code stays centred on its dot and the caller's fade
        // handles it, as it did before there was a slide.
        plateShiftPx(left = 100f, width = 1_040f, surfaceWidth = surface, marginPx = margin) shouldBe 0f
    }
}
