package com.github.daanbouwman.flightplanner.core.designsystem.components

import com.github.daanbouwman.flightplanner.routing.MapFrame
import com.github.daanbouwman.flightplanner.routing.RouteArc
import io.kotest.matchers.floats.shouldBeGreaterThan
import io.kotest.matchers.floats.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlin.math.abs
import kotlin.test.Test

/**
 * The two pure decisions [RouteMap] makes before it draws: whether a leg is
 * too short for two markers and a head, and where those marks land. Seen on
 * a device as a 66 NM hop whose departure ring, destination dot and arrowhead
 * collapsed into one blob at the middle of the card.
 */
class RouteMapGeometryTest {

    /** A route card's canvas at 3× density: 328 × 180 dp. */
    private val width = 984f
    private val height = 540f
    private val density = 3f

    @Test
    fun `the chord is the distance between the ends, whatever the arc does between them`() {
        // Amsterdam to Tokyo bows far north of both ends; the chord ignores that.
        val arc = RouteArc.sampleGeographic(52.31, 4.76, 35.55, 139.78, samples = RouteArc.CARD_SAMPLES)
        val frame = MapFrame.forRoute(arc.lats, arc.lons, aspect = (width / height).toDouble())
        val projected = frame.project(arc.lats, arc.lons)

        val chord = projectedChord(projected, width, height)

        val expectedX = (projected[projected.size - 2] - projected[0]) * width
        val expectedY = (projected[projected.size - 1] - projected[1]) * height
        abs(chord - kotlin.math.hypot(expectedX, expectedY)) shouldBeLessThan 1e-3f
        // A long haul spans most of the card.
        chord shouldBeGreaterThan width * 0.6f
    }

    @Test
    fun `a 30 NM hop is short and a 300 NM leg is not`() {
        val threshold = MinArrowChordDp * density

        // Schiphol to Rotterdam: the minimum span floors the frame at 25° of
        // longitude, so 30 NM is a few pixels.
        val hop = RouteArc.sampleGeographic(52.31, 4.76, 51.96, 4.44, samples = RouteArc.CARD_SAMPLES)
        val hopFrame = MapFrame.forRoute(hop.lats, hop.lons, aspect = (width / height).toDouble())
        projectedChord(hopFrame.project(hop.lats, hop.lons), width, height) shouldBeLessThan threshold

        // Schiphol to London: framed at the same floor, and clearly two places.
        val leg = RouteArc.sampleGeographic(52.31, 4.76, 51.48, -0.46, samples = RouteArc.CARD_SAMPLES)
        val legFrame = MapFrame.forRoute(leg.lats, leg.lons, aspect = (width / height).toDouble())
        projectedChord(legFrame.project(leg.lats, leg.lons), width, height) shouldBeGreaterThan threshold
    }

    @Test
    fun `a degenerate projection has no chord and no arrowhead`() {
        projectedChord(FloatArray(0), width, height) shouldBe 0f
        projectedChord(floatArrayOf(0.5f, 0.5f), width, height) shouldBe 0f

        // A route to itself: every sample at one point, so no heading.
        val still = RouteArc.sampleGeographic(52.31, 4.76, 52.31, 4.76, samples = RouteArc.CARD_SAMPLES)
        val frame = MapFrame.forRoute(still.lats, still.lons, aspect = 2.0)
        val projected = frame.project(still.lats, still.lons)
        projectedChord(projected, width, height) shouldBeLessThan 1e-3f
        arrowPath(projected, projected.size / 4, width, height, ArrowLengthDp * density) shouldBe null
    }

    @Test
    fun `a leg long enough for a head gets one, pointing along the arc`() {
        val leg = RouteArc.sampleGeographic(52.31, 4.76, 51.48, -0.46, samples = RouteArc.CARD_SAMPLES)
        val frame = MapFrame.forRoute(leg.lats, leg.lons, aspect = (width / height).toDouble())
        val projected = frame.project(leg.lats, leg.lons)

        arrowPath(projected, projected.size / 4, width, height, ArrowLengthDp * density) shouldNotBe null
    }
}
