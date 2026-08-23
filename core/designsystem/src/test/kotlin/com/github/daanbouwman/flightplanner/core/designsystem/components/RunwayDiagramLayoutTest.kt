package com.github.daanbouwman.flightplanner.core.designsystem.components

import com.github.daanbouwman.flightplanner.model.Runway
import com.github.daanbouwman.flightplanner.model.SurfaceKind
import io.kotest.matchers.doubles.shouldBeGreaterThan
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

    private fun runway(id: Int, heading: Double, lengthFt: Int = 10_000, ident: String = "$id") = Runway(
        id = id,
        airportId = 0,
        ident = ident,
        trueHeadingDeg = heading,
        lengthFt = lengthFt,
        widthFt = 150,
        surface = "ASPH",
        surfaceKind = SurfaceKind.HARD,
        latitude = null,
        longitude = null,
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
