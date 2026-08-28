package com.github.daanbouwman.flightplanner.model

import kotlin.math.roundToInt

/**
 * Everything a raw METAR string yields, decoded.
 *
 * Only the fields the raw text actually carries. Flight category is absent on
 * purpose — a METAR does not state one; NOAA computes it and sends it alongside,
 * so it is supplied separately rather than guessed here.
 */
data class ParsedMetar(
    val station: String? = null,
    /** The day/time group as reported, e.g. `271751Z`. */
    val dayTimeZulu: String? = null,
    val isSpeci: Boolean = false,
    val isAutomated: Boolean = false,
    val isCorrected: Boolean = false,
    /** True when the report carried the `$` maintenance-needed flag. */
    val maintenanceNeeded: Boolean = false,

    val skyCover: SkyCover = SkyCover.Unknown,
    val presentWeather: List<PresentWeather> = emptyList(),

    val windDirectionDeg: Int? = null,
    /** `VRB` — direction genuinely varying, which is not the same as unreported. */
    val windVariable: Boolean = false,
    val windSpeedKt: Int? = null,
    val windGustKt: Int? = null,
    /** The `110V200` group: the range a varying wind swung through. */
    val windRangeFromDeg: Int? = null,
    val windRangeToDeg: Int? = null,

    val visibilityStatuteMiles: Double? = null,
    /** True for `10+SM` or a `9999` ICAO group — "this or better". */
    val visibilityIsOrGreater: Boolean = false,

    val temperatureC: Double? = null,
    val dewpointC: Double? = null,

    val altimeterInHg: Double? = null,
    val altimeterHectopascals: Double? = null,
    /**
     * Which convention the station itself used.
     *
     * Worth keeping because NOAA normalises everything to hectopascals and the
     * app then converts to inches, so a European field's `Q1008` would come out
     * as `29.77 inHg` — a number the pilot has to convert back. Showing what was
     * actually transmitted is the truthful option.
     */
    val altimeterConvention: AltimeterConvention? = null,

    /** `CAVOK` — ceiling and visibility OK. Implies clear sky and 10 km+ visibility. */
    val cavok: Boolean = false,
) {
    /** The derived ground surface, from the air and whatever extras are supplied. */
    fun groundCondition(
        hourlyPrecipInches: Double? = null,
        snowDepthInches: Double? = null,
    ): GroundCondition = GroundConditions.derive(
        temperatureC = temperatureC,
        dewpointC = dewpointC,
        presentWeather = presentWeather,
        hourlyPrecipInches = hourlyPrecipInches,
        snowDepthInches = snowDepthInches,
    )
}

/** Whether a station reported its altimeter setting in inches of mercury or hectopascals. */
enum class AltimeterConvention { INCHES_MERCURY, HECTOPASCALS }

/**
 * A tokeniser for raw METAR text.
 *
 * **Why this exists rather than trusting the provider's structured fields.**
 * `raw` is the only lossless channel the app has: it is present on every path
 * (both providers and the cache), and it carries several things no structured
 * field does — every cloud layer with its `CB`/`TCU` modifier, `VV` obscuration,
 * an affirmative `CLR`/`SKC`/`CAVOK`, the `A` versus `Q` altimeter convention,
 * and the `110V200` wind range. NOAA's JSON drops all of those; AVWX decodes
 * almost nothing. Parsing the text once means every provider and every cache row
 * yields the same richness.
 *
 * ### What it deliberately does not do
 *
 * It **stops at `RMK`**. The remarks section is free-form, station-specific and
 * effectively unbounded; the two useful figures in it (`P0001` hourly
 * precipitation, `4/012` snow depth) are supplied as structured fields by NOAA
 * for the stations that report them, so mining remarks would be re-deriving
 * worse copies of data already in hand.
 *
 * It **also stops at a trend group** — `TEMPO`, `BECMG`, `NOSIG`, or an
 * `FMnnnn`/`TLnnnn`/`ATnnnn` time marker. This is correctness rather than
 * tidiness: everything after one of those describes the *forecast*, so reading
 * `TEMPO 3000 SHRA` as part of the observation would invent a 3,000 m visibility
 * and rain that is not falling. A current-conditions view must not report the
 * future as the present.
 *
 * It does not parse runway visual range (`R09/1200`), runway state (`88CLRD//`)
 * or wind shear (`WS R25`). Each describes one runway, nothing in this app shows
 * any of them, and guessing would be worse than the gap.
 *
 * It **never throws and never guesses**. An unrecognised token is skipped. A
 * report with nothing recognisable yields a [ParsedMetar] whose every field is
 * absent — which is the honest answer, and which the sky renders as unknown
 * rather than as fine weather.
 */
