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
 * Frame timing, and the cost of each section of the globe's frame callback,
 * while the immersive globe is spun.
 *
 * ### What it measures
 *
 * `GlobeRenderHost.renderFrame` is the whole of the globe's per-frame work, and
 * it is instrumented with `trace(...)` sections: `globe:upload` (decoded tiles
 * into the atlas, already on a 3 ms budget), `globe:update` (the quadtree
 * traversal plus the mesh rebuild, together), its two halves `globe:traversal`
 * and `globe:mesh`, `globe:ribbon` (this surface's route line) and
 * `globe:render` (Filament's own frame). [TraceSectionSamplesMetric] reports
 * each as a distribution, so the number this exists to produce — traversal plus
 * rebuild at P90 — is read straight off `globeUpdateMs P90`.
 *
 * `FrameTimingMetric` rides along so the section costs can be read against the
 * frames they land in.
 *
 * ### Why the immersive screen, and why a fling
 *
 * See `openImmersiveGlobe` and `spinGlobe` in `Globe.kt`. Briefly: the
 * immersive screen owns every drag and is the globe's largest viewport, and the
 * fling after each drag is the sustained motion the loop has to keep up with.
 *
 * ### The two compilation modes
 *
 * The baseline profile was collected from a launch and a Plan fling, so the
 * globe's code is not in it: [spinBaselineProfile] measures the globe on its
 * first use after an install, interpreted until the JIT catches up, which is
 * the state a user meets it in. [spinPartialCompilation] is the same journey
 * warmed three times first, which is the state it settles into. A budget
 * decision has to hold in the first; the second says how much of the first is
 * JIT.
 */
@RunWith(AndroidJUnit4::class)
class GlobeSpinBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun spinBaselineProfile() = spin(
        CompilationMode.Partial(
            baselineProfileMode = BaselineProfileMode.Require,
            warmupIterations = 0,
        ),
    )

    @Test
    fun spinPartialCompilation() = spin(CompilationMode.Partial())

    private fun spin(compilationMode: CompilationMode) = rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(
            FrameTimingMetric(),
            TraceSectionSamplesMetric("globe:update", "globeUpdateMs"),
            TraceSectionSamplesMetric("globe:traversal", "globeTraversalMs"),
            TraceSectionSamplesMetric("globe:mesh", "globeMeshMs"),
            TraceSectionSamplesMetric("globe:upload", "globeUploadMs"),
            TraceSectionSamplesMetric("globe:ribbon", "globeRibbonMs"),
            TraceSectionSamplesMetric("globe:render", "globeRenderMs"),
        ),
        compilationMode = compilationMode,
        // Five rather than the fling benchmark's ten: each iteration is a cold
        // launch plus a network-fed globe, and six seconds of spinning yields
        // several hundred section samples on its own.
        iterations = 5,
        setupBlock = {
            killProcess()
            pressHome()
            startActivityAndWait()
            openImmersiveGlobe()
        },
    ) {
        spinGlobe()
    }
}
