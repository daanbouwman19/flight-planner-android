package com.github.daanbouwman.flightplanner.feature.globe.render

import com.github.daanbouwman.flightplanner.feature.globe.math.GlobeCamera
import com.github.daanbouwman.flightplanner.feature.globe.math.GlobeViewport
import com.github.daanbouwman.flightplanner.feature.globe.math.MIN_ALTITUDE
import com.github.daanbouwman.flightplanner.feature.globe.math.Quadtree
import com.github.daanbouwman.flightplanner.feature.globe.tile.TileAtlas
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import kotlin.test.Test

/**
 * The tile mesh's vertex and index ceilings against what the traversal can
 * actually ask for.
 *
 * `GlobeMesh.MAX_TILE_VERTICES` used to be justified by an enumeration that was
 * not a legal quadtree partition. This measures the real worst case — every
 * leaf carrying a coarse layer under its sharp one, which is the most a sharpen
 * can cost — over the shipping viewports and a sweep of cameras, and it is the
 * figure that constant's KDoc quotes. Change the tessellation, the leaf cap or
 * the atlas budget and this is what says whether the ceiling still holds.
 */
class GlobeMeshBudgetTest {

    private companion object {
        /** The keyed provider's ceiling — the deepest the traversal ever goes. */
        const val KEYED_MAX_LOD = 18
    }

    @Test
    fun `no frame's visible set can overflow the tile mesh buffers`() {
        val viewports = listOf(
            GlobeViewport(1080f, 2340f),
            GlobeViewport(1080f, 1030f),
            GlobeViewport(1440f, 3120f),
            GlobeViewport(800f, 800f),
        )
        val altitudes = listOf(9f, 3f, 1f, 0.5f, 0.3f, 0.2f, 0.1f, 0.05f, 0.01f, 0.001f, MIN_ALTITUDE)
        val tilts = listOf(0f, 0.8f, 1.2f)
        val centres = listOf(0f to 0f, 45f to -60f, 70f to 10f, -33f to 151f, 84f to 0f)

        var worstVertices = 0
        var worstIndices = 0
        for (viewport in viewports) {
            for (altitude in altitudes) {
                for (tilt in tilts) {
                    for ((lat, lon) in centres) {
                        val camera = GlobeCamera(
                            centerLat = lat,
                            centerLon = lon,
                            altitude = altitude,
                            tilt = tilt,
                        )
                        val leaves = Quadtree.collectVisibleTiles(
                            camera = camera,
                            basis = camera.computeBasis(),
                            viewport = viewport,
                            maxLod = KEYED_MAX_LOD,
                            pinnedMaxLevel = TileAtlas.PINNED_MAX_LEVEL,
                            evictableSlots = TileAtlas.EVICTABLE_SLOTS,
                            request = { _, _, _ -> },
                        )
                        // Every tile at its worst: a coarse layer under a sharp one.
                        val vertices = 2 * leaves.sumOf { tile ->
                            val stride = GlobeMesh.substepsFor(tile.z) + 1
                            stride * stride
                        }
                        val indices = 2 * leaves.sumOf { tile ->
                            val substeps = GlobeMesh.substepsFor(tile.z)
                            substeps * substeps * 6
                        }
                        worstVertices = maxOf(worstVertices, vertices)
                        worstIndices = maxOf(worstIndices, indices)
                    }
                }
            }
        }
        println("tile mesh worst case over the sweep: $worstVertices vertices, $worstIndices indices")
        worstVertices shouldBeLessThanOrEqual GlobeMesh.MAX_TILE_VERTICES
        worstIndices shouldBeLessThanOrEqual GlobeMesh.MAX_TILE_INDICES
    }
}
