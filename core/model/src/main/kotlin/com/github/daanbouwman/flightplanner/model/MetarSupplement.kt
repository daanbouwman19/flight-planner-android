package com.github.daanbouwman.flightplanner.model

/**
 * Everything about an observation that is **not** in the raw METAR text.
 *
 * This type is the contract between the providers, the cache and [buildMetar],
 * and it is what makes the cache correct by construction: **`raw` plus a
 * supplement reconstructs a [Metar] exactly**, so the cache stores precisely
 * these fields and nothing else. Cloud layers, present weather, wind,
 * visibility and the altimeter are never stored — they are re-derived from
 * `raw` by [MetarParser] on read, through the same [buildMetar] a fresh fetch
 * uses.
 *
 * That removes an entire class of defect. The previous cache kept a
 * hand-maintained second copy of the decoded fields, and its own KDoc warned
 * that a forgotten field would read as "data flickering between two visits of
 * the same station within fifteen minutes". There is now nothing to forget.
 *
 * **No field has a default value.** Adding one here is a compile error at every
 * producer and at the cache mapping until it is wired, which is the guard the
 * old flat mapping lacked. Use [None] for "no provider metadata at all".
 */
data class MetarSupplement(
    /** NOAA `fltCat` / AVWX `flight_rules`. The official category; a METAR states none. */
    val flightRulesCode: String?,
    /** NOAA `metarType` — `METAR` or `SPECI`, when the raw text carries no prefix. */
    val reportKindCode: String?,
    /**
     * NOAA `obsTime` / AVWX `time.dt`, as epoch seconds.
     *
     * Epoch seconds rather than a string on purpose: NOAA's `reportTime` has at
     * least three live formats and is never read, and the raw text's `ddhhmmZ`
     * group carries no month or year.
     */
    val observationEpochSeconds: Long?,
    /**
     * NOAA `temp` / `dewp`, in tenths of a degree.
     *
     * The one place a provider's decode beats the raw text: AWC reads the
     * remarks `Tddddd` group, so it returns 23.3 / 21.7 where the raw body says
     * `23/22`. Tenths of dewpoint spread drive the frost inference, so the
     * precision earns its place here and nowhere else.
     */
    val temperatureC: Double?,
    val dewpointC: Double?,
    /** NOAA `slp`, hectopascals. In the remarks, which the parser does not read. */
    val seaLevelPressureHpa: Double?,
    /** NOAA `precip` — inches in the last hour. Null means *not reported*, not "no rain". */
    val hourlyPrecipInches: Double?,
    val precip3hInches: Double?,
    val precip6hInches: Double?,
    val precip24hInches: Double?,
    /** NOAA `snow` — lying snow depth, inches. US stations, winter only. */
    val snowDepthInches: Double?,
    /** NOAA `lat` / `lon`. Not in a METAR at all, and what the celestial position needs. */
    val latitude: Double?,
    val longitude: Double?,
    /**
     * Station elevation in **feet**.
     *
     * NOAA sends `elev` in **metres**; it is converted once, at the network
     * boundary, so that every elevation inside the app is feet — matching
     * [Airport.elevationFt]. Mixing the two was a live trap.
     */
    val elevationFt: Int?,
    /** NOAA `name` — "Amsterdam/Schiphol Arpt, NH, NL". */
    val stationName: String?,
) {
    companion object {
        /** No provider metadata at all — a bare raw string, or an AVWX report before its two fields land. */
        val None = MetarSupplement(
            flightRulesCode = null,
            reportKindCode = null,
            observationEpochSeconds = null,
            temperatureC = null,
            dewpointC = null,
            seaLevelPressureHpa = null,
            hourlyPrecipInches = null,
            precip3hInches = null,
            precip6hInches = null,
            precip24hInches = null,
            snowDepthInches = null,
            latitude = null,
            longitude = null,
            elevationFt = null,
            stationName = null,
        )
    }
}

