package com.github.daanbouwman.flightplanner.routing

import io.kotest.matchers.doubles.shouldBeGreaterThanOrEqual
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.floats.shouldBeGreaterThan
import io.kotest.matchers.floats.shouldBeLessThan
import io.kotest.matchers.shouldBe
import kotlin.math.abs
import kotlin.math.cos
import kotlin.test.Test

/**
 * The frame is the one thing the coastline and the arc share, so everything that
 * can go wrong with it goes wrong *silently*: land is drawn in the right shape
 * around the wrong place, or the route runs off a card that framed the ocean
 * beside it. None of that throws, and none of it is visible in a unit test of
 * either layer alone — only in what the two agree on, which is this.
 */
class MapFrameTest {

    private val amsterdam = 52.31 to 4.76
    private val london = 51.48 to -0.46
    private val tokyo = 35.55 to 139.78
    private val losAngeles = 33.94 to -118.41
    private val sydney = -33.95 to 151.18
    private val oslo = 60.19 to 11.10
    private val rome = 41.80 to 12.24
    private val singapore = 1.35 to 103.99
    private val nairobi = -1.32 to 36.93

    @Test
    fun `the window is fitted to the canvas, so a degree is the same size on both axes`() {
        val arc = arc(amsterdam, tokyo)

        for (aspect in listOf(0.5, 1.0, 2.0, 3.0)) {
            val frame = MapFrame.forRoute(arc.lats, arc.lons, aspect)
            val lonScale = cos(Math.toRadians(frame.centreLat))
            val projectedAspect = frame.spanLon * lonScale / frame.spanLat

            abs(projectedAspect - aspect) shouldBeLessThan 1e-9
        }
    }

    @Test
    fun `a short hop is framed at the minimum span rather than magnified`() {
        val arc = arc(amsterdam, london)

        val frame = MapFrame.forRoute(arc.lats, arc.lons, aspect = 2.0)

        frame.spanLon shouldBeGreaterThanOrEqual MapFrame.MIN_SPAN_DEGREES
        // Wide enough to hold the North Sea and the Channel, not the hemisphere.
        frame.spanLon shouldBeLessThan MapFrame.MIN_SPAN_DEGREES * 1.01
    }

    @Test
    fun `a long haul is framed by the route, not by the floor`() {
        val arc = arc(amsterdam, tokyo)

        val frame = MapFrame.forRoute(arc.lats, arc.lons, aspect = 2.0)

        frame.spanLon shouldBeGreaterThanOrEqual 135.0
    }

    @Test
    fun `both endpoints land inside the card, clear of its edges`() {
        val routes = listOf(
            amsterdam to london,
            amsterdam to tokyo,
            tokyo to losAngeles,
            sydney to losAngeles,
            amsterdam to sydney,
        )

        for ((departure, destination) in routes) {
            val arc = arc(departure, destination)
            val frame = MapFrame.forRoute(arc.lats, arc.lons, aspect = 2.0)
            val projected = frame.project(arc.lats, arc.lons)

            for (i in 0 until projected.size / 2) {
                projected[i * 2] shouldBeGreaterThan 0f
                projected[i * 2] shouldBeLessThan 1f
                projected[i * 2 + 1] shouldBeGreaterThan 0f
                projected[i * 2 + 1] shouldBeLessThan 1f
            }
        }
    }

    /**
     * A Pacific crossing is sampled as longitudes running past 180°, so its frame
     * is centred somewhere around 190°E — a place no coordinate in the outline is
     * stored at. Getting this wrong draws an empty ocean over the one route where
     * the land either side is the whole point.
     */
    @Test
    fun `a window past the seam is met by land stored on the other side of it`() {
        val arc = arc(tokyo, losAngeles)
        val frame = MapFrame.forRoute(arc.lats, arc.lons, aspect = 2.0)
        val outline = outlineOf(
            square("Japan", centreLon = 138.0, centreLat = 36.0, halfSize = 4.0),
            square("California", centreLon = -120.0, centreLat = 36.0, halfSize = 4.0),
            square("Brazil", centreLon = -50.0, centreLat = -10.0, halfSize = 8.0),
        )

        frame.maxLon shouldBeGreaterThanOrEqual 180.0
        val land = frame.projectOutline(outline)

        // Japan and California, each shifted into the window; Brazil dropped.
        land.fill.ringCount shouldBe 2
        (land.coast.ringCount >= 2) shouldBe true
    }

