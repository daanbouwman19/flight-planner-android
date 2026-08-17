package com.github.daanbouwman.flightplanner.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import io.kotest.matchers.doubles.shouldBeGreaterThanOrEqual
import kotlin.math.pow
import kotlin.test.Test

/**
 * Proves the flight-rules chips are legible, rather than asserting it in a comment.
 *
 * [FlightRulesColors] claims every container/on-container pair clears WCAG AA for
 * normal text in both tone mappings. That claim is only worth having if something
 * checks it, because the failure mode is invisible to the person who introduces
 * it: a hand-picked colour that looks fine on the author's monitor can sit well
 * under 4.5:1, and the chips most likely to be misjudged — LIFR magenta on pink,
 * UNKNOWN grey on grey — are exactly the ones a pilot most needs to read quickly.
 *
 * The ratio is computed from the WCAG 2.x definition rather than approximated from
 * Material tone numbers. Tone is a CIELAB lightness and contrast is a ratio of
 * sRGB relative luminances; "tone 90 on tone 30" is a good heuristic for clearing
 * 4.5:1 but it is not a guarantee, and the two diverge most at saturated hues.
 */
class FlightRulesContrastTest {

    @Test
    fun `light mapping clears AA for every category`() {
        assertAllPairsClearAa(LightFlightRulesColors, "light")
    }

    @Test
    fun `dark and cockpit mapping clears AA for every category`() {
        assertAllPairsClearAa(DarkFlightRulesColors, "dark")
    }

    /**
     * The five categories must be distinguishable from each other, not merely
     * readable. Colour is the primary signal on a weather chart — a pilot reads
     * green-vs-red before reading "VFR" — so two categories whose containers are
     * nearly the same colour would defeat the point even with perfect text
     * contrast. This is a much weaker bound than the AA text threshold; it exists
     * to catch a copy-paste that leaves two categories sharing a container.
     */
    @Test
    fun `categories are distinguishable from one another`() {
        for (colors in listOf(LightFlightRulesColors, DarkFlightRulesColors)) {
            val containers = colors.all.map { (rules, pair) -> rules to pair.container }
            for (i in containers.indices) {
                for (j in i + 1 until containers.size) {
                    val (aRules, aColor) = containers[i]
                    val (bRules, bColor) = containers[j]
                    val distance = perceptualDistance(aColor, bColor)
                    check(distance >= MinCategoryDistance) {
                        "$aRules and $bRules containers are too similar " +
                            "(distance $distance < $MinCategoryDistance)"
                    }
                }
            }
        }
    }

    private fun assertAllPairsClearAa(colors: FlightRulesColors, label: String) {
        for ((rules, pair) in colors.all) {
            val ratio = contrastRatio(pair.container, pair.onContainer)
            withClue("$label $rules: ${pair.onContainer.hex()} on ${pair.container.hex()} = ${"%.2f".format(ratio)}:1") {
                ratio shouldBeGreaterThanOrEqual AaNormalText
            }
        }
    }

    private companion object {
        /** WCAG 2.x AA, normal-size text. */
        const val AaNormalText = 4.5

        /**
         * Minimum separation between two category containers, in plain RGB
         * distance normalised to 0..1. Deliberately loose: it is a smoke test for
         * duplicated colours, not a perceptual-uniformity claim.
         */
        const val MinCategoryDistance = 0.10
    }
}

/** Runs [block] and rethrows any failure with [clue] prepended, so a failure names the pair. */
private inline fun withClue(clue: String, block: () -> Unit) {
    try {
        block()
    } catch (e: AssertionError) {
        throw AssertionError("$clue -- ${e.message}", e)
    }
}

/**
 * WCAG 2.x contrast ratio: `(Lighter + 0.05) / (Darker + 0.05)`, ranging 1..21.
 */
private fun contrastRatio(a: Color, b: Color): Double {
    val la = relativeLuminance(a)
    val lb = relativeLuminance(b)
    val lighter = maxOf(la, lb)
    val darker = minOf(la, lb)
    return (lighter + 0.05) / (darker + 0.05)
}

/**
 * WCAG relative luminance: linearise each sRGB channel, then weight by the eye's
 * sensitivity to it. The green coefficient dominates, which is why a green and a
 * blue of the same nominal "tone" do not have the same contrast against white.
 */
private fun relativeLuminance(color: Color): Double =
    0.2126 * linearise(color.red) + 0.7152 * linearise(color.green) + 0.0722 * linearise(color.blue)

private fun linearise(channel: Float): Double {
    val c = channel.toDouble()
    return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
}

private fun perceptualDistance(a: Color, b: Color): Double {
    val dr = (a.red - b.red).toDouble()
    val dg = (a.green - b.green).toDouble()
    val db = (a.blue - b.blue).toDouble()
    return kotlin.math.sqrt((dr * dr + dg * dg + db * db) / 3.0)
}

private fun Color.hex(): String =
    "#%02X%02X%02X".format((red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt())
