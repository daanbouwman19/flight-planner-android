package com.github.daanbouwman.flightplanner.core.designsystem.components

import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.floats.shouldBeGreaterThan
import io.kotest.matchers.floats.shouldBeLessThan
import io.kotest.matchers.shouldBe
import kotlin.test.Test

/**
 * The air, as four flat steps.
 *
 * The steps are a drawing subdivision rather than a measurement, so what is worth
 * asserting is not where any one of them sits but that together they are a
 * *partition* of the frame with no seam and no overlap — a gap would paint the card
 * surface through the sky, and an overlap would put an unintended edge in the air at
 * a height that means nothing.
 */
class AirStepsTest {

    @Test
    fun `the steps tile the frame exactly`() {
        AirSteps.first().bottomFraction shouldBe 0f
        AirSteps.last().topFraction shouldBe 1f
        AirSteps.zipWithNext().forEach { (lower, upper) ->
            // Shared edges, not merely adjacent ones: a hairline gap between two
            // flat rects is a visible seam at any density.
            upper.bottomFraction shouldBe lower.topFraction
        }
    }

    @Test
    fun `every internal step edge is a ceiling threshold`() {
        // The claim that makes the steps information rather than decoration: each
        // one is the air inside a single flight-rules band, so the tone change and
        // the hairline are one edge stated twice. A step boundary anywhere else would
        // put a visible seam in the air at an altitude that means nothing.
        val edges = AirSteps.drop(1).map { it.bottomFraction }.toSet()
        edges shouldBe CeilingThresholds.map { (ft, _) -> altitudeToFraction(ft) }.toSet()
    }

    @Test
    fun `there is one step per band, from the ground to the top of the axis`() {
        AirSteps.size shouldBe CeilingThresholds.size + 1
    }

    @Test
    fun `the air darkens with height`() {
        // Every palette's `high` is darker than its `low`, because that is what the
        // atmosphere does. Listed from the ground up, the mix has to rise or the sky
        // is drawn upside down.
        AirSteps.zipWithNext().forEach { (lower, upper) ->
            lower.mix shouldBeLessThan upper.mix
        }
        // The air just above the field is the band's own `low`, undiluted.
        AirSteps.first().mix shouldBe 0f
    }

    @Test
    fun `a numeral's chip sits between the two ends of its band`() {
        // What makes one proved contrast pair serve every numeral. A mix outside
        // 0..1 would put the chip beyond `low` or beyond `high`, where `cloudEdge`
        // has never been measured.
        AxisChipMix shouldBeGreaterThan 0f
        AxisChipMix shouldBeLessThan 1f
    }
}

/**
 * The strike envelope.
 *
 * A lightning flash is the one piece of motion in this scene that carries content
 * rather than ambience, and its shape is the whole of what distinguishes it from a
 * pulsing indicator: mostly dark, then a leader and a return stroke a few frames
 * apart. [boltOpacity] *is* that shape, so it is asserted here rather than described
 * in a comment and eyeballed on a device.
 */
class BoltEnvelopeTest {

    @Test
    fun `a strike is dark for most of its cycle`() {
        // Sampled rather than reasoned about, so a future retune that quietly turns
        // the storm into a strobe fails here. Three quarters is a floor with real
        // headroom under the current 0.78 — the assertion is "rare", not "0.78".
        val samples = 1_000
        val dark = (0 until samples).count { boltOpacity(it / samples.toFloat()) == 0f }
        (dark.toFloat() / samples) shouldBeGreaterThan 0.75f
    }

    @Test
    fun `the flash is a double tick rather than a single fade`() {
        // Two peaks with a genuine dip between them. A single ramp up and down would
        // satisfy "mostly dark" and read as a pulse, which is the failure this test
        // exists to catch.
        boltOpacity(0.80f) shouldBe (1f plusOrMinus 0.001f)
        boltOpacity(0.83f) shouldBe (0.15f plusOrMinus 0.001f)
        boltOpacity(0.86f) shouldBe (1f plusOrMinus 0.001f)
    }

    @Test
    fun `the envelope starts and ends dark, so the loop has no seam`() {
        boltOpacity(0f) shouldBe 0f
        boltOpacity(0.999f) shouldBe 0f
    }

    @Test
    fun `a phase outside zero to one wraps`() {
        // Load-bearing: the draw block feeds this `master * rate + phase`, which is
        // several whole cycles plus an offset and is never in 0..1.
        boltOpacity(3.80f) shouldBe (boltOpacity(0.80f) plusOrMinus 0.0001f)
        boltOpacity(-0.20f) shouldBe (boltOpacity(0.80f) plusOrMinus 0.0001f)
    }

