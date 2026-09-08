package com.github.daanbouwman.flightplanner.feature.globe.render

import android.app.ActivityManager
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.github.daanbouwman.flightplanner.feature.globe.math.GlobeCamera
import com.github.daanbouwman.flightplanner.feature.globe.math.GlobeFit
import com.github.daanbouwman.flightplanner.feature.globe.math.GlobeViewport
import com.github.daanbouwman.flightplanner.feature.globe.math.MIN_ALTITUDE
import com.github.daanbouwman.flightplanner.feature.globe.tile.ImageryAttribution
import com.github.daanbouwman.flightplanner.feature.globe.tile.TileAtlas
import com.github.daanbouwman.flightplanner.feature.globe.tile.TileHttp
import com.github.daanbouwman.flightplanner.feature.globe.tile.TileKey
import com.github.daanbouwman.flightplanner.feature.globe.tile.TileLoader
import com.github.daanbouwman.flightplanner.feature.globe.tile.TileProvider
import com.github.daanbouwman.flightplanner.feature.globe.tile.TileProviders
import com.google.android.filament.Engine
import com.google.android.filament.Filament
import okhttp3.OkHttpClient
import java.io.File
import kotlin.math.max

private const val TAG = "GlobeSession"

/** Why the globe is or is not available on this device. */
internal enum class GlobeSupport {
    /** A renderer came up and there is memory for the atlas. */
    Available,

    /** Filament could not create an engine on any backend. */
    NoRenderer,

    /** The platform reports a low-RAM device; 32 MB of atlas is not affordable. */
    LowMemory,
}

/**
 * The one Filament engine, the one tile atlas, and the camera they share.
 *
 * ### Why this is a process singleton
 *
 * The atlas is 32 MB. A session per `SurfaceView` would mean two of them during
 * the hero-to-immersive transition, when both surfaces are briefly alive, and
 * 64 MB of texture for one globe is exactly the mistake PLAN.md records the
 * desktop's 128 MB LRU making. One session, many views.
 *
 * It also gives the design what it asks for at no cost. The immersive screen is
 * a different `SurfaceView` and **a `SurfaceView` cannot be a shared element**,
 * so there is no way to morph the sphere from the hero into the full window.
 * With the camera living here rather than in either screen, it does not need to
 * be: the new surface starts from exactly the state the old one was left at, and
 * what the user sees is the frame around the globe growing while the globe holds
 * still. That is the honest version of the transition and it is what the design
 * specifies.
 *
 * ### Teardown is deferred — except when the app itself goes away
 *
 * Detaching the last view starts a short timer rather than destroying the
 * engine, because a navigation composes the destination before it disposes the
 * source often enough — but not always. Tearing down and rebuilding an engine
 * and an atlas across a transition would be a black rectangle where the globe
 * was, for the length of a re-download.
 *
 * That reasoning stops applying the moment the app is backgrounded: nothing
 * detaches then — the composition holding a globe simply stops being drawn —
 * so the 2 s timer never starts and the 32 MB atlas, the engine and the tile
 * workers were held for as long as the app stayed in the recent-apps list,
 * unbounded. [ProcessLifecycleOwner]'s `ON_STOP` and a live
 * [ComponentCallbacks2.onTrimMemory] level both force the teardown immediately
 * instead — see [forceTeardown] — and record [lastRouteKey]/[lastCamera] so
 * the view that is still composed underneath, once the app is foregrounded
 * again, rebuilds a session that picks the camera up where it left off rather
 * than silently re-framing the route. See [reacquireIfNeeded].
 */
