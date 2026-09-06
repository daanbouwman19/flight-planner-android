package com.github.daanbouwman.flightplanner.feature.globe.math

import io.kotest.matchers.floats.shouldBeLessThan
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import kotlin.test.Test
import kotlin.test.assertTrue

/** The Rust original's quadtree suite, ported, plus the mobile LOD ceiling. */
class QuadtreeTest {

    private val viewport = GlobeViewport(800f, 800f)

    private fun collect(camera: GlobeCamera): Pair<List<VisibleTile>, Set<Triple<Int, Int, Int>>> {
        val requested = mutableSetOf<Triple<Int, Int, Int>>()
        val leaves = Quadtree.collectVisibleTiles(camera, camera.computeBasis(), viewport) { z, x, y ->
            requested += Triple(z, x, y)
        }
        return leaves to requested
    }

    @Test
    fun `produces tiles and respects the budget at every altitude and tilt`() {
        for (altitude in listOf(0.0002f, 0.01f, 0.2f, 1f, 4f, 9f)) {
            for (tilt in listOf(0f, 0.6f, 1.2f)) {
                val camera = GlobeCamera(
                    centerLat = 45f,
                    centerLon = -60f,
                    altitude = altitude,
                    tilt = tilt,
                )
                val (leaves, _) = collect(camera)
                assertTrue(leaves.isNotEmpty(), "no tiles at altitude=$altitude, tilt=$tilt")
                leaves.size shouldBeLessThanOrEqual Quadtree.MAX_VISIBLE_TILES
            }
        }
    }

    @Test
    fun `zooming in increases detail`() {
        val (far, _) = collect(GlobeCamera(altitude = 2f))
        val (near, _) = collect(GlobeCamera(altitude = 0.05f))
        near.maxOf { it.z } shouldBeGreaterThan far.maxOf { it.z }
    }

    @Test
    fun `leaves are unique`() {
        val (leaves, _) = collect(GlobeCamera(altitude = 0.3f, tilt = 0.8f))
        val keys = leaves.map { Triple(it.z, it.x, it.y) }
        assertTrue(keys.size == keys.toSet().size, "duplicate leaves in the visible set")
    }

    @Test
    fun `every ancestor of every leaf is requested`() {
        val (leaves, requested) = collect(
            GlobeCamera(centerLat = 30f, centerLon = 10f, altitude = 0.1f),
        )
        for (leaf in leaves) {
            var z = leaf.z
            var x = leaf.x
            var y = leaf.y
            while (z > 0) {
                z--
                x /= 2
                y /= 2
                assertTrue(
                    Triple(z, x, y) in requested,
                    "ancestor $z/$x/$y of leaf ${leaf.z}/${leaf.x}/${leaf.y} was never requested",
                )
            }
        }
    }

    @Test
    fun `the nadir is always covered`() {
        val camera = GlobeCamera(centerLat = 20f, centerLon = 30f, altitude = 0.5f)
        val (leaves, _) = collect(camera)
        val covered = leaves.any { tile ->
            val numTiles = (1 shl tile.z).toFloat()
            val latN = Quadtree.tileYToLat(tile.y.toFloat(), numTiles)
            val latS = Quadtree.tileYToLat((tile.y + 1).toFloat(), numTiles)
            camera.centerLat in latS..latN &&
                camera.centerLon in tile.lonMin..tile.lonMax
        }
        assertTrue(covered, "no leaf covers the camera nadir")
    }

    /**
     * The ceiling is the provider's, not a rendering choice — NASA GIBS stops
     * publishing around z8–z9, and every request past it is a 404. If this ever
     * has to move it moves with the provider.
     */
    @Test
    fun `no leaf is deeper than the provider publishes`() {
        val (leaves, requested) = collect(GlobeCamera(altitude = MIN_ALTITUDE))
        leaves.maxOf { it.z } shouldBeLessThanOrEqual Quadtree.MAX_LOD
        requested.maxOf { it.first } shouldBeLessThanOrEqual Quadtree.MAX_LOD
    }

    @Test
    fun `mercator tile-Y and latitude round-trip`() {
        for (z in 0..Quadtree.MAX_LOD) {
            val numTiles = (1 shl z).toFloat()
            for (step in 0..8) {
                val y = numTiles * step / 8f
                val lat = Quadtree.tileYToLat(y, numTiles)
                kotlin.math.abs(Quadtree.latToTileY(lat, numTiles) - y) shouldBeLessThan 1e-2f
            }
        }
    }
}
