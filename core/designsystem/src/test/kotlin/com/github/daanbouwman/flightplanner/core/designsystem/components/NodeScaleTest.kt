package com.github.daanbouwman.flightplanner.core.designsystem.components

import io.kotest.matchers.floats.shouldBeGreaterThan
import io.kotest.matchers.floats.shouldBeLessThan
import io.kotest.matchers.shouldBe
import kotlin.math.abs
import kotlin.test.Test

/**
 * The visited network's one sizing rule. The case it exists for: a logbook in
 * which every field has been visited once drew every dot at the maximum, and
 * two fields 90 NM apart merged into one blob on both maps.
 */
class NodeScaleTest {

    @Test
    fun `equal counts are all the smallest dot`() {
        nodeSizeFraction(visits = 1, minVisits = 1, maxVisits = 1) shouldBe 0f
        nodeSizeFraction(visits = 7, minVisits = 7, maxVisits = 7) shouldBe 0f
    }

    @Test
    fun `the least and most visited fields anchor the ends of the scale`() {
        nodeSizeFraction(visits = 1, minVisits = 1, maxVisits = 6) shouldBe 0f
        nodeSizeFraction(visits = 6, minVisits = 1, maxVisits = 6) shouldBe 1f
        // Whatever the absolute counts are: a log where the quietest field has
        // three visits still draws it as the small dot.
        nodeSizeFraction(visits = 3, minVisits = 3, maxVisits = 12) shouldBe 0f
        nodeSizeFraction(visits = 12, minVisits = 3, maxVisits = 12) shouldBe 1f
    }

    @Test
    fun `between the ends the area, not the radius, grows with the count`() {
        // 1..N: a field with a quarter of the range above the floor is half way
        // up the radius scale, which is a quarter of the way up in area.
        val quarter = nodeSizeFraction(visits = 3, minVisits = 1, maxVisits = 9)
        abs(quarter - 0.5f) shouldBeLessThan 1e-6f

        val fractions = (1..9).map { nodeSizeFraction(it, minVisits = 1, maxVisits = 9) }
        fractions.zipWithNext().forEach { (a, b) -> b shouldBeGreaterThan a }
        // Concave: each extra visit adds less radius than the one before.
        fractions.zipWithNext { a, b -> b - a }.zipWithNext().forEach { (d1, d2) -> d2 shouldBeLessThan d1 }
    }

    @Test
    fun `a count outside the stated range is clamped rather than extrapolated`() {
        nodeSizeFraction(visits = 0, minVisits = 1, maxVisits = 4) shouldBe 0f
        nodeSizeFraction(visits = 40, minVisits = 1, maxVisits = 4) shouldBe 1f
    }
}
