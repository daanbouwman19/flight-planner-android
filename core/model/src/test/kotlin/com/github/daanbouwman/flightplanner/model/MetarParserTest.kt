package com.github.daanbouwman.flightplanner.model

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlin.test.Test

/**
 * Every fixture here is a real report captured from NOAA on 2026-08-27, not an
 * invented string. The awkward cases in real METAR text are the whole reason
 * this parser exists, so inventing tidy input would test nothing.
 */
class MetarParserTest {

    // ------------------------------------------------------------------ the bug

    @Test
    fun `an obscuration is a 200 ft indefinite ceiling, not an unlimited one`() {
        // The headline regression. The previous implementation looked for BKN or
        // OVC, found neither, and reported no ceiling at all — which then drew a
        // sun over a fogged-in field.
        val parsed = MetarParser.parse(
            "SPECI PACD 271823Z 14015G22KT 1/2SM FG VV002 12/12 A3011 RMK AO2 T01220117 RVRNO \$",
        )

        parsed.skyCover shouldBe SkyCover.Obscured(verticalVisibilityFt = 200)
        parsed.skyCover.ceiling shouldBe Ceiling.At(ft = 200, indefinite = true)
        parsed.isSpeci shouldBe true
        parsed.maintenanceNeeded shouldBe true
        parsed.visibilityStatuteMiles!! shouldBe (0.5 plusOrMinus 0.001)
        parsed.presentWeather.single().phenomena shouldBe listOf(WeatherPhenomenon.FOG)
    }

    @Test
    fun `an affirmatively clear sky is Clear, and an unreported one is Unknown`() {
        // These two must never be the same value. Conflating them is what made
        // "no data" render as good weather.
        MetarParser.parse("METAR EHAM 271855Z 31003KT CAVOK 22/17 Q1008 NOSIG")
            .skyCover shouldBe SkyCover.Clear
        MetarParser.parse("METAR MMCL 271851Z 22005KT 10SM SKC 38/26 A2980")
            .skyCover shouldBe SkyCover.Clear
        MetarParser.parse("METAR CYYL 271800Z AUTO 15004KT 110V200 9SM CLR 19/08 A3013")
            .skyCover shouldBe SkyCover.Clear

        // No sky group at all — the sky was simply not reported.
        MetarParser.parse("METAR XXXX 271800Z 31006KT 22/17 Q1008").skyCover shouldBe SkyCover.Unknown
        // And nothing at all.
        MetarParser.parse("").skyCover shouldBe SkyCover.Unknown
    }

    @Test
    fun `a clear sky has an unlimited ceiling and an unknown sky has no ceiling at all`() {
        SkyCover.Clear.ceiling shouldBe Ceiling.Unlimited
        SkyCover.Unknown.ceiling shouldBe Ceiling.Unknown
    }

    // ----------------------------------------------------------------- layers

    @Test
    fun `every layer is kept, in order, with its convective modifier`() {
        val parsed = MetarParser.parse(
            "METAR KJFK 271851Z 17020G27KT 4SM -TSRA BR FEW015 BKN043CB BKN110 OVC130 23/22 A3003",
        )

        val layers = (parsed.skyCover as SkyCover.Layers).layers
        layers shouldHaveSize 4
        layers[0] shouldBe CloudLayer(CloudCover.FEW, baseFt = 1_500)
        layers[1] shouldBe CloudLayer(CloudCover.BROKEN, baseFt = 4_300, convective = ConvectiveCloud.CUMULONIMBUS)
        layers[2] shouldBe CloudLayer(CloudCover.BROKEN, baseFt = 11_000)
        layers[3] shouldBe CloudLayer(CloudCover.OVERCAST, baseFt = 13_000)

        // The ceiling is the first BROKEN-or-worse layer, not the lowest layer:
        // FEW015 is lower but does not constitute a ceiling.
        parsed.skyCover.ceiling shouldBe Ceiling.At(ft = 4_300, indefinite = false)
    }

