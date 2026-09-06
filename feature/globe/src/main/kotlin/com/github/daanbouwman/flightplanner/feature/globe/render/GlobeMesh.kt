package com.github.daanbouwman.flightplanner.feature.globe.render

import com.github.daanbouwman.flightplanner.feature.globe.math.Quadtree
import com.github.daanbouwman.flightplanner.feature.globe.math.VisibleTile
import com.github.daanbouwman.flightplanner.feature.globe.math.latLonToWorld
import com.github.daanbouwman.flightplanner.feature.globe.tile.TileAtlas
import com.github.daanbouwman.flightplanner.feature.globe.tile.TileKey
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
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
 * traversal requests every node it visits and why levels 0–3 are pinned: the
 * walk up terminates at a tile that is always resident, so a leaf is never
 * simply missing. It is the difference between a globe that sharpens and one
 * that shows holes.
 */
internal object GlobeMesh {

    /**
     * Bytes per tile vertex: position float3, uv float2, and a float2 carrying
     * when this tile's imagery arrived and whether this is the sharp layer.
     *
     * The last pair is what the material's sharpen fade reads. It is per vertex
     * rather than a uniform because a single draw call covers a hundred and
     * sixty tiles that all arrived at different moments.
     */
    const val TILE_VERTEX_BYTES: Int = 28

    /** Bytes per backdrop vertex: position float3. */
    const val POSITION_VERTEX_BYTES: Int = 12

    /**
     * Vertex ceiling for one rebuild of the tile mesh.
     *
     * **Recomputed from [substepsFor], and it has to be recomputed whenever that
     * changes.** The first version of this bound was derived from substeps of
     * 16/12/8/6, and when those were raised to 32/20/14/10/7/5 to keep the
     * circumscribing bulge small at the coarse end, the bound stayed — leaving a
     * ceiling roughly half of what the mesh can now ask for.
     *
     * The worst case is [Quadtree.MAX_VISIBLE_TILES] leaves taking the most
     * expensive levels that have enough tiles to fill them: one z0 at 33², four
     * z1 at 21², sixteen z2 at 15², sixty-four z3 at 11² and the remaining
     * seventy-five at z4’s 8², which is 18,997 — **doubled**, because during a
     * sharpen every one of those tiles can be carrying a coarse layer under it.
     * Forty thousand is that with room, and it stays well inside a `ushort`
     * index, which is the reason the bound is worth stating rather than growing
     * the buffer on demand.
     */
    const val MAX_TILE_VERTICES: Int = 40_000

    /** The matching index ceiling, by the same enumeration: 95,010 doubled. */
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
            val layers = (if (stillFading && hasCoarse) 1 else 0) + (if (hasOwn) 1 else 0) +
                (if (!hasOwn && hasCoarse) 1 else 0)
            if (layers == 0) continue
            if (vertexCount + layers * stride * stride > MAX_TILE_VERTICES) break
            if (indexCount + layers * substeps * substeps * 6 > MAX_TILE_INDICES) break

