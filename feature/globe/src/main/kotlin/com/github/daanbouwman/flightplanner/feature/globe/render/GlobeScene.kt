package com.github.daanbouwman.flightplanner.feature.globe.render

import android.content.Context
import com.github.daanbouwman.flightplanner.feature.globe.math.CameraBasis
import com.github.daanbouwman.flightplanner.feature.globe.math.CameraMatrices
import com.github.daanbouwman.flightplanner.feature.globe.math.GlobeCamera
import com.github.daanbouwman.flightplanner.feature.globe.math.GlobeViewport
import com.github.daanbouwman.flightplanner.feature.globe.math.Quadtree
import com.github.daanbouwman.flightplanner.feature.globe.math.RibbonBuffer
import com.github.daanbouwman.flightplanner.feature.globe.math.RouteGeometry
import com.github.daanbouwman.flightplanner.feature.globe.math.Vec3
import com.github.daanbouwman.flightplanner.feature.globe.math.VisibleTile
import com.github.daanbouwman.flightplanner.feature.globe.tile.DecodedTile
import com.github.daanbouwman.flightplanner.feature.globe.tile.TileAtlas
import com.github.daanbouwman.flightplanner.feature.globe.tile.TileKey
import com.github.daanbouwman.flightplanner.feature.globe.tile.TileLoader
import com.google.android.filament.Box
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.IndexBuffer
import com.google.android.filament.Material
import com.google.android.filament.MaterialInstance
import com.google.android.filament.RenderableManager
import com.google.android.filament.Scene
import com.google.android.filament.Texture
import com.google.android.filament.TextureSampler
import com.google.android.filament.VertexBuffer
import com.google.android.filament.View
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Everything the globe draws, and the per-frame work that keeps it current.
 *
 * ### Three renderables, and why not more
 *
 * The backdrop sphere, the tile mesh and the route ribbon. The tile mesh is
 * **one** primitive covering every visible tile, which is what the single atlas
 * buys: with a texture per tile it would have to be one draw call per tile and
 * the visible set would cost a hundred and sixty state changes a frame.
 *
 * The DEP and DEST markers are deliberately *not* here. Per the design they are
 * Compose — a dot and its code on one plate, anchored to a CPU-projected
 * position — because they are the one part of the globe that has to be
 * announced to TalkBack, and a `SurfaceView` announces nothing.
 *
 * ### What is rebuilt when
 *
 * The camera matrices change every frame. The ribbon is rebuilt every frame the
 * camera moves, because its width is constant in pixels and therefore depends on
 * the projection. The tile mesh is rebuilt only when the **visible set or the
 * atlas** changes, which during a slow drag is a few times a second: positions
 * are on the unit sphere and the limb fade lives in the material, so nothing in
 * that mesh is a function of where the camera is.
 */
