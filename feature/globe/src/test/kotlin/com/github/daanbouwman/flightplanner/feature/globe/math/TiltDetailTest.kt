package com.github.daanbouwman.flightplanner.feature.globe.math

import com.github.daanbouwman.flightplanner.feature.globe.tile.TileAtlas
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tilting must not cost detail — the assertion `QuadtreeTest` was missing.
 *
 * The user's report was *"everything gets lower res the farther I tilt, until
 * everything is just a blob of pixels."* Eleven existing cases went green through
 * that, and one of them was actively helped by it: the atlas-budget test asserts
 * `evictableRequested <= EVICTABLE`, and coarsening **reduces** requests, so the
 * defect is what made that assertion comfortable. Nothing anywhere compared leaf
 * depth under tilt against leaf depth at nadir.
 *
 * ### Why neither case controls for altitude
 *
 * Under tilt the camera genuinely is further from the ground it is looking at:
 * [GlobeCamera.nadirDistance] grows from 0.010 to 0.031 between tilt 0 and
 * `MAX_TILT` at altitude 0.01. Some of the centre-of-screen deficit is therefore
 * correct geometry, not the bug, and an assertion that held *altitude* constant
 * would be asserting something false. [A] holds the camera-to-ground distance
 * constant instead; [B] moves the probe to the near field, which under tilt sits
 * at about the ground distance the nadir has at tilt 0.
 */
class TiltDetailTest {

    private companion object {
        const val MAX_LOD = 18
        const val PINNED = TileAtlas.PINNED_MAX_LEVEL

        /**
         * Far above `TileAtlas.EVICTABLE_SLOTS`, so case A measures the split
         * metric and nothing else. The budget is case B's subject.
         */
        const val GENEROUS_SLOTS = 4096

        val SHIPPING_VIEWPORTS = listOf(
            GlobeViewport(1080f, 2340f),
            GlobeViewport(1080f, 1030f),
            GlobeViewport(800f, 800f),
        )

        /**
         * How much blur the near field may lose to tilt, in LOD levels.
         *
         * A level is a factor of two of real blur, so this is not a rounding
         * allowance. Set from what this test prints, not from a desk estimate.
         */
        const val MAX_TILT_DEFICIT_LEVELS = 2
    }

    private fun collect(
        camera: GlobeCamera,
        viewport: GlobeViewport,
        evictableSlots: Int,
    ): List<VisibleTile> = Quadtree.collectVisibleTiles(
        camera = camera,
        basis = camera.computeBasis(),
        viewport = viewport,
        maxLod = MAX_LOD,
        pinnedMaxLevel = PINNED,
        evictableSlots = evictableSlots,
        request = { _, _, _ -> },
    )

    /** The `z` of the leaf covering the sphere point under [x], [y]. */
    private fun depthUnder(
        camera: GlobeCamera,
        viewport: GlobeViewport,
        x: Float,
        y: Float,
        evictableSlots: Int,
    ): Int? {
        val world = camera.screenToWorld(ScreenPoint(x, y), viewport) ?: return null
        val lat = latitudeOf(world)
        val lon = longitudeOf(world)
        val leaves = collect(camera, viewport, evictableSlots)
        return leaves
            .filter { tile -> covers(tile, lat, lon) }
            .maxOfOrNull { it.z }
    }

    /** Whether [tile]'s Mercator extent contains the point. */
    private fun covers(tile: VisibleTile, latDeg: Float, lonDeg: Float): Boolean {
        val numTiles = (1 shl tile.z).toFloat()
        val tileX = (lonDeg + 180f) / 360f * numTiles
        val tileY = Quadtree.latToTileY(latDeg, numTiles)
        return tileX >= tile.x && tileX < tile.x + 1 && tileY >= tile.y && tileY < tile.y + 1
    }

    private fun latitudeOf(w: Vec3): Float =
        Math.toDegrees(kotlin.math.asin(w.y.coerceIn(-1f, 1f)).toDouble()).toFloat()

    private fun longitudeOf(w: Vec3): Float =
        Math.toDegrees(kotlin.math.atan2(w.x.toDouble(), w.z.toDouble())).toFloat()

