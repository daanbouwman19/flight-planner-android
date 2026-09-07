package com.github.daanbouwman.flightplanner.feature.globe.render

import com.github.daanbouwman.flightplanner.feature.globe.math.CameraBasis
import com.github.daanbouwman.flightplanner.feature.globe.math.GlobeCamera
import com.github.daanbouwman.flightplanner.feature.globe.math.GlobeViewport
import com.github.daanbouwman.flightplanner.feature.globe.math.RibbonBuffer
import com.github.daanbouwman.flightplanner.feature.globe.math.RouteGeometry
import com.github.daanbouwman.flightplanner.feature.globe.math.Vec3
import com.google.android.filament.Box
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.Material
import com.google.android.filament.MaterialInstance
import com.google.android.filament.RenderableManager
import com.google.android.filament.Scene
import com.google.android.filament.VertexBuffer

/**
 * One surface's copy of the route line, on its own view layer.
 *
 * ### Why this is per-surface when the tile mesh is not
 *
 * The tile mesh is a pure function of *which* tiles are visible and where their
 * imagery sits in the atlas — positions on the unit sphere, UVs into a texture —
 * so one scene can build it once and every attached surface can draw it from its
 * own camera. `GlobeScene` does exactly that, and the update owner exists to
 * make sure only one of them pays for it.
 *
 * **The ribbon has no such property.** Its width is constant in *pixels*, its
 * lift off the surface is half a *pixel*, and samples behind the camera plane
 * are dropped — three things that are functions of the camera looking at it. A
 * scene-wide ribbon built by whichever surface happens to drive is therefore
 * geometry built for somebody else's camera, and during the hero → immersive
 * transition the two cameras deliberately differ by the viewport-height rescale.
 * The non-driving surface drew a route of the wrong weight, clipped against the
 * driver's horizon rather than its own, for the length of the transition.
 *
 * So each surface owns a ribbon, and Filament's per-view visibility layers keep
 * them apart in the one shared scene: every ribbon takes a layer bit of its own,
 * every view is told to render the shared bit plus its own, and the entities
 * still cost one draw call each. The alternative — a `Scene` per surface — would
 * mean the tile mesh belonging to several scenes, which is the thing this design
 * exists to avoid.
 */
