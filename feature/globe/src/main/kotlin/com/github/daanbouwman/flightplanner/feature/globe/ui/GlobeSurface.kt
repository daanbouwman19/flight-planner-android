package com.github.daanbouwman.flightplanner.feature.globe.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.github.daanbouwman.flightplanner.core.designsystem.motion.FlightMotion
import com.github.daanbouwman.flightplanner.core.designsystem.motion.LocalReduceMotion
import com.github.daanbouwman.flightplanner.feature.globe.R
import com.github.daanbouwman.flightplanner.feature.globe.math.GlobeCamera
import com.github.daanbouwman.flightplanner.feature.globe.math.GlobeFit
import com.github.daanbouwman.flightplanner.feature.globe.math.GlobeViewport
import com.github.daanbouwman.flightplanner.feature.globe.math.Limb
import com.github.daanbouwman.flightplanner.feature.globe.math.ScreenPoint
import com.github.daanbouwman.flightplanner.feature.globe.math.Vec3
import com.github.daanbouwman.flightplanner.feature.globe.math.latLonToWorld
import com.github.daanbouwman.flightplanner.feature.globe.render.GlobeInk
import com.github.daanbouwman.flightplanner.feature.globe.render.GlobeSession
import com.github.daanbouwman.flightplanner.feature.globe.render.GlobeSupport
import com.github.daanbouwman.flightplanner.feature.globe.render.GlobeSurfaceView
import kotlin.math.roundToInt

/**
 * The leg a globe is drawn for.
 *
 * It carries the **already sampled** great circle rather than four coordinates,
 * so the globe and the flat `RouteMap` draw the same curve from the same array —
 * see [com.github.daanbouwman.flightplanner.feature.globe.math.RouteGeometry]
 * for why re-deriving it here would be a second place for the ±180° seam to be
 * got wrong.
 */
class GlobeRoute(
    val departureIcao: String,
    val destinationIcao: String,
    val arcLats: DoubleArray,
    val arcLons: DoubleArray,
) {
    /** Identity for the session's route cache: for this purpose the codes are the route. */
    internal val key: String get() = "leg:$departureIcao-$destinationIcao"

    override fun equals(other: Any?): Boolean = other is GlobeRoute && key == other.key

    override fun hashCode(): Int = key.hashCode()
}

/** One airport in the visited network, and how often it has been flown to. */
class GlobeNode(
    val icao: String,
    val latitude: Double,
    val longitude: Double,
    val visits: Int,
)

/**
 * Everywhere the logbook has been, and every leg between.
 *
 * The same visited set and the same legs the flat card draws, taken already
 * sampled for the reason [GlobeRoute] is. What changes is only the projection:
 * onto a sphere instead of into a fitted rectangle, so a logbook spread across
 * two hemispheres stops folding into one window.
 */
class GlobeNetwork(
    val nodes: List<GlobeNode>,
    val legs: List<Pair<DoubleArray, DoubleArray>>,
) {
    internal val key: String
        get() = "net:" + nodes.size + ":" + legs.size + ":" + (nodes.firstOrNull()?.icao ?: "")

    override fun equals(other: Any?): Boolean = other is GlobeNetwork && key == other.key

    override fun hashCode(): Int = key.hashCode()
}

/** Whether this device can draw a globe at all. See [GlobeSurface] and 3B. */
@Composable
fun rememberGlobeAvailable(): Boolean = rememberGlobeStatus() == GlobeStatus.Available

/**
 * Why the globe is or is not available, for the one place that says so out loud.
 *
 * The globe itself never explains itself — 3B is explicit that a device without
 * a renderer shows the still map and *nothing drawn to say so*, because that is
 * the app working rather than the app failing. This exists for Settings, where
 * somebody who has gone looking can find one line about it.
 */
enum class GlobeStatus {
    /** A renderer came up and there is memory for the atlas. */
    Available,

    /** Filament could not create an engine on any backend. */
    NoRenderer,

    /** The platform reports a low-RAM device; 32 MB of atlas is not affordable. */
    LowMemory,
}

