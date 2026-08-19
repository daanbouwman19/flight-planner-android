@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.github.daanbouwman.flightplanner.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDirection

/**
 * The OpenType feature tag for tabular (fixed-width) figures.
 *
 * `tnum` makes every digit occupy the same advance width. In prose that is a
 * mild regression — proportional figures fit the rhythm of a sentence better —
 * but in a column of distances it is the difference between a table and a mess,
 * and it stops a counting-up value from making the layout twitch on every frame
 * as `1` gives way to `8`.
 */
const val TabularFigures: String = "tnum"

/** Returns this style with tabular figures switched on. */
fun TextStyle.withTabularFigures(): TextStyle = copy(fontFeatureSettings = TabularFigures)

/**
 * Returns this style set the way a chart sets a figure: tabular, and **laid out
 * left to right whatever the app's language is**.
 *
 * The direction is the part that is easy to miss and impossible to unsee. Under
 * an RTL locale the bidirectional algorithm reorders a run of neutral characters
 * in an RTL paragraph, so a perfectly correct string arrives on screen backwards:
 * `1,308 NM` renders as `NM 1,308`, `25.9089, 54.5394` as `54.5394 ,25.9089`, and
 * a runway line as `ft · Hard 175 × 8,345 · 127° · 12`. Every one of those is a
 * *chart figure* — a runway ident, a heading, a distance, a coordinate — and a
 * chart figure reads left to right on every aeronautical publication in the
 * world, including in countries that read right to left.
 *
 * This is the same rule the app already applies to digits (`asFigure` formats in
 * a fixed locale so a distance never becomes Arabic-Indic), carried through to
 * the one thing that rule missed: the order the fields come out in.
 *
 * **It is not a substitute for [withTabularFigures] on prose.** Apply it only to
 * a string that is entirely a figure. A sentence with a number in it is prose,
 * and prose follows the language it is written in.
 */
fun TextStyle.asChartFigure(): TextStyle = copy(
    fontFeatureSettings = TabularFigures,
    textDirection = TextDirection.Ltr,
)

private val Base = Typography()

/**
 * The app's type scale.
 *
 * Two decisions are encoded here, and both are deliberate enough to be worth
 * reading before changing anything.
 *
 * ### Emphasized styles occupy the ordinary slots
 *
 * Material 3 Expressive ships a second, heavier cut of every style
 * (`displayLargeEmphasized`, `titleMediumEmphasized`, …). Rather than asking
 * every screen to remember which of the two to reach for, the slots the design
 * actually wants emphasized are *filled* with the emphasized cut:
 *
 * | Slot | Cut | Why |
 * | --- | --- | --- |
 * | `display*` | emphasized | Hero numerics — total distance, the count-ups |
 * | `headlineLarge`, `headlineMedium` | emphasized | Screen and section titles |
 * | `titleMedium` | emphasized | Route card headline, the `EHAM → KJFK` line |
 * | `labelLarge` | emphasized | Buttons, chips, the flight-rules badge |
 * | everything else | regular | Prose and secondary text |
 *
 * The `*Emphasized` slots keep their own values too, so a screen that genuinely
 * needs a heavier `bodyLarge` can still ask for one.
 *
 * ### Which styles carry `tnum`
 *
 * **Carry it:** every `display`, `headline`, `title` and `label` style, regular
 * and emphasized alike. Those are the styles numbers actually appear in — ICAO
 * codes, distances, runway lengths, estimated times, dates, counters — and none
 * of them is ever used for a paragraph.
 *
 * **Do not carry it:** `bodyLarge`, `bodyMedium`, `bodySmall` and their
 * emphasized twins. Those are prose: empty-state copy, error messages, METAR
 * decoding notes. Proportional figures read better mid-sentence. If a body-sized
 * string does need aligned digits, call [withTabularFigures] on it at the site
 * that knows.
 */
val FlightTypography: Typography = Base.copy(
    displayLarge = Base.displayLargeEmphasized.withTabularFigures(),
    displayLargeEmphasized = Base.displayLargeEmphasized.withTabularFigures(),
    displayMedium = Base.displayMediumEmphasized.withTabularFigures(),
    displayMediumEmphasized = Base.displayMediumEmphasized.withTabularFigures(),
    displaySmall = Base.displaySmallEmphasized.withTabularFigures(),
    displaySmallEmphasized = Base.displaySmallEmphasized.withTabularFigures(),
    headlineLarge = Base.headlineLargeEmphasized.withTabularFigures(),
    headlineLargeEmphasized = Base.headlineLargeEmphasized.withTabularFigures(),
    headlineMedium = Base.headlineMediumEmphasized.withTabularFigures(),
    headlineMediumEmphasized = Base.headlineMediumEmphasized.withTabularFigures(),
    headlineSmall = Base.headlineSmall.withTabularFigures(),
    headlineSmallEmphasized = Base.headlineSmallEmphasized.withTabularFigures(),
    titleLarge = Base.titleLarge.withTabularFigures(),
    titleLargeEmphasized = Base.titleLargeEmphasized.withTabularFigures(),
    titleMedium = Base.titleMediumEmphasized.withTabularFigures(),
    titleMediumEmphasized = Base.titleMediumEmphasized.withTabularFigures(),
    titleSmall = Base.titleSmall.withTabularFigures(),
    titleSmallEmphasized = Base.titleSmallEmphasized.withTabularFigures(),
    bodyLarge = Base.bodyLarge,
    bodyLargeEmphasized = Base.bodyLargeEmphasized,
    bodyMedium = Base.bodyMedium,
    bodyMediumEmphasized = Base.bodyMediumEmphasized,
    bodySmall = Base.bodySmall,
    bodySmallEmphasized = Base.bodySmallEmphasized,
    labelLarge = Base.labelLargeEmphasized.withTabularFigures(),
    labelLargeEmphasized = Base.labelLargeEmphasized.withTabularFigures(),
    labelMedium = Base.labelMedium.withTabularFigures(),
    labelMediumEmphasized = Base.labelMediumEmphasized.withTabularFigures(),
    labelSmall = Base.labelSmall.withTabularFigures(),
    labelSmallEmphasized = Base.labelSmallEmphasized.withTabularFigures(),
)
