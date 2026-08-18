package com.github.daanbouwman.flightplanner.ui

import java.util.Locale

/**
 * Groups a figure the way a chart does, in a fixed locale.
 *
 * **A deliberate divergence from the platform default.** `%,d` in a string
 * resource formats with the *user's* locale, so under an Arabic locale a runway
 * reads `٣.٩٣٧` and a distance `٤٩٧` — correct for prose, and wrong here. These
 * are chart figures: runway lengths, distances and flight times are written in
 * Western digits on every aeronautical chart, flight plan and ATIS in the world,
 * including in countries whose everyday digits are not. Rendering them any other
 * way makes the card less readable to the one audience it is for.
 *
 * It also removes an inconsistency the app had already introduced by accident:
 * estimated time en route was formatted in a fixed locale, so `3:48` sat next to
 * `٤٩٧ NM` on the same card.
 *
 * The one place figures stay localised is the spoken content description, where
 * they are being *read aloud* rather than shown, and speech follows the language
 * it is spoken in.
 */
fun Int.asFigure(): String = String.format(Locale.ROOT, "%,d", this)
