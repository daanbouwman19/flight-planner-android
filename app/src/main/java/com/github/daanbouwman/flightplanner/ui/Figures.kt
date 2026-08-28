package com.github.daanbouwman.flightplanner.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.res.stringResource
import com.github.daanbouwman.flightplanner.R
import com.github.daanbouwman.flightplanner.model.AltimeterConvention
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

private const val METRES_PER_STATUTE_MILE = 1_609.344

/**
 * A reported visibility, in the convention the unit setting asks for.
 *
 * **This is not the suffix swap the other figures here are**, which is why it is
 * a function of its own rather than another `xToDisplayY`. The world reports
 * visibility in two genuinely different ways: US stations in statute miles and
 * fractions of one (`1/2SM`, `2 1/2SM`), and everyone else in metres, where
 * `9999` is the code for *ten kilometres or more* rather than a measurement of
 * 9,999 m. [MetarParser][com.github.daanbouwman.flightplanner.model.MetarParser]
 * normalises both to statute miles so that one comparison decides the flight
 * category; this puts them back into the reader's own convention.
 *
 * [orGreater] is the parser's `visibilityIsOrGreater` — set by `10+SM` and by an
 * ICAO `9999`, both of which mean "this or better". Dropping it would turn a
 * floor into a measurement.
 */
@Composable
fun visibilityText(miles: Double, orGreater: Boolean): String {
    val prefix = if (orGreater) "≥" else ""
    return when (LocalUnitSystem.current) {
        UnitSystem.AVIATION -> "$prefix${statuteMilesFigure(miles)} SM"
        UnitSystem.METRIC -> "$prefix${metresFigure(miles * METRES_PER_STATUTE_MILE)}"
    }
}

/**
 * Statute miles the way a METAR writes them: `1/2`, `2 1/2`, `10`.
 *
 * A US station reports visibility below three miles in sixteenths, and its raw
 * text says `1/2SM`. Rendering that as `0.5 SM` is a conversion the pilot then
 * has to undo — the same objection the altimeter has to being forced into one
 * unit — so a value that lands on a sixteenth comes back out as the fraction the
 * station sent, reduced. A value that does not land on one has been converted
 * from metres and is not a fraction anybody reported, so it prints as a decimal.
 *
 * **Above three miles a whole number is printed only when the value genuinely is
 * one.** Rounding to the nearest mile here looks tidier and is wrong at exactly
 * the place it matters: `9000` m is 5.59 SM and rounded to `6 SM`, and a 5.4 SM
 * report would have rounded to `5 SM` — the MVFR/VFR visibility boundary, so a
 * VFR field would have read as sitting on the edge of marginal. Caught on a
 * device against a live `VVPQ … 9000` and not by any test, which is the shape of
 * defect this screen keeps producing: a figure that is only wrong near a
 * threshold.
 */
internal fun statuteMilesFigure(miles: Double): String {
    if (miles >= 3.0) {
        return if (miles % 1.0 < 0.05) {
            String.format(Locale.ROOT, "%,.0f", miles)
        } else {
            String.format(Locale.ROOT, "%,.1f", miles)
        }
    }

    val sixteenths = (miles * 16.0).roundToInt()
    val exact = kotlin.math.abs(miles * 16.0 - sixteenths) < 0.01
    if (!exact || sixteenths <= 0) return String.format(Locale.ROOT, "%.1f", miles)

    val whole = sixteenths / 16
    var numerator = sixteenths % 16
    if (numerator == 0) return whole.toString()

    var denominator = 16
    while (numerator % 2 == 0) {
        numerator /= 2
        denominator /= 2
    }
    return if (whole == 0) "$numerator/$denominator" else "$whole $numerator/$denominator"
}

/**
 * Metres below five kilometres, kilometres above — the ICAO convention.
 *
 * Rounded to the step the report itself resolves to (100 m below 5 km, whole
 * kilometres above) rather than to the full precision of the conversion. A US
 * `2SM` is 3,218.688 m, and printing that would claim a resolution no station
 * ever transmits.
 */
internal fun metresFigure(metres: Double): String = if (metres < 5_000.0) {
    "${((metres / 100.0).roundToInt() * 100).asFigure()} m"
} else {
    "${(metres / 1_000.0).roundToInt().asFigure()} km"
}

/**
 * An altimeter setting, in **the station's own convention** rather than the
 * reader's.
 *
 * The one figure in the app that deliberately ignores [LocalUnitSystem], and the
 * reasoning is the opposite of everywhere else. An altimeter setting is not a
 * quantity to compare — it is a number dialled into a subscale, and the value
 * that gets dialled is the one the station transmitted. Showing a European
 * field's `Q1008` as `29.77 inHg` because the reader picked aviation units is a
 * conversion they would have to undo before flying, and an opportunity to
 * mis-set the altimeter that the report itself never offered.
 *
 * So `A` prints as inches and `Q` prints as hectopascals, and
 * [AltimeterConvention][com.github.daanbouwman.flightplanner.model.AltimeterConvention]
 * — parsed off the raw text and carried through the cache — is what decides.
 * Returns `null` when the report carried no altimeter group at all.
 */
