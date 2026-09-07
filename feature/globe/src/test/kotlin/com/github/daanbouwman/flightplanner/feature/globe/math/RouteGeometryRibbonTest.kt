package com.github.daanbouwman.flightplanner.feature.globe.math

import kotlin.math.abs
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The route ribbon has to be drawn, and drawn on the ground, at every altitude.
 *
 * Both halves of that shipped broken, and neither was visible until an ArcGIS
 * key let the camera off the 640 km floor NASA GIBS imposed:
 *
 * - The arc floated at a fixed radius of `1.0015`, **9.5 km up**. It slid over
 *   the terrain as the camera panned, by a fraction of the altitude, and below
 *   9.5 km the camera was *under* that shell so every sample failed the depth
 *   test in `appendArc` and the route disappeared entirely.
 * - The ribbon's half-width is a pixel count converted to world units, and at
 *   the bottom of the range it fell under one ulp of a unit-sphere coordinate,
 *   so both edges rounded onto the same point and every quad was degenerate.
 *
 * So this asserts the two properties rather than the constants: the strip is
 * always emitted with real width, and it always projects onto the ground point
 * it is meant to be lying on.
 */
class RouteGeometryRibbonTest {

    private val viewport = GlobeViewport(1080f, 2424f)

    /** Every altitude the camera can hold, ends included. */
    private val altitudes = listOf(
        MIN_ALTITUDE,
        1.5e-4f,
        3e-4f,
        1e-3f,
        0.01f,
        0.2f,
        2f,
        MAX_ALTITUDE,
    )

    private val samples = 16

    /**
     * A leg across the nadir, scaled to the altitude so it is always on screen —
     * the deepest zoom sees a few hundred metres and the shallowest a continent.
     */
    private fun legFor(altitude: Float): Array<Vec3> {
        val spanDegrees = min(altitude.toDouble(), 0.5) * (180.0 / Math.PI)
        val lats = DoubleArray(samples)
        val lons = DoubleArray(samples) { -spanDegrees / 2 + spanDegrees * it / (samples - 1) }
        return RouteGeometry.toWorldPoints(lats, lons)
    }

    private fun ribbonAt(altitude: Float): Pair<RibbonBuffer, Array<Vec3>> {
        val camera = GlobeCamera(altitude = altitude)
        val points = legFor(altitude)
        val out = RibbonBuffer(samples * 4)
        val written = RouteGeometry.ribbonInto(
            arcs = listOf(points),
            camera = camera,
            basis = camera.computeBasis(),
            viewport = viewport,
            halfWidthPx = 1.6f,
            out = out,
        )
        assertTrue(
            written == samples * 2,
            "at altitude $altitude the ribbon wrote $written vertices for $samples visible " +
                "samples — the arc is being culled, which is what happened when it floated " +
                "above the camera",
        )
        return out to points
    }

    private fun vertex(out: RibbonBuffer, index: Int): Vec3 = Vec3(
        out.positions[index * 3],
        out.positions[index * 3 + 1],
        out.positions[index * 3 + 2],
    )

    @Test
    fun `the ribbon is drawn with real width at every altitude`() {
        for (altitude in altitudes) {
            val (out, _) = ribbonAt(altitude)
            for (i in 0 until samples) {
                val left = vertex(out, i * 2)
                val right = vertex(out, i * 2 + 1)
                assertTrue(
                    left != right,
                    "at altitude $altitude sample $i collapsed onto itself at $left — the " +
                        "offset fell below one ulp of the coordinate it was added to",
                )
            }
        }
    }

    @Test
    fun `the arc lies on the ground it is drawn over`() {
        // Half a pixel of lift, plus the pixel the projection itself rounds to.
        val toleranceP = 1.5f
        for (altitude in altitudes) {
            val camera = GlobeCamera(altitude = altitude)
            val (out, points) = ribbonAt(altitude)
            for (i in 0 until samples) {
                val ribbonMid = (vertex(out, i * 2) + vertex(out, i * 2 + 1)) * 0.5f
                val drawn = assertNotNull(
                    camera.worldToScreen(ribbonMid, viewport),
                    "at altitude $altitude sample $i the ribbon is behind the camera",
                )
                val ground = assertNotNull(
                    camera.worldToScreen(points[i], viewport),
                    "at altitude $altitude sample $i the ground point is behind the camera",
                )
                val drift = (drawn - ground).length()
                assertTrue(
                    drift < toleranceP,
                    "at altitude $altitude sample $i the arc is drawn ${drift}px from the " +
                        "ground beneath it — a lift that scales with the radius rather than " +
                        "with the pixel is what makes the route slide when the camera pans",
                )
            }
        }
    }

    @Test
    fun `the arc stays below the camera`() {
        for (altitude in altitudes) {
            val (out, _) = ribbonAt(altitude)
            val distance = GlobeCamera(altitude = altitude).distance
            for (i in 0 until out.vertexCount) {
                val radius = vertex(out, i).length()
                assertTrue(
                    radius < distance,
                    "at altitude $altitude vertex $i sits at radius $radius, at or above the " +
                        "camera's own $distance — the whole ribbon is then behind the camera",
                )
                assertTrue(radius >= 1f, "at altitude $altitude vertex $i sank below the surface")
            }
        }
    }

    @Test
    fun `the lift is bounded in pixels, not in radii`() {
        // Asserted in pixels because that is what the lift now *is*, and the
        // distinction is the whole fix: the constant it replaced was 1.5e-3 of a
        // radius, which is a third of a pixel at MAX_ALTITUDE and fifteen
        // thousand pixels at the deck. A bound in radii would pass the old
        // broken constant at high altitude and tell us nothing about the case
        // that failed.
        for (altitude in altitudes) {
            val focal = GlobeCamera(altitude = altitude).focalPixels(viewport.height)
            val worldPerPixelAtNadir = altitude / focal
            val (out, _) = ribbonAt(altitude)
            for (i in 0 until out.vertexCount) {
                val liftPixels = (vertex(out, i).length() - 1f) / worldPerPixelAtNadir
                assertTrue(
                    liftPixels <= 1f,
                    "at altitude $altitude vertex $i floats ${liftPixels}px above the surface",
                )
            }
        }
    }

    @Test
    fun `a leg behind the limb is dropped rather than drawn through the planet`() {
        val camera = GlobeCamera(altitude = 2f)
        // Antipodal to the camera's nadir: every sample is behind the horizon.
        val points = RouteGeometry.toWorldPoints(
            lats = DoubleArray(samples),
            lons = DoubleArray(samples) { 170.0 + 20.0 * it / (samples - 1) },
        )
        val out = RibbonBuffer(samples * 4)
        val written = RouteGeometry.ribbonInto(
            arcs = listOf(points),
            camera = camera,
            basis = camera.computeBasis(),
            viewport = viewport,
            halfWidthPx = 1.6f,
            out = out,
        )
        assertTrue(written == 0, "the far side of the planet contributed $written vertices")
    }

    @Test
    fun `Vec3 length is what these assertions think it is`() {
        // The tests above lean on `length()` meaning distance from the origin;
        // a ribbon vertex is only interpretable as a radius if it does.
        assertTrue(abs(Vec3(0.6f, 0f, 0.8f).length() - 1f) < 1e-6f)
    }
}
