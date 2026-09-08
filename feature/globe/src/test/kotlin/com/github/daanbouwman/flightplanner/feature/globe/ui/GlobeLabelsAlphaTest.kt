package com.github.daanbouwman.flightplanner.feature.globe.ui

import androidx.compose.ui.geometry.Rect
import com.github.daanbouwman.flightplanner.feature.globe.math.ScreenPoint
import io.kotest.matchers.floats.shouldBeGreaterThan
import io.kotest.matchers.floats.shouldBeLessThan
import io.kotest.matchers.shouldBe
import kotlin.test.Test

/**
 * [cornerAlpha] — the fade that keeps a DEP/DEST plate from drawing its code
 * behind the camera stack or the imagery credit in a bottom corner of the hero.
 *
 * The rects come from the chrome's own `onGloballyPositioned`, so the fade
 * follows the control whatever its height and whatever the font scale, rather
 * than a dp constant guessing at it.
 */
class GlobeLabelsAlphaTest {

    // A control stack low in a bottom corner: 56 px wide, 180 px tall, bottom at 800.
    private val stack = Rect(left = 744f, top = 620f, right = 800f, bottom = 800f)
    private val reserve = 40f

    @Test
    fun `a dot clear of every corner is untouched`() {
        cornerAlpha(ScreenPoint(400f, 400f), listOf(stack), reserve) shouldBe 1f
    }

    @Test
    fun `no point, or no reserved corners, is untouched`() {
        cornerAlpha(null, listOf(stack), reserve) shouldBe 1f
        cornerAlpha(ScreenPoint(760f, 700f), emptyList(), reserve) shouldBe 1f
    }

    @Test
    fun `a dot over the stack is gone`() {
        cornerAlpha(ScreenPoint(760f, 700f), listOf(stack), reserve) shouldBe 0f
        // Level with the top edge: the plate still hangs down over the stack.
        cornerAlpha(ScreenPoint(760f, stack.top), listOf(stack), reserve) shouldBe 0f
    }

    @Test
    fun `the fade is one plate-height above the stack`() {
        val half = cornerAlpha(ScreenPoint(760f, stack.top - reserve / 2f), listOf(stack), reserve)
        half shouldBeGreaterThan 0f
        half shouldBeLessThan 1f

        cornerAlpha(ScreenPoint(760f, stack.top - reserve - 1f), listOf(stack), reserve) shouldBe 1f
    }

    @Test
    fun `a dot beside the stack, in its own column, is untouched`() {
        // Same height as inside the stack, but left of its left edge.
        cornerAlpha(ScreenPoint(stack.left - 1f, 700f), listOf(stack), reserve) shouldBe 1f
    }

    @Test
    fun `the tightest corner wins when two overlap a dot`() {
        val credit = Rect(left = 0f, top = 500f, right = 240f, bottom = 560f)
        val wide = Rect(left = 0f, top = 700f, right = 800f, bottom = 800f)
        // Inside `credit` (which starts higher) and inside `wide`.
        cornerAlpha(ScreenPoint(120f, 540f), listOf(credit, wide), reserve) shouldBe 0f
    }
}
