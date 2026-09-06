package com.github.daanbouwman.flightplanner.feature.globe.math

import java.util.ArrayDeque
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.asinh
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sinh
import kotlin.math.sqrt

/**
 * Screen-space-error driven tile selection, ported from the Rust original.
 *
 * Instead of picking one global level of detail and enumerating every tile
 * inside the visible lat/lon box — which explodes combinatorially at high zoom
 * and fetches the horizon at nadir resolution — the visible set is found by
 * walking the slippy-map quadtree from the root: a tile subdivides only while
 * its projected screen size exceeds a threshold *and* it survives horizon and
 * viewport culling. That naturally yields high detail at the nadir, coarse tiles
 * at the horizon, and a tile count bounded by what is actually on screen.
 *
 * ### The mobile-specific divergence
 *
 * [MAX_LOD] is **8**, not the desktop's 18. NASA GIBS — the default provider
 * while Esri's consumer licensing is unresolved — publishes its imagery to
 * around z8–z9, so asking for z18 would be thousands of 404s rather than
 * detail. The subdivision test is unchanged; only the ceiling moved, and the
 * traversal degrades to a coarser leaf exactly the way it does when it runs out
 * of budget.
 *
 * [MAX_VISIBLE_TILES] is a property of the atlas rather than of the renderer —
 * see the constant’s own note for why it is 160 and not the 256 slots the atlas
 * has.
 */
internal object Quadtree {

    /** Deepest zoom requested from the imagery provider. See the class note. */
    const val MAX_LOD: Int = 8

    /** Native tile texture edge, pixels. */
    private const val TILE_PX = 256f

    /**
     * Angular size of one texel of the deepest tile this will ever ask for, in
     * radians on the unit sphere.
     *
     * [MAX_LOD] splits the equator into `2^MAX_LOD` tiles of [TILE_PX] each, so
     * this is `2π` over their product. It is the finest detail the imagery can
     * ever hold, and therefore the point past which moving the camera closer adds
     * blur and nothing else — which is what [GlobeFit] uses it for. It lives here
     * because both of the numbers it comes from live here, and it moves when they
     * do.
     */
    const val FINEST_TEXEL_RADIANS: Float =
        (2.0 * Math.PI / ((1 shl MAX_LOD) * TILE_PX.toDouble())).toFloat()

    /**
     * A tile subdivides while its projected size exceeds this.
     *
     * Slightly above the texture size, so rendered tiles land between about 50%
     * and 112% texel density — under-sampling on the near side of the split
     * rather than over-sampling on the far side, because a slightly soft tile
     * reads better than a slightly aliased one.
     */
    private const val SPLIT_SCREEN_PX = TILE_PX * 1.125f

    /**
     * Hard cap on leaves per frame.
     *
     * Breadth-first order means hitting the cap degrades detail uniformly across
     * the view rather than dropping whole regions — the traversal has already
     * emitted every coarse ancestor before it runs out.
     *
     * **The desktop's figure is 256 and this is 160, because the atlas is the
     * real constraint here.** `TileAtlas` has exactly 256 slots and pins 85 of
     * them for z0–z3, leaving 171 that can be evicted; a frame allowed to select
     * more leaves than that would be asking the atlas to evict a tile the same
     * frame is about to draw, and the result is a flicker that only appears on a
     * fast pan. 160 is that ceiling with room to spare, and it is still far more
     * than a phone-sized viewport can consume: a 1080-pixel-wide window at the
     * split threshold wants around 40.
     */
    const val MAX_VISIBLE_TILES: Int = 160

    /** Extra angle past the horizon cone before a tile is culled, so the limb can fade. */
    private const val HORIZON_CULL_MARGIN = 0.08f

    /**
     * Viewport culling applies only to tiles below this angular radius; larger
     * ones curve too much for a screen-space box to say anything true about.
     */
    private const val VIEWPORT_CULL_MAX_ANG_RADIUS = 1.0f

    /** Floor for the camera-to-tile distance in the screen-size estimate. */
    private const val MIN_DEPTH = 1e-4f

    private const val DEG_TO_RAD = (PI / 180.0).toFloat()

    /** Inverse Mercator: tile-Y to latitude in degrees, at a given level. */
    fun tileYToLat(y: Float, numTiles: Float): Float {
        val n = PI - 2.0 * PI * y / numTiles
        return ((180.0 / PI) * atan(sinh(n))).toFloat()
    }

    /** Forward Mercator: latitude in degrees to tile-Y, at a given level. */
    fun latToTileY(latDeg: Float, numTiles: Float): Float {
        val lat = latDeg.toDouble() * PI / 180.0
        val n = asinh(kotlin.math.tan(lat))
        return ((PI - n) * numTiles / (2.0 * PI)).toFloat()
    }