@Composable
fun rememberGlobeStatus(): GlobeStatus {
    val context = LocalContext.current
    return remember(context) {
        when (GlobeSession.support(context)) {
            GlobeSupport.Available -> GlobeStatus.Available
            GlobeSupport.NoRenderer -> GlobeStatus.NoRenderer
            GlobeSupport.LowMemory -> GlobeStatus.LowMemory
        }
    }
}

/**
 * The globe: a Filament surface, the glass over it, and everything connecting
 * the two.
 *
 * This is G6 through G9 in one place, because from the outside they are one
 * thing — the surface renders, the gestures move the camera it renders from, the
 * labels project through the *same* camera, and the controls are what make all
 * of it reachable without a gesture.
 *
 * ### What is drawn where
 *
 * | Layer | Drawn by | Why |
 * | --- | --- | --- |
 * | imagery, backdrop, route ribbon | Filament | it is 3D, and it has to be depth-correct |
 * | limb rim and atmosphere | Compose | the colours are `outline` and `primary`, and the geometry is one projected ring |
 * | DEP/DEST plates | Compose | they are the only part of the globe TalkBack can read |
 * | controls, attribution | Compose | chrome is Compose everywhere else in this app |
 *
 * ### The crossfade in (G8)
 *
 * [content] — the still `RouteMap` from C3 — is composed **on top of** the
 * surface and fades out on an effects spring once the first tile mesh has been
 * built, which reads as the globe fading in. It is drawn on top rather than
 * underneath because the surface is a `SurfaceView` below the window: a Compose
 * alpha on the node hosting it never reaches the SurfaceControl, so the first
 * build of this — the still map underneath, the surface fading in — was a hard
 * cut. **Alpha only: nothing moves, so nothing may overshoot**, and the still
 * map holds the frame throughout. There is deliberately no geometric morph
 * between the two; a `SurfaceView` cannot be a shared element, and pretending
 * otherwise would mean animating a screenshot of one.
 *
 * ### With no renderer (3B)
 *
 * [content] is all there is, and **nothing is drawn to say so** — no error
 * state, no banner, no greyed control. This is the app working. The only honest
 * signal is that the host does not offer a fullscreen button, which it decides
 * from [rememberGlobeAvailable]: a control that opens nothing is worse than no
 * control.
 */
@Composable
fun GlobeSurface(
    route: GlobeRoute,
    modifier: Modifier = Modifier,
    /**
     * The handle the glass controls drive the camera through.
     *
     * Passed in rather than created here because the controls are laid out by
     * the host — bottom-right on the deep hero, top-right in the immersive
     * screen — and a handle created inside would be unreachable from there.
     */
    controls: GlobeControlsHandle? = null,
    /**
     * How much of the top of this surface the host covers with its own chrome.
     *
     * An app bar over a globe is opaque enough to hide a label whole, and the
     * label may not move out from under it — see [GlobeLabels]. The host is the
     * only thing that knows how tall its chrome is, so it says.
     */
    topChromeInset: Dp = 0.dp,
    /**
     * Whether touch moves the camera at all. False for a globe that is looked
     * at rather than handled; the controls and the accessibility actions still
     * work, because they never went through the pointer handler.
     */
    interactive: Boolean = true,
    /**
     * True when this globe sits inside a vertically scrolling page. A one-finger
     * drag that is near-vertical is then given to the page — see
     * [globeGestures] for the compromise and its cone.
     */
    nestedVerticalScroll: Boolean = false,
    /** Placed over the globe, inside the same box — controls, a title, chips. */
    overlay: @Composable BoxScope.() -> Unit = {},
    /** The still map: drawn over the globe until the first tiles land, then faded out; the whole picture where there is no renderer. */
    content: @Composable () -> Unit = {},
) {
    val depWorld = remember(route) {
        latLonToWorld(route.arcLats.first().toFloat(), route.arcLons.first().toFloat())
    }
    val destWorld = remember(route) {
        latLonToWorld(route.arcLats.last().toFloat(), route.arcLons.last().toFloat())
    }
    val description = stringResource(
        R.string.globe_description,
        route.departureIcao,
        route.destinationIcao,
    )

    GlobeCanvas(
        key = route.key,
        arcs = remember(route) { listOf(route.arcLats to route.arcLons) },
        fit = remember(route) {
            { viewport: GlobeViewport, maxLod: Int ->
                GlobeFit.frameRoute(
                    route.arcLats.first(), route.arcLons.first(),
                    route.arcLats.last(), route.arcLons.last(),
                    viewport,
                    maxLod = maxLod,
                )
            }
        },
        description = description,
        modifier = modifier,
        controls = controls,
        interactive = interactive,
        nestedVerticalScroll = nestedVerticalScroll,
        markers = { cameraState, viewport, primary, tertiary ->
            GlobeLabels(
                departure = GlobeLabel(route.departureIcao, depWorld, primary),
                destination = GlobeLabel(route.destinationIcao, destWorld, tertiary),
                cameraState = cameraState,
                viewport = viewport,
                modifier = Modifier.fillMaxSize(),
                topChromeInset = topChromeInset,
            )
        },
        overlay = overlay,
        content = content,
    )
}