object MetarParser {

    private val DAY_TIME = Regex("""^(\d{6})Z$""")
    private val WIND = Regex("""^(\d{3}|VRB)(\d{2,3})(?:G(\d{2,3}))?(KT|MPS|KMH)$""")
    private val WIND_RANGE = Regex("""^(\d{3})V(\d{3})$""")
    private val SKY_LAYER = Regex("""^(FEW|SCT|BKN|OVC)(\d{3}|///)(CB|TCU)?$""")
    private val VERTICAL_VIS = Regex("""^VV(\d{3}|///)$""")
    private val TEMP_DEW = Regex("""^(M?\d{2})/(M?\d{2})?$""")
    private val ALTIMETER_INHG = Regex("""^A(\d{4})$""")
    private val ALTIMETER_HPA = Regex("""^Q(\d{4})$""")
    private val VIS_STATUTE = Regex("""^(M)?(\d{1,2})?(?:(\d)/(\d))?SM$""")
    private val VIS_METRES = Regex("""^(\d{4})(NDV)?$""")
    private val PRESENT_WEATHER = Regex(
        """^([-+]|VC)?(MI|PR|BC|DR|BL|SH|TS|FZ)?((?:DZ|RA|SN|SG|IC|PL|GR|GS|UP|BR|FG|FU|VA|DU|SA|HZ|PY|PO|SQ|FC|SS|DS)*)$""",
    )

    private val CLEAR_CODES = setOf("CLR", "SKC", "NSC", "NCD")

    /**
     * Tokens that end the observation body.
     *
     * `RMK` opens a free-form, station-specific section. The other four open a
     * **forecast**, and treating a forecast's groups as current conditions is a
     * silent wrong render — the reason this set exists rather than just `RMK`.
     */
    private val BODY_TERMINATORS = setOf("RMK", "RMKS", "TEMPO", "BECMG", "NOSIG")

    /** `FM1200`, `TL0300`, `AT1830` — a trend's time marker, and equally a forecast. */
    private val TREND_TIME = Regex("""^(FM|TL|AT)\d{4}$""")

    /** Metres-per-statute-mile, for the ICAO visibility group. */
    private const val METRES_PER_STATUTE_MILE = 1609.344

    /** An ICAO visibility of `9999` means 10 km or more. */
    private const val ICAO_VIS_UNLIMITED_M = 9999

