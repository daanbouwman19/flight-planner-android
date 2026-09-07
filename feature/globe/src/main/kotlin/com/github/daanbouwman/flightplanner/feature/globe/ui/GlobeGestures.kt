package com.github.daanbouwman.flightplanner.feature.globe.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.github.daanbouwman.flightplanner.feature.globe.math.GestureConfig
import com.github.daanbouwman.flightplanner.feature.globe.math.GestureIntent
import com.github.daanbouwman.flightplanner.feature.globe.math.GestureRecognizer
import com.github.daanbouwman.flightplanner.feature.globe.math.PointerSample
import kotlin.math.ln

/**
 * One decided movement, in the units the camera wants.
 *
 * The Compose-typed face of [GestureIntent]: the recogniser in `math/` speaks
 * floats so it can be tested without an Android runtime, and this is where they
 * become `Offset` and `Velocity`.
 */
internal sealed interface GlobeGestureEvent {
    /** Something is now touching the globe. Stops any running camera motion. */
    data object Down : GlobeGestureEvent

    /** The finger, or the centroid of two, moved to [position]; the drag began at [start]. */
    data class Pan(val position: Offset, val start: Offset) : GlobeGestureEvent

    /** The pinch separation grew by [factor] since the last event, about [focus]. Above one is closer. */
    data class Zoom(val factor: Float, val focus: Offset) : GlobeGestureEvent

    /** The fingers twisted by [radians], positive clockwise on screen. */
    data class Rotate(val radians: Float) : GlobeGestureEvent

    /** Two fingers moved together by [dy] pixels vertically. */
    data class Tilt(val dy: Float) : GlobeGestureEvent

    /** Two clean taps at [position], inside the double-tap timeout. */
    data class DoubleTap(val position: Offset) : GlobeGestureEvent

    /**
     * The gesture is over. [velocity] is the centroid's release velocity, already
     * clamped to the platform's maximum fling; it is zero unless [flingEligible].
     */
    data class End(val flingEligible: Boolean, val velocity: Velocity) : GlobeGestureEvent
}

/**
 * The pump: pointer events in, [GlobeGestureEvent]s out, with a
 * [GestureRecognizer] doing all of the deciding in between.
 *
 * This function owns exactly three things the recogniser cannot, because they
 * are Compose's:
 *
 * - **Velocity.** A `VelocityTracker` over the tracked centroid, reset on every
 *   [GestureIntent.Reseed] so the jump when a finger lands or lifts is never
 *   read as speed, and clamped through `calculateVelocity(maximumVelocity)` —
 *   the unlimited overload let an abandoned pinch release into a fling nobody
 *   moved at.
 * - **Consumption.** Position changes are consumed only once the recogniser has
 *   claimed the gesture — a pan latched, two fingers down, or a quick-scale
 *   running. Consuming from the first move, as the old loop did, meant a drag
 *   that started on the globe could never scroll the page it sits in, and it
 *   cancelled the double-tap detector that used to sit in a second
 *   `pointerInput` on its final pass.
 * - **Cancellation.** A system pointer-cancel — the back gesture stealing the
 *   stream — arrives as an up that is already consumed; a real up never is. It
 *   ends the gesture with no fling instead of being read as a release.
 *
 * ### Embedded in a scrolling page
 *
 * With [nestedVerticalScroll], a single finger whose drag turns out to be within
 * [GestureRecognizer.RELEASE_CONE_DEGREES] of vertical at the slop is released:
 * no pan, nothing consumed, and the hosting `Column` or `LazyColumn` scrolls.
 * Horizontal drags and anything with two fingers stay the globe's. This is the
 * standard embedded-map compromise — the alternative is a page that cannot be
 * scrolled past its own map — and the immersive screen, which owns the window,
 * leaves it off.
 */
