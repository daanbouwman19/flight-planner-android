package com.github.daanbouwman.flightplanner.feature.globe.math

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.floats.shouldBeGreaterThan
import io.kotest.matchers.floats.shouldBeLessThan
import io.kotest.matchers.shouldBe
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.test.Test

/**
 * The recogniser driven from synthetic pointer samples at 16.67 ms steps — the
 * test the old classifier never had, and the one that would have found every
 * defect in it.
 *
 * Density 3, so the thresholds are the ones a phone has: touch slop 24 px
 * (8 dp), span slop 48 px (`ScaleGestureDetector`'s two touch slops), tilt slop
 * 24 px per finger, double-tap slop 300 px (100 dp), double-tap timeout 300 ms.
 * Speeds in the test names are in dp/s and are converted at that density; a
 * frame is one event.
 *
 * Each case is a script — fingers placed frame by frame — and an assertion
 * about the intents that came out. Nothing here depends on Compose.
 */
class GestureRecognizerTest {

    private companion object {
        const val FRAME_MS = 16.67
        const val DENSITY = 3f
        const val TOUCH_SLOP = 8f * DENSITY
        const val SPAN_SLOP = TOUCH_SLOP * 2f
        const val DOUBLE_TAP_TIMEOUT_MS = 300L
        const val DOUBLE_TAP_SLOP = 100f * DENSITY
        const val VIEWPORT_HEIGHT = 2340f
        const val DEG = PI / 180.0
        val QUICK_SCALE_LN_PER_PX = 2f * ln(2f) / (VIEWPORT_HEIGHT / 2f)
    }

    private fun config(nestedVerticalScroll: Boolean = false) = GestureConfig(
        touchSlopPx = TOUCH_SLOP,
        spanSlopPx = SPAN_SLOP,
        tiltSlopPx = TOUCH_SLOP,
        doubleTapTimeoutMs = DOUBLE_TAP_TIMEOUT_MS,
        doubleTapSlopPx = DOUBLE_TAP_SLOP,
        quickScaleLnPerPx = QUICK_SCALE_LN_PER_PX,
        releaseVerticalSingleFingerDrags = nestedVerticalScroll,
    )

    /** One event per call, the clock advancing a frame between calls. */
    private class Script(config: GestureConfig) {
        val recognizer = GestureRecognizer(config)
        val intents = mutableListOf<GestureIntent>()
        private var clock = 0.0

        val timeMs: Long get() = clock.roundToLong()

        fun frame(vararg fingers: PointerSample): List<GestureIntent> {
            val out = recognizer.onEvent(fingers.toList(), timeMs)
            intents += out
            clock += FRAME_MS
            return out
        }

        fun hold(frames: Int, vararg fingers: PointerSample) = repeat(frames) { frame(*fingers) }

        fun release(): List<GestureIntent> = frame()

        /** Time passing with nothing touching the glass — the gap between two taps. */
        fun pause(ms: Long) {
            clock += ms
        }

        inline fun <reified T : GestureIntent> all(): List<T> = intents.filterIsInstance<T>()

        fun end(): GestureIntent.End = all<GestureIntent.End>().single()
    }

    private fun finger(id: Long, x: Float, y: Float) = PointerSample(id, x, y)

    /** Two fingers [separation] apart, centred on ([cx], [cy]), the line between them at [angle] radians (y down). */
    private fun pair(cx: Float, cy: Float, separation: Float, angle: Double = 0.0): Array<PointerSample> {
        val hx = (cos(angle) * separation / 2.0).toFloat()
        val hy = (sin(angle) * separation / 2.0).toFloat()
        return arrayOf(finger(0, cx - hx, cy - hy), finger(1, cx + hx, cy + hy))
    }

    private fun List<GestureIntent.Zoom>.product(): Float = fold(1f) { acc, z -> acc * z.factor }

    // --- pinch -------------------------------------------------------------------