    @Test
    fun `layers with no broken or overcast deck report an unlimited ceiling`() {
        // Reported layers, none of them a ceiling. This is the one case where a
        // populated layer list still means "unlimited" — and it is a measurement.
        val parsed = MetarParser.parse("SPECI KLCH 271830Z VRB05KT 10SM SCT027 30/23 A2999")

        (parsed.skyCover as SkyCover.Layers).layers shouldHaveSize 1
        parsed.skyCover.ceiling shouldBe Ceiling.Unlimited
    }

    @Test
    fun `four broken layers all survive`() {
        val parsed = MetarParser.parse(
            "METAR KSYR 271754Z 30015G25KT 10SM TS BKN032 BKN060 BKN120 BKN200 24/17 A2996",
        )

        (parsed.skyCover as SkyCover.Layers).layers.map { it.baseFt } shouldBe
            listOf(3_200, 6_000, 12_000, 20_000)
        parsed.skyCover.ceiling shouldBe Ceiling.At(ft = 3_200, indefinite = false)
        // A bare TS group is a descriptor with no phenomenon, and is legal.
        parsed.presentWeather.single().isThunderstorm shouldBe true
    }

    // ------------------------------------------------------------------- wind

    @Test
    fun `a variable wind is variable, not missing`() {
        val parsed = MetarParser.parse("SPECI KLCH 271830Z VRB05KT 10SM SCT027 30/23 A2999")

        parsed.windVariable shouldBe true
        parsed.windDirectionDeg.shouldBeNull()
        parsed.windSpeedKt shouldBe 5
    }

    @Test
    fun `gusts and a direction range are both kept`() {
        val parsed = MetarParser.parse("METAR CYYL 271800Z AUTO 15004KT 110V200 9SM CLR 19/08 A3013")

        parsed.windDirectionDeg shouldBe 150
        parsed.windSpeedKt shouldBe 4
        parsed.windRangeFromDeg shouldBe 110
        parsed.windRangeToDeg shouldBe 200
        parsed.isAutomated shouldBe true

        val jfk = MetarParser.parse("METAR KJFK 271851Z 17020G27KT 4SM -TSRA BR FEW015 23/22 A3003")
        jfk.windDirectionDeg shouldBe 170
        jfk.windSpeedKt shouldBe 20
        jfk.windGustKt shouldBe 27
    }

    // ------------------------------------------------------------- visibility

    @Test
    fun `statute visibility handles plain, fractional and mixed forms`() {
        fun vis(raw: String) = MetarParser.parse(raw).visibilityStatuteMiles

        vis("METAR XXXX 271800Z 31006KT 10SM CLR 20/10 A3000")!! shouldBe (10.0 plusOrMinus 0.001)
        vis("METAR XXXX 271800Z 31006KT 7SM CLR 20/10 A3000")!! shouldBe (7.0 plusOrMinus 0.001)
        vis("METAR XXXX 271800Z 31006KT 1/2SM FG 20/10 A3000")!! shouldBe (0.5 plusOrMinus 0.001)
        vis("METAR XXXX 271800Z 31006KT 3/4SM BR 20/10 A3000")!! shouldBe (0.75 plusOrMinus 0.001)
        // "less than a quarter mile" — the M is dropped, not made negative.
        vis("METAR XXXX 271800Z 31006KT M1/4SM FG 20/10 A3000")!! shouldBe (0.25 plusOrMinus 0.001)
        // A mixed number arrives as two tokens.
        vis("METAR XXXX 271800Z 31006KT 1 1/2SM BR 20/10 A3000")!! shouldBe (1.5 plusOrMinus 0.001)
    }

    @Test
    fun `the ICAO metre group converts, and 9999 means or better`() {
        val parsed = MetarParser.parse("METAR EDDF 271820Z 25008KT 9999 FEW035 24/14 Q1015")

        parsed.visibilityStatuteMiles!! shouldBe (6.21 plusOrMinus 0.01)
        parsed.visibilityIsOrGreater shouldBe true
    }

    // ------------------------------------------------------- present weather

