package com.github.daanbouwman.flightplanner.ui.goldens

import androidx.compose.runtime.Composable
import com.github.daanbouwman.flightplanner.feature.globe.ui.GlobeImagery
import com.github.daanbouwman.flightplanner.ui.settings.LicencesScreen

/**
 * The licences page: the app bar and the attribution prose, which is the whole
 * screen. The imagery credit is pinned to the keyless provider's — the build's
 * own follows `local.properties`, and the first CI run of these goldens failed
 * on exactly that: Esri on the machine that recorded them, NASA on the runner.
 */
class LicencesGoldens : GoldenSuite(subject = "licences", primaryState = "default") {

    @Composable
    override fun Primary() = LicencesScreen(onBack = {}, imagery = GlobeImagery.keyless)
}
