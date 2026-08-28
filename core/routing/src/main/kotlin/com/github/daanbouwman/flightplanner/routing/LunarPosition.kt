package com.github.daanbouwman.flightplanner.routing

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * The Moon's ecliptic position and its distance.
 *
 * [longitudeDeg] and [latitudeDeg] are apparent, referred to the mean equinox of
 * date; [distanceKm] is centre of the Earth to centre of the Moon, which is what
 * [topocentricElevationDeg] needs in order to move the observer to the surface.
 */
internal data class LunarPosition(
    val longitudeDeg: Double,
    val latitudeDeg: Double,
    val distanceKm: Double,
)

/**
 * The Moon's position, by Meeus chapter 47 — **the whole of tables 47.A and 47.B**,
 * all sixty rows of each, plus every additive term.
 *
 * ### Why the full tables and not an abridgement
 *
 * An abridgement is the obvious economy: the largest 25 rows of 47.A and 20 of
 * 47.B land within 0.027° of the full series, which is a fifth of a pixel in this
 * scene, and 45 rows of transcribed integers are easier to read against the book
 * than 120.
 *
 * It was rejected for a reason about **checkability rather than accuracy**. These
 * coefficients are transcribed from a printed book, and the realistic defect is not
 * the truncation — it is one mis-keyed digit. With the full tables, Meeus's own
 * Example 47.a can be asserted *exactly*: the three sums come out to the unit, so a
 * single wrong digit anywhere in the 120 rows fails the test. Abridged, that same
 * example can only be asserted to a tolerance wide enough to absorb the truncation
 * — and a tolerance that wide is exactly wide enough to hide a mis-keyed small
 * coefficient.
 *
 * So the extra rows are not bought for their 0.027°. They are what turns a
 * tolerance into an identity, and the price is 45 sines that run twice per airport
 * panel.
 *
 * ### What is deliberately absent
 *
 * No ΔT — see [julianCenturies], which measures what that costs. No nutation in
 * longitude beyond what the shared apparent-obliquity correction carries, because
 * its 0.005° is a twenty-fifth of a pixel. The **elongation shortcut** — deriving
 * phase from the Sun–Moon angle without a real lunar position — was measured at
 * 10.4° of error and rejected before this file existed. It must not come back.
 */
internal fun lunarPosition(t: Double): LunarPosition {
    // Meeus 47.1-47.6. Each is a mean argument in degrees, and each reaches about
    // 10^7 before reduction — the numerical reason this file is `Double`.
    val meanLongitude = 218.3164477 + 481_267.88123421 * t - 0.0015786 * t * t +
        t * t * t / 538_841.0 - t * t * t * t / 65_194_000.0
    val meanElongation = 297.8501921 + 445_267.1114034 * t - 0.0018819 * t * t +
        t * t * t / 545_868.0 - t * t * t * t / 113_065_000.0
    val solarAnomaly = 357.5291092 + 35_999.0502909 * t - 0.0001536 * t * t +
        t * t * t / 24_490_000.0
    val lunarAnomaly = 134.9633964 + 477_198.8675055 * t + 0.0087414 * t * t +
        t * t * t / 69_699.0 - t * t * t * t / 14_712_000.0
    val argumentOfLatitude = 93.2720950 + 483_202.0175233 * t - 0.0036539 * t * t -
        t * t * t / 3_526_000.0 + t * t * t * t / 863_310_000.0

    // Meeus's three further arguments, for Venus, Jupiter and the flattening of the
    // Earth. A1's term (3958) is larger than the 24th periodic term, which is why
    // these are never what an abridgement drops first.
    val a1 = 119.75 + 131.849 * t
    val a2 = 53.09 + 479_264.290 * t
    val a3 = 313.45 + 481_266.484 * t

    // The eccentricity factor. Rows with |M| = 1 scale by E and |M| = 2 by E
    // squared, because those terms depend on Earth's orbit, whose eccentricity is
    // slowly changing while the Moon's own arguments are not.
    val e = 1.0 - 0.002516 * t - 0.0000074 * t * t

    var sumLongitude = 0.0
    var sumRadius = 0.0
    var index = 0
    while (index < Table47A.size) {
        val argument = Math.toRadians(
            Table47A[index] * meanElongation +
                Table47A[index + 1] * solarAnomaly +
                Table47A[index + 2] * lunarAnomaly +
                Table47A[index + 3] * argumentOfLatitude,
        )
        val scale = eccentricityScale(Table47A[index + 1], e)
        sumLongitude += Table47A[index + 4] * scale * sin(argument)
        sumRadius += Table47A[index + 5] * scale * cos(argument)
        index += 6
    }

    var sumLatitude = 0.0
    index = 0
    while (index < Table47B.size) {
        val argument = Math.toRadians(
            Table47B[index] * meanElongation +
                Table47B[index + 1] * solarAnomaly +
                Table47B[index + 2] * lunarAnomaly +
                Table47B[index + 3] * argumentOfLatitude,
        )
        sumLatitude += Table47B[index + 4] * eccentricityScale(Table47B[index + 1], e) * sin(argument)
        index += 5
    }

    sumLongitude += 3_958.0 * sin(Math.toRadians(a1)) +
        1_962.0 * sin(Math.toRadians(meanLongitude - argumentOfLatitude)) +
        318.0 * sin(Math.toRadians(a2))
    sumLatitude += -2_235.0 * sin(Math.toRadians(meanLongitude)) +
        382.0 * sin(Math.toRadians(a3)) +
        175.0 * sin(Math.toRadians(a1 - argumentOfLatitude)) +
        175.0 * sin(Math.toRadians(a1 + argumentOfLatitude)) +
        127.0 * sin(Math.toRadians(meanLongitude - lunarAnomaly)) -
        115.0 * sin(Math.toRadians(meanLongitude + lunarAnomaly))

    return LunarPosition(
        // Three different units in one table: the longitude and latitude sums are
        // millionths of a degree, the radius sum thousandths of a kilometre. Each
        // is divided at exactly one place, here.
        longitudeDeg = normaliseDeg(meanLongitude + sumLongitude / 1_000_000.0),
        latitudeDeg = sumLatitude / 1_000_000.0,
        distanceKm = 385_000.56 + sumRadius / 1_000.0,
    )
}

