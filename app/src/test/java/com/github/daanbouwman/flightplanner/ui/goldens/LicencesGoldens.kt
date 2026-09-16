package com.github.daanbouwman.flightplanner.ui.goldens

import androidx.compose.runtime.Composable
import com.github.daanbouwman.flightplanner.ui.settings.LicencesScreen

/** The licences page: the app bar and the attribution prose, which is the whole screen. */
class LicencesGoldens : GoldenSuite(subject = "licences", primaryState = "default") {

    @Composable
    override fun Primary() = LicencesScreen(onBack = {})
}
