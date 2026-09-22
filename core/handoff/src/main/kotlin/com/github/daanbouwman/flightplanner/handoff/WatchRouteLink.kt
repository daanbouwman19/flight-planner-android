package com.github.daanbouwman.flightplanner.handoff

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * A route as it crosses from the watch to the phone.
 *
 * Deliberately *not* [com.github.daanbouwman.flightplanner.routing.GeneratedRoute]
 * and not the phone's `Destination.RouteDetail`. The generated route names its
 * airports by slot in the index that produced it, and two processes with two
 * copies of the asset have no business trusting each other's slot numbers; the
 * phone's destination names the airframe by its Room row id, which the watch
 * cannot know. What both sides *can* agree on is the pair of ICAO codes, the
 * distance already computed, and enough of the airframe's identity to find it
 * again in whatever fleet the phone is carrying.
 */
data class WatchRoute(
    val departureIcao: String,
    val destinationIcao: String,
    val distanceNm: Int,
    /**
     * The airframe's type code, e.g. `B738`. Not an airport code, and not
     * unique — several variants share one — so it is the fallback, not the key.
     */
    val aircraftTypeCode: String,
    /** [com.github.daanbouwman.flightplanner.model.AircraftSpec.displayName]. */
    val aircraftName: String,
)

/**
 * The one URI encoding of a [WatchRoute], written on the watch and read on the
 * phone.
 *
 * This is the same argument `LaunchIntents` makes for the widgets' Intent
 * extras, one process further apart: the watch app and the phone app are
 * separate APKs on separate devices, and the only thing holding their
 * understanding of a link together is that they compile against this file.
 * `WatchRouteLinkTest` round-trips it, so a change to the grammar breaks a
 * unit test rather than a tap on a wrist.
 *
 * It lives in a pure-JVM module rather than in `:app` because the watch cannot
 * see `:app` and must not grow a second, hand-synchronised copy of the format
 * — and because `android.net.Uri` is unavailable to a JVM test, so the codec
 * would otherwise only be testable on a device.
 *
 * The grammar is `flightplanner://route/<departure>/<destination>?<query>`,
 * with the airframe in the query rather than the path because a display name
 * contains spaces and slashes and a path segment should not.
 */
object WatchRouteLink {

    const val SCHEME: String = "flightplanner"
    const val HOST: String = "route"

    private const val PARAM_DISTANCE = "nm"
    private const val PARAM_TYPE_CODE = "ac"
    private const val PARAM_NAME = "name"

    /** Codes are four characters at most, so an over-long segment is not one. */
    private const val MAX_ICAO_LENGTH = 8

    /**
     * Writes [route] as a URI string.
     *
     * Percent-encoded with `application/x-www-form-urlencoded` rules, which
     * spell a space `+`; [parse] decodes with the same rules, so the pair
     * round-trips. A name is free text from a CSV the user can edit, so it is
     * never interpolated raw.
     */
    fun build(route: WatchRoute): String = buildString {
        append(SCHEME).append("://").append(HOST)
        append('/').append(encode(route.departureIcao.trim().uppercase()))
        append('/').append(encode(route.destinationIcao.trim().uppercase()))
        append('?').append(PARAM_DISTANCE).append('=').append(route.distanceNm)
        append('&').append(PARAM_TYPE_CODE).append('=').append(encode(route.aircraftTypeCode))
        append('&').append(PARAM_NAME).append('=').append(encode(route.aircraftName))
    }

    /**
     * Reads a URI string, or null when it is not one of ours.
     *
     * Null rather than an exception for everything: a link arriving from
     * another device is input, and the only safe reading of malformed input is
     * "do nothing". That covers a wrong scheme or host, a missing or
     * unparseable code, and a distance that is not a number — the same posture
     * `LaunchIntents.parse` takes toward a malformed Intent.
     *
     * The distance is the one field allowed to be absent: it is a property of
     * the route rather than part of its identity, and the phone recomputes the
     * real figure from the index once it has both ends. A missing airframe is
     * not tolerated the same way, because a route without one is not a route.
     */
    fun parse(uri: String?): WatchRoute? {
        val parsed = runCatching { URI(uri ?: return null) }.getOrNull() ?: return null
        if (!SCHEME.equals(parsed.scheme, ignoreCase = true)) return null
        if (!HOST.equals(parsed.host, ignoreCase = true)) return null

        val segments = parsed.rawPath.orEmpty().split('/').filter { it.isNotEmpty() }
        if (segments.size != 2) return null
        val departure = icao(segments[0]) ?: return null
        val destination = icao(segments[1]) ?: return null

        val query = query(parsed.rawQuery)
        val typeCode = query[PARAM_TYPE_CODE]?.takeIf { it.isNotBlank() } ?: return null
        val name = query[PARAM_NAME]?.takeIf { it.isNotBlank() } ?: return null
        val distance = query[PARAM_DISTANCE]?.let { it.toIntOrNull() ?: return null } ?: 0
        if (distance < 0) return null

        return WatchRoute(
            departureIcao = departure,
            destinationIcao = destination,
            distanceNm = distance,
            aircraftTypeCode = typeCode,
            aircraftName = name,
        )
    }

    private fun icao(segment: String): String? =
        decode(segment).trim().uppercase().takeIf { it.isNotEmpty() && it.length <= MAX_ICAO_LENGTH }

    /**
     * The query as a map, last occurrence winning.
     *
     * A bare key with no `=` is dropped rather than mapped to the empty string:
     * every parameter here carries a value, so a key alone is malformed, and
     * dropping it lets the required-field checks above reject it.
     */
    private fun query(raw: String?): Map<String, String> {
        if (raw.isNullOrEmpty()) return emptyMap()
        val out = mutableMapOf<String, String>()
        for (pair in raw.split('&')) {
            val separator = pair.indexOf('=')
            if (separator <= 0) continue
            out[decode(pair.substring(0, separator))] = decode(pair.substring(separator + 1))
        }
        return out
    }

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8)

    /**
     * Decodes one component, falling back to the raw text.
     *
     * `URLDecoder` throws on a truncated escape such as a trailing `%`, and a
     * malformed escape in an otherwise readable code should leave the caller
     * to reject the code on its own terms rather than take the whole link down.
     */
    private fun decode(value: String): String =
        runCatching { URLDecoder.decode(value, Charsets.UTF_8) }.getOrDefault(value)
}