    @Test
    fun `a second finger 80 ms late still pinches, and the pinch also pans`() {
        val s = Script(config())
        // One finger, held for 80 ms: the old classifier's window had closed.
        s.hold(5, finger(0, 500f, 1000f))
        var separation = 100f
        var cx = 550f
        s.frame(finger(0, cx - separation / 2f, 1000f), finger(1, cx + separation / 2f, 1000f))
        // Open at 300 dp/s of separation — 15 px a frame — with the hand
        // drifting right 2 px a frame, as hands do.
        var rebaselined = -1f
        repeat(30) {
            separation += 15f
            cx += 2f
            if (rebaselined < 0f && abs(separation - 100f) > SPAN_SLOP) rebaselined = separation
            s.frame(finger(0, cx - separation / 2f, 1000f), finger(1, cx + separation / 2f, 1000f))
        }
        s.release()

        val zooms = s.all<GestureIntent.Zoom>()
        zooms.shouldNotBeEmpty()
        // The slop was absorbed at the latch, so the product of the factors is
        // the ratio to the *rebaselined* separation, not to the seed.
        abs(zooms.product() - separation / rebaselined) shouldBeLessThan 1e-3f
        val pans = s.all<GestureIntent.Pan>()
        pans.shouldNotBeEmpty()
        pans.last().x shouldBe cx
        s.end().flingEligible shouldBe true
    }

    @Test
    fun `a slow simultaneous pinch zooms`() {
        val s = Script(config())
        // 60 dp/s per finger is 3 px a frame each: 6 px of separation a frame,
        // eight frames to the span slop. The old code needed it inside 60 ms.
        var separation = 100f
        s.frame(*pair(500f, 1000f, separation))
        repeat(40) {
            separation += 6f
            s.frame(*pair(500f, 1000f, separation))
        }
        s.release()

        val zooms = s.all<GestureIntent.Zoom>()
        zooms.shouldNotBeEmpty()
        zooms.all { it.factor > 1f } shouldBe true
        // Symmetric about a still centroid: nothing to pan.
        s.all<GestureIntent.Pan>().shouldBeEmpty()
    }

    @Test
    fun `a thumb-anchored pinch zooms and pans, and does not rotate`() {
        val s = Script(config())
        var bx = 500f
        s.frame(finger(0, 400f, 1000f), finger(1, bx, 1000f))
        repeat(30) {
            bx += 8f
            s.frame(finger(0, 400f, 1000f), finger(1, bx, 1000f))
        }
        s.release()

        s.all<GestureIntent.Zoom>().shouldNotBeEmpty()
        s.all<GestureIntent.Pan>().shouldNotBeEmpty()
        s.all<GestureIntent.Rotate>().shouldBeEmpty()
        s.all<GestureIntent.Tilt>().shouldBeEmpty()
    }

    @Test
    fun `the per-event ratio is clamped as noise rejection`() {
        val s = Script(config())
        s.frame(*pair(500f, 1000f, 200f))
        s.frame(*pair(500f, 1000f, 260f)) // past the span slop: latched, absorbed
        s.frame(*pair(500f, 1000f, 270f)).filterIsInstance<GestureIntent.Zoom>().single().factor shouldBe 270f / 260f
        // A finger reported hundreds of pixels away for one frame.
        val jump = s.frame(*pair(500f, 1000f, 900f)).filterIsInstance<GestureIntent.Zoom>().single()
        jump.factor shouldBe GestureRecognizer.MAX_EVENT_ZOOM
    }

    // --- two-finger drags, twist and tilt -----------------------------------------

    @Test
    fun `a two-finger straight drag pans immediately and does nothing else`() {
        val s = Script(config())
        var x = 400f
        s.frame(finger(0, x, 1000f), finger(1, x + 200f, 1000f))
        // No slop for two fingers: the first move is already a pan.
        x += 10f
        s.frame(finger(0, x, 1000f), finger(1, x + 200f, 1000f))
            .filterIsInstance<GestureIntent.Pan>().single().x shouldBe x + 100f
        repeat(29) {
            x += 10f
            s.frame(finger(0, x, 1000f), finger(1, x + 200f, 1000f))
        }
        s.release()

        s.all<GestureIntent.Pan>().size shouldBe 30
        s.all<GestureIntent.Zoom>().shouldBeEmpty()
        s.all<GestureIntent.Rotate>().shouldBeEmpty()
        s.all<GestureIntent.Tilt>().shouldBeEmpty()
        s.end().flingEligible shouldBe true
    }

