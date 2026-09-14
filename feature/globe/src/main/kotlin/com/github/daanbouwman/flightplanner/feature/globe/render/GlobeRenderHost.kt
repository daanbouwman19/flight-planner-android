package com.github.daanbouwman.flightplanner.feature.globe.render

import android.graphics.Rect
import android.view.Choreographer
import android.view.Surface
import androidx.tracing.trace
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
import android.view.View as AndroidView

/**
 * Everything Filament needs to draw the globe into a view — *without* being one.
 *
 * ### Why this is not simply the view
 *
 * There are two kinds of view the globe is drawn into, and they cannot share a
 * superclass because `SurfaceView` and `TextureView` do not. They can share this,
 * which is all of the interesting code: the swap chain, the renderer, the camera,
 * the ribbon, the frame loop, the settled test and the teardown ordering. The two
 * views are then a dozen lines each of lifecycle forwarding, which is the amount
 * of duplication that cannot drift.
 *
 * [GlobeSurfaceView] and [GlobeTextureView] each say why they exist. The short
 * version: a `SurfaceView` is a separate compositor layer, which is free to draw
 * and impossible for Compose to clip; a `TextureView` is ordinary view content,
 * which costs a copy and clips like anything else. The full-bleed hosts want the
 * first and a card in a scrolling list needs the second.
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
 * cases: [onWindowVisibility] carries the window going away, and [onScreen] is
 * the host telling it that a Compose scroll has carried the box out of the
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
 *
 * @param view the view being drawn into, for its `context` and its `display`.
 * @param attachHelper hands the [UiHelper] its view. A lambda because
 *   `attachTo` is overloaded per view type and there is no common supertype
 *   that carries it.
 */
