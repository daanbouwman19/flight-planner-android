package com.github.daanbouwman.flightplanner.routing

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The reported wind resolved against one runway heading.
 *
 * [headwindKt] is positive into the nose and negative for a tailwind, which is the
 * sign convention every performance chart uses. [crosswindKt] is the *magnitude*,
 * always positive, with [fromRight] carrying the side — split that way because the
 * two are read for different reasons: the number goes against the aircraft's
 * demonstrated crosswind limit, and the side tells the pilot which way to hold
 * aileron. A single signed value makes the comparison against a limit an
 * `abs()` the caller has to remember.
 */
data class WindComponents(
    val headwindKt: Double,
    val crosswindKt: Double,
    val fromRight: Boolean,
) {
    /** A tailwind is a headwind with the sign the charts give it. */
    val isTailwind: Boolean get() = headwindKt < 0.0
}

/**
 * Surface wind resolved against a runway.
 *
 * Pure trigonometry with no Android and no Compose, in `:core:routing` for the
 * reason that module exists: this decides which runway a pilot would use, so it
 * wants unit tests measured in milliseconds rather than a screenshot.
 *
 * `Double` here rather than [GreatCircle]'s deliberate `Float`. That convention is
 * about a sampling loop that dominates route generation; this runs a handful of
 * times per airport, so there is nothing to buy and one less rounding story to
 * explain.
 *
 * **Directions are the aviation ones and they are not the same as each other.** A
 * wind direction is where the wind blows *from*; a runway heading is where an
 * aircraft on it points *toward*. So a 090° wind on runway 09 is a pure headwind,
 * and getting these two backwards produces an answer that is exactly wrong rather
 * than obviously wrong — which is why every function here names its argument.
 */
object SurfaceWind {

    /**
     * How [windSpeedKt] from [windFromDeg] resolves against a runway pointing
     * [runwayHeadingDeg].
     *
     * Both headings are degrees true. The runway's own magnetic ident (`09`, `27`)
     * is not used: the dataset publishes a true heading per end, and mixing true
     * wind with a magnetic runway would introduce the local variation as a silent
     * error of up to about 20°.
     */
    fun components(runwayHeadingDeg: Int, windFromDeg: Int, windSpeedKt: Int): WindComponents {
        val offsetDeg = signedOffsetDeg(windFromDeg - runwayHeadingDeg)
        val radians = Math.toRadians(offsetDeg)
        val speed = windSpeedKt.toDouble()
        return WindComponents(
            headwindKt = speed * cos(radians),
            crosswindKt = abs(speed * sin(radians)),
            // A positive offset means the wind comes from clockwise of the runway
            // heading, which from the cockpit is the right-hand side.
            fromRight = offsetDeg > 0.0,
        )
    }

    /**
     * The index in [runwayHeadingsDeg] of the end a pilot would most likely use, or
     * `null` when there is nothing to choose between.
     *
     * Most headwind wins, and a tie on headwind goes to the least crosswind. That
     * ordering is the right way round: a tailwind lengthens the ground roll on every
     * takeoff and landing, while a crosswind within limits is a technique problem.
     *
     * Returns `null` for a calm or variable wind rather than picking arbitrarily —
     * with no wind every end is equally good, and highlighting one would be the
     * diagram inventing a recommendation.
     */
    fun favouredEnd(runwayHeadingsDeg: List<Int>, windFromDeg: Int?, windSpeedKt: Int?): Int? {
        if (runwayHeadingsDeg.isEmpty()) return null
        if (windFromDeg == null) return null
        if (windSpeedKt == null || windSpeedKt < MinDecisiveWindKt) return null
        return runwayHeadingsDeg.indices.minByOrNull { index ->
            val components = components(runwayHeadingsDeg[index], windFromDeg, windSpeedKt)
            // Sorted ascending, so both keys are negated into "smaller is better".
            // Crosswind is scaled far below headwind's range so it can only break a
            // tie, never outvote it.
            -components.headwindKt + components.crosswindKt * CrosswindTieWeight
        }
    }

    /**
     * How far a windsock stands off its mast at [windSpeedKt], from 0 (hanging
     * straight down) to 1 (streaming horizontally).
     *
     * A real sock is fully extended at about 15 kt — that is what the 15-knot
     * standard sock means — and hangs limp below roughly 3. Between those it lifts
     * roughly linearly, which is close enough to the real thing and is what makes
     * the glyph readable as a speed rather than only as a direction.
     */
    fun sockLift(windSpeedKt: Int?): Float {
        val speed = windSpeedKt ?: return 0f
        if (speed <= SockLimpKt) return 0f
        if (speed >= SockFullKt) return 1f
        return (speed - SockLimpKt).toFloat() / (SockFullKt - SockLimpKt).toFloat()
    }

    /** Normalises a difference of headings to −180..180. */
    private fun signedOffsetDeg(deltaDeg: Int): Double {
        var offset = deltaDeg % 360
        if (offset > 180) offset -= 360
        if (offset < -180) offset += 360
        return offset.toDouble()
    }

    /**
     * Below this the wind does not favour a runway.
     *
     * 3 kt, which is where a sock stops indicating a direction at all. Picking a
     * favoured end from a 1 kt wind would be reading noise.
     */
    const val MinDecisiveWindKt: Int = 3

    /** Below this a sock hangs; at or above [SockFullKt] it streams level. */
    const val SockLimpKt: Int = 3
    const val SockFullKt: Int = 15

    /** Scales crosswind so it can only break a headwind tie. See [favouredEnd]. */
    private const val CrosswindTieWeight: Double = 0.001
}

/** The reported wind, rounded for display beside a runway. */
fun WindComponents.headwindRounded(): Int = headwindKt.roundToInt()

/** The crosswind magnitude, rounded for display beside a runway. */
fun WindComponents.crosswindRounded(): Int = crosswindKt.roundToInt()