fun altimeterText(
    convention: AltimeterConvention?,
    inHg: Double?,
    hectopascals: Double?,
): String? = when (convention) {
    AltimeterConvention.INCHES_MERCURY -> inHg?.let { String.format(Locale.ROOT, "%.2f inHg", it) }
    AltimeterConvention.HECTOPASCALS -> hectopascals?.let { String.format(Locale.ROOT, "%.0f hPa", it) }
    null -> null
}

/**
 * Which of the three things a METAR's wind group can say.
 *
 * Split out from the text so it can be asserted: [windText] is `@Composable` and
 * reaches for string resources, which puts it out of range of this module's JVM
 * tests, and the classification is where the mistakes are. The formatting is not —
 * see `WindFigureTest`, and [statuteMilesFigure] for the same split.
 */
internal enum class WindReading {
    /** `00000KT`. */
    CALM,

    /** `VRB05KT` — a speed with no settled direction. */
    VARIABLE,

    /** A speed the station reported without a direction, and not marked variable. */
    SPEED_ONLY,

    /** `27008KT` — the ordinary case. */
    BEARING,
}

/**
 * What the wind group is saying, from the three fields the parser fills.
 *
 * **A direction of `000°` is not a bearing.** It is the code for *no wind*, and it
 * arrives as a real `0` rather than as a null, so the speed has to be tested first
 * or a calm field prints `0° 0 kt` — which reads as a wind out of the north.
 *
 * `VARIABLE` and `SPEED_ONLY` both have a null direction and are still different
 * readings. A variable wind is a positive statement about the field, and a
 * consequential one: it is a crosswind on every runway. A speed with no direction
 * and no `VRB` is a report that simply did not say.
 */
internal fun windReading(directionDeg: Int?, variable: Boolean, speedKt: Int): WindReading = when {
    speedKt == 0 -> WindReading.CALM
    directionDeg != null -> WindReading.BEARING
    variable -> WindReading.VARIABLE
    else -> WindReading.SPEED_ONLY
}

/**
 * The wind, as the weather panel's `WIND` chip states it.
 *
 * Knots throughout, and deliberately not routed through [speedText]: every wind
 * group in every METAR in the world is transmitted in knots (or converted to them
 * by the parser), and it is the figure a pilot compares against an aircraft's
 * crosswind limit — which is also published in knots. Converting it to km/h for a
 * reader who picked metric would hand them a number no document they own uses.
 *
 * [speedKt] is taken unwrapped, so the caller's null check is the only one and
 * there is no unreachable branch here.
 */
@Composable
fun windText(directionDeg: Int?, variable: Boolean, speedKt: Int, gustKt: Int?): String {
    val reading = windReading(directionDeg, variable, speedKt)
    if (reading == WindReading.CALM) return stringResource(R.string.weather_wind_calm)

    val magnitude = stringResource(
        R.string.weather_wind_speed,
        "$speedKt${gustKt?.let { "G$it" }.orEmpty()}",
    )
    val from = when (reading) {
        WindReading.BEARING -> "$directionDeg°"
        WindReading.VARIABLE -> stringResource(R.string.weather_wind_variable)
        else -> return magnitude
    }
    return "$from $magnitude"
}

/**
 * Temperature over dewpoint, as the report states the pair.
 *
 * **Rounded, not truncated**, and that is the whole reason this is a function
 * rather than a string template. `Double.toInt()` truncates toward zero, and
 * `MetarSupplement.temperatureC` carries *tenths* where NOAA's `T` remark group
 * decoded them — so a field at −0.4 °C over a dewpoint of −0.9 °C printed
 * `0°/0°C`. The minus sign vanishes, and a below-freezing field reads as
 * at-or-above freezing on the one figure a pilot checks for airframe icing and
 * for frost on the wings before the first flight of the day. Truncation is wrong
 * on both sides of zero; it is only *dangerous* on this one.
 *
 * Celsius, unconditionally. [UnitSystem] has no Fahrenheit option, and adding one
 * to convert a figure that every aviation report in the world states in Celsius
 * would be the tail wagging the dog.
 *
 * A fixed locale, like every other chart figure here — see [asFigure].
 */
internal fun temperatureText(temperatureC: Double, dewpointC: Double): String =
    String.format(Locale.ROOT, "%d°/%d°C", temperatureC.roundToInt(), dewpointC.roundToInt())