internal class GlobeScene(
    context: Context,
    val engine: Engine,
    private val loader: TileLoader,
) {

    companion object {
        /** Backdrop radius: just inside the imagery, so the tiles win any tie. */
        private const val BACKDROP_RADIUS = 0.999f
        private const val BACKDROP_RINGS = 48
        private const val BACKDROP_SEGMENTS = 96

        /** Ribbon half-width in pixels — the arc's weight on the glass. */
        private const val ARC_HALF_WIDTH_PX = 1.6f

        /** The casing under the arc, wider, in the surface's own colour — the
         * technique an aeronautical chart uses to keep a route legible wherever
         * it crosses something else. See [GlobeInk.routeCasing]. */
        private const val ARC_CASING_HALF_WIDTH_PX = 3.4f

        /**
         * Tiles uploaded per frame.
         *
         * An upload is a synchronous texture write, so draining a whole pan's
         * worth in one frame is a visible hitch while spreading them over a few
         * frames is not — each one only sharpens a tile that already has a
         * coarse ancestor drawn under it.
         */
        private const val UPLOADS_PER_FRAME = 6

        /**
         * The ribbon budget, in vertices, for **both** subjects.
         *
         * It used to be `SAMPLES * 2 + 8` — two vertices per point of one
         * 256-point arc. That was right while the only subject was a leg, and it
         * became a crash the moment [setArcs] took a list: the visited network
         * feeds every leg in the logbook, sampled at 32 points each, so eight
         * legs is 526 vertices and the eighth one overflowed the upload buffer
         * inside a Choreographer callback.
         *
         * 16,384 holds a logbook of roughly 250 distinct legs at 66 vertices
         * apiece — 64 for the run and two for the degenerate bridge to the next
         * one. Past that [RibbonBuffer] stops accepting vertices rather than
         * growing, so a larger logbook loses its last few arcs instead of
         * throwing. The cost is 196 kB a buffer, and the ring holds three of
         * them for each of the arc and its casing.
         */
        private const val MAX_RIBBON_VERTICES = 16_384

        /**
         * The effects-fast spring's sampled duration, in seconds.
         *
         * Quoted from the motion table rather than re-picked here: a shader
         * cannot call [com.github.daanbouwman.flightplanner.core.designsystem.motion.FlightMotion],
         * so this is the one place in the app where a duration is written down.
         * It is the token's value, and the comment is what keeps it honest.
         */
        private const val DEFAULT_SHARPEN_SECONDS = 0.166f
    }

    val atlas = TileAtlas(engine)

    private val tileMaterial = loadMaterial(context, "materials/globe_tile.filamat")
    private val solidMaterial = loadMaterial(context, "materials/globe_solid.filamat")
    private val overlayMaterial = loadMaterial(context, "materials/globe_overlay.filamat")

    private val tileInstance: MaterialInstance = tileMaterial.createInstance()
    private val backdropInstance: MaterialInstance = solidMaterial.createInstance()
    private val arcInstance: MaterialInstance = overlayMaterial.createInstance()
    private val arcCasingInstance: MaterialInstance = overlayMaterial.createInstance()

    private val tileVertices = VertexBuffer.Builder()
        .bufferCount(1)
        .vertexCount(GlobeMesh.MAX_TILE_VERTICES)
        .attribute(
            VertexBuffer.VertexAttribute.POSITION, 0,
            VertexBuffer.AttributeType.FLOAT3, 0, GlobeMesh.TILE_VERTEX_BYTES,
        )
        .attribute(
            VertexBuffer.VertexAttribute.UV0, 0,
            VertexBuffer.AttributeType.FLOAT2, 12, GlobeMesh.TILE_VERTEX_BYTES,
        )
        // When this tile's imagery arrived, and whether this is the sharp layer
        // or the coarse one under it. Read by the material's sharpen fade.
        .attribute(
            VertexBuffer.VertexAttribute.CUSTOM0, 0,
            VertexBuffer.AttributeType.FLOAT2, 20, GlobeMesh.TILE_VERTEX_BYTES,
        )
        .build(engine)

    private val tileIndices = IndexBuffer.Builder()
        .indexCount(GlobeMesh.MAX_TILE_INDICES)
        .bufferType(IndexBuffer.Builder.IndexType.USHORT)
        .build(engine)

    private val backdropVertices: VertexBuffer
    private val backdropIndices: IndexBuffer
    private val backdropIndexCount: Int

    private val ribbonVertices = VertexBuffer.Builder()
        .bufferCount(1)
        .vertexCount(MAX_RIBBON_VERTICES)
        .attribute(
            VertexBuffer.VertexAttribute.POSITION, 0,
            VertexBuffer.AttributeType.FLOAT3, 0, GlobeMesh.POSITION_VERTEX_BYTES,
        )
        .build(engine)

    private val casingVertices = VertexBuffer.Builder()
        .bufferCount(1)
        .vertexCount(MAX_RIBBON_VERTICES)
        .attribute(
            VertexBuffer.VertexAttribute.POSITION, 0,
            VertexBuffer.AttributeType.FLOAT3, 0, GlobeMesh.POSITION_VERTEX_BYTES,
        )
        .build(engine)

    private val tileVertexRing =
        BufferRing(GlobeMesh.MAX_TILE_VERTICES * GlobeMesh.TILE_VERTEX_BYTES)
    private val tileIndexRing = BufferRing(GlobeMesh.MAX_TILE_INDICES * 2)
    private val ribbonRing = BufferRing(MAX_RIBBON_VERTICES * GlobeMesh.POSITION_VERTEX_BYTES)
    private val casingRing = BufferRing(MAX_RIBBON_VERTICES * GlobeMesh.POSITION_VERTEX_BYTES)

    private val entityManager = EntityManager.get()
    private val backdropEntity = entityManager.create()
    private val tileEntity = entityManager.create()
    private val arcEntity = entityManager.create()
    private val casingEntity = entityManager.create()

    val scene: Scene = engine.createScene()

    /** Set once the first tile mesh has been built — what G8's crossfade waits on. */
    var hasDrawnImagery: Boolean = false
        private set

    private val ribbon = RibbonBuffer(MAX_RIBBON_VERTICES)
    private val casingRibbon = RibbonBuffer(MAX_RIBBON_VERTICES)
    private val decodedScratch = ArrayList<DecodedTile>(UPLOADS_PER_FRAME)

    private var routeArcs: List<Array<Vec3>> = emptyList()
    private var visibleTiles: List<VisibleTile> = emptyList()
    private var lastVisibleSignature: Long = Long.MIN_VALUE
    private var atlasGeneration: Int = 0
    private var lastBuiltGeneration: Int = -1

    /** Seconds since the scene was created — the clock both fades read. */
    private val startNanos = System.nanoTime()
    private var nowSeconds = 0f

    /**
     * How long a tile takes to sharpen over its coarse ancestor.
     *
     * Zero switches the crossfade off entirely, which is what reduce motion
     * asks for. The default is the effects-fast token's sampled duration.
     */
    var sharpenSeconds: Float = DEFAULT_SHARPEN_SECONDS

    /** When the running sharpen fades finish, so the coarse layers can be retired. */
    private var nextFadeExpiry: Float = Float.MAX_VALUE

    /**
     * The backdrop’s own storage, held for the life of the scene.
     *
     * **Fields, not locals, and that is the whole point of them.** Filament does
     * not copy a buffer handed to `setBufferAt`: it keeps the address. A direct
     * `ByteBuffer` that goes out of scope is collectable the moment it does, and
     * its native storage goes with it - so the sphere renders perfectly until the
     * first garbage collection and then quietly stops, which reads as a driver
     * fault rather than as a Java lifetime one. The geometry ring buffers are safe
     * for the same reason these now are: something on the heap still points at them.
     */
    private val backdropVertexBytes: ByteBuffer = ByteBuffer
        .allocateDirect((BACKDROP_RINGS + 1) * (BACKDROP_SEGMENTS + 1) * GlobeMesh.POSITION_VERTEX_BYTES)
        .order(ByteOrder.nativeOrder())

    private val backdropIndexBytes: ByteBuffer = ByteBuffer
        .allocateDirect(BACKDROP_RINGS * BACKDROP_SEGMENTS * 6 * 2)
        .order(ByteOrder.nativeOrder())

    init {
        val vertexBytes = backdropVertexBytes
        val indexBytes = backdropIndexBytes
        val (vertexCount, indexCount) = GlobeMesh.buildBackdrop(
            radius = BACKDROP_RADIUS,
            rings = BACKDROP_RINGS,
            segments = BACKDROP_SEGMENTS,
            vertices = vertexBytes,
            indices = indexBytes,
        )
        backdropIndexCount = indexCount
        backdropVertices = VertexBuffer.Builder()
            .bufferCount(1)
            .vertexCount(vertexCount)
            .attribute(
                VertexBuffer.VertexAttribute.POSITION, 0,
                VertexBuffer.AttributeType.FLOAT3, 0, GlobeMesh.POSITION_VERTEX_BYTES,
            )
            .build(engine)
        backdropVertices.setBufferAt(engine, 0, vertexBytes)
        backdropIndices = IndexBuffer.Builder()
            .indexCount(indexCount)
            .bufferType(IndexBuffer.Builder.IndexType.USHORT)
            .build(engine)
        backdropIndices.setBuffer(engine, indexBytes)

        tileInstance.setParameter("atlas", atlas.texture, ATLAS_SAMPLER)
        atlas.onEvicted = { loader.markEvicted(it) }

        // The whole scene lives inside a sphere a little over unit radius, so
        // one box serves every renderable and frustum culling is switched off:
        // there are four of them and they are all always on screen.
        val bounds = Box(0f, 0f, 0f, 1.1f, 1.1f, 1.1f)

        RenderableManager.Builder(1)
            .boundingBox(bounds)
            .geometry(
                0, RenderableManager.PrimitiveType.TRIANGLES,
                backdropVertices, backdropIndices, 0, indexCount,
            )
            .material(0, backdropInstance)
            .culling(false)
            .priority(PRIORITY_BACKDROP)
            .build(engine, backdropEntity)

        RenderableManager.Builder(1)
            .boundingBox(bounds)
            .geometry(0, RenderableManager.PrimitiveType.TRIANGLES, tileVertices, tileIndices, 0, 0)
            .material(0, tileInstance)
            .culling(false)
            .priority(PRIORITY_TILES)
            .build(engine, tileEntity)

        RenderableManager.Builder(1)
            .boundingBox(bounds)
            .geometry(0, RenderableManager.PrimitiveType.TRIANGLE_STRIP, casingVertices, 0, 0)
            .material(0, arcCasingInstance)
            .culling(false)
            .priority(PRIORITY_ARC_CASING)
            .build(engine, casingEntity)

        RenderableManager.Builder(1)
            .boundingBox(bounds)
            .geometry(0, RenderableManager.PrimitiveType.TRIANGLE_STRIP, ribbonVertices, 0, 0)
            .material(0, arcInstance)
            .culling(false)
            .priority(PRIORITY_ARC)
            .build(engine, arcEntity)

        scene.addEntity(backdropEntity)
        scene.addEntity(tileEntity)
        scene.addEntity(casingEntity)
        scene.addEntity(arcEntity)
    }

    /**
     * The arcs to draw, as sampled geography. Empty clears them.
     *
     * A list rather than one leg, because the visited-network card draws the
     * whole logbook on the same sphere — and they all become one triangle
     * strip, so two hundred flights cost one draw call rather than two hundred.
     */
    fun setArcs(arcs: List<Pair<DoubleArray, DoubleArray>>) {
        routeArcs = arcs.mapNotNull { (lats, lons) ->
            if (lats.size < 2) null else RouteGeometry.toWorldPoints(lats, lons)
        }
    }

    /** The themed colours the sphere and the arc are drawn in. */
    fun setInk(ink: GlobeInk) {
        backdropInstance.setParameter(
            "baseColor",
            ink.backdrop.red, ink.backdrop.green, ink.backdrop.blue, 1f,
        )
        arcInstance.setParameter(
            "baseColor",
            ink.route.red, ink.route.green, ink.route.blue, ink.route.alpha,
        )
        arcCasingInstance.setParameter(
            "baseColor",
            ink.routeCasing.red, ink.routeCasing.green, ink.routeCasing.blue, ink.routeCasing.alpha,
        )
        tileInstance.setParameter("tint", ink.imageryDim, ink.imageryDim, ink.imageryDim, 1f)
        spaceColor = doubleArrayOf(
            ink.space.red.toDouble(),
            ink.space.green.toDouble(),
            ink.space.blue.toDouble(),
            1.0,
        )
        spaceGeneration++
    }

    /**
     * The clear colour, and a counter the surface watches.
     *
     * The renderer belongs to [GlobeSurfaceView] and the theme belongs to
     * Compose, so the colour is parked here - the one object both of them
     * already hold - rather than threaded through either.
     */
    // A DoubleArray because that is what `Renderer.ClearOptions.clearColor` is.
    var spaceColor: DoubleArray = doubleArrayOf(0.0, 0.0, 0.0, 1.0)
        private set

    var spaceGeneration: Int = 0
        private set


    /**
     * Brings the scene up to date for one frame.
     *
     * Returns the visible tile list, which the caller needs for the diagnostics
     * overlay and which the label layer does not — labels project their own two
     * points and are not part of the tile pipeline at all.
     */
    fun update(camera: GlobeCamera, basis: CameraBasis, viewport: GlobeViewport): List<VisibleTile> {
        nowSeconds = (System.nanoTime() - startNanos) / 1e9f
        uploadReadyTiles()

        visibleTiles = Quadtree.collectVisibleTiles(camera, basis, viewport) { z, x, y ->
            loader.request(TileKey.of(z, x, y))
        }

        val signature = signatureOf(visibleTiles)
        val stale = signature != lastVisibleSignature ||
            atlasGeneration != lastBuiltGeneration ||
            nowSeconds >= nextFadeExpiry
        if (stale && rebuildTileMesh()) {
            lastVisibleSignature = signature
            lastBuiltGeneration = atlasGeneration
        }

        tileInstance.setParameter(
            "fade",
            basis.facingUnit.x, basis.facingUnit.y, basis.facingUnit.z, camera.cullThreshold(),
        )
        tileInstance.setParameter("clock", nowSeconds, sharpenSeconds, 0f, 0f)

        rebuildRibbon(camera, basis, viewport)
        return visibleTiles
    }


    /**
     * Whether the scene would draw something different from last frame.
     *
     * Tiles arriving, tiles still being fetched, or a sharpen crossfade that
     * has not run out. It deliberately says nothing about the camera: that is
     * the surface’s to compare, and the surface is the only thing that knows
     * which camera it last rendered.
     */
    val wantsFrame: Boolean
        get() = loader.hasWork || isFading

    /**
     * Whether a sharpen crossfade is still running.
     *
     * [nextFadeExpiry] is `Float.MAX_VALUE` when no tile is fading, so comparing
     * the clock against it directly is always true and the scene never settles —
     * which is exactly the way this was wrong the first time, and it looked like
     * the frame loop simply ignoring the new gate.
     */
    private val isFading: Boolean
        get() = nextFadeExpiry != Float.MAX_VALUE && nowSeconds < nextFadeExpiry

    /** Points the Filament camera the way [GlobeCamera] says. */
    fun applyCamera(
        filamentCamera: com.google.android.filament.Camera,
        camera: GlobeCamera,
        viewport: GlobeViewport,
    ) {
        filamentCamera.setCustomProjection(
            CameraMatrices.projection(camera, viewport),
            CameraMatrices.nearPlane(camera),
            CameraMatrices.farPlane(camera),
        )
        filamentCamera.setModelMatrix(CameraMatrices.model(camera))
    }

    private fun uploadReadyTiles() {
        loader.drainReady(UPLOADS_PER_FRAME, decodedScratch)
        for (tile in decodedScratch) {
            val uploaded = atlas.upload(tile.key, tile.pixels, nowSeconds) {
                loader.recycleBuffer(tile.pixels)
            }
            if (uploaded) {
                loader.markResident(tile.key)
                atlasGeneration++
            } else {
                loader.recycleBuffer(tile.pixels)
            }
        }
        decodedScratch.clear()
    }

    /**
     * A cheap identity for the visible set.
     *
     * Order-sensitive by construction, which is what is wanted: the list is
     * sorted back to front, so a change of order is a change of draw order and
     * the mesh has to be rebuilt for it.
     */
    private fun signatureOf(tiles: List<VisibleTile>): Long {
        var hash = 1125899906842597L
        for (tile in tiles) {
            hash = hash * 31 + tile.z
            hash = hash * 31 + tile.x
            hash = hash * 31 + tile.y
        }
        return hash
    }

    private fun rebuildTileMesh(): Boolean {
        val vertexBytes = tileVertexRing.acquire() ?: return false
        val indexBytes = tileIndexRing.acquire() ?: run {
            // The vertex buffer was already taken. Give it back, or the ring
            // loses one buffer per frame that lands here.
            tileVertexRing.release(vertexBytes)
            return false
        }

        val mesh = GlobeMesh.buildTiles(
            visibleTiles, atlas, nowSeconds, sharpenSeconds, vertexBytes, indexBytes,
        )
        val vertexCount = mesh.vertexCount
        val indexCount = mesh.indexCount
        nextFadeExpiry = mesh.nextFadeExpiry
        if (vertexCount == 0 || indexCount == 0) {
            // Nothing to draw this frame - the first frames, before any tile
            // has imagery, are all like this. Both buffers go back: they were
            // never handed to the driver, so no callback will ever return them.
            tileVertexRing.release(vertexBytes)
            tileIndexRing.release(indexBytes)
            return false
        }

        val (vertexHandler, vertexCallback) = tileVertexRing.releaseCallback(vertexBytes)
        tileVertices.setBufferAt(
            engine, 0, vertexBytes, 0, vertexCount * GlobeMesh.TILE_VERTEX_BYTES,
            vertexHandler, vertexCallback,
        )
        val (indexHandler, indexCallback) = tileIndexRing.releaseCallback(indexBytes)
        tileIndices.setBuffer(engine, indexBytes, 0, indexCount * 2, indexHandler, indexCallback)

        renderableManager().setGeometryAt(
            renderableManager().getInstance(tileEntity),
            0,
            RenderableManager.PrimitiveType.TRIANGLES,
            tileVertices,
            tileIndices,
            0,
            indexCount,
        )
        hasDrawnImagery = true
        return true
    }

    private fun rebuildRibbon(
        camera: GlobeCamera,
        basis: CameraBasis,
        viewport: GlobeViewport,
    ) {
        if (routeArcs.isEmpty()) {
            setStripCount(arcEntity, ribbonVertices, 0)
            setStripCount(casingEntity, casingVertices, 0)
            return
        }
        uploadStrip(
            RouteGeometry.ribbonInto(
                routeArcs, camera, basis, viewport, ARC_CASING_HALF_WIDTH_PX, casingRibbon,
            ),
            casingRibbon, casingRing, casingVertices, casingEntity,
        )
        uploadStrip(
            RouteGeometry.ribbonInto(
                routeArcs, camera, basis, viewport, ARC_HALF_WIDTH_PX, ribbon,
            ),
            ribbon, ribbonRing, ribbonVertices, arcEntity,
        )
    }

    private fun uploadStrip(
        vertexCount: Int,
        source: RibbonBuffer,
        ring: BufferRing,
        buffer: VertexBuffer,
        entity: Int,
    ) {
        if (vertexCount < 3) {
            setStripCount(entity, buffer, 0)
            return
        }
        val bytes = ring.acquire() ?: return
        bytes.asFloatBuffer().put(source.positions, 0, vertexCount * 3)
        val (handler, callback) = ring.releaseCallback(bytes)
        buffer.setBufferAt(
            engine, 0, bytes, 0, vertexCount * GlobeMesh.POSITION_VERTEX_BYTES, handler, callback,
        )
        setStripCount(entity, buffer, vertexCount)
    }

    private fun setStripCount(entity: Int, buffer: VertexBuffer, count: Int) {
        renderableManager().setGeometryAt(
            renderableManager().getInstance(entity),
            0,
            RenderableManager.PrimitiveType.TRIANGLE_STRIP,
            buffer,
            0,
            count,
        )
    }

    private fun renderableManager(): RenderableManager = engine.renderableManager

    fun configureView(view: View) {
        view.scene = scene
        // No bloom, no tone mapping, no anti-alias pass. The globe is imagery and
        // two flat overlays: post-processing would spend milliseconds a frame
        // changing colours that are already the ones the theme chose.
        view.isPostProcessingEnabled = false
        view.blendMode = View.BlendMode.OPAQUE
    }

    fun destroy() {
        scene.removeEntities(intArrayOf(backdropEntity, tileEntity, arcEntity, casingEntity))
        engine.destroyEntity(backdropEntity)
        engine.destroyEntity(tileEntity)
        engine.destroyEntity(arcEntity)
        engine.destroyEntity(casingEntity)
        entityManager.destroy(backdropEntity)
        entityManager.destroy(tileEntity)
        entityManager.destroy(arcEntity)
        entityManager.destroy(casingEntity)

        engine.destroyVertexBuffer(tileVertices)
        engine.destroyIndexBuffer(tileIndices)
        engine.destroyVertexBuffer(backdropVertices)
        engine.destroyIndexBuffer(backdropIndices)
        engine.destroyVertexBuffer(ribbonVertices)
        engine.destroyVertexBuffer(casingVertices)

        engine.destroyMaterialInstance(tileInstance)
        engine.destroyMaterialInstance(backdropInstance)
        engine.destroyMaterialInstance(arcInstance)
        engine.destroyMaterialInstance(arcCasingInstance)
        engine.destroyMaterial(tileMaterial)
        engine.destroyMaterial(solidMaterial)
        engine.destroyMaterial(overlayMaterial)

        atlas.destroy()
        engine.destroyScene(scene)
    }

    private fun loadMaterial(context: Context, assetPath: String): Material {
        val bytes = context.assets.open(assetPath).use { it.readBytes() }
        val buffer = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder())
        buffer.put(bytes)
        buffer.flip()
        return Material.Builder().payload(buffer, buffer.remaining()).build(engine)
    }
}

/**
 * Draw order within the scene.
 *
 * Filament sorts transparent geometry back to front by depth, which is right for
 * the tiles among themselves and wrong for the four layers here — the arc's
 * casing and the arc itself are the same geometry at the same depth, so their
 * order has to be stated rather than derived. Lower draws first.
 */
private const val PRIORITY_BACKDROP: Int = 0
private const val PRIORITY_TILES: Int = 1
private const val PRIORITY_ARC_CASING: Int = 2
private const val PRIORITY_ARC: Int = 3

/**
 * The atlas sampler.
 *
 * Linear filtering with clamped wrapping. Clamping matters even though every UV
 * is inside the atlas: a wrapped sample at a slot's edge would fetch from the
 * far side of the texture, which is a different continent.
 */
private val ATLAS_SAMPLER = TextureSampler(
    TextureSampler.MinFilter.LINEAR,
    TextureSampler.MagFilter.LINEAR,
    TextureSampler.WrapMode.CLAMP_TO_EDGE,
)
