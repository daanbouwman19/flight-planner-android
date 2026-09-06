package com.github.daanbouwman.flightplanner.feature.globe.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2

/**
 * What a touch on the globe has been decided to mean.
 *
 * The set is closed and exactly one is active at a time — see [globeGestures]
 * for why that matters more here than in most gesture handling.
 */
internal enum class GlobeGesture { Pan, Zoom, Rotate, Tilt }

/** One classified movement, in the units the camera wants. */
internal sealed interface GlobeGestureEvent {
    /** The finger, or the centroid of two, moved to [position]. */
    data class Pan(val position: Offset, val start: Offset) : GlobeGestureEvent

    /** The pinch span changed by [factor], centred on [focus]. */
    data class Zoom(val factor: Float, val focus: Offset) : GlobeGestureEvent

    /** The two fingers turned by [radians]. */
    data class Rotate(val radians: Float) : GlobeGestureEvent

    /** Two fingers moved together by [dy] pixels vertically. */
    data class Tilt(val dy: Float) : GlobeGestureEvent

    /** The gesture is over; [velocity] is the release velocity of a pan. */
    data class End(val gesture: GlobeGesture, val velocity: Velocity) : GlobeGestureEvent

    /** Something is now touching the globe. Stops any running camera animation. */
    data object Down : GlobeGestureEvent
}

/**
 * Pan, pinch, rotate and tilt, with the classification window that makes them
 * usable.
 *
 * ### The mode lock, and why the globe is unusable without it
 *
 * Two fingers on a screen are *always* doing all three of pinching, twisting and
 * dragging a little, because hands are not machines. Applied continuously, the
 * result is a camera whose zoom, bearing and centre all wobble at once — which
 * on a sphere compounds, because a small bearing error rotates the pan direction
 * and a small tilt error changes what the pinch is centred on. The desktop
 * original avoids the question entirely by having separate mouse buttons.
 *
 * So the first [CLASSIFY_MS] milliseconds, or the first [SLOP_DP] of movement,
 * whichever ends sooner, are spent **watching rather than acting**. Whichever
 * axis moves furthest relative to its own threshold wins, and from then until
 * every finger lifts, only that axis is applied. A gesture that turns into a
 * different gesture requires lifting and starting again, which is what everyone
 * already does.
 *
 * Nothing is lost during the window: the movement accumulated while classifying
 * is applied in full the moment the mode is decided, so the first event is not
 * a jump but a catch-up.
 */
