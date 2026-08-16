package com.github.daanbouwman.flightplanner.routing

import com.github.daanbouwman.flightplanner.model.Units
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Great-circle geometry, ported from the desktop application's `util.rs`.
 *
 * The `Float` precision is inherited deliberately, not by accident. The Rust
 * original documents the reasoning: at an Earth radius of 3440 NM, `Float`'s
 * ~7 significant digits give roughly 0.001 NM (1.8 m) of resolution, which is
 * far finer than flight planning needs, and it halves the register pressure in
 * the sampling loop that dominates route generation. Matching it also keeps the
 * two applications' outputs directly comparable.
 */
object GreatCircle {

    const val EARTH_RADIUS_NM: Float = 3440.0f

    /** Half the circumference: the greatest possible great-circle distance. */
    private const val MAX_DISTANCE_NM: Float = EARTH_RADIUS_NM * PI.toFloat()

    /**
     * Haversine `a`, i.e. sin²(c/2), from the differences and the two cosines.
     *
     * `a = sin²(Δlat/2) + cos(lat₁)·cos(lat₂)·sin²(Δlon/2)`
     */
    private fun haversineFactor(latDiff: Float, lonDiff: Float, cosLat1: Float, cosLat2: Float): Float {
        val halfLat = sin(latDiff / 2.0f)
        val halfLon = sin(lonDiff / 2.0f)
        return Math.fma(cosLat1 * cosLat2, halfLon * halfLon, halfLat * halfLat)
    }

    /** Great-circle distance in nautical miles, rounded, from decimal degrees. */
    fun distanceNm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Int {
        val rLat1 = Math.toRadians(lat1).toFloat()
        val rLon1 = Math.toRadians(lon1).toFloat()
        val rLat2 = Math.toRadians(lat2).toFloat()
        val rLon2 = Math.toRadians(lon2).toFloat()

        val a = haversineFactor(rLat2 - rLat1, rLon2 - rLon1, cos(rLat1), cos(rLat2))
        val c = 2.0f * atan2(sqrt(a), sqrt(1.0f - a))
        return (EARTH_RADIUS_NM * c).roundToInt()
    }

    /**
     * Pre-computes the comparison threshold for a maximum distance.
     *
     * Returns the Haversine `a` corresponding to [maxDistanceNm], so the hot
     * loop can compare squared half-angles directly and skip `sqrt` and
     * `atan2` entirely.
     *
     * The half-mile slack matches the original: since [distanceNm] rounds, a
     * route of exactly `maxDistanceNm` must still qualify.
     */
    fun thresholdFor(maxDistanceNm: Int): Float {
        val limit = maxDistanceNm.toFloat() + 0.5f
        if (limit >= MAX_DISTANCE_NM) return 1.0f
        val halfAngle = limit / EARTH_RADIUS_NM / 2.0f
        val s = sin(halfAngle)
        return s * s
    }

    /**
     * Haversine `a` between two slots of [index], using their cached sines and
     * cosines so the inner loop performs no trigonometry at all.
     *
     * `cos(Δlon)` is recovered from the cached values via
     * `cos(lon₁)·cos(lon₂) + sin(lon₁)·sin(lon₂)`, then
     * `a = ½·(1 − (sin·sin + cos·cos·cos(Δlon)))` by the identity
     * `sin²(x/2) = (1 − cos x)/2`.
     */
    fun cachedFactor(index: AirportIndex, a: Int, b: Int): Float {
        val sinLatProduct = index.sinLat[a] * index.sinLat[b]
        val cosLatProduct = index.cosLat[a] * index.cosLat[b]
        val cosLonDiff = Math.fma(index.sinLon[a], index.sinLon[b], index.cosLon[a] * index.cosLon[b])
        val inner = Math.fma(cosLatProduct, cosLonDiff, sinLatProduct)
        return 0.5f * (1.0f - inner)
    }

    /** Whether two slots are within a threshold from [thresholdFor]. */
    fun withinThreshold(index: AirportIndex, a: Int, b: Int, threshold: Float): Boolean =
        cachedFactor(index, a, b) <= threshold

    /** Great-circle distance between two slots, in nautical miles, rounded. */
    fun distanceNm(index: AirportIndex, a: Int, b: Int): Int {
        val factor = cachedFactor(index, a, b).coerceIn(0.0f, 1.0f)
        val c = 2.0f * atan2(sqrt(factor), sqrt(1.0f - factor))
        return (EARTH_RADIUS_NM * c).roundToInt()
    }

    /**
     * Initial great-circle bearing in degrees, 0–360.
     *
     * Not present in the desktop app; added because a route detail screen that
     * shows a heading is markedly more useful to a pilot than one that does not.
     */
    fun initialBearingDeg(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val rLat1 = Math.toRadians(lat1)
        val rLat2 = Math.toRadians(lat2)
        val dLon = Math.toRadians(lon2 - lon1)
        val y = sin(dLon) * cos(rLat2)
        val x = cos(rLat1) * sin(rLat2) - sin(rLat1) * cos(rLat2) * cos(dLon)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    /**
     * The maximum latitude separation, in radians, for a given range.
     *
     * Used as a cheap rejection test before the (still cheap, but not free)
     * Haversine check: two points more than this far apart in latitude alone
     * cannot possibly be in range.
     */
    fun maxLatitudeDeltaRad(maxDistanceNm: Int): Float =
        Math.toRadians(maxDistanceNm / 60.0).toFloat()

    /**
     * Estimated flight time, ported from `calculate_flight_time`.
     *
     * Falls back to [Units.DEFAULT_CRUISE_KNOTS] when an airframe records no
     * cruise speed, and carries the same minutes-rounding-to-60 rollover.
     */
    fun flightTime(distanceNm: Double, cruiseSpeedKt: Int): FlightTime {
        val speed = if (cruiseSpeedKt > 0) cruiseSpeedKt.toDouble() else Units.DEFAULT_CRUISE_KNOTS
        val hoursExact = distanceNm / speed
        val hours = hoursExact.toInt()
        val minutes = ((hoursExact - hours) * 60.0).roundToInt()
        return if (minutes == 60) {
            FlightTime(hours + 1, 0, speed)
        } else {
            FlightTime(hours, minutes, speed)
        }
    }
}

/** Estimated flight time and the cruise speed actually used to compute it. */
data class FlightTime(val hours: Int, val minutes: Int, val effectiveSpeedKt: Double) {
    /** "02h 35m" */
    fun format(): String = "%02dh %02dm".format(hours, minutes)
}

/** Signed shortest difference between two longitudes, in degrees, in (-180, 180]. */
internal fun longitudeDelta(from: Double, to: Double): Double {
    var delta = (to - from) % 360.0
    if (delta > 180.0) delta -= 360.0
    if (delta <= -180.0) delta += 360.0
    return delta
}

/** Whether [lon] lies within [halfWidth] degrees of [centre], handling the ±180° seam. */
internal fun longitudeWithin(lon: Double, centre: Double, halfWidth: Double): Boolean =
    halfWidth >= 180.0 || abs(longitudeDelta(centre, lon)) <= halfWidth