    /**
     * **A — the split metric, with the atlas out of the way.**
     *
     * A tilted camera compared against a top-down one *at the same distance from
     * the ground under the screen centre*. Any remaining deficit is the metric
     * scaling an extent estimate by something that is not an extent.
     */
    @Test
    fun `tilting costs no detail at the screen centre once the atlas is out of the way`() {
        val viewport = GlobeViewport(800f, 800f)
        val failures = mutableListOf<String>()

        for (altitude in listOf(0.5f, 0.3f, 0.2f, 0.1f, 0.05f)) {
            for (tilt in listOf(0.6f, 0.8f, 1.0f, 1.2f, MAX_TILT)) {
                val tilted = GlobeCamera(
                    centerLat = 30f,
                    centerLon = 10f,
                    altitude = altitude,
                    tilt = tilt,
                )
                // The same distance from the ground under the screen centre,
                // without the lean. See the class note.
                val flatAtSameDistance = GlobeCamera(
                    centerLat = 30f,
                    centerLon = 10f,
                    altitude = tilted.nadirDistance(),
                )

                val tiltedZ = depthUnder(
                    tilted, viewport, viewport.centerX, viewport.centerY, GENEROUS_SLOTS,
                )
                val flatZ = depthUnder(
                    flatAtSameDistance, viewport, viewport.centerX, viewport.centerY, GENEROUS_SLOTS,
                )
                assertNotNull(tiltedZ, "no leaf under the centre at tilt=$tilt, altitude=$altitude")
                assertNotNull(flatZ, "no leaf under the centre top-down at altitude=$altitude")

                if (tiltedZ < flatZ) {
                    failures += "tilt $tilt at altitude $altitude gave z$tiltedZ where the same " +
                        "ground distance top-down gave z$flatZ"
                }
            }
        }

        assertTrue(
            failures.isEmpty(),
            "the split test is scaling an extent metric by incidence:\n" +
                failures.joinToString("\n") { "  - $it" },
        )
    }

    /**
     * **B — the near field, under the atlas the phone actually has.**
     *
     * This is the user's complaint stated as an assertion, and it is also the
     * guard against the naive fix: simply deleting the incidence term makes this
     * *worse*, because the breadth-first walk then runs out of atlas slots and
     * truncates every branch at the same level.
     */
    @Test
    fun `the near field under tilt keeps the detail the nadir has at the same altitude`() {
        var worst = 0
        val failures = mutableListOf<String>()

        for (viewport in SHIPPING_VIEWPORTS) {
            for (altitude in listOf(0.3f, 0.2f, 0.1f, 0.05f, 0.01f, 0.002f)) {
                val nadir = GlobeCamera(centerLat = 45f, centerLon = -60f, altitude = altitude)
                val baseline = depthUnder(
                    nadir, viewport, viewport.centerX, viewport.centerY, TileAtlas.EVICTABLE_SLOTS,
                ) ?: continue

                for (tilt in listOf(0.4f, 0.8f, 1.0f, 1.2f, MAX_TILT)) {
                    val camera = nadir.copy(tilt = tilt)
                    // 85% down the screen: the near field, which under tilt sits
                    // at about the ground distance the nadir has at tilt 0.
                    val near = depthUnder(
                        camera,
                        viewport,
                        viewport.centerX,
                        0.85f * viewport.height,
                        TileAtlas.EVICTABLE_SLOTS,
                    ) ?: continue

                    val deficit = baseline - near
                    if (deficit > worst) worst = deficit
                    if (deficit > MAX_TILT_DEFICIT_LEVELS) {
                        failures += "near field at tilt $tilt, altitude $altitude, " +
                            "${viewport.width.toInt()}x${viewport.height.toInt()} fell to z$near " +
                            "against z$baseline at nadir — $deficit levels of blur"
                    }
                }
            }
        }

        println("worst near-field tilt deficit over the sweep: $worst levels")
        assertTrue(failures.isEmpty(), failures.joinToString("\n") { "  - $it" })
    }
}