internal suspend fun PointerInputScope.globeGestures(
    onEvent: (GlobeGestureEvent) -> Unit,
) {
    val slop = SLOP_DP.dp.toPx()
    val zoomSlop = slop
    val rotateSlop = ROTATE_SLOP_RADIANS
    val tiltSlop = slop

    awaitEachGesture {
        val first = awaitFirstDown(requireUnconsumed = false)
        onEvent(GlobeGestureEvent.Down)

        val startTime = first.uptimeMillis
        var gesture: GlobeGesture? = null

        var centroidStart = first.position
        var centroid = first.position
        var span = 0f
        var angle = 0f
        var haveTwoFingers = false

        val velocity = VelocityTracker()
        velocity.addPosition(first.uptimeMillis, first.position)

        var accumulatedZoom = 1f
        var accumulatedRotation = 0f
        var accumulatedTilt = 0f

        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Main)
            val pressed = event.changes.filter { it.pressed }
            if (pressed.isEmpty()) break

            val nextCentroid = pressed.centroid()
            val nextSpan = pressed.span(nextCentroid)
            val nextAngle = pressed.angle()

            if (pressed.size >= 2 && !haveTwoFingers) {
                // A second finger has landed. Re-seed the two-finger quantities
                // rather than measuring them against a one-finger frame, which
                // would report a span that jumped from zero.
                haveTwoFingers = true
                span = nextSpan
                angle = nextAngle
                centroidStart = nextCentroid
                centroid = nextCentroid
            } else if (pressed.size < 2 && haveTwoFingers) {
                // **And a finger leaving needs the same treatment.** The centroid
                // of two fingers is between them; the centroid of the one that
                // remains is under it. Without re-seeding, that jump — half the
                // finger separation — arrives as a single frame of pan, which
                // slams the globe sideways, and goes into the velocity tracker as
                // a real movement, so letting go then flings at a speed nobody
                // moved at.
                haveTwoFingers = false
                span = 0f
                centroidStart = nextCentroid
                centroid = nextCentroid
                velocity.resetTracking()
                velocity.addPosition(event.changes.first().uptimeMillis, nextCentroid)
            }

            val panDelta = nextCentroid - centroid
            val zoomFactor = if (span > 1f && nextSpan > 1f) nextSpan / span else 1f
            val rotation = if (pressed.size >= 2) shortestAngle(nextAngle - angle) else 0f
            val tiltDelta = if (pressed.size >= 2) panDelta.y else 0f

            if (gesture == null) {
                accumulatedZoom *= zoomFactor
                accumulatedRotation += rotation
                accumulatedTilt += tiltDelta

                val elapsed = event.changes.first().uptimeMillis - startTime
                val panScore = (nextCentroid - centroidStart).getDistance() / slop
                val zoomScore = if (haveTwoFingers) abs(span * (accumulatedZoom - 1f)) / zoomSlop else 0f
                val rotateScore = if (haveTwoFingers) abs(accumulatedRotation) / rotateSlop else 0f
                val tiltScore = if (haveTwoFingers) abs(accumulatedTilt) / tiltSlop else 0f

                val best = maxOf(panScore, zoomScore, rotateScore, tiltScore)
                if (best >= 1f || elapsed >= CLASSIFY_MS) {
                    gesture = when {
                        // A single finger can only ever be a pan, whatever the
                        // scores say — the other three need two.
                        !haveTwoFingers -> GlobeGesture.Pan
                        best < 1f -> GlobeGesture.Pan
                        best == zoomScore -> GlobeGesture.Zoom
                        best == rotateScore -> GlobeGesture.Rotate
                        best == tiltScore -> GlobeGesture.Tilt
                        else -> GlobeGesture.Pan
                    }
                    // Catch up: apply everything the window watched, so the
                    // gesture starts from where the fingers actually are.
                    when (gesture) {
                        GlobeGesture.Pan -> onEvent(
                            GlobeGestureEvent.Pan(nextCentroid, centroidStart),
                        )
                        GlobeGesture.Zoom -> onEvent(
                            GlobeGestureEvent.Zoom(accumulatedZoom, nextCentroid),
                        )
                        GlobeGesture.Rotate -> onEvent(
                            GlobeGestureEvent.Rotate(accumulatedRotation),
                        )
                        GlobeGesture.Tilt -> onEvent(GlobeGestureEvent.Tilt(accumulatedTilt))
                    }
                }
            } else {
                when (gesture) {
                    GlobeGesture.Pan -> if (panDelta != Offset.Zero) {
                        onEvent(GlobeGestureEvent.Pan(nextCentroid, centroidStart))
                    }
                    GlobeGesture.Zoom -> if (zoomFactor != 1f) {
                        onEvent(GlobeGestureEvent.Zoom(zoomFactor, nextCentroid))
                    }
                    GlobeGesture.Rotate -> if (rotation != 0f) {
                        onEvent(GlobeGestureEvent.Rotate(rotation))
                    }
                    GlobeGesture.Tilt -> if (tiltDelta != 0f) {
                        onEvent(GlobeGestureEvent.Tilt(tiltDelta))
                    }
                }
            }

            velocity.addPosition(event.changes.first().uptimeMillis, nextCentroid)
            centroid = nextCentroid
            span = nextSpan
            angle = nextAngle

            // Consumed so an ancestor scroll container does not also act on it.
            // The globe fills its box and every touch inside it is the globe's.
            event.changes.forEach { if (it.positionChanged()) it.consume() }
        }

        val settled = gesture ?: GlobeGesture.Pan
        onEvent(
            GlobeGestureEvent.End(
                gesture = settled,
                velocity = if (settled == GlobeGesture.Pan) {
                    velocity.calculateVelocity()
                } else {
                    Velocity.Zero
                },
            ),
        )
    }
}

/**
 * How long the classifier watches before it has to decide.
 *
 * Short enough that a deliberate gesture does not feel like it is being ignored
 * — under about 80 ms a delay reads as the finger's own travel rather than as
 * lag — and long enough to see which way a two-finger gesture is going.
 */
private const val CLASSIFY_MS = 60L

/** Movement, in dp, that ends the window early and settles the classification. */
private const val SLOP_DP = 12f

/** Twist, in radians, worth the same as [SLOP_DP] of movement. About 8°. */
private const val ROTATE_SLOP_RADIANS = (PI / 22.0).toFloat()

private fun List<PointerInputChange>.centroid(): Offset {
    var sum = Offset.Zero
    forEach { sum += it.position }
    return sum / size.toFloat()
}

/** Mean distance from the centroid — zero for one finger, half the span for two. */
private fun List<PointerInputChange>.span(centroid: Offset): Float {
    if (size < 2) return 0f
    var sum = 0f
    forEach { sum += (it.position - centroid).getDistance() }
    return sum / size
}

/** The angle of the line between the first two fingers. */
private fun List<PointerInputChange>.angle(): Float {
    if (size < 2) return 0f
    val delta = this[1].position - this[0].position
    return atan2(delta.y, delta.x)
}

/** Folds an angle difference into (−π, π], so a twist past the seam is small. */
private fun shortestAngle(radians: Float): Float {
    var a = radians
    val turn = (2 * PI).toFloat()
    while (a > PI) a -= turn
    while (a < -PI) a += turn
    return a
}

