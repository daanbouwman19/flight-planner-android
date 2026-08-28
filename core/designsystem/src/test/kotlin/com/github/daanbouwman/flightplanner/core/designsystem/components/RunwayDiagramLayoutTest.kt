package com.github.daanbouwman.flightplanner.core.designsystem.components

import androidx.compose.ui.geometry.Offset
import com.github.daanbouwman.flightplanner.model.Runway
import com.github.daanbouwman.flightplanner.model.SurfaceKind
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.floats.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlin.math.abs
import kotlin.test.Test

/**
 * [layoutRunways] exists because drawing every runway through one shared
 * centre point — the first version of [RunwayDiagram] — looked like a
 * starburst for any airport with near-parallel or identical-heading
 * runways, which in reality run *beside* each other rather than through one
 * point. Found on-device against Edwards Air Force Base (three runways at
 * 050°/058°/077°, all crossing at a single point that doesn't exist in
 * reality) and Denver (twelve ends at just four headings, fully
 * overlapping).
 */
class RunwayDiagramLayoutTest {

    private fun runway(
        id: Int,
        heading: Double,
        lengthFt: Int = 10_000,
        ident: String = "$id",
        lat: Double? = null,
        lon: Double? = null,
    ) = Runway(
        id = id,
        airportId = 0,
        ident = ident,
        trueHeadingDeg = heading,
        lengthFt = lengthFt,
        widthFt = 150,
        surface = "ASPH",
        surfaceKind = SurfaceKind.HARD,
        latitude = lat,
        longitude = lon,
        elevationFt = 0,
        lighted = false,
    )

    @Test
    fun `a single physical runway needs no lane`() {
        val runways = listOf(runway(1, 90.0), runway(2, 270.0))

        layoutRunways(runways) shouldBe listOf(0.0, 0.0)
    }

    @Test
    fun `one runway alone is left at offset zero`() {
        layoutRunways(listOf(runway(1, 90.0))) shouldBe listOf(0.0)
    }

    @Test
    fun `two runways that plausibly cross stay through the centre`() {
        // 90° apart — well outside the "roughly parallel" band.
        val runways = listOf(runway(1, 0.0), runway(2, 180.0), runway(3, 90.0), runway(4, 270.0))

        layoutRunways(runways) shouldBe listOf(0.0, 0.0, 0.0, 0.0)
    }

    @Test
    fun `edwards-shaped input fans three near-parallel runways into distinct lanes`() {
        // 050/230, 058/238, 077/257 — the exact case that looked wrong on
        // Edwards Air Force Base: three physical runways all crossing one
        // point that isn't real.
        val runways = listOf(
            runway(1, 58.0), runway(2, 238.0),
            runway(3, 50.0), runway(4, 230.0),
            runway(5, 77.0), runway(6, 257.0),
        )

        val offsets = layoutRunways(runways)

        // Both ends of one physical strip share a lane.
        offsets[0] shouldBe offsets[1]
        offsets[2] shouldBe offsets[3]
        offsets[4] shouldBe offsets[5]
        // Three distinct physical runways, three distinct lanes.
        setOf(offsets[0], offsets[2], offsets[4]).size shouldBe 3
        // Centred: the sum of the three physical runways' lanes is zero.
        (offsets[0] + offsets[2] + offsets[4]) shouldBe 0.0
    }

    @Test
    fun `identical headings still separate into distinct lanes`() {
        // Denver's own shape: four physical runways, all at true north.
        val runways = listOf(
            runway(1, 0.0, ident = "34L"), runway(2, 180.0, ident = "16R"),
            runway(3, 0.0, ident = "34R"), runway(4, 180.0, ident = "16L"),
            runway(5, 0.0, ident = "35L"), runway(6, 180.0, ident = "17R"),
            runway(7, 0.0, ident = "35R"), runway(8, 180.0, ident = "17L"),
        )

        val offsets = layoutRunways(runways)

        val physicalRunwayLanes = setOf(offsets[0], offsets[2], offsets[4], offsets[6])
        physicalRunwayLanes.size shouldBe 4
        offsets[0] shouldBe offsets[1]
        offsets[2] shouldBe offsets[3]
    }

