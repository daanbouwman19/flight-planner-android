package com.github.daanbouwman.flightplanner.model

import io.kotest.matchers.shouldBe
import kotlin.test.Test

class FlightRulesDeriveTest {

    @Test
    fun `both inputs absent is unknown`() {
        FlightRules.derive(Ceiling.Unknown, visibilityStatuteMiles = null) shouldBe FlightRules.UNKNOWN
    }

    @Test
    fun `boundaries match the enum's own description text`() {
        fun at(ft: Int, vis: Double) = FlightRules.derive(Ceiling.At(ft), vis)

        // VFR: ceiling > 3,000 AND visibility > 5.
        at(3_001, 6.0) shouldBe FlightRules.VFR
        // Exactly 3,000/5.0 is MVFR — the description says "ceiling 1,000 to
        // 3,000" for MVFR, i.e. inclusive at the top.
        at(3_000, 5.0) shouldBe FlightRules.MVFR
        at(1_000, 3.0) shouldBe FlightRules.MVFR
        at(999, 6.0) shouldBe FlightRules.IFR
        at(6_000, 2.9) shouldBe FlightRules.IFR
        at(500, 6.0) shouldBe FlightRules.IFR
        at(499, 6.0) shouldBe FlightRules.LIFR
        at(6_000, 0.9) shouldBe FlightRules.LIFR
    }

    @Test
    fun `whichever of ceiling and visibility is worse decides`() {
        FlightRules.derive(Ceiling.At(10_000), 0.5) shouldBe FlightRules.LIFR
        FlightRules.derive(Ceiling.At(400), 10.0) shouldBe FlightRules.LIFR
    }

    @Test
    fun `a reported clear sky is not a missing one`() {
        // Unlimited is a measurement — the station said so — so it combines with
        // a good visibility to give VFR.
        FlightRules.derive(Ceiling.Unlimited, 10.0) shouldBe FlightRules.VFR
        // And with a poor visibility it still yields the visibility's category.
        FlightRules.derive(Ceiling.Unlimited, 2.0) shouldBe FlightRules.IFR
    }

    @Test
    fun `an unreported sky does not get the benefit of the doubt`() {
        // This is the defect the redesign exists to remove. Ten miles of
        // visibility and no sky report is NOT VFR: the sky could be a 200 ft
        // overcast, and answering VFR is how an IFR field got drawn with a sun.
        FlightRules.derive(Ceiling.Unknown, 10.0) shouldBe FlightRules.UNKNOWN
        // Symmetrically, a good ceiling with no visibility report is not VFR.
        FlightRules.derive(Ceiling.At(5_000), null) shouldBe FlightRules.UNKNOWN
        FlightRules.derive(Ceiling.Unlimited, null) shouldBe FlightRules.UNKNOWN
    }

    @Test
    fun `a category that cannot be worsened stands even with the other input missing`() {
        // Below a mile of visibility is LIFR whatever the ceiling turns out to
        // be, so the answer is safe to give.
        FlightRules.derive(Ceiling.Unknown, 0.5) shouldBe FlightRules.LIFR
        // Likewise a ceiling under 500 ft.
        FlightRules.derive(Ceiling.At(200), null) shouldBe FlightRules.LIFR
        // An indefinite ceiling counts the same way.
        FlightRules.derive(Ceiling.At(200, indefinite = true), null) shouldBe FlightRules.LIFR
    }

    @Test
    fun `fromCode is case and whitespace tolerant, and unknown for anything else`() {
        FlightRules.fromCode(" vfr ") shouldBe FlightRules.VFR
        FlightRules.fromCode("LIFR") shouldBe FlightRules.LIFR
        FlightRules.fromCode(null) shouldBe FlightRules.UNKNOWN
        FlightRules.fromCode("") shouldBe FlightRules.UNKNOWN
        FlightRules.fromCode("bogus") shouldBe FlightRules.UNKNOWN
    }
}

class MetarFromRawTest {