    fun parse(raw: String): ParsedMetar {
        if (raw.isBlank()) return ParsedMetar()

        // Uppercased with Locale.ROOT explicitly: a Turkish locale maps `i` to
        // `İ`, and this app has already shipped one locale-digit defect.
        val upper = raw.uppercase(java.util.Locale.ROOT).trim().removeSuffix("=")

        // Truncate at remarks *or* at the first trend group, whichever comes
        // first. See the class KDoc — everything past a trend marker is a
        // forecast, and reading it as an observation invents weather.
        val allTokens = upper.split(Regex("""\s+""")).filter { it.isNotEmpty() }
        val bodyEnd = allTokens.indexOfFirst { it in BODY_TERMINATORS || TREND_TIME.matches(it) }
        val tokens = if (bodyEnd >= 0) allTokens.take(bodyEnd) else allTokens
        if (tokens.isEmpty()) return ParsedMetar()

        // The `$` maintenance flag is the one group that lives *after* remarks,
        // at the very end of the report, so the body scan below cannot see it.
        // It is read off the whole string instead. Worth carrying: it means the
        // station's own sensors are flagged as needing attention, which is a
        // reason to trust the figures less.
        //
        // Off `upper`, not off `raw`. Reports arrive `=`-terminated — which is why
        // `upper` strips one above — so testing the raw text meant `… RMK AO2 $=`
        // never set the flag, and the "trust this station less" signal was lost for
        // exactly the feeds that terminate their reports.
        var result = ParsedMetar(maintenanceNeeded = upper.trimEnd().endsWith("$"))
        val layers = mutableListOf<CloudLayer>()
        val weather = mutableListOf<PresentWeather>()
        var obscured: SkyCover.Obscured? = null
        var affirmativelyClear = false
        var sawStation = false
        var index = 0

        while (index < tokens.size) {
            val token = tokens[index]

            when {
                token == "METAR" -> Unit
                token == "SPECI" -> result = result.copy(isSpeci = true)
                token == "AUTO" -> result = result.copy(isAutomated = true)
                token == "COR" -> result = result.copy(isCorrected = true)

                token == "CAVOK" -> {
                    affirmativelyClear = true
                    result = result.copy(
                        cavok = true,
                        visibilityIsOrGreater = true,
                        visibilityStatuteMiles = ICAO_VIS_UNLIMITED_M / METRES_PER_STATUTE_MILE,
                    )
                }

                token in CLEAR_CODES -> affirmativelyClear = true

                DAY_TIME.matches(token) ->
                    result = result.copy(dayTimeZulu = token)

                WIND.matches(token) -> {
                    val match = WIND.find(token)!!
                    val (direction, speed, gust, unit) = match.destructured
                    val factor = speedFactorToKnots(unit)
                    result = result.copy(
                        windVariable = direction == "VRB",
                        windDirectionDeg = direction.toIntOrNull(),
                        // Rounded, not truncated. `toInt()` drops the fraction, so
                        // every metric station under-reports: `05MPS` is 9.72 kt and
                        // came out as 9, `10MPS` is 19.44 and came out as 19. It is
                        // under a knot each time and it is systematic, in one
                        // direction, on the whole non-US world — and it feeds
                        // `SurfaceWind.favouredEnd`, which has a threshold.
                        windSpeedKt = speed.toIntOrNull()?.let { (it * factor).roundToInt() },
                        windGustKt = gust.toIntOrNull()?.let { (it * factor).roundToInt() },
                    )
                }

                WIND_RANGE.matches(token) -> {
                    val match = WIND_RANGE.find(token)!!
                    result = result.copy(
                        windRangeFromDeg = match.groupValues[1].toIntOrNull(),
                        windRangeToDeg = match.groupValues[2].toIntOrNull(),
                    )
                }

                SKY_LAYER.matches(token) -> {
                    val match = SKY_LAYER.find(token)!!
                    val cover = CloudCover.fromCode(match.groupValues[1])
                    val hundreds = match.groupValues[2].toIntOrNull()
                    // `///` is a real report: layer present, height unmeasured by
                    // an automated station. Skipped rather than invented.
                    if (cover != null && hundreds != null) {
                        layers += CloudLayer(
                            cover = cover,
                            baseFt = hundreds * 100,
                            convective = ConvectiveCloud.fromCode(
                                match.groupValues[3].takeIf { it.isNotEmpty() },
                            ),
                        )
                    }
                }

                VERTICAL_VIS.matches(token) -> {
                    val hundreds = VERTICAL_VIS.find(token)!!.groupValues[1].toIntOrNull()
                    obscured = SkyCover.Obscured(verticalVisibilityFt = hundreds?.times(100))
                }

                TEMP_DEW.matches(token) -> {
                    val match = TEMP_DEW.find(token)!!
                    result = result.copy(
                        temperatureC = signedTemperature(match.groupValues[1]),
                        dewpointC = signedTemperature(match.groupValues[2]),
                    )
                }

                ALTIMETER_INHG.matches(token) -> {
                    val raw4 = ALTIMETER_INHG.find(token)!!.groupValues[1].toIntOrNull()
                    if (raw4 != null) {
                        result = result.copy(
                            altimeterInHg = raw4 / 100.0,
                            altimeterConvention = AltimeterConvention.INCHES_MERCURY,
                        )
                    }
                }

                ALTIMETER_HPA.matches(token) -> {
                    val raw4 = ALTIMETER_HPA.find(token)!!.groupValues[1].toIntOrNull()
                    if (raw4 != null) {
                        result = result.copy(
                            altimeterHectopascals = raw4.toDouble(),
                            altimeterConvention = AltimeterConvention.HECTOPASCALS,
                        )
                    }
                }

                token.endsWith("SM") && VIS_STATUTE.matches(token) -> {
                    // A mixed number arrives as two tokens — `1 1/2SM`. The whole
                    // part is the token before, and only if it is a bare integer
                    // that nothing else has claimed.
                    val previous = tokens.getOrNull(index - 1)
                    val wholeFromPrevious = previous
                        ?.takeIf { it.toIntOrNull() != null && result.visibilityStatuteMiles == null }
                        ?.toIntOrNull()
                    val parsed = parseStatuteVisibility(token, wholeFromPrevious)
                    if (parsed != null) {
                        result = result.copy(
                            visibilityStatuteMiles = parsed.miles,
                            visibilityIsOrGreater = parsed.orGreater,
                        )
                    }
                }

                // The ICAO metre group. Guarded on not already having a visibility
                // so a stray four-digit token cannot overwrite a good `SM` value.
                VIS_METRES.matches(token) && result.visibilityStatuteMiles == null -> {
                    val metres = VIS_METRES.find(token)!!.groupValues[1].toIntOrNull()
                    if (metres != null) {
                        result = result.copy(
                            visibilityStatuteMiles = metres / METRES_PER_STATUTE_MILE,
                            visibilityIsOrGreater = metres >= ICAO_VIS_UNLIMITED_M,
                        )
                    }
                }

                // The station identifier: a bare four-character code, taken only
                // once and only near the front, so a later token cannot claim it.
                !sawStation && index <= 2 && token.length == 4 && token.all { it.isLetterOrDigit() } -> {
                    sawStation = true
                    result = result.copy(station = token)
                }

                else -> {
                    val group = parsePresentWeather(token)
                    if (group != null) weather += group
                }
            }

            index++
        }

        val sky = when {
            obscured != null -> obscured
            layers.isNotEmpty() -> SkyCover.Layers(layers.sortedBy { it.baseFt })
            affirmativelyClear -> SkyCover.Clear
            else -> SkyCover.Unknown
        }

        return result.copy(skyCover = sky, presentWeather = weather)
    }

