package com.github.daanbouwman.flightplanner.core.designsystem.components

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import kotlin.math.cos
import kotlin.math.sin

/**
 * The surface wind, as a runway diagram needs it.
 *
 * Its own type rather than a `Metar`, because [RunwayDiagram] draws airports and
 * knows nothing about weather reports — and because the three states below are not
 * expressible as a nullable direction plus a nullable speed without the call site
 * having to remember which combinations mean what:
 *
 * - **Calm** — [speedKt] is 0. There is a report and it says the air is still, which
 *   is a fact about the field rather than an absence of one.
 * - **Variable** — [variable] is true and [directionFromDeg] is null. The station
 *   reported `VRB`: a real speed with no usable direction. The sock is drawn
 *   lifted but spinning, never pointing.
 * - **Steady** — a direction and a speed.
 *
 * [directionFromDeg] is degrees true and is where the wind blows **from**, the
 * aviation convention. See `SurfaceWind` for why that distinction is worth naming
 * in every signature.
 */
@Immutable
data class DiagramWind(
    val directionFromDeg: Int?,
    val speedKt: Int,
    val gustKt: Int? = null,
    val variable: Boolean = false,
) {
    /** True when there is a direction worth drawing a sock along. */
    val hasDirection: Boolean get() = directionFromDeg != null && !variable && speedKt > 0
}

/**
 * The silhouette of a windsock on its mast at [mast], **seen from above**.
 *
 * ### Why a sock and not an arrow
 *
 * An arrow would say the direction in less ink. A sock says the direction *and* the
 * speed in one shape, because that is what the object is for: the cone's extension
 * is calibrated, and a pilot who has looked at a field has already read thousands
 * of them. It is the rare case where the literal object beats the abstract symbol,
 * and it is the one piece of the weather feature allowed to be a picture of a
 * thing.
 *
 * ### Lift is length, because this is a plan view
 *
 * [RunwayDiagram] is looking straight down at the field, and **from above you
 * cannot see a sock droop** — you see it foreshortened. A limp sock is a stub
 * beside its mast; a sock streaming level reaches its full length downwind. So
 * [lift] scales [length] between [LimpLengthFraction] and 1 rather than tilting the
 * cone toward the bottom of the screen.
 *
 * The first version tilted it, and the result was a sock that rose *above* its own
 * mast when the wind came from the north — the droop was being added to the
 * bearing's screen-space Y component, which is a quantity a plan view does not
 * have. The mast is a dot here for the same reason: a vertical line drawn on a
 * top-down chart is a projection error, not a shorthand.
 *
 * [lift] comes from `SurfaceWind.sockLift`, which puts full extension at 15 kt
 * because that is what a standard sock is built to indicate. [bearingDeg] is the
 * direction the sock *points*, which is downwind, so a caller passes the reported
 * direction plus 180.
 *
 * The cone tapers from [throatRadius] at the mast to [TailTaper] of it at the tail,
 * and the taper is what makes the shape readable at 20 dp: a constant-width band
 * reads as a flag, and a flag has no direction.
 */
internal fun windsockPath(
    mast: Offset,
    length: Float,
    throatRadius: Float,
    bearingDeg: Float,
    lift: Float,
): Path {
    val axis = sockAxis(bearingDeg)
    val reach = length * sockReach(lift)
    val tail = Offset(mast.x + axis.x * reach, mast.y + axis.y * reach)
    // Perpendicular to the axis, for the cone's two edges.
    val perpX = -axis.y
    val perpY = axis.x
    val tailRadius = throatRadius * TailTaper

    return Path().apply {
        moveTo(mast.x + perpX * throatRadius, mast.y + perpY * throatRadius)
        lineTo(tail.x + perpX * tailRadius, tail.y + perpY * tailRadius)
        lineTo(tail.x - perpX * tailRadius, tail.y - perpY * tailRadius)
        lineTo(mast.x - perpX * throatRadius, mast.y - perpY * throatRadius)
        close()
    }
}

/** The unit vector a sock at [bearingDeg] points along, in screen coordinates. */
private fun sockAxis(bearingDeg: Float): Offset {
    val bearing = Math.toRadians(bearingDeg.toDouble())
    return Offset(sin(bearing).toFloat(), -cos(bearing).toFloat())
}

