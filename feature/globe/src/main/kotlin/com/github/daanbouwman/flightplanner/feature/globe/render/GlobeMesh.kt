package com.github.daanbouwman.flightplanner.feature.globe.render

import com.github.daanbouwman.flightplanner.feature.globe.math.Quadtree
import com.github.daanbouwman.flightplanner.feature.globe.math.VisibleTile
import com.github.daanbouwman.flightplanner.feature.globe.tile.TileAtlas
import com.github.daanbouwman.flightplanner.feature.globe.tile.TileKey
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Turns a frame's visible tile set into vertices, and builds the static
 * backdrop sphere.
 *
 * ### The mesh does not depend on the camera
 *
 * Positions are on the unit sphere and UVs are atlas coordinates, so the tile
 * mesh is a pure function of *which* tiles are visible and *where their imagery
 * sits in the atlas* — not of where the camera is. The projection is the GPU's
 * job and the limb fade is the material's. That is what lets the mesh be rebuilt
 * only when the visible set changes rather than every frame, which during a
 * slow drag is a handful of times a second instead of sixty.
 *
 * ### Falling back to an ancestor
 *
 * A leaf whose own tile has not arrived is drawn from the deepest ancestor that
 * has, with the UV rect cropped to the leaf's quadrant of it. That is why the
 * traversal requests every node it visits and why levels 0–2 are pinned: the
 * walk up terminates at a tile that is always resident, so a leaf is never
 * simply missing. It is the difference between a globe that sharpens and one
 * that shows holes.
 *
 * ### The trigonometry is per row and per column
 *
 * A tile's grid is separable: a vertex is `(cosLat · sinLon, sinLat, cosLat · cosLon)`
 * scaled by the bulge, so the sines and cosines are computed once per row and
 * once per column into scratch arrays and the inner loop is three multiplies.
 * It used to call `latLonToWorld` per vertex — four transcendentals — and solve
 * the row latitudes through `atan(sinh())` twice over, once for the bulge and
 * once for the vertices. A tile's two layers share the same grid and differ only
 * in their UV rect, so the scratch is filled once per tile and both layers write
 * from it. The index pattern of a grid is likewise the same for every tile at a
 * level, so there is one per tessellation density, built once.
 */
internal object GlobeMesh {

    /**
     * Bytes per tile vertex: position float3, uv float2, and a float2 carrying
     * when this tile's imagery arrived and whether this is the sharp layer.
     *
     * The last pair is what the material's sharpen fade reads. It is per vertex
     * rather than a uniform because a single draw call covers a couple of
     * hundred tiles that all arrived at different moments.
     */
    const val TILE_VERTEX_BYTES: Int = 28

    /** Bytes per backdrop vertex: position float3. */
    const val POSITION_VERTEX_BYTES: Int = 12

    /**
     * Vertex ceiling for one rebuild of the tile mesh.
     *
     * **Measured, not enumerated.** The first version of this bound was an
     * enumeration — one z0 leaf at 33², four z1 at 21², sixteen z2, sixty-four
     * z3 and the rest at z4 — that is not a legal quadtree partition: a z0 leaf
     * covers the whole planet, so nothing else is a leaf beside it, and the same
     * holds one level down. What the traversal can actually produce is what
     * `GlobeMeshBudgetTest` re-derives from the live constants: over a sweep of
     * latitude, longitude, tilt and altitude at 1440×3120 and the two phone
     * viewports, the reachable maximum is 13,230 vertices with every tile
     * carrying a coarse layer under its sharp one, which is the worst a sharpen
     * can do. Forty thousand is that three times over, and it stays well inside
     * a `ushort` index, which is the reason the bound is worth stating rather
     * than growing the buffer on demand.
     *
     * It has to be re-measured whenever [substepsFor], the leaf cap or the
     * atlas's budget changes, and the test is what makes sure it is.
     */
    const val MAX_TILE_VERTICES: Int = 40_000

    /**
     * The matching index ceiling: six per grid cell, by the same measurement
     * (59,676 at the vertex maximum).
     */
    const val MAX_TILE_INDICES: Int = 192_000

    /**
     * Tessellation density per level, from the Rust original.
     *
     * A level-0 tile spans a whole hemisphere and needs many segments to round
     * the sphere; a level-6 tile is nearly flat and four is already more than
     * its curvature asks for. Getting this wrong at the coarse end is what makes
     * a zoomed-out globe look like a polyhedron.
     */
    fun substepsFor(z: Int): Int = when (z) {
        0 -> 32
        1 -> 20
        2 -> 14
        3 -> 10
        4 -> 7
        else -> 5
    }

    /** The densest tessellation's row length, and so the size of every scratch row. */
    private const val MAX_STRIDE = 32 + 1

