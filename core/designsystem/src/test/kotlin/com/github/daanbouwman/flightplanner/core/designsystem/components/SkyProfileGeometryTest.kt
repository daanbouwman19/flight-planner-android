package com.github.daanbouwman.flightplanner.core.designsystem.components

import com.github.daanbouwman.flightplanner.model.Ceiling
import com.github.daanbouwman.flightplanner.model.CloudCover
import com.github.daanbouwman.flightplanner.model.CloudLayer
import com.github.daanbouwman.flightplanner.model.ConvectiveCloud
import com.github.daanbouwman.flightplanner.model.MetarParser
import com.github.daanbouwman.flightplanner.model.PhenomenonKind
import com.github.daanbouwman.flightplanner.model.PresentWeather
import com.github.daanbouwman.flightplanner.model.SkyCover
import com.github.daanbouwman.flightplanner.model.WeatherPhenomenon
import io.kotest.assertions.withClue
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.floats.shouldBeGreaterThan
import io.kotest.matchers.floats.shouldBeGreaterThanOrEqual
import io.kotest.matchers.floats.shouldBeLessThanOrEqual
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import kotlin.test.Test

/**
 * The sky profile's arithmetic, asserted.
 *
 * Real reports rather than invented ones wherever a fixture is needed — all
 * captured from NOAA on 2026-08-27, the same corpus the parser tests use, so a
 * change that breaks one breaks both and the divergence is visible.
 */
class SkyProfileGeometryTest {

    /** Four decks, one convective. The best single fixture in the set. */
    private val kjfk = "METAR KJFK 271851Z 17020G27KT 4SM -TSRA BR " +
        "FEW015 BKN043CB BKN110 OVC130 23/22 A3003"

    /** Four broken decks — the merge-within-band case. */
    private val ksyr = "METAR KSYR 271754Z 30015G25KT 10SM TS " +
        "BKN032 BKN060 BKN120 BKN200 24/17 A2996"

    /** Fog to 200 ft, the indefinite ceiling. */
    private val pacd = "SPECI PACD 271823Z 14015G22KT 1/2SM FG VV002 12/12 A3011"

    /** Two ceiling decks close enough that one body would cover the other. */
    private val khpn = "METAR KHPN 271956Z 16010G17KT 2 1/2SM BKN013 OVC019 " +
        "23/21 A3001 RMK AO2 SFC VIS 10 LTG DSNT SW SLP158"

    // --- the axis -----------------------------------------------------------

    @Test
    fun `the axis is monotonic across its whole range`() {
        var previous = -1f
        for (ft in 0..46_000 step 50) {
            val fraction = altitudeToFraction(ft)
            withClue("$ft ft") { fraction shouldBeGreaterThanOrEqual previous }
            previous = fraction
        }
    }

    @Test
    fun `the axis passes exactly through every breakpoint`() {
        for ((ft, expected) in AxisBreakpoints) {
            withClue("$ft ft") { altitudeToFraction(ft).toDouble() shouldBe (expected.toDouble() plusOrMinus 1e-6) }
        }
    }

    @Test
    fun `the axis stays inside zero and one, and clamps rather than extrapolating`() {
        altitudeToFraction(-500) shouldBe 0f
        altitudeToFraction(0) shouldBe 0f
        altitudeToFraction(AxisTopFt) shouldBe 1f
        altitudeToFraction(120_000) shouldBe 1f
    }

    /**
     * The design claim, checked as arithmetic: the first 3,000 ft — everything that
     * decides a flight category — gets more than half the frame.
     */
    @Test
    fun `the first three thousand feet occupy more than half the axis`() {
        altitudeToFraction(3_000) shouldBeGreaterThan 0.5f
    }

    /**
     * The reason the axis is not linear, stated as a test.
     *
     * An IFR ceiling and an MVFR ceiling must be far enough apart on the axis to be
     * seen as different. On a linear 45,000 ft scale they would sit 0.044 apart; a
     * deck drawn at each would overlap.
     */
    @Test
    fun `the IFR and MVFR ceilings are visibly far apart`() {
        val separation = altitudeToFraction(2_900) - altitudeToFraction(900)
        separation shouldBeGreaterThan MinDeckSeparation * 2f
    }

    // --- merging ------------------------------------------------------------

    @Test
    fun `decks far enough apart on the axis are left alone`() {
        val decks = mergeDecks(
            listOf(
                CloudLayer(CloudCover.FEW, 1_500),
                CloudLayer(CloudCover.BROKEN, 4_300),
                CloudLayer(CloudCover.OVERCAST, 11_000),
            ),
        )
        decks.map { it.baseFt } shouldBe listOf(1_500, 4_300, 11_000)
        decks.map { it.mergedCount } shouldBe listOf(1, 1, 1)
    }

