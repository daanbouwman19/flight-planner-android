package com.github.daanbouwman.flightplanner.feature.globe.math

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.tan

/**
 * The thresholds a [GestureRecognizer] decides with, all in pixels, radians or
 * milliseconds. The pump in `ui/GlobeGestures.kt` derives them from the
 * platform's `ViewConfiguration` and names, next to each one, the framework
 * detector whose constant it mirrors; this class only carries them.
 *
 * @property touchSlopPx how far one finger travels before it is a drag rather than a tap
 * @property spanSlopPx how much the separation of two fingers changes before it is a pinch
 * @property tiltSlopPx how far *each* of two fingers moves vertically, together, before it is a tilt
 * @property doubleTapTimeoutMs the longest gap between the first tap's release and the second's press
 * @property doubleTapSlopPx how far apart the two presses of a double-tap may land
 * @property quickScaleLnPerPx log-zoom per pixel of drag after a double-tap that turns into a drag
 * @property rotateSlopRadians how far two fingers twist before it is a rotation; see [DEFAULT_ROTATE_SLOP_RADIANS]
 * @property maxPointers how many pointers take part in the geometry; every one past this is ignored
 * @property releaseVerticalSingleFingerDrags see [GestureIntent.Released]
 */
internal data class GestureConfig(
    val touchSlopPx: Float,
    val spanSlopPx: Float,
    val tiltSlopPx: Float,
    val doubleTapTimeoutMs: Long,
    val doubleTapSlopPx: Float,
    val quickScaleLnPerPx: Float,
    val rotateSlopRadians: Float = DEFAULT_ROTATE_SLOP_RADIANS,
    val maxPointers: Int = 2,
    val releaseVerticalSingleFingerDrags: Boolean = false,
)

/**
 * The twist that makes two fingers a rotation: about 15°.
 *
 * At a 100 px half-separation this is ~27 px of tangential travel per finger,
 * comfortably above a 24 px pan slop, so a pinch with the ordinary wobble a hand
 * puts in it does not start turning the globe. The 8° the previous classifier
 * used was derived from its pan constant — "worth the same as 12 dp" — and lost
 * races against the pan and the pinch it should not have entered; ~15° is what
 * a rotate has to be to be *meant*.
 */
internal const val DEFAULT_ROTATE_SLOP_RADIANS: Float = (PI / 12.0).toFloat()

/** One pressed pointer at one instant. [id] is stable for the pointer's life. */
internal data class PointerSample(val id: Long, val x: Float, val y: Float)

/**
 * What the recogniser has decided the fingers mean, one instant at a time.
 *
 * Everything is a per-event delta or an absolute pixel, never an accumulated
 * value: the consumer applies each intent as it arrives and the recogniser keeps
 * no opinion about the camera.
 */
internal sealed interface GestureIntent {
    /** The first pointer has landed. Whatever the camera was doing on its own should stop. */
    data object Down : GestureIntent

    /**
     * The tracked-pointer set changed — a finger landed or lifted — and every
     * baseline was re-seeded from the new set. Nothing moved; a velocity tracker
     * over the centroid must be reset here, because the centroid of two fingers
     * is between them and the centroid of the one that remains is under it, and
     * that half-separation jump is not a movement anybody made.
     */
    data object Reseed : GestureIntent

    /**
     * The finger, or the centroid of two, is at ([x], [y]); the drag began at
     * ([startX], [startY]). The start changes at every [Reseed] and the consumer
     * re-takes its anchor when it does.
     */
    data class Pan(val x: Float, val y: Float, val startX: Float, val startY: Float) : GestureIntent

    /**
     * The separation grew by [factor] since the previous event, about the focus.
     *
     * A factor above one means the fingers spread — *closer*, in the camera's
     * terms — and a quick-scale drag downward is expressed the same way.
     */
    data class Zoom(val factor: Float, val focusX: Float, val focusY: Float) : GestureIntent

    /**
     * The fingers twisted by [radians] since the previous event, **positive when
     * clockwise on screen** — the sign `atan2` gives in y-down pixel coordinates.
     * The camera's bearing runs the other way; the conversion is its business.
     */
    data class Rotate(val radians: Float) : GestureIntent

    /** Both fingers moved [dy] pixels vertically together since the previous event. */
    data class Tilt(val dy: Float) : GestureIntent

    /** Two clean taps at ([x], [y]) — the position of the first — within the timeout. */
    data class DoubleTap(val x: Float, val y: Float) : GestureIntent