    @Test
    fun `identical-heading parallel runways pair by real position, not arrival order`() {
        // Zahedan International (OIZH), found live on-device: two parallel
        // physical runways, 17R-35L (west) and 17L-35R (east), both 14,042 ft
        // and sharing the same heading pair (173/353). RunwayDao orders
        // same-length rows alphabetically by ident, so they arrive as
        // 17L, 17R, 35L, 35R — not in true-pair order — and heading+length
        // alone cannot tell 35L from 35R as 17L's partner. Real threshold
        // coordinates (OurAirports, verified against a Jeppesen chart) are
        // what settles it.
        val seventeenLeft = runway(1, 173.0, 14_042, ident = "17L", lat = 29.4949, lon = 60.904701)
        val seventeenRight = runway(2, 173.0, 14_042, ident = "17R", lat = 29.494642, lon = 60.902241)
        val thirtyFiveLeft = runway(3, 353.0, 14_042, ident = "35L", lat = 29.458872, lon = 60.907368)
        val thirtyFiveRight = runway(4, 353.0, 14_042, ident = "35R", lat = 29.458836, lon = 60.909866)
        val runways = listOf(seventeenLeft, seventeenRight, thirtyFiveLeft, thirtyFiveRight)

        val groups = pairPhysicalRunways(runways)

        // 17L's real reciprocal is 35R (index 3), not 35L (index 2) — the
        // first opposite-heading, same-length candidate in arrival order,
        // which is what the pre-fix greedy match picked and drew as a cross.
        groups.first { 0 in it.runwayIndices }.runwayIndices shouldBe listOf(0, 3)
        groups.first { 1 in it.runwayIndices }.runwayIndices shouldBe listOf(1, 2)
    }

    @Test
    fun `two independent parallel families are laned separately`() {
        // Denver's other axis: two east-west runways, alongside the four
        // north-south ones, at a heading far enough apart not to merge.
        val runways = listOf(
            runway(1, 0.0), runway(2, 180.0),
            runway(3, 5.0), runway(4, 185.0),
            runway(5, 90.0), runway(6, 270.0),
            runway(7, 95.0), runway(8, 275.0),
        )

        val offsets = layoutRunways(runways)

        setOf(offsets[0], offsets[2]).size shouldBe 2
        setOf(offsets[4], offsets[6]).size shouldBe 2
    }

    @Test
    fun `mismatched lengths do not pair as one physical runway`() {
        // Opposite headings, wildly different lengths: not the two ends of
        // one strip — two distinct physical runways that happen to share an
        // orientation, and pairing must not force them into one strip whose
        // "two ends" disagree on length. They still end up laned apart from
        // each other, which is correct: two distinct runways at the same
        // orientation should not draw through the identical point either.
        val runways = listOf(runway(1, 90.0, lengthFt = 12_000), runway(2, 270.0, lengthFt = 2_000))

        val offsets = layoutRunways(runways)

        offsets[0] shouldNotBe offsets[1]
    }

    @Test
    fun `an unpaired end becomes its own physical runway`() {
        // Only one end published a heading — the other is real on a grass
        // strip and would already have been filtered out by the caller.
        val runways = listOf(runway(1, 58.0), runway(2, 50.0), runway(3, 230.0))

        val offsets = layoutRunways(runways)

        // Three physical runways (58 alone, 50/230 paired), all within the
        // parallel band of each other, so three distinct lanes.
        setOf(offsets[0], offsets[1]).size shouldBe 2
        offsets[1] shouldBe offsets[2]
    }
}

/**
 * [projectLocal] is what makes [RunwayDiagram] draw a genuine miniature of
 * the airport — a runway's real threshold coordinates, projected onto a
 * small local plane — rather than the compass schematic, whenever the
 * dataset has them. A sign flipped here would silently mirror or invert
 * every positioned diagram, so the sign conventions get their own tests
 * rather than being trusted from reading the code once.
 */
class RunwayDiagramProjectionTest {

    private val amsterdam = 52.31 // roughly Schiphol's own latitude

    @Test
    fun `the reference point projects to the origin`() {
        val p = projectLocal(lat = amsterdam, lon = 4.76, refLat = amsterdam, refLon = 4.76)

        p.x shouldBe 0f
        p.y shouldBe 0f
    }