    @Test
    fun `the two strikes are not synchronised`() {
        // The reason each bolt carries its own rate.
        //
        // The first version of this asserted the two are *never* lit together, and
        // that was the wrong claim rather than a failing implementation: two cells
        // flashing at the same instant is what a storm does, and with coprime rates
        // it happens for about two per cent of the cycle. What must not happen is
        // that they always flash together — a pair in lockstep reads as one blinking
        // indicator, which is exactly the mark this replaced.
        //
        // So the assertion is the ratio: each bolt spends far more of its lit time
        // alone than in company.
        val (first, second) = ConvectiveStrikes
        val samples = 40_000
        var alone = 0
        var together = 0
        repeat(samples) { step ->
            val master = step / samples.toFloat()
            val a = boltOpacity(master * first.rate + first.phase) > 0f
            val b = boltOpacity(master * second.rate + second.phase) > 0f
            if (a && b) together++ else if (a || b) alone++
        }
        (together > 0) shouldBe true
        (alone > together * 8) shouldBe true
    }

    @Test
    fun `every strike's rate is a whole number of cycles`() {
        // What makes the master loop seamless. A fractional rate leaves a bolt
        // part-way through its envelope when the master restarts, which is a visible
        // cut in the one element that must not look like a glitch.
        ConvectiveStrikes.forEach { strike ->
            (strike.rate * BoltCycleMillis) % BoltCycleMillis shouldBe 0
            (strike.rate > 0) shouldBe true
        }
    }
}

/**
 * The precipitation field's variation.
 *
 * The field used to read as pen hatching because every stroke was identical. The fix
 * is one number per drop driving length *and* speed together, and the two properties
 * worth pinning are that they move together — a long slow streak is the one
 * combination that cannot occur in nature — and that the slowest drop still crosses
 * the frame inside a cycle, or it never appears to land.
 */
class PrecipitationVariationTest {

    @Test
    fun `every speed is a whole number of crossings`() {
        // The load-bearing constraint, and the one a future retune is most likely to
        // reach for: a fractional speed leaves its drop part-way down the frame when
        // the shared phase restarts, so the entire field jumps once a cycle. See
        // [PrecipSpeeds], and `the particle field is seamless at the end of a cycle`,
        // which is the same claim asserted through the drawing arithmetic.
        PrecipSpeeds.forEach { speed -> (speed >= 1) shouldBe true }
        (MedianPrecipSpeed in PrecipSpeeds.toList()) shouldBe true
    }

    @Test
    fun `the spread is wide enough to break the texture and narrow enough to follow`() {
        // Two to one. Below about 1.5 the field reads as uniform again, which is the
        // defect; much above two and the fast drops are a blur the eye reads as noise
        // rather than as rain falling harder.
        val ratio = PrecipSpeeds.max().toFloat() / PrecipSpeeds.min().toFloat()
        ratio shouldBeGreaterThan 1.5f
        ratio shouldBeLessThan 2.5f
    }

    @Test
    fun `a streak's length tracks its speed`() {
        // Not a stylistic pairing: the streak is what the eye keeps of a drop that
        // has already moved on, so it is long *because* the drop is fast. Length and
        // speed varying independently is what made the field read as pen hatching.
        precipitationMarkScale(PrecipSpeeds.max()) shouldBeGreaterThan
            precipitationMarkScale(PrecipSpeeds.min())
        precipitationMarkScale(MedianPrecipSpeed) shouldBe (1f plusOrMinus 0.001f)
    }

    @Test
    fun `a particle is deterministic and inside its bounds`() {
        // Deterministic for the reason `deckSpans` gives: a field that reshuffled
        // when an unrelated part of the screen recomposed would be motion carrying
        // no meaning.
        repeat(60) { index ->
            val a = precipitationParticle(index, 3)
            val b = precipitationParticle(index, 3)
            a shouldBe b

            a.x shouldBeGreaterThan -0.001f
            a.x shouldBeLessThan 1.001f
            a.y shouldBeGreaterThan -0.001f
            a.y shouldBeLessThan 1.001f
            (a.speed in PrecipSpeeds.toList()) shouldBe true
            // Never transparent, and never fully opaque: a drop at zero is a hole in
            // the field, and a field of solid strokes is the hatch again.
            a.opacity shouldBeGreaterThan 0.4f
            a.opacity shouldBeLessThan 1f
            a.gauge shouldBeGreaterThan 0.7f
            a.gauge shouldBeLessThan 1.3f
        }
    }

    @Test
    fun `two particles differ in more than position`() {
        // The actual defect. Two drops at different places but identical length,
        // weight and speed is what a hatch pattern is, and a generator that silently
        // collapsed to a constant would look identical to a clean tree without this.
        val speeds = (0 until 60).map { precipitationParticle(it, 0).speed }.toSet()
        speeds shouldBe PrecipSpeeds.toSet()
        val gauges = (0 until 40).map { precipitationParticle(it, 0).gauge }.toSet()
        (gauges.size > 30) shouldBe true
    }

    @Test
    fun `a drop's speed is uncorrelated with where it starts`() {
        // Why all five values come off one stream. Two streams seeded a fixed
        // distance apart were tried first, and the generator's own linearity carried
        // straight through the offset: the fast drops ended up biased to one side of
        // the frame, which is a diagonal band of rain rather than a field.
        val field = (0 until 400).map { precipitationParticle(it, 0) }
        val left = field.filter { it.x < 0.5f }.map { it.speed.toDouble() }.average()
        val right = field.filter { it.x >= 0.5f }.map { it.speed.toDouble() }.average()
        (kotlin.math.abs(left - right) < 0.25) shouldBe true
    }
}
