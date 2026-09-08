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

    /** Half a plate, as [GlobeLabels] measures it. */
    private val halfW = 28f * 3f

    private fun alpha(x: Float, y: Float, rects: List<Rect>) =
        cornerAlpha(ScreenPoint(x, y), rects, reserve, halfW)

    @Test
    fun `a dot clear of every corner is untouched`() {
        alpha(400f, 400f, listOf(stack)) shouldBe 1f
    }

    @Test
    fun `no point, or no reserved corners, is untouched`() {
        cornerAlpha(null, listOf(stack), reserve, halfW) shouldBe 1f
        alpha(760f, 700f, emptyList()) shouldBe 1f
    }

    @Test
    fun `a dot over the stack is gone`() {
        alpha(760f, 700f, listOf(stack)) shouldBe 0f
        // Level with the top edge: the plate still hangs down over the stack.
        alpha(760f, stack.top, listOf(stack)) shouldBe 0f
    }

    @Test
    fun `the fade is one plate-height above the stack`() {
        val half = alpha(760f, stack.top - reserve / 2f, listOf(stack))
        half shouldBeGreaterThan 0f
        half shouldBeLessThan 1f

        alpha(760f, stack.top - reserve - 1f, listOf(stack)) shouldBe 1f
    }

    /**
     * **This case changed, and deliberately.** It used to assert that a dot one
     * pixel left of the stack was untouched, which was the defect written down as
     * an expectation: `Plate` centres its code on the dot, so a dot just outside
     * the stack still draws the inner half of that code over it. A dot is only
     * clear once it is a plate-half-width outside.
     */
    @Test
    fun `a dot just outside the stack still fades, because its plate is not`() {
        alpha(stack.left - 1f, 700f, listOf(stack)) shouldBe 0f
        alpha(stack.left - halfW + 1f, 700f, listOf(stack)) shouldBe 0f
    }

    @Test
    fun `a dot a whole plate clear of the stack is untouched`() {
        alpha(stack.left - halfW - 1f, 700f, listOf(stack)) shouldBe 1f
    }

    @Test
    fun `the tightest corner wins when two overlap a dot`() {
        val credit = Rect(left = 0f, top = 500f, right = 240f, bottom = 560f)
        val wide = Rect(left = 0f, top = 700f, right = 800f, bottom = 800f)
        // Inside `credit` (which starts higher) and inside `wide`.
        alpha(120f, 540f, listOf(credit, wide)) shouldBe 0f
    }
}
