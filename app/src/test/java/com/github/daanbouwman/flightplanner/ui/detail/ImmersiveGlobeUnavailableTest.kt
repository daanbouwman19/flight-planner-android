package com.github.daanbouwman.flightplanner.ui.detail

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.github.daanbouwman.flightplanner.core.designsystem.theme.FlightPlannerTheme
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression test for Medium finding 6 of the 2026-09 review: the immersive
 * globe with no arc used to be an early `return` and therefore a blank surface
 * with nothing on it — no globe, no message, and no collapse control.
 *
 * Composes only the no-globe branch. The globe branch needs a Filament session
 * and a `SurfaceView`, neither of which Robolectric can stand up; the branch
 * selection itself is [ImmersiveGlobeStageTest]'s job. JUnit 4 throughout for
 * the reason `LogbookOverlayTest` gives: `RobolectricTestRunner` is a JUnit 4
 * runner reached through the vintage engine.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ImmersiveGlobeUnavailableTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val collapseLabel = "Leave the full-screen globe"

    @Test
    fun `with nothing to draw, the way out is still there and works`() {
        var collapsed = 0
        composeTestRule.setContent {
            FlightPlannerTheme(dynamicColor = false) {
                ImmersiveGlobeUnavailable(stage = ImmersiveGlobeStage.Unavailable, onCollapse = { collapsed++ })
            }
        }

        composeTestRule.onNodeWithContentDescription(collapseLabel).assertIsDisplayed().assertHasClickAction()
        composeTestRule.onNodeWithText("No globe for this route").assertIsDisplayed()

        composeTestRule.onNodeWithContentDescription(collapseLabel).performClick()
        collapsed shouldBe 1
    }

    @Test
    fun `while still loading, the way out is there and the surface stays quiet`() {
        composeTestRule.setContent {
            FlightPlannerTheme(dynamicColor = false) {
                ImmersiveGlobeUnavailable(stage = ImmersiveGlobeStage.Loading, onCollapse = {})
            }
        }

        composeTestRule.onNodeWithContentDescription(collapseLabel).assertIsDisplayed()
        composeTestRule.onNodeWithText("No globe for this route").assertDoesNotExist()
    }
}
