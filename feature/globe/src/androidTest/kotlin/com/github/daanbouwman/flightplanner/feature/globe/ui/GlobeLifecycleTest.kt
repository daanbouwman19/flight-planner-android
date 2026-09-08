package com.github.daanbouwman.flightplanner.feature.globe.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.daanbouwman.flightplanner.feature.globe.render.GlobeSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The camera survives `GlobeSession` being torn down and rebuilt underneath a
 * view that never detached — the case `ProcessLifecycleOwner`'s `ON_STOP`
 * forces (see that class's doc) and Compose's own attach/dispose pair never
 * sees, because backgrounding does not recompose this screen away.
 *
 * Drives a real `Activity` lifecycle transition rather than calling any of
 * `GlobeSession`'s internals directly, because the thing actually worth
 * proving is that a *real* backgrounding — not a call that assumes the
 * mechanism it is meant to test — ends in the same camera.
 */
@RunWith(AndroidJUnit4::class)
class GlobeLifecycleTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var handle: GlobeControlsHandle

    private fun showGlobe() {
        rule.setContent {
            handle = rememberGlobeControls()
            GlobeSurface(
                route = GlobeRoute(
                    departureIcao = "EHAM",
                    destinationIcao = "KJFK",
                    arcLats = doubleArrayOf(52.3086, 50.0, 45.0, 40.6413),
                    arcLons = doubleArrayOf(4.7639, -20.0, -50.0, -73.7781),
                ),
                controls = handle,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    /**
     * The forced teardown must not kill the process while a surface is attached.
     *
     * This is the backgrounding crash, reduced to the thing that actually
     * caused it: `GlobeSession.forceTeardown()` runs `GlobeScene.destroy()` —
     * which frees the `globeOverlay` material — while this surface's own
     * `RouteRibbon` still holds two `MaterialInstance`s of it, because nothing
     * detached. Filament refuses, on the main thread, and the app dies with
     * `destroying material "globeOverlay" but 2 instances still alive`.
     *
     * **It calls `forceTeardown()` directly rather than backgrounding the
     * activity, and that is deliberate.** The first version of this test drove
     * `ActivityScenario.moveToState(CREATED)` and waited out
     * `ProcessLifecycleOwner`'s debounce — and passed against code that was
     * crashing on every Home press on a real device, because neither `ON_STOP`
     * nor a trim callback is reliably delivered to an app under instrumentation.
     * A test that can pass without running the code it is about is worse than no
     * test. Whether the platform delivers those callbacks is a separate question,
     * and it is answered on a device, not here.
     *
     * Red, before the fix, is a died-process run rather than a failed assertion:
     * the crash is on the app's main thread, so the runner reports the whole run
     * as crashed. Reaching the assertions at all is the result.
     */
    @Test
    fun aForcedTeardownUnderAnAttachedSurfaceDoesNotCrash() {
        showGlobe()
        rule.waitUntil(timeoutMillis = 15_000) {
            handle.altitude.isFinite() && handle.altitude != 2f
        }

        // On the main thread, which is where ON_STOP and onTrimMemory arrive.
        rule.runOnUiThread { GlobeSession.forceTeardown() }
        rule.waitForIdle()

        // The surface is still attached and must come back on its own, against
        // whatever session it now finds — see GlobeSurfaceView.syncLoop.
        rule.waitUntil(timeoutMillis = 15_000) { handle.altitude.isFinite() }
        assertTrue("the globe must still have a camera after a forced teardown", handle.altitude.isFinite())
    }

    @Test
    fun cameraSurvivesBackgroundingAndForegrounding() {
        showGlobe()
        // See GlobePinchTest: the default camera sits at altitude 2 exactly,
        // until the surface has reported its size and the fit has run once.
        rule.waitUntil(timeoutMillis = 15_000) {
            handle.altitude.isFinite() && handle.altitude != 2f
        }

        // Moved off the fit before backgrounding, so a silent re-fit on the
        // rebuilt session — rather than a real adoption of the carried camera
        // — would be caught instead of coinciding with where it already was.
        val fitted = handle.altitude
        handle.zoomIn()
        rule.waitUntil(timeoutMillis = 5_000) { handle.altitude != fitted }
        rule.waitForIdle()
        val altitude = handle.altitude
        val lon = handle.centerLon
        val lat = handle.centerLat

        rule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        // ProcessLifecycleOwner debounces a quick pause/resume — a
        // configuration change, a brief interruption — for about 700 ms
        // before it dispatches `ON_STOP` at all, specifically so that case
        // does *not* tear a session down. Waiting past that window is what
        // makes the resume below exercise the real teardown-and-rebuild
        // rather than the debounce swallowing it, which would make this
        // test pass for a reason that proves nothing.
        Thread.sleep(1_000)
        rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)

        rule.waitUntil(timeoutMillis = 10_000) { handle.altitude.isFinite() }
        rule.waitForIdle()

        assertEquals("altitude", altitude, handle.altitude, 1e-3f)
        assertEquals("centerLon", lon, handle.centerLon, 1e-3f)
        assertEquals("centerLat", lat, handle.centerLat, 1e-3f)
    }
}