/**
 * The single merge point: raw text, decoded, overlaid with what only a provider
 * knows.
 *
 * Precedence is **raw text for everything it can extract, supplement for
 * everything it cannot**, with two documented exceptions:
 *
 * - **Flight category** comes from [MetarSupplement.flightRulesCode] when it
 *   names a real category, because a METAR does not state one and NOAA's is the
 *   official answer. A blank or unrecognised value falls through to local
 *   derivation rather than poisoning the result with `UNKNOWN` — **and so does
 *   an unreported sky**, for the reason below.
 *
 * ### Why an unreported sky takes the category away from the provider
 *
 * `ZUZH` sends `9999 // //////` — an automated station whose cloud sensor
 * returned nothing — and NOAA computes `fltCat: "VFR"` from that same missing
 * sky. Rendered, the chip said VFR beside a scene hatched to say *we do not know
 * what the sky is*: the original defect's exact shape, surviving in the one
 * element the redesign had not touched.
 *
 * So when [MetarParser] finds no sky at all, the provider's category is not
 * consulted and [FlightRules.derive] decides instead. That is deliberately **not
 * the same as forcing [FlightRules.UNKNOWN]**, which would have been the obvious
 * fix and is a worse one: a fogged-in field reporting `1/4SM` with no cloud group
 * is genuinely LIFR on the visibility alone, and blanking that would trade a
 * false reassurance for a suppressed warning.
 *
 * The property that makes this safe is already proved in [FlightRules.derive] and
 * is not restated here: with [Ceiling.Unknown] it can only return [FlightRules.LIFR]
 * — where one known-good side already forces the worst category — or
 * [FlightRules.UNKNOWN]. It cannot return VFR, MVFR or IFR. So the chip may now
 * say *nothing* or say *the weather is bad*, and there is no report for which it
 * can say the weather is fine over a sky nobody measured.
 * - **Temperature and dewpoint** prefer the supplement, which carries tenths
 *   where the raw body carries whole degrees. See that field's KDoc.
 *
 * Everything else the supplement carries is absent from the raw body entirely,
 * so there is no contest.
 *
 * ### The property that makes the cache provably safe
 *
 * ```
 * buildMetar(s, r, buildMetar(s, r, sup).toSupplement()) == buildMetar(s, r, sup)
 * ```
 *
 * Storing the merged value and re-merging yields the merged value, so a cache
 * round trip is the identity. `MetarSupplementTest` asserts it.
 */
fun buildMetar(station: String, raw: String, supplement: MetarSupplement): Metar {
    val parsed = MetarParser.parse(raw)

    val providerRules = supplement.flightRulesCode
        ?.let(FlightRules::fromCode)
        ?.takeIf { it != FlightRules.UNKNOWN }
        // The veto. See "Why an unreported sky takes the category away from the
        // provider" above: NOAA is willing to call a station VFR off a sky its
        // own sensor did not report, and this app is not.
        ?.takeIf { parsed.skyCover !is SkyCover.Unknown }

    val temperature = supplement.temperatureC ?: parsed.temperatureC
    val dewpoint = supplement.dewpointC ?: parsed.dewpointC

    return Metar(
        station = parsed.station ?: station,
        raw = raw,
        flightRules = providerRules
            ?: FlightRules.derive(parsed.skyCover.ceiling, parsed.visibilityStatuteMiles),
        observationTime = parsed.dayTimeZulu,
        observationEpochSeconds = supplement.observationEpochSeconds,

        skyCover = parsed.skyCover,
        presentWeather = parsed.presentWeather,

        windDirectionDeg = parsed.windDirectionDeg,
        windVariable = parsed.windVariable,
        windSpeedKt = parsed.windSpeedKt,
        windGustKt = parsed.windGustKt,
        windRangeFromDeg = parsed.windRangeFromDeg,
        windRangeToDeg = parsed.windRangeToDeg,

        visibilityStatuteMiles = parsed.visibilityStatuteMiles,
        visibilityIsOrGreater = parsed.visibilityIsOrGreater,

        temperatureC = temperature,
        dewpointC = dewpoint,

        altimeterInHg = parsed.altimeterInHg,
        altimeterHectopascals = parsed.altimeterHectopascals,
        altimeterConvention = parsed.altimeterConvention,
        seaLevelPressureHpa = supplement.seaLevelPressureHpa,

        hourlyPrecipInches = supplement.hourlyPrecipInches,
        precip3hInches = supplement.precip3hInches,
        precip6hInches = supplement.precip6hInches,
        precip24hInches = supplement.precip24hInches,
        snowDepthInches = supplement.snowDepthInches,

        latitude = supplement.latitude,
        longitude = supplement.longitude,
        elevationFt = supplement.elevationFt,
        stationName = supplement.stationName,

        isAutomated = parsed.isAutomated,
        isSpeci = parsed.isSpeci || supplement.reportKindCode?.uppercase() == "SPECI",
        maintenanceNeeded = parsed.maintenanceNeeded,
    )
}

/** The inverse projection: what of this observation the cache has to store. */
fun Metar.toSupplement(): MetarSupplement = MetarSupplement(
    // The merged category is stored, so a cache read reproduces it exactly
    // rather than re-deriving a possibly different one.
    flightRulesCode = flightRules.takeIf { it != FlightRules.UNKNOWN }?.code,
    reportKindCode = if (isSpeci) "SPECI" else null,
    observationEpochSeconds = observationEpochSeconds,
    temperatureC = temperatureC,
    dewpointC = dewpointC,
    seaLevelPressureHpa = seaLevelPressureHpa,
    hourlyPrecipInches = hourlyPrecipInches,
    precip3hInches = precip3hInches,
    precip6hInches = precip6hInches,
    precip24hInches = precip24hInches,
    snowDepthInches = snowDepthInches,
    latitude = latitude,
    longitude = longitude,
    elevationFt = elevationFt,
    stationName = stationName,
)