/**
 * The visited network on the sphere — 1G.
 *
 * Everything the flat card shows, projected onto a globe instead of into a
 * fitted rectangle. It is the same component underneath the route globe: one
 * camera, one atlas, one session. What differs is what is drawn on the glass —
 * dots sized by how often each field has been flown to, and no labels, because
 * a hundred four-letter codes over a planet is not a map, it is a word cloud.
 *
 * The nodes carry their codes to TalkBack all the same: they are the only thing
 * on this surface an accessibility service can read, and a sphere with a
 * hundred unnamed dots on it says nothing at all.
 */
@Composable
fun GlobeNetworkSurface(
    network: GlobeNetwork,
    modifier: Modifier = Modifier,
    controls: GlobeControlsHandle? = null,
    /** See [GlobeSurface]. */
    interactive: Boolean = true,
    /** See [GlobeSurface]. The Stats band sits in a `LazyColumn`, which is what this is for. */
    nestedVerticalScroll: Boolean = false,
    overlay: @Composable BoxScope.() -> Unit = {},
    content: @Composable () -> Unit = {},
) {
    val description = pluralStringResource(
        R.plurals.globe_network_description,
        network.nodes.size,
        network.nodes.size,
        network.legs.size,
    )

    GlobeCanvas(
        key = network.key,
        arcs = network.legs,
        fit = remember(network) {
            val lats = network.nodes.map { it.latitude }.toDoubleArray()
            val lons = network.nodes.map { it.longitude }.toDoubleArray()
            // Named rather than returned as a trailing lambda: after a `val`, the
            // parser reads a bare `{ … }` as an argument to the line above it.
            val solve: (GlobeViewport, Int) -> GlobeCamera =
                { viewport, maxLod -> GlobeFit.framePoints(lats, lons, viewport, maxLod = maxLod) }
            solve
        },
        description = description,
        modifier = modifier,
        controls = controls,
        interactive = interactive,
        nestedVerticalScroll = nestedVerticalScroll,
        markers = { cameraState, viewport, primary, _ ->
            GlobeNodes(
                nodes = network.nodes,
                color = primary,
                cameraState = cameraState,
                viewport = viewport,
                modifier = Modifier.fillMaxSize(),
            )
        },
        overlay = overlay,
        content = content,
    )
}

/**
 * The globe itself, for whatever is being drawn on it.
 *
 * Both public entry points are this function with a different set of arcs and a
 * different marker layer. Everything else — the surface, the crossfade, the
 * gestures, the limb, the semantics — is identical, and it is identical because
 * none of it depends on whether the subject is one leg or two hundred.
 */