    /** `M02` → −2.0, `23` → 23.0, empty → null. */
    private fun signedTemperature(group: String): Double? {
        if (group.isEmpty()) return null
        val negative = group.startsWith("M")
        val digits = group.removePrefix("M").toIntOrNull() ?: return null
        return if (negative) -digits.toDouble() else digits.toDouble()
    }

    private fun speedFactorToKnots(unit: String): Double = when (unit) {
        "MPS" -> 1.943844
        "KMH" -> 0.539957
        else -> 1.0
    }

    private class Visibility(val miles: Double, val orGreater: Boolean)

    /**
     * `10SM`, `7SM`, `1/2SM`, `M1/4SM`, and the trailing half of `1 1/2SM`.
     *
     * `M` means "less than"; the prefix is dropped rather than made negative,
     * because the reportable minimum is the information and a negative distance
     * is not a thing.
     */
    private fun parseStatuteVisibility(token: String, wholeFromPrevious: Int?): Visibility? {
        val match = VIS_STATUTE.find(token) ?: return null
        val (lessThan, whole, numerator, denominator) = match.destructured

        val fraction = if (numerator.isNotEmpty() && denominator.isNotEmpty()) {
            val n = numerator.toDoubleOrNull() ?: return null
            val d = denominator.toDoubleOrNull() ?: return null
            if (d == 0.0) return null else n / d
        } else {
            0.0
        }
        val wholePart = whole.toIntOrNull()?.toDouble()
            ?: wholeFromPrevious?.toDouble().takeIf { fraction > 0.0 }
            ?: 0.0

        val total = wholePart + fraction
        if (total <= 0.0 && lessThan.isEmpty()) return null
        return Visibility(miles = total, orGreater = false)
    }

    /**
     * `-SHRA`, `+TSRA`, `VCSH`, `FZFG`, `BLSN`, `TS`, `BR`.
     *
     * Returns null unless the token yields at least a descriptor or one
     * phenomenon, so ordinary tokens that happen to match the (very permissive)
     * shape do not become empty weather groups.
     */
    private fun parsePresentWeather(token: String): PresentWeather? {
        val match = PRESENT_WEATHER.find(token) ?: return null
        val prefix = match.groupValues[1]
        val descriptorCode = match.groupValues[2]
        val phenomenaCodes = match.groupValues[3]

        val descriptor = descriptorCode.takeIf { it.isNotEmpty() }?.let(WeatherDescriptor::fromCode)
        val phenomena = phenomenaCodes.chunked(2).mapNotNull(WeatherPhenomenon::fromCode)
        if (descriptor == null && phenomena.isEmpty()) return null

        return PresentWeather(
            intensity = when (prefix) {
                "-" -> WeatherIntensity.LIGHT
                "+" -> WeatherIntensity.HEAVY
                else -> WeatherIntensity.MODERATE
            },
            inVicinity = prefix == "VC",
            descriptor = descriptor,
            phenomena = phenomena,
            code = token,
        )
    }
}