/** E for |M| = 1, E² for |M| = 2, and 1 for every row independent of Earth's orbit. */
private fun eccentricityScale(m: Int, e: Double): Double = when (abs(m)) {
    1 -> e
    2 -> e * e
    else -> 1.0
}

/**
 * The Moon's phase, as the two numbers a renderer can honestly use.
 *
 * [illuminatedFraction] is Meeus's *k*, the lit fraction of the visible disc.
 * [elongationDeg] is the Sun–Moon angular separation, 0 at new and 180 at full.
 */
internal data class LunarPhase(
    val illuminatedFraction: Double,
    val elongationDeg: Double,
    val waxing: Boolean,
)

/**
 * The Moon's illuminated fraction and elongation, Meeus chapter 48.
 *
 * `k = (1 + cos i) / 2`, with the phase angle from `tan i = R·sin ψ / (Δ − R·cos ψ)`
 * and the elongation from the ecliptic form `cos ψ = cos β · cos(λ☾ − λ☉)`. The
 * ecliptic form rather than the equatorial one because λ and β are already in hand,
 * and the equatorial form would need right ascension and declination for *both*
 * bodies purely to arrive at the same number.
 *
 * **`k` is not `(1 − cos elongation) / 2`.** That simplification drops the Sun's
 * finite distance and is wrong by up to 0.3° of phase angle near quadrature. It is
 * the same family of shortcut this design already rejected once, at 10.4°.
 *
 * [LunarPhase.waxing] is elongation in (0°, 180°). That is a statement about the
 * **order of two longitudes** and nothing else: the Moon travels eastward, so when
 * it is east of the Sun the lit limb faces back toward the Sun — westward, the
 * trailing limb. It depends on neither the observer's latitude nor the time of
 * night, which is exactly what a renderer drawing an upright terminator can use,
 * and all it can honestly use.
 */
internal fun lunarPhase(
    moon: LunarPosition,
    sunApparentLongitudeDeg: Double,
    sunDistanceKm: Double,
): LunarPhase {
    val elongationSigned = normaliseDeg(moon.longitudeDeg - sunApparentLongitudeDeg)
    val psi = acos(
        cos(Math.toRadians(moon.latitudeDeg)) * cos(Math.toRadians(elongationSigned)),
    )
    val phaseAngle = atan2(
        sunDistanceKm * sin(psi),
        moon.distanceKm - sunDistanceKm * cos(psi),
    )
    return LunarPhase(
        illuminatedFraction = (1.0 + cos(phaseAngle)) / 2.0,
        elongationDeg = Math.toDegrees(psi),
        waxing = elongationSigned > 0.0 && elongationSigned < 180.0,
    )
}

/**
 * Meeus table 47.A, all sixty rows: D, M, M′, F, Σl (millionths of a degree),
 * Σr (thousandths of a kilometre).
 *
 * A flat [IntArray] of six-element rows rather than a list of objects: it is a
 * coefficient table walked once per call in a tight loop, and boxing 360 integers
 * to iterate it would be the only allocation in this file.
 */