    @Test
    fun `rings outside the window are dropped`() {
        val arc = arc(amsterdam, london)
        val frame = MapFrame.forRoute(arc.lats, arc.lons, aspect = 2.0)
        val outline = outlineOf(
            square("Europe", centreLon = 4.0, centreLat = 51.0, halfSize = 6.0),
            square("Australia", centreLon = 134.0, centreLat = -25.0, halfSize = 12.0),
            square("Greenland", centreLon = -42.0, centreLat = 72.0, halfSize = 10.0),
        )

        val land = frame.projectOutline(outline)

        land.fill.ringCount shouldBe 1
    }

    /**
     * The point of clipping. Before it, a card showing a European hop still filled
     * and stroked every point of the ring it sits on, and trusted the canvas to
     * throw the rest away — about 9 ms a frame on the benchmark build.
     */
    @Test
    fun `a ring far larger than the window is reduced to what the window holds`() {
        val arc = arc(amsterdam, london)
        val frame = MapFrame.forRoute(arc.lats, arc.lons, aspect = 2.0)
        // A ring of a thousand points around the whole northern hemisphere.
        val continent = circle(centreLon = 0.0, centreLat = 45.0, radius = 40.0, points = 1_000)

        val land = frame.projectOutline(outlineOf(continent), margin = 0.05)

        (land.fill.pointCount < 40) shouldBe true
        // Its boundary is nowhere near this window, so there is no coast to draw.
        land.coast.ringCount shouldBe 0
    }

    @Test
    fun `clipped geometry stays inside the window and its margin`() {
        val arc = arc(amsterdam, london)
        val frame = MapFrame.forRoute(arc.lats, arc.lons, aspect = 2.0)
        val margin = 0.05
        val outline = outlineOf(
            square("a coast that runs off the card", centreLon = 4.0, centreLat = 51.0, halfSize = 40.0),
        )

        val land = frame.projectOutline(outline, margin = margin)

        val low = (-margin).toFloat() - 1e-5f
        val high = (1.0 + margin).toFloat() + 1e-5f
        for (layer in listOf(land.fill, land.coast)) {
            for (i in 0 until layer.pointCount) {
                (layer.points[i * 2] in low..high) shouldBe true
                (layer.points[i * 2 + 1] in low..high) shouldBe true
            }
        }
    }

    /**
     * The reason the coast is clipped as open polylines rather than taken from the
     * filled polygon: a clipped polygon's boundary runs along the window's edge,
     * and stroking that draws a hairline box around every card.
     */
    @Test
    fun `the coast never runs along the window's edge`() {
        val arc = arc(amsterdam, london)
        val frame = MapFrame.forRoute(arc.lats, arc.lons, aspect = 2.0)
        // Land covering the left half of the world: its only real boundary inside
        // the window is one vertical line, and the fill has to close around it.
        val outline = outlineOf(
            square("half the world", centreLon = -40.0, centreLat = 51.0, halfSize = 40.0),
        )

        val land = frame.projectOutline(outline)

        // The fill closes along the card's edges...
        land.fill.ringCount shouldBe 1
        // ...and the coast is only the one real edge that crosses the card, so no
        // segment of it lies along the top, bottom or right of the window.
        for (ring in 0 until land.coast.ringCount) {
            for (i in land.coast.ringStart[ring] until land.coast.ringStart[ring + 1] - 1) {
                val onSameEdge = (land.coast.points[i * 2] == land.coast.points[(i + 1) * 2] &&
                    (land.coast.points[i * 2] <= 0f || land.coast.points[i * 2] >= 1f)) ||
                    (land.coast.points[i * 2 + 1] == land.coast.points[(i + 1) * 2 + 1] &&
                        (land.coast.points[i * 2 + 1] <= 0f || land.coast.points[i * 2 + 1] >= 1f))
                onSameEdge shouldBe false
            }
        }
    }

    @Test
    fun `a coast that only touches the window contributes what crosses it`() {
        val frame = MapFrame(centreLon = 0.0, centreLat = 0.0, spanLon = 20.0, spanLat = 10.0)
        // A square whose right edge crosses the window and whose other three
        // edges are far outside it.
        val outline = outlineOf(square("mainland", centreLon = -30.0, centreLat = 0.0, halfSize = 32.0))

        val land = frame.projectOutline(outline)

        land.fill.ringCount shouldBe 1
        land.coast.ringCount shouldBe 1
        // Exactly the crossing edge: entering at one side of the window and
        // leaving at the other.
        land.coast.pointCount shouldBe 2
    }

    @Test
    fun `an empty outline projects to nothing rather than throwing`() {
        val arc = arc(amsterdam, london)
        val frame = MapFrame.forRoute(arc.lats, arc.lons, aspect = 2.0)

        frame.projectOutline(WorldOutline.Empty).isEmpty shouldBe true
    }