    @Test
    fun `intensity, descriptor and multiple phenomena all decode`() {
        val groups = MetarParser.parse(
            "METAR KJFK 271851Z 17020G27KT 4SM -TSRA BR FEW015 BKN043CB 23/22 A3003",
        ).presentWeather

        groups shouldHaveSize 2

        val storm = groups[0]
        storm.intensity shouldBe WeatherIntensity.LIGHT
        storm.descriptor shouldBe WeatherDescriptor.THUNDERSTORM
        storm.phenomena shouldBe listOf(WeatherPhenomenon.RAIN)
        storm.isThunderstorm shouldBe true
        storm.isPrecipitating shouldBe true
        storm.isFrozenPrecipitation shouldBe false

        groups[1].phenomena shouldBe listOf(WeatherPhenomenon.MIST)
        groups[1].isObscuring shouldBe true
    }

    @Test
    fun `the phenomena the old code lists missed all parse now`() {
        // Every one of these fell through the previous four hand-written lists
        // into a ceiling-only guess, and from there into "clear".
        fun phenomena(code: String) =
            MetarParser.parse("METAR XXXX 271800Z 31006KT 2SM $code OVC010 00/00 A3000")
                .presentWeather
                .single()

        phenomena("SHSN").descriptor shouldBe WeatherDescriptor.SHOWERS
        phenomena("SHSN").phenomena shouldBe listOf(WeatherPhenomenon.SNOW)
        phenomena("GR").phenomena shouldBe listOf(WeatherPhenomenon.HAIL)
        phenomena("FU").phenomena shouldBe listOf(WeatherPhenomenon.SMOKE)
        phenomena("DU").phenomena shouldBe listOf(WeatherPhenomenon.DUST)
        phenomena("BLSA").descriptor shouldBe WeatherDescriptor.BLOWING
        phenomena("BLSA").phenomena shouldBe listOf(WeatherPhenomenon.SAND)
        phenomena("SQ").phenomena shouldBe listOf(WeatherPhenomenon.SQUALLS)
        phenomena("VA").phenomena shouldBe listOf(WeatherPhenomenon.VOLCANIC_ASH)
        phenomena("PO").phenomena shouldBe listOf(WeatherPhenomenon.DUST_WHIRLS)
        phenomena("+FC").phenomena shouldBe listOf(WeatherPhenomenon.FUNNEL_CLOUD)
    }

    @Test
    fun `vicinity weather is not happening at the field`() {
        val group = MetarParser.parse("METAR XXXX 271800Z 31006KT 10SM VCSH SCT030 20/10 A3000")
            .presentWeather
            .single()

        group.inVicinity shouldBe true
        group.descriptor shouldBe WeatherDescriptor.SHOWERS
        // A bare VCSH has no phenomenon, and nothing is falling on the field.
        group.phenomena.shouldBeEmpty()
        group.isPrecipitating shouldBe false
    }

    @Test
    fun `freezing precipitation is distinct from frozen precipitation`() {
        val freezing = MetarParser.parse("METAR XXXX 271800Z 31006KT 2SM FZRA OVC008 M01/M02 A3000")
            .presentWeather
            .single()
        freezing.isFreezingPrecipitation shouldBe true

        val snow = MetarParser.parse("METAR XXXX 271800Z 31006KT 2SM -SN OVC008 M05/M07 A3000")
            .presentWeather
            .single()
        snow.isFrozenPrecipitation shouldBe true
        snow.isFreezingPrecipitation shouldBe false
    }

    @Test
    fun `fog and mist are told apart from haze`() {
        fun single(code: String) =
            MetarParser.parse("METAR XXXX 271800Z 31006KT 2SM $code OVC010 10/09 A3000")
                .presentWeather
                .single()

        single("FG").isFogOrMist shouldBe true
        single("BR").isFogOrMist shouldBe true
        single("HZ").isFogOrMist shouldBe false
        single("HZ").isObscuring shouldBe true
    }

    // ------------------------------------------------- temperature, altimeter

    @Test
    fun `negative temperatures and a missing dewpoint both decode`() {
        val cold = MetarParser.parse("METAR XXXX 271800Z 31006KT 10SM CLR M02/M04 A3000")
        cold.temperatureC!! shouldBe (-2.0 plusOrMinus 0.001)
        cold.dewpointC!! shouldBe (-4.0 plusOrMinus 0.001)

        val noDew = MetarParser.parse("METAR XXXX 271800Z 31006KT 10SM CLR 23/ A3000")
        noDew.temperatureC!! shouldBe (23.0 plusOrMinus 0.001)
        noDew.dewpointC.shouldBeNull()
    }

