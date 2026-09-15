package com.github.daanbouwman.flightplanner.ui.logbook

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression test for High finding 1 of the 2026-09 review: the `SnackbarHost` and the
 * `FloatingActionButton` in [LogbookOverlay] are siblings in one `Box`, and the FAB —
 * composed last, aligned `BottomEnd` — used to paint straight over the last ~88 dp of
 * whatever the host drew, occluding the "Undo" action on a delete snackbar.
 *
 * This runs on Robolectric rather than as an instrumented test so it stays in the fast
 * `testDebugUnitTest` suite. `RobolectricTestRunner` is a plain JUnit 4 `Runner`, so this
 * file is JUnit 4 throughout (not the `kotlin.test` JUnit 5 style the rest of `:app`'s
 * tests use) and reaches the JUnit 5 platform the module runs on via the vintage engine —
 * see `AndroidApplicationConventionPlugin`, which wires up all three (Robolectric, the
 * Compose test harness and the vintage engine) for exactly this test.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LogbookOverlayTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `undo action on the delete snackbar does not sit under the FAB`() {
        val snackbarHostState = SnackbarHostState()
        lateinit var scope: CoroutineScope

        composeTestRule.setContent {
            scope = rememberCoroutineScope()
            LogbookOverlay(
                snackbarHostState = snackbarHostState,
                onAddClick = {},
            )
        }

        // Mirrors what LogbookScreen actually shows on LogbookEvent.FlightDeleted:
        // R.string.logbook_flight_deleted with an R.string.plan_action_undo action.
        scope.launch {
            snackbarHostState.showSnackbar(
                message = "Flight removed from logbook",
                actionLabel = "Undo",
            )
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Undo").assertIsDisplayed()

        val undoBounds = composeTestRule.onNodeWithText("Undo").getBoundsInRoot()
        val fabBounds = composeTestRule.onNodeWithTag(FabTestTag).getBoundsInRoot()

        val intersects = undoBounds.left < fabBounds.right &&
            fabBounds.left < undoBounds.right &&
            undoBounds.top < fabBounds.bottom &&
            fabBounds.top < undoBounds.bottom

        intersects shouldBe false
    }
}