    /**
     * The recogniser has given this gesture away and will emit nothing more for
     * it. Emitted only with [GestureConfig.releaseVerticalSingleFingerDrags],
     * when a single finger's drag turns out to be near-vertical: the hosting
     * scroll container owns vertical drags, the globe owns everything else.
     */
    data object Released : GestureIntent

    /**
     * Every pointer has lifted, or the gesture was cancelled. [flingEligible] is
     * true only when a [Pan] was emitted and the release was a real one — never
     * after a cancel, a quick-scale, a tap or a release.
     */
    data class End(val flingEligible: Boolean) : GestureIntent
}

/**
 * Pan, pinch, twist, tilt, double-tap and quick-scale, decided from pointer
 * samples alone.
 *
 * ### Concurrent, latched, clockless
 *
 * The previous recogniser picked *one* of pan, zoom, rotate or tilt in the first
 * 60 ms or 12 dp and held it until every finger lifted. Two fingers landing
 * 80 ms apart could therefore only ever pan, a gentle pinch scored below its
 * threshold inside the window and was locked to pan on the merits, and a pinch
 * could never also pan. None of that is how a map behaves.
 *
 * Here every axis has its **own latch on distance alone**, and once latched it
 * runs alongside the others:
 *
 * - **One finger** pans once it has travelled [GestureConfig.touchSlopPx].
 * - **Two fingers** pan by their centroid immediately — a pinch is also a drag.
 *   Zoom latches when the separation has changed by [GestureConfig.spanSlopPx];
 *   rotate when the twist has reached [GestureConfig.rotateSlopRadians]; tilt
 *   when both fingers have moved the same way vertically by
 *   [GestureConfig.tiltSlopPx] each, within [TILT_CONE_DEGREES] of vertical,
 *   without the separation having changed. Rotate and tilt exclude *each other*
 *   — the first to latch wins — and nothing else.
 *
 * No clock takes part in any of this. A clock is what made the old classifier
 * deterministic in the wrong direction: a second finger arriving after the
 * window had closed re-seeded every score to zero on the very frame the window
 * expired, and `best < 1` chose pan.
 *
 * ### Every latch absorbs its slop
 *
 * On the frame an axis latches, its baseline is set to the *current* value and
 * nothing is emitted for it. The slop is spent, not repaid as a one-frame jump —
 * exactly what `ScaleGestureDetector` does with its span slop, and the opposite
 * of the old catch-up dispatch, which applied a whole slop of accumulated zoom
 * as a 1.2–1.4× pop.
 *
 * ### The tracked pair
 *
 * The geometry runs on the first [GestureConfig.maxPointers] pointers by id. A
 * third finger changes nothing except that its arrival and departure each cause
 * a [GestureIntent.Reseed], because the old code re-seeded on neither and a
 * palm brushing the glass slammed the globe sideways. `ScaleGestureDetector`
 * likewise reduces any number of pointers to two.
 *
 * ### Why it is here and not in `ui/`
 *
 * So it can be driven from a JVM test at 16.67 ms steps with synthetic samples.
 * The old recogniser was interleaved with `awaitPointerEvent` and had no test at
 * all; every one of the defects above was found by reading it.
 */
internal class GestureRecognizer(private val config: GestureConfig) {

    private enum class Phase { Idle, Single, Multi, QuickScale, Released }

    private var phase = Phase.Idle

    /** Pointers down at the previous event, for detecting a change in the set. */
    private var pressedCount = 0
    private var trackedIds: List<Long> = emptyList()

    /** True once this press has ever had two fingers on it; such a press is never a tap. */
    private var wasMulti = false

    // --- one finger -------------------------------------------------------------

    private var downX = 0f
    private var downY = 0f
    private var maxTravel = 0f
    private var panLatched = false
    private var panStartX = 0f
    private var panStartY = 0f
    private var panEmitted = false

    // --- two fingers ------------------------------------------------------------

    /** Separation when the pair was seeded, for the tilt test; never rebaselined. */
    private var seedSeparation = 0f

    /** Separation the zoom latch measures against; becomes the running previous once latched. */
    private var zoomBaseline = 0f
    private var zoomLatched = false

    private var prevAngle = 0f
    private var twist = 0f
    private var rotateLatched = false
    private var tiltLatched = false

    private var seed0X = 0f
    private var seed0Y = 0f
    private var seed1X = 0f
    private var seed1Y = 0f

