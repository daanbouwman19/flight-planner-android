package com.github.daanbouwman.flightplanner.feature.globe.math

import com.github.daanbouwman.flightplanner.feature.globe.tile.TileAtlas
import io.kotest.matchers.floats.shouldBeLessThan
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The Rust original's quadtree suite, ported, plus the budgets the phone
 * actually has: the provider's ceiling, the atlas's evictable slots, and the
 * shipping viewports rather than only the reference's square one.
 */
class QuadtreeTest {

    private companion object {
        /** The keyless fallback's ceiling and the keyed provider's — see `TileProvider`. */
        const val KEYLESS_MAX_LOD = 8
        const val KEYED_MAX_LOD = 18

        /** The constants the atlas actually has, so this test and the scene cannot disagree. */
        const val PINNED = TileAtlas.PINNED_MAX_LEVEL
        const val EVICTABLE = TileAtlas.EVICTABLE_SLOTS

        /** The immersive screen and the deep hero on a 1080-wide phone, and the reference's square. */
        val SHIPPING_VIEWPORTS = listOf(
            GlobeViewport(1080f, 2340f),
            GlobeViewport(1080f, 1030f),
            GlobeViewport(800f, 800f),
        )
    }

    private val viewport = GlobeViewport(800f, 800f)

    private class Frame(
        val leaves: List<VisibleTile>,
        val requested: Set<Triple<Int, Int, Int>>,
        val prefetched: List<Triple<Int, Int, Int>>,
    )

    private fun collect(
        camera: GlobeCamera,
        viewport: GlobeViewport = this.viewport,
        maxLod: Int = KEYED_MAX_LOD,
    ): Frame {
        val requested = LinkedHashSet<Triple<Int, Int, Int>>()
        val prefetched = ArrayList<Triple<Int, Int, Int>>()
        val leaves = Quadtree.collectVisibleTiles(
            camera = camera,
            basis = camera.computeBasis(),
            viewport = viewport,
            maxLod = maxLod,
            pinnedMaxLevel = PINNED,
            evictableSlots = EVICTABLE,
            request = { z, x, y -> requested += Triple(z, x, y) },
            prefetch = { z, x, y -> prefetched += Triple(z, x, y) },
        )
        return Frame(leaves, requested, prefetched)
    }

    @Test
    fun `produces tiles and respects the leaf cap at every altitude and tilt`() {
        for (viewport in SHIPPING_VIEWPORTS) {
            for (altitude in listOf(0.0002f, 0.01f, 0.2f, 1f, 4f, 9f)) {
                for (tilt in listOf(0f, 0.6f, 1.2f)) {
                    val camera = GlobeCamera(
                        centerLat = 45f,
                        centerLon = -60f,
                        altitude = altitude,
                        tilt = tilt,
                    )
                    val frame = collect(camera, viewport)
                    assertTrue(frame.leaves.isNotEmpty(), "no tiles at altitude=$altitude, tilt=$tilt")
                    frame.leaves.size shouldBeLessThanOrEqual Quadtree.MAX_VISIBLE_TILES
                }
            }
        }
    }

    /**
     * The assertion that would have caught the atlas thrashing: a frame at a
     * phone-sized viewport used to request 238 distinct tiles above the pinned
     * floor against 171 evictable slots, so on a parked camera the atlas evicted
     * the fallback pyramid itself, six tiles a frame, forever.
     */
    @Test
    fun `a frame never asks the atlas for more than it can hold`() {
        for (viewport in SHIPPING_VIEWPORTS) {
            for (altitude in listOf(3f, 1f, 0.5f, 0.3f, 0.2f, 0.1f, 0.05f, 0.01f)) {
                for (tilt in listOf(0f, 0.8f, 1.2f)) {
                    val camera = GlobeCamera(
                        centerLat = 45f,
                        centerLon = -60f,
                        altitude = altitude,
                        tilt = tilt,
                    )
                    val frame = collect(camera, viewport)
                    val evictableRequested = frame.requested.count { it.first > PINNED }
                    assertTrue(
                        evictableRequested <= EVICTABLE,
                        "$evictableRequested evictable tiles requested against $EVICTABLE slots " +
                            "at ${viewport.width}x${viewport.height}, altitude=$altitude, tilt=$tilt",
                    )
                    // Prefetch spends only what the visible set left over.
                    evictableRequested + frame.prefetched.size shouldBeLessThanOrEqual EVICTABLE
                }
            }
        }
    }

