package com.github.daanbouwman.flightplanner.routing

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * The frame conversions the Sun and the Moon both pass through.
 *
 * **This file exists so that there is exactly one route to the horizon.** The Sun
 * could have reached it through the equation of time, which is how most solar
 * calculators are written; the Moon has no equation of time and must go via
 * sidereal time. Writing both would be the same spherical identity twice, and the
 * failure mode is not a wrong number — it is a sun and a moon drawn in the same
 * frame that disagree about where south is.
 *
 * Everything here is `Double`. That diverges from [GreatCircle]'s deliberate
 * `Float`, and the reasoning is the one [SurfaceWind] already set down in this
 * module: `Float` there buys register pressure in the sampling loop that dominates
 * route generation, and this runs twice per airport panel. It also matters
 * numerically in a way `SurfaceWind`'s does not — the lunar mean longitude reaches
 * about 10^7 degrees before reduction, and `Float`'s seven significant digits
 * would leave nothing of the fractional part that carries the answer.
 */

/** A position on the celestial sphere, in the frame both bodies pass through. */
internal data class Equatorial(val rightAscensionDeg: Double, val declinationDeg: Double)

/**
 * Unix seconds to Julian Day.
 *
 * The constant is the Julian Day of 1970-01-01T00:00Z. Written out rather than
 * derived because it is the one number in this file a reader can check against any
 * almanac in a single line.
 */
internal fun julianDay(epochSeconds: Long): Double = epochSeconds / 86_400.0 + 2_440_587.5

/**
 * Julian centuries from J2000.0.
 *
 * **There is no ΔT here and that is deliberate.** Meeus's *T* is in Terrestrial
 * Time and this is handed a UTC instant, so it is short by ΔT — about 69 s in
 * 2026. Measured cost of ignoring it: 0.0122° in the Moon's ecliptic longitude and
 * 0.0008° in the Sun's, both an order of magnitude below one physical pixel of the
 * scene this feeds. Modelling it means carrying a ΔT polynomial that itself needs
 * revising every few years — a standing maintenance obligation guarding an error
 * nothing can draw. A reader who knows Meeus will come looking for this, which is
 * why the omission is stated rather than left to be read as an oversight.
 */
internal fun julianCenturies(julianDay: Double): Double = (julianDay - 2_451_545.0) / 36_525.0

/**
 * The mean obliquity of the ecliptic, Meeus 22.2.
 *
 * 23° 26′ 21.448″ − 46.8150″·T − 0.00059″·T² + 0.001813″·T³, about 23.4358° in
 * 2026.
 */
internal fun meanObliquityDeg(t: Double): Double =
    23.0 + (26.0 + 21.448 / 60.0) / 60.0 -
        (46.8150 * t + 0.00059 * t * t - 0.001813 * t * t * t) / 3_600.0

/**
 * [meanObliquityDeg] plus the nutation term the apparent-longitude correction is
 * paired with.
 *
 * The two belong together and are never available separately: using the *mean*
 * obliquity with the *apparent* longitude leaves a systematic error of about 9
 * arcseconds in declination, which is the kind of mistake that survives every
 * sanity check because it is small, constant and in one direction.
 */
internal fun apparentObliquityDeg(t: Double): Double =
    meanObliquityDeg(t) + 0.00256 * cos(Math.toRadians(nutationArgumentDeg(t)))

/**
 * Ω, the longitude of the Moon's ascending node, Meeus 25.8.
 *
 * Shared by the solar apparent-longitude correction and by [apparentObliquityDeg],
 * because they are two halves of one adjustment.
 */
internal fun nutationArgumentDeg(t: Double): Double = 125.04 - 1_934.136 * t

/**
 * Greenwich mean sidereal time in degrees, Meeus 12.4.
 *
 * This is what both bodies take their hour angle from — see this file's own KDoc
 * for why that is the point of it living here rather than inside the Sun.
 */
internal fun greenwichMeanSiderealTimeDeg(julianDay: Double): Double {
    val t = julianCenturies(julianDay)
    return normaliseDeg(
        280.46061837 + 360.98564736629 * (julianDay - 2_451_545.0) +
            0.000387933 * t * t - (t * t * t) / 38_710_000.0,
    )
}

/**
 * Ecliptic to equatorial, Meeus 13.3 and 13.4.
 *
 * Written with `atan2` rather than `atan`, so the quadrant falls out of the
 * arithmetic instead of out of a correction every caller has to remember.
 */
internal fun equatorialFromEcliptic(
    longitudeDeg: Double,
    latitudeDeg: Double,
    obliquityDeg: Double,
): Equatorial {
    val lambda = Math.toRadians(longitudeDeg)
    val beta = Math.toRadians(latitudeDeg)
    val eps = Math.toRadians(obliquityDeg)

    val rightAscension = atan2(
        sin(lambda) * cos(eps) - tan(beta) * sin(eps),
        cos(lambda),
    )
    val declination = asin(sin(beta) * cos(eps) + cos(beta) * sin(eps) * sin(lambda))
    return Equatorial(
        rightAscensionDeg = normaliseDeg(Math.toDegrees(rightAscension)),
        declinationDeg = Math.toDegrees(declination),
    )
}