    @Test
    fun `a raw report becomes a fully populated observation`() {
        val metar = buildMetar(
            station = "KJFK",
            raw = "METAR KJFK 271851Z 17020G27KT 4SM -TSRA BR FEW015 BKN043CB OVC130 23/22 A3003",
            supplement = MetarSupplement.None.copy(flightRulesCode = "MVFR"),
        )

        metar.station shouldBe "KJFK"
        metar.flightRules shouldBe FlightRules.MVFR
        metar.observationTime shouldBe "271851Z"
        metar.ceiling shouldBe Ceiling.At(ft = 4_300, indefinite = false)
        metar.windGustKt shouldBe 27
        metar.groundCondition shouldBe GroundCondition.Wet
        metar.skyUnknown shouldBe false
        (metar.skyCover as SkyCover.Layers).layers.size shouldBe 3
    }

    @Test
    fun `an obscured field is not clear and not unknown`() {
        val metar = Metar.fromRaw(
            station = "PACD",
            raw = "SPECI PACD 271823Z 14015G22KT 1/2SM FG VV002 12/12 A3011",
        )

        metar.skyCover shouldBe SkyCover.Obscured(verticalVisibilityFt = 200)
        metar.skyUnknown shouldBe false
        // Derived, since no provider category was supplied: half a mile is LIFR.
        metar.flightRules shouldBe FlightRules.LIFR
        metar.isSpeci shouldBe true
    }

    @Test
    fun `a report with no sky group leaves the sky unknown and the category unknown`() {
        // The AVWX-shaped case, and the reason `fromRaw` exists: an unreported
        // sky must stay unreported all the way to the renderer.
        val metar = Metar.fromRaw(station = "XXXX", raw = "METAR XXXX 271800Z 31006KT 10SM 22/17 Q1008")

        metar.skyUnknown shouldBe true
        metar.ceiling shouldBe Ceiling.Unknown
        metar.flightRules shouldBe FlightRules.UNKNOWN
    }

    @Test
    fun `a provider category wins over the derived one, when there is a sky to have one about`() {
        // NOAA's fltCat is the official categorisation and local derivation is
        // the fallback — over a report whose sky the station actually measured.
        val metar = buildMetar(
            station = "XXXX",
            raw = "METAR XXXX 271800Z 31006KT 10SM BKN025 22/17 Q1008",
            supplement = MetarSupplement.None.copy(flightRulesCode = "VFR"),
        )

        // Local derivation would say MVFR off a 2,500 ft ceiling. The provider
        // outranks it, which is the half of the old rule that survives.
        metar.flightRules shouldBe FlightRules.VFR
    }

    @Test
    fun `a provider category does not survive an unreported sky`() {
        // **This assertion is the reverse of the one that stood here before**,
        // and the reversal is deliberate rather than a test bent to fit a
        // change. The old test asserted `VFR`, and its own second line admitted
        // the problem: "…but the sky is still honestly unknown, which is what
        // the scene draws". That is a chip and a scene contradicting each other
        // on the same card — recorded as defect 2 in docs/WEATHER-PLAN.md, which
        // named it "the original bug's shape, surviving in the chip", and
        // deliberately left the call to the repository owner. The call was made:
        // an unreported sky vetoes the provider.
        //
        // What did not change is that a sky *is* still honestly unknown here.
        // The scene drew that correctly all along; it is the chip that has
        // stopped disagreeing with it.
        val metar = buildMetar(
            station = "XXXX",
            raw = "METAR XXXX 271800Z 31006KT 10SM 22/17 Q1008",
            supplement = MetarSupplement.None.copy(flightRulesCode = "VFR"),
        )

        metar.flightRules shouldBe FlightRules.UNKNOWN
        metar.skyUnknown shouldBe true
    }

    @Test
    fun `an empty raw string yields an observation that claims nothing`() {
        val metar = Metar.fromRaw(station = "XXXX", raw = "")

        metar.skyCover shouldBe SkyCover.Unknown
        metar.flightRules shouldBe FlightRules.UNKNOWN
        metar.groundCondition shouldBe GroundCondition.Unknown
        metar.station shouldBe "XXXX"
    }
}
