package com.github.daanbouwman.flightplanner.macrobenchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Frame timing while flinging the Plan screen's route list.
 *
 * This replaces the `input motionevent` plus `dumpsys gfxinfo` harness that
 * produced every scrolling figure in `docs/UI-PLAN.md` up to Phase P. That
 * harness could rank two builds measured minutes apart and nothing more: no
 * warm-up control, no iteration control, and — the expensive part — no way to
 * notice that an install had silently failed and it was reading a different APK
 * than it thought. `MacrobenchmarkRule` controls all three, and refuses outright
 * to measure a debuggable target.
 *
 * ### Only the fling is measured
 *
 * Launching the app, waiting for the first batch of fifty routes and letting the
 * staggered entrance finish all happen in `setupBlock`, outside the measurement.
 * Left inside it, `FrameTimingMetric` would be averaging a fling together with an
 * entrance animation and a generation — three different things, reported as one
 * number that moves when any of them does.
 *
 * ### Every iteration starts from a fresh process
 *
 * The complaint this exists to quantify is that the list *stutters for the first
 * second or two and then smooths out*. That is ART warming up, and warm-up is
 * per-process: without the kill, iteration 2 onwards would measure an already-hot
 * process and the median would quietly describe the case nobody complained about.
 *
 * ### What the two compilation modes mean
 *
 * - [CompilationMode.None] is the app with no compiled code at all: every frame
 *   runs interpreted until the JIT catches up. It is the worst case, and the
 *   closest thing to the first fling after a fresh install.
 * - [CompilationMode.Partial] is the app as a user has it after a while — and,
 *   once P1 lands, the app as it is on the *first* run too, because a baseline
 *   profile is what moves the first case toward the second.
 *
 * The gap between them is the size of the prize P1 is playing for, measured
 * before P1 is written rather than argued about afterwards.
 */
@RunWith(AndroidJUnit4::class)
class PlanScrollBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun flingNoCompilation() = fling(CompilationMode.None())

    @Test
    fun flingPartialCompilation() = fling(CompilationMode.Partial())

    private fun fling(compilationMode: CompilationMode) = rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = compilationMode,
        // The panel on the test device runs at 120 Hz while scrolling, so these
        // frames are judged against 8.33 ms, not 16.7. See docs/UI-PLAN.md.
        iterations = 10,
        setupBlock = {
            killProcess()
            pressHome()
            startActivityAndWait()
            awaitRouteListSettled()
        },
    ) {
        flingRouteList(awaitRouteList())
    }
}