    @Test
    fun `a twenty-degree twist rotates, clockwise positive, with the slop absorbed`() {
        val s = Script(config())
        s.frame(*pair(500f, 1000f, 200f))
        // Increasing atan2 in y-down pixels is clockwise on the glass.
        repeat(20) { k -> s.frame(*pair(500f, 1000f, 200f, (k + 1) * DEG)) }
        s.release()

        val rotates = s.all<GestureIntent.Rotate>()
        rotates.shouldNotBeEmpty()
        val total = rotates.sumOf { it.radians.toDouble() }
        total shouldBeGreaterThan 2 * DEG
        // Only what came after the ~15° latch is applied; the twist spent
        // reaching it is never repaid as a jump.
        total shouldBeLessThan 6 * DEG
        s.all<GestureIntent.Tilt>().shouldBeEmpty()
    }

    @Test
    fun `a five-degree wobble during a pinch does not rotate`() {
        val s = Script(config())
        var separation = 100f
        s.frame(*pair(500f, 1000f, separation))
        repeat(40) { k ->
            separation += 8f
            s.frame(*pair(500f, 1000f, separation, 5 * DEG * sin(k * 0.4)))
        }
        s.release()

        s.all<GestureIntent.Zoom>().shouldNotBeEmpty()
        s.all<GestureIntent.Rotate>().shouldBeEmpty()
    }

    @Test
    fun `both fingers dragged down together tilt, and do not rotate`() {
        val s = Script(config())
        var y = 1000f
        s.frame(finger(0, 400f, y), finger(1, 600f, y))
        repeat(30) {
            y += 10f
            s.frame(finger(0, 400f, y), finger(1, 600f, y))
        }
        s.release()

        val tilts = s.all<GestureIntent.Tilt>()
        tilts.shouldNotBeEmpty()
        tilts.all { it.dy > 0f } shouldBe true
        s.all<GestureIntent.Rotate>().shouldBeEmpty()
        s.all<GestureIntent.Zoom>().shouldBeEmpty()
    }

    @Test
    fun `two fingers dragged diagonally pan without tilting`() {
        val s = Script(config())
        var d = 0f
        s.frame(finger(0, 400f + d, 1000f + d), finger(1, 600f + d, 1000f + d))
        repeat(30) {
            d += 10f
            s.frame(finger(0, 400f + d, 1000f + d), finger(1, 600f + d, 1000f + d))
        }
        s.release()

        s.all<GestureIntent.Pan>().shouldNotBeEmpty()
        // 45° is outside the 20° cone around vertical.
        s.all<GestureIntent.Tilt>().shouldBeEmpty()
    }

    @Test
    fun `a single finger dragged down pans only`() {
        val s = Script(config())
        var y = 1000f
        s.frame(finger(0, 500f, y))
        repeat(30) {
            y += 10f
            s.frame(finger(0, 500f, y))
        }
        s.release()

        s.all<GestureIntent.Pan>().shouldNotBeEmpty()
        s.all<GestureIntent.Tilt>().shouldBeEmpty()
        s.all<GestureIntent.Zoom>().shouldBeEmpty()
        s.all<GestureIntent.Rotate>().shouldBeEmpty()
    }

    // --- pointer count changes ---------------------------------------------------

