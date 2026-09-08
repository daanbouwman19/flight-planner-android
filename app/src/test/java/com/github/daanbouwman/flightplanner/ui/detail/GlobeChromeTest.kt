package com.github.daanbouwman.flightplanner.ui.detail

import io.kotest.matchers.shouldBe
import kotlin.test.Test

/**
 * [imageryCovers] against the shape of the defect it replaced:
 * `overlappedFraction` used to step from "chrome on glass" to "opaque slab"
 * within about 0.6 dp of scroll, with hundreds of dp of photograph still on
 * screen. This asks the actual question instead — is the strip still behind
 * imagery — at the boundary and one pixel either side of it.
 */
class GlobeChromeTest {

    @Test
    fun `at rest, a hero taller than the depth covers it`() {
        imageryCovers(heroHeightPx = 900f, scrollPx = 0f, depthPx = 200f) shouldBe true
    }

    @Test
    fun `one pixel before the boundary, still covered`() {
        // hero 900, depth 200: the boundary is at scroll = 700.
        imageryCovers(heroHeightPx = 900f, scrollPx = 699f, depthPx = 200f) shouldBe true
    }

    @Test
    fun `at the boundary itself, no longer covered`() {
        // Strictly greater than, not greater-or-equal: at the exact point the
        // hero's bottom edge reaches the depth, there is no imagery left in
        // the strip being asked about.
        imageryCovers(heroHeightPx = 900f, scrollPx = 700f, depthPx = 200f) shouldBe false
    }

    @Test
    fun `one pixel past the boundary, not covered`() {
        imageryCovers(heroHeightPx = 900f, scrollPx = 701f, depthPx = 200f) shouldBe false
    }

    @Test
    fun `scrolled well past the hero, not covered`() {
        imageryCovers(heroHeightPx = 900f, scrollPx = 2000f, depthPx = 200f) shouldBe false
    }

    @Test
    fun `a negative scroll — overscroll — only covers more`() {
        imageryCovers(heroHeightPx = 900f, scrollPx = -50f, depthPx = 200f) shouldBe true
    }

    @Test
    fun `the shorter status-bar depth stays covered after the taller app-bar depth has cleared`() {
        // The two callers ask about different strips of the same hero: the
        // whole measured app bar (chromeOverImagery) and the shorter status
        // inset alone (barsOverImagery) it contains. A scroll position exists
        // where the first has cleared and the second has not.
        val heroHeightPx = 900f
        val scrollPx = 720f
        val statusBarPx = 100f
        val appBarPx = 200f

        imageryCovers(heroHeightPx, scrollPx, appBarPx) shouldBe false
        imageryCovers(heroHeightPx, scrollPx, statusBarPx) shouldBe true
    }
}