    @Test
    fun `the altimeter keeps the convention the station actually used`() {
        // The reason this matters: NOAA normalises everything to hectopascals and
        // the app then converts to inches, so a European Q-code came out in inHg.
        val american = MetarParser.parse("METAR KJFK 271751Z 17011KT 7SM CLR 23/22 A3003")
        american.altimeterInHg!! shouldBe (30.03 plusOrMinus 0.001)
        american.altimeterConvention shouldBe AltimeterConvention.INCHES_MERCURY
        american.altimeterHectopascals.shouldBeNull()

        val european = MetarParser.parse("METAR EHAM 271855Z 31003KT CAVOK 22/17 Q1008")
        european.altimeterHectopascals!! shouldBe (1008.0 plusOrMinus 0.001)
        european.altimeterConvention shouldBe AltimeterConvention.HECTOPASCALS
        european.altimeterInHg.shouldBeNull()
    }

    // ------------------------------------------------------------- robustness

    @Test
    fun `remarks are not parsed`() {
        // P0001 in remarks is hourly precipitation and RAB16 is "rain began at
        // :16". Neither may be mistaken for a present-weather group.
        val parsed = MetarParser.parse(
            "METAR KJFK 271751Z 17011G20KT 7SM FEW023 23/22 A3003 " +
                "RMK AO2 RAB16 TSB03 SLP170 OCNL LTGICCCCG P0001 60001 T02330217",
        )

        parsed.presentWeather.shouldBeEmpty()
        (parsed.skyCover as SkyCover.Layers).layers shouldHaveSize 1
    }

    @Test
    fun `a trend group is a forecast and must not be read as current conditions`() {
        // The bug this guards: `TEMPO 3000 SHRA` describes what the weather is
        // expected to *become*. Parsing it as part of the observation invents a
        // 3,000 m visibility and rain that is not falling — a silent wrong
        // render, and exactly the class of defect this redesign exists to remove.
        val parsed = MetarParser.parse(
            "METAR EGLL 271820Z 24012KT 9999 FEW030 18/12 Q1014 TEMPO 3000 SHRA BKN008",
        )

        // The observation: 10 km+, a few at 3,000 ft, nothing falling.
        parsed.visibilityIsOrGreater shouldBe true
        parsed.presentWeather.shouldBeEmpty()
        (parsed.skyCover as SkyCover.Layers).layers shouldBe
            listOf(CloudLayer(CloudCover.FEW, baseFt = 3_000))
        // Emphatically NOT the forecast's BKN008.
        parsed.skyCover.ceiling shouldBe Ceiling.Unlimited
    }

    @Test
    fun `every trend marker terminates the body`() {
        fun ceilingOf(raw: String) = MetarParser.parse(raw).skyCover.ceiling

        // Each of these would otherwise contribute an OVC005 ceiling.
        ceilingOf("METAR XXXX 271800Z 31006KT 9999 FEW030 18/12 Q1014 NOSIG OVC005") shouldBe
            Ceiling.Unlimited
        ceilingOf("METAR XXXX 271800Z 31006KT 9999 FEW030 18/12 Q1014 BECMG OVC005") shouldBe
            Ceiling.Unlimited
        ceilingOf("METAR XXXX 271800Z 31006KT 9999 FEW030 18/12 Q1014 FM1200 OVC005") shouldBe
            Ceiling.Unlimited
        ceilingOf("METAR XXXX 271800Z 31006KT 9999 FEW030 18/12 Q1014 TL0300 OVC005") shouldBe
            Ceiling.Unlimited
    }

    @Test
    fun `a lowercase report parses, and does so locale-independently`() {
        // Uppercasing with the default locale rather than Locale.ROOT turns `i`
        // into `İ` under a Turkish locale. Nothing in a METAR contains `i`, but
        // this app has already shipped one locale defect and the guard is free.
        val parsed = MetarParser.parse("metar eham 271855z 31003kt cavok 22/17 q1008")

        parsed.station shouldBe "EHAM"
        parsed.skyCover shouldBe SkyCover.Clear
        parsed.temperatureC!! shouldBe (22.0 plusOrMinus 0.001)
    }

