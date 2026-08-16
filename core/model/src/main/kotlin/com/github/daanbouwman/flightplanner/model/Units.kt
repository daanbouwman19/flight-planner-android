package com.github.daanbouwman.flightplanner.model

import kotlin.math.roundToInt

/**
 * Unit conversions shared by the whole app.
 *
 * These constants are ported verbatim from the Rust application so that routes
 * generated here remain directly comparable with routes generated there. In
 * particular [METERS_TO_FEET] must stay at this exact precision: it is applied
 * to an aircraft's takeoff distance to derive the minimum runway length, and a
 * different rounding would silently shift which airports qualify.
 */
object Units {
    const val METERS_TO_FEET: Double = 3.28084

    /** Fallback cruise speed when an airframe records none, matching the desktop app. */
    const val DEFAULT_CRUISE_KNOTS: Double = 300.0

    /**
     * Minimum runway length, in feet, for an aircraft whose takeoff distance is
     * recorded in metres. A `null` takeoff distance means "no requirement",
     * which the desktop app represents as 0.
     */
    fun takeoffDistanceToRunwayFeet(takeoffDistanceMeters: Int?): Int =
        takeoffDistanceMeters?.let { (it * METERS_TO_FEET).roundToInt() } ?: 0
}