internal class GlobeSession private constructor(
    context: Context,
    val engine: Engine,
) {
    /** Set once, by [destroy]. A view holding a reference past this point must re-acquire. */
    var isDestroyed: Boolean = false
        private set

    private val provider: TileProvider = TileProviders.active

    val loader = TileLoader(provider = provider, client = tileClient(context))
    val scene = GlobeScene(context.applicationContext, engine, loader)

    /**
     * Where the globe is being looked at from.
     *
     * On the session rather than in a `remember`, so it survives the surface
     * being destroyed and recreated — a rotation, or the move to the immersive
     * screen. This is the state the design means by "the camera simply keeps its
     * state across the change".
     */
    var camera: GlobeCamera = GlobeCamera()

    /** The route currently loaded, so a re-attach does not re-upload the same arc. */
    var routeKey: String? = null

    /**
     * The height, in pixels, of the surface [camera] was last driven in.
     *
     * A camera is not a view. The projection's focal length is half the surface
     * height over `tan(fovY / 2)`, so the same [camera] carried from the 44%
     * hero into the full-height immersive screen magnifies everything by the
     * height ratio — about 2.3× — and puts both airports off the sides. Holding
     * the *picture* still across the change, which is what the design asks for,
     * means scaling the altitude by that ratio when the next surface adopts the
     * camera; this is the number it scales from. Zero until a surface has said.
     */
    var cameraViewportHeight: Float = 0f

    /** The credit the live provider requires — see [ImageryAttribution]. */
    val imagery: ImageryAttribution get() = provider.attribution

    /** The deepest level the live imagery publishes: 8 keyless, 18 with an ArcGIS key. */
    val maxLevel: Int get() = provider.maxLevel

    /**
     * The closest the camera may be pinched to, in [viewport].
     *
     * Half of [GlobeFit.sharpestAltitude] — one octave of deliberate over-zoom
     * past the point where the imagery is one texel per pixel. The fit itself
     * stops at that point, because a route should not *open* on an upsampled
     * photograph; a reader pinching in is asking for the last of the detail, and
     * one doubling shows them what there is before the picture turns to blur.
     *
     * This is a divergence from `camera.rs`, which clamps to plain `MIN_DISTANCE`
     * and needs no such floor: at its `MAX_LOD` of 18 the halved floor is at or
     * under `MIN_ALTITUDE` on a phone-sized viewport (0.95 × 10⁻⁴ at 2340 px
     * tall), so the two coincide. The keyless fallback publishes to z8, where the
     * sharpest altitude is a thousand times higher, and without this a pinch
     * would carry the camera 637 m above a tile drawn at a texel per fifty
     * pixels. The `max` keeps the keyed case where the reference has it.
     */
    fun zoomFloor(viewport: GlobeViewport): Float =
        max(MIN_ALTITUDE, GlobeFit.sharpestAltitude(viewport, provider.maxLevel) / 2f)

    init {
        // Adopt whatever the last session was showing, one shot. Ordinary
        // construction — the first-ever session, or one built fresh after the
        // deferred timer actually ran out — finds both null and starts at the
        // defaults, same as before. A session rebuilt by [reacquireIfNeeded]
        // after a forced teardown finds the route and camera [forceTeardown]
        // recorded, and `GlobeCanvas`'s own `adopted` check treats this
        // exactly like the hero-to-immersive carry it already handles.
        routeKey = lastRouteKey
        camera = lastCamera ?: GlobeCamera()
        lastRouteKey = null
        lastCamera = null

        loader.start()
        // Warm the permanent base levels immediately, so a coarse planet exists
        // from the first frames on and every leaf has an ancestor to fall back
        // to. Deepest level first: the queue is newest-first within a level and
        // coarse-first across them, so this order says what is wanted — z0/0/0
        // out of the socket before anything else — rather than relying on the
        // buckets to correct it. 21 tiles, and after the first run they come
        // from the disk cache.
        for (z in TileAtlas.PINNED_MAX_LEVEL downTo 0) {
            val span = 1 shl z
            for (x in 0 until span) {
                for (y in 0 until span) {
                    loader.request(TileKey.of(z, x, y))
                }
            }
        }
    }

    private fun destroy() {
        isDestroyed = true
        loader.shutdown()
        scene.destroy()
        engine.destroy()
    }

    companion object {
        /**
         * How long the engine outlives its last view.
         *
         * Long enough to cover a navigation that disposes the old screen after
         * composing the new one, short enough that backing out of the route
         * detail gives the 32 MB back promptly.
         */
        private const val TEARDOWN_DELAY_MS = 2_000L

        /** The tiles' own disk cache, apart from the app's METAR cache. */
        private const val TILE_CACHE_DIR = "globe-tiles"

        /**
         * The one tile HTTP client for the process.
         *
         * Built on the first [acquire] — never at application start, because
         * opening a disk cache is a directory scan — and kept for the life of
         * the process, because OkHttp requires exclusive access to a cache
         * directory and the sessions come and go. See [TileHttp].
         */
        private var tileClient: OkHttpClient? = null

        private fun tileClient(context: Context): OkHttpClient =
            tileClient ?: TileHttp.build(File(context.cacheDir, TILE_CACHE_DIR)).also {
                tileClient = it
            }

        private val handler = Handler(Looper.getMainLooper())

        private var instance: GlobeSession? = null
        private var attachedViews = 0
        private var support: GlobeSupport? = null

        /** What the session was showing, across a [forceTeardown]. See [reacquireIfNeeded]. */
        private var lastRouteKey: String? = null
        private var lastCamera: GlobeCamera? = null

        /**
         * The surfaces attached right now, so a forced teardown can ask each to
         * let go of its own Filament objects first. See [AttachedSurfaces].
         */
        private val surfaces = AttachedSurfaces()

        internal fun registerSurface(view: GlobeSurfaceView) = surfaces.register(view)

        internal fun unregisterSurface(view: GlobeSurfaceView) = surfaces.unregister(view)

        /** Registers the process-level watchers at most once. See [ensureWatchersRegistered]. */
        private var watchersRegistered = false

        private val teardown = Runnable {
            if (attachedViews == 0) {
                instance?.destroy()
                instance = null
            }
        }

        /**
         * `ON_STOP` and a live [ComponentCallbacks2] level both call this, and it
         * is idempotent under both firing for the same backgrounding. Unlike
         * [teardown] it does not check [attachedViews]: the composition holding a
         * globe is still there, unbroken, underneath an app the user has simply
         * left — nothing detaches, so the count staying above zero is not a
         * reason to keep 32 MB of atlas resident for as long as the app sits in
         * the recent-apps list.
         *
         * **`internal` for the instrumented test**, which calls it directly.
         * Driving it through a real backgrounding instead sounds better and is
         * not: neither `ON_STOP` nor a trim callback is reliably delivered to an
         * app under instrumentation, so a test that waits for one passes without
         * ever running this — which is exactly what the first attempt did.
         */
        internal fun forceTeardown() {
            handler.removeCallbacks(teardown)
            instance?.let { session ->
                lastRouteKey = session.routeKey
                lastCamera = session.camera
                // **Every attached surface lets go before anything shared is
                // destroyed.** Each one owns a RouteRibbon holding two
                // MaterialInstances of a material `session.destroy()` frees, and
                // Filament refuses to free a material while an instance of it is
                // alive — see forcedTeardown. Nothing detaches when an app is
                // backgrounded, so this is the only thing that asks them.
                forcedTeardown(surfaces) { session.destroy() }
            }
            instance = null
        }

        /**
         * The [ProcessLifecycleOwner] observer and the [ComponentCallbacks2]
         * registration that drive [forceTeardown], installed once on the first
         * [acquire].
         *
         * Not at application start: `androidx.startup` and `Application.onCreate`
         * are cold-start budget, and this has nothing to watch until a globe has
         * been shown at least once.
         */
        private fun ensureWatchersRegistered(context: Context) {
            if (watchersRegistered) return
            watchersRegistered = true

            ProcessLifecycleOwner.get().lifecycle.addObserver(
                object : DefaultLifecycleObserver {
                    override fun onStop(owner: LifecycleOwner) = forceTeardown()
                },
            )

            // `onLowMemory()` is required by `ComponentCallbacks` (hence
            // implemented) but is itself deprecated in favour of
            // `onTrimMemory`, which is why the override below carries the same
            // annotation — the compiler's own suggested alternative to
            // suppressing the "overrides a deprecated member" diagnostic.
            context.applicationContext.registerComponentCallbacks(
                object : ComponentCallbacks2 {
                    override fun onTrimMemory(level: Int) {
                        // Compared against one named level rather than switched
                        // on all of them: a throwaway probe (compiled, read,
                        // deleted — CLAUDE.md's rule for exactly this question)
                        // found `TRIM_MEMORY_MODERATE`, `_COMPLETE` and every
                        // `_RUNNING_*` level deprecated in the pinned SDK — the
                        // background-trim levels a cached process used to
                        // receive are not delivered the way they once were — and
                        // referencing a deprecated constant is exactly the
                        // warning CLAUDE.md's "no @Suppress" rule means not to
                        // write around. `TRIM_MEMORY_UI_HIDDEN` and
                        // `_BACKGROUND` were the two that survived, and the
                        // lower of them already means what this needs: the UI
                        // this session's atlas is for is no longer visible.
                        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) forceTeardown()
                    }

                    override fun onConfigurationChanged(newConfig: Configuration) = Unit

                    @Deprecated("Deprecated in ComponentCallbacks; superseded by onTrimMemory.")
                    override fun onLowMemory() = Unit
                },
            )
        }

        /**
         * Whether this device can show a globe at all, cached for the process.
         *
         * ### The divergence from the design, stated
         *
         * The concept says "no Vulkan … the globe never composes". This accepts
         * whichever backend Filament actually brings up, preferring Vulkan and
         * settling for OpenGL. Refusing a working OpenGL engine would remove the
         * feature from devices that can run it perfectly well in order to honour
         * a sentence about an API, and what the design is actually about is what
         * the user sees when there is *no* renderer — which is unchanged: the
         * still map stays, nothing is drawn to explain it, and the fullscreen
         * button is absent because a control that opens nothing is worse than no
         * control.
         *
         * Answered from what the platform declares rather than by building an
         * engine to find out — this is called from composition, so it may not
         * block. See [resolveSupport]. Cached either way, and still resolved on
         * first use rather than at startup: nothing here may run before the
         * first frame.
         */
        fun support(context: Context): GlobeSupport = support ?: resolveSupport(context).also {
            support = it
        }

        private fun resolveSupport(context: Context): GlobeSupport {
            val activityManager = context.getSystemService(ActivityManager::class.java)
            if (activityManager?.isLowRamDevice == true) return GlobeSupport.LowMemory

            // **A declared capability, not a built engine.** This runs inside
            // composition - the nav host asks it on every route detail, and so do
            // Stats and Settings - and building a Filament engine there blocked the
            // first caller for as long as the native driver took to come up, only to
            // destroy it and have `acquire` build another one immediately.
            //
            // Every device reporting GLES 3.0 brings up Filament's GL backend, so
            // this answers the question the probe was answering. A device that
            // reports it and then fails is caught where it actually matters:
            // `acquire` caches `NoRenderer` when `createEngine` returns null, and
            // the control is gone on the next composition.
            val glEsVersion = activityManager?.deviceConfigurationInfo?.reqGlEsVersion ?: 0
            return if (glEsVersion >= GLES_3) GlobeSupport.Available else GlobeSupport.NoRenderer
        }

        /** `reqGlEsVersion` packs the major version high: 3.0 is 0x0003_0000. */
        private const val GLES_3 = 0x0003_0000

        /**
         * The session, building it if there is none — shared by [acquire] and
         * [reacquireIfNeeded], which differ only in whether they count a view.
         */
        private fun ensureInstance(context: Context): GlobeSession? {
            instance?.let { return it }

            if (support(context) != GlobeSupport.Available) return null
            val engine = createEngine() ?: run {
                support = GlobeSupport.NoRenderer
                return null
            }
            return GlobeSession(context.applicationContext, engine).also { instance = it }
        }

        /**
         * Acquires the session, creating it if this is the first view.
         *
         * Returns null on a device with no renderer, which the caller must treat
         * as "draw the still map and no fullscreen control" rather than as an
         * error to report.
         */
        fun acquire(context: Context): GlobeSession? {
            handler.removeCallbacks(teardown)
            ensureWatchersRegistered(context)
            attachedViews++
            val session = ensureInstance(context)
            if (session == null) attachedViews--
            return session
        }

        /**
         * Rebuilds the session for a view that never detached — [forceTeardown]
         * tore it down while the app was backgrounded, out from under a
         * composition that is still, unbroken, right there.
         *
         * **Does not touch [attachedViews].** The view calling this was already
         * counted by the [acquire] that first attached it; counting it again
         * here would leave the tally permanently one over the true number of
         * attached views, and this class exists specifically so that the last
         * one going away tears the session down promptly — a leak of exactly
         * the kind [forceTeardown] was written to close.
         */
        internal fun reacquireIfNeeded(context: Context): GlobeSession? {
            handler.removeCallbacks(teardown)
            return ensureInstance(context)
        }

        fun release() {
            attachedViews = (attachedViews - 1).coerceAtLeast(0)
            if (attachedViews == 0) {
                handler.postDelayed(teardown, TEARDOWN_DELAY_MS)
            }
        }

        private fun createEngine(): Engine? {
            val loaded = runCatching { Filament.init() }
                .onFailure { Log.e(TAG, "Filament native library failed to load", it) }
                .isSuccess
            if (!loaded) return null

            // Vulkan first, because the probe confirmed FEATURE_LEVEL_3 on it and
            // it is the backend the phase was planned against. `Engine.Backend`
            // lists VULKAN whether or not the native library can provide it, so
            // asking and then failing is the only way to find out.
            return runCatching { Engine.Builder().backend(Engine.Backend.VULKAN).build() }
                .recoverCatching { Engine.Builder().backend(Engine.Backend.OPENGL).build() }
                .onFailure { Log.e(TAG, "no Filament backend could be created", it) }
                .getOrNull()
        }
    }
}
