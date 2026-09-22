package com.github.daanbouwman.flightplanner.wear.theme

import androidx.compose.ui.graphics.Color
import com.github.daanbouwman.flightplanner.handoff.WatchThemeChoice
import com.github.daanbouwman.flightplanner.handoff.WatchThemeState
import com.github.daanbouwman.flightplanner.wear.ui.theme.WearBrandDarkColorScheme
import com.github.daanbouwman.flightplanner.wear.ui.theme.WearBrandLightColorScheme
import com.github.daanbouwman.flightplanner.wear.ui.theme.WearChartColorScheme
import com.github.daanbouwman.flightplanner.wear.ui.theme.WearCockpitColorScheme
import com.github.daanbouwman.flightplanner.wear.ui.theme.schemeFor
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlin.test.Test

/** Which scheme each synced theme lands on, and the two that ignore darkness. */
class WearColorSchemeTest {

    private fun state(choice: WatchThemeChoice, systemDark: Boolean = true) =
        WatchThemeState(choice, systemDark)

    @Test
    fun `system follows the phone's system setting`() {
        schemeFor(state(WatchThemeChoice.SYSTEM, systemDark = true)) shouldBe WearBrandDarkColorScheme
        schemeFor(state(WatchThemeChoice.SYSTEM, systemDark = false)) shouldBe WearBrandLightColorScheme
    }

    @Test
    fun `an explicit light or dark ignores the phone's system setting`() {
        for (systemDark in listOf(true, false)) {
            schemeFor(state(WatchThemeChoice.DARK, systemDark)) shouldBe WearBrandDarkColorScheme
            schemeFor(state(WatchThemeChoice.LIGHT, systemDark)) shouldBe WearBrandLightColorScheme
        }
    }

    /**
     * A look rather than a tone mapping, the same as on the phone: Cockpit is a
     * night panel and Chart is a paper chart, whatever the system says.
     */
    @Test
    fun `cockpit and chart are themselves in either system setting`() {
        for (systemDark in listOf(true, false)) {
            schemeFor(state(WatchThemeChoice.COCKPIT, systemDark)) shouldBe WearCockpitColorScheme
            schemeFor(state(WatchThemeChoice.CHART, systemDark)) shouldBe WearChartColorScheme
        }
    }

    /**
     * The OLED divergence from the phone, which is the reason these schemes are
     * ported rather than shared: `#111319` and `#110A02` on the phone, an unlit
     * pixel here. Chart is deliberately exempt — a paper chart that went black
     * would be a different theme, not the same one on a different device.
     */
    @Test
    fun `every dark theme sits on true black, and chart does not`() {
        WearBrandDarkColorScheme.background shouldBe Color(0xFF000000)
        WearCockpitColorScheme.background shouldBe Color(0xFF000000)
        WearChartColorScheme.background shouldNotBe Color(0xFF000000)
        WearBrandLightColorScheme.background shouldNotBe Color(0xFF000000)
    }
}