internal class GlobeRenderHost(
    private val view: AndroidView,
    private val attachHelper: (UiHelper) -> Unit,
) : BackgroundReleasable {

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
        // A `TextureView` composites against the app and would be correct either
        // way; opaque is also its cheaper path, so the two agree.
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
                renderer?.let { displayHelper?.attach(it, view.display) }
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
                applyGestureExclusion()
            }
        }
    }

    /** The viewport the CPU projection must use — pixels, matching the surface. */
    fun currentViewport(): GlobeViewport = viewport

    /**
     * Whether the system's edge gestures are kept off this surface at all.
     *
     * True for a globe touch moves; false for one that is only looked at, where
     * excluding the strip would break the back gesture for nothing. See
     * [GestureExclusion] for what is excluded and what is deliberately left.
     */
    var excludeSystemGestures: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            applyGestureExclusion()
        }

    /**
     * How much of the top of this surface the host covers with its own chrome,
     * in px — the collapse button, or the app bar. The exclusion band starts
     * below it, so the system's back gesture still works beside those controls.
     */
    var gestureExclusionTopPx: Int = 0
        set(value) {
            if (field == value) return
            field = value
            applyGestureExclusion()
        }

    /**
     * Re-derives the exclusion rects from the surface's size and the two
     * properties above. Called from [UiHelper.RendererCallback.onResized],
     * which is where this class learns the view's size, and from the setters,
     * because the host binds them from a Compose `update` block that can run
     * after the surface has already been sized.
     */
    private fun applyGestureExclusion() {
        val band = if (excludeSystemGestures) {
            GestureExclusion.forSurface(
                width = viewport.width.toInt(),
                height = viewport.height.toInt(),
                topChromePx = gestureExclusionTopPx,
                maxBandPx = (MAX_EXCLUSION_DP * view.resources.displayMetrics.density).toInt(),
            )
        } else {
            null
        }
        view.systemGestureExclusionRects =
            listOfNotNull(band?.let { Rect(it.left, it.top, it.right, it.bottom) })
    }

    /** The view has attached to its window. */
    fun onAttached() {
        attached = true
        // Registered whether or not a session comes back: the registry is of
        // *attached surfaces*, and a device with no renderer simply has nothing
        // to release when one is asked for.
        GlobeSession.registerSurface(this)
        val acquired = GlobeSession.acquire(view.context) ?: return
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
        displayHelper = DisplayHelper(view.context)
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
        attachHelper(uiHelper)
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
     * Deliberately **not** the detach half of [onDetached]: no `releaseUpdates`,
     * no `GlobeSession.release()`, no unregistering. This view is still attached
     * to its window and still counted; only its Filament objects are gone, and
     * [syncLoop] builds them again the moment it needs to draw.
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

    /** The window this view is in became visible, or stopped being. */
    fun onWindowVisibility(isVisible: Boolean) {
        windowVisible = isVisible
        syncLoop()
    }

    /**
     * Whether the composition still has this surface on screen.
     *
     * **Compose scrolling is invisible to `onVisibilityAggregated`.** A hero
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
     * Starts or stops the frame callback to match `attached` and `visible`, and
     * rebuilds this surface's Filament objects if a forced teardown took them.
     *
     * Idempotent, because both signals can arrive in either order and more than
     * once. Starting also forgets the last rendered camera, so the first frame
     * back is always drawn rather than skipped as settled.
     *
     * **The rebuild lives here rather than in a lifecycle callback of its own**
     * because this is already the one place that knows whether anything wants a
     * frame: [onWindowVisibility] calls it when the app comes back, and
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
            GlobeSession.reacquireIfNeeded(view.context)?.let { buildAgainst(it) }
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

    /** The view is leaving its window. Call **before** `super.onDetachedFromWindow()`. */
    fun onDetached() {
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
            GlobeSession.release(GlobeSession.graceFor(embedded))
        }
    }

    /**
     * Whether this globe is embedded in ordinary scrolling content rather than
     * owning a full-bleed box of its own.
     *
     * The view type follows from it — see [GlobeTextureView] — and so does the
     * teardown grace: an embedded globe is a `LazyColumn` item, disposed the
     * moment it scrolls out of view, so a short grace would rebuild the engine
     * and the 32 MB atlas on every pass. See [GlobeSession.graceFor].
     */
    var embedded: Boolean = false

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
        // Its own trace section, like the scene's stages: `GlobeSpinBenchmark`
        // reads the cost of each part of this callback separately.
        trace("globe:ribbon") {
            ribbon?.update(scene.routeArcs, scene.arcsGeneration, camera, basis, viewport)
        }
        filamentCamera?.let { scene.applyCamera(it, camera, viewport) }

        // `beginFrame` returning false is the driver saying it would rather this
        // frame were skipped — it is behind, and rendering anyway only makes the
        // queue longer. What was rendered is recorded only when something was:
        // a declined frame has to be retried on the next vsync, not remembered
        // as drawn.
        trace("globe:render") {
            if (renderer.beginFrame(chain, frameTimeNanos)) {
                renderer.render(view)
                renderer.endFrame()
                renderedCamera = camera
                renderedViewport = viewport
                renderedMeshGeneration = scene.meshGeneration
                renderedArcsGeneration = scene.arcsGeneration
                // The clear colour is *set* above, because the renderer has to
                // carry it into the frame, but it is only *recorded as applied*
                // here. Ticking the generation before the driver had accepted the
                // frame lost a theme change outright on a still globe: the settled
                // test then saw no change pending and returned early on every
                // following vsync, so the colour sat on the renderer and was never
                // drawn.
                appliedSpaceGeneration = scene.spaceGeneration
            }
        }

        onFrame(tiles)
        if (!announcedFirstImagery && scene.hasDrawnImagery) {
            announcedFirstImagery = true
            onFirstImagery()
        }
    }
}

/**
 * The most exclusion height the platform honours per edge, in dp.
 *
 * `View.setSystemGestureExclusionRects` documents the cap; above it the request
 * is silently trimmed, so the band asks for exactly this much and places it
 * where a spin starts rather than leaving the platform to choose. See
 * [GestureExclusion].
 */
private const val MAX_EXCLUSION_DP = 200f

/**
 * A view that draws the globe, whichever kind it is.
 *
 * `SurfaceView` and `TextureView` share no supertype below `android.view.View`,
 * so this is what lets the Compose host wire one up without caring which it got.
 * See [GlobeSurfaceView] and [GlobeTextureView] for when each is used.
 */
internal interface GlobeHostView {
    val host: GlobeRenderHost
}
