package com.github.daanbouwman.flightplanner.ui.detail

import com.github.daanbouwman.flightplanner.routing.RouteArc
import io.kotest.matchers.shouldBe
import kotlin.test.Test

/**
 * The branch the immersive globe takes for a given detail state.
 *
 * Medium finding 6 of the 2026-09 review: with no arc the screen used to
 * `return` before composing anything, leaving a blank full-screen surface with
 * no way back but the system gesture. The stage is what decides between "still
 * loading, stay quiet" and "finished with nothing, say so" — and both of those
 * must keep the collapse control, which [ImmersiveGlobeUnavailableTest] proves.
 */
class ImmersiveGlobeStageTest {

    private val arc = RouteArc.sampleGeographic(52.31, 4.76, 40.64, -73.78, samples = 4)

    @Test
    fun `an arc means the globe, whatever the loading flag says`() {
        immersiveGlobeStage(RouteDetailUiState(arc = arc, loading = false)) shouldBe ImmersiveGlobeStage.Globe
        // A state can carry an arc from a previous publish while a later one is
        // in flight; the arc is the thing that is drawable, so it wins.
        immersiveGlobeStage(RouteDetailUiState(arc = arc, loading = true)) shouldBe ImmersiveGlobeStage.Globe
    }

    @Test
    fun `no arc while loading is a moment of nothing, not an error`() {
        immersiveGlobeStage(RouteDetailUiState(loading = true)) shouldBe ImmersiveGlobeStage.Loading
    }

    @Test
    fun `no arc after the load is the dead end the screen has to name`() {
        immersiveGlobeStage(RouteDetailUiState(loading = false)) shouldBe ImmersiveGlobeStage.Unavailable
    }
}
