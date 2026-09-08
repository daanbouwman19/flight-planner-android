package com.github.daanbouwman.flightplanner.feature.globe.render

import android.content.Context
import android.view.Choreographer
import android.view.Surface
import android.view.SurfaceView
import com.github.daanbouwman.flightplanner.feature.globe.math.GlobeCamera
import com.github.daanbouwman.flightplanner.feature.globe.math.GlobeViewport
import com.github.daanbouwman.flightplanner.feature.globe.math.VisibleTile
import com.google.android.filament.Camera
import com.google.android.filament.EntityManager
import com.google.android.filament.Renderer
import com.google.android.filament.SwapChain
import com.google.android.filament.View
import com.google.android.filament.Viewport
import com.google.android.filament.android.DisplayHelper
import com.google.android.filament.android.UiHelper

/**
 * The window Filament draws the globe into.
 *
 * ### A `SurfaceView`, below the window, and what that buys
 *
 * The globe is a separate hardware layer composited **underneath** the app
 * window, with a hole punched through the window where this view sits. Compose
 * content drawn later in the tree — the controls, the labels, the attribution —
 * lands on top of that hole and is fully visible. This is how a video player
 * puts controls over its surface, and it is the reason none of the glass chrome
 * has to be drawn by the renderer.
 *
 * The alternative is a `TextureView`, which is an ordinary view and composites
 * with everything else — at the cost of an extra full-screen copy per frame,
 * every frame, for a view that is 44% of the window. The hole-punch is free.
 *
 * The consequence to know about: a `SurfaceView`'s layer is **not clipped by its
 * parent's rounded corners**. Anywhere the globe sits in a rounded container the
 * corners have to be painted back over it, which
 * `GlobeSurface` does with a mask in the container's own colour. On the deep
 * hero and in the immersive screen the box is square and the question does not
 * arise.
 *
 * ### The frame loop
 *
 * A `Choreographer` callback, so the globe renders in step with the display's
 * vsync rather than on a timer of its own. It rests in two ways, and they are
 * different sizes of saving.
 *
 * **Hidden: the callback stops entirely.** A hero scrolled off the page, or an
 * app in the background, is still an attached view, and this used to keep
 * traversing a quadtree and submitting frames for a surface nobody could see.
 * It takes *two* signals, because the platform only knows about one of the two
 * cases: [onVisibilityAggregated] catches the window going away, and [onScreen]
 * is the host telling it that a Compose scroll has carried the box out of the
 * viewport — which leaves the `View` `VISIBLE` in a visible window and is
 * therefore invisible to the platform.
 *
 * **Visible but settled: the callback stays, the work does not.** Posting to the
 * Choreographer costs nothing worth measuring; the tile traversal, the ribbon
 * rebuild and `Renderer.render` cost the frame. So a frame is skipped when the
 * camera has not moved, the viewport is unchanged, the mesh this view last drew
 * is still the scene's current one, the clear colour is current, and the scene
 * says it has no tiles in flight and no crossfade running. Every one of those is
 * biased toward drawing: anything unknown renders.
 *
 * ### Two views, one scene
 *
 * During the hero-to-immersive transition both surfaces are attached at once,
 * and again under a predictive back. The most recently attached one drives the
 * scene's per-frame update — see [GlobeScene.drivesUpdates] — and the other only
 * points its own camera and draws what the driver built.
 */
internal class GlobeSurfaceView(context: Context) : SurfaceView(context), BackgroundReleasable {

    /** Called every frame with the camera to render from. */
    var cameraProvider: () -> GlobeCamera = { GlobeCamera() }

    /** Called after each frame, with what the traversal selected. */
    var onFrame: (List<VisibleTile>) -> Unit = {}

    /** Called once, the first frame the globe has imagery to show. See G8. */
    var onFirstImagery: () -> Unit = {}

    /**
     * Called whenever the surface is sized, with its size in pixels.
     *
     * The CPU projection has to use exactly the surface's own pixels, so this is
     * read from the surface rather than from the Compose constraints around it.
     * The two are the same number in the steady state, but only one of them is
     * the number the GPU is using, and during a resize they are a frame apart.
     */
    var onViewportChanged: (GlobeViewport) -> Unit = {}

    private var session: GlobeSession? = null
    private var renderer: Renderer? = null