    @Test
    fun `a third finger landing and lifting reseeds and slams nothing`() {
        val s = Script(config())
        var x = 400f
        val step = 0.5f
        s.frame(finger(0, x, 1000f), finger(1, x + 200f, 1000f))
        repeat(10) {
            x += step
            s.frame(finger(0, x, 1000f), finger(1, x + 200f, 1000f))
        }
        // A palm lands 400 px away and stays for ten frames.
        repeat(10) {
            x += step
            s.frame(finger(0, x, 1000f), finger(1, x + 200f, 1000f), finger(2, 800f, 1500f))
        }
        repeat(10) {
            x += step
            s.frame(finger(0, x, 1000f), finger(1, x + 200f, 1000f))
        }
        s.release()

        s.all<GestureIntent.Reseed>().size shouldBe 2
        s.all<GestureIntent.Zoom>().all { it.factor in 0.99f..1.01f } shouldBe true
        s.all<GestureIntent.Rotate>().shouldBeEmpty()
        // The centroid is the tracked pair's, never the palm's: consecutive
        // pans differ by the half-pixel the pair moved, not by the 200 px the
        // palm would have dragged the centroid.
        val pans = s.all<GestureIntent.Pan>()
        pans.shouldNotBeEmpty()
        pans.zipWithNext().forEach { (a, b) -> hypot(b.x - a.x, b.y - a.y) shouldBeLessThan 2f }
    }

    @Test
    fun `a finger landing or lifting reseeds and the remaining finger keeps dragging`() {
        val s = Script(config())
        var x = 500f
        s.frame(finger(0, x, 1000f))
        repeat(5) {
            x += 10f
            s.frame(finger(0, x, 1000f))
        }
        s.frame(finger(0, x, 1000f), finger(1, x + 200f, 1000f)) shouldBe listOf(GestureIntent.Reseed)
        repeat(5) {
            x += 10f
            s.frame(finger(0, x, 1000f), finger(1, x + 200f, 1000f))
        }
        // The pan's start moved to the pair's centroid at the reseed.
        s.all<GestureIntent.Pan>().last().startX shouldBe (x - 50f) + 100f
        s.frame(finger(0, x, 1000f)) shouldBe listOf(GestureIntent.Reseed)
        // No second slop: the finger that stays was already dragging.
        x += 5f
        s.frame(finger(0, x, 1000f)).filterIsInstance<GestureIntent.Pan>().single().x shouldBe x
        s.release()

        s.all<GestureIntent.Down>().size shouldBe 1
        s.end().flingEligible shouldBe true
    }

    // --- taps and releases -------------------------------------------------------

    @Test
    fun `a two-finger tap ends without a fling`() {
        val s = Script(config())
        s.hold(3, finger(0, 400f, 1000f), finger(1, 600f, 1000f))
        s.release()

        s.end().flingEligible shouldBe false
        s.all<GestureIntent.Pan>().shouldBeEmpty()
    }

    @Test
    fun `one finger pans only past the touch slop, and the slop is absorbed`() {
        val s = Script(config())
        s.frame(finger(0, 500f, 1000f)) shouldBe listOf(GestureIntent.Down)
        s.frame(finger(0, 510f, 1000f)).shouldBeEmpty()
        s.recognizer.isClaimed shouldBe false
        // 30 px: past the slop. Latched, and nothing emitted for this event.
        s.frame(finger(0, 530f, 1000f)).shouldBeEmpty()
        s.recognizer.isClaimed shouldBe true
        val pan = s.frame(finger(0, 540f, 1000f)).filterIsInstance<GestureIntent.Pan>().single()
        pan.x shouldBe 540f
        // The drag starts where the slop ended, so the globe never jumps.
        pan.startX shouldBe 530f
        s.frame(finger(0, 550f, 1000f)).filterIsInstance<GestureIntent.Pan>().size shouldBe 1
        s.release()

        s.end().flingEligible shouldBe true
    }

    @Test
    fun `a still finger lifted is a tap, not a fling`() {
        val s = Script(config())
        s.hold(4, finger(0, 500f, 1000f))
        s.release()

        s.end().flingEligible shouldBe false
        s.all<GestureIntent.Pan>().shouldBeEmpty()
        s.all<GestureIntent.DoubleTap>().shouldBeEmpty()
    }

