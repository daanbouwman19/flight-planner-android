package com.github.daanbouwman.flightplanner.feature.globe.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/**
 * The DEP/DEST plates, after the input/semantics layer moved beneath them —
 * see the KDoc on that reordering in `GlobeCanvas`.
 *
 * `assertIsDisplayed` does not reproduce the actual defect: a real
 * accessibility service prunes a node its own *paint order* says is fully
 * covered, which is a check the compose testing tree does not apply the way
 * `AndroidComposeViewAccessibilityDelegateCompat` does for a live TalkBack
 * session. What this proves is the half that *is* reachable from a JVM-side
 * test — the plates exist and are laid out — plus that reordering the layers
 * did not, as a side effect, change what a touch on a plate's own pixels
 * does. The occlusion claim itself is verified on a device: see UI-PLAN's
 * verification section for this phase, `adb shell uiautomator dump`.
 */
@RunWith(AndroidJUnit4::class)
class GlobeLabelSemanticsTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var handle: GlobeControlsHandle

    /**
     * Shows a globe on a route **unique to the calling test**.
     *
     * `GlobeSession` is a process singleton, instrumented tests share a process,
     * and `GlobeCanvas` re-fits only for a route the session has not seen —
     * `adopted = session.routeKey == key`, which is deliberate, because moving
     * the hero's camera into the immersive screen must not re-frame a view the
     * reader had already moved. The consequence for tests is that a camera
     * outlives the test that moved it: this class's own drag test pans the
     * globe, and whichever test ran second on the *same* route inherited the pan
     * and found its departure plate dragged off screen. Method order is not
     * fixed, which is exactly why that showed up as a test failing in some runs
     * and not others rather than as an honest, repeatable red.
     *
     * A route per test is the isolation, and it costs nothing: the identity the
     * session keys on is the pair of codes.
     */
    private fun show(destinationIcao: String, destinationLat: Double, destinationLon: Double) {
        rule.setContent {
            handle = rememberGlobeControls()
            GlobeSurface(
                route = GlobeRoute(
                    departureIcao = "EHAM",
                    destinationIcao = destinationIcao,
                    arcLats = doubleArrayOf(52.3086, 48.5, 44.5, destinationLat),
                    arcLons = doubleArrayOf(4.7639, 2.0, -0.5, destinationLon),
                ),
                controls = handle,
                modifier = Modifier.fillMaxSize(),
            )
        }
        // **Settled, not merely fitted.** Waiting only for "a fit has happened"
        // races the camera that is still moving: the viewport-rescale effect
        // runs after the first fit. Two consecutive identical samples is the
        // same "settled" test the renderer itself uses to skip a frame.
        var previous: Triple<Float, Float, Float>? = null
        rule.waitUntil(timeoutMillis = 15_000) {
            val now = Triple(handle.altitude, handle.centerLon, handle.centerLat)
            val settled = now == previous && now.first.isFinite() && now.first != 2f
            previous = now
            settled
        }
        rule.waitForIdle()
    }

    @Test
    fun bothPlatesExistAndAreDisplayed() {
        show("LEMD", destinationLat = 40.4936, destinationLon = -3.5668)
        rule.onNodeWithContentDescription("EHAM").assertIsDisplayed()
        rule.onNodeWithContentDescription("LEMD").assertIsDisplayed()
    }

    @Test
    fun aDragStartingOnAPlateStillPansTheGlobe() {
        // Its own route, because this one deliberately leaves the camera moved.
        show("LIRF", destinationLat = 41.8003, destinationLon = 12.2389)
        val before = handle.centerLon
        val depBounds = rule.onNodeWithContentDescription("EHAM").fetchSemanticsNode().boundsInRoot
        val start = depBounds.center

        rule.onRoot().performTouchInput {
            down(0, start)
            repeat(20) { step ->
                updatePointerTo(0, start + Offset(15f * (step + 1), 0f))
                move(16)
            }
            up(0)
        }
        rule.waitForIdle()
        assertTrue(
            "a drag starting on the DEP plate's own pixels must still reach the input layer beneath it: " +
                "$before -> ${handle.centerLon}",
            abs(handle.centerLon - before) > 1f,
        )
    }
}
