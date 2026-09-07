package com.github.daanbouwman.flightplanner.feature.globe.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/**
 * The gesture pump, driven with synthetic pointers against a real surface.
 *
 * Everything the recogniser decides is covered on the JVM in
 * `GestureRecognizerTest`; what is *not* reachable from there is the Compose
 * side — the pointer loop inside `awaitPointerEventScope`, the consumption
 * policy, the velocity tracker and the hand-off to the camera. That layer is
 * exactly where the first build lost the pinch: a 60 ms window measured from the
 * first finger, never re-opened when the second one landed, so two fingers
 * landing 80 ms apart could only ever pan. Every gesture here lands its second
 * finger late on purpose.
 *
 * The camera is read back through [GlobeControlsHandle]'s internal seams rather
 * than inferred from pixels — a screenshot of imagery that may not have loaded
 * proves nothing about the camera.
 */
@RunWith(AndroidJUnit4::class)
class GlobePinchTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var handle: GlobeControlsHandle

    private fun show(nestedVerticalScroll: Boolean = false) {
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
                nestedVerticalScroll = nestedVerticalScroll,
                modifier = Modifier.fillMaxSize(),
            )
        }
        // The camera is fitted only once the surface has reported its size; until
        // then it sits at the default `GlobeCamera()` altitude of exactly 2. A
        // transatlantic leg on a phone never fits at exactly that number.
        rule.waitUntil(timeoutMillis = 15_000) {
            handle.altitude.isFinite() && handle.altitude != 2f
        }
    }

    private fun globe() = rule.onNodeWithContentDescription("Globe showing", substring = true)

    @Test
    fun lateSecondFingerPinchOpenZoomsIn() {
        show()
        val before = handle.altitude
        globe().performTouchInput {
            val c = center
            down(0, c - Offset(150f, 0f))
            advanceEventTime(80)
            down(1, c + Offset(150f, 0f))
            repeat(30) { step ->
                val half = 150f + 10f * (step + 1)
                updatePointerTo(0, c - Offset(half, 0f))
                updatePointerTo(1, c + Offset(half, 0f))
                move(16)
            }
            up(0)
            up(1)
        }
        rule.waitForIdle()
        val after = handle.altitude
        assertTrue("pinch open must zoom in: $before -> $after", after < before * 0.6f)
    }

    @Test
    fun lateSecondFingerPinchCloseZoomsOut() {
        show()
        val before = handle.altitude
        globe().performTouchInput {
            val c = center
            down(0, c - Offset(450f, 0f))
            advanceEventTime(80)
            down(1, c + Offset(450f, 0f))
            repeat(30) { step ->
                val half = 450f - 10f * (step + 1)
                updatePointerTo(0, c - Offset(half, 0f))
                updatePointerTo(1, c + Offset(half, 0f))
                move(16)
            }
            up(0)
            up(1)
        }
        rule.waitForIdle()
        val after = handle.altitude
        assertTrue("pinch close must zoom out: $before -> $after", after > before * 1.5f)
    }

    @Test
    fun oneFingerDragPans() {
        show()
        val before = handle.centerLon
        globe().performTouchInput {
            val c = center
            down(0, c)
            repeat(20) { step ->
                updatePointerTo(0, c + Offset(15f * (step + 1), 0f))
                move(16)
            }
            up(0)
        }
        rule.waitForIdle()
        assertTrue(
            "a 300 px drag must turn the globe: $before -> ${handle.centerLon}",
            abs(handle.centerLon - before) > 1f,
        )
    }

    @Test
    fun twoFingerTapMovesNothing() {
        show()
        val altitude = handle.altitude
        val lon = handle.centerLon
        val lat = handle.centerLat
        globe().performTouchInput {
            val c = center
            down(0, c - Offset(150f, 0f))
            advanceEventTime(30)
            down(1, c + Offset(150f, 0f))
            advanceEventTime(50)
            up(0)
            up(1)
        }
        rule.waitForIdle()
        // The first build fed the centroid jump of the second finger to the
        // velocity tracker and released it as a fling at a speed nobody moved at.
        assertEquals(altitude, handle.altitude, 1e-4f)
        assertEquals(lon, handle.centerLon, 1e-3f)
        assertEquals(lat, handle.centerLat, 1e-3f)
    }

    @Test
    fun nestedVerticalScrollReleasesVerticalDragsAndKeepsHorizontalOnes() {
        show(nestedVerticalScroll = true)
        val lat = handle.centerLat
        globe().performTouchInput {
            val c = center
            down(0, c)
            repeat(20) { step ->
                updatePointerTo(0, c + Offset(0f, 15f * (step + 1)))
                move(16)
            }
            up(0)
        }
        rule.waitForIdle()
        assertEquals("a vertical drag belongs to the page", lat, handle.centerLat, 1e-3f)

        val lon = handle.centerLon
        globe().performTouchInput {
            val c = center
            down(0, c)
            repeat(20) { step ->
                updatePointerTo(0, c + Offset(15f * (step + 1), 0f))
                move(16)
            }
            up(0)
        }
        rule.waitForIdle()
        assertTrue("a horizontal drag is still the globe's", abs(handle.centerLon - lon) > 1f)
    }
}
