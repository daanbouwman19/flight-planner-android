package com.github.daanbouwman.flightplanner.routing

import io.kotest.assertions.withClue
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import kotlin.test.Test

/**
 * Crosswind and headwind, against values a pilot can check by hand.
 *
 * The cardinal cases are exact, and the 30/45/60° cases are the ones on every
 * crosswind chart — 0.50, 0.71 and 0.87 of the wind speed — so a regression here
 * shows up as a number that disagrees with a chart rather than as a wrong picture.
 */
class SurfaceWindTest {

    @Test
    fun `a wind straight down the runway is all headwind`() {
        val components = SurfaceWind.components(runwayHeadingDeg = 90, windFromDeg = 90, windSpeedKt = 20)

        components.headwindKt shouldBe (20.0 plusOrMinus 1e-9)
        components.crosswindKt shouldBe (0.0 plusOrMinus 1e-9)
        components.isTailwind shouldBe false
    }

    @Test
    fun `a wind straight up the runway is all tailwind`() {
        val components = SurfaceWind.components(runwayHeadingDeg = 90, windFromDeg = 270, windSpeedKt = 20)

        components.headwindKt shouldBe (-20.0 plusOrMinus 1e-9)
        components.crosswindKt shouldBe (0.0 plusOrMinus 1e-9)
        components.isTailwind shouldBe true
    }

    @Test
    fun `a wind across the runway is all crosswind`() {
        val fromRight = SurfaceWind.components(runwayHeadingDeg = 90, windFromDeg = 180, windSpeedKt = 20)
        fromRight.headwindKt shouldBe (0.0 plusOrMinus 1e-9)
        fromRight.crosswindKt shouldBe (20.0 plusOrMinus 1e-9)
        fromRight.fromRight shouldBe true

        val fromLeft = SurfaceWind.components(runwayHeadingDeg = 90, windFromDeg = 0, windSpeedKt = 20)
        fromLeft.crosswindKt shouldBe (20.0 plusOrMinus 1e-9)
        fromLeft.fromRight shouldBe false
    }

    /** The three rows every crosswind chart prints. */
    @Test
    fun `the chart angles come out at the chart values`() {
        val expected = mapOf(
            30 to (0.500 to 0.866),
            45 to (0.707 to 0.707),
            60 to (0.866 to 0.500),
        )
        for ((offset, factors) in expected) {
            val (crosswindFactor, headwindFactor) = factors
            val components = SurfaceWind.components(
                runwayHeadingDeg = 0,
                windFromDeg = offset,
                windSpeedKt = 100,
            )
            withClue("$offset° off the nose") {
                components.crosswindKt shouldBe (crosswindFactor * 100 plusOrMinus 0.5)
                components.headwindKt shouldBe (headwindFactor * 100 plusOrMinus 0.5)
            }
        }
    }

    /**
     * The 350/010 case: a difference of headings that crosses north.
     *
     * Without normalising to −180..180 the offset comes out as 340°, whose sine is
     * negative, and the crosswind would be reported from the wrong side.
     */
    @Test
    fun `an offset across north keeps the correct side`() {
        val windFromLeft = SurfaceWind.components(runwayHeadingDeg = 10, windFromDeg = 350, windSpeedKt = 15)
        windFromLeft.fromRight shouldBe false
        windFromLeft.headwindKt shouldBeGreaterThan 0.0

        val windFromRight = SurfaceWind.components(runwayHeadingDeg = 350, windFromDeg = 10, windSpeedKt = 15)
        windFromRight.fromRight shouldBe true
        windFromRight.headwindKt shouldBeGreaterThan 0.0
    }

    @Test
    fun `a heading and a wind given outside zero to 360 still resolve`() {
        val wrapped = SurfaceWind.components(runwayHeadingDeg = 450, windFromDeg = 450, windSpeedKt = 10)
        wrapped.headwindKt shouldBe (10.0 plusOrMinus 1e-9)
    }

    @Test
    fun `a calm wind resolves to nothing at all`() {
        val components = SurfaceWind.components(runwayHeadingDeg = 90, windFromDeg = 180, windSpeedKt = 0)
        components.headwindKt shouldBe (0.0 plusOrMinus 1e-9)
        components.crosswindKt shouldBe (0.0 plusOrMinus 1e-9)
    }