    @Test
    fun `a point north of the reference has negative y, matching screen up`() {
        val p = projectLocal(lat = amsterdam + 0.01, lon = 4.76, refLat = amsterdam, refLon = 4.76)

        p.y.toDouble() shouldBeLessThan 0.0
        p.x shouldBe 0f
    }

    @Test
    fun `a point south of the reference has positive y, matching screen down`() {
        val p = projectLocal(lat = amsterdam - 0.01, lon = 4.76, refLat = amsterdam, refLon = 4.76)

        p.y.toDouble() shouldBeGreaterThan 0.0
    }

    @Test
    fun `a point east of the reference has positive x`() {
        val p = projectLocal(lat = amsterdam, lon = 4.76 + 0.01, refLat = amsterdam, refLon = 4.76)

        p.x.toDouble() shouldBeGreaterThan 0.0
    }

    @Test
    fun `a point west of the reference has negative x`() {
        val p = projectLocal(lat = amsterdam, lon = 4.76 - 0.01, refLat = amsterdam, refLon = 4.76)

        p.x.toDouble() shouldBeLessThan 0.0
    }

    @Test
    fun `the same longitude delta is compressed more at higher latitude`() {
        // A degree of longitude spans less real distance the further from
        // the equator it is measured — the whole reason the projection
        // multiplies by cos(latitude) rather than treating lat and lon the
        // same way MapFrame's own KDoc explains for the world map.
        val nearEquator = projectLocal(lat = 5.0, lon = 5.01, refLat = 5.0, refLon = 5.0)
        val nearPole = projectLocal(lat = 80.0, lon = 5.01, refLat = 80.0, refLon = 5.0)

        abs(nearPole.x.toDouble()) shouldBeLessThan abs(nearEquator.x.toDouble())
    }

    @Test
    fun `distances are preserved well enough to reconstruct a known separation`() {
        // Two points 0.01 deg latitude apart, at Amsterdam's own latitude,
        // are about 1,112 m apart on the ground (111,320 m per degree).
        val a = projectLocal(lat = amsterdam, lon = 4.76, refLat = amsterdam, refLon = 4.76)
        val b = projectLocal(lat = amsterdam + 0.01, lon = 4.76, refLat = amsterdam, refLon = 4.76)

        val distance = kotlin.math.hypot((b.x - a.x).toDouble(), (b.y - a.y).toDouble())
        distance shouldBeGreaterThan 1_100.0
        distance shouldBeLessThan 1_125.0
    }
}

/**
 * Where a runway's designator is drawn.
 *
 * Every real airport diagram paints it at the **threshold** — the end you cross
 * on landing and start the roll from on takeoff. [RunwayDiagram] painted it at
 * the ray tip, which in both layouts is the end the runway *points toward*, so
 * every ident sat at the wrong end of its own strip. Invisible until the
 * favoured-end highlight arrived: a pilot then read `30` at the north-west end,
 * applied the convention, and concluded that departing 30 meant rolling
 * south-east — downwind, and the opposite of what `SurfaceWind` had chosen.
 *
 * The two layouts reach the threshold differently, so both are asserted here.
 * The property they share is the one that would have caught the original defect:
 * **an ident's anchor is on the opposite side of the strip's midpoint from its
 * own ray tip.**
 */
class RunwayIdentAnchorTest {

    private val center = Offset(100f, 100f)
    private val radius = 80f

    private fun runway(
        id: Int,
        heading: Double,
        ident: String,
        lat: Double? = null,
        lon: Double? = null,
    ) = Runway(
        id = id,
        airportId = 0,
        ident = ident,
        trueHeadingDeg = heading,
        lengthFt = 10_000,
        widthFt = 150,
        surface = "ASPH",
        surfaceKind = SurfaceKind.HARD,
        latitude = lat,
        longitude = lon,
        elevationFt = 0,
        lighted = false,
    )