@Composable
private fun GlobeCanvas(
    key: String,
    arcs: List<Pair<DoubleArray, DoubleArray>>,
    /**
     * The opening camera, **as a function of the box it is framing**.
     *
     * A camera rather than a function was the shape of this for one release, and
     * it could not be right: the horizontal half-angle is
     * `atan(tan(fovY / 2) · width / height)`, so the same leg needs a different
     * distance in the deep hero and in the immersive screen. Passing the fit
     * already solved meant solving it against a viewport nobody had measured yet.
     *
     * The second argument is the imagery's deepest level, from the session: the
     * fit will not park the camera closer than the point where that level is one
     * texel per pixel, and with a keyed provider that point is far lower than
     * with the keyless fallback.
     */
    fit: (GlobeViewport, Int) -> GlobeCamera,
    description: String,
    modifier: Modifier,
    controls: GlobeControlsHandle?,
    interactive: Boolean,
    nestedVerticalScroll: Boolean,
    markers: @Composable (GlobeCameraState, GlobeViewport, Color, Color) -> Unit,
    overlay: @Composable BoxScope.() -> Unit,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    // **Acquired in the effect, not in `remember`.** A `remember` runs during
    // composition and a `DisposableEffect` only when that composition is applied,
    // so a composition that is started and thrown away took a reference the
    // release never balanced - after which the teardown’s "no views left" guard
    // can never pass and a 32 MB atlas outlives the screen for the rest of the
    // process. Both halves belong to the same effect.
    //
    // The first frame therefore has no session and draws [content], which is the
    // still map that stays on top until the globe has something to show.
    var held by remember(context) { mutableStateOf<GlobeSession?>(null) }
    DisposableEffect(context) {
        held = GlobeSession.acquire(context)
        onDispose {
            if (held != null) GlobeSession.release()
            held = null
        }
    }

    val session = held
    if (session == null) {
        Box(modifier = modifier.clipToBounds()) {
            content()
            overlay()
        }
        return
    }

    val ink = rememberGlobeInk()
    val destinationInk = androidx.compose.material3.MaterialTheme.colorScheme.tertiary
    val reduceMotion = LocalReduceMotion.current
    val scope = rememberCoroutineScope()

    val crossfade = FlightMotion.effects<Float>()
    val spatial = FlightMotion.spatial<Float>()
    val spatialFast = FlightMotion.spatialFast<Float>()
    val flingDecay = remember { FlightMotion.flingDecay<Offset>() }

    // Declared before the camera, because the fit is now a function of it. Until
    // the surface has been measured this is a placeholder and the fit it produces
    // is a placeholder too; the effect below is what corrects both.
    var viewport by remember { mutableStateOf(GlobeViewport(1f, 1f)) }

    val fitted = remember(fit, viewport, session) { fit(viewport, session.maxLevel) }

    // The camera starts from the session, not from the fit: moving from the hero
    // to the immersive screen must not re-frame a view the user had already
    // moved. A route the session has not seen is the case where the fit applies.
    val adopted = session.routeKey == key
    val cameraState = remember(session) {
        GlobeCameraState(
            initial = if (adopted) session.camera else fitted,
            scope = scope,
            // The session knows how deep its imagery goes; the floor is the
            // altitude at which the finest tile is one texel per pixel, one
            // octave lower. A pinch stops there instead of ten doublings later
            // in magnified mush. See the class note on GlobeCameraState.
            zoomFloor = { box -> session.zoomFloor(box) },
            onChange = {
                session.camera = it
                session.cameraViewportHeight = viewport.height
            },
        )
    }

    // The state's copy of the box, for the fling's per-frame anchor and the
    // zoom's focus. Written after composition so the pointer handler never
    // observes a viewport newer than the frame it is handling.
    SideEffect { cameraState.viewport = viewport }

    // **A carried camera is rescaled to the box that adopts it.** The focal
    // length is proportional to the surface height, so the hero's camera shown
    // in the immersive screen — 2.3× taller — is the same view magnified 2.3×,
    // with both airports off the sides. The design says the frame grows while
    // the globe holds still; holding the picture still under a taller focal
    // length means the camera has to be that much further out. Once, when the
    // adopting surface first learns its height, and through the zoom path so the
    // imagery floor still binds.
    var carriedFromHeight by remember(session) {
        mutableStateOf(if (adopted) session.cameraViewportHeight else 0f)
    }
    LaunchedEffect(viewport) {
        val fromHeight = carriedFromHeight
        if (viewport.height <= 1f) return@LaunchedEffect
        if (fromHeight > 1f && fromHeight != viewport.height) {
            cameraState.zoomBy(factor = viewport.height / fromHeight, focus = null)
        }
        carriedFromHeight = 0f
    }

    // The fit the camera is currently parked at, or null if it arrived already
    // moved. This is what tells a re-fit apart from a reader's own framing
    // without either of them having to announce itself.
    var appliedFit by remember(session) {
        mutableStateOf(if (session.routeKey == key) null else fitted)
    }

    LaunchedEffect(session, key, arcs) {
        val isNewSubject = session.routeKey != key
        session.routeKey = key
        session.scene.setArcs(arcs)
        if (isNewSubject) {
            cameraState.set(fitted)
            appliedFit = fitted
        }
    }

    // **The viewport is not known at first composition, and it changes again when
    // the hero hands the route to the immersive screen.** Re-solving there is the
    // entire point of the fit knowing its box — a window half as wide for its
    // focal length needs the camera more than twice as far out to hold the same
    // leg. It runs only while the camera is still sitting exactly where the last
    // fit put it, so a reader who has panned or zoomed keeps their view across a
    // rotation and across the fullscreen toggle.
    LaunchedEffect(fitted) {
        val applied = appliedFit
        if (applied != null && applied != fitted && cameraState.camera == applied) {
            cameraState.set(fitted)
            appliedFit = fitted
        }
    }

    LaunchedEffect(session, ink) { session.scene.setInk(ink) }
    LaunchedEffect(session, reduceMotion) {
        session.scene.sharpenSeconds = if (reduceMotion) 0f else DefaultSharpenSeconds
    }
    // A fling or a spring already in flight when the setting flips is exactly
    // the motion the setting asks not to see. Stopped, not finished quickly.
    LaunchedEffect(cameraState, reduceMotion) { cameraState.cancelMotion() }

    // Whether the composition still has the surface inside the window. See the
    // note on the modifier below, and on GlobeSurfaceView.onScreen.
    var onScreen by remember { mutableStateOf(true) }
    var firstImagery by remember(session) { mutableStateOf(session.scene.hasDrawnImagery) }
    val globeAlpha = remember(session) {
        Animatable(if (session.scene.hasDrawnImagery) 1f else 0f)
    }

    LaunchedEffect(firstImagery, reduceMotion) {
        if (!firstImagery) return@LaunchedEffect
        // Reduce motion wants a swap, not a shorter fade.
        if (reduceMotion) globeAlpha.snapTo(1f) else globeAlpha.animateTo(1f, crossfade)
    }

    // Binding rather than a side effect: the handle holds no state of its own,
    // it only forwards, so pointing it at this camera is idempotent and has to
    // happen before the first control can be tapped.
    controls?.bind(cameraState, fitted)

    // **`clipToBounds`, and it is load-bearing.** The label plates are placed by
    // a `layout` block at a projected pixel, and Compose lets a child be placed
    // outside its parent and drawn there. A dot near the bottom of the sphere
    // therefore put its plate on the page *below* the hero, next to text it has
    // nothing to do with; the limb overlay drew its rim across the page for the
    // same reason. The globe owns its box and nothing it draws leaves it.
    Box(modifier = modifier.clipToBounds()) {
        AndroidView(
            factory = { ctx ->
                GlobeSurfaceView(ctx).apply {
                    cameraProvider = { cameraState.camera }
                    onFirstImagery = { firstImagery = true }
                    onViewportChanged = { viewport = it }
                }
            },
            update = { it.onScreen = onScreen },
            modifier = Modifier
                .fillMaxSize()
                // A Compose scroll carrying the hero off the page leaves the
                // `View` VISIBLE inside a visible window, so the platform's own
                // visibility signal never fires and the renderer kept drawing a
                // surface nobody could see. Measuring the box is the only way to
                // know, and it costs one rectangle intersection per layout.
                .onGloballyPositioned { coordinates ->
                    val bounds = coordinates.boundsInWindow()
                    onScreen = bounds.width > 0f && bounds.height > 0f
                },
        )

        // **The still map fades *out*, over the surface, rather than the surface
        // fading in.** The surface is a `SurfaceView` composited below the window
        // through a hole punched in it; a Compose `graphicsLayer { alpha }` on the
        // node hosting it never reaches the SurfaceControl, so the globe used to
        // arrive as a hard cut at the moment the first mesh landed — and with the
        // node rendered offscreen for the alpha, the hole punch could fail
        // outright. Drawing the still map on top and taking *it* to zero is the
        // same crossfade with the alpha on something Compose actually owns.
        //
        // Never removed: before the first tile it is the whole picture, and on a
        // device without a renderer this branch is never reached and it is the
        // whole feature.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = 1f - globeAlpha.value },
        ) {
            content()
        }

        LimbOverlay(
            cameraState = cameraState,
            viewport = viewport,
            ink = ink,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = globeAlpha.value },
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = globeAlpha.value },
        ) {
            markers(cameraState, viewport, ink.route, destinationInk)
        }

        // The input layer sits over the labels so the whole box is grabbable,
        // including the parts of it a plate happens to cover. Without
        // `interactive` there is no pointer handler at all — not a handler that
        // ignores what it is given — so a touch falls straight through to the
        // page underneath.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (interactive) {
                        Modifier.globeInput(
                            cameraState,
                            reduceMotion,
                            nestedVerticalScroll,
                            flingDecay,
                            spatialFast,
                        )
                    } else {
                        Modifier
                    },
                )
                .globeSemantics(description, cameraState, fitted, spatial, spatialFast),
        )

        overlay()
    }
}