    /**
     * KJFK's four layers become three, and the pair that collapses is the upper one.
     *
     * BKN110 and OVC130 are 2,000 ft apart in the air and 0.031 apart on the axis,
     * because above 12,000 ft the scale is compressed by a factor of about seven.
     * That is the compression working as intended rather than a limit being hit: two
     * hairlines 5 dp apart at 160 dp would read as one thick deck regardless, and
     * drawing them separately would only make it a blurrier one.
     *
     * The merged deck takes OVC — the denser of the two — at 11,000 ft, the lower
     * base. The ceiling was already 11,000 (BKN is a ceiling), so it does not move.
     */
    @Test
    fun `the KJFK decks collapse only where the axis compresses`() {
        val layers = (MetarParser.parse(kjfk).skyCover as SkyCover.Layers).layers
        layers.size shouldBe 4

        val decks = mergeDecks(layers)

        decks.map { it.baseFt } shouldBe listOf(1_500, 4_300, 11_000)
        decks.map { it.mergedCount } shouldBe listOf(1, 1, 2)
        decks.last().cover shouldBe CloudCover.OVERCAST
        SkyCover.Layers(layers).ceiling shouldBe Ceiling.At(ft = 4_300, indefinite = false)
    }

    @Test
    fun `the convective modifier survives a merge`() {
        val decks = mergeDecks((MetarParser.parse(kjfk).skyCover as SkyCover.Layers).layers)
        decks.single { it.baseFt == 4_300 }.convective shouldBe ConvectiveCloud.CUMULONIMBUS
    }

    @Test
    fun `two decks that would overlap on the axis become one`() {
        // KSYR's upper two decks, 12,000 and 20,000 ft, land inside the compressed
        // high air. Both are BKN, so they share a ceiling class and may merge.
        val decks = mergeDecks((MetarParser.parse(ksyr).skyCover as SkyCover.Layers).layers)
        decks.size shouldBe 3
        decks.map { it.baseFt } shouldBe listOf(3_200, 6_000, 12_000)
        decks.last().mergedCount shouldBe 2
    }

    /**
     * The correctness argument for merging at all.
     *
     * A merge may reorganise how the sky is *drawn*; it may not change what the sky
     * *is*. The ceiling is the one fact the whole flight category rests on, so it is
     * asserted directly against the model's own derivation over every fixture,
     * including a synthetic worst case built to tempt the merge across the boundary.
     */
    @Test
    fun `merging never moves the ceiling`() {
        val fixtures = listOf(
            "KJFK" to kjfk,
            "KSYR" to ksyr,
            "KLCH" to "SPECI KLCH 271830Z VRB05KT 10SM SCT027 30/23 A2999",
        ).mapNotNull { (name, raw) ->
            (MetarParser.parse(raw).skyCover as? SkyCover.Layers)?.let { name to it.layers }
        } + listOf(
            // A FEW 100 ft under a BKN: adjacent on the axis, opposite ceiling
            // classes. Merging these would drop the ceiling from 3,000 to 2,900.
            "adjacent across the boundary" to listOf(
                CloudLayer(CloudCover.FEW, 2_900),
                CloudLayer(CloudCover.BROKEN, 3_000),
            ),
            // The same trap from the other side.
            "OVC just above a SCT" to listOf(
                CloudLayer(CloudCover.SCATTERED, 20_000),
                CloudLayer(CloudCover.OVERCAST, 20_500),
            ),
        )

        for ((name, layers) in fixtures) {
            val before = SkyCover.Layers(layers).ceiling
            val after = SkyCover.Layers(
                mergeDecks(layers).map { CloudLayer(it.cover, it.baseFt, it.convective) },
            ).ceiling
            withClue("$name: $before became $after") { after shouldBe before }
        }
    }

    @Test
    fun `a merge takes the densest cover and the lowest base of its members`() {
        val decks = mergeDecks(
            listOf(
                CloudLayer(CloudCover.BROKEN, 20_000),
                CloudLayer(CloudCover.OVERCAST, 20_400),
            ),
        )
        decks.size shouldBe 1
        decks.single().cover shouldBe CloudCover.OVERCAST
        decks.single().baseFt shouldBe 20_000
    }

    @Test
    fun `layers arrive sorted regardless of the order they were reported in`() {
        val decks = mergeDecks(
            listOf(
                CloudLayer(CloudCover.OVERCAST, 13_000),
                CloudLayer(CloudCover.FEW, 1_500),
                CloudLayer(CloudCover.BROKEN, 4_300),
            ),
        )
        decks.map { it.baseFt } shouldBe listOf(1_500, 4_300, 13_000)
    }

