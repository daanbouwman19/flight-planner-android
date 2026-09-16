package com.github.daanbouwman.flightplanner.ui.goldens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.github.daanbouwman.flightplanner.ui.detail.ImmersiveGlobeStage
import com.github.daanbouwman.flightplanner.ui.detail.ImmersiveGlobeUnavailable
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * The immersive globe's two branches that need no renderer: the airports were
 * read and there is no arc, and the airports are still being read. The globe
 * branch itself needs a Filament session and a `SurfaceView`, which Robolectric
 * cannot stand up; it is judged on a device.
 */
class ImmersiveGlobeGoldens : GoldenSuite(subject = "immersive-globe", primaryState = "unavailable") {

    @Composable
    override fun Primary() = Unavailable(ImmersiveGlobeStage.Unavailable)

    @Test
    @Config(qualifiers = GoldenQualifiers.COMPACT)
    fun loading() = captureState("loading") { Unavailable(ImmersiveGlobeStage.Loading) }

    @Composable
    private fun Unavailable(stage: ImmersiveGlobeStage) = ImmersiveGlobeUnavailable(
        stage = stage,
        onCollapse = {},
        modifier = Modifier.fillMaxSize(),
    )
}
