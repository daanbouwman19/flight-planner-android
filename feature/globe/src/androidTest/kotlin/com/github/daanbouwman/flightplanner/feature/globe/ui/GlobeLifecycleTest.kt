package com.github.daanbouwman.flightplanner.feature.globe.ui

import android.accessibilityservice.AccessibilityService
import android.app.Instrumentation
import android.content.ComponentName
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.daanbouwman.flightplanner.feature.globe.render.GlobeSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The camera survives `GlobeSession` being torn down and rebuilt underneath a
 * view that never detached — the case `ProcessLifecycleOwner`'s `ON_STOP`
 * forces (see that class's doc) and Compose's own attach/dispose pair never
 * sees, because backgrounding does not recompose this screen away.
 *
 * Drives a real backgrounding — a HOME action, see the second test — rather
 * than calling any of `GlobeSession`'s internals directly, because the thing
 * actually worth proving is that a *real* backgrounding — not a call that
 * assumes the mechanism it is meant to test — ends in the same camera.
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

        // **A real Home press, not `scenario.moveToState(CREATED)`.** This used
        // to move the scenario to CREATED and sleep a second for
        // ProcessLifecycleOwner's ~700 ms debounce — and it passed, every run,
        // with the teardown never having happened. `ActivityScenario` backgrounds
        // an activity by starting its own transparent `EmptyActivity` over it,
        // *in this process*; the process therefore still has a resumed activity,
        // and `ProcessLifecycleOwner`, which is about the process and not the
        // activity, never dispatches `ON_STOP`. Polling for it instead of
        // sleeping is what found this: the owner sat at RESUMED for the whole
        // deadline. A global HOME action through `UiAutomation` takes every
        // activity of this process off the screen, which is the case the
        // session's observer is written for.
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assertTrue(
            "the device must accept a global HOME action",
            instrumentation.uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME),
        )
        // The owner debounces a quick pause/resume - a configuration change, a
        // brief interruption - for about 700 ms before dispatching `ON_STOP`,
        // specifically so that case does *not* tear a session down. Waiting for
        // the owner to actually reach CREATED is waiting for the event itself;
        // a fixed sleep is wrong in both directions, late on a loaded device
        // and silent on a build where the event never comes. Observers run
        // inside the same dispatch, so once the state reads CREATED on the main
        // thread the session's `forceTeardown` has already run.
        awaitProcessLifecycle(Lifecycle.State.CREATED)
        assertFalse(
            "ON_STOP must have torn the session down, or the rebuild below is not being tested",
            GlobeSession.hasLiveInstance,
        )

        // Back the way a user comes back: the same instance brought to the
        // front, so `onStart` fires on the process owner and the composition
        // that never went away is resumed under a rebuilt session.
        // `scenario.moveToState(RESUMED)` cannot do this - it only knows how to
        // finish its own EmptyActivity, and after a real HOME it waits out its
        // 45 s for a transition that never comes.
        bringActivityBackToFront(instrumentation)
        awaitProcessLifecycle(Lifecycle.State.RESUMED)

        // The camera below is read through the handle, which is re-bound to the
        // *rebuilt* session's state on recomposition - so wait for that session
        // to exist, not merely for a finite altitude, which the old state would
        // report just as happily.
        rule.waitUntil(timeoutMillis = 10_000) {
            GlobeSession.hasLiveInstance && handle.altitude.isFinite()
        }
        rule.waitForIdle()

        assertEquals("altitude", altitude, handle.altitude, 1e-3f)
        assertEquals("centerLon", lon, handle.centerLon, 1e-3f)
        assertEquals("centerLat", lat, handle.centerLat, 1e-3f)
    }

    /**
     * Blocks until `ProcessLifecycleOwner` reports [state], or fails at the deadline.
     *
     * Polled from the test thread with the state read on main, rather than
     * through `rule.waitUntil`: the activity is stopped for the duration, and
     * the compose rule's idling is not something to lean on while the host it
     * idles is not resumed. Three seconds is four times the debounce.
     */
    private fun awaitProcessLifecycle(state: Lifecycle.State, timeoutMillis: Long = 3_000) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val deadline = SystemClock.uptimeMillis() + timeoutMillis
        while (true) {
            var current: Lifecycle.State? = null
            instrumentation.runOnMainSync {
                current = ProcessLifecycleOwner.get().lifecycle.currentState
            }
            if (current == state) return
            if (SystemClock.uptimeMillis() > deadline) {
                fail(
                    "ProcessLifecycleOwner never reached $state within ${timeoutMillis} ms " +
                        "(still $current), so the teardown this test exists to exercise did not run",
                )
            }
            Thread.sleep(POLL_MILLIS)
        }
    }

    /**
     * Re-launches this test's activity with `FLAG_ACTIVITY_REORDER_TO_FRONT`,
     * through the shell rather than `startActivity`: an app with nothing on
     * screen is exactly what the platform's background-launch restriction
     * stops, and the instrumentation's shell is exempt. Reorder-to-front,
     * because the point is to resume the *existing* instance - the one holding
     * the composition and the handle - not to create a second.
     */
    private fun bringActivityBackToFront(instrumentation: Instrumentation) {
        val component = ComponentName(instrumentation.targetContext, ComponentActivity::class.java)
        val command = "am start --activity-reorder-to-front -n ${component.flattenToShortString()}"
        val fd = instrumentation.uiAutomation.executeShellCommand(command)
        // Drain and close, or the shell's own process is left waiting on its pipe.
        ParcelFileDescriptor.AutoCloseInputStream(fd).use { it.readBytes() }
    }

    private companion object {
        const val POLL_MILLIS = 50L
    }
}