    @Test
    fun `no layers means no decks`() {
        mergeDecks(emptyList()) shouldBe emptyList()
    }

    // --- drawing fractions --------------------------------------------------

    @Test
    fun `drawn decks are always at least the minimum separation apart`() {
        val decks = mergeDecks(
            listOf(
                CloudLayer(CloudCover.FEW, 2_900),
                CloudLayer(CloudCover.BROKEN, 3_000),
                CloudLayer(CloudCover.OVERCAST, 3_100),
            ),
        )
        val fractions = deckFractions(decks)
        for (i in 1 until fractions.size) {
            withClue("$i: $fractions") {
                (fractions[i] - fractions[i - 1]) shouldBeGreaterThanOrEqual MinDeckSeparation - 1e-5f
            }
        }
    }

    @Test
    fun `drawn decks stay on the axis and keep their order`() {
        // Eight layers, deliberately crowded: the nudge has to run out of room.
        val layers = (1..8).map { CloudLayer(CloudCover.BROKEN, it * 40) }
        val fractions = deckFractions(mergeDecks(layers))
        fractions.zipWithNext().forEach { (lower, upper) ->
            withClue("$fractions") { upper shouldBeGreaterThan lower }
        }
        fractions.forEach {
            withClue("$fractions") {
                it shouldBeGreaterThanOrEqual 0f
                it shouldBeLessThanOrEqual MaxDeckFraction + 1e-5f
            }
        }
    }

    @Test
    fun `nudging leaves the reported altitudes alone`() {
        val decks = mergeDecks(
            listOf(CloudLayer(CloudCover.FEW, 2_900), CloudLayer(CloudCover.BROKEN, 3_000)),
        )
        // The fractions move; the feet do not.
        deckFractions(decks)[1] shouldBeGreaterThan altitudeToFraction(3_000)
        decks.map { it.baseFt } shouldBe listOf(2_900, 3_000)
    }

    /**
     * KHPN's `BKN013 OVC019` is right on the merge boundary, and merges.
     *
     * 1,300 and 1,900 ft land 0.06 apart — exactly [MinDeckSeparation], which was
     * chosen as roughly 10 dp at the shorter of the two sizes the scene ships at. So
     * the pair collapses to one overcast deck at 1,300 ft, and on a device that is
     * what it looks like: a single solid underside on the 1,000 ft hairline.
     *
     * Pinned because the merge was initially mistaken for a drawing bug — the two
     * decks appearing as one line looked like a body painting over its neighbour.
     * The ceiling is unaffected either way (BKN013 was already the ceiling) and the
     * panel's text still lists both layers, which is where the second base is read.
     */
    @Test
    fun `the KHPN pair sits exactly on the merge boundary and collapses`() {
        val layers = (MetarParser.parse(khpn).skyCover as SkyCover.Layers).layers
        layers.map { it.baseFt } shouldBe listOf(1_300, 1_900)

        val decks = mergeDecks(layers)

        decks.map { it.baseFt } shouldBe listOf(1_300)
        decks.single().cover shouldBe CloudCover.OVERCAST
        decks.single().mergedCount shouldBe 2
        SkyCover.Layers(layers).ceiling shouldBe Ceiling.At(ft = 1_300, indefinite = false)
    }

    /**
     * A deck never paints over the deck above it.
     *
     * The case that needs the clamp is the one [mergeDecks] refuses to collapse: a
     * FEW below a BKN cannot merge, because that would move the ceiling, so the two
     * arrive [MinDeckSeparation] apart — and an overcast body wants 0.075, more than
     * that gap. Unclamped it would paint straight over the deck below's underside.
     */
    @Test
    fun `a deck never paints over the deck above it`() {
        val decks = mergeDecks(
            listOf(
                CloudLayer(CloudCover.FEW, 2_900),
                CloudLayer(CloudCover.OVERCAST, 3_000),
            ),
        )
        decks.size shouldBe 2

        val fractions = deckFractions(decks)
        val thicknesses = deckThicknesses(decks, fractions)

        for (i in 0 until decks.size - 1) {
            val gap = fractions[i + 1] - fractions[i]
            withClue("deck $i thickness ${thicknesses[i]} vs gap $gap") {
                thicknesses[i] shouldBeLessThanOrEqual gap
            }
        }
        // And the clamp actually bit: the nominal thickness was larger than the gap.
        thicknesses[0] shouldBeLessThanOrEqual deckThicknessFraction(decks[0].cover)
    }