    // --- the centroid, shared by both -------------------------------------------

    private var prevX = 0f
    private var prevY = 0f

    // --- taps ---------------------------------------------------------------------

    private var hasLastTap = false
    private var lastTapUpTime = 0L
    private var lastTapX = 0f
    private var lastTapY = 0f
    private var secondTapCandidate = false
    private var quickPrevY = 0f
    private var quickFocusX = 0f
    private var quickFocusY = 0f

    /**
     * Where the tracked pointers are, after the last [onEvent] — one finger's
     * position, or the pair's centroid. What a velocity tracker should be fed.
     */
    var centroidX: Float = 0f
        private set
    var centroidY: Float = 0f
        private set

    /**
     * True once the gesture is the globe's: a pan has latched, two fingers are
     * down, or a quick-scale is running. Until then the pointer changes belong to
     * nobody and an ancestor is free to read them.
     */
    val isClaimed: Boolean
        get() = when (phase) {
            Phase.Single -> panLatched
            Phase.Multi, Phase.QuickScale -> true
            Phase.Idle, Phase.Released -> false
        }

    /**
     * Feeds one pointer event. [pressed] is every pointer currently down; an
     * empty list is the release. Returns the intents this event produced, in the
     * order they should be applied.
     */
    fun onEvent(pressed: List<PointerSample>, timeMillis: Long): List<GestureIntent> {
        val out = ArrayList<GestureIntent>(4)
        if (pressed.isEmpty()) {
            finish(timeMillis, out)
            return out
        }
        val tracked = track(pressed)
        when (phase) {
            Phase.Idle -> begin(tracked, timeMillis, out)
            Phase.Released -> Unit
            else -> {
                if (pressed.size != pressedCount || tracked.map { it.id } != trackedIds) {
                    reseed(tracked, out)
                } else {
                    when (phase) {
                        Phase.Single -> single(tracked[0], out)
                        Phase.Multi -> multi(tracked[0], tracked[1], out)
                        Phase.QuickScale -> quickScale(tracked[0], out)
                        Phase.Idle, Phase.Released -> Unit
                    }
                }
            }
        }
        pressedCount = pressed.size
        trackedIds = tracked.map { it.id }
        updateCentroid(tracked)
        return out
    }

    /**
     * The gesture was taken away — a system back-gesture, a parent stealing the
     * stream — rather than released. Ends it without a fling and forgets any tap
     * in progress, so the next press cannot complete a double-tap with a gesture
     * that never finished.
     */
    fun onCancel(): List<GestureIntent> {
        if (phase == Phase.Idle) return emptyList()
        reset()
        hasLastTap = false
        return listOf(GestureIntent.End(flingEligible = false))
    }

    private fun track(pressed: List<PointerSample>): List<PointerSample> =
        pressed.sortedBy { it.id }.take(config.maxPointers)

    private fun begin(tracked: List<PointerSample>, timeMillis: Long, out: MutableList<GestureIntent>) {
        out += GestureIntent.Down
        wasMulti = false
        panEmitted = false
        if (tracked.size >= 2) {
            phase = Phase.Multi
            seedPair(tracked[0], tracked[1])
            return
        }
        val p = tracked[0]
        phase = Phase.Single
        downX = p.x
        downY = p.y
        maxTravel = 0f
        panLatched = false
        prevX = p.x
        prevY = p.y
        // Android's GestureDetector measures the double-tap gap from the first
        // tap's *release* to the second's press, and this does the same.
        secondTapCandidate = hasLastTap &&
            timeMillis - lastTapUpTime <= config.doubleTapTimeoutMs &&
            hypot(p.x - lastTapX, p.y - lastTapY) <= config.doubleTapSlopPx
        if (secondTapCandidate) {
            quickFocusX = lastTapX
            quickFocusY = lastTapY
        }
    }

    private fun reseed(tracked: List<PointerSample>, out: MutableList<GestureIntent>) {
        secondTapCandidate = false
        if (tracked.size >= 2) {
            phase = Phase.Multi
            seedPair(tracked[0], tracked[1])
        } else {
            // A finger left and one remains. It goes on dragging from where it
            // is, without a second slop: the gesture was already the globe's.
            val p = tracked[0]
            phase = Phase.Single
            panLatched = true
            panStartX = p.x
            panStartY = p.y
            prevX = p.x
            prevY = p.y
        }
        out += GestureIntent.Reseed
    }