private val Table47A: IntArray = intArrayOf(
     0,  0,  1,  0,  6288774, -20905355,   2,  0, -1,  0,  1274027,  -3699111,
     2,  0,  0,  0,   658314,  -2955968,   0,  0,  2,  0,   213618,   -569925,
     0,  1,  0,  0,  -185116,     48888,   0,  0,  0,  2,  -114332,     -3149,
     2,  0, -2,  0,    58793,    246158,   2, -1, -1,  0,    57066,   -152138,
     2,  0,  1,  0,    53322,   -170733,   2, -1,  0,  0,    45758,   -204586,
     0,  1, -1,  0,   -40923,   -129620,   1,  0,  0,  0,   -34720,    108743,
     0,  1,  1,  0,   -30383,    104755,   2,  0,  0, -2,    15327,     10321,
     0,  0,  1,  2,   -12528,         0,   0,  0,  1, -2,    10980,     79661,
     4,  0, -1,  0,    10675,    -34782,   0,  0,  3,  0,    10034,    -23210,
     4,  0, -2,  0,     8548,    -21636,   2,  1, -1,  0,    -7888,     24208,
     2,  1,  0,  0,    -6766,     30824,   1,  0, -1,  0,    -5163,     -8379,
     1,  1,  0,  0,     4987,    -16675,   2, -1,  1,  0,     4036,    -12831,
     2,  0,  2,  0,     3994,    -10445,   4,  0,  0,  0,     3861,    -11650,
     2,  0, -3,  0,     3665,     14403,   0,  1, -2,  0,    -2689,     -7003,
     2,  0, -1,  2,    -2602,         0,   2, -1, -2,  0,     2390,     10056,
     1,  0,  1,  0,    -2348,      6322,   2, -2,  0,  0,     2236,     -9884,
     0,  1,  2,  0,    -2120,      5751,   0,  2,  0,  0,    -2069,         0,
     2, -2, -1,  0,     2048,     -4950,   2,  0,  1, -2,    -1773,      4130,
     2,  0,  0,  2,    -1595,         0,   4, -1, -1,  0,     1215,     -3958,
     0,  0,  2,  2,    -1110,         0,   3,  0, -1,  0,     -892,      3258,
     2,  1,  1,  0,     -810,      2616,   4, -1, -2,  0,      759,     -1897,
     0,  2, -1,  0,     -713,     -2117,   2,  2, -1,  0,     -700,      2354,
     2,  1, -2,  0,      691,         0,   2, -1,  0, -2,      596,         0,
     4,  0,  1,  0,      549,     -1423,   0,  0,  4,  0,      537,     -1117,
     4, -1,  0,  0,      520,     -1571,   1,  0, -2,  0,     -487,     -1739,
     2,  1,  0, -2,     -399,         0,   0,  0,  2, -2,     -381,     -4421,
     1,  1,  1,  0,      351,         0,   3,  0, -2,  0,     -340,         0,
     4,  0, -3,  0,      330,         0,   2, -1,  2,  0,      327,         0,
     0,  2,  1,  0,     -323,      1165,   1,  1, -1,  0,      299,         0,
     2,  0,  3,  0,      294,         0,   2,  0, -1, -2,        0,      8752,
)


/** Meeus table 47.B, all sixty rows: D, M, M-prime, F, sum-b (millionths of a degree). */
private val Table47B: IntArray = intArrayOf(
     0,  0,  0,  1,  5128122,   0,  0,  1,  1,   280602,   0,  0,  1, -1,   277693,
     2,  0,  0, -1,   173237,   2,  0, -1,  1,    55413,   2,  0, -1, -1,    46271,
     2,  0,  0,  1,    32573,   0,  0,  2,  1,    17198,   2,  0,  1, -1,     9266,
     0,  0,  2, -1,     8822,   2, -1,  0, -1,     8216,   2,  0, -2, -1,     4324,
     2,  0,  1,  1,     4200,   2,  1,  0, -1,    -3359,   2, -1, -1,  1,     2463,
     2, -1,  0,  1,     2211,   2, -1, -1, -1,     2065,   0,  1, -1, -1,    -1870,
     4,  0, -1, -1,     1828,   0,  1,  0,  1,    -1794,   0,  0,  0,  3,    -1749,
     0,  1, -1,  1,    -1565,   1,  0,  0,  1,    -1491,   0,  1,  1,  1,    -1475,
     0,  1,  1, -1,    -1410,   0,  1,  0, -1,    -1344,   1,  0,  0, -1,    -1335,
     0,  0,  3,  1,     1107,   4,  0,  0, -1,     1021,   4,  0, -1,  1,      833,
     0,  0,  1, -3,      777,   4,  0, -2,  1,      671,   2,  0,  0, -3,      607,
     2,  0,  2, -1,      596,   2, -1,  1, -1,      491,   2,  0, -2,  1,     -451,
     0,  0,  3, -1,      439,   2,  0,  2,  1,      422,   2,  0, -3, -1,      421,
     2,  1, -1,  1,     -366,   2,  1,  0,  1,     -351,   4,  0,  0,  1,      331,
     2, -1,  1,  1,      315,   2, -2,  0, -1,      302,   0,  0,  1,  3,     -283,
     2,  1,  1, -1,     -229,   1,  1,  0, -1,      223,   1,  1,  0,  1,      223,
     0,  1, -2, -1,     -220,   2,  1, -1, -1,     -220,   1,  0,  1,  1,     -185,
     2, -1, -2, -1,      181,   0,  1,  2,  1,     -177,   4,  0, -2, -1,      176,
     4, -1, -1, -1,      166,   1,  0,  1, -1,     -164,   4,  0,  1, -1,      132,
     1,  0, -1, -1,     -119,   4, -1,  0, -1,      115,   2, -2,  0,  1,      107,
)
