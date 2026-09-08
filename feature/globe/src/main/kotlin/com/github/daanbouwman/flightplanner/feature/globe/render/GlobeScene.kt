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
 * ### Two shared renderables, and why not more
 *
 * The backdrop sphere and the tile mesh. The tile mesh is **one** primitive
 * covering every visible tile, which is what the single atlas buys: with a
 * texture per tile it would have to be one draw call per tile and the visible
 * set would cost a couple of hundred state changes a frame.
 *
 * The route ribbon is **not** here. It is per-surface — see [RouteRibbon] and
 * [createRibbon] — because unlike the mesh it is a function of the camera
 * looking at it.
 *
 * The DEP and DEST markers are deliberately *not* here. Per the design they are
 * Compose — a dot and its code on one plate, anchored to a CPU-projected
 * position — because they are the one part of the globe that has to be
 * announced to TalkBack, and a `SurfaceView` announces nothing.
 *
 * ### What is rebuilt when
 *
 * The camera matrices change every frame. The tile mesh is rebuilt only when the
 * **visible set or the atlas** changes, which during a slow drag is a few times
 * a second: positions are on the unit sphere and the limb fade lives in the
 * material, so nothing in that mesh is a function of where the camera is. Each
 * surface's ribbon is rebuilt when *its* camera, viewport or the arcs change.
 *
 * ### One driver
 *
 * More than one surface can be attached to this scene at a time — see
 * [drivesUpdates]. Exactly one of them runs [update]; every one of them runs
 * [applyCamera] and its own [RouteRibbon] with its own camera.
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

        /**
         * How long one frame may spend handing decoded tiles to the driver.
         *
         * A time budget rather than a count. It was six tiles a frame, and a
         * 60-frame zoom left 27 frames — nearly half a second — of catch-up
         * after the fingers lifted, the globe still sharpening on a picture that
         * had stopped moving. Each upload only sharpens a tile that already has
         * a coarse ancestor drawn under it, so spreading them out buys
         * smoothness; but the right amount to spread is however many fit in the
         * slack of a frame, not a number picked once. Three milliseconds leaves
         * the rest of a 16 ms frame for the traversal and the mesh. At least one
         * tile goes through per frame regardless, so a slow frame still makes
         * progress.
         */
        private const val UPLOAD_BUDGET_NANOS = 3_000_000L

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

    private val tileVertexRing =
        BufferRing(GlobeMesh.MAX_TILE_VERTICES * GlobeMesh.TILE_VERTEX_BYTES)
    private val tileIndexRing = BufferRing(GlobeMesh.MAX_TILE_INDICES * 2)

    private val entityManager = EntityManager.get()
    private val backdropEntity = entityManager.create()
    private val tileEntity = entityManager.create()

    val scene: Scene = engine.createScene()

    /** Set once the first tile mesh has been built — what G8's crossfade waits on. */
    var hasDrawnImagery: Boolean = false
        private set


    /** The visible set as of the last [update], for a surface that is not driving it. */
    var visibleTiles: List<VisibleTile> = emptyList()
        private set

    private var lastVisibleSignature: Long = Long.MIN_VALUE
    private var atlasGeneration: Int = 0
    private var lastBuiltGeneration: Int = -1

    /**
     * Bumped every time the tile mesh is rebuilt.
     *
     * A surface compares it with the value it last drew, so a view that is not
     * driving [update] still redraws when the driver's frame changes the
     * geometry under it — otherwise it would settle on a mesh that was current
     * a frame ago and stay there.
     */
    var meshGeneration: Int = 0
        private set

    /**
     * A tile-mesh rebuild was owed and could not run because every ring buffer
     * was still with the driver, so the next frame must try again.
     *
     * Set **only** there — never on the "nothing to draw" branch. That branch is
     * every frame before the first tile lands, and treating it as owed work
     * would spin the loop at 60 Hz offline forever.
     */
    private var meshDirty = false


    /** The arcs every attached surface draws, in its own ribbon. */
    var routeArcs: List<Array<Vec3>> = emptyList()
        private set

    /** Bumped by [setArcs], so a ribbon can tell a new subject from a redraw. */
    var arcsGeneration: Int = 0
        private set


    /** The traversal's callbacks, allocated once rather than per frame. */
    private val requestTile: (Int, Int, Int) -> Unit = { z, x, y -> loader.request(TileKey.of(z, x, y)) }
    private val prefetchTile: (Int, Int, Int) -> Unit = { z, x, y -> loader.prefetch(TileKey.of(z, x, y)) }

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
     * The backdrop's own storage, held for the life of the scene.
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
            // Shared: every attached surface renders it.
            .layerMask(LAYER_MASK_ALL, LAYER_SHARED)
            .build(engine, backdropEntity)

        RenderableManager.Builder(1)
            .boundingBox(bounds)
            .geometry(0, RenderableManager.PrimitiveType.TRIANGLES, tileVertices, tileIndices, 0, 0)
            .material(0, tileInstance)
            .culling(false)
            .priority(PRIORITY_TILES)
            .layerMask(LAYER_MASK_ALL, LAYER_SHARED)
            .build(engine, tileEntity)

        scene.addEntity(backdropEntity)
        scene.addEntity(tileEntity)
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
        arcsGeneration++
    }

    /** The themed colours the sphere and the arc are drawn in. */
    fun setInk(ink: GlobeInk) {
        backdropInstance.setParameter(
            "baseColor",
            ink.backdrop.red, ink.backdrop.green, ink.backdrop.blue, 1f,
        )
        tileInstance.setParameter("tint", ink.imageryDim, ink.imageryDim, ink.imageryDim, 1f)
        // Held so a ribbon created after the theme was set still gets it, and
        // pushed to the ones that already exist.
        currentInk = ink
        ribbons.forEach { it.setInk(ink) }
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
     * already hold - rather than threaded through either. The surface folds the
     * counter into its settled test, so a theme flip on a still globe is drawn
     * rather than merely applied to a renderer that then skips the frame.
     */
    // A DoubleArray because that is what `Renderer.ClearOptions.clearColor` is.
    var spaceColor: DoubleArray = doubleArrayOf(0.0, 0.0, 0.0, 1.0)
        private set

    var spaceGeneration: Int = 0
        private set

    /**
     * The one surface whose frame runs [update].
     *
     * During the hero-to-immersive transition, and again under a predictive
     * back, two surfaces are attached to this scene at once — and there is one
     * tile mesh and one visible-set signature between them. Both calling
     * [update] with their own cameras had them rebuilding the mesh out from
     * under each other every frame. So the most recently attached surface
     * drives; the other draws what the driver built — it still points its own
     * Filament camera with [applyCamera], so it renders from its own viewpoint —
     * and whichever is left when one detaches takes over on its next frame.
     *
     * This is sound only because the mesh is camera-independent. The ribbon is
     * not, and is per-surface for exactly that reason.
     */
    private var updateOwner: Any? = null

    /**
     * A route ribbon of this surface's own, on a layer only its view renders.
     *
     * Filament gives every renderable an eight-bit layer mask and every view a
     * mask of the layers it draws; [LAYER_SHARED] carries the mesh and the
     * backdrop, and each ribbon takes one of the remaining seven bits. The
     * caller is responsible for telling its view which bits to render and for
     * calling [RouteRibbon.destroy] on detach.
     *
     * Seven is far more than the two surfaces the app can have attached at
     * once — a route detail handing over to the immersive screen — so running
     * out means a ribbon was leaked rather than that the app grew; sharing the
     * last bit degrades to two surfaces drawing each other's arc, which is the
     * behaviour this replaced, rather than to a crash.
     */
    fun createRibbon(): RouteRibbon {
        val free = (1..7).map { 1 shl it }.firstOrNull { it and ribbonLayers == 0 } ?: (1 shl 7)
        ribbonLayers = ribbonLayers or free
        return RouteRibbon(engine, scene, overlayMaterial, free).also { ribbon ->
            ribbons += ribbon
            currentInk?.let(ribbon::setInk)
        }
    }

    /** Releases [ribbon]'s layer bit and its geometry. */
    fun destroyRibbon(ribbon: RouteRibbon) {
        if (!ribbons.remove(ribbon)) return
        ribbonLayers = ribbonLayers and ribbon.layerBit.inv()
        ribbon.destroy()
    }

    private val ribbons = mutableListOf<RouteRibbon>()
    private var ribbonLayers = LAYER_SHARED
    private var currentInk: GlobeInk? = null

    /** [owner] has attached; it drives from here on. */
    fun claimUpdates(owner: Any) {
        updateOwner = owner
    }

    /** [owner] has detached. A no-op unless it was the one driving. */
    fun releaseUpdates(owner: Any) {
        if (updateOwner === owner) updateOwner = null
    }

    /**
     * Whether [owner] runs [update] this frame. A scene nobody is driving goes
     * to the first surface that asks, which is how the survivor of a detach
     * takes over.
     */
    fun drivesUpdates(owner: Any): Boolean {
        if (updateOwner == null) updateOwner = owner
        return updateOwner === owner
    }

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

        // Forgotten before the traversal and re-armed by it: every visible tile
        // that is resting after a failure folds its due time back in as it is
        // requested, so what remains describes exactly the tiles still wanted.
        loader.resetRetryDue()
        visibleTiles = Quadtree.collectVisibleTiles(
            camera = camera,
            basis = basis,
            viewport = viewport,
            maxLod = loader.maxLevel,
            pinnedMaxLevel = atlas.pinnedMaxLevel,
            evictableSlots = atlas.evictableSlots,
            request = requestTile,
            prefetch = prefetchTile,
        )

        val signature = signatureOf(visibleTiles)
        val stale = meshDirty ||
            signature != lastVisibleSignature ||
            atlasGeneration != lastBuiltGeneration ||
            nowSeconds >= nextFadeExpiry
        if (stale) {
            meshDirty = !rebuildTileMesh()
            if (!meshDirty) {
                lastVisibleSignature = signature
                lastBuiltGeneration = atlasGeneration
            }
        }

        tileInstance.setParameter(
            "fade",
            basis.facingUnit.x, basis.facingUnit.y, basis.facingUnit.z, camera.cullThreshold(),
        )
        tileInstance.setParameter("clock", nowSeconds, sharpenSeconds, 0f, 0f)

        // The ribbon is deliberately absent here. Its width, its lift and the
        // samples it drops are all functions of the camera looking at it, so it
        // belongs to the surface rather than to the scene — see [RouteRibbon].
        return visibleTiles
    }

    /**
     * Whether the scene would draw something different from last frame.
     *
     * Tiles arriving or in flight, a sharpen crossfade still running, a mesh
     * rebuild that was owed and starved of a buffer, or a failed tile     * whose backoff has run out. It deliberately says nothing about the camera,
     * the clear colour or the mesh generation: those are the surface's to
     * compare, and the surface is the only thing that knows which it last drew.
     */
    val wantsFrame: Boolean
        get() = loader.hasWork || isFading || meshDirty || loader.retryDue()

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

    /**
     * Hands decoded tiles to the atlas, newest first, until the budget is spent.
     *
     * Newest first for the reason the loader's queue is: the tile decoded a
     * moment ago is for where the camera is, and one from the start of a pan
     * may be for a place that has scrolled off. A tile the atlas declines — no
     * evictable slot, which the current budgets make impossible — is un-held so
     * it can be asked for again rather than lost.
     */
    private fun uploadReadyTiles() {
        val deadline = System.nanoTime() + UPLOAD_BUDGET_NANOS
        do {
            val tile = loader.pollReady() ?: return
            val uploaded = atlas.upload(tile.key, tile.pixels, nowSeconds) {
                loader.recycleBuffer(tile.pixels)
            }
            if (uploaded) {
                loader.markResident(tile.key)
                atlasGeneration++
            } else {
                loader.markEvicted(tile.key)
                loader.recycleBuffer(tile.pixels)
            }
        } while (System.nanoTime() < deadline)
    }

    /**
     * A cheap identity for the visible set.
     *
     * Order-sensitive by construction, and that is harmless rather than wanted:
     * the traversal returns its leaves sorted by `(z, x, y)`, which is a pure
     * function of the visible set, so this changes when the set does and not
     * otherwise. The list used to be sorted back to front first, and the sort
     * changed the order — and forced a rebuild — on 49 of 60 frames of a slow pan
     * in which the set itself changed on 7.
     *
     * The key sort is load-bearing rather than tidy. Breadth-first order was a
     * function of the set too, by accident; the priority order that replaced it is
     * a function of continuous float priorities, so without the sort a sub-pixel
     * camera move could reorder two near-ties and rebuild geometry that had not
     * changed. See `Quadtree`'s note on order.
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

    /**
     * Rebuilds the tile mesh. Returns false **only** when the ring was starved
     * and the rebuild has to be retried; a frame with nothing to draw returns
     * true, because nothing is owed.
     */
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
            return true
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
        meshGeneration++
        hasDrawnImagery = true
        return true
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
        scene.removeEntities(intArrayOf(backdropEntity, tileEntity))
        engine.destroyEntity(backdropEntity)
        engine.destroyEntity(tileEntity)
        entityManager.destroy(backdropEntity)
        entityManager.destroy(tileEntity)
        engine.destroyVertexBuffer(tileVertices)
        engine.destroyIndexBuffer(tileIndices)
        engine.destroyVertexBuffer(backdropVertices)
        engine.destroyIndexBuffer(backdropIndices)
        engine.destroyMaterialInstance(tileInstance)
        engine.destroyMaterialInstance(backdropInstance)
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
