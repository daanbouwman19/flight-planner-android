package com.github.daanbouwman.flightplanner.macrobenchmark

import androidx.benchmark.macro.BaselineProfileMode
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
 * ### What the three compilation modes mean
 *
 * - [CompilationMode.None] is the app with no compiled code at all: every frame
 *   runs interpreted until the JIT catches up. It is the floor.
 * - [flingBaselineProfile]'s mode — `Partial(Require, warmupIterations = 0)` — is
 *   the app on its **first** launch after an install, with the baseline profile
 *   applied and nothing else. This is the case P1 exists to improve, and it is the
 *   case a user meets once per install.
 * - [CompilationMode.Partial] with its default three warm-up iterations is the app
 *   after a while: profile-guided compilation on top of a JIT that has already
 *   seen the journey. It is the ceiling.
 *
 * **The middle one had to be added for P1 to be measurable at all.** With only the
 * outer two, a baseline profile cannot show up in either: `None` issues
 * `cmd package compile --reset`, which discards the installed profile along with
 * everything else, and `Partial()` warms the JIT three times before measuring, so
 * it arrives at a hot process whether or not a profile helped it get there. P2
 * reported the None-to-Partial gap as "the size of the prize P1 is playing for".
 * That was the wrong pair of numbers: it is the distance between the floor and the
 * ceiling, and a profile only ever buys part of it.
 */
@RunWith(AndroidJUnit4::class)
class PlanScrollBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun flingNoCompilation() = fling(CompilationMode.None())

    /**
     * The first fling after an install, with only the baseline profile compiled.
     *
     * `Require` rather than `UseIfAvailable`: if the profile is missing this must
     * fail rather than quietly measure [flingNoCompilation] a second time and
     * report it as P1 having achieved nothing.
     */
    @Test
    fun flingBaselineProfile() = fling(
        CompilationMode.Partial(
            baselineProfileMode = BaselineProfileMode.Require,
            warmupIterations = 0,
        ),
    )

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
