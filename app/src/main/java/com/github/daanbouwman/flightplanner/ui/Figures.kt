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

/**
 * A heading, the way a chart writes one: three digits and a degree sign, `093°`.
 *
 * Padded to three digits for the same reason a runway is `09` and never `9` —
 * headings are read as a fixed-width field, and `93°` next to `291°` costs the
 * eye a moment that a leading zero does not. Fixed-locale for the reason
 * [asFigure] is.
 */
fun Int.asBearing(): String = String.format(Locale.ROOT, "%03d°", this)

/**
 * A coordinate pair at the precision the desktop app copies, `52.3086, 4.7639`.
 *
 * Four decimals is about 11 m, which is finer than an airport reference point is
 * published to and coarse enough to stay readable. Ported from
 * `render_airport_elevation_with_map_link` so a coordinate copied here and one
 * copied there are the same string.
 */
fun coordinates(latitude: Double, longitude: Double): String =
    String.format(Locale.ROOT, "%.4f, %.4f", latitude, longitude)
