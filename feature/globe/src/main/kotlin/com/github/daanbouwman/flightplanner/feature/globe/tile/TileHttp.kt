package com.github.daanbouwman.flightplanner.feature.globe.tile

import com.github.daanbouwman.flightplanner.model.UserAgent
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.util.concurrent.TimeUnit

/**
 * The one HTTP client every tile request in the process goes through, and the
 * rule for how long what it fetches may be kept.
 *
 * ### Why one, built once
 *
 * One connection pool for every session: GIBS is HTTP/1.1, so a warm pooled
 * connection is the only thing saving a TLS handshake per tile, and a session
 * rebuilt on rotation would otherwise start cold. It is built lazily on the
 * first `GlobeSession.acquire`, never at application start.
 *
 * It carries **no** `okhttp3.Cache`. Tiles are cached by [TileDiskCache], which
 * says why; this client is only the wire, and [expiresAt] is the part of HTTP
 * caching the disk cache needs.
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

    private const val CONNECT_TIMEOUT_SECONDS = 4L
    private const val READ_TIMEOUT_SECONDS = 6L
    private const val CALL_TIMEOUT_SECONDS = 8L

    private const val MILLIS_PER_SECOND = 1_000L

    /**
     * Builds the client.
     *
     * [baseClient] is the client whose connection pool and dispatcher the result
     * shares; tests hand in one of their own so a `MockWebServer` can sit under
     * it. Production passes the default.
     */
    fun build(baseClient: OkHttpClient = OkHttpClient()): OkHttpClient =
        baseClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .addInterceptor(UserAgentInterceptor)
            .build()

    /**
     * When the tile in [response] stops being fresh, as wall-clock epoch millis,
     * or null when it must not be stored at all.
     *
     * An [immutable][TileProvider.immutable] provider's tiles never go stale,
     * whatever the origin said: GIBS sends `max-age=259200` — three days — on a
     * layer with no date in its path whose tiles never change. Anything else
     * keeps the origin's own policy, which is what Esri's licence question in
     * [EsriWorldImagery] needs: `no-store` is not stored, `max-age` (less the
     * `Age` a CDN has already spent) or `Expires` is honoured, and `no-cache` or
     * no stated lifetime at all is stored stale — revalidated before every use,
     * and still there to fall back on offline.
     *
     * Deliberately not OkHttp's full heuristic: a response with only
     * `Last-Modified` is not given a guessed lifetime. Neither provider sends one,
     * and guessing is how a mutable layer would come to be kept past its terms.
     */
    fun expiresAt(response: Response, provider: TileProvider): Long? {
        if (provider.immutable) return Long.MAX_VALUE
        val cacheControl = response.cacheControl
        if (cacheControl.noStore) return null
        if (cacheControl.noCache) return 0L
        val received = response.receivedResponseAtMillis
        if (cacheControl.maxAgeSeconds >= 0) {
            val age = response.header("Age")?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
            return received + (cacheControl.maxAgeSeconds - age) * MILLIS_PER_SECOND
        }
        val expires = response.headers.getDate("Expires") ?: return 0L
        val served = response.headers.getDate("Date")?.time ?: received
        return received + (expires.time - served)
    }

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
}
