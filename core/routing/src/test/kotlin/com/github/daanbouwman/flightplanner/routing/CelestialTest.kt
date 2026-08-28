package com.github.daanbouwman.flightplanner.routing

import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import kotlin.math.abs
import kotlin.test.Test

private fun utc(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
    java.time.LocalDateTime.of(year, month, day, hour, minute)
        .toInstant(java.time.ZoneOffset.UTC)
        .epochSecond

/** Stations the plan names, with the coordinates the dataset publishes. */
private const val EHAM_LAT = 52.3086
private const val EHAM_LON = 4.7639
private const val PABR_LAT = 71.2854
private const val PABR_LON = -156.7660

/**
 * The Sun, against the NOAA Solar Calculator and against closed forms.
 *
 * Every numeric assertion here is either a value from the reference algorithm or a
 * figure derivable from spherical geometry in one line — never a value read off
 * this implementation and pinned. The tolerances are set by what the drawing can
 * show: across roughly 200 dp of sky at 3.5× density, one physical pixel is about
 * 0.13° of elevation, so 0.02° is a seventh of a pixel.
 */
class SolarPositionTest {

    @Test
    fun `the declination reaches the obliquity at the solstices`() {
        // Derivable rather than looked up: the IAU mean obliquity
        // 23.4392911 - 0.0130042*T at T = 0.265 (mid-2026) is 23.4359, plus the
        // +0.00256*cos(omega) apparent term gives 23.4383. This is the single check
        // that the equation of centre, the obliquity polynomial and the
        // ecliptic-to-equatorial rotation are all consistent — none of the three
        // can be wrong on its own and still land here.
        var maximum = 0.0
        var hour = 0L
        val end = utc(2027, 1, 1, 0, 0)
        var instant = utc(2026, 1, 1, 0, 0)
        while (instant < end) {
            val t = julianCenturies(julianDay(instant))
            val equatorial = equatorialFromEcliptic(
                solarPosition(t).apparentLongitudeDeg, 0.0, apparentObliquityDeg(t),
            )
            maximum = maxOf(maximum, abs(equatorial.declinationDeg))
            instant += 3_600L
            hour++
        }

        maximum shouldBe (23.438 plusOrMinus 0.010)
    }

    @Test
    fun `the declination crosses zero at the published equinoxes`() {
        // Published 2026 equinox instants. The tolerance is derived rather than
        // chosen: the NOAA series' apparent longitude is good to about 0.01 deg and
        // the Sun's longitude advances 0.0416 deg per hour, so 0.01 deg *is* 14
        // minutes of time. Asserting tighter would assert a precision the series
        // does not have.
        fun declinationAt(instant: Long): Double {
            val t = julianCenturies(julianDay(instant))
            return equatorialFromEcliptic(
                solarPosition(t).apparentLongitudeDeg, 0.0, apparentObliquityDeg(t),
            ).declinationDeg
        }

        val march = utc(2026, 3, 20, 14, 46)
        declinationAt(march - 20 * 60).shouldBeLessThan(0.0)
        declinationAt(march + 20 * 60).shouldBeGreaterThan(0.0)

        val september = utc(2026, 9, 23, 0, 5)
        declinationAt(september - 20 * 60).shouldBeGreaterThan(0.0)
        declinationAt(september + 20 * 60).shouldBeLessThan(0.0)
    }

    @Test
    fun `the equation of time reaches its published extremes on the published dates`() {
        // Reconstructed here rather than exposed in production, because nothing
        // draws it — but it is the sharpest external check the solar series has. It
        // is a *difference* of two large angles, so a 0.01 deg error in the equation
        // of centre or the obliquity that is invisible in an elevation shows up in
        // it directly, and its extremes are printed in every almanac: about -14.2
        // minutes around 11 February and +16.4 minutes around 3 November.
        fun equationOfTimeMinutes(instant: Long): Double {
            val t = julianCenturies(julianDay(instant))
            val sun = solarPosition(t)
            val rightAscension = equatorialFromEcliptic(
                sun.apparentLongitudeDeg, 0.0, apparentObliquityDeg(t),
            ).rightAscensionDeg
            val difference = normaliseDeg(sun.meanLongitudeDeg - rightAscension + 180.0) - 180.0
            return 4.0 * difference
        }

        var minimum = Double.MAX_VALUE
        var minimumDay = 0
        var maximum = -Double.MAX_VALUE
        var maximumDay = 0
        var day = 0
        while (day < 365) {
            val value = equationOfTimeMinutes(utc(2026, 1, 1, 12, 0) + day * 86_400L)
            if (value < minimum) { minimum = value; minimumDay = day }
            if (value > maximum) { maximum = value; maximumDay = day }
            day++
        }

        minimum shouldBe (-14.23 plusOrMinus 0.30)
        maximum shouldBe (16.49 plusOrMinus 0.30)
        // 11 February is day 41 of 2026; 3 November is day 306.
        (abs(minimumDay - 41) < 3) shouldBe true
        (abs(maximumDay - 306) < 3) shouldBe true
    }

    @Test
    fun `the sun over Schiphol stands where the NOAA calculator puts it`() {
        val state = Celestial.at(EHAM_LAT, EHAM_LON, utc(2026, 6, 21, 12, 0))

        // NOAA's own published algorithm gives 60.9419 / 188.1575; the
        // Blanco-Muriel PSA algorithm — a structurally different truncation
        // reaching the horizon by a different route — gives 60.9384 / 188.1573.
        state.sun.elevationDeg shouldBe (60.942 plusOrMinus 0.02)
        state.sun.azimuthDeg shouldBe (188.152 plusOrMinus 0.05)
    }

    @Test
    fun `a western longitude is negative, and the sun still stands where it should`() {
        // KJFK, five minutes from local apparent noon two hours after the March
        // equinox — so the elevation must be near 90 - lat + declination =
        // 90 - 40.6398 + 0.037 = 49.397. This is the case that catches an
        // east/west sign flip, which produces an answer wrong by twice the
        // longitude rather than obviously wrong.
        val state = Celestial.at(40.6398, -73.7789, utc(2026, 3, 20, 17, 0))

        state.sun.elevationDeg shouldBe (49.395 plusOrMinus 0.02)
        state.sun.azimuthDeg shouldBe (179.045 plusOrMinus 0.05)
    }

    @Test
    fun `a southern-hemisphere noon sun stands in the north`() {
        // Santiago at the September equinox. The claim worth having is the
        // azimuth: 16 deg is north of east-west, and a hemisphere sign error would
        // put it near 164.
        val state = Celestial.at(-33.393, -70.7858, utc(2026, 9, 23, 16, 0))

        state.sun.elevationDeg shouldBe (55.832 plusOrMinus 0.02)
        state.sun.azimuthDeg shouldBe (15.93 plusOrMinus 0.05)
    }

    @Test
    fun `the sun does not set at Barrow on the June solstice`() {
        // Closed form, needing no table: at hour angle 180 the elevation is
        // lat + declination - 90 = 71.2854 + 23.4381 - 90 = 4.7235.
        var minimum = Double.MAX_VALUE
        var minute = 0
        while (minute < 24 * 60) {
            val state = Celestial.at(PABR_LAT, PABR_LON, utc(2026, 6, 21, 0, 0) + minute * 60L)
            state.sun.isUp shouldBe true
            minimum = minOf(minimum, state.sun.elevationDeg)
            minute++
        }

        minimum shouldBe (4.723 plusOrMinus 0.015)
    }

    @Test
    fun `the sun does not rise at Barrow on the December solstice`() {
        // Closed form: at hour angle 0 with declination -23.4381, the elevation is
        // 90 - 71.2854 - 23.4381 = -4.7235. That this is the June figure mirrored
        // to three decimals is itself the check — the two are the same geometry
        // with the declination reversed, and an error in the hour-angle convention
        // would break the symmetry.
        var maximum = -Double.MAX_VALUE
        var minute = 0
        while (minute < 24 * 60) {
            val state = Celestial.at(PABR_LAT, PABR_LON, utc(2026, 12, 21, 0, 0) + minute * 60L)
            state.sun.isUp shouldBe false
            maximum = maxOf(maximum, state.sun.elevationDeg)
            minute++
        }

        maximum shouldBe (-4.723 plusOrMinus 0.015)
    }

    @Test
    fun `Kangerlussuaq clears the Arctic Circle and Eagle does not`() {
        // The *pair* is the assertion: the polar cases are not one case. BGSF sits
        // 0.45 deg inside the circle and PAEG 1.79 deg outside it, so they must
        // come out on opposite sides of zero. Closed form again, lat + decl - 90.
        var bgsfMinimum = Double.MAX_VALUE
        var paegMinimum = Double.MAX_VALUE
        var minute = 0
        while (minute < 24 * 60) {
            val instant = utc(2026, 6, 21, 0, 0) + minute * 60L
            bgsfMinimum = minOf(bgsfMinimum, Celestial.at(67.0122, -50.7116, instant).sun.elevationDeg)
            paegMinimum = minOf(paegMinimum, Celestial.at(64.7764, -141.1513, instant).sun.elevationDeg)
            minute++
        }

        bgsfMinimum shouldBe (0.450 plusOrMinus 0.015)
        bgsfMinimum.shouldBeGreaterThan(0.0)
        paegMinimum shouldBe (-1.785 plusOrMinus 0.015)
        paegMinimum.shouldBeLessThan(0.0)
    }

    @Test
    fun `a polar year needs no crossing time and takes no branch`() {
        // The test that encodes the design claim: elevation is asin of a value the
        // spherical identity confines to -1..1 at every latitude, so there is
        // nothing for a branch to guard. It fails only if someone introduces a
        // special case for the poles — which is exactly the change that must not
        // be made.
        var hour = 0
        while (hour < 366 * 24) {
            val instant = utc(2026, 1, 1, 0, 0) + hour * 3_600L
            for (latitude in listOf(PABR_LAT, 89.9, -89.9)) {
                val state = Celestial.at(latitude, PABR_LON, instant)
                for (body in listOf(state.sun, state.moon)) {
                    body.elevationDeg.isFinite() shouldBe true
                    (body.elevationDeg in -90.0..90.0) shouldBe true
                    (body.azimuthDeg >= 0.0 && body.azimuthDeg < 360.0) shouldBe true
                }
            }
            hour += 7
        }
    }

    @Test
    fun `the sun passes close to the zenith at the equator at the equinox`() {
        val state = Celestial.at(0.0, 0.0, utc(2026, 3, 20, 12, 0))

        state.sun.elevationDeg shouldBe (88.141 plusOrMinus 0.02)
        // The azimuth is deliberately not asserted. Near the zenith every azimuth
        // converges, and the three formulations that agree to 0.025 deg everywhere
        // else spread by 0.42 deg here. That is geometry, not disagreement, and a
        // test asserting it would be a test of nothing.
    }

    @Test
    fun `the sun's parallax is real, one-signed and invisible`() {
        // The Sun's mean equatorial horizontal parallax is 8.794 arcsec =
        // 0.002443 deg, and the correction is parallax * cos(h), so it is bounded
        // by that and vanishes at the zenith. Too small to draw; the test exists
        // for the two mistakes that are not small — a sign flip, and passing the
        // radius vector in AU rather than kilometres, which would be a factor of
        // 149 million out.
        var largest = 0.0
        var hour = 0
        while (hour < 365 * 24) {
            val instant = utc(2026, 1, 1, 0, 0) + hour * 3_600L
            val t = julianCenturies(julianDay(instant))
            val sun = solarPosition(t)
            val equatorial = equatorialFromEcliptic(sun.apparentLongitudeDeg, 0.0, apparentObliquityDeg(t))
            val geocentric = horizontalFrom(
                equatorial, EHAM_LAT, localHourAngleDeg(equatorial, EHAM_LON, julianDay(instant)),
            ).elevationDeg
            if (geocentric > 0.0) {
                val difference = geocentric - topocentricElevationDeg(geocentric, sun.radiusVectorKm)
                difference.shouldBeGreaterThan(0.0)
                largest = maxOf(largest, difference)
            }
            hour += 5
        }

        largest.shouldBeLessThan(0.0025)
        largest.shouldBeGreaterThan(0.0020)
    }
}

/**
 * The Moon, against Meeus's own worked examples.
 *
 * The first two tests are why this file carries all 120 rows of tables 47.A and
 * 47.B rather than an abridgement: they assert Meeus's printed sums **exactly**, to
 * the unit, so one mis-keyed digit anywhere in the table fails. An abridged table
 * could only be checked to a tolerance, and a tolerance wide enough to absorb the
 * truncation is wide enough to hide a typo.
 */
class LunarPositionTest {

    /** Meeus Example 47.a: 1992 April 12.0 TD. */
    private val exampleT = julianCenturies(2_448_724.5)

    @Test
    fun `the series reproduces Meeus's own worked example exactly`() {
        val moon = lunarPosition(exampleT)

        // Printed in Astronomical Algorithms, 2nd ed., Example 47.a:
        // lambda = 133.162655, beta = -3.229126, delta = 368409.7 km.
        moon.longitudeDeg shouldBe (133.162655 plusOrMinus 0.000001)
        moon.latitudeDeg shouldBe (-3.229126 plusOrMinus 0.000001)
        moon.distanceKm shouldBe (368_409.7 plusOrMinus 0.05)
    }

    @Test
    fun `the mean arguments match the example's printed intermediates`() {
        // Not redundant with the test above: a compensating pair of errors in two
        // arguments could still land on the right longitude. These are the printed
        // intermediates, and they pin each polynomial separately.
        val t = exampleT
        t shouldBe (-0.077221081451 plusOrMinus 1e-12)

        val meanLongitude = normaliseDeg(
            218.3164477 + 481_267.88123421 * t - 0.0015786 * t * t +
                t * t * t / 538_841.0 - t * t * t * t / 65_194_000.0,
        )
        meanLongitude shouldBe (134.290182 plusOrMinus 0.000001)
    }

    @Test
    fun `the illuminated fraction matches Meeus's worked example`() {
        // Meeus Example 48.a, same instant: k = 0.6786, i = 69.0756, psi = 110.7929.
        val sun = solarPosition(exampleT)
        val phase = lunarPhase(lunarPosition(exampleT), sun.apparentLongitudeDeg, sun.radiusVectorKm)

        phase.illuminatedFraction shouldBe (0.6786 plusOrMinus 0.0005)
        phase.elongationDeg shouldBe (110.7929 plusOrMinus 0.02)
    }

    @Test
    fun `the full moon lands where the almanac puts it`() {
        // The strongest external anchor the lunar code has: a published instant
        // that depends on the Sun's position, the Moon's position and the phase
        // arithmetic all at once. Published full moon for 3 January 2026:
        // 10:02-10:03 UTC.
        var previous = 0.0
        var crossing = 0L
        var minute = 0
        val start = utc(2026, 1, 3, 6, 0)
        while (minute < 8 * 60) {
            val instant = start + minute * 60L
            val t = julianCenturies(julianDay(instant))
            val sun = solarPosition(t)
            val elongation = normaliseDeg(lunarPosition(t).longitudeDeg - sun.apparentLongitudeDeg)
            if (previous in 1.0..180.0 && elongation > 180.0) {
                crossing = instant
                break
            }
            previous = elongation
            minute++
        }

        // Tolerance derived: the Moon's elongation advances 0.0085 deg/minute, so
        // the position error is worth a few minutes.
        val published = utc(2026, 1, 3, 10, 2)
        (abs(crossing - published) < 600L) shouldBe true
    }

    @Test
    fun `waxing marks the lit limb and flips at full`() {
        // The assertion is that a boolean derived from a modular subtraction has
        // not been written with the comparison the wrong way round — the plausible
        // mistake, and one that would leave every crescent lit on the wrong side.
        fun phaseAt(instant: Long): LunarPhase {
            val t = julianCenturies(julianDay(instant))
            val sun = solarPosition(t)
            return lunarPhase(lunarPosition(t), sun.apparentLongitudeDeg, sun.radiusVectorKm)
        }

        val full = utc(2026, 1, 3, 10, 2)
        phaseAt(full - 3_600L).waxing shouldBe true
        phaseAt(full + 3_600L).waxing shouldBe false

        // And k really is flat near full: the fraction moves with a cosine, so it
        // barely changes for two days either side. This is what stops anyone
        // rewriting the phase as a symmetric linear rise and fall.
        val atFull = phaseAt(full).illuminatedFraction
        atFull.shouldBeGreaterThan(0.998)
        phaseAt(full - 2 * 86_400L).illuminatedFraction.shouldBeLessThan(atFull)
        phaseAt(full + 2 * 86_400L).illuminatedFraction.shouldBeLessThan(atFull)
    }

    @Test
    fun `the synodic month falls out of the elongation cycle`() {
        // A whole-series test that cannot be satisfied by luck: a wrong argument or
        // a sign error on any large periodic term beats against the mean motion and
        // skews the mean interval. It needs no external table beyond a constant
        // every almanac prints — the mean synodic month, 29.530588 days.
        //
        // **The fifty-year window is the assertion's own precision, not padding.**
        // The estimator is (last - first) / intervals, so it is determined entirely
        // by the two end crossings and is *not* an average over the intervals
        // between them. Real lunations run from 29.27 to 29.83 days, so wherever
        // the two ends happen to fall in that cycle costs the span up to half a
        // day — which over ten years is 0.004 days of bias and over twenty is
        // 0.0035, both larger than the constant is being checked to. Measured
        // across windows: 10 years is out by 0.0039, 20 by 0.0035, 40 by 0.0010
        // and 50 by 0.00013. Only the last of those is a statement about the
        // series rather than about the window.
        // The crossings are *interpolated* between samples rather than taken as the
        // first sample past the wrap, which is what lets the step be this coarse:
        // measured, the answer is identical to seven decimals at 30-minute and at
        // four-hour sampling, because the elongation is very nearly linear across
        // one step. Taking the first sample past the wrap instead would quantise
        // the two end crossings by up to a whole step each.
        val step = 14_400L
        val crossings = mutableListOf<Double>()
        var previous = Double.NaN
        var sample = 0L
        val start = utc(2000, 1, 1, 0, 0)
        val steps = 50L * 365L * 6L
        while (sample < steps) {
            val instant = start + sample * step
            val t = julianCenturies(julianDay(instant))
            val elongation = normaliseDeg(
                lunarPosition(t).longitudeDeg - solarPosition(t).apparentLongitudeDeg,
            )
            if (!previous.isNaN() && previous > 300.0 && elongation < 60.0) {
                // Elongation increases monotonically here and wraps through 360.
                val fraction = (360.0 - previous) / (elongation + 360.0 - previous)
                crossings += (instant - step) + fraction * step
            }
            previous = elongation
            sample++
        }

        (crossings.size > 600) shouldBe true
        val meanDays = (crossings.last() - crossings.first()) /
            (crossings.size - 1).toDouble() / 86_400.0
        meanDays shouldBe (29.530588 plusOrMinus 0.0005)
    }

    @Test
    fun `the moon's parallax is applied, in the right direction, and is worth applying`() {
        // The magnitude is the point: about two lunar diameters, so a regression
        // that dropped it would move the Moon where a reader could see it. The sign
        // assertion catches applying it backwards, which would raise the Moon
        // instead of lowering it.
        var largest = 0.0
        var minute = 0
        while (minute < 31 * 24 * 4) {
            val instant = utc(2026, 1, 1, 0, 0) + minute * 900L
            val t = julianCenturies(julianDay(instant))
            val moon = lunarPosition(t)
            val equatorial = equatorialFromEcliptic(
                moon.longitudeDeg, moon.latitudeDeg, apparentObliquityDeg(t),
            )
            val geocentric = horizontalFrom(
                equatorial, EHAM_LAT, localHourAngleDeg(equatorial, EHAM_LON, julianDay(instant)),
            ).elevationDeg
            if (geocentric > -5.0) {
                val topocentric = topocentricElevationDeg(geocentric, moon.distanceKm)
                topocentric.shouldBeLessThan(geocentric)
                largest = maxOf(largest, geocentric - topocentric)
            }
            minute++
        }

        // Closed form: sin(parallax) = 6378.14 / distance, so at the January 2026
        // perigee (about 356,500 km) the parallax is 1.025 deg, and the correction
        // is parallax * cos(h).
        largest shouldBe (1.009 plusOrMinus 0.020)
    }
}
