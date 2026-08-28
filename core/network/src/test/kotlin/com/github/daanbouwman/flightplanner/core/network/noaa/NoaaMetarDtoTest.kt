package com.github.daanbouwman.flightplanner.core.network.noaa

import com.github.daanbouwman.flightplanner.model.Ceiling
import com.github.daanbouwman.flightplanner.model.CloudCover
import com.github.daanbouwman.flightplanner.model.ConvectiveCloud
import com.github.daanbouwman.flightplanner.model.FlightRules
import com.github.daanbouwman.flightplanner.model.SkyCover
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test

/**
 * The DTO's job is now narrow: pull the facts a METAR text does not carry and
 * hand the text itself to the parser. These tests cover that boundary — the
 * supplement extraction, the unit conversion, and the tolerant accessors.
 *
 * Cloud, wind, visibility and present-weather decoding is `MetarParserTest`'s
 * business, because the DTO no longer reads NOAA's versions of them.
 */
class NoaaMetarDtoTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun parse(body: String) =
        (json.parseToJsonElement(body) as JsonObject).toMetar()

    /** The real KJFK object, verbatim from NOAA on 2026-08-27. */
    private val kjfk = """
        {"icaoId":"KJFK","receiptTime":"2026-08-27T17:54:10.606Z","obsTime":1787853060,
         "reportTime":"2026-08-27T18:00:00.000Z","temp":23.3,"dewp":21.7,"wdir":170,
         "wspd":11,"wgst":20,"visib":7,"altim":1017,"slp":1017,"qcField":4,
         "wxString":"-TSRA","presTend":-0.5,"maxT":26.1,"minT":23.3,"precip":0.01,
         "pcp6hr":0.01,"metarType":"METAR",
         "rawOb":"METAR KJFK 271751Z 17011G20KT 7SM -TSRA FEW023 BKN043CB OVC110 23/22 A3003 RMK AO2 SLP170",
         "lat":40.6392,"lon":-73.7639,"elev":3,"name":"New York/JF Kennedy Intl, NY, US",
         "cover":"OVC","clouds":[{"cover":"FEW","base":2300},{"cover":"BKN","base":4300},
         {"cover":"OVC","base":11000}],"fltCat":"VFR"}
    """.trimIndent()

    @Test
    fun `elev is metres on the wire and feet in the model`() {
        // The unit trap: NOAA sends `elev` in metres, Airport.elevationFt is
        // feet, and mixing them silently misplaces every field by a factor of
        // three. Converted once, here. 3 m x 3.28084 = 9.84 -> 10 ft.
        parse(kjfk).elevationFt shouldBe 10
    }

    @Test
    fun `the provider-only facts are carried across`() {
        val metar = parse(kjfk)

        metar.latitude!! shouldBe (40.6392 plusOrMinus 0.0001)
        metar.longitude!! shouldBe (-73.7639 plusOrMinus 0.0001)
        metar.stationName shouldBe "New York/JF Kennedy Intl, NY, US"
        metar.observationEpochSeconds shouldBe 1_787_853_060L
        metar.hourlyPrecipInches!! shouldBe (0.01 plusOrMinus 0.0001)
        metar.precip6hInches!! shouldBe (0.01 plusOrMinus 0.0001)
        metar.precip3hInches.shouldBeNull()
        metar.seaLevelPressureHpa!! shouldBe (1017.0 plusOrMinus 0.001)
        metar.flightRules shouldBe FlightRules.VFR
    }

    @Test
    fun `temperature comes from the provider's tenths, not the raw whole degrees`() {
        // AWC reads the remarks `Tddddd` group, so it has 23.3/21.7 where the
        // raw body says `23/22`. The dewpoint spread drives the frost inference,
        // so the precision matters here specifically.
        val metar = parse(kjfk)
        metar.temperatureC!! shouldBe (23.3 plusOrMinus 0.001)
        metar.dewpointC!! shouldBe (21.7 plusOrMinus 0.001)
    }

    @Test
    fun `the sky comes from the raw text, keeping what NOAA's clouds array drops`() {
        // NOAA's own `clouds[]` for this station reports the middle layer as
        // {"cover":"BKN","base":4300} — the CB is gone. The raw text has
        // BKN043CB, and that is the difference between a broken deck and a
        // broken deck with thunderstorms in it.
        val layers = (parse(kjfk).skyCover as SkyCover.Layers).layers

        layers.size shouldBe 3
        layers[1].cover shouldBe CloudCover.BROKEN
        layers[1].baseFt shouldBe 4_300
        layers[1].convective shouldBe ConvectiveCloud.CUMULONIMBUS
    }

    @Test
    fun `reportTime is never parsed`() {
        // It has at least three live formats across one response. Only `obsTime`
        // is unambiguous. Feeding garbage must change nothing.
        val withGarbage = kjfk.replace(
            "\"reportTime\":\"2026-08-27T18:00:00.000Z\"",
            "\"reportTime\":\"not a date at all\"",
        )
        parse(withGarbage).observationEpochSeconds shouldBe 1_787_853_060L
    }

    @Test
    fun `an obscuration yields a real ceiling rather than an unlimited one`() {
        // The headline regression, at the network boundary. NOAA normalises a
        // raw VV002 to cover "OVX", which the old ceiling lookup did not match —
        // so a fogged-in field at 200 ft reported no ceiling at all.
        val pacd = """
            {"icaoId":"PACD","obsTime":1787851380,"visib":0.5,"wxString":"FG","vertVis":2,
             "cover":"OVX","clouds":[{"cover":"OVX","base":200}],"fltCat":"LIFR",
             "rawOb":"SPECI PACD 271823Z 14015G22KT 1/2SM FG VV002 12/12 A3011 RMK AO2 ${'$'}"}
        """.trimIndent()

        val metar = parse(pacd)
        metar.skyCover shouldBe SkyCover.Obscured(verticalVisibilityFt = 200)
        metar.ceiling shouldBe Ceiling.At(ft = 200, indefinite = true)
        metar.flightRules shouldBe FlightRules.LIFR
        metar.maintenanceNeeded shouldBe true
    }

    @Test
    fun `an affirmatively clear sky and an unreported one are different`() {
        // Both have "clouds": [] in NOAA's JSON. The raw text is what separates
        // them, and conflating them is the defect this redesign removes.
        val clear = """
            {"icaoId":"EHAM","cover":"CAVOK","clouds":[],"fltCat":"VFR",
             "rawOb":"METAR EHAM 271855Z 31003KT CAVOK 22/17 Q1008 NOSIG"}
        """.trimIndent()
        parse(clear).skyCover shouldBe SkyCover.Clear

        val unreported = """
            {"icaoId":"XXXX","clouds":[],
             "rawOb":"METAR XXXX 271800Z 31006KT 10SM 22/17 Q1008"}
        """.trimIndent()
        val metar = parse(unreported)
        metar.skyCover shouldBe SkyCover.Unknown
        metar.skyUnknown shouldBe true
        // Ten miles of visibility does NOT make this VFR — the sky is unknown.
        metar.flightRules shouldBe FlightRules.UNKNOWN
    }

    @Test
    fun `a report with no raw text has an unknown sky, even when NOAA decoded one`() {
        // A deliberate decision, pinned here so it is not "fixed" later.
        //
        // NOAA's `cover` and `clouds[]` are its decode of the same `rawOb`, and
        // consuming them as a fallback would mean two decoders for one string —
        // which is what produced the defect this redesign exists to remove. It
        // would also be a value the cache cannot store (the cache keeps `raw`
        // plus the supplement, nothing else), so a cache read would revert it and
        // reintroduce exactly the flicker the design prevents.
        //
        // The cost is bounded: a report with no raw text is not cacheable either
        // way, and "unknown" is the honest answer for a report with no text in
        // it. `NoaaCloudsCrossCheckTest` is where the two decodes are compared,
        // at test time, where a divergence is information.
        val noRaw = """{"icaoId":"XXXX","cover":"CLR","clouds":[],"rawOb":""}"""
        parse(noRaw).skyCover shouldBe SkyCover.Unknown

        val noRawLayers = """
            {"icaoId":"XXXX","cover":"BKN","clouds":[{"cover":"BKN","base":900}],"rawOb":""}
        """.trimIndent()
        parse(noRawLayers).ceiling shouldBe Ceiling.Unknown
    }

    @Test
    fun `a junk provider category falls through to local derivation`() {
        val bogus = kjfk.replace("\"fltCat\":\"VFR\"", "\"fltCat\":\"nonsense\"")
        // 7 SM against a 4,300 ft ceiling: both VFR.
        parse(bogus).flightRules shouldBe FlightRules.VFR
    }

    @Test
    fun `the accessors tolerate a wrong JSON type`() {
        // `visib` genuinely arrives as Int, Double and String across stations in
        // one response, and a numeric field quoted as a string is a shape NOAA
        // has shipped. A wrong type must read as absent, never throw.
        val quoted = """
            {"icaoId":"XXXX","temp":"18.5","obsTime":"1787853060","elev":"100",
             "rawOb":"METAR XXXX 271800Z 31006KT 10SM CLR 22/17 Q1008"}
        """.trimIndent()

        val metar = parse(quoted)
        metar.temperatureC!! shouldBe (18.5 plusOrMinus 0.001)
        metar.observationEpochSeconds shouldBe 1_787_853_060L
        metar.elevationFt shouldBe 328
    }

    @Test
    fun `a JSON null and an absent key both read as absent`() {
        val nulls = """
            {"icaoId":"XXXX","temp":null,"snow":null,"name":null,
             "rawOb":"METAR XXXX 271800Z 31006KT 10SM CLR 22/17 Q1008"}
        """.trimIndent()

        val metar = parse(nulls)
        metar.snowDepthInches.shouldBeNull()
        metar.stationName.shouldBeNull()
        // Temperature falls back to the raw body's whole degrees.
        metar.temperatureC!! shouldBe (22.0 plusOrMinus 0.001)
    }

    @Test
    fun `a missing icaoId throws so the client can drop that element`() {
        val result = runCatching { parse("""{"rawOb":"METAR XXXX 271800Z"}""") }
        result.isFailure shouldBe true
    }
}
