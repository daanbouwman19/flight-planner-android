package com.github.daanbouwman.flightplanner.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.test.Test

/**
 * Proves the four sky palettes work, rather than asserting it in [SkyColors]' KDoc.
 *
 * This is the sibling of [FlightRulesContrastTest] and exists for the same reason:
 * the failure mode of a hand-picked colour is invisible to the person who picks it.
 * It is a longer test than that one because a scene has more ways to go wrong than
 * a chip does, and because two of the checks here — Cockpit's luminance ceiling and
 * Chart's amber exclusion — are theme *identity* rules that until now lived only in
 * prose and could be broken by a plausible-looking edit.
 *
 * The six claims, each with the specific mistake it catches:
 *
 * 1. **Cloud edges clear 3:1 against both ends of their own band.** Catches a deck
 *    whose underside hairline vanishes into the air behind it. This is the check
 *    that forced the palettes apart in the first place — the same ink cannot serve
 *    a bright sky and a dark one, which is why the edge lives inside [SkyBand].
 * 2. **The five ground states are mutually distinguishable.** Catches frost and ice
 *    collapsing into one pale blue-grey, which they did twice while these values
 *    were being chosen, and dry and wet collapsing in Cockpit, which they also did.
 * 3. **The moon's two halves are distinguishable.** A phase is drawn as a
 *    terminator across one disc; if the halves match, every night is a full moon.
 * 4. **The three horizons are distinguishable.** Catches a copy-pasted band. Tests
 *    the horizon only — see the note on that test for why the tops legitimately
 *    converge.
 * 5. **Cockpit stays dim.** Catches a bright sky pasted into the night-flying
 *    theme, which is the single most plausible way to break it and would compile
 *    and preview perfectly.
 * 6. **Chart carries no amber.** [ChartColorScheme] reserves amber for the runway.
 *    Catches a warm sun or a golden dusk drifting in from another palette.
 */
class SkyColorsContrastTest {

    private val palettes = listOf(
        "Brand light" to BrandLightSkyColors,
        "Brand dark" to BrandDarkSkyColors,
        "Cockpit" to CockpitSkyColors,
        "Chart" to ChartSkyColors,
    )

    @Test
    fun `every cloud edge clears the graphical-object threshold against its own band`() {
        for ((palette, colors) in palettes) {
            for ((bandName, band) in colors.bands) {
                for ((airName, air) in listOf("low" to band.low, "high" to band.high)) {
                    val ratio = contrastRatio(band.cloudEdge, air)
                    check(ratio >= NonTextContrast) {
                        "$palette $bandName: cloudEdge ${band.cloudEdge.hex()} on $airName " +
                            "${air.hex()} is ${"%.2f".format(ratio)}:1, below $NonTextContrast:1"
                    }
                }
            }
        }
    }

    @Test
    fun `the five ground states are distinguishable from one another`() {
        for ((palette, colors) in palettes) {
            val states = colors.groundStates
            for (i in states.indices) {
                for (j in i + 1 until states.size) {
                    val (aName, a) = states[i]
                    val (bName, b) = states[j]
                    val distance = perceptualDistance(a, b)
                    check(distance >= MinGroundDistance) {
                        "$palette: ground $aName ${a.hex()} and $bName ${b.hex()} are too " +
                            "similar (${"%.3f".format(distance)} < $MinGroundDistance)"
                    }
                }
            }
        }
    }

    @Test
    fun `the lit and unlit halves of the moon are distinguishable`() {
        for ((palette, colors) in palettes) {
            val ratio = contrastRatio(colors.celestial.moonLit, colors.celestial.moonDark)
            check(ratio >= NonTextContrast) {
                "$palette: moonLit ${colors.celestial.moonLit.hex()} against moonDark " +
                    "${colors.celestial.moonDark.hex()} is ${"%.2f".format(ratio)}:1 — the " +
                    "terminator would be invisible and every night would look full"
            }
        }
    }

    /**
     * The three horizons must differ.
     *
     * **Only the horizon**, deliberately. Time of day is legible at the bottom of
     * the axis, where the air is thick and takes a colour from the low sun; at the
     * top of the axis every band converges toward dark in every palette, because
     * thin air is dark at any hour. Asserting a difference up there would be
     * asserting something false about the atmosphere.
     *
     * The threshold is looser than [MinGroundDistance] because Cockpit's luminance
     * ceiling deliberately compresses its range to a narrow window, leaving hue as
     * the only axis its three bands can separate on — and plain RGB distance
     * under-weights hue at low luminance. Like the flight-rules distinguishability
     * bound, this is a smoke test for a duplicated value, not a perceptual claim.
     */
    @Test
    fun `the three horizons are distinguishable within each palette`() {
        for ((palette, colors) in palettes) {
            val horizons = colors.bands.map { (name, band) -> name to band.low }
            for (i in horizons.indices) {
                for (j in i + 1 until horizons.size) {
                    val (aName, a) = horizons[i]
                    val (bName, b) = horizons[j]
                    val distance = perceptualDistance(a, b)
                    check(distance >= MinHorizonDistance) {
                        "$palette: $aName horizon ${a.hex()} and $bName horizon ${b.hex()} are " +
                            "too similar (${"%.3f".format(distance)} < $MinHorizonDistance)"
                    }
                }
            }
        }
    }