    /** Levels with a tessellation of their own; everything deeper shares the last. */
    private const val TESSELLATED_LEVELS = 5

    /**
     * One index pattern per tessellation density, relative to the grid's first
     * vertex. Built once: the pattern is the same for every tile at a level, and
     * a frame writes it a couple of hundred times.
     */
    private val indexPatterns = Array(TESSELLATED_LEVELS + 1) { buildIndexPattern(substepsFor(it)) }

    private fun indexPatternFor(z: Int): ShortArray = indexPatterns[min(z, TESSELLATED_LEVELS)]

    // Per-tile scratch, filled by `prepareTile` and read by `writeGrid` for both
    // of a tile's layers. The latitudes are Doubles for the reason given on
    // `Quadtree.tileYToLat`'s Double overload; everything derived is a Float.
    private val rowLat = DoubleArray(MAX_STRIDE)
    private val rowSinLat = FloatArray(MAX_STRIDE)
    private val rowCosLat = FloatArray(MAX_STRIDE)
    private val colSinLon = FloatArray(MAX_STRIDE)
    private val colCosLon = FloatArray(MAX_STRIDE)

    /** `i / substeps` for row and column `i` — the grid is square, so one array serves both. */
    private val fraction = FloatArray(MAX_STRIDE)

    private val sharpUv = FloatArray(4)
    private val coarseUv = FloatArray(4)

    /** What one call to [buildTiles] produced. */
    class TileMeshResult(
        val vertexCount: Int,
        val indexCount: Int,
        /**
         * The scene time at which the last still-animating sharpen fade
         * finishes, or [Float.MAX_VALUE] when none is running.
         *
         * The scene forces one rebuild at that moment, which is what retires the
         * coarse layers underneath the tiles that have finished sharpening. Left
         * to the next camera move, a still globe would keep drawing a redundant
         * layer indefinitely.
         */
        val nextFadeExpiry: Float,
    )

    /**
     * Writes the tile mesh into [vertices] and [indices].
     *
     * ### The sharpen crossfade is two layers, not one animated one
     *
     * A tile whose own imagery landed within [fadeDurationSeconds] is emitted
     * **twice**: once with its coarse ancestor's crop at full alpha, and once
     * with its own texture ramping up over it. Fading a single layer in would
     * fade it up from the backdrop, which reads as the tile appearing out of
     * nothing rather than as detail arriving in a picture that was already
     * there — and 1E is explicit that the coarse ancestor *is* the picture.
     *
     * With [fadeDurationSeconds] at zero — reduce motion — the second layer is
     * never emitted at all. Off, not shortened.
     */
    fun buildTiles(
        tiles: List<VisibleTile>,
        atlas: TileAtlas,
        nowSeconds: Float,
        fadeDurationSeconds: Float,
        vertices: ByteBuffer,
        indices: ByteBuffer,
    ): TileMeshResult {
        vertices.clear()
        indices.clear()
        var vertexCount = 0
        var indexCount = 0
        var nextExpiry = Float.MAX_VALUE

        for (tile in tiles) {
            val key = TileKey.of(tile.z, tile.x, tile.y)
            val hasOwn = atlas.uvRect(key, sharpUv)
            val arrival = if (hasOwn) atlas.arrivalOf(key) else 0f
            val stillFading = hasOwn &&
                fadeDurationSeconds > 0f &&
                nowSeconds - arrival < fadeDurationSeconds

            val hasCoarse = if (hasOwn && !stillFading) false else resolveAncestorUv(tile, atlas)

            val substeps = substepsFor(tile.z)
            val stride = substeps + 1
            val pattern = indexPatternFor(tile.z)
            val layers = (if (stillFading && hasCoarse) 1 else 0) + (if (hasOwn) 1 else 0) +
                (if (!hasOwn && hasCoarse) 1 else 0)
            if (layers == 0) continue
            // `continue`, not `break`. The list is coarse first and fine last, so
            // stopping at the first tile that does not fit would drop every
            // finer tile behind it — the ones nearest the camera. Skipping only
            // the tile that overflowed lets a smaller one behind it still land.
            // The cap is a backstop; see MAX_TILE_VERTICES for why it is not met.
            if (vertexCount + layers * stride * stride > MAX_TILE_VERTICES) continue
            if (indexCount + layers * pattern.size > MAX_TILE_INDICES) continue

            val bulge = prepareTile(tile, substeps)

            if (stillFading && hasCoarse) {
                // The picture that is already there, held at full strength.
                vertexCount = writeGrid(coarseUv, 0f, 0f, substeps, bulge, vertexCount, vertices)
                indexCount = writeIndices(vertexCount - stride * stride, pattern, indexCount, indices)
                nextExpiry = minOf(nextExpiry, arrival + fadeDurationSeconds)
            }
            when {
                hasOwn -> {
                    val layerFlag = if (stillFading && hasCoarse) 1f else 0f
                    vertexCount =
                        writeGrid(sharpUv, arrival, layerFlag, substeps, bulge, vertexCount, vertices)
                    indexCount =
                        writeIndices(vertexCount - stride * stride, pattern, indexCount, indices)
                }
                hasCoarse -> {
                    vertexCount = writeGrid(coarseUv, 0f, 0f, substeps, bulge, vertexCount, vertices)
                    indexCount =
                        writeIndices(vertexCount - stride * stride, pattern, indexCount, indices)
                }
            }
        }

        vertices.flip()
        indices.flip()
        return TileMeshResult(vertexCount, indexCount, nextExpiry)
    }

