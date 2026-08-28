package com.github.daanbouwman.flightplanner.model

/** How heavy a present-weather group is. Encoded in METAR as `-`, nothing, or `+`. */
enum class WeatherIntensity { LIGHT, MODERATE, HEAVY }

/**
 * The optional descriptor in a present-weather group — the middle of
 * `[intensity][descriptor][phenomena]`.
 *
 * Exactly one may appear, and it qualifies whatever follows: `SHRA` is showers
 * *of rain*, `FZFG` is *freezing* fog, `BLSN` is *blowing* snow. [THUNDERSTORM]
 * is a descriptor rather than a phenomenon in the METAR grammar, which is why a
 * bare `TS` group is legal and means "thunderstorm, no precipitation reaching
 * the station".
 */
enum class WeatherDescriptor(val code: String) {
    SHALLOW("MI"),
    PARTIAL("PR"),
    PATCHES("BC"),
    LOW_DRIFTING("DR"),
    BLOWING("BL"),
    SHOWERS("SH"),
    THUNDERSTORM("TS"),
    FREEZING("FZ"),
    ;

    companion object {
        fun fromCode(code: String): WeatherDescriptor? =
            entries.firstOrNull { it.code == code.uppercase() }
    }
}

/** What kind of thing a phenomenon is, which is what the ground and the sky each care about. */
enum class PhenomenonKind {
    /** Something falling out of the sky. Wets or covers the ground. */
    PRECIPITATION,

    /** Something suspended in the air. Reduces visibility without wetting anything. */
    OBSCURATION,

    /** Everything else — whirls, squalls, storms. */
    OTHER,
}

/**
 * A present-weather phenomenon.
 *
 * The full WMO 4678 set a METAR may report, which is deliberately more than the
 * previous implementation's four hand-written lists covered — those missed
 * `SHSN`, `GR`, `FU`, `DU`, `BLSA`, `SQ`, `VA` and `PO`, and anything they
 * missed fell through to a ceiling-only guess and then to "clear".
 *
 * [frozen] marks precipitation that accumulates as snow or ice rather than as
 * water, which is what the ground surface needs to know.
 */
enum class WeatherPhenomenon(
    val code: String,
    val kind: PhenomenonKind,
    val frozen: Boolean = false,
) {
    // Precipitation
    DRIZZLE("DZ", PhenomenonKind.PRECIPITATION),
    RAIN("RA", PhenomenonKind.PRECIPITATION),
    SNOW("SN", PhenomenonKind.PRECIPITATION, frozen = true),
    SNOW_GRAINS("SG", PhenomenonKind.PRECIPITATION, frozen = true),
    ICE_CRYSTALS("IC", PhenomenonKind.PRECIPITATION, frozen = true),
    ICE_PELLETS("PL", PhenomenonKind.PRECIPITATION, frozen = true),
    HAIL("GR", PhenomenonKind.PRECIPITATION, frozen = true),
    SMALL_HAIL("GS", PhenomenonKind.PRECIPITATION, frozen = true),
    UNKNOWN_PRECIPITATION("UP", PhenomenonKind.PRECIPITATION),

    // Obscuration
    MIST("BR", PhenomenonKind.OBSCURATION),
    FOG("FG", PhenomenonKind.OBSCURATION),
    SMOKE("FU", PhenomenonKind.OBSCURATION),
    VOLCANIC_ASH("VA", PhenomenonKind.OBSCURATION),
    DUST("DU", PhenomenonKind.OBSCURATION),
    SAND("SA", PhenomenonKind.OBSCURATION),
    HAZE("HZ", PhenomenonKind.OBSCURATION),
    SPRAY("PY", PhenomenonKind.OBSCURATION),

    // Other
    DUST_WHIRLS("PO", PhenomenonKind.OTHER),
    SQUALLS("SQ", PhenomenonKind.OTHER),
    FUNNEL_CLOUD("FC", PhenomenonKind.OTHER),
    SANDSTORM("SS", PhenomenonKind.OTHER),
    DUSTSTORM("DS", PhenomenonKind.OTHER),
    ;

    companion object {
        /** Two-letter codes, longest-first irrelevant here since all are length 2. */
        fun fromCode(code: String): WeatherPhenomenon? =
            entries.firstOrNull { it.code == code.uppercase() }
    }
}

/**
 * One present-weather group, decoded — `-SHRA`, `+TSRA`, `VCSH`, `FZFG`, `BLSN`.
 *
 * A group is `[intensity or VC][descriptor][phenomena…]`, where the descriptor
 * and the phenomena are each optional but at least one must be present. `VCSH`
 * is a real group with a descriptor and no phenomenon; `TS` is a real group with
 * a descriptor and no phenomenon either.
 *
 * [code] keeps the original text so the raw group can be shown or logged when
 * the decoded form loses a nuance.
 */
data class PresentWeather(
    val intensity: WeatherIntensity = WeatherIntensity.MODERATE,
    /** `VC` — happening near the field rather than at it. */
    val inVicinity: Boolean = false,
    val descriptor: WeatherDescriptor? = null,
    val phenomena: List<WeatherPhenomenon> = emptyList(),
    val code: String = "",
) {
    /** Precipitation actually reaching the station — vicinity weather does not. */
    val isPrecipitating: Boolean
        get() = !inVicinity && phenomena.any { it.kind == PhenomenonKind.PRECIPITATION }

    /** Precipitation that accumulates as snow or ice rather than as water. */
    val isFrozenPrecipitation: Boolean
        get() = isPrecipitating && phenomena.any { it.frozen }

    /**
     * Freezing rain or drizzle — liquid that freezes on contact.
     *
     * Distinct from [isFrozenPrecipitation]: this glazes a surface with ice,
     * which is a different ground condition from snow lying on it, and a much
     * more serious one for an aircraft.
     */
    val isFreezingPrecipitation: Boolean
        get() = descriptor == WeatherDescriptor.FREEZING &&
            phenomena.any { it.kind == PhenomenonKind.PRECIPITATION }

    val isThunderstorm: Boolean get() = descriptor == WeatherDescriptor.THUNDERSTORM

    /** Something suspended in the air, reducing visibility. */
    val isObscuring: Boolean
        get() = phenomena.any { it.kind == PhenomenonKind.OBSCURATION }

    /** Fog or mist specifically — what lies on the ground rather than hazing the distance. */
    val isFogOrMist: Boolean
        get() = phenomena.any { it == WeatherPhenomenon.FOG || it == WeatherPhenomenon.MIST }
}