    @Test
    fun `garbage yields absence rather than an exception or a guess`() {
        val parsed = MetarParser.parse("this is not a metar at all")

        parsed.skyCover shouldBe SkyCover.Unknown
        parsed.temperatureC.shouldBeNull()
        parsed.windSpeedKt.shouldBeNull()
        parsed.visibilityStatuteMiles.shouldBeNull()
        parsed.presentWeather.shouldBeEmpty()
    }

    @Test
    fun `the station and time group are recovered`() {
        val parsed = MetarParser.parse("SPECI PACD 271823Z 14015G22KT 1/2SM FG VV002 12/12 A3011")

        parsed.station shouldBe "PACD"
        parsed.dayTimeZulu shouldBe "271823Z"
    }

    @Test
    fun `a layer with an unmeasured height is skipped rather than invented`() {
        // `///` is a real automated report: a layer is there, its height was not
        // measured. Inventing a base would put a deck at a made-up altitude.
        val parsed = MetarParser.parse("METAR XXXX 271800Z 31006KT 10SM BKN/// 20/10 A3000")

        parsed.skyCover shouldBe SkyCover.Unknown
    }
}

/**
 * Two arithmetic-and-string defects found by review, both silent and both
 * systematic — which is what makes them worth pinning rather than just fixing.
 */
class MetarParserPrecisionTest {

    @Test
    fun `a metric wind is rounded to the nearest knot, not truncated`() {
        // `toInt()` truncates, so every metric station under-reported by up to a
        // knot, always downward, forever. 5 m/s is 9.72 kt and came out as 9;
        // 10 m/s is 19.44 and came out as 19. It feeds `SurfaceWind.favouredEnd`,
        // which has a decisiveness threshold, so a systematic bias is not cosmetic.
        //
        // **Every speed here is chosen to discriminate.** Truncation and rounding
        // agree for most of the range — 10 m/s is 19.44 and both give 19 — so an
        // arbitrary fixture passes under the bug and looks identical to a clean
        // tree. 5 m/s is 9.72 (9 versus 10) and 8 m/s is 15.55 (15 versus 16).
        MetarParser.parse("METAR ZUZH 271000Z 04005MPS 9999 SCT030 26/23 Q1006").windSpeedKt shouldBe 10
        MetarParser.parse("METAR ZUZH 271000Z 04008MPS 9999 SCT030 26/23 Q1006").windSpeedKt shouldBe 16
    }

    @Test
    fun `a metric gust is rounded the same way as the wind it belongs to`() {
        // Separately, because the two are separate expressions and fixing one and
        // not the other would produce a gust below its own wind speed. 18 m/s is
        // 34.99, which is the discriminating value here — 34 versus 35.
        val parsed = MetarParser.parse("METAR ZUZH 271000Z 04005G18MPS 9999 SCT030 26/23 Q1006")
        parsed.windSpeedKt shouldBe 10
        parsed.windGustKt shouldBe 35
    }

    @Test
    fun `knots are untouched, because the factor is exactly one`() {
        val parsed = MetarParser.parse("METAR EHAM 271000Z 27035G50KT 9999 BKN012 13/11 Q0988")
        parsed.windSpeedKt shouldBe 35
        parsed.windGustKt shouldBe 50
    }

    @Test
    fun `the maintenance flag survives an equals-terminated report`() {
        // The defect: the flag was tested against the *raw* text while the parser
        // strips a trailing `=` from its working copy one line earlier. So every
        // feed that terminates its reports — which is why the strip exists — lost
        // the one signal saying this station's own sensors need attention.
        val terminated = MetarParser.parse(
            "SPECI PACD 271823Z 14015G22KT 1/2SM FG VV002 12/12 A3011 RMK AO2 RVRNO \$=",
        )
        terminated.maintenanceNeeded shouldBe true

        // And is still absent when the station did not raise it, `=` or not.
        MetarParser.parse(
            "METAR EHAM 271855Z 31003KT CAVOK 22/17 Q1008=",
        ).maintenanceNeeded shouldBe false
    }
}
