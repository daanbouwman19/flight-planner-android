package com.github.daanbouwman.flightplanner.feature.globe.tile

import com.github.daanbouwman.flightplanner.model.UserAgent
import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.util.concurrent.TimeUnit

/**
 * The one HTTP client every tile request in the process goes through.
 *
 * ### Why one, built once
 *
 * There used to be a bare `OkHttpClient()` shared by sessions and, on top of
 * it, a **new** 96 MB `okhttp3.Cache` opened over the same directory by every
 * `TileLoader` and never closed. OkHttp requires exclusive access to a cache
 * directory; two `Cache` objects on one directory is undefined behaviour that
 * happens to mostly work. Building the client here, once, and handing the same
 * instance to every loader is what makes the cache a single owner's.
 *
 * It is built lazily on the first `GlobeSession.acquire`, never at application
 * start: opening a disk cache is a directory scan, and nothing here may run
 * before the first frame.
 *
 * ### Timeouts
 *
 * Connect 4 s, read 6 s, call 8 s. The call timeout is the load-bearing one — it
 * is a total deadline, which is what `reqwest`'s `.timeout(10 s)` in the Rust
 * reference is too — and it is tighter than the reference on purpose. A tile
 * that has not arrived in eight seconds is not going to be missed: a coarse
 * ancestor is already drawn under it, and the next frame re-requests it. What
 * would be missed is a worker held for ten seconds on a dead connection while
 * the tiles behind it queue.
 *
 * `retryOnConnectionFailure` stays at its default of true. Turning it off
 * converts the ordinary recovery from a stale pooled connection into a hard
 * failure and a thirty-second backoff on a tile that was one retry from landing.
 *
 * ### Concurrency
 *
 * The loader's worker count is the concurrency, not `Dispatcher.maxRequestsPerHost`.
 * That limit applies to `enqueue()` only and the loader uses blocking
 * `execute()`; and GIBS is HTTP/1.1-only (their documentation says so, and ALPN
 * confirms it), so there is no multiplexing to lean on either.
 */
internal object TileHttp {

    /**
     * Disk cache for tiles.
     *
     * Sized to hold the pinned base levels many times over, so airplane mode
     * after any real use still draws a recognisable planet. Its own directory
     * rather than the app's shared one, because tile traffic would otherwise
     * evict the METAR responses — which are tiny, and expensive to lose.
     */
    private const val DISK_CACHE_BYTES = 96L * 1024 * 1024

    private const val CONNECT_TIMEOUT_SECONDS = 4L
    private const val READ_TIMEOUT_SECONDS = 6L
    private const val CALL_TIMEOUT_SECONDS = 8L

    /**
     * What an immutable provider's tiles are stored as, regardless of what the
     * origin said.
     *
     * GIBS sends `max-age=259200` — three days — on a layer with no date in its
     * path whose tiles never change. This tells the cache the truth about them.
     */
    private const val IMMUTABLE_CACHE_CONTROL = "public, max-age=31536000, immutable"

    /**
     * Builds the client, with its cache at [cacheDir].
     *
     * [baseClient] is the client whose connection pool and dispatcher the result
     * shares; tests hand in one of their own so a `MockWebServer` can sit under
     * it. Production passes the default.
     */
    fun build(cacheDir: File, baseClient: OkHttpClient = OkHttpClient()): OkHttpClient =
        baseClient.newBuilder()
            .cache(Cache(cacheDir, DISK_CACHE_BYTES))
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .addInterceptor(UserAgentInterceptor)
            .addInterceptor(OfflineFallbackInterceptor)
            .addNetworkInterceptor(ImmutableCachePolicyInterceptor)
            .build()

    /**
     * Identifies this app to the providers with the same string
     * `core/network`'s `NetworkModule` sends to the weather services — one
     * constant in `:core:model`, which both modules already depend on. The
     * clients themselves stay separate on purpose; see the class note.
     */
    private object UserAgentInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response =
            chain.proceed(
                chain.request().newBuilder().header(UserAgent.HEADER, UserAgent.VALUE).build(),
            )
    }

    /**
     * A network interceptor that rewrites the cache policy of a successful
     * response from an immutable provider.
     *
     * A **network** interceptor, so the rewritten headers are what the cache
     * stores; an application interceptor sees the response after the cache has
     * already written it. Guarded on 2xx so an error body is stored — if at all
     * — for exactly as long as the origin said, never for a year.
     *
     * The provider is read off the request's tag, which the loader sets, so one
     * shared client serves an immutable and a mutable provider at once.
     */
    private object ImmutableCachePolicyInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            val response = chain.proceed(request)
            val provider = request.tag(TileProvider::class.java)
            if (provider?.immutable != true || !response.isSuccessful) return response
            return response.newBuilder()
                .header("Cache-Control", IMMUTABLE_CACHE_CONTROL)
                .removeHeader("Pragma")
                .removeHeader("Expires")
                .build()
        }
    }

    /**
     * An application interceptor that serves a stale cached tile when the
     * network fails.
     *
     * The ordinary offline case: the pinned levels and everything seen recently
     * are on disk, and a stale tile of the right place is better than a coarse
     * ancestor of it. The retry asks the cache with `FORCE_CACHE`, and the
     * answer is used only when it is a **real hit** — a `FORCE_CACHE` miss is a
     * synthetic 504 with no body, and returning that would be handing the
     * loader an "error" to count and back off from instead of the
     * `IOException` it needs to reach `failed()`.
     *
     * A call that failed by timing out has been cancelled by then, and OkHttp
     * refuses to proceed a cancelled call, so this covers a refused or absent
     * network rather than a slow one. That is the right split: a timed-out call
     * has spent its budget, and the next frame re-requests the tile.
     */
    private object OfflineFallbackInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            val networkFailure = try {
                return chain.proceed(request)
            } catch (e: IOException) {
                e
            }
            val cached = try {
                chain.proceed(request.newBuilder().cacheControl(CacheControl.FORCE_CACHE).build())
            } catch (e: IOException) {
                // On the failure that is actually thrown, so the cache lookup's
                // own reason reaches a log rather than dying with the exception
                // that is discarded — and never on itself: OkHttp can report the
                // same instance for both attempts when the call was cancelled,
                // and `addSuppressed` throws IllegalArgumentException for that,
                // out of an interceptor, where the worker would memo it as a
                // failed tile rather than an offline fallback.
                if (e !== networkFailure) networkFailure.addSuppressed(e)
                throw networkFailure
            }
            if (cached.code == HttpURLConnection.HTTP_GATEWAY_TIMEOUT || cached.cacheResponse == null) {
                cached.close()
                throw networkFailure
            }
            return cached
        }
    }
}
