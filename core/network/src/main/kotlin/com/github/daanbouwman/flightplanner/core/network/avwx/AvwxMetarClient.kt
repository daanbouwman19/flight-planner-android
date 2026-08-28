package com.github.daanbouwman.flightplanner.core.network.avwx

import com.github.daanbouwman.flightplanner.model.Metar
import com.github.daanbouwman.flightplanner.model.MetarSupplement
import com.github.daanbouwman.flightplanner.model.buildMetar
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.time.OffsetDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * The desktop app's weather provider, kept as an optional fallback
 * (docs/UI-PLAN.md F5) behind a masked API key in Settings.
 *
 * `GET https://avwx.rest/api/metar/{station}` with a plain
 * `Authorization: {key}` header — no `Bearer` prefix — ported from the Rust
 * reference (`weather_service.rs`).
 *
 * ### The narrow DTO stays narrow, for a completely different reason than before
 *
 * It used to be narrow because the desktop app was: `route_popup.rs` rendered a
 * badge and the raw text, so four fields sufficed. That made AVWX reports
 * **cloudless by construction** — no cloud data decoded at all — which was one
 * of the four ways an IFR field came to be drawn with a sun on it.
 *
 * It is narrow now because it does not need to be wide. AVWX returns `raw`, and
 * [com.github.daanbouwman.flightplanner.model.MetarParser] extracts strictly
 * more from that text than AVWX's own `clouds` array carries — `CB`/`TCU`
 * modifiers, `CLR` versus `SKC` versus `CAVOK`, `VV` obscuration, the
 * `110V200` wind range — with no AVWX-specific code and the same tests NOAA
 * gets. An AVWX report is now exactly as rich as a NOAA one.
 *
 * It also sidesteps a live unit trap: AVWX's `clouds[].base` is in **hundreds**
 * of feet where NOAA's is in feet, in a codebase that already has `elev` in
 * metres and `takeoff_distance_m` in metres.
 */
interface AvwxMetarClient {

    /** Null on a missing station (`204`/`400`), an auth failure, or a network error. */
    suspend fun fetchMetar(stationIcao: String, apiKey: String): Metar?
}

@Serializable
internal data class AvwxMetarDto(
    val raw: String? = null,
    @SerialName("flight_rules") val flightRules: String? = null,
    /**
     * AVWX's station identifier.
     *
     * This was `san`, which is **not a field AVWX has** — its keys are `station`
     * and, separately, `sanitized` (the cleaned raw text). The Rust reference has
     * the same mistake, and it was never caught because its test hand-wrote the
     * JSON. Harmless in both apps, since the fallback to the requested ICAO is
     * always correct, but it was dead code pretending to be a decode.
     */
    val station: String? = null,
    val time: AvwxTimeDto? = null,
)

@Serializable
internal data class AvwxTimeDto(val repr: String? = null, val dt: String? = null)

@Singleton
internal class DefaultAvwxMetarClient @Inject constructor(
    private val http: OkHttpClient,
    private val json: Json,
) : AvwxMetarClient {

    /** See [com.github.daanbouwman.flightplanner.core.network.noaa.DefaultNoaaMetarClient.baseUrl]. */
    internal var baseUrl: String = DEFAULT_BASE_URL

    override suspend fun fetchMetar(stationIcao: String, apiKey: String): Metar? {
        val request = Request.Builder()
            .url("$baseUrl/$stationIcao")
            .header("Authorization", apiKey)
            .build()
        val result = execute(request) ?: return null
        if (result.code == 204 || result.code == 400 || result.body.isNullOrBlank()) return null
        val dto = runCatching { json.decodeFromString<AvwxMetarDto>(result.body) }.getOrNull() ?: return null

        // A report whose only content is a flight-rules letter is not worth a
        // row: `raw` is what every decoded field is derived from, and the cache
        // refuses to store a row it could not faithfully return.
        val raw = dto.raw?.takeIf { it.isNotBlank() } ?: return null

        return buildMetar(
            station = dto.station?.takeIf { it.isNotBlank() } ?: stationIcao,
            raw = raw,
            supplement = MetarSupplement.None.copy(
                flightRulesCode = dto.flightRules,
                // OffsetDateTime, not Instant.parse: the ISO_INSTANT parser
                // rejects the `+00:00` offset form that AVWX does sometimes emit.
                observationEpochSeconds = dto.time?.dt?.let { text ->
                    runCatching { OffsetDateTime.parse(text).toEpochSecond() }.getOrNull()
                },
            ),
        )
    }

    private class HttpResult(val code: Int, val body: String?)

    /** See [com.github.daanbouwman.flightplanner.core.network.noaa.DefaultNoaaMetarClient.execute]. */
    private suspend fun execute(request: Request): HttpResult? = suspendCancellableCoroutine { continuation ->
        val call = http.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                continuation.resume(null)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use { resp -> continuation.resume(HttpResult(resp.code, resp.body.string())) }
            }
        })
    }

    private companion object {
        const val DEFAULT_BASE_URL = "https://avwx.rest/api/metar"
    }
}