internal class RouteRibbon(
    private val engine: Engine,
    private val scene: Scene,
    material: Material,
    /** The single layer bit this ribbon draws on. See [GlobeScene.createRibbon]. */
    val layerBit: Int,
) {

    companion object {
        /** The arc's weight on the glass, in pixels. */
        private const val ARC_HALF_WIDTH_PX = 1.6f

        /**
         * The casing under the arc, wider, in the surface's own colour — the
         * technique an aeronautical chart uses to keep a route legible wherever
         * it crosses something else. See [GlobeInk.routeCasing].
         */
        private const val ARC_CASING_HALF_WIDTH_PX = 3.4f

        /**
         * The ribbon budget, in vertices, for **both** subjects.
         *
         * It used to be `SAMPLES * 2 + 8` — two vertices per point of one
         * 256-point arc. That was right while the only subject was a leg, and it
         * became a crash the moment `setArcs` took a list: the visited network
         * feeds every leg in the logbook, sampled at 32 points each, so eight
         * legs is 526 vertices and the eighth one overflowed the upload buffer
         * inside a Choreographer callback.
         *
         * 16,384 holds a logbook of roughly 250 distinct legs at 66 vertices
         * apiece — 64 for the run and two for the degenerate bridge to the next
         * one. Past that [RibbonBuffer] stops accepting vertices rather than
         * growing, so a larger logbook loses its last few arcs instead of
         * throwing. The cost is 196 kB a buffer, and each surface holds a
         * three-deep ring of them for each of the arc and its casing.
         */
        private const val MAX_RIBBON_VERTICES = 16_384
    }

    private val arcInstance: MaterialInstance = material.createInstance()
    private val casingInstance: MaterialInstance = material.createInstance()

    private val arcVertices = positionBuffer()
    private val casingVertices = positionBuffer()

    private val arcRing = BufferRing(MAX_RIBBON_VERTICES * GlobeMesh.POSITION_VERTEX_BYTES)
    private val casingRing = BufferRing(MAX_RIBBON_VERTICES * GlobeMesh.POSITION_VERTEX_BYTES)

    private val arcBuffer = RibbonBuffer(MAX_RIBBON_VERTICES)
    private val casingBuffer = RibbonBuffer(MAX_RIBBON_VERTICES)

    private val entityManager = EntityManager.get()
    private val arcEntity = entityManager.create()
    private val casingEntity = entityManager.create()

    /** True when a rebuild was owed and the ring had no buffer to give. */
    var isDirty: Boolean = false
        private set

    private var lastCamera: GlobeCamera? = null
    private var lastViewport = GlobeViewport(0f, 0f)
    private var lastArcsGeneration: Int = -1

    init {
        // The whole scene lives inside a sphere a little over unit radius, so one
        // box serves every renderable and frustum culling is switched off.
        val bounds = Box(0f, 0f, 0f, 1.1f, 1.1f, 1.1f)
        build(casingEntity, casingVertices, casingInstance, PRIORITY_ARC_CASING, bounds)
        build(arcEntity, arcVertices, arcInstance, PRIORITY_ARC, bounds)
        scene.addEntity(casingEntity)
        scene.addEntity(arcEntity)
    }

    private fun positionBuffer(): VertexBuffer = VertexBuffer.Builder()
        .bufferCount(1)
        .vertexCount(MAX_RIBBON_VERTICES)
        .attribute(
            VertexBuffer.VertexAttribute.POSITION, 0,
            VertexBuffer.AttributeType.FLOAT3, 0, GlobeMesh.POSITION_VERTEX_BYTES,
        )
        .build(engine)

    private fun build(
        entity: Int,
        vertices: VertexBuffer,
        instance: MaterialInstance,
        priority: Int,
        bounds: Box,
    ) {
        RenderableManager.Builder(1)
            .boundingBox(bounds)
            .geometry(0, RenderableManager.PrimitiveType.TRIANGLE_STRIP, vertices, 0, 0)
            .material(0, instance)
            .culling(false)
            .priority(priority)
            // Only the view that owns this ribbon renders it.
            .layerMask(LAYER_MASK_ALL, layerBit)
            .build(engine, entity)
    }

    /** The themed colours the arc and its casing are drawn in. */
    fun setInk(ink: GlobeInk) {
        arcInstance.setParameter(
            "baseColor",
            ink.route.red, ink.route.green, ink.route.blue, ink.route.alpha,
        )
        casingInstance.setParameter(
            "baseColor",
            ink.routeCasing.red, ink.routeCasing.green, ink.routeCasing.blue, ink.routeCasing.alpha,
        )
    }

    /**
     * Rebuilds the strip for [camera] if anything it depends on has moved.
     *
     * Cheap when nothing has: three comparisons. The camera is one of them, so a
     * surface that has settled does no ribbon work at all, and a surface that is
     * following its own gesture rebuilds only its own.
     */
    fun update(
        arcs: List<Array<Vec3>>,
        arcsGeneration: Int,
        camera: GlobeCamera,
        basis: CameraBasis,
        viewport: GlobeViewport,
    ) {
        val stale = isDirty ||
            camera != lastCamera ||
            viewport != lastViewport ||
            arcsGeneration != lastArcsGeneration
        if (!stale) return

        isDirty = !rebuild(arcs, camera, basis, viewport)
        lastCamera = camera
        lastViewport = viewport
        lastArcsGeneration = arcsGeneration
    }

    private fun rebuild(
        arcs: List<Array<Vec3>>,
        camera: GlobeCamera,
        basis: CameraBasis,
        viewport: GlobeViewport,
    ): Boolean {
        if (arcs.isEmpty()) {
            setStripCount(arcEntity, arcVertices, 0)
            setStripCount(casingEntity, casingVertices, 0)
            return true
        }
        val casingUploaded = uploadStrip(
            RouteGeometry.ribbonInto(
                arcs, camera, basis, viewport, ARC_CASING_HALF_WIDTH_PX, casingBuffer,
            ),
            casingBuffer, casingRing, casingVertices, casingEntity,
        )
        val arcUploaded = uploadStrip(
            RouteGeometry.ribbonInto(arcs, camera, basis, viewport, ARC_HALF_WIDTH_PX, arcBuffer),
            arcBuffer, arcRing, arcVertices, arcEntity,
        )
        return casingUploaded && arcUploaded
    }

    private fun uploadStrip(
        vertexCount: Int,
        source: RibbonBuffer,
        ring: BufferRing,
        buffer: VertexBuffer,
        entity: Int,
    ): Boolean {
        if (vertexCount < 3) {
            setStripCount(entity, buffer, 0)
            return true
        }
        val bytes = ring.acquire() ?: return false
        bytes.asFloatBuffer().put(source.positions, 0, vertexCount * 3)
        val (handler, callback) = ring.releaseCallback(bytes)
        buffer.setBufferAt(
            engine, 0, bytes, 0, vertexCount * GlobeMesh.POSITION_VERTEX_BYTES, handler, callback,
        )
        setStripCount(entity, buffer, vertexCount)
        return true
    }

    private fun setStripCount(entity: Int, buffer: VertexBuffer, count: Int) {
        val renderableManager = engine.renderableManager
        renderableManager.setGeometryAt(
            renderableManager.getInstance(entity),
            0,
            RenderableManager.PrimitiveType.TRIANGLE_STRIP,
            buffer,
            0,
            count,
        )
    }

    fun destroy() {
        scene.removeEntities(intArrayOf(arcEntity, casingEntity))
        engine.destroyEntity(arcEntity)
        engine.destroyEntity(casingEntity)
        entityManager.destroy(arcEntity)
        entityManager.destroy(casingEntity)
        engine.destroyVertexBuffer(arcVertices)
        engine.destroyVertexBuffer(casingVertices)
        engine.destroyMaterialInstance(arcInstance)
        engine.destroyMaterialInstance(casingInstance)
    }
}

/**
 * Draw order, continuing [GlobeScene]'s: the backdrop is 0 and the tiles are 1.
 *
 * Filament sorts transparent geometry back to front by depth, which is right for
 * the tiles among themselves and wrong for these two — the casing and the arc
 * are the same geometry at the same depth, so their order has to be stated
 * rather than derived. Lower draws first.
 */
private const val PRIORITY_ARC_CASING: Int = 2
private const val PRIORITY_ARC: Int = 3

/** Every layer bit, for the `select` half of Filament's layer calls. */
internal const val LAYER_MASK_ALL: Int = 0xFF

/**
 * The bit the tile mesh and the backdrop draw on: every view renders it.
 *
 * Filament defaults a renderable to layer 0 and a view to rendering every layer,
 * so this is only stated because the ribbons rely on the split.
 */
internal const val LAYER_SHARED: Int = 0x01
