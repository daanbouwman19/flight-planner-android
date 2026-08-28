package com.github.daanbouwman.flightplanner.model

import io.kotest.matchers.shouldBe
import kotlin.test.Test

class GroundConditionTest {

    private fun weather(raw: String): List<PresentWeather> =
        MetarParser.parse("METAR XXXX 271800Z 31006KT 2SM $raw OVC010 00/00 A3000").presentWeather

    private fun derive(
        temperatureC: Double? = 15.0,
        dewpointC: Double? = 5.0,
        presentWeather: List<PresentWeather> = emptyList(),
        hourlyPrecipInches: Double? = null,
        snowDepthInches: Double? = null,
    ) = GroundConditions.derive(
        temperatureC = temperatureC,
        dewpointC = dewpointC,
        presentWeather = presentWeather,
        hourlyPrecipInches = hourlyPrecipInches,
        snowDepthInches = snowDepthInches,
    )

    @Test
    fun `nothing reported at all is Unknown, not Dry`() {
        // The distinction the whole redesign turns on: absence of information is
        // never rendered as a fine day.
        GroundConditions.derive(
            temperatureC = null,
            dewpointC = null,
            presentWeather = emptyList(),
        ) shouldBe GroundCondition.Unknown
    }

    @Test
    fun `a measured snow depth outranks everything else`() {
        // The only direct observation of the surface anywhere in this feed.
        derive(snowDepthInches = 3.5) shouldBe GroundCondition.Snow(depthInches = 3.5)
        // Even with rain falling on top of it.
        derive(
            temperatureC = 1.0,
            presentWeather = weather("RA"),
            snowDepthInches = 2.0,
        ) shouldBe GroundCondition.Snow(depthInches = 2.0)
    }

    @Test
    fun `a zero snow depth is not snow`() {
        // A reported depth of zero is a measurement that there is no snow, which
        // must not become Snow(0.0).
        derive(snowDepthInches = 0.0) shouldBe GroundCondition.Dry
    }

    @Test
    fun `freezing precipitation outranks frozen, which outranks liquid`() {
        // Worst-and-most-specific first: glaze ice is the most consequential
        // thing this feed can report about a surface.
        derive(temperatureC = -1.0, presentWeather = weather("FZRA")) shouldBe GroundCondition.Icy
        derive(temperatureC = -5.0, presentWeather = weather("-SN")) shouldBe GroundCondition.Snow()
        derive(temperatureC = 12.0, presentWeather = weather("-RA")) shouldBe GroundCondition.Wet
    }

    @Test
    fun `every frozen phenomenon lands on Snow`() {
        listOf("SN", "SG", "PL", "IC", "GS", "GR", "SHSN").forEach { code ->
            derive(temperatureC = -2.0, presentWeather = weather(code)) shouldBe GroundCondition.Snow()
        }
    }

    @Test
    fun `vicinity showers do not wet the ground`() {
        // VCSH is weather near the field, not at it. Nothing is falling here.
        derive(temperatureC = 18.0, presentWeather = weather("VCSH")) shouldBe GroundCondition.Dry
    }

    @Test
    fun `an obscuration alone leaves the ground dry`() {
        // Haze, smoke and mist are suspended in the air; they do not wet a runway.
        listOf("HZ", "FU", "BR", "DU").forEach { code ->
            derive(temperatureC = 18.0, dewpointC = 17.0, presentWeather = weather(code)) shouldBe
                GroundCondition.Dry
        }
    }

    @Test
    fun `recent measured precipitation leaves the surface wet with nothing falling now`() {
        derive(temperatureC = 12.0, hourlyPrecipInches = 0.01) shouldBe GroundCondition.Wet
        // The same total below freezing is snow rather than water.
        derive(temperatureC = -3.0, dewpointC = -20.0, hourlyPrecipInches = 0.02) shouldBe
            GroundCondition.Snow()
        // Zero is a measurement of no precipitation.
        derive(temperatureC = 12.0, hourlyPrecipInches = 0.0) shouldBe GroundCondition.Dry
    }

    @Test
    fun `frost needs both a cold surface and moisture to deposit`() {
        // Below freezing and near-saturated: frost is plausible.
        derive(temperatureC = -2.0, dewpointC = -3.0) shouldBe GroundCondition.Frost
        derive(temperatureC = 0.0, dewpointC = 0.0) shouldBe GroundCondition.Frost

        // Below freezing but bone dry — a 15-degree spread. Calling this frosted
        // would be the inference over-reaching, which is the failure mode a guess
        // presented next to measurements must avoid.
        derive(temperatureC = -20.0, dewpointC = -35.0) shouldBe GroundCondition.Dry

        // Just outside the spread.
        derive(temperatureC = -1.0, dewpointC = -5.0) shouldBe GroundCondition.Dry
    }

    @Test
    fun `above freezing with nothing falling is Dry`() {
        derive(temperatureC = 22.0, dewpointC = 21.0) shouldBe GroundCondition.Dry
    }

    @Test
    fun `present weather that misses the ground with no temperature is Unknown`() {
        // Haze was reported, so the report is not empty — but nothing in it says
        // anything about the surface and there is no temperature to reason from.
        GroundConditions.derive(
            temperatureC = null,
            dewpointC = null,
            presentWeather = weather("HZ"),
        ) shouldBe GroundCondition.Unknown
    }

    @Test
    fun `the real fixtures derive the conditions you would expect`() {
        // PACD, fogged in at 12/12 — saturated, above freezing, nothing falling.
        val pacd = MetarParser.parse("SPECI PACD 271823Z 14015G22KT 1/2SM FG VV002 12/12 A3011")
        pacd.groundCondition() shouldBe GroundCondition.Dry

        // KJFK in a thunderstorm with rain.
        val kjfk = MetarParser.parse(
            "METAR KJFK 271851Z 17020G27KT 4SM -TSRA BR FEW015 BKN043CB 23/22 A3003",
        )
        kjfk.groundCondition() shouldBe GroundCondition.Wet

        // EHAM, CAVOK, warm and dry-ish.
        val eham = MetarParser.parse("METAR EHAM 271855Z 31003KT CAVOK 22/17 Q1008")
        eham.groundCondition() shouldBe GroundCondition.Dry
    }
}
