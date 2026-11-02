package com.github.daanbouwman.flightplanner.mapscene

import com.github.daanbouwman.flightplanner.routing.MapFrame
import com.github.daanbouwman.flightplanner.routing.RouteArc
import io.kotest.matchers.floats.shouldBeGreaterThan
import io.kotest.matchers.floats.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlin.math.abs
import kotlin.test.Test

/**
 * The two pure decisions [routeMapScene] makes before it draws: whether a leg
 * is too short for two markers and a head, and where those marks land. Seen on
 * a device as a 66 NM hop whose departure ring, destination dot and arrowhead
 * collapsed into one blob at the middle of the card.
 *
 * Moved here from `:core:designsystem` with the scene it tests, assertions
 * unchanged; the figures are still the phone's route card. The watch pins the
 * same rule at its own face and density in `:wear`'s `WatchPlaneTest`.
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

        // Schiphol to Rotterdam: the minimum span floors the frame at 15° of
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

    // An A320-sized outline in centimetres: a little longer than it is wide.
    private val spanCm = 3579
    private val lengthCm = 3773
    private val planePx = PlaneLengthDp * density

    private fun place(projected: FloatArray) =
        planePlacement(projected, projected.size / 4, width, height, spanCm, lengthCm, planePx)

    @Test
    fun `the nose points along the track, clockwise from up`() {
        // Three samples, the middle one the midpoint: straight up, right, down
        // and left across most of the card.
        val north = place(floatArrayOf(0.5f, 0.9f, 0.5f, 0.5f, 0.5f, 0.1f))!!
        val east = place(floatArrayOf(0.1f, 0.5f, 0.5f, 0.5f, 0.9f, 0.5f))!!
        val south = place(floatArrayOf(0.5f, 0.1f, 0.5f, 0.5f, 0.5f, 0.9f))!!
        val west = place(floatArrayOf(0.9f, 0.5f, 0.5f, 0.5f, 0.1f, 0.5f))!!

        abs(north.degrees) shouldBeLessThan 1e-3f
        abs(east.degrees - 90f) shouldBeLessThan 1e-3f
        abs(abs(south.degrees) - 180f) shouldBeLessThan 1e-3f
        abs(west.degrees + 90f) shouldBeLessThan 1e-3f
    }

    @Test
    fun `the box is centred on the midpoint and its longer side is the plane length`() {
        val placed = place(floatArrayOf(0.1f, 0.5f, 0.5f, 0.4f, 0.9f, 0.5f))!!

        abs(placed.x - 0.5f * width) shouldBeLessThan 1e-3f
        abs(placed.y - 0.4f * height) shouldBeLessThan 1e-3f
        abs(placed.pivotX - spanCm / 2f) shouldBeLessThan 1e-3f
        abs(placed.pivotY - lengthCm / 2f) shouldBeLessThan 1e-3f
        abs(lengthCm * placed.scale - planePx) shouldBeLessThan 1e-3f
    }

    @Test
    fun `Amsterdam to Madrid carries the aircraft, heading south-west`() {
        val leg = RouteArc.sampleGeographic(52.31, 4.76, 40.47, -3.56, samples = RouteArc.CARD_SAMPLES)
        val frame = MapFrame.forRoute(leg.lats, leg.lons, aspect = (width / height).toDouble())
        val placed = place(frame.project(leg.lats, leg.lons))!!

        // More south than west on the card: between due south and south-west.
        placed.degrees shouldBeLessThan -135f
        placed.degrees shouldBeGreaterThan -180f
    }

    @Test
    fun `Amsterdam to London carries the aircraft`() {
        // About 200 NM. Under the first rule (3.6 plane lengths) this kept its
        // arrowhead, and on a device that read as an aircraft with no icon.
        val leg = RouteArc.sampleGeographic(52.31, 4.76, 51.48, -0.46, samples = RouteArc.CARD_SAMPLES)
        val frame = MapFrame.forRoute(leg.lats, leg.lons, aspect = (width / height).toDouble())

        place(frame.project(leg.lats, leg.lons)) shouldNotBe null
    }

    @Test
    fun `Amsterdam to Brussels carries the aircraft at the 15 degree floor`() {
        // About 90 NM. At the old 25° floor this kept its arrowhead; the tighter
        // floor gives it two-thirds more card, which is room for the plane.
        val leg = RouteArc.sampleGeographic(52.31, 4.76, 50.90, 4.48, samples = RouteArc.CARD_SAMPLES)
        val frame = MapFrame.forRoute(leg.lats, leg.lons, aspect = (width / height).toDouble())

        place(frame.project(leg.lats, leg.lons)) shouldNotBe null
    }

    @Test
    fun `Amsterdam to Eindhoven keeps its arrowhead, its ends would sit under the wings`() {
        // About 55 NM: long enough for a head, short of the plane and two markers.
        val leg = RouteArc.sampleGeographic(52.31, 4.76, 51.45, 5.37, samples = RouteArc.CARD_SAMPLES)
        val frame = MapFrame.forRoute(leg.lats, leg.lons, aspect = (width / height).toDouble())
        val projected = frame.project(leg.lats, leg.lons)

        projectedChord(projected, width, height) shouldBeGreaterThan MinArrowChordDp * density
        projectedChord(projected, width, height) shouldBeLessThan MinPlaneChords * planePx
        place(projected) shouldBe null
    }

    @Test
    fun `no heading or no outline means no plane`() {
        val still = RouteArc.sampleGeographic(52.31, 4.76, 52.31, 4.76, samples = RouteArc.CARD_SAMPLES)
        val frame = MapFrame.forRoute(still.lats, still.lons, aspect = 2.0)
        place(frame.project(still.lats, still.lons)) shouldBe null

        val leg = floatArrayOf(0.1f, 0.5f, 0.5f, 0.5f, 0.9f, 0.5f)
        planePlacement(leg, 1, width, height, 0, lengthCm, planePx) shouldBe null
        planePlacement(leg, 1, width, height, spanCm, lengthCm, 0f) shouldBe null
    }
}
