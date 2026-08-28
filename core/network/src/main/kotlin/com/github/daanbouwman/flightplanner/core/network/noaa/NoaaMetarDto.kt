package com.github.daanbouwman.flightplanner.core.network.noaa

import com.github.daanbouwman.flightplanner.model.Metar
import com.github.daanbouwman.flightplanner.model.MetarSupplement
import com.github.daanbouwman.flightplanner.model.Units
import com.github.daanbouwman.flightplanner.model.buildMetar
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import kotlin.math.roundToInt

/**
 * NOAA's `?format=json` METAR shape, mapped onto the domain [Metar].
 *
 * ### Only the fields that are *not* in the raw METAR text are read
 *
 * `cover`, `clouds[]`, `wxString`, `wdir`, `wspd`, `wgst` and `visib` are all
 * AWC's own decode of the same `rawOb` this app parses itself, and every one of
 * them is lossier than the text:
 *
 * - `clouds[]` gives `{"cover":"BKN","base":4300}` for a raw `BKN043CB` — **the
 *   CB is gone**, and with it the difference between a broken deck and a broken
 *   deck with thunderstorms in it.
 * - top-level `cover` collapses `CLR`, `SKC`, `CAVOK` and `NSC` into one value,
 *   which the raw text keeps apart.
 * - `wdir` returns the *string* `"VRB"` for a variable wind, which an `intOrNull`
 *   silently turns into "no wind direction reported" — two different facts.
 * - `visib` is polymorphic (Int, Double or `"10+"`) for no gain over the text.
 *
 * Consuming them would also force cloud layers into the cache, which is exactly
 * what [MetarSupplement] exists to avoid. **Two decoders for one string is what
 * produced the defect this redesign fixes**, so there is now one: the parser.
 * `NoaaCloudsCrossCheckTest` compares the two at test time, where a divergence
 * is information rather than a silent wrong render.
 *
 * Structured extraction is by hand rather than through a `@Serializable` class
 * because the accessors have to tolerate a field arriving as the wrong JSON type.
 *
 * Malformed input throws; [NoaaMetarClient] catches per element so one station's
 * shape drifting cannot discard a fifty-station batch.
 */
internal fun JsonElement.toMetar(): Metar {
    val obj = this as JsonObject
    val station = obj.string("icaoId") ?: error("no icaoId")
    return buildMetar(
        station = station,
        raw = obj.string("rawOb").orEmpty(),
        supplement = obj.toSupplement(),
    )
}

/** The facts a METAR text does not carry. See [MetarSupplement]. */
internal fun JsonObject.toSupplement(): MetarSupplement = MetarSupplement(
    flightRulesCode = string("fltCat"),
    reportKindCode = string("metarType"),
    // `obsTime` only. `reportTime` has at least three live formats across one
    // response and is never parsed.
    observationEpochSeconds = long("obsTime"),
    temperatureC = double("temp"),
    dewpointC = double("dewp"),
    seaLevelPressureHpa = double("slp"),
    hourlyPrecipInches = double("precip"),
    precip3hInches = double("pcp3hr"),
    precip6hInches = double("pcp6hr"),
    precip24hInches = double("pcp24hr"),
    snowDepthInches = double("snow"),
    latitude = double("lat"),
    longitude = double("lon"),
    // `elev` is METRES on the wire. Airport.elevationFt is FEET. Converted once,
    // here, so nothing downstream has to remember which unit it holds.
    elevationFt = double("elev")?.let { metres -> (metres * Units.METERS_TO_FEET).roundToInt() },
    stationName = string("name"),
)

/**
 * Tolerant accessors: absent, JSON `null`, and wrong-typed all yield null.
 *
 * The wrong-typed fallback is not defensive padding — `visib` genuinely arrives
 * as an Int, a Double and a String across stations in a single response, and a
 * numeric field quoted as a string is a shape NOAA has shipped before.
 */
private fun JsonObject.prim(key: String): JsonPrimitive? =
    (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }

private fun JsonObject.string(key: String): String? =
    prim(key)?.contentOrNull?.takeIf { it.isNotBlank() }

private fun JsonObject.double(key: String): Double? =
    prim(key)?.let { it.doubleOrNull ?: it.contentOrNull?.toDoubleOrNull() }

private fun JsonObject.long(key: String): Long? =
    prim(key)?.let { it.longOrNull ?: it.contentOrNull?.toLongOrNull() }