    @Test
    fun `an isolated deck keeps the full thickness its cover asks for`() {
        val decks = mergeDecks(
            listOf(CloudLayer(CloudCover.FEW, 1_500), CloudLayer(CloudCover.OVERCAST, 25_000)),
        )
        val thicknesses = deckThicknesses(decks, deckFractions(decks))

        // Far apart, so neither is clamped; the top one is never clamped at all.
        thicknesses[0] shouldBe deckThicknessFraction(CloudCover.FEW)
        thicknesses[1] shouldBe deckThicknessFraction(CloudCover.OVERCAST)
    }

    @Test
    fun `a sparse deck gets a shorter underside than a solid one`() {
        listOf(CloudCover.FEW, CloudCover.SCATTERED, CloudCover.BROKEN, CloudCover.OVERCAST)
            .zipWithNext()
            .forEach { (sparser, denser) ->
                withClue("$sparser vs $denser") {
                    baseInsetFor(denser) shouldBeLessThanOrEqual baseInsetFor(sparser)
                }
            }
        baseInsetFor(CloudCover.OVERCAST) shouldBe 0f
    }

    // --- spans and density --------------------------------------------------

    @Test
    fun `cover reads as horizontal extent, densest to sparsest`() {
        val coverage = listOf(
            CloudCover.FEW,
            CloudCover.SCATTERED,
            CloudCover.BROKEN,
            CloudCover.OVERCAST,
        ).map { cover -> cover to deckSpans(CloudDeck(cover, 3_000, null, 1)).sumOf { it.width.toDouble() } }

        coverage.zipWithNext().forEach { (sparser, denser) ->
            withClue("${sparser.first} ${sparser.second} vs ${denser.first} ${denser.second}") {
                denser.second shouldBeGreaterThan sparser.second
            }
        }
        coverage.last().second shouldBe (1.0 plusOrMinus 1e-6)
    }

    @Test
    fun `overcast is one unbroken span and the others are broken up`() {
        deckSpans(CloudDeck(CloudCover.OVERCAST, 3_000, null, 1)) shouldBe listOf(DeckSpan(0f, 1f))
        deckSpans(CloudDeck(CloudCover.FEW, 3_000, null, 1)).size shouldBe 2
        deckSpans(CloudDeck(CloudCover.SCATTERED, 3_000, null, 1)).size shouldBe 3
    }

    @Test
    fun `spans are deterministic, in order, and stay inside the frame`() {
        for (cover in CloudCover.entries) {
            val deck = CloudDeck(cover, 4_300, null, 1)
            val first = deckSpans(deck)
            withClue("$cover") {
                deckSpans(deck) shouldBe first
                first.zipWithNext().forEach { (a, b) -> b.start shouldBeGreaterThanOrEqual a.end - 1e-6f }
                first.forEach {
                    it.start shouldBeGreaterThanOrEqual 0f
                    it.end shouldBeLessThanOrEqual 1f + 1e-6f
                }
            }
        }
    }

    @Test
    fun `two decks in one report get different spans`() {
        val low = deckSpans(CloudDeck(CloudCover.SCATTERED, 1_500, null, 1))
        val high = deckSpans(CloudDeck(CloudCover.SCATTERED, 11_000, null, 1))
        (low == high) shouldBe false
    }

    @Test
    fun `density and thickness both rise with cover`() {
        val ordered = listOf(
            CloudCover.FEW,
            CloudCover.SCATTERED,
            CloudCover.BROKEN,
            CloudCover.OVERCAST,
        )
        ordered.zipWithNext().forEach { (sparser, denser) ->
            withClue("$sparser vs $denser") {
                deckOpacity(denser) shouldBeGreaterThan deckOpacity(sparser)
                deckThicknessFraction(denser) shouldBeGreaterThan deckThicknessFraction(sparser)
            }
        }
        deckOpacity(CloudCover.OVERCAST) shouldBeLessThanOrEqual 1f
    }

    // --- silhouette ---------------------------------------------------------

    /**
     * Density is a shape, and this is the ordering that makes it one.
     *
     * More lobes and a higher shoulder as cover rises: FEW is two tall puffs that
     * almost part company, OVC is six shallow ripples on a continuous mass. If the
     * shoulder ever stopped rising, every deck would look like the same cloud.
     */
    @Test
    fun `a denser deck is built from more lobes on a higher shoulder`() {
        listOf(CloudCover.FEW, CloudCover.SCATTERED, CloudCover.BROKEN, CloudCover.OVERCAST)
            .zipWithNext()
            .forEach { (sparser, denser) ->
                withClue("$sparser vs $denser") {
                    deckLobes(denser) shouldBeGreaterThan deckLobes(sparser)
                    deckShoulder(denser) shouldBeGreaterThan deckShoulder(sparser)
                    // And the lobes get *shallower*, which is the other half of it:
                    // a slab has texture, a puff has height.
                    deckLobeAmplitude(denser) shouldBeLessThanOrEqual deckLobeAmplitude(sparser)
                }
            }
    }