    @Test
    fun `the projection is north-up and west-left`() {
        val frame = MapFrame(centreLon = 0.0, centreLat = 0.0, spanLon = 100.0, spanLat = 50.0)

        frame.x(0.0) shouldBe 0.5f
        frame.y(0.0) shouldBe 0.5f
        frame.x(-50.0) shouldBe 0f
        frame.x(50.0) shouldBe 1f
        // North is the top of the card: the higher latitude gets the smaller y.
        frame.y(25.0) shouldBe 0f
        frame.y(-25.0) shouldBe 1f
    }

    /**
     * A window at 80°N would otherwise need 800° of longitude to fill a card,
     * which frames the whole hemisphere to show two airports an hour apart.
     */
    @Test
    fun `a polar window is stretched rather than made unusably wide`() {
        val arc = arc(80.1 to 15.0, 78.9 to 20.0)

        val frame = MapFrame.forRoute(arc.lats, arc.lons, aspect = 2.0)

        frame.spanLon shouldBeLessThan 120.0
    }

    /**
     * The route card prints its title across the top of the map. A north–south
     * leg framed symmetrically put its northern endpoint on the title's
     * baseline; with the inset, nothing of the route is projected into that
     * band, and the map itself still covers it.
     */
    @Test
    fun `a top inset keeps every point of the route below the band`() {
        val inset = 0.3
        val routes = listOf(
            // Tall: fitted by height, so the inset is what places the north end.
            oslo to rome,
            // Wide: fitted by width, so the route sits centred in the band below.
            amsterdam to tokyo,
            amsterdam to london,
            sydney to losAngeles,
        )

        for ((departure, destination) in routes) {
            val arc = arc(departure, destination)
            val frame = MapFrame.forRoute(arc.lats, arc.lons, aspect = 2.0, topInsetFraction = inset)
            val projected = frame.project(arc.lats, arc.lons)

            for (i in 0 until projected.size / 2) {
                // Below the band, and still inside the card with its padding.
                (projected[i * 2 + 1] > inset.toFloat()) shouldBe true
                projected[i * 2 + 1] shouldBeLessThan 1f
                projected[i * 2] shouldBeGreaterThan 0f
                projected[i * 2] shouldBeLessThan 1f
            }
        }
    }

    @Test
    fun `a tall route with a top inset is padded within the band, not the canvas`() {
        val arc = arc(oslo, rome)
        val inset = 0.3

        val frame = MapFrame.forRoute(arc.lats, arc.lons, aspect = 2.0, topInsetFraction = inset)

        // The northernmost sample lands at the band's top plus the band's own
        // padding, not the canvas's: the title is the clearance from the edge,
        // and the padding is the clearance from the title.
        val northmost = arc.lats.indices.minBy { frame.y(arc.lats[it]) }
        val expectedTop = inset + MapFrame.PADDING_FRACTION * (1.0 - inset) / (1.0 + 2.0 * MapFrame.PADDING_FRACTION)
        abs(frame.y(arc.lats[northmost]) - expectedTop) shouldBeLessThan 0.02
    }

    @Test
    fun `a top inset preserves the projected aspect and the width`() {
        val arc = arc(amsterdam, tokyo)

        for (aspect in listOf(0.5, 1.0, 2.0, 3.0)) {
            val plain = MapFrame.forRoute(arc.lats, arc.lons, aspect)
            val inset = MapFrame.forRoute(arc.lats, arc.lons, aspect, topInsetFraction = 0.3)
            // The standard parallel is the *route's* centre latitude, which the
            // symmetric frame is centred on; the inset frame's centre has moved
            // north of it by construction, and that is not where the scale is set.
            val lonScale = cos(Math.toRadians(plain.centreLat))

            abs(inset.spanLon * lonScale / inset.spanLat - aspect) shouldBeLessThan 1e-9
            // Never narrower: the band is the whole width, so a route fitted
            // by width still fills the card edge to edge below the title.
            (inset.spanLon >= plain.spanLon - 1e-9) shouldBe true
            abs(inset.centreLon - plain.centreLon) shouldBeLessThan 1e-9
        }

        // A route fitted by width keeps exactly the symmetric frame's width: an
        // equatorial east–west leg, which barely bows, on a card-shaped canvas.
        // (Amsterdam–Tokyo above bows to 69°N and is fitted by *height* once
        // the band is only seventy percent of the card, so giving up the top
        // widens it — the alternative is a route that no longer fits.)
        val flat = arc(singapore, nairobi)
        val plain = MapFrame.forRoute(flat.lats, flat.lons, aspect = 2.0)
        val inset = MapFrame.forRoute(flat.lats, flat.lons, aspect = 2.0, topInsetFraction = 0.3)
        abs(inset.spanLon - plain.spanLon) shouldBeLessThan 1e-9
    }

