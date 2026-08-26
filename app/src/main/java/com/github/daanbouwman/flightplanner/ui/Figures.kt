package com.github.daanbouwman.flightplanner.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import com.github.daanbouwman.flightplanner.settings.UnitSystem
import java.util.Locale
import kotlin.math.roundToInt

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

/**
 * The active [UnitSystem] for distance, length and speed figures.
 *
 * Provided once, from `MainActivity` alongside the resolved theme, so every
 * screen reads it rather than each one collecting `SettingsRepository` itself.
 * Defaults to [UnitSystem.AVIATION] — the app's own behaviour before this
 * existed — so a composable previewed without a provider still renders.
 */
val LocalUnitSystem = compositionLocalOf { UnitSystem.AVIATION }

/**
 * Display-only unit conversions.
 *
 * Deliberately **not** in `core/model/Units.kt`: that object's constants are
 * ported verbatim from Rust for route-generation parity, where precision is
 * load-bearing. These exist only to round a number for a label and have no
 * Rust precedent — the desktop app has no unit toggle either.
 */
private const val KM_PER_NM = 1.852
private const val M_PER_FT = 0.3048

/** A distance in nautical miles, converted to the active unit's magnitude. */
fun nmToDisplayDistance(nm: Int, unit: UnitSystem): Int =
    if (unit == UnitSystem.METRIC) (nm * KM_PER_NM).roundToInt() else nm

/** A length in feet, converted to the active unit's magnitude. */
fun ftToDisplayLength(ft: Int, unit: UnitSystem): Int =
    if (unit == UnitSystem.METRIC) (ft * M_PER_FT).roundToInt() else ft

/** A speed in knots, converted to the active unit's magnitude. 1 kt = 1 NM/h, so the factor is [KM_PER_NM]. */
fun ktToDisplaySpeed(kt: Int, unit: UnitSystem): Int =
    if (unit == UnitSystem.METRIC) (kt * KM_PER_NM).roundToInt() else kt

/**
 * The bare unit suffix, with no figure attached.
 *
 * `internal`, not `private`: [StatTile][com.github.daanbouwman.flightplanner.core.designsystem.components.StatTile]'s
 * `format` is a plain `(Int) -> String`, not `@Composable`, because it is
 * called every frame of a count-up from inside `StatSummaryStrip` — so a
 * distance tile there converts and captures this suffix once, outside the
 * lambda, rather than calling [distanceText] from within it.
 */
internal fun distanceUnitSuffix(unit: UnitSystem) = if (unit == UnitSystem.METRIC) "km" else "NM"
private fun lengthUnitSuffix(unit: UnitSystem) = if (unit == UnitSystem.METRIC) "m" else "ft"
private fun speedUnitSuffix(unit: UnitSystem) = if (unit == UnitSystem.METRIC) "km/h" else "kt"

/**
 * A distance, in the active unit — `"497 NM"` or `"920 km"`.
 *
 * Safe to call every frame on a `FlightMotion.rememberCountUp` target that is
 * still animating: the target stays in nautical miles regardless of the
 * active unit, and this converts at render time, so an in-flight count-up
 * scales smoothly rather than needing to be re-targeted when the unit changes.
 */
@Composable
fun distanceText(nm: Int): String {
    val unit = LocalUnitSystem.current
    return "${nmToDisplayDistance(nm, unit).asFigure()} ${distanceUnitSuffix(unit)}"
}

/** A runway length, elevation or similar, in the active unit — `"6,900 ft"` or `"2,103 m"`. */
@Composable
fun lengthText(ft: Int): String {
    val unit = LocalUnitSystem.current
    return "${ftToDisplayLength(ft, unit).asFigure()} ${lengthUnitSuffix(unit)}"
}

/** A cruise speed, in the active unit — `"300 kt"` or `"556 km/h"`. */
@Composable
fun speedText(kt: Int): String {
    val unit = LocalUnitSystem.current
    return "${ktToDisplaySpeed(kt, unit).asFigure()} ${speedUnitSuffix(unit)}"
}

/**
 * A runway's length and width sharing one unit suffix — `"6,000 × 150 ft"` or
 * `"1,829 × 46 m"` — rather than two calls to [lengthText] repeating it.
 */
@Composable
fun runwayDimensionsText(lengthFt: Int, widthFt: Int): String {
    val unit = LocalUnitSystem.current
    val length = ftToDisplayLength(lengthFt, unit).asFigure()
    val width = ftToDisplayLength(widthFt, unit).asFigure()
    return "$length × $width ${lengthUnitSuffix(unit)}"
}
