package com.github.daanbouwman.flightplanner.model

import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlin.test.Test

/** Every fixture is a real report captured from NOAA on 2026-08-27. */
private const val KJFK = "METAR KJFK 271851Z 17020G27KT 4SM -TSRA BR " +
    "FEW015 BKN043CB BKN110 OVC130 23/22 A3003 RMK AO2 SLP170 P0001"

private fun fullSupplement() = MetarSupplement(
    flightRulesCode = "MVFR",
    reportKindCode = "METAR",
    observationEpochSeconds = 1_787_853_060L,
    // Deliberately different from the raw body's `23/22`, to prove tenths win.
    temperatureC = 23.3,
    dewpointC = 21.7,
    seaLevelPressureHpa = 1017.0,
    hourlyPrecipInches = 0.01,
    precip3hInches = 0.02,
    precip6hInches = 0.03,
    precip24hInches = 0.04,
    snowDepthInches = 1.5,
    latitude = 40.6392,
    longitude = -73.7639,
    elevationFt = 10,
    stationName = "New York/JF Kennedy Intl, NY, US",
)

class MetarSupplementTest {

    @Test
    fun `a cache round trip is the identity`() {
        // The property the whole cache design rests on:
        //   buildMetar(s, r, buildMetar(s, r, sup).toSupplement()) == buildMetar(s, r, sup)
        // Storing the merged observation and re-merging yields the merged
        // observation, so a station read back from the cache is byte-identical to
        // the one that was written. That is what makes it structurally
        // impossible for cloud layers to "flicker" between two visits.
        val fresh = buildMetar("KJFK", KJFK, fullSupplement())
        val fromCache = buildMetar("KJFK", KJFK, fresh.toSupplement())

        fromCache shouldBe fresh
    }

    @Test
    fun `the round trip holds with no provider metadata at all`() {
        // The AVWX path, and a bare raw string.
        val fresh = buildMetar("PACD", "SPECI PACD 271823Z 14015G22KT 1/2SM FG VV002 12/12 A3011", MetarSupplement.None)
        buildMetar("PACD", fresh.raw, fresh.toSupplement()) shouldBe fresh
    }

    @Test
    fun `cloud layers survive a cache round trip, because they are never stored`() {
        // Under the previous design these were a hand-maintained set of columns
        // and were the first thing a forgotten field would drop. Now they come
        // back out of `raw`, so there is nothing to forget.
        val fromCache = buildMetar("KJFK", KJFK, buildMetar("KJFK", KJFK, fullSupplement()).toSupplement())

        val layers = (fromCache.skyCover as SkyCover.Layers).layers
        layers.size shouldBe 4
        layers[1] shouldBe CloudLayer(CloudCover.BROKEN, baseFt = 4_300, convective = ConvectiveCloud.CUMULONIMBUS)
        fromCache.ceiling shouldBe Ceiling.At(ft = 4_300, indefinite = false)
        fromCache.windGustKt shouldBe 27
        fromCache.presentWeather.size shouldBe 2
    }

    @Test
    fun `the provider category wins over local derivation`() {
        val metar = buildMetar("KJFK", KJFK, MetarSupplement.None.copy(flightRulesCode = "IFR"))
        metar.flightRules shouldBe FlightRules.IFR
    }

    @Test
    fun `an unrecognised provider category falls through to derivation, not to unknown`() {
        // A junk `fltCat` must not poison the result — the local derivation is a
        // perfectly good answer and the raw text supports it.
        val metar = buildMetar("KJFK", KJFK, MetarSupplement.None.copy(flightRulesCode = "bogus"))

        // 4 SM visibility against a 4,300 ft ceiling: the ceiling is VFR, the
        // visibility is in the 3–5 SM band, and the worse of the two decides.
        // Worth noting as a cross-check: NOAA's own `fltCat` for this exact
        // report is also MVFR, so the local derivation agrees with the official
        // categorisation here.
        metar.flightRules shouldBe FlightRules.MVFR
    }