    private fun seedPair(a: PointerSample, b: PointerSample) {
        wasMulti = true
        val cx = (a.x + b.x) * 0.5f
        val cy = (a.y + b.y) * 0.5f
        panStartX = cx
        panStartY = cy
        prevX = cx
        prevY = cy
        seedSeparation = hypot(b.x - a.x, b.y - a.y)
        zoomBaseline = seedSeparation
        zoomLatched = false
        prevAngle = atan2(b.y - a.y, b.x - a.x)
        twist = 0f
        rotateLatched = false
        tiltLatched = false
        seed0X = a.x
        seed0Y = a.y
        seed1X = b.x
        seed1Y = b.y
    }

    private fun single(p: PointerSample, out: MutableList<GestureIntent>) {
        if (!panLatched) {
            val dx = p.x - downX
            val dy = p.y - downY
            val travel = hypot(dx, dy)
            if (travel > maxTravel) maxTravel = travel
            if (travel <= config.touchSlopPx) return
            when {
                // A second tap that moves is a quick-scale, not a drag: the
                // one-handed zoom every map on the platform has.
                secondTapCandidate -> {
                    phase = Phase.QuickScale
                    quickPrevY = p.y
                }
                config.releaseVerticalSingleFingerDrags && isNearVertical(dx, dy, RELEASE_CONE_DEGREES) -> {
                    phase = Phase.Released
                    out += GestureIntent.Released
                }
                else -> {
                    panLatched = true
                    panStartX = p.x
                    panStartY = p.y
                    prevX = p.x
                    prevY = p.y
                }
            }
            return
        }
        if (p.x != prevX || p.y != prevY) {
            out += GestureIntent.Pan(p.x, p.y, panStartX, panStartY)
            panEmitted = true
            prevX = p.x
            prevY = p.y
        }
    }

    private fun multi(a: PointerSample, b: PointerSample, out: MutableList<GestureIntent>) {
        val cx = (a.x + b.x) * 0.5f
        val cy = (a.y + b.y) * 0.5f
        val separation = hypot(b.x - a.x, b.y - a.y)
        val angle = atan2(b.y - a.y, b.x - a.x)

        // Zoom. Latched on the span slop, then a per-event ratio. The clamp is
        // digitiser-noise rejection, not a zoom-range clamp — a real pinch moves
        // the separation a few percent per frame, and a 25 % jump in one frame
        // is a pointer that was misreported. The camera owns the altitude range.
        if (!zoomLatched) {
            if (abs(separation - zoomBaseline) > config.spanSlopPx) {
                zoomLatched = true
                zoomBaseline = separation
            }
        } else {
            if (zoomBaseline > MIN_SEPARATION_PX && separation > MIN_SEPARATION_PX) {
                val factor = separation / zoomBaseline
                // Dropped, not clamped. Clamping a misreported pointer still
                // applies the bound — a hard 25 % altitude step in one frame,
                // which is the pop this rejection exists to prevent. The
                // baseline resyncs either way, so a real pinch that genuinely
                // moved this far in one event loses that event and nothing more.
                if (factor in MIN_EVENT_ZOOM..MAX_EVENT_ZOOM && factor != 1f) {
                    out += GestureIntent.Zoom(factor, cx, cy)
                }
            }
            zoomBaseline = separation
        }

        // Rotate. The twist accumulates until the latch; from then on it is the
        // per-event delta, and the accumulated slop is never applied.
        val delta = shortestAngle(angle - prevAngle)
        prevAngle = angle
        if (rotateLatched) {
            if (delta != 0f) out += GestureIntent.Rotate(delta)
        } else if (!tiltLatched) {
            twist += delta
            if (abs(twist) > config.rotateSlopRadians) rotateLatched = true
        }

        // Tilt. Both fingers, the same way, near-vertical, without pinching.
        if (tiltLatched) {
            val dy = cy - prevY
            if (dy != 0f) out += GestureIntent.Tilt(dy)
        } else if (!rotateLatched) {
            val d0x = a.x - seed0X
            val d0y = a.y - seed0Y
            val d1x = b.x - seed1X
            val d1y = b.y - seed1Y
            val sameWay = (d0y > 0f && d1y > 0f) || (d0y < 0f && d1y < 0f)
            if (sameWay &&
                abs(d0y) > config.tiltSlopPx && abs(d1y) > config.tiltSlopPx &&
                isNearVertical(d0x, d0y, TILT_CONE_DEGREES) &&
                isNearVertical(d1x, d1y, TILT_CONE_DEGREES) &&
                abs(separation - seedSeparation) < config.spanSlopPx
            ) {
                tiltLatched = true
            }
        }

        // Pan, of the centroid, always. Last, so that when the consumer re-pins
        // its anchor under the centroid it does so after zoom, rotate and tilt
        // have moved the picture — which makes the anchor exact on every frame.
        if (cx != prevX || cy != prevY) {
            out += GestureIntent.Pan(cx, cy, panStartX, panStartY)
            panEmitted = true
        }
        prevX = cx
        prevY = cy
    }

