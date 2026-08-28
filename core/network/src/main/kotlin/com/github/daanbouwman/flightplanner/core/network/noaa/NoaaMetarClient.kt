package com.github.daanbouwman.flightplanner.core.network.noaa

import com.github.daanbouwman.flightplanner.model.Metar
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * NOAA's Aviation Weather Center METAR feed.
 *
 * `https://aviationweather.gov/api/data/metar?ids=…&format=json` — keyless,
 * no SLA, and the app's default provider (docs/PLAN.md §6). Verified live and
 * comma-joined `ids` accepts a batch in one request, which is what makes F2's
 * "one request per screenful" possible.
 */
interface NoaaMetarClient {

    /** One request for all of [stationIcaos]. The caller has already chunked to ≤50. */
    suspend fun fetchMetars(stationIcaos: List<String>): List<Metar>
}

@Singleton
internal class DefaultNoaaMetarClient @Inject constructor(
    private val http: OkHttpClient,
    private val json: Json,
) : NoaaMetarClient {

    /**
     * `internal var`, not an `@Inject` constructor parameter with a default:
     * Dagger ignores Kotlin default parameter values and would look for an
     * unqualified `String` binding, which does not exist. A mutable property
     * lets a test in this module's own test source set (a "friend" module,
     * so `internal` is visible there) point this at a `MockWebServer`;
     * production code never touches it.
     */
    internal var baseUrl: String = DEFAULT_BASE_URL

    override suspend fun fetchMetars(stationIcaos: List<String>): List<Metar> {
        if (stationIcaos.isEmpty()) return emptyList()
        val ids = stationIcaos.joinToString(",")
        val request = Request.Builder().url("$baseUrl?ids=$ids&format=json").build()
        val body = execute(request) ?: return emptyList()
        val elements = runCatching { json.parseToJsonElement(body).jsonArray }.getOrNull() ?: return emptyList()
        // Each element parsed independently: one malformed station must not
        // discard the fifty others sharing its response.
        //
        // One observation per station, newest first. NOAA's `hours` parameter
        // returns *every* observation in the window, so a plain `mapNotNull`
        // would silently yield two rows for one station and whichever landed
        // last would win when the caller builds a map. `hours` is deliberately
        // not sent; this guard is here so adding it later cannot reintroduce
        // that.
        return elements.mapNotNull { element -> runCatching { element.toMetar() }.getOrNull() }
            .sortedByDescending { it.observationEpochSeconds ?: Long.MIN_VALUE }
            .distinctBy { it.station }
    }

    /**
     * `Call.enqueue` behind [suspendCancellableCoroutine], not a blocking
     * `execute()` on `Dispatchers.IO`. F2's `collectLatest` pipeline depends
     * on a superseded request's socket actually stopping when the collecting
     * coroutine is cancelled — only `enqueue` plus `invokeOnCancellation`
     * gives that; a blocking call would keep reading until its own timeout
     * regardless of what cancelled the coroutine awaiting it.
     */
    private suspend fun execute(request: Request): String? = suspendCancellableCoroutine { continuation ->
        val call = http.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                continuation.resume(null)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use { resp -> continuation.resume(if (resp.isSuccessful) resp.body.string() else null) }
            }
        })
    }

    private companion object {
        const val DEFAULT_BASE_URL = "https://aviationweather.gov/api/data/metar"
    }
}