    /**
     * Fills the per-tile scratch — the rows' latitudes with their sines and
     * cosines, the columns' longitudes with theirs, and the grid fractions —
     * and returns the bulge factor for this tile's grid.
     */
    private fun prepareTile(tile: VisibleTile, substeps: Int): Float {
        val numTiles = (1 shl tile.z).toFloat()
        for (i in 0..substeps) {
            fraction[i] = i.toFloat() / substeps
            // In Double, in and out: see the Double overload for why a Float
            // row is up to a third of a grid step off at z18 near the poles.
            rowLat[i] = Quadtree.tileYToLat(tile.y.toDouble() + fraction[i], numTiles)
            rowCosLat[i] = cos(rowLat[i] * DEG_TO_RAD_D).toFloat()
        }

        // **Circumscribe, do not inscribe.**
        //
        // A grid of points *on* the unit sphere gives quads whose flat faces
        // sag inside it, by 1 - cos(half a step). At z0 a step is 22.5 deg and
        // that sag is 0.019 - twenty times the 0.001 the backdrop sphere sits
        // below the surface - so the backdrop pokes through the imagery in a
        // scatter of grey triangles. Pushing every vertex out by 1/cos(half a
        // step) puts the *middle* of each face on the unit sphere instead of
        // its corners, which is where the sphere the camera math assumes
        // actually is.
        val lonStep = ((tile.lonMax - tile.lonMin) / substeps) * DEG_TO_RAD
        var maxHalfDiagonal = 0f
        for (sy in 1..substeps) {
            val nsStep = (abs(rowLat[sy - 1] - rowLat[sy]) * DEG_TO_RAD_D).toFloat()
            // A quad's east-west arc shrinks with latitude, so its widest end
            // is the one nearer the equator. Mercator rows are not evenly spaced
            // in latitude either, which is why this is measured rather than
            // divided out: at z0 the row against the pole is twice the one at
            // the equator.
            val ewStep = lonStep * max(rowCosLat[sy - 1], rowCosLat[sy])
            maxHalfDiagonal = max(
                maxHalfDiagonal,
                0.5f * sqrt(ewStep * ewStep + nsStep * nsStep),
            )
        }
        val bulge = 1f / cos(maxHalfDiagonal)

        // Web Mercator stops at 85.05 deg, so the top and bottom rows of tiles
        // leave a cap of bare sphere at each pole - a grey ellipse sitting on
        // the globe where the ice should be. The outermost row of vertices is
        // pushed to the pole itself and keeps its own v, which stretches that
        // tile's last row of texels over the cap. It is the standard answer and
        // it is honest at this scale: the imagery it repeats is ice either way.
        // Applied after the bulge, which is about the grid's real spacing.
        if (tile.y == 0) rowLat[0] = POLE_LATITUDE
        if (tile.y == (1 shl tile.z) - 1) rowLat[substeps] = -POLE_LATITUDE
        for (sy in 0..substeps) {
            val lat = rowLat[sy] * DEG_TO_RAD_D
            rowSinLat[sy] = sin(lat).toFloat()
            rowCosLat[sy] = cos(lat).toFloat()
        }
        val lonSpan = tile.lonMax - tile.lonMin
        for (sx in 0..substeps) {
            val lon = (tile.lonMin + fraction[sx] * lonSpan) * DEG_TO_RAD
            colSinLon[sx] = sin(lon)
            colCosLon[sx] = cos(lon)
        }
        return bulge
    }

    /** Writes one tessellated tile quad from the prepared scratch and returns the new vertex count. */
    private fun writeGrid(
        uv: FloatArray,
        arrival: Float,
        layerFlag: Float,
        substeps: Int,
        bulge: Float,
        vertexCount: Int,
        vertices: ByteBuffer,
    ): Int {
        val u0 = uv[0]
        val v0 = uv[1]
        val du = uv[2] - u0
        val dv = uv[3] - v0
        for (sy in 0..substeps) {
            val v = v0 + fraction[sy] * dv
            val y = rowSinLat[sy] * bulge
            val ring = rowCosLat[sy] * bulge
            for (sx in 0..substeps) {
                vertices.putFloat(ring * colSinLon[sx])
                vertices.putFloat(y)
                vertices.putFloat(ring * colCosLon[sx])
                vertices.putFloat(u0 + fraction[sx] * du)
                vertices.putFloat(v)
                vertices.putFloat(arrival)
                vertices.putFloat(layerFlag)
            }
        }
        return vertexCount + (substeps + 1) * (substeps + 1)
    }