    @Test
    fun `two taps inside the timeout and slop are a double-tap at the first tap`() {
        val s = Script(config())
        s.hold(3, finger(0, 500f, 1000f))
        s.release()
        s.pause(100)
        s.hold(3, finger(0, 508f, 1004f))
        s.release()

        val tap = s.all<GestureIntent.DoubleTap>().single()
        tap.x shouldBe 500f
        tap.y shouldBe 1000f
        s.all<GestureIntent.Pan>().shouldBeEmpty()
        s.all<GestureIntent.End>().none { it.flingEligible } shouldBe true
    }

    @Test
    fun `a second tap after the timeout is only a tap`() {
        val s = Script(config())
        s.hold(3, finger(0, 500f, 1000f))
        s.release()
        s.pause(DOUBLE_TAP_TIMEOUT_MS + 50)
        s.hold(3, finger(0, 500f, 1000f))
        s.release()

        s.all<GestureIntent.DoubleTap>().shouldBeEmpty()
    }

    @Test
    fun `a second tap too far from the first is only a tap`() {
        val s = Script(config())
        s.hold(3, finger(0, 500f, 1000f))
        s.release()
        s.pause(100)
        s.hold(3, finger(0, 500f + DOUBLE_TAP_SLOP + 10f, 1000f))
        s.release()

        s.all<GestureIntent.DoubleTap>().shouldBeEmpty()
    }

    @Test
    fun `a second press dragged sideways is a quick-scale of nothing, not a pan`() {
        // `ScaleGestureDetector` enters quick-scale on any drag after a
        // double-tap's second press and reads only its vertical component;
        // Google Maps behaves the same. A sideways drag there moves nothing
        // until the finger lifts, and the press is not a tap either.
        val s = Script(config())
        s.hold(3, finger(0, 500f, 1000f))
        s.release()
        s.pause(50)
        var x = 500f
        s.frame(finger(0, x, 1000f))
        repeat(5) {
            x += 20f
            s.frame(finger(0, x, 1000f))
        }
        s.recognizer.isClaimed shouldBe true
        s.release()

        s.all<GestureIntent.DoubleTap>().shouldBeEmpty()
        s.all<GestureIntent.Zoom>().shouldBeEmpty()
        s.all<GestureIntent.Pan>().shouldBeEmpty()
        s.all<GestureIntent.End>().last().flingEligible shouldBe false
    }

    @Test
    fun `a drag that was never a tap does not arm a double-tap`() {
        val s = Script(config())
        var x = 500f
        s.frame(finger(0, x, 1000f))
        repeat(5) {
            x += 20f
            s.frame(finger(0, x, 1000f))
        }
        s.release()
        s.pause(50)
        s.hold(3, finger(0, 500f, 1000f))
        s.release()

        s.all<GestureIntent.DoubleTap>().shouldBeEmpty()
    }

    @Test
    fun `a second press that drags is a quick-scale about the first tap, down zooming in`() {
        val s = Script(config())
        s.hold(3, finger(0, 500f, 1000f))
        s.release()
        s.pause(100)
        var y = 1000f
        s.frame(finger(0, 500f, y))
        repeat(20) {
            y += 10f
            s.frame(finger(0, 500f, y))
        }
        s.release()

        val zooms = s.all<GestureIntent.Zoom>()
        zooms.shouldNotBeEmpty()
        // Down is in — a factor above one — and the focus is the *first* tap,
        // not the moving finger.
        zooms.all { it.factor > 1f && it.focusX == 500f && it.focusY == 1000f } shouldBe true
        // The slop (the first 30 px) is absorbed; everything after it is zoom,
        // at 2·ln 2 per half-screen.
        val expected = exp((y - 1030f) * QUICK_SCALE_LN_PER_PX)
        abs(zooms.product() - expected) shouldBeLessThan 1e-3f
        s.all<GestureIntent.Pan>().shouldBeEmpty()
        s.all<GestureIntent.DoubleTap>().shouldBeEmpty()
        s.all<GestureIntent.End>().last().flingEligible shouldBe false
    }