/** How much of its length a sock reaches at [lift]. See [windsockPath]. */
private fun sockReach(lift: Float): Float =
    LimpLengthFraction + (1f - LimpLengthFraction) * lift.coerceIn(0f, 1f)

/**
 * The two points a stripe crosses the sock at, as a fraction [from]..[to] along it.
 *
 * A real sock is banded orange and white, and the bands are how the eye judges the
 * taper — an unbanded cone at this size is a triangle. Returns the quad in the same
 * winding as [windsockPath] so it can be filled directly.
 */
internal fun windsockStripe(
    mast: Offset,
    length: Float,
    throatRadius: Float,
    bearingDeg: Float,
    lift: Float,
    from: Float,
    to: Float,
): Path {
    val axis = sockAxis(bearingDeg)
    val reach = length * sockReach(lift)
    val perpX = -axis.y
    val perpY = axis.x

    fun radiusAt(t: Float) = throatRadius * (1f - t * (1f - TailTaper))
    fun pointAt(t: Float) = Offset(mast.x + axis.x * reach * t, mast.y + axis.y * reach * t)

    val near = pointAt(from)
    val far = pointAt(to)
    val nearRadius = radiusAt(from)
    val farRadius = radiusAt(to)

    return Path().apply {
        moveTo(near.x + perpX * nearRadius, near.y + perpY * nearRadius)
        lineTo(far.x + perpX * farRadius, far.y + perpY * farRadius)
        lineTo(far.x - perpX * farRadius, far.y - perpY * farRadius)
        lineTo(near.x - perpX * nearRadius, near.y - perpY * nearRadius)
        close()
    }
}

/**
 * The stripes on a sock, as fractions along its length.
 *
 * Five bands, alternating, which is the standard livery. Only the odd ones are
 * returned — the even ones are the body colour already painted underneath.
 */
internal val WindsockStripes: List<Pair<Float, Float>> = listOf(
    0.2f to 0.4f,
    0.6f to 0.8f,
)

/**
 * How short a limp sock is, as a fraction of its full length.
 *
 * Not zero: a sock in a calm still exists, and a mast with nothing on it would read
 * as missing data rather than as still air.
 */
internal const val LimpLengthFraction: Float = 0.34f

/** How wide the tail is relative to the throat. See [windsockPath]. */
internal const val TailTaper: Float = 0.34f

/**
 * How many degrees the sock swings for one pixel of horizontal drag.
 *
 * **The resistance is the wind**, which is the whole reason this is a function of
 * speed rather than a constant. A sock held out straight by 25 kt is a stiff thing
 * to push; one hanging limp in 3 kt swings with a finger. So the same drag turns
 * a calm sock several times further than a gale-blown one, and the object under
 * the finger feels like the object on the field.
 *
 * The floor matters as much as the slope: without it a 60 kt report would pin the
 * sock so hard that the gesture would read as broken rather than as stiff, and a
 * control that does not respond is indistinguishable from one that is not there.
 */
internal fun sockDragDegreesPerPixel(windSpeedKt: Int): Float {
    val speed = windSpeedKt.coerceAtLeast(0)
    return (SockDragReferenceDegrees / (1f + speed / SockDragStiffnessKt))
        .coerceAtLeast(SockDragMinimumDegrees)
}

/**
 * How far the sock has been swung, from the drag so far.
 *
 * Clamped to [SockDragLimitDeg] either side of the true bearing rather than left
 * free to spin. A sock that can be wound right round stops reading as a sock and
 * starts reading as a dial, and — more to the point — a swing past 180° would
 * momentarily show the wind coming from the opposite quarter, which is the one
 * thing this component must never draw.
 */
internal fun sockDragOffsetDeg(dragPx: Float, windSpeedKt: Int): Float =
    (dragPx * sockDragDegreesPerPixel(windSpeedKt)).coerceIn(-SockDragLimitDeg, SockDragLimitDeg)

/** A calm sock turns this far per pixel; [SockDragStiffnessKt] halves it. */
private const val SockDragReferenceDegrees = 0.55f
private const val SockDragStiffnessKt = 12f
private const val SockDragMinimumDegrees = 0.08f

/** How far either side of the truth the sock may be swung. */
internal const val SockDragLimitDeg = 120f