    /** The signed position of [point] along the strip, measured from its midpoint. */
    private fun alongStrip(ray: RunwayRay, point: Offset): Float {
        val midpoint = Offset((ray.origin.x + ray.tip.x) / 2f, (ray.origin.y + ray.tip.y) / 2f)
        val axis = ray.tip - ray.origin
        val length = kotlin.math.hypot(axis.x, axis.y)
        val unit = Offset(axis.x / length, axis.y / length)
        val delta = point - midpoint
        return delta.x * unit.x + delta.y * unit.y
    }

    /**
     * The ident is behind the strip's midpoint and the tip is in front of it.
     *
     * **What this does and does not catch.** It bites hardest where the threshold
     * is *computed* — the lane-schematic mirror, which lands 1.5 strip-lengths
     * behind the midpoint and could easily be reflected the wrong way. Where the
     * threshold is simply the origin, as it is for every unpaired end and for a
     * positioned paired one, it holds by construction and proves only that the
     * value was not set to the tip. Those cases assert `threshold shouldBe origin`
     * directly, which is the claim that actually matters for them; this is kept
     * alongside as a cheap guard on the shared shape.
     */
    private fun assertOppositeSideOfMidpoint(ray: RunwayRay) {
        val tipSide = alongStrip(ray, ray.tip)
        val thresholdSide = alongStrip(ray, ray.threshold)

        // Not merely "different": strictly opposite signs, so a threshold that
        // crept to the same half of the strip as its tip fails even if the two
        // are not identical.
        (tipSide > 0f) shouldBe true
        (thresholdSide < 0f) shouldBe true
    }

    @Test
    fun `in the positioned layout an ident sits at its own published threshold`() {
        // A paired runway drawn between two real threshold coordinates. `09`'s
        // own coordinate is the western one; it points east, so its ray tips at
        // `27`'s threshold and its ident must stay in the west.
        val west = 4.740
        val east = 4.780
        val lat = 52.3166
        val runways = listOf(
            runway(1, heading = 87.0, ident = "09", lat = lat, lon = west),
            runway(2, heading = 267.0, ident = "27", lat = lat, lon = east),
        )
        val rays = positionedRays(
            runways = runways,
            groups = pairPhysicalRunways(runways),
            center = center,
            radius = radius,
            maxLengthFt = 10_000,
        )

        rays.forEach(::assertOppositeSideOfMidpoint)
        // And concretely: 09's designator is west of 27's, which is the whole
        // claim a reader takes off the diagram.
        (rays[0].threshold.x < rays[1].threshold.x) shouldBe true
        // The strip itself is unchanged — only the label moved.
        rays[0].origin shouldBe rays[1].tip
        rays[0].tip shouldBe rays[1].origin
    }

    @Test
    fun `an unpaired positioned stub anchors at the one real point it has`() {
        val runways = listOf(runway(1, heading = 90.0, ident = "09", lat = 52.3, lon = 4.74))
        val rays = positionedRays(
            runways = runways,
            groups = pairPhysicalRunways(runways),
            center = center,
            radius = radius,
            maxLengthFt = 10_000,
        )

        // The published coordinate *is* the threshold, so the stub's origin
        // carries the ident and the ray runs away from it.
        rays.single().threshold shouldBe rays.single().origin
        assertOppositeSideOfMidpoint(rays.single())
    }

    @Test
    fun `in the lane schematic the tip is mirrored, because both ends share an origin`() {
        // The case `ray.origin` cannot serve: 12 and 30 are one strip and are
        // laid out from the same lane point, so an origin-anchored ident would
        // stack both designators on top of each other in the middle.
        val twelve = laneRay(runway(1, 120.0, "12"), laneOffsetPx = 0.0, center = center, radius = radius, maxLengthFt = 10_000, paired = true)
        val thirty = laneRay(runway(2, 300.0, "30"), laneOffsetPx = 0.0, center = center, radius = radius, maxLengthFt = 10_000, paired = true)

        twelve.origin shouldBe thirty.origin
        twelve.threshold shouldNotBe thirty.threshold

        assertOppositeSideOfMidpoint(twelve)
        assertOppositeSideOfMidpoint(thirty)

        // Mirroring puts each designator where its opposite number's ray tips,
        // which is exactly the threshold a printed diagram paints it at.
        abs((twelve.threshold - thirty.tip).x).toDouble() shouldBeLessThan 0.01
        abs((twelve.threshold - thirty.tip).y).toDouble() shouldBeLessThan 0.01
    }