    private var appliedSpaceGeneration = -1
    private var filamentView: View? = null
    private var filamentCamera: Camera? = null
    private var cameraEntity: Int = 0
    private var swapChain: SwapChain? = null
    private var displayHelper: DisplayHelper? = null

    private var viewport = GlobeViewport(1f, 1f)
    private var announcedFirstImagery = false

    private val uiHelper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK).apply {
        // Opaque, because a below-window surface composites against whatever is
        // behind the *app*, not against the app's own background. A translucent
        // globe here would blend with the wallpaper. The sphere paints its own
        // themed backdrop instead, which is what `GlobeInk.backdrop` is for.
        isOpaque = true
    }

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running) return
            Choreographer.getInstance().postFrameCallback(this)
            renderFrame(frameTimeNanos)
        }
    }

    /** Whether the loop is posted. Attached *and* visible, never one of them. */
    private var running = false

    private var windowVisible = false
    private var attached = false

    /** What the last **drawn** frame used, for the settled test. */
    private var renderedCamera: GlobeCamera? = null
    private var renderedViewport = GlobeViewport(0f, 0f)
    private var renderedMeshGeneration = -1
    private var renderedArcsGeneration = -1

    /** This surface's route line. See [RouteRibbon] for why it is not the scene's. */
    private var ribbon: RouteRibbon? = null

    init {
        uiHelper.renderCallback = object : UiHelper.RendererCallback {
            override fun onNativeWindowChanged(surface: Surface) {
                swapChain?.let { engineOrNull()?.destroySwapChain(it) }
                swapChain = engineOrNull()?.createSwapChain(surface)
                renderer?.let { displayHelper?.attach(it, display) }
            }

            override fun onDetachedFromSurface() {
                displayHelper?.detach()
                swapChain?.let { chain ->
                    engineOrNull()?.let { engine ->
                        engine.destroySwapChain(chain)
                        // The swap chain is destroyed asynchronously; waiting here
                        // is what makes it safe for the platform to reclaim the
                        // native window as soon as this callback returns.
                        engine.flushAndWait()
                    }
                }
                swapChain = null
            }

            override fun onResized(width: Int, height: Int) {
                filamentView?.viewport = Viewport(0, 0, width, height)
                viewport = GlobeViewport(width.toFloat(), height.toFloat())
                onViewportChanged(viewport)
            }
        }
    }

    /** The viewport the CPU projection must use — pixels, matching the surface. */
    fun currentViewport(): GlobeViewport = viewport

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        attached = true
        // Registered whether or not a session comes back: the registry is of
        // *attached surfaces*, and a device with no renderer simply has nothing
        // to release when one is asked for.
        GlobeSession.registerSurface(this)
        val acquired = GlobeSession.acquire(context) ?: return
        buildAgainst(acquired)
        syncLoop()
    }

    /**
     * Builds everything this surface owns against [acquired].
     *
     * Shared by the first attach and by the rebuild after [releaseForBackground],
     * so the two cannot drift — a second copy of this block that forgot the
     * ribbon's layer bit, or the update claim, would be a bug visible only on
     * the path nobody exercises by hand.
     */
    private fun buildAgainst(acquired: GlobeSession) {
        session = acquired
        // Last attached drives: this is the surface the user is now looking at.
        acquired.scene.claimUpdates(this)
        displayHelper = DisplayHelper(context)
        renderer = acquired.engine.createRenderer().apply {
            clearOptions = Renderer.ClearOptions().apply { clear = true }
        }
        cameraEntity = EntityManager.get().create()
        filamentCamera = acquired.engine.createCamera(cameraEntity)
        // This surface's own route line. The mesh is shared because it does not
        // depend on the camera; the ribbon does, so every surface builds its own
        // and renders only its own — see RouteRibbon.
        val ownRibbon = acquired.scene.createRibbon()
        ribbon = ownRibbon
        filamentView = acquired.engine.createView().apply {
            camera = filamentCamera
            acquired.scene.configureView(this)
            setVisibleLayers(LAYER_MASK_ALL, LAYER_SHARED or ownRibbon.layerBit)
        }
        // A rebuilt scene has drawn nothing yet, so G8's crossfade is owed its
        // announcement again — otherwise the still map never fades back out.
        announcedFirstImagery = false
        appliedSpaceGeneration = -1
        // **Last, and part of this block rather than the caller's.** The swap
        // chain is created by the UiHelper's callback, so a rebuild that did not
        // re-attach would leave `swapChain` null and `renderFrame` returning at
        // its third line for the rest of the process — a globe that is black
        // rather than one that crashed, which is harder to notice and no better.
        uiHelper.attachTo(this)
    }

    /**
     * Gives up everything this surface owns, without detaching.
     *
     * Called by [GlobeSession] before it destroys the engine these objects point
     * into — see [forcedTeardown]. The reason it exists at all is that
     * backgrounding detaches nothing: `onDetachedFromWindow` is what used to
     * free this surface's [RouteRibbon], and the ribbon holds two
     * `MaterialInstance`s of a material `GlobeScene.destroy()` frees. Destroying
     * the material first is a crash, not a leak.
     *
     * Deliberately **not** the detach half of [onDetachedFromWindow]: no
     * `releaseUpdates`, no `GlobeSession.release()`, no unregistering. This view
     * is still attached to its window and still counted; only its Filament
     * objects are gone, and [syncLoop] builds them again the moment it needs to
     * draw.
     */
    override fun releaseForBackground() {
        val current = session ?: return
        running = false
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        // Frees the swap chain through the existing onDetachedFromSurface, which
        // also flushAndWaits — so nothing is left in flight against the engine.
        uiHelper.detach()

        ribbon?.let { current.scene.destroyRibbon(it) }
        ribbon = null

        val engine = current.engine
        filamentView?.let { engine.destroyView(it) }
        renderer?.let { engine.destroyRenderer(it) }
        filamentCamera?.let { engine.destroyCameraComponent(cameraEntity) }
        if (cameraEntity != 0) {
            EntityManager.get().destroy(cameraEntity)
            cameraEntity = 0
        }
        filamentView = null
        renderer = null
        filamentCamera = null
        displayHelper = null
        session = null
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        windowVisible = isVisible
        syncLoop()
    }

    /**
     * Whether the composition still has this surface on screen.
     *
     * **Compose scrolling is invisible to [onVisibilityAggregated].** A hero
     * scrolled off the page is a `View` that is still `VISIBLE` in a window that
     * is still visible — it is simply not drawn, because the layout put it
     * outside the viewport. The platform signal covers the app going to the
     * background and nothing else, so the host measures the box and says.
     */
    var onScreen: Boolean = true
        set(value) {
            if (field == value) return
            field = value
            syncLoop()
        }

    /**
     * Starts or stops the frame callback to match [attached] and [visible], and
     * rebuilds this surface's Filament objects if a forced teardown took them.
     *
     * Idempotent, because both signals can arrive in either order and more than
     * once. Starting also forgets the last rendered camera, so the first frame
     * back is always drawn rather than skipped as settled.
     *
     * **The rebuild lives here rather than in a lifecycle callback of its own**
     * because this is already the one place that knows whether anything wants a
     * frame: [onVisibilityAggregated] calls it when the app comes back, and
     * [onScreen]'s setter calls it when a Compose scroll brings the box back.
     * Both are exactly when a surface released for the background needs to
     * exist again, and neither needs to know that is what it is asking for.
     */
    private fun syncLoop() {
        val wantsToRun = attached && windowVisible && onScreen
        if (wantsToRun && session == null) {
            // Null only after releaseForBackground, or when this device has no
            // renderer at all — in which case this returns null every time and
            // costs a cached lookup.
            GlobeSession.reacquireIfNeeded(context)?.let { buildAgainst(it) }
        }
        val shouldRun = wantsToRun && session != null
        if (shouldRun == running) return
        running = shouldRun
        if (shouldRun) {
            renderedCamera = null
            Choreographer.getInstance().postFrameCallback(frameCallback)
        } else {
            Choreographer.getInstance().removeFrameCallback(frameCallback)
        }
    }

    override fun onDetachedFromWindow() {
        attached = false
        running = false
        // Unregistered first: from here on this surface has nothing for a forced
        // teardown to release, and the teardown must not reach a half-detached one.
        GlobeSession.unregisterSurface(this)
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        uiHelper.detach()

        // Before the view goes: the ribbon's entities are in the shared scene,
        // and its layer bit has to come back for the next surface.
        ribbon?.let { session?.scene?.destroyRibbon(it) }
        ribbon = null

        val engine = engineOrNull()
        if (engine != null) {
            filamentView?.let { engine.destroyView(it) }
            renderer?.let { engine.destroyRenderer(it) }
            filamentCamera?.let { engine.destroyCameraComponent(cameraEntity) }
            if (cameraEntity != 0) {
                EntityManager.get().destroy(cameraEntity)
                cameraEntity = 0
            }
        }
        filamentView = null
        renderer = null
        filamentCamera = null
        displayHelper = null

        session?.let {
            // Only if this view was the one driving; a view that was never the
            // driver must not take the drive away from the one that is.
            it.scene.releaseUpdates(this)
            session = null
            GlobeSession.release()
        }
        super.onDetachedFromWindow()
    }

    private fun engineOrNull() = session?.engine

    private fun renderFrame(frameTimeNanos: Long) {
        val session = session ?: return
        val renderer = renderer ?: return
        val view = filamentView ?: return
        val chain = swapChain ?: return
        if (!uiHelper.isReadyToRender) return
        if (viewport.width < 1f || viewport.height < 1f) return

        val scene = session.scene
        val camera = cameraProvider()

        // The theme can change under a live surface - Cockpit, or the system
        // flipping to dark - so the clear colour is re-read rather than set once
        // at creation. A generation counter, because comparing four floats every
        // frame to set a value that changes twice a session is the wrong shape.
        // It is part of the settled test below: a flip on a still globe used to
        // be applied to the renderer here and then never drawn.
        val spaceChanged = appliedSpaceGeneration != scene.spaceGeneration

        // Settled: the same camera in the same box, the mesh this view last drew
        // still current, the clear colour current, and nothing arriving and no
        // crossfade left to run. Returning here skips the traversal, the ribbon
        // rebuild and the render — everything that costs a frame — while the
        // callback stays posted so the next change is picked up on the next vsync.
        if (camera == renderedCamera &&
            viewport == renderedViewport &&
            scene.meshGeneration == renderedMeshGeneration &&
            scene.arcsGeneration == renderedArcsGeneration &&
            ribbon?.isDirty != true &&
            !spaceChanged &&
            !scene.wantsFrame
        ) {
            return
        }

        if (spaceChanged) {
            renderer.clearOptions = Renderer.ClearOptions().apply {
                clear = true
                clearColor = scene.spaceColor
            }
        }

        val basis = camera.computeBasis()

        // One driver per scene; the other view draws what the driver built,
        // from its own camera.
        val tiles = if (scene.drivesUpdates(this)) {
            scene.update(camera, basis, viewport)
        } else {
            scene.visibleTiles
        }
        // Built from *this* surface's camera, driver or not: the width, the lift
        // and the samples it keeps are all functions of the viewpoint, so the
        // driver's ribbon would be the wrong geometry for the other surface.
        ribbon?.update(scene.routeArcs, scene.arcsGeneration, camera, basis, viewport)
        filamentCamera?.let { scene.applyCamera(it, camera, viewport) }

        // `beginFrame` returning false is the driver saying it would rather this
        // frame were skipped — it is behind, and rendering anyway only makes the
        // queue longer. What was rendered is recorded only when something was:
        // a declined frame has to be retried on the next vsync, not remembered
        // as drawn.
        if (renderer.beginFrame(chain, frameTimeNanos)) {
            renderer.render(view)
            renderer.endFrame()
            renderedCamera = camera
            renderedViewport = viewport
            renderedMeshGeneration = scene.meshGeneration
            renderedArcsGeneration = scene.arcsGeneration
            // The clear colour is *set* above, because the renderer has to carry
            // it into the frame, but it is only *recorded as applied* here. Ticking
            // the generation before the driver had accepted the frame lost a theme
            // change outright on a still globe: the settled test then saw no change
            // pending and returned early on every following vsync, so the colour
            // sat on the renderer and was never drawn.
            appliedSpaceGeneration = scene.spaceGeneration
        }

        onFrame(tiles)
        if (!announcedFirstImagery && scene.hasDrawnImagery) {
            announcedFirstImagery = true
            onFirstImagery()
        }
    }
}