/**
 * The chrome the concept puts on the glass, wired to a camera.
 *
 * Hoisted out of [GlobeSurface] so a host can place it where its own layout
 * wants it — bottom-right on the deep hero, top-right in the immersive screen —
 * without either of them re-deriving what a re-fit or a zoom step means.
 */
@Composable
fun rememberGlobeControls(): GlobeControlsHandle {
    val spatial = FlightMotion.spatial<Float>()
    val spatialFast = FlightMotion.spatialFast<Float>()
    return remember(spatial, spatialFast) {
        GlobeControlsHandle(spatial, spatialFast)
    }
}

/**
 * What the glass controls can ask the camera to do. See [GlobeCameraControls].
 *
 * It holds no coroutine scope of its own: every move it asks for is launched by
 * the camera state, which is the one place that can also cancel the fling a
 * tap on ± has to interrupt.
 */
class GlobeControlsHandle internal constructor(
    private val spatial: AnimationSpec<Float>,
    private val spatialFast: AnimationSpec<Float>,
) {
    private var state: GlobeCameraState? = null
    private var fitted: GlobeCamera? = null

    internal fun bind(state: GlobeCameraState, fitted: GlobeCamera) {
        this.state = state
        this.fitted = fitted
    }

    /** Degrees the view is turned from north, for the compass needle. */
    val bearingDegrees: Float get() = state?.camera?.bearingDegrees() ?: 0f

    /**
     * Where the camera is, for the module's own instrumented tests.
     *
     * The gesture pump is the one layer of the globe a JVM test cannot reach —
     * it lives inside `awaitPointerEventScope` — so the pinch and the drag are
     * proved on a device by driving the surface with synthetic pointers and
     * reading the camera back here. Internal, and not a UI affordance.
     */
    internal val altitude: Float get() = state?.camera?.altitude ?: Float.NaN
    internal val centerLon: Float get() = state?.camera?.centerLon ?: Float.NaN
    internal val centerLat: Float get() = state?.camera?.centerLat ?: Float.NaN

    /** Whether the view has been turned or leaned, so the reset control is worth offering. */
    val isRotated: Boolean get() = state?.camera?.isRotated() == true

    fun zoomIn() = step(-1)

    fun zoomOut() = step(1)

    /** Re-frames the whole leg. Spatial: the camera travels, and it should arrive. */
    fun refit() {
        val target = fitted ?: return
        val current = state ?: return
        current.animateTo(target, spatial)
    }

    /** North up and level, keeping position and zoom. */
    fun resetNorth() {
        val current = state ?: return
        current.animateTo(current.uprightCamera(), spatial)
    }

    private fun step(notches: Int) {
        val current = state ?: return
        current.animateTo(current.steppedZoom(notches), spatialFast)
    }
}