    @Test
    fun `zooming in increases detail`() {
        val far = collect(GlobeCamera(altitude = 2f))
        val near = collect(GlobeCamera(altitude = 0.05f))
        near.leaves.maxOf { it.z } shouldBeGreaterThan far.leaves.maxOf { it.z }
    }

    @Test
    fun `leaves are unique`() {
        val frame = collect(GlobeCamera(altitude = 0.3f, tilt = 0.8f))
        val keys = frame.leaves.map { Triple(it.z, it.x, it.y) }
        assertTrue(keys.size == keys.toSet().size, "duplicate leaves in the visible set")
    }

    @Test
    fun `every ancestor of every leaf is requested`() {
        val frame = collect(GlobeCamera(centerLat = 30f, centerLon = 10f, altitude = 0.1f))
        for (leaf in frame.leaves) {
            var z = leaf.z
            var x = leaf.x
            var y = leaf.y
            while (z > 0) {
                z--
                x /= 2
                y /= 2
                assertTrue(
                    Triple(z, x, y) in frame.requested,
                    "ancestor $z/$x/$y of leaf ${leaf.z}/${leaf.x}/${leaf.y} was never requested",
                )
            }
        }
    }

    /**
     * The scene's mesh signature is order-sensitive, so the order has to be a
     * function of the set alone. Breadth-first emission is: every leaf at one
     * level comes before any at the next, and two traversals of one camera
     * agree exactly. The back-to-front sort that used to follow changed the
     * order on 49 of 60 frames of a slow pan in which the set changed on 7.
     */
    @Test
    fun `leaves come back coarse to fine in traversal order, and the same set gives the same order`() {
        val camera = GlobeCamera(centerLat = 30f, centerLon = 10f, altitude = 0.3f, tilt = 0.8f)
        val frame = collect(camera)
        frame.leaves.zipWithNext().forEach { (before, after) ->
            before.z shouldBeLessThanOrEqual after.z
        }
        collect(camera).leaves shouldBe frame.leaves
    }

    @Test
    fun `prefetch offers the children of leaves about to split, and nothing already requested`() {
        var seen = false
        for (altitude in listOf(1f, 0.5f, 0.3f, 0.2f, 0.1f, 0.05f)) {
            val frame = collect(GlobeCamera(centerLat = 45f, centerLon = -60f, altitude = altitude))
            if (frame.prefetched.isEmpty()) continue
            seen = true
            frame.prefetched.size shouldBeLessThanOrEqual Quadtree.PREFETCH_CAP
            frame.prefetched.size % 4 shouldBe 0
            val leaves = frame.leaves.map { Triple(it.z, it.x, it.y) }.toSet()
            for ((z, x, y) in frame.prefetched) {
                assertTrue(Triple(z, x, y) !in frame.requested, "prefetched $z/$x/$y was already requested")
                assertTrue(Triple(z - 1, x / 2, y / 2) in leaves, "prefetched $z/$x/$y is not a leaf's child")
            }
        }
        assertTrue(seen, "no altitude in the sweep prefetched anything")
    }

    @Test
    fun `the nadir is always covered`() {
        val camera = GlobeCamera(centerLat = 20f, centerLon = 30f, altitude = 0.5f)
        val frame = collect(camera)
        val covered = frame.leaves.any { tile ->
            val numTiles = (1 shl tile.z).toFloat()
            val latN = Quadtree.tileYToLat(tile.y.toFloat(), numTiles)
            val latS = Quadtree.tileYToLat((tile.y + 1).toFloat(), numTiles)
            camera.centerLat in latS..latN &&
                camera.centerLon in tile.lonMin..tile.lonMax
        }
        assertTrue(covered, "no leaf covers the camera nadir")
    }