    /**
     * Walks the quadtree breadth-first and returns the leaves to render, sorted
     * back to front.
     *
     * [request] is called for **every visited node** — leaves and their
     * ancestors alike — which is what makes the imagery arrive coarse to fine
     * and guarantees every leaf has a cached ancestor to fall back on while it
     * loads. That property is asserted by a test, because it is the difference
     * between a globe that sharpens and one that shows holes.
     */
    fun collectVisibleTiles(
        camera: GlobeCamera,
        basis: CameraBasis,
        viewport: GlobeViewport,
        request: (z: Int, x: Int, y: Int) -> Unit,
    ): List<VisibleTile> {
        val frame = TraversalFrame(
            camera = camera,
            basis = basis,
            viewport = viewport,
            focal = camera.focalPixels(viewport.height),
            horizonAngle = camera.horizonAngle(),
        )

        val leaves = ArrayList<VisibleTile>(64)
        val queue = ArrayDeque<IntArray>()
        queue.addLast(intArrayOf(0, 0, 0))

        while (queue.isNotEmpty()) {
            val (z, x, y) = queue.removeFirst().let { Triple(it[0], it[1], it[2]) }
            val m = tileMetrics(frame, z, x, y) ?: continue
            request(z, x, y)

            // Emitting a leaf keeps `leaves + queue` constant and culling shrinks
            // it, so gating subdivision — a net +3 nodes — on the prospective
            // total keeps the final leaf count at or under the budget.
            val withinBudget = leaves.size + queue.size + 4 <= MAX_VISIBLE_TILES

            if (z < MAX_LOD && withinBudget && m.screenPx > SPLIT_SCREEN_PX) {
                val cx = x * 2
                val cy = y * 2
                queue.addLast(intArrayOf(z + 1, cx, cy))
                queue.addLast(intArrayOf(z + 1, cx + 1, cy))
                queue.addLast(intArrayOf(z + 1, cx, cy + 1))
                queue.addLast(intArrayOf(z + 1, cx + 1, cy + 1))
            } else {
                leaves += VisibleTile(
                    z = z,
                    x = x,
                    y = y,
                    lonMin = m.lonMin,
                    lonMax = m.lonMax,
                    depth = m.depth,
                )
            }
        }

        leaves.sortByDescending { it.depth }
        return leaves
    }

    private class TraversalFrame(
        val camera: GlobeCamera,
        val basis: CameraBasis,
        val viewport: GlobeViewport,
        val focal: Float,
        val horizonAngle: Float,
    )

    private class TileMetrics(
        val screenPx: Float,
        val depth: Float,
        val lonMin: Float,
        val lonMax: Float,
    )

    /**
     * Culls `(z, x, y)` against the horizon cone and the viewport, and estimates
     * its projected screen size. Null when the tile cannot be visible.
     */
    private fun tileMetrics(frame: TraversalFrame, z: Int, x: Int, y: Int): TileMetrics? {
        val numTiles = (1 shl z).toFloat()
        val lonMin = (x / numTiles) * 360f - 180f
        val lonMax = ((x + 1) / numTiles) * 360f - 180f
        val latN = tileYToLat(y.toFloat(), numTiles)
        val latS = tileYToLat((y + 1).toFloat(), numTiles)
        val latC = (latN + latS) * 0.5f
        val lonC = (lonMin + lonMax) * 0.5f

        // World-space angular extents of the tile, radians.
        val ewArc = (lonMax - lonMin) * DEG_TO_RAD * cos(latC * DEG_TO_RAD)
        val nsArc = (latN - latS) * DEG_TO_RAD

        // Conservative angular radius: the flat half-diagonal underestimates
        // spherical distance for near-hemisphere tiles, so inflate and cap.
        val angRadius = min(PI.toFloat(), sqrt(ewArc * ewArc + nsArc * nsArc) * 0.625f)

        val center = latLonToWorld(latC, lonC)
        val cosCenter = facingValueFast(frame.basis, center).coerceIn(-1f, 1f)
        if (acos(cosCenter) - angRadius > frame.horizonAngle + HORIZON_CULL_MARGIN) return null

        val cam = rotateFast(frame.basis, center)
        val dist = cam.length()
        // World-space extent across the tile's larger axis.
        val chord = 2f * sin(min(PI.toFloat(), max(ewArc, nsArc)) * 0.5f)
        // Nearest possible camera-to-tile distance; the nadir at `altitude` is a
        // global lower bound for the whole sphere.
        val near = max(MIN_DEPTH, max(frame.camera.altitude, dist - chord * 0.5f))
        val screenPx = chord * frame.focal / near

        if (angRadius < VIEWPORT_CULL_MAX_ANG_RADIUS &&
            !projectsIntoViewport(
                frame,
                screenPx,
                floatArrayOf(latN, latC, latS),
                floatArrayOf(lonMin, lonC, lonMax),
            )
        ) {
            return null
        }

        return TileMetrics(screenPx = screenPx, depth = dist, lonMin = lonMin, lonMax = lonMax)
    }

    /**
     * Conservative viewport test: project a 3×3 sample of the tile and check
     * whether its screen box, padded for the bulge between samples, touches the
     * viewport. A sample behind the camera keeps the tile — it cannot be
     * bounded, and dropping it would punch a hole in the near field, which is
     * the one place a hole is unmissable.
     */
    private fun projectsIntoViewport(
        frame: TraversalFrame,
        screenPx: Float,
        lats: FloatArray,
        lons: FloatArray,
    ): Boolean {
        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        for (lat in lats) {
            for (lon in lons) {
                val cam = rotateFast(frame.basis, latLonToWorld(lat, lon))
                val p = frame.camera.project(cam, frame.viewport) ?: return true
                minX = min(minX, p.x)
                minY = min(minY, p.y)
                maxX = max(maxX, p.x)
                maxY = max(maxY, p.y)
            }
        }
        val margin = screenPx * 0.35f + 24f
        return (minX - margin) <= frame.viewport.width &&
            (maxX + margin) >= 0f &&
            (minY - margin) <= frame.viewport.height &&
            (maxY + margin) >= 0f
    }
}

/**
 * A tile selected for rendering this frame.
 *
 * [lonMin] and [lonMax] come back with the tile because the traversal has
 * already computed them and the mesh builder needs them; recomputing them per
 * vertex would be the same two divides a few thousand times over.
 *
 * @property depth distance from the camera to the tile centre, for back-to-front order
 */
internal data class VisibleTile(
    val z: Int,
    val x: Int,
    val y: Int,
    val lonMin: Float,
    val lonMax: Float,
    val depth: Float,
)