/**
 * Pan, pinch, twist, tilt, double-tap, quick-scale, and the fling that follows
 * a pan — [globeGestures] wired to the camera.
 *
 * One `pointerInput` block, where there were two. The second held a
 * `detectTapGestures` for the double-tap, and it never reliably fired: the first
 * block consumed every position change from the first move, and the tap detector
 * saw the consumed change on its final pass and gave up. The recogniser now
 * decides taps itself, from the same stream.
 *
 * Keyed on the state rather than on the viewport, because the state carries the
 * viewport now: a resize during a gesture no longer restarts the handler under
 * the fingers.
 */
private fun Modifier.globeInput(
    state: GlobeCameraState,
    reduceMotion: Boolean,
    nestedVerticalScroll: Boolean,
    flingDecay: DecayAnimationSpec<Offset>,
    zoomSpec: AnimationSpec<Float>,
): Modifier = pointerInput(state, reduceMotion, nestedVerticalScroll) {
    var anchor: Vec3? = null
    var anchorStart: Offset? = null

    globeGestures(nestedVerticalScroll) { event ->
        when (event) {
            // A touch always wins over a running camera motion: a re-fit or a
            // fling that kept going under the finger would be fighting it.
            GlobeGestureEvent.Down -> state.cancelMotion()

            is GlobeGestureEvent.Pan -> {
                // The anchor is re-taken whenever the start moves, which is at
                // every reseed: a finger landing or lifting changes what the
                // centroid is, and the world point under the new one is what
                // should stay under it from here.
                if (anchorStart != event.start) {
                    anchorStart = event.start
                    anchor = state.camera.screenToWorldClamped(
                        ScreenPoint(event.start.x, event.start.y),
                        state.viewport,
                    )
                }
                anchor?.let { state.panAnchor(it, ScreenPoint(event.position.x, event.position.y)) }
            }

            // Spreading the fingers means "closer", which is a *smaller*
            // altitude — hence the reciprocal. No clamp here: the recogniser has
            // already rejected implausible per-event ratios, and the altitude
            // range is the state's to enforce. The old `coerceIn(0.25f, 4f)` was
            // dead for a per-event factor and silently distorted the catch-up one.
            is GlobeGestureEvent.Zoom -> state.zoomBy(
                factor = 1f / event.factor,
                focus = ScreenPoint(event.focus.x, event.focus.y),
            )

            is GlobeGestureEvent.Rotate -> state.twistBy(event.radians)

            // Dragging down leans the view toward the horizon, which is the
            // direction the horizon itself moves under the fingers.
            is GlobeGestureEvent.Tilt -> state.tiltBy(event.dy * tiltRadiansPerPixel(state.viewport))

            // One notch in, about the tapped point, on the same spring as the ±
            // controls — it is the same move made a different way. The focus
            // is resolved the way a pinch's is, so a double-tap on the sky
            // zooms about the nearest edge of the globe and a double-tap near
            // the edge does not swing the camera; see GlobeCameraState.zoomedBy.
            is GlobeGestureEvent.DoubleTap -> state.animateTo(
                state.steppedZoom(-1, ScreenPoint(event.position.x, event.position.y)),
                zoomSpec,
            )

            is GlobeGestureEvent.End -> {
                anchor = null
                anchorStart = null
                if (event.flingEligible && !reduceMotion) state.fling(event.velocity, flingDecay)
            }
        }
    }
}