    // --- picking a runway ---------------------------------------------------

    @Test
    fun `the favoured end is the one into wind`() {
        // Runway 09/27, wind from the east: 09 it is.
        val favoured = SurfaceWind.favouredEnd(listOf(90, 270), windFromDeg = 90, windSpeedKt = 12)
        favoured shouldBe 0

        SurfaceWind.favouredEnd(listOf(90, 270), windFromDeg = 270, windSpeedKt = 12) shouldBe 1
    }

    /**
     * Headwind decides before crosswind, and this fixture forces the two apart.
     *
     * Wind from 180° at 20 kt, over runways 000 and 090. Runway 000 has a **20 kt
     * pure tailwind and no crosswind at all**; runway 090 has a 20 kt pure crosswind
     * and no headwind. A rule that minimised crosswind would send the aircraft down
     * the one with the tailwind, which lengthens every takeoff and landing roll.
     *
     * Most real winds make one end better on both counts at once, which is why this
     * case is synthetic: it is the only shape that can tell the two rules apart.
     */
    @Test
    fun `a tailwind runway is never favoured over a crosswind one`() {
        val headings = listOf(0, 90)
        val favoured = SurfaceWind.favouredEnd(headings, windFromDeg = 180, windSpeedKt = 20)

        favoured shouldBe 1
        val chosen = SurfaceWind.components(headings[1], windFromDeg = 180, windSpeedKt = 20)
        val rejected = SurfaceWind.components(headings[0], windFromDeg = 180, windSpeedKt = 20)

        chosen.isTailwind shouldBe false
        rejected.isTailwind shouldBe true
        // The chosen end is the one with *more* crosswind, which is the whole point.
        chosen.crosswindKt shouldBeGreaterThan rejected.crosswindKt
    }

    @Test
    fun `crosswind breaks a headwind tie`() {
        // Two ends symmetric about the wind have identical headwind; the closer one
        // to the nose has less crosswind and wins.
        val headings = listOf(30, 60)
        val favoured = SurfaceWind.favouredEnd(headings, windFromDeg = 30, windSpeedKt = 10)
        favoured shouldBe 0
    }

    @Test
    fun `a calm or unreported wind favours nothing`() {
        SurfaceWind.favouredEnd(listOf(90, 270), windFromDeg = 90, windSpeedKt = 2) shouldBe null
        SurfaceWind.favouredEnd(listOf(90, 270), windFromDeg = null, windSpeedKt = 12) shouldBe null
        SurfaceWind.favouredEnd(listOf(90, 270), windFromDeg = 90, windSpeedKt = null) shouldBe null
        SurfaceWind.favouredEnd(emptyList(), windFromDeg = 90, windSpeedKt = 12) shouldBe null
    }

    // --- the sock -----------------------------------------------------------

    @Test
    fun `the sock hangs limp in nothing and streams level in fifteen knots`() {
        SurfaceWind.sockLift(null) shouldBe 0f
        SurfaceWind.sockLift(0) shouldBe 0f
        SurfaceWind.sockLift(SurfaceWind.SockLimpKt) shouldBe 0f
        SurfaceWind.sockLift(SurfaceWind.SockFullKt) shouldBe 1f
        SurfaceWind.sockLift(45) shouldBe 1f
    }

    @Test
    fun `the sock lifts monotonically between limp and level`() {
        var previous = -1f
        for (kt in 0..20) {
            val lift = SurfaceWind.sockLift(kt)
            withClue("$kt kt") {
                (lift >= previous) shouldBe true
                (lift in 0f..1f) shouldBe true
            }
            previous = lift
        }
        // And it is genuinely in between somewhere, rather than a step function.
        val middle = SurfaceWind.sockLift(9).toDouble()
        middle shouldBeGreaterThan 0.2
        middle shouldBeLessThan 0.8
    }

    @Test
    fun `the rounded figures are the ones shown beside a runway`() {
        val components = SurfaceWind.components(runwayHeadingDeg = 0, windFromDeg = 45, windSpeedKt = 17)
        components.headwindRounded() shouldBe 12
        components.crosswindRounded() shouldBe 12
    }
}
