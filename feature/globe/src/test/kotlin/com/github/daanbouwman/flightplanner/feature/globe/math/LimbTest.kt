package com.github.daanbouwman.flightplanner.feature.globe.math

import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import kotlin.test.Test

/**
 * [Limb.projectInto] and the gap it must not hide.
 *
 * The consumer strokes the silhouette as a polyline. When the view is tilted in
 * close, one contiguous arc of the ring goes behind the camera; if those samples
 * were dropped *compacted* the consumer would draw a straight segment from the
 * last survivor before the gap to the first after it — a chord clean across the
 * planet, which is exactly what the rim and its glow used to show under tilt.
 * So a culled sample is written as `(NaN, NaN)` in place, and the return value
 * is only the count in front of the camera.
 */
class LimbTest {

    private val viewport = GlobeViewport(width = 800f, height = 800f)
    private val out = FloatArray(Limb.SAMPLES * 2)

    private fun project(camera: GlobeCamera): Int =
        Limb.projectInto(camera, camera.computeBasis(), viewport, out)

    @Test
    fun `a top-down view keeps the whole ring`() {
        val visible = project(GlobeCamera(altitude = 2f, tilt = 0f))

        visible shouldBe Limb.SAMPLES
        (0 until Limb.SAMPLES).forEach { i ->
            out[i * 2].isNaN() shouldBe false
            out[i * 2 + 1].isNaN() shouldBe false
        }
    }

    @Test
    fun `a steep close view drops one contiguous arc and marks it in place`() {
        val visible = project(GlobeCamera(altitude = 0.05f, tilt = MAX_TILT))

        // Something survived and something was culled.
        visible shouldBeGreaterThanOrEqual 3
        (visible < Limb.SAMPLES) shouldBe true

        // Every slot is written — the gap is NaN in place, not a shorter array.
        val nanCount = (0 until Limb.SAMPLES).count { out[it * 2].isNaN() }
        nanCount shouldBe (Limb.SAMPLES - visible)

        // Both the culled samples and the survivors form exactly one run when
        // the ring is walked cyclically: the arc behind the camera, and the arc
        // in front of it. A consumer that lifts its pen on the NaN run therefore
        // draws one open polyline and never a segment bridging the two ends —
        // the chord across the planet the compacted array used to invite.
        cyclicRuns { out[it * 2].isNaN() } shouldBe 1
        cyclicRuns { !out[it * 2].isNaN() } shouldBe 1
    }

    @Test
    fun `a smaller gap is still one contiguous run`() {
        // A shallower lean drops only a handful of samples; the consumer's walk
        // still has exactly one break to lift the pen across.
        val visible = project(GlobeCamera(altitude = 0.3f, tilt = 1.0f))
        (visible in 3 until Limb.SAMPLES) shouldBe true
        cyclicRuns { out[it * 2].isNaN() } shouldBe 1
        cyclicRuns { !out[it * 2].isNaN() } shouldBe 1
    }

    private fun prev(i: Int) = (i + Limb.SAMPLES - 1) % Limb.SAMPLES

    /** How many maximal runs of samples satisfy [pred], walking the ring cyclically. */
    private fun cyclicRuns(pred: (Int) -> Boolean): Int {
        val hit = BooleanArray(Limb.SAMPLES) { pred(it) }
        if (hit.none { it }) return 0
        if (hit.all { it }) return 1
        return (0 until Limb.SAMPLES).count { hit[it] && !hit[prev(it)] }
    }
}
