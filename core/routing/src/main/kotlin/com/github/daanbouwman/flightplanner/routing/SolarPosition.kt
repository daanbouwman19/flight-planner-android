package com.github.daanbouwman.flightplanner.routing

import kotlin.math.cos
import kotlin.math.sin

/**
 * The Sun's geometric mean longitude, its apparent ecliptic longitude, and its
 * distance.
 *
 * [meanLongitudeDeg] is carried out rather than left to be recomputed, because
 * `SolarPositionTest` reconstructs the equation of time from it and the apparent
 * right ascension — and a test that recomputed `L0` from its own copy of the
 * polynomial would be checking itself.
 *
 * The Sun's ecliptic *latitude* is not returned. It never exceeds 1.2
 * arcseconds, which is a four-hundredth of what this scene can draw, so the
 * conversion to equatorial coordinates passes zero.
 */
internal data class SolarPosition(
    val meanLongitudeDeg: Double,
    val apparentLongitudeDeg: Double,
    val radiusVectorKm: Double,
)

/**
 * The Sun's apparent position, by the series published with the NOAA Solar
 * Calculator — Meeus chapter 25, low accuracy.
 *
 * Chosen over the higher-accuracy VSOP87 truncation for a reason that is about
 * verification rather than about precision: the calculator the tests are written
 * against *runs this arithmetic*, so agreement between the two is an identity
 * rather than a coincidence. The higher-accuracy route would buy an improvement
 * four orders of magnitude below one pixel, for hundreds of coefficients.
 *
 * Cross-checked against a structurally different formulation — the Blanco-Muriel
 * PSA algorithm (*Solar Energy* 70(5), 2001), which truncates its ecliptic
 * longitude differently and reaches the horizon by a different route — agreeing to
 * 0.0075° in elevation away from the zenith, against a scene in which one physical
 * pixel is about 0.13°.
 *
 * **No refraction**, and that is not an omission. The twilight thresholds this
 * feeds — 0°, −6°, −18° — are themselves *defined* on the geometric elevation of
 * the body's centre, so refracting first would make the app disagree with the
 * definition it is quoting. Refraction is also not a constant: the standard 0.57°
 * at the horizon assumes 10 °C and 1010 hPa, and the panel is already displaying
 * the temperature and pressure that would change it, so applying a standard value
 * beside a non-standard reading would assert a precision the scene does not have.
 */
internal fun solarPosition(t: Double): SolarPosition {
    // Meeus 25.2: geometric mean longitude, referred to the mean equinox of date.
    val meanLongitude = normaliseDeg(280.46646 + 36_000.76983 * t + 0.0003032 * t * t)
    // 25.3: mean anomaly.
    val meanAnomaly = 357.52911 + 35_999.05029 * t - 0.0001537 * t * t
    // 25.4: eccentricity of Earth's orbit.
    val eccentricity = 0.016708634 - 0.000042037 * t - 0.0000001267 * t * t

    val m = Math.toRadians(meanAnomaly)
    // The equation of the centre — the whole of the orbit's departure from a
    // circle, and the term an error in which shows up in the equation of time
    // long before it shows up in an elevation.
    val centre = (1.914602 - 0.004817 * t - 0.000014 * t * t) * sin(m) +
        (0.019993 - 0.000101 * t) * sin(2 * m) +
        0.000289 * sin(3 * m)

    val trueLongitude = meanLongitude + centre
    val trueAnomaly = Math.toRadians(meanAnomaly + centre)
    // 25.5: the radius vector, in astronomical units.
    val radiusAu = 1.000001018 * (1 - eccentricity * eccentricity) /
        (1 + eccentricity * cos(trueAnomaly))

    // 25.8: apparent longitude, corrected for nutation and aberration. Paired
    // with `apparentObliquityDeg`; see that function on why they travel together.
    val omega = Math.toRadians(nutationArgumentDeg(t))
    val apparentLongitude = trueLongitude - 0.00569 - 0.00478 * sin(omega)

    return SolarPosition(
        meanLongitudeDeg = meanLongitude,
        apparentLongitudeDeg = normaliseDeg(apparentLongitude),
        radiusVectorKm = radiusAu * AstronomicalUnitKm,
    )
}