            if (stillFading && hasCoarse) {
                // The picture that is already there, held at full strength.
                vertexCount = writeGrid(tile, coarseUv, 0f, 0f, substeps, vertexCount, vertices)
                indexCount = writeIndices(vertexCount - stride * stride, substeps, indexCount, indices)
                nextExpiry = minOf(nextExpiry, arrival + fadeDurationSeconds)
            }
            when {
                hasOwn -> {
                    val layerFlag = if (stillFading && hasCoarse) 1f else 0f
                    vertexCount =
                        writeGrid(tile, sharpUv, arrival, layerFlag, substeps, vertexCount, vertices)
                    indexCount =
                        writeIndices(vertexCount - stride * stride, substeps, indexCount, indices)
                }
                hasCoarse -> {
                    vertexCount = writeGrid(tile, coarseUv, 0f, 0f, substeps, vertexCount, vertices)
                    indexCount =
                        writeIndices(vertexCount - stride * stride, substeps, indexCount, indices)
                }
            }
        }

        vertices.flip()
        indices.flip()
        return TileMeshResult(vertexCount, indexCount, nextExpiry)
    }

    /** Writes one tessellated tile quad and returns the new vertex count. */
    private fun writeGrid(
        tile: VisibleTile,
        uv: FloatArray,
        arrival: Float,
        layerFlag: Float,
        substeps: Int,
        vertexCount: Int,
        vertices: ByteBuffer,
    ): Int {
        var count = vertexCount
        val numTiles = (1 shl tile.z).toFloat()
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
        var previousLat = Quadtree.tileYToLat(tile.y.toFloat(), numTiles)
        for (sy in 1..substeps) {
            val lat = Quadtree.tileYToLat(tile.y + sy.toFloat() / substeps, numTiles)
            val nsStep = abs(previousLat - lat) * DEG_TO_RAD
            // A quad’s east-west arc shrinks with latitude, so its widest end
            // is the one nearer the equator. Mercator rows are not evenly spaced
            // in latitude either, which is why this is measured rather than
            // divided out: at z0 the row against the pole is twice the one at
            // the equator.
            val ewStep = lonStep * max(
                cos(previousLat * DEG_TO_RAD),
                cos(lat * DEG_TO_RAD),
            )
            maxHalfDiagonal = max(
                maxHalfDiagonal,
                0.5f * sqrt(ewStep * ewStep + nsStep * nsStep),
            )
            previousLat = lat
        }
        val bulge = 1f / cos(maxHalfDiagonal)
        // Web Mercator stops at 85.05 deg, so the top and bottom rows of tiles
        // leave a cap of bare sphere at each pole - a grey ellipse sitting on
        // the globe where the ice should be. The outermost row of vertices is
        // pushed to the pole itself and keeps its own v, which stretches that
        // tile’s last row of texels over the cap. It is the standard answer and
        // it is honest at this scale: the imagery it repeats is ice either way.
        val topRow = tile.y == 0
        val bottomRow = tile.y == (1 shl tile.z) - 1
        for (sy in 0..substeps) {
            val fy = sy.toFloat() / substeps
            val lat = when {
                topRow && sy == 0 -> POLE_LATITUDE
                bottomRow && sy == substeps -> -POLE_LATITUDE
                else -> Quadtree.tileYToLat(tile.y + fy, numTiles)
            }
            val v = uv[1] + fy * (uv[3] - uv[1])
            for (sx in 0..substeps) {
                val fx = sx.toFloat() / substeps
                val lon = tile.lonMin + fx * (tile.lonMax - tile.lonMin)
                val w = latLonToWorld(lat, lon)
                vertices.putFloat(w.x * bulge)
                vertices.putFloat(w.y * bulge)
                vertices.putFloat(w.z * bulge)
                vertices.putFloat(uv[0] + fx * (uv[2] - uv[0]))
                vertices.putFloat(v)
                vertices.putFloat(arrival)
                vertices.putFloat(layerFlag)
                count++
            }
        }
        return count
    }

    private fun writeIndices(
        base: Int,
        substeps: Int,
        indexCount: Int,
        indices: ByteBuffer,
    ): Int {
        val stride = substeps + 1
        var count = indexCount
        for (sy in 0 until substeps) {
            for (sx in 0 until substeps) {
                val i = base + sy * stride + sx
                // Wound counter-clockwise as seen from outside the sphere, which
                // is what `culling : back` in the material expects.
                indices.putShort(i.toShort())
                indices.putShort((i + stride).toShort())
                indices.putShort((i + 1).toShort())
                indices.putShort((i + 1).toShort())
                indices.putShort((i + stride).toShort())
                indices.putShort((i + stride + 1).toShort())
                count += 6
            }
        }
        return count
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
    /** Where the sphere ends and Web Mercator does not reach. */
    private const val POLE_LATITUDE = 90f

    private const val DEG_TO_RAD = (Math.PI / 180.0).toFloat()

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