    /**
     * Cockpit is a night-flying theme, and this is what makes that true rather than
     * merely intended.
     *
     * Fills get a stricter ceiling than marks because the constraint is about area:
     * Cockpit's own `onSurface` (`#E8E1DB`) sits near 0.78, far above either bound,
     * because a glyph costs a dark-adapted eye almost nothing while a fill spanning
     * the frame costs it real time to recover.
     */
    @Test
    fun `no Cockpit colour exceeds its luminance ceiling`() {
        for ((name, color) in CockpitSkyColors.fills) {
            val luminance = relativeLuminance(color)
            check(luminance <= CockpitFillCeiling) {
                "Cockpit fill $name ${color.hex()} has luminance ${"%.3f".format(luminance)}, " +
                    "above the $CockpitFillCeiling ceiling — too bright for a dark-adapted eye"
            }
        }
        for ((name, color) in CockpitSkyColors.marks) {
            val luminance = relativeLuminance(color)
            check(luminance <= CockpitMarkCeiling) {
                "Cockpit mark $name ${color.hex()} has luminance ${"%.3f".format(luminance)}, " +
                    "above the $CockpitMarkCeiling ceiling"
            }
        }
    }

    /**
     * Chart may not contain amber, per [ChartColorScheme]'s own rule that the accent
     * belongs to the runway.
     *
     * The exclusion is a hue-and-saturation window rather than a list of forbidden
     * hex values, so it catches a colour nobody thought to name. The bounds are
     * generous on purpose: Chart is full of warm paper tones and printed vermilion,
     * and neither is amber. What the window catches is a *saturated* yellow-orange
     * bright enough to read as the runway accent.
     */
    @Test
    fun `Chart contains no amber`() {
        for ((name, color) in ChartSkyColors.all) {
            val hue = hueDegrees(color)
            val saturation = saturation(color)
            val value = value(color)
            val isAmber = hue in AmberHueRange && saturation >= AmberMinSaturation && value >= AmberMinValue
            check(!isAmber) {
                "Chart $name ${color.hex()} is amber (hue ${"%.0f".format(hue)}°, " +
                    "saturation ${"%.2f".format(saturation)}, value ${"%.2f".format(value)}) — " +
                    "that accent belongs to the runway"
            }
        }
    }

    /** Every band must darken with altitude, which is the one thing the axis assumes. */
    @Test
    fun `the air is darker at the top of every band than at the bottom`() {
        for ((palette, colors) in palettes) {
            for ((bandName, band) in colors.bands) {
                val low = relativeLuminance(band.low)
                val high = relativeLuminance(band.high)
                check(high < low) {
                    "$palette $bandName: high ${band.high.hex()} (${"%.3f".format(high)}) is not " +
                        "darker than low ${band.low.hex()} (${"%.3f".format(low)})"
                }
            }
        }
    }

    private companion object {
        /** WCAG 2.x 1.4.11, non-text contrast: the bound for a graphical object. */
        const val NonTextContrast = 3.0

        /** Minimum separation between two ground states, in RGB distance normalised to 0..1. */
        const val MinGroundDistance = 0.10

        /** Minimum separation between two horizons. Looser than above — see that test. */
        const val MinHorizonDistance = 0.06

        /** Ceiling on the relative luminance of a Cockpit colour that covers area. */
        const val CockpitFillCeiling = 0.42

        /** Ceiling on the relative luminance of a Cockpit line, disc or small shape. */
        const val CockpitMarkCeiling = 0.58

        /** Yellow-orange, in HSV degrees. */
        val AmberHueRange = 35.0..65.0
        const val AmberMinSaturation = 0.35
        const val AmberMinValue = 0.45
    }
}

/** WCAG 2.x contrast ratio: `(Lighter + 0.05) / (Darker + 0.05)`, ranging 1..21. */
private fun contrastRatio(a: Color, b: Color): Double {
    val la = relativeLuminance(a)
    val lb = relativeLuminance(b)
    return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
}

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
    return sqrt((dr * dr + dg * dg + db * db) / 3.0)
}

/**
 * HSV hue in degrees, computed here rather than taken from `android.graphics.Color`
 * so this stays a plain JVM test with no Robolectric.
 */
private fun hueDegrees(color: Color): Double {
    val r = color.red.toDouble()
    val g = color.green.toDouble()
    val b = color.blue.toDouble()
    val maxChannel = maxOf(r, g, b)
    val delta = maxChannel - minOf(r, g, b)
    if (delta == 0.0) return 0.0
    val hue = when (maxChannel) {
        r -> 60.0 * (((g - b) / delta) % 6.0)
        g -> 60.0 * (((b - r) / delta) + 2.0)
        else -> 60.0 * (((r - g) / delta) + 4.0)
    }
    return if (hue < 0.0) hue + 360.0 else hue
}

private fun saturation(color: Color): Double {
    val maxChannel = maxOf(color.red, color.green, color.blue).toDouble()
    if (maxChannel == 0.0) return 0.0
    return (maxChannel - minOf(color.red, color.green, color.blue).toDouble()) / maxChannel
}

private fun value(color: Color): Double = maxOf(color.red, color.green, color.blue).toDouble()

private fun Color.hex(): String =
    "#%02X%02X%02X".format((red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt())
