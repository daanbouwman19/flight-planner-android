package com.github.daanbouwman.flightplanner.model

/**
 * How much of the sky one cloud layer covers, in eighths.
 *
 * The four values are the only ones a METAR reports for a layer with a
 * measurable base. Obscuration — sky hidden from the ground up, with no base to
 * measure — is not a cover value here; it is [SkyCover.Obscured], because it is
 * a different kind of fact and the two must not be confusable.
 */
enum class CloudCover(val code: String, val minOctas: Int, val maxOctas: Int) {
    FEW(code = "FEW", minOctas = 1, maxOctas = 2),
    SCATTERED(code = "SCT", minOctas = 3, maxOctas = 4),
    BROKEN(code = "BKN", minOctas = 5, maxOctas = 7),
    OVERCAST(code = "OVC", minOctas = 8, maxOctas = 8),
    ;

    /**
     * Whether a layer at this cover constitutes a **ceiling**.
     *
     * Broken or worse, which is the standard definition and the same one the
     * flight-rules thresholds in [FlightRules] are written against. Few and
     * scattered layers are cloud you can see but not cloud that stops you.
     */
    val isCeiling: Boolean get() = this == BROKEN || this == OVERCAST

    /** Midpoint of the octas range, for anything that wants one number. */
    val nominalOctas: Int get() = (minOctas + maxOctas) / 2

    companion object {
        /**
         * Parses a cover code, from either a raw METAR group or NOAA's JSON.
         *
         * Returns null for `OVX` on purpose: NOAA uses it for obscuration, which
         * is [SkyCover.Obscured] rather than a layer, and silently mapping it to
         * [OVERCAST] would invent a cloud base that was never measured. It also
         * returns null for `CLR`/`SKC`/`CAVOK`/`NSC`/`NCD`, which describe the
         * whole sky rather than a layer — see [SkyCover.Clear].
         */
        fun fromCode(code: String?): CloudCover? = when (code?.trim()?.uppercase()) {
            "FEW" -> FEW
            "SCT" -> SCATTERED
            "BKN" -> BROKEN
            "OVC" -> OVERCAST
            else -> null
        }
    }
}

/**
 * A convective type appended to a layer group — `BKN043CB`.
 *
 * Worth carrying because it is the difference between a broken deck and a broken
 * deck with thunderstorms in it. **NOAA's JSON drops this**: its `clouds[]` gives
 * `{"cover":"BKN","base":4300}` for a raw `BKN043CB`, so the modifier survives
 * only in the raw text. That is one of the reasons [MetarParser] exists.
 */
enum class ConvectiveCloud(val code: String) {
    CUMULONIMBUS("CB"),
    TOWERING_CUMULUS("TCU"),
    ;

    companion object {
        fun fromCode(code: String?): ConvectiveCloud? = when (code?.trim()?.uppercase()) {
            "CB" -> CUMULONIMBUS
            "TCU" -> TOWERING_CUMULUS
            else -> null
        }
    }
}

/** One reported cloud layer: how much, how high, and whether it is convective. */
data class CloudLayer(
    val cover: CloudCover,
    /** Base above ground level, in feet. */
    val baseFt: Int,
    val convective: ConvectiveCloud? = null,
)

/**
 * The state of the sky, as a type that cannot conflate "clear" with "unknown".
 *
 * **This distinction is the whole point of this file.** The previous model
 * reduced the sky to a nullable ceiling in feet, and a null there meant any of:
 * genuinely no cloud, no BKN/OVC layer among several reported layers, an
 * obscuration the parser did not recognise, a provider that decodes no cloud
 * data at all, or no report whatsoever. Those were then all rendered as a sunny
 * day, which is how an IFR airport came to be drawn with a sun on it.
 *
 * So the absence of information is [Unknown] — a value a renderer must handle —
 * and never a default that happens to look like good weather.
 */
sealed interface SkyCover {

    /**
     * No usable cloud information.
     *
     * Reached by an AVWX report (which decodes no cloud data), a cache row
     * written before the decoded columns existed, a raw METAR with no sky
     * groups, or a fetch that returned nothing. **Must never render as good
     * weather.**
     */
    data object Unknown : SkyCover

    /**
     * Affirmatively clear — the station said so.
     *
     * `CLR` (automated, no cloud below 12,000 ft), `SKC` (human observer, no
     * cloud at all), `NSC`/`NCD` (ICAO's equivalents), or `CAVOK`. This is a
     * measurement, not an inference from having found no layers.
     */
    data object Clear : SkyCover

    /** One or more reported layers, ordered by ascending [CloudLayer.baseFt]. */
    data class Layers(val layers: List<CloudLayer>) : SkyCover

    /**
     * Sky obscured from the ground up — an indefinite ceiling.
     *
     * Raw `VV002`; NOAA sends it as `cover: "OVX"` with a base in feet, and
     * separately as `vertVis` in *hundreds* of feet. There is no cloud base to
     * report because the observer cannot see one, only how far up they can see.
     * [verticalVisibilityFt] is null when the group was `VV///` — obscured, depth
     * unmeasured — which is a real report and not a parse failure.
     */
    data class Obscured(val verticalVisibilityFt: Int?) : SkyCover

    /** The reported ceiling, as a type that keeps "unknown" and "none" apart. */
    val ceiling: Ceiling
        get() = when (this) {
            Unknown -> Ceiling.Unknown
            Clear -> Ceiling.Unlimited
            is Obscured -> verticalVisibilityFt
                ?.let { Ceiling.At(ft = it, indefinite = true) }
                ?: Ceiling.Unknown
            is Layers -> layers.firstOrNull { it.cover.isCeiling }
                ?.let { Ceiling.At(ft = it.baseFt, indefinite = false) }
                // Layers were reported and none of them is broken or worse, so
                // there genuinely is no ceiling. This is the one place a
                // populated layer list still means "unlimited", and it is a
                // measurement rather than an absence.
                ?: Ceiling.Unlimited
        }
}

/**
 * A ceiling, or the reason there isn't one.
 *
 * A nullable `Int` cannot express this: null would have to stand for both "no
 * ceiling was reported" and "there is no ceiling", which are opposite facts. The
 * flight-rules derivation depends on telling them apart — an unknown ceiling
 * cannot produce a category at all, while an unlimited one produces VFR.
 */
sealed interface Ceiling {

    /** Not reported. Yields [FlightRules.UNKNOWN], never VFR. */
    data object Unknown : Ceiling

    /** Reported, and there is none — clear, or no layer broken or worse. */
    data object Unlimited : Ceiling

    /**
     * A ceiling at [ft] above ground.
     *
     * [indefinite] marks an obscuration, where the figure is how far up the
     * observer can see rather than the height of a cloud base. Worth keeping
     * because a 200 ft indefinite ceiling in fog and a 200 ft overcast deck are
     * the same number and different weather.
     */
    data class At(val ft: Int, val indefinite: Boolean = false) : Ceiling
}