    @Test
    fun `no top inset is exactly the symmetric frame`() {
        val arc = arc(amsterdam, tokyo)

        val plain = MapFrame.forRoute(arc.lats, arc.lons, aspect = 2.0)
        val zero = MapFrame.forRoute(arc.lats, arc.lons, aspect = 2.0, topInsetFraction = 0.0)

        zero.centreLat shouldBe plain.centreLat
        zero.centreLon shouldBe plain.centreLon
        zero.spanLat shouldBe plain.spanLat
        zero.spanLon shouldBe plain.spanLon
    }

    @Test
    fun `a route to nowhere still produces a usable window`() {
        val arc = arc(amsterdam, amsterdam)

        val frame = MapFrame.forRoute(arc.lats, arc.lons, aspect = 2.0)

        frame.spanLon shouldBeGreaterThanOrEqual MapFrame.MIN_SPAN_DEGREES
        frame.spanLat shouldBeGreaterThanOrEqual 1.0
        abs(frame.x(arc.lons[0]) - 0.5f) shouldBeLessThan 1e-6f
        abs(frame.y(arc.lats[0]) - 0.5f) shouldBeLessThan 1e-6f
    }


    /**
     * A ring wide enough to reach the window at two different whole turns has to
     * be drawn at both. Antarctica is that ring: it spans −180° to 180°, so a
     * window straddling the seam sees it twice, and emitting only the first match
     * drew the leftmost sliver and dropped the rest.
     */
    @Test
    fun `a ring that reaches the window at two turns is drawn at both`() {
        // A window straddling the seam, as a far-southern Pacific route produces.
        val frame = MapFrame(centreLon = -170.0, centreLat = -70.0, spanLon = 80.0, spanLat = 40.0)
        val antarctica = outlineOf(
            band(minLon = -180.0, maxLon = 180.0, minLat = -85.0, maxLat = -65.0),
        )

        val land = frame.projectOutline(antarctica)

        // Once at shift 0 and once at −360, so the fill covers the window rather
        // than a strip at one edge of it.
        land.fill.ringCount shouldBe 2
        val xs = (0 until land.fill.pointCount).map { land.fill.points[it * 2] }
        (xs.min() < 0.05f) shouldBe true
        (xs.max() > 0.95f) shouldBe true
    }
    private fun arc(departure: Pair<Double, Double>, destination: Pair<Double, Double>): GeoArc =
        RouteArc.sampleGeographic(departure.first, departure.second, destination.first, destination.second)

    /** A closed latitude band spanning a longitude range — Antarctica's shape. */
    private fun band(minLon: Double, maxLon: Double, minLat: Double, maxLat: Double) = doubleArrayOf(
        minLon, minLat,
        maxLon, minLat,
        maxLon, maxLat,
        minLon, maxLat,
        minLon, minLat,
    )

    /** A closed square ring, the shape of a country as far as a frame is concerned. */
    private fun square(name: String, centreLon: Double, centreLat: Double, halfSize: Double): DoubleArray {
        check(name.isNotEmpty())
        return doubleArrayOf(
            centreLon - halfSize, centreLat - halfSize,
            centreLon + halfSize, centreLat - halfSize,
            centreLon + halfSize, centreLat + halfSize,
            centreLon - halfSize, centreLat + halfSize,
            centreLon - halfSize, centreLat - halfSize,
        )
    }

    /** A closed ring approximating a circle, for a landmass far bigger than a window. */
    private fun circle(centreLon: Double, centreLat: Double, radius: Double, points: Int): DoubleArray {
        val ring = DoubleArray((points + 1) * 2)
        for (i in 0..points) {
            val angle = 2.0 * Math.PI * i / points
            ring[i * 2] = centreLon + radius * kotlin.math.cos(angle)
            ring[i * 2 + 1] = centreLat + radius * kotlin.math.sin(angle) / 2.0
        }
        return ring
    }

    private fun outlineOf(vararg rings: DoubleArray): WorldOutline {
        val lon = ArrayList<Float>()
        val lat = ArrayList<Float>()
        val starts = ArrayList<Int>()
        for (ring in rings) {
            starts += lon.size
            for (i in ring.indices step 2) {
                lon += ring[i].toFloat()
                lat += ring[i + 1].toFloat()
            }
        }
        starts += lon.size
        return WorldOutline(lon.toFloatArray(), lat.toFloatArray(), starts.toIntArray())
    }
}
