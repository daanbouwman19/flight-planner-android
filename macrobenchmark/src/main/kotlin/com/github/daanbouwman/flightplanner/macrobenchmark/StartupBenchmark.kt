package com.github.daanbouwman.flightplanner.macrobenchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Cold start: time to initial display, on the `benchmark` variant.
 *
 * The 500 ms budget in `CLAUDE.md` has until now been defended with a median of
 * `am start -W` readings, which the same document then has to warn about at
 * length: an emulator number is only comparable to another taken minutes earlier
 * on the same idle host, the first runs after an install read ~130 ms high, and a
 * before/after spanning a work session proves nothing. None of that is a flaw in
 * the reasoning — it is a flaw in the instrument. This is the instrument. It
 * controls iterations and warm-up, drops the process and the page cache between
 * them, and reports a distribution rather than a number picked out by hand.
 *
 * ### What is and is not in the figure
 *
 * [StartupTimingMetric] reports time to *initial* display: the first frame the
 * activity produces. This app installs a splash screen and holds it until the
 * airport index and the stored theme have settled, and that hold is on the far
 * side of the first frame — so a regression in the index load will show up as a
 * longer splash a user can see and *not* in this number. Time to full display
 * would cover it, but only if the app called `reportFullyDrawn`, which it does
 * not. Waiting for the route list below is therefore not part of the metric; it
 * is what makes each iteration end in the same state, and what makes a run that
 * never reached the Plan screen fail instead of quietly reporting a fast start.
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun startupNoCompilation() = startup(CompilationMode.None())

    /**
     * The first launch after an install, with only the baseline profile compiled.
     *
     * See `PlanScrollBenchmark` for why this mode has to exist separately: neither
     * of the two either side of it has a baseline profile installed *and* an
     * unwarmed JIT, which is the only state in which a profile is what made the
     * difference.
     */
    @Test
    fun startupBaselineProfile() = startup(
        CompilationMode.Partial(
            baselineProfileMode = BaselineProfileMode.Require,
            warmupIterations = 0,
        ),
    )

    @Test
    fun startupPartialCompilation() = startup(CompilationMode.Partial())

    private fun startup(compilationMode: CompilationMode) = rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = compilationMode,
        iterations = 12,
        startupMode = StartupMode.COLD,
        setupBlock = { pressHome() },
    ) {
        startActivityAndWait()
        awaitRouteList()
    }
}