/**
 * The globe's limb, and the atmosphere along it.
 *
 * Drawn over the surface rather than in it, so the rim's `outline` and the
 * glow's `primary` are read where every other themed colour in this app is read.
 * The ring is the true projected silhouette — see [Limb] for why a screen-space
 * circle is wrong the moment the view tilts.
 */
@Composable
private fun LimbOverlay(
    cameraState: GlobeCameraState,
    viewport: GlobeViewport,
    ink: GlobeInk,
    modifier: Modifier = Modifier,
) {
    val points = remember { FloatArray(Limb.SAMPLES * 2) }
    Box(
        modifier = modifier.drawWithContent {
            drawContent()
            if (viewport.width < 1f) return@drawWithContent
            // Read inside the draw lambda: a state read in the draw phase
            // invalidates the drawing and nothing else, so a fling redraws the
            // rim without recomposing anything.
            val camera = cameraState.camera
            val count = Limb.projectInto(camera, camera.computeBasis(), viewport, points)
            if (count < 3) return@drawWithContent

            val path = Path().apply {
                moveTo(points[0], points[1])
                for (i in 1 until count) lineTo(points[i * 2], points[i * 2 + 1])
                // Closed only when the whole ring survived projection. At high
                // tilt part of the limb is behind the camera and the run is
                // genuinely open; closing it there draws a chord across the
                // planet.
                if (count == Limb.SAMPLES) close()
            }
            drawPath(
                path = path,
                color = ink.atmosphere.copy(alpha = AtmosphereAlpha),
                style = Stroke(
                    width = AtmosphereWidth.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )
            drawPath(
                path = path,
                color = ink.limb,
                style = Stroke(
                    width = RimWidth.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )
        },
    )
}

/**
 * What TalkBack is told about the globe, and what it can do to it.
 *
 * A `SurfaceView` has no semantics of its own, so all of it is here: a
 * description of what is drawn and where the camera is pointing, plus custom
 * actions for the four camera moves. Without the actions the globe would be
 * announced and then be inert, which is worse than being silent.
 */
@Composable
private fun Modifier.globeSemantics(
    description: String,
    state: GlobeCameraState,
    fitted: GlobeCamera,
    spatial: AnimationSpec<Float>,
    spatialFast: AnimationSpec<Float>,
): Modifier {
    // Every string this needs, resolved once. The bearing is deliberately not
    // among them: reading it here would make the whole semantics modifier a
    // per-frame subscriber during a fling, to produce a sentence nothing reads
    // until an accessibility service asks for it.
    val headingFormat = stringResource(R.string.globe_camera_state)
    val compass = List(COMPASS_POINTS) { stringResource(compassPointRes(it * 45f)) }
    val refit = stringResource(R.string.globe_action_refit)
    val north = stringResource(R.string.globe_action_north)
    val zoomIn = stringResource(R.string.globe_action_zoom_in)
    val zoomOut = stringResource(R.string.globe_action_zoom_out)

    return semantics {
        val bearing = state.camera.bearingDegrees()
        val point = compass[(((bearing + 22.5f) % 360f) / 45f).toInt() % COMPASS_POINTS]
        val heading = headingFormat.format(point, bearing.roundToInt())
        contentDescription = "$description. $heading"
        customActions = listOf(
            CustomAccessibilityAction(refit) {
                state.animateTo(fitted, spatial)
                true
            },
            CustomAccessibilityAction(north) {
                state.animateTo(state.uprightCamera(), spatial)
                true
            },
            CustomAccessibilityAction(zoomIn) {
                state.animateTo(state.steppedZoom(-1), spatialFast)
                true
            },
            CustomAccessibilityAction(zoomOut) {
                state.animateTo(state.steppedZoom(1), spatialFast)
                true
            },
        )
    }
}

/** North, north-east, and so on round to north-west. */
private const val COMPASS_POINTS = 8

/** The eight-point compass name for a bearing, for the spoken camera state. */
private fun compassPointRes(degrees: Float): Int =
    when ((((degrees + 22.5f) % 360f) / 45f).toInt()) {
        0 -> R.string.globe_compass_north
        1 -> R.string.globe_compass_northeast
        2 -> R.string.globe_compass_east
        3 -> R.string.globe_compass_southeast
        4 -> R.string.globe_compass_south
        5 -> R.string.globe_compass_southwest
        6 -> R.string.globe_compass_west
        else -> R.string.globe_compass_northwest
    }

/**
 * A full-height two-finger drag is 60° of tilt.
 *
 * Derived from the viewport rather than fixed per pixel, so the gesture covers
 * the same range of the camera on a phone and on a tablet — which is what makes
 * it the same gesture rather than a slower one on a bigger screen.
 */
private fun tiltRadiansPerPixel(viewport: GlobeViewport): Float =
    (Math.PI / 3.0).toFloat() / viewport.height.coerceAtLeast(1f)

/**
 * The tile sharpen duration, in seconds, mirroring the effects-fast token.
 *
 * A shader cannot call `FlightMotion`, so this is the one place in the app where
 * that duration is written as a number. The comment is what keeps it honest.
 */
private const val DefaultSharpenSeconds = 0.166f

/** The haze along the limb: `primary`, faint and wide. */
private const val AtmosphereAlpha = 0.30f
private val AtmosphereWidth = 5.dp

/** The rim itself: `outline`, a hairline. */
private val RimWidth = 1.5.dp