    /**
     * Quick-scale: a double-tap whose second press dragged. Dragging **down zooms
     * in** (a factor above one), matching Google Maps, and the focus is pinned
     * to the first tap rather than following the finger — the finger is moving
     * to express an amount, not a place.
     */
    private fun quickScale(p: PointerSample, out: MutableList<GestureIntent>) {
        val dy = p.y - quickPrevY
        quickPrevY = p.y
        if (dy != 0f) {
            out += GestureIntent.Zoom(exp(dy * config.quickScaleLnPerPx), quickFocusX, quickFocusY)
        }
    }

    private fun finish(timeMillis: Long, out: MutableList<GestureIntent>) {
        when (phase) {
            Phase.Idle -> return
            Phase.Single -> {
                val isTap = !panLatched && !wasMulti && maxTravel <= config.doubleTapSlopPx
                if (isTap && secondTapCandidate) {
                    out += GestureIntent.DoubleTap(quickFocusX, quickFocusY)
                    hasLastTap = false
                } else if (isTap) {
                    // Remembered, never acted on: a single tap does nothing to a
                    // globe, so no timer has to fire to confirm it was single.
                    hasLastTap = true
                    lastTapUpTime = timeMillis
                    lastTapX = downX
                    lastTapY = downY
                } else {
                    hasLastTap = false
                }
                out += GestureIntent.End(flingEligible = panEmitted)
            }
            Phase.Multi -> {
                hasLastTap = false
                out += GestureIntent.End(flingEligible = panEmitted)
            }
            Phase.QuickScale, Phase.Released -> {
                hasLastTap = false
                out += GestureIntent.End(flingEligible = false)
            }
        }
        reset()
    }

    private fun reset() {
        phase = Phase.Idle
        pressedCount = 0
        trackedIds = emptyList()
        panLatched = false
        panEmitted = false
        secondTapCandidate = false
        wasMulti = false
    }

    private fun updateCentroid(tracked: List<PointerSample>) {
        if (tracked.size >= 2) {
            centroidX = (tracked[0].x + tracked[1].x) * 0.5f
            centroidY = (tracked[0].y + tracked[1].y) * 0.5f
        } else {
            centroidX = tracked[0].x
            centroidY = tracked[0].y
        }
    }

    companion object {
        /**
         * The cone around vertical inside which a one-finger drag is handed to
         * a hosting scroll container. The standard embedded-map compromise: a
         * map inside a page cannot own vertical drags without trapping the page.
         */
        const val RELEASE_CONE_DEGREES = 30f

        /** The cone around vertical inside which two fingers moving together are a tilt. */
        const val TILT_CONE_DEGREES = 20f

        /**
         * The per-event pinch ratio a real hand can produce. Outside this is a
         * misreported pointer, dropped rather than applied. Not a zoom range.
         */
        const val MIN_EVENT_ZOOM = 0.8f
        const val MAX_EVENT_ZOOM = 1.25f

        /** Two fingers closer than this have no meaningful separation to take a ratio of. */
        private const val MIN_SEPARATION_PX = 1f

        /** Whether ([dx], [dy]) points within [degrees] of straight up or down. */
        fun isNearVertical(dx: Float, dy: Float, degrees: Float): Boolean {
            if (dy == 0f) return false
            return abs(dx) <= abs(dy) * tan(degrees * DEG_TO_RAD)
        }

        private const val DEG_TO_RAD = (PI / 180.0).toFloat()

        /** Folds an angle difference into (−π, π], so a twist past the seam is small. */
        fun shortestAngle(radians: Float): Float {
            var a = radians
            val turn = (2 * PI).toFloat()
            while (a > PI) a -= turn
            while (a < -PI) a += turn
            return a
        }
    }
}