    /** The triangle indices of one `substeps × substeps` grid, relative to its first vertex. */
    private fun buildIndexPattern(substeps: Int): ShortArray {
        val stride = substeps + 1
        val pattern = ShortArray(substeps * substeps * 6)
        var n = 0
        for (sy in 0 until substeps) {
            for (sx in 0 until substeps) {
                val i = sy * stride + sx
                // Wound counter-clockwise as seen from outside the sphere, which
                // is what `culling : back` in the material expects.
                pattern[n++] = i.toShort()
                pattern[n++] = (i + stride).toShort()
                pattern[n++] = (i + 1).toShort()
                pattern[n++] = (i + 1).toShort()
                pattern[n++] = (i + stride).toShort()
                pattern[n++] = (i + stride + 1).toShort()
            }
        }
        return pattern
    }

    private fun writeIndices(
        base: Int,
        pattern: ShortArray,
        indexCount: Int,
        indices: ByteBuffer,
    ): Int {
        for (offset in pattern) indices.putShort((base + offset).toShort())
        return indexCount + pattern.size
    }

    /**
     * Fills [coarseUv] with the deepest resident **ancestor**'s rectangle,
     * cropped to [tile]'s quadrant of it.
     *
     * Strictly an ancestor: the tile's own texture is resolved separately,
     * because when both exist they are the two layers of the crossfade.
     */
    private fun resolveAncestorUv(tile: VisibleTile, atlas: TileAtlas): Boolean {
        var z = tile.z
        var x = tile.x
        var y = tile.y
        while (z > 0) {
            z--
            x /= 2
            y /= 2
            if (!atlas.uvRect(TileKey.of(z, x, y), coarseUv)) continue
            // Which sub-square of the ancestor this tile occupies.
            val span = 1 shl (tile.z - z)
            val dx = (tile.x and (span - 1)).toFloat() / span
            val dy = (tile.y and (span - 1)).toFloat() / span
            val u0 = coarseUv[0]
            val v0 = coarseUv[1]
            val du = coarseUv[2] - u0
            val dv = coarseUv[3] - v0
            coarseUv[0] = u0 + dx * du
            coarseUv[1] = v0 + dy * dv
            coarseUv[2] = u0 + (dx + 1f / span) * du
            coarseUv[3] = v0 + (dy + 1f / span) * dv
            return true
        }
        return false
    }

    /** Where the sphere ends and Web Mercator does not reach. */
    private const val POLE_LATITUDE = 90.0

    private const val DEG_TO_RAD_D = Math.PI / 180.0
    private const val DEG_TO_RAD = DEG_TO_RAD_D.toFloat()

    /**
     * A UV sphere for the backdrop, written into [vertices] and [indices].
     *
     * Built once. It is drawn just inside the tiles, so it needs enough
     * segments that its silhouette is a circle rather than a polygon at the
     * closest the camera gets to the limb — but it carries no texture, so the
     * cost of extra segments is only bandwidth.
     *
     * @return `vertexCount to indexCount`
     */
    fun buildBackdrop(
        radius: Float,
        rings: Int,
        segments: Int,
        vertices: ByteBuffer,
        indices: ByteBuffer,
    ): Pair<Int, Int> {
        vertices.clear()
        indices.clear()

        for (r in 0..rings) {
            val phi = PI * r / rings
            val y = cos(phi).toFloat()
            val ringRadius = sin(phi).toFloat()
            for (s in 0..segments) {
                val theta = 2.0 * PI * s / segments
                vertices.putFloat(radius * ringRadius * sin(theta).toFloat())
                vertices.putFloat(radius * y)
                vertices.putFloat(radius * ringRadius * cos(theta).toFloat())
            }
        }
        val stride = segments + 1
        for (r in 0 until rings) {
            for (s in 0 until segments) {
                val i = r * stride + s
                indices.putShort(i.toShort())
                indices.putShort((i + stride).toShort())
                indices.putShort((i + 1).toShort())
                indices.putShort((i + 1).toShort())
                indices.putShort((i + stride).toShort())
                indices.putShort((i + stride + 1).toShort())
            }
        }

        val vertexCount = (rings + 1) * stride
        val indexCount = rings * segments * 6
        vertices.flip()
        indices.flip()
        return vertexCount to indexCount
    }
}