    /**
     * The ceiling is the provider's, handed in by the scene — 8 for the keyless
     * fallback, 18 for the keyed one — and the traversal neither knows nor
     * cares which is live. Past it the deepest tiles stretch rather than fail.
     */
    @Test
    fun `no leaf is deeper than the provider publishes`() {
        for (maxLod in listOf(KEYLESS_MAX_LOD, KEYED_MAX_LOD)) {
            val frame = collect(GlobeCamera(altitude = MIN_ALTITUDE), maxLod = maxLod)
            frame.leaves.maxOf { it.z } shouldBeLessThanOrEqual maxLod
            frame.requested.maxOf { it.first } shouldBeLessThanOrEqual maxLod
        }
        // And the keyed ceiling is actually reached when the camera is close enough.
        collect(GlobeCamera(altitude = MIN_ALTITUDE), maxLod = KEYED_MAX_LOD)
            .leaves.maxOf { it.z } shouldBeGreaterThan KEYLESS_MAX_LOD
    }

    /**
     * The tolerance scales with the level. This used to run to z8 at a flat
     * 1e-2, and z18 cannot meet that in a `Float`: past 2¹⁸ the last place of
     * `y` itself is 1/32 of a tile, and the latitude's own rounding — a
     * hundred-thousandth of a degree, magnified eleven times by the Mercator
     * stretch at 85° — adds as much again. Half a millionth of the level's tile
     * count is 1e-2 or better through z14 and 0.13 of a tile at z18, about
     * three times the error that arithmetic predicts there.
     */
    @Test
    fun `mercator tile-Y and latitude round-trip`() {
        for (z in 0..KEYED_MAX_LOD) {
            val numTiles = (1 shl z).toFloat()
            val tolerance = maxOf(1e-2f, numTiles * 5e-7f)
            for (step in 0..8) {
                val y = numTiles * step / 8f
                val lat = Quadtree.tileYToLat(y, numTiles)
                kotlin.math.abs(Quadtree.latToTileY(lat, numTiles) - y) shouldBeLessThan tolerance
            }
        }
    }

    /**
     * The mesh's rows at the deepest level, on the last tile before the pole —
     * the worst case for both roundings the Double overload exists to avoid.
     */
    @Test
    fun `a fractional tile-Y keeps its fraction at the deepest level`() {
        val numTiles = (1 shl KEYED_MAX_LOD).toFloat()
        val y = (1 shl KEYED_MAX_LOD) - 1
        val whole = Quadtree.tileYToLat(y.toDouble(), numTiles)
        val fifth = Quadtree.tileYToLat(y.toDouble() + 0.2, numTiles)
        val next = Quadtree.tileYToLat(y.toDouble() + 1.0, numTiles)
        // A fifth of the way from one row to the next. The residual — about
        // 2e-6 — is the Mercator curve's own bend across one row, not rounding;
        // rounding in a Double is a million times smaller.
        val position = (fifth - whole) / (next - whole)
        assertTrue(kotlin.math.abs(position - 0.2) < 1e-5, "fifth row landed at $position of a tile")

        // The Float path is what the overload replaces: between 2^17 and 2^18 a
        // Float steps in sixty-fourths, so (2^18 - 1) + 0.2f is 0.203125 and the
        // row lands a three-hundredth of a tile low. If this ever passes, the
        // overload has become redundant.
        val floatFifth = Quadtree.tileYToLat(y.toFloat() + 0.2f, numTiles).toDouble()
        val floatPosition = (floatFifth - whole) / (next - whole)
        assertTrue(kotlin.math.abs(floatPosition - 0.2) > 2e-3, "Float path landed at $floatPosition")
    }
}