internal suspend fun PointerInputScope.globeGestures(
    nestedVerticalScroll: Boolean,
    onEvent: (GlobeGestureEvent) -> Unit,
) {
    // Created at the first touch rather than here: the quick-scale rate needs
    // the measured height, and it lives across gestures because a double-tap is
    // two of them. Rebuilt if the surface has been resized between gestures —
    // the hero handing over to the immersive screen — so the rate stays a
    // half-screen per two notches; a tap remembered across a resize is lost,
    // which is nothing anybody was doing.
    var recognizer: GestureRecognizer? = null
    var recognizerHeight = -1
    val velocity = VelocityTracker()
    val maximumVelocity = Velocity(
        viewConfiguration.maximumFlingVelocity,
        viewConfiguration.maximumFlingVelocity,
    )

    awaitEachGesture {
        val first = awaitFirstDown()
        val recogniser = recognizer?.takeIf { recognizerHeight == size.height }
            ?: GestureRecognizer(gestureConfig(nestedVerticalScroll)).also {
                recognizer = it
                recognizerHeight = size.height
            }
        velocity.resetTracking()
        var claimed = false

        fun dispatch(intents: List<GestureIntent>) {
            for (intent in intents) {
                when (intent) {
                    GestureIntent.Down -> onEvent(GlobeGestureEvent.Down)
                    GestureIntent.Reseed -> velocity.resetTracking()
                    GestureIntent.Released -> Unit
                    is GestureIntent.Pan -> onEvent(
                        GlobeGestureEvent.Pan(
                            position = Offset(intent.x, intent.y),
                            start = Offset(intent.startX, intent.startY),
                        ),
                    )
                    is GestureIntent.Zoom -> onEvent(
                        GlobeGestureEvent.Zoom(intent.factor, Offset(intent.focusX, intent.focusY)),
                    )
                    is GestureIntent.Rotate -> onEvent(GlobeGestureEvent.Rotate(intent.radians))
                    is GestureIntent.Tilt -> onEvent(GlobeGestureEvent.Tilt(intent.dy))
                    is GestureIntent.DoubleTap -> onEvent(
                        GlobeGestureEvent.DoubleTap(Offset(intent.x, intent.y)),
                    )
                    is GestureIntent.End -> onEvent(
                        GlobeGestureEvent.End(
                            flingEligible = intent.flingEligible,
                            velocity = if (intent.flingEligible) {
                                velocity.calculateVelocity(maximumVelocity)
                            } else {
                                Velocity.Zero
                            },
                        ),
                    )
                }
            }
        }

        dispatch(recogniser.onEvent(listOf(first.sample()), first.uptimeMillis))
        velocity.addPosition(first.uptimeMillis, first.position)

        while (true) {
            val event = awaitPointerEvent()
            if (event.changes.any { it.isSystemCancel() }) {
                dispatch(recogniser.onCancel())
                break
            }

            val pressed = event.changes.filter { it.pressed }
            val time = event.changes.first().uptimeMillis
            val intents = recogniser.onEvent(pressed.map { it.sample() }, time)
            if (pressed.isEmpty()) {
                dispatch(intents)
                break
            }

            // The tracker is fed the recogniser's own centroid — the first two
            // pointers by id — so a third finger is as invisible to the fling as
            // it is to the zoom. Reset first, on a reseed, then given the new
            // centroid as its opening sample.
            dispatch(intents)
            velocity.addPosition(time, Offset(recogniser.centroidX, recogniser.centroidY))

            claimed = claimed || recogniser.isClaimed
            if (claimed) event.changes.forEach { if (it.positionChanged()) it.consume() }
        }
    }
}

private fun PointerInputChange.sample() = PointerSample(id.value, position.x, position.y)

/**
 * A pointer-cancel delivered by the system, as opposed to a finger lifting.
 *
 * Compose hands `ACTION_CANCEL` to gesture detectors as a synthetic up: a copy
 * of the last change with `pressed = false`, **already consumed**, and with its
 * previous position and time set equal to its current ones — nothing moved and
 * no time passed, because no event happened. Every built-in detector reads that
 * as a cancel, and so does this. A real release reaches this node unconsumed
 * because nothing below it exists to consume it; the position and time
 * equalities are there so that even a release an ancestor did consume on the
 * initial pass is not mistaken for a cancel.
 */
private fun PointerInputChange.isSystemCancel(): Boolean =
    previousPressed && !pressed && isConsumed &&
        position == previousPosition && uptimeMillis == previousUptimeMillis

/**
 * Every threshold, in one place, each named for the platform detector it mirrors.
 *
 * - `touchSlop` is `ViewConfiguration.getScaledTouchSlop`, which `GestureDetector`
 *   uses to tell a tap from a drag.
 * - The span slop is **twice** the touch slop: `ScaleGestureDetector.mSpanSlop`
 *   is `getScaledTouchSlop() * 2`, and the span it measures is the full
 *   separation, as here.
 * - The tilt slop is the touch slop, per finger.
 * - The double-tap timeout is `ViewConfiguration.getDoubleTapTimeout` (300 ms).
 * - The double-tap slop is `ViewConfiguration.getScaledDoubleTapSlop`, 100 dp,
 *   which Compose does not surface; it is named here as [DoubleTapSlop]. It
 *   bounds how far apart the two presses may land, not how far a tap may move.
 * - The quick-scale rate is `2·ln 2` over half the height: a drag from the tap
 *   to the bottom of the surface is two notches of the ± controls.
 */
private fun PointerInputScope.gestureConfig(nestedVerticalScroll: Boolean): GestureConfig {
    val touchSlop = viewConfiguration.touchSlop
    return GestureConfig(
        touchSlopPx = touchSlop,
        spanSlopPx = touchSlop * 2f,
        tiltSlopPx = touchSlop,
        doubleTapTimeoutMs = viewConfiguration.doubleTapTimeoutMillis,
        doubleTapSlopPx = DoubleTapSlop.toPx(),
        quickScaleLnPerPx = 2f * ln(2f) / (size.height.coerceAtLeast(1) / 2f),
        releaseVerticalSingleFingerDrags = nestedVerticalScroll,
    )
}

/** See [gestureConfig]: `ViewConfiguration.DOUBLE_TAP_SLOP`, which is 100 dp on every Android release. */
private val DoubleTapSlop = 100.dp