    @Test
    fun `a cancel mid-pan ends without a fling and leaves the recogniser clean`() {
        val s = Script(config())
        var x = 500f
        s.frame(finger(0, x, 1000f))
        repeat(10) {
            x += 10f
            s.frame(finger(0, x, 1000f))
        }
        s.all<GestureIntent.Pan>().shouldNotBeEmpty()

        s.recognizer.onCancel() shouldBe listOf(GestureIntent.End(flingEligible = false))
        s.recognizer.isClaimed shouldBe false
        s.recognizer.onCancel().shouldBeEmpty()
        // The next press is a fresh gesture.
        s.frame(finger(0, 100f, 100f)) shouldBe listOf(GestureIntent.Down)
    }

    // --- embedded in a scrolling page --------------------------------------------

    @Test
    fun `with nested scrolling a near-vertical one-finger drag is released`() {
        val s = Script(config(nestedVerticalScroll = true))
        var y = 1000f
        s.frame(finger(0, 500f, y))
        repeat(10) {
            y += 10f
            // About 11° off vertical: inside the 30° cone.
            s.frame(finger(0, 500f + (y - 1000f) * 0.2f, y))
        }
        s.recognizer.isClaimed shouldBe false
        s.release()

        s.all<GestureIntent.Released>().size shouldBe 1
        s.all<GestureIntent.Pan>().shouldBeEmpty()
        s.end().flingEligible shouldBe false
    }

    @Test
    fun `with nested scrolling a horizontal drag and any two-finger drag stay the globe's`() {
        val horizontal = Script(config(nestedVerticalScroll = true))
        var x = 500f
        horizontal.frame(finger(0, x, 1000f))
        repeat(10) {
            x += 10f
            horizontal.frame(finger(0, x, 1000f))
        }
        horizontal.release()
        horizontal.all<GestureIntent.Released>().shouldBeEmpty()
        horizontal.all<GestureIntent.Pan>().shouldNotBeEmpty()

        val twoFingers = Script(config(nestedVerticalScroll = true))
        var y = 1000f
        twoFingers.frame(finger(0, 400f, y), finger(1, 600f, y))
        repeat(10) {
            y += 10f
            twoFingers.frame(finger(0, 400f, y), finger(1, 600f, y))
        }
        twoFingers.release()
        twoFingers.all<GestureIntent.Released>().shouldBeEmpty()
        twoFingers.all<GestureIntent.Tilt>().shouldNotBeEmpty()
    }

    @Test
    fun `without nested scrolling a vertical one-finger drag pans`() {
        val s = Script(config(nestedVerticalScroll = false))
        var y = 1000f
        s.frame(finger(0, 500f, y))
        repeat(10) {
            y += 10f
            s.frame(finger(0, 500f, y))
        }
        s.release()

        s.all<GestureIntent.Released>().shouldBeEmpty()
        s.all<GestureIntent.Pan>().shouldNotBeEmpty()
    }

    @Test
    fun `the release cone is thirty degrees`() {
        GestureRecognizer.isNearVertical(dx = sin(29 * DEG).toFloat(), dy = cos(29 * DEG).toFloat(), degrees = 30f) shouldBe true
        GestureRecognizer.isNearVertical(dx = sin(31 * DEG).toFloat(), dy = cos(31 * DEG).toFloat(), degrees = 30f) shouldBe false
        GestureRecognizer.isNearVertical(dx = 10f, dy = 0f, degrees = 30f) shouldBe false
    }

    @Test
    fun `a twist across the atan2 seam is small`() {
        GestureRecognizer.shortestAngle((PI - 0.1 - (-PI + 0.1)).toFloat()) shouldBeLessThan 0.3f
        GestureRecognizer.shortestAngle(0.5f) shouldBe 0.5f
    }
}
