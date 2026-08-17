package com.github.daanbouwman.flightplanner.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.github.daanbouwman.flightplanner.model.FlightRules

/**
 * The container/on-container pair a flight-rules chip is painted with.
 *
 * Split out rather than stored as two flat colours so a call site cannot pick a
 * container from one category and a foreground from another — the pair travels
 * together or not at all, which is what keeps the contrast guarantee below true
 * in practice and not only on paper.
 */
@Immutable
data class FlightRulesColorPair(val container: Color, val onContainer: Color)

/**
 * The five flight-category colours, as a set.
 *
 * **These are deliberately outside the Material colour scheme and are never
 * derived from dynamic colour.** VFR green, MVFR blue, IFR red and LIFR magenta
 * are the colours every aviation weather chart uses; a pilot reads the colour
 * before the letters. Re-tinting them from the user's wallpaper would not be a
 * stylistic choice, it would make the app say something false — a magenta LIFR
 * station rendered green is a safety-shaped bug, not a theming bug. So they live
 * here, in their own [LocalFlightRulesColors], and the only thing that varies is
 * whether the light or the dark tone mapping is in effect.
 *
 * Every pair clears **4.5:1** (WCAG AA for normal text) in both variants. That is
 * not an eyeballed claim: `FlightRulesContrastTest` computes the relative
 * luminance ratio of all ten pairs and fails the build if any of them drops
 * below the threshold. If you retune a colour here, that test is the thing that
 * tells you whether you may.
 */
@Immutable
data class FlightRulesColors(
    val vfr: FlightRulesColorPair,
    val mvfr: FlightRulesColorPair,
    val ifr: FlightRulesColorPair,
    val lifr: FlightRulesColorPair,
    val unknown: FlightRulesColorPair,
) {
    operator fun get(rules: FlightRules): FlightRulesColorPair = when (rules) {
        FlightRules.VFR -> vfr
        FlightRules.MVFR -> mvfr
        FlightRules.IFR -> ifr
        FlightRules.LIFR -> lifr
        FlightRules.UNKNOWN -> unknown
    }

    /** All five pairs, for iteration in tests and in the theme gallery preview. */
    val all: List<Pair<FlightRules, FlightRulesColorPair>>
        get() = listOf(
            FlightRules.VFR to vfr,
            FlightRules.MVFR to mvfr,
            FlightRules.IFR to ifr,
            FlightRules.LIFR to lifr,
            FlightRules.UNKNOWN to unknown,
        )
}

/**
 * Light-theme mapping: a tone-85 container carrying tone-30 text.
 *
 * The tones come from a CIELAB ramp at a fixed hue per category, so the five
 * chips read as one family — the same lightness, differing only in hue, which is
 * what lets a row of them be scanned at a glance.
 *
 * The containers are deliberately a step **more saturated than the usual tone-90
 * Material container**, and that is a correction rather than a preference. At
 * tone 90 the five pastels compress toward white and stop being separable: the
 * first draft of this palette had IFR `#FFDAD7` and LIFR `#FFD8ED` sitting 0.05
 * apart in normalised RGB, which is two barely-different pinks standing for
 * *"below minimums"* and *"well below minimums"* — the single pair a pilot most
 * needs to tell apart at a glance. MVFR and UNKNOWN were nearly as close. Text
 * contrast was fine in every case; contrast against the *background* was never
 * the problem, contrast against *each other* was. `FlightRulesContrastTest`
 * enforces both, which is why these values are what they are.
 */
val LightFlightRulesColors: FlightRulesColors = FlightRulesColors(
    vfr = FlightRulesColorPair(container = Color(0xFF9CE8B0), onContainer = Color(0xFF005227)),
    mvfr = FlightRulesColorPair(container = Color(0xFFBBD9FF), onContainer = Color(0xFF004A75)),
    ifr = FlightRulesColorPair(container = Color(0xFFFFC5C0), onContainer = Color(0xFF8E1121)),
    lifr = FlightRulesColorPair(container = Color(0xFFE9B8F5), onContainer = Color(0xFF7F1D60)),
    unknown = FlightRulesColorPair(container = Color(0xFFC9CCD1), onContainer = Color(0xFF44474E)),
)

/**
 * Dark- and Cockpit-theme mapping: the same hues with container and foreground
 * tones swapped (tone 28 container, tone 90 text).
 *
 * Cockpit shares this set rather than getting an amber-shifted one of its own,
 * for the same reason the colours are not dynamic: the night theme may restyle
 * the app, but it may not restate the weather.
 */
val DarkFlightRulesColors: FlightRulesColors = FlightRulesColors(
    vfr = FlightRulesColorPair(container = Color(0xFF004D24), onContainer = Color(0xFF9CE8B0)),
    mvfr = FlightRulesColorPair(container = Color(0xFF00466E), onContainer = Color(0xFFBBD9FF)),
    ifr = FlightRulesColorPair(container = Color(0xFF88081D), onContainer = Color(0xFFFFC5C0)),
    lifr = FlightRulesColorPair(container = Color(0xFF7A175B), onContainer = Color(0xFFE9B8F5)),
    unknown = FlightRulesColorPair(container = Color(0xFF3F424A), onContainer = Color(0xFFC9CCD1)),
)

/**
 * The flight-rules palette in scope.
 *
 * `static` because it changes only when the theme changes — at which point the
 * whole tree recomposes anyway — so paying for read tracking on every chip would
 * buy nothing.
 */
val LocalFlightRulesColors: ProvidableCompositionLocal<FlightRulesColors> =
    staticCompositionLocalOf { LightFlightRulesColors }