    @Test
    fun `an unpaired lane stub anchors at its origin, not at a mirrored phantom`() {
        // The gap the paired case hid. `pairPhysicalRunways` makes a physical
        // runway of one whenever the reciprocal end has no published heading, and
        // only the origin-to-tip half is then ever drawn — so mirroring the tip
        // through the origin puts the designator a full strip length out on bare
        // canvas, detached from its own line and possibly over a neighbour's.
        //
        // Both calls are the same runway; only `paired` differs. That is the
        // whole test: the flag has to change the answer.
        val stub = runway(1, 90.0, "09")
        val unpaired = laneRay(stub, laneOffsetPx = 0.0, center = center, radius = radius, maxLengthFt = 10_000, paired = false)
        val mirrored = laneRay(stub, laneOffsetPx = 0.0, center = center, radius = radius, maxLengthFt = 10_000, paired = true)

        unpaired.threshold shouldBe unpaired.origin
        mirrored.threshold shouldNotBe mirrored.origin
        // And the strip drawn is identical either way — this moves the label only.
        unpaired.origin shouldBe mirrored.origin
        unpaired.tip shouldBe mirrored.tip
    }

    @Test
    fun `a lane offset displaces both designators without swapping their ends`() {
        // The offset moves the whole strip sideways; it must not move a
        // designator along it.
        val offset = laneRay(runway(1, 120.0, "12"), laneOffsetPx = 12.0, center = center, radius = radius, maxLengthFt = 10_000, paired = true)

        assertOppositeSideOfMidpoint(offset)
    }
}

/**
 * The windsock's drag — the app's first custom gesture.
 *
 * The physics is here rather than in the composable for the reason everything
 * testable in this module is: `:core:designsystem` has JVM tests only, so a
 * function can be asserted where the same arithmetic inside a `pointerInput`
 * could only be felt.
 */
class WindsockDragTest {

    @Test
    fun `the resistance is the wind`() {
        // The whole reason this is a function of speed. A sock held out straight by
        // 25 kt is a stiff thing to push; one hanging limp in 3 kt swings with a
        // finger. Same drag, different swing — so the object under the finger feels
        // like the object on the field.
        val calm = sockDragOffsetDeg(dragPx = 60f, windSpeedKt = 0)
        val breezy = sockDragOffsetDeg(dragPx = 60f, windSpeedKt = 12)
        val gale = sockDragOffsetDeg(dragPx = 60f, windSpeedKt = 45)

        calm shouldBeGreaterThan breezy
        breezy shouldBeGreaterThan gale
    }

    @Test
    fun `even a gale still moves, because a control that does not respond reads as broken`() {
        // The floor, and why it exists: without it a 60 kt report would pin the sock
        // so hard the gesture would be indistinguishable from an absent one.
        sockDragOffsetDeg(dragPx = 200f, windSpeedKt = 90) shouldBeGreaterThan 5f
    }

    @Test
    fun `the swing is clamped, so the sock can never show the opposite quarter`() {
        // A sock wound right round stops reading as a sock and starts reading as a
        // dial — and a swing past 180 would briefly draw the wind coming from the
        // wrong side, which is the one thing this component must not do.
        sockDragOffsetDeg(dragPx = 100_000f, windSpeedKt = 5) shouldBe SockDragLimitDeg
        sockDragOffsetDeg(dragPx = -100_000f, windSpeedKt = 5) shouldBe -SockDragLimitDeg
        SockDragLimitDeg.toDouble() shouldBeLessThan 180.0
    }

    @Test
    fun `the drag is symmetric and rests at the truth`() {
        // It springs back to zero, and zero is the reported direction — so the toy
        // cannot leave a false reading on screen.
        sockDragOffsetDeg(dragPx = 0f, windSpeedKt = 10) shouldBe 0f
        sockDragOffsetDeg(dragPx = 40f, windSpeedKt = 10) shouldBe
            -sockDragOffsetDeg(dragPx = -40f, windSpeedKt = 10)
    }
}