    @Test
    fun `an unreported sky takes the category away from the provider`() {
        // Defect 2 in docs/WEATHER-PLAN.md, and it is the original bug's shape:
        // ZUZH's cloud sensor returned nothing, NOAA computed VFR from that same
        // missing sky, and the chip said VFR beside a scene hatched to say the
        // sky is unknown. A reassurance drawn from an absence.
        val zuzh = "METAR ZUZH 241300Z 04002MPS 9999 // ////// 26/23 Q1006 NOSIG"
        val metar = buildMetar("ZUZH", zuzh, MetarSupplement.None.copy(flightRulesCode = "VFR"))

        metar.skyCover shouldBe SkyCover.Unknown
        metar.flightRules shouldBe FlightRules.UNKNOWN
    }

    @Test
    fun `the veto suppresses a false reassurance, not a real warning`() {
        // Why the fix is a veto on the provider rather than a blanket UNKNOWN,
        // which was the obvious version and is a worse one. With no cloud group
        // at all the visibility can still force the worst category on its own,
        // and blanking that would trade a false reassurance for a suppressed
        // warning — the same trade in the other direction.
        //
        // The guarantee is `FlightRules.derive`'s, not this function's: against
        // `Ceiling.Unknown` it can only answer LIFR or UNKNOWN, so there is no
        // report for which the chip can call the weather fine over a sky nobody
        // measured.
        val fogged = "METAR ZZZZ 271823Z 14015KT 1/4SM FG 12/12 A3011"
        val metar = buildMetar("ZZZZ", fogged, MetarSupplement.None.copy(flightRulesCode = "VFR"))

        metar.skyCover shouldBe SkyCover.Unknown
        metar.flightRules shouldBe FlightRules.LIFR
    }

    @Test
    fun `provider tenths beat the raw body's whole degrees`() {
        // The one field where a provider's decode is better than the raw text:
        // AWC reads the remarks `Tddddd` group.
        val withTenths = buildMetar("KJFK", KJFK, fullSupplement())
        withTenths.temperatureC!! shouldBe (23.3 plusOrMinus 0.001)
        withTenths.dewpointC!! shouldBe (21.7 plusOrMinus 0.001)

        // With no supplement, the raw body's whole degrees stand.
        val rawOnly = buildMetar("KJFK", KJFK, MetarSupplement.None)
        rawOnly.temperatureC!! shouldBe (23.0 plusOrMinus 0.001)
        rawOnly.dewpointC!! shouldBe (22.0 plusOrMinus 0.001)
    }

    @Test
    fun `provider-only facts are carried, and absent when not supplied`() {
        val supplied = buildMetar("KJFK", KJFK, fullSupplement())
        supplied.latitude!! shouldBe (40.6392 plusOrMinus 0.0001)
        supplied.elevationFt shouldBe 10
        supplied.snowDepthInches!! shouldBe (1.5 plusOrMinus 0.001)
        supplied.observationEpochSeconds shouldBe 1_787_853_060L
        supplied.stationName shouldBe "New York/JF Kennedy Intl, NY, US"

        val bare = buildMetar("KJFK", KJFK, MetarSupplement.None)
        bare.latitude.shouldBeNull()
        bare.elevationFt.shouldBeNull()
        bare.snowDepthInches.shouldBeNull()
        bare.observationEpochSeconds.shouldBeNull()
    }

    @Test
    fun `the station falls back to the requested code when the raw text has none`() {
        val metar = buildMetar("EHAM", "271855Z 31003KT CAVOK 22/17 Q1008", MetarSupplement.None)
        metar.station shouldBe "EHAM"
    }

    @Test
    fun `unknown builds an observation that claims nothing`() {
        val metar = Metar.unknown("ZZZZ")

        metar.skyCover shouldBe SkyCover.Unknown
        metar.skyUnknown shouldBe true
        metar.ceiling shouldBe Ceiling.Unknown
        metar.flightRules shouldBe FlightRules.UNKNOWN
        metar.groundCondition shouldBe GroundCondition.Unknown
    }
}