    @Test
    fun `lobe heights are deterministic, varied, and inside the deck`() {
        for (cover in CloudCover.entries) {
            val heights = lobeHeights(cover, baseFt = 4_300, runIndex = 1)
            withClue("$cover -> $heights") {
                heights.size shouldBe deckLobes(cover)
                lobeHeights(cover, baseFt = 4_300, runIndex = 1) shouldBe heights
                heights.forEach {
                    it shouldBeGreaterThan 0f
                    it shouldBeLessThanOrEqual deckLobeAmplitude(cover)
                }
                // Never a lobe so short it reads as a hole in the deck.
                heights.min() shouldBeGreaterThanOrEqual deckLobeAmplitude(cover) * 0.6f - 1e-5f
            }
        }
    }

    @Test
    fun `a multi-lobe deck does not come out as a machined edge`() {
        // Identical lobe heights would draw a scalloped rule rather than cloud.
        val heights = lobeHeights(CloudCover.OVERCAST, baseFt = 1_300, runIndex = 0)
        heights.distinct().size shouldBeGreaterThan 1
    }

    @Test
    fun `two runs of one deck get different lobes, and two decks do too`() {
        val runZero = lobeHeights(CloudCover.SCATTERED, baseFt = 2_700, runIndex = 0)
        val runOne = lobeHeights(CloudCover.SCATTERED, baseFt = 2_700, runIndex = 1)
        val otherDeck = lobeHeights(CloudCover.SCATTERED, baseFt = 9_000, runIndex = 0)

        (runZero == runOne) shouldBe false
        (runZero == otherDeck) shouldBe false
    }

    // --- fog ----------------------------------------------------------------

    @Test
    fun `an obscured sky puts its fog top at the measured vertical visibility`() {
        val parsed = MetarParser.parse(pacd)
        parsed.skyCover shouldBe SkyCover.Obscured(200)
        fogHeightFraction(parsed.skyCover, parsed.presentWeather, parsed.visibilityStatuteMiles) shouldBe
            altitudeToFraction(200)
    }

    @Test
    fun `an obscured sky with no measured depth falls back to the inferred cap`() {
        fogHeightFraction(SkyCover.Obscured(null), emptyList(), 0.25) shouldBe MaxInferredFogFraction
    }

    @Test
    fun `ground fog under a clear sky is inferred and capped`() {
        val fog = listOf(PresentWeather(phenomena = listOf(WeatherPhenomenon.FOG)))
        val thick = fogHeightFraction(SkyCover.Clear, fog, 0.25)
        val thin = fogHeightFraction(SkyCover.Clear, fog, 2.5)

        thick shouldBeGreaterThan thin
        thick shouldBeLessThanOrEqual MaxInferredFogFraction
        // Above 3 SM the phenomenon is mist and says nothing about depth.
        fogHeightFraction(SkyCover.Clear, fog, 6.0) shouldBe 0f
    }

    @Test
    fun `no fog phenomenon means no fog`() {
        val rain = listOf(PresentWeather(phenomena = listOf(WeatherPhenomenon.RAIN)))
        fogHeightFraction(SkyCover.Clear, rain, 0.25) shouldBe 0f
        fogHeightFraction(SkyCover.Clear, emptyList(), 0.25) shouldBe 0f
    }

    /**
     * The bug, at the geometry boundary.
     *
     * An unknown sky must draw nothing at all — not fog, not weather, not a
     * suggestion of either. Whatever the rest of the report happens to contain.
     */
    @Test
    fun `an unknown sky draws no fog even when fog is reported`() {
        val fog = listOf(PresentWeather(phenomena = listOf(WeatherPhenomenon.FOG)))
        fogHeightFraction(SkyCover.Unknown, fog, 0.25) shouldBe 0f
    }

    @Test
    fun `the fog phenomena the scene recognises are the obscuring ones`() {
        // Guards the isFogOrMist predicate this depends on: if FOG or MIST ever
        // stopped being classified as obscurations, fog would silently vanish.
        WeatherPhenomenon.FOG.kind shouldBe PhenomenonKind.OBSCURATION
        WeatherPhenomenon.MIST.kind shouldBe PhenomenonKind.OBSCURATION
        PresentWeather(phenomena = listOf(WeatherPhenomenon.MIST)).isFogOrMist shouldBe true
    }
}