/**
 * The local hour angle: sidereal time, plus east longitude, minus right ascension,
 * reduced to −180..180.
 *
 * **Longitude is east-positive throughout**, matching the airport dataset and
 * [com.github.daanbouwman.flightplanner.model.Metar.longitude]. A western field
 * passes a negative number and nothing in this file flips a sign for it — which is
 * worth saying, because the other common convention is west-positive and the two
 * differ by a result that is wrong by twice the longitude rather than obviously
 * wrong.
 */
internal fun localHourAngleDeg(
    equatorial: Equatorial,
    longitudeDeg: Double,
    julianDay: Double,
): Double {
    val raw = greenwichMeanSiderealTimeDeg(julianDay) + longitudeDeg - equatorial.rightAscensionDeg
    return normaliseDeg(raw + 180.0) - 180.0
}

/**
 * Equatorial to horizontal, Meeus 13.5 and 13.6.
 *
 * The azimuth is turned to 0..360 from **true north, clockwise** rather than
 * Meeus's from-south convention, because every other bearing in this codebase is
 * from north and mixing the two is precisely the class of error [SurfaceWind]'s
 * KDoc is entirely about.
 *
 * **Always defined, at every latitude, on every date.** The elevation is `asin` of
 * a value the spherical identity confines to −1..1, which is why the polar fields
 * need no branch: what does not exist at PABR in December is a *rise time*, and
 * nothing here asks for one.
 */
internal fun horizontalFrom(
    equatorial: Equatorial,
    latitudeDeg: Double,
    hourAngleDeg: Double,
): CelestialBody {
    val phi = Math.toRadians(latitudeDeg)
    val decl = Math.toRadians(equatorial.declinationDeg)
    val h = Math.toRadians(hourAngleDeg)

    val elevation = asin(sin(phi) * sin(decl) + cos(phi) * cos(decl) * cos(h))
    // atan2 in this form measures azimuth from south, westward positive; the +180
    // is what puts it on the navigation convention.
    val azimuth = atan2(sin(h), cos(h) * sin(phi) - tan(decl) * cos(phi))

    return CelestialBody(
        elevationDeg = Math.toDegrees(elevation),
        azimuthDeg = normaliseDeg(Math.toDegrees(azimuth) + 180.0),
    )
}

/**
 * Lowers a geocentric elevation to what an observer standing on the surface sees.
 *
 * `h′ = h − asin(sin π · cos h)`, with `sin π = ` [EarthEquatorialRadiusKm] `/
 * distanceKm`. The scene's whole premise is a cross-section *at this field*, so a
 * geocentric Moon would be the right Moon for the wrong observer.
 *
 * Applied to both bodies through one function so that
 * [CelestialBody.elevationDeg] means one thing rather than two — though only one
 * of them can show it. Measured over January 2026 at EHAM: the Moon drops by up to
 * 1.0088°, about two lunar diameters, while the Sun drops by 0.0024°. The Sun's
 * term changes no pixel and is kept anyway, because a shared function cannot drift
 * apart and the alternative is a KDoc that has to disclaim which body it applies
 * to; `SolarPositionTest` pins it as non-zero and bounded so it is not code no test
 * can distinguish from its absence.
 *
 * First order and spherical: it ignores Earth's flattening and does not correct
 * azimuth. Against a rigorous Meeus chapter 40 reduction that costs 0.0090° in
 * altitude and 0.0164° in azimuth, both far below what the frame can draw.
 */
internal fun topocentricElevationDeg(geocentricElevationDeg: Double, distanceKm: Double): Double {
    val sinParallax = EarthEquatorialRadiusKm / distanceKm
    val h = Math.toRadians(geocentricElevationDeg)
    return geocentricElevationDeg - Math.toDegrees(asin(sinParallax * cos(h)))
}

/**
 * Earth's equatorial radius, the numerator of the horizontal parallax.
 *
 * Meeus's value. The WGS-84 figure of 6378.137 differs by 3 m, and by nothing this
 * can draw.
 */
internal const val EarthEquatorialRadiusKm: Double = 6_378.14

/**
 * The astronomical unit in kilometres, IAU 2012.
 *
 * The Sun's radius vector arrives in AU and the phase-angle formula needs both
 * distances in the same unit. Getting this wrong by a factor of 149 million is the
 * mistake `the sun's parallax is real, one-signed and invisible` exists to catch.
 */
internal const val AstronomicalUnitKm: Double = 149_597_870.7

/**
 * Reduces an angle to 0..360.
 *
 * Kept here rather than repeated per file because the mean longitudes arrive near
 * 10^7 degrees and every one of them needs it.
 */
internal fun normaliseDeg(deg: Double): Double {
    val reduced = deg % 360.0
    return if (reduced < 0.0) reduced + 360.0 else reduced
}
