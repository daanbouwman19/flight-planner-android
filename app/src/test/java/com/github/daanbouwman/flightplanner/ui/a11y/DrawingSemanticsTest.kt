package com.github.daanbouwman.flightplanner.ui.a11y

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import com.github.daanbouwman.flightplanner.core.designsystem.components.FlightRulesBadge
import com.github.daanbouwman.flightplanner.core.designsystem.theme.FlightPlannerTheme
import com.github.daanbouwman.flightplanner.model.CloudCover
import com.github.daanbouwman.flightplanner.model.CloudLayer
import com.github.daanbouwman.flightplanner.model.FlightRules
import com.github.daanbouwman.flightplanner.model.Metar
import com.github.daanbouwman.flightplanner.model.SkyCover
import com.github.daanbouwman.flightplanner.ui.airport.AirportDetailContent
import com.github.daanbouwman.flightplanner.ui.airport.AirportDetailUiState
import com.github.daanbouwman.flightplanner.ui.detail.MetarPanel
import com.github.daanbouwman.flightplanner.ui.plan.PlanPreviewData
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The two drawings and the one chip, as a screen reader meets them.
 *
 * `RunwayDiagram` and `SkyProfile` are childless canvas nodes: whatever they say
 * is the sentence their caller composed, and these pin that the caller composes
 * one with the facts in it — the ends drawn, the end the wind favours, the
 * category and the two figures it turns on. `FlightRulesBadge` is the opposite
 * case, a node that used to say too much: "VFR" as a text stop after "Visual
 * Flight Rules", now folded into one.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DrawingSemanticsTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `the runway diagram names the ends it draws and the one the wind favours`() {
        // 270° at 12 kt at Schiphol: straight down 27. 36L has no heading in the
        // fixture and is not drawn, so it must not be named either.
        val metar = Metar(
            station = "EHAM",
            raw = "EHAM 121325Z 27012KT 9999 FEW040 18/09 Q1015",
            flightRules = FlightRules.VFR,
            windDirectionDeg = 270,
            windSpeedKt = 12,
        )
        compose.setContent {
            FlightPlannerTheme(dynamicColor = false) {
                AirportDetailContent(
                    state = AirportDetailUiState(
                        airport = PlanPreviewData.schiphol,
                        runways = PlanPreviewData.schipholRunways,
                        metar = metar,
                        loading = false,
                    ),
                    onFlyFromHere = {},
                    snackbarHostState = SnackbarHostState(),
                )
            }
        }

        compose.onNode(hasContentDescription("Runway diagram", substring = true))
            .assert(hasContentDescription("11 runway ends", substring = true))
            .assert(hasContentDescription("09, 27, 04, 22, 06, 24, 18L, 36R, 18C, 36C, 18R.", substring = true))
            .assert(hasContentDescription("Favoured for the current wind: 27.", substring = true))
    }

    @Test
    fun `the sky profile states the category and the two figures it was decided from`() {
        val metar = Metar(
            station = "EGLL",
            raw = "EGLL 121320Z 24008KT 4000 BKN008 12/11 Q1002",
            flightRules = FlightRules.IFR,
            skyCover = SkyCover.Layers(listOf(CloudLayer(cover = CloudCover.BROKEN, baseFt = 800))),
            visibilityStatuteMiles = 4000.0 / 1609.344,
        )
        compose.setContent {
            FlightPlannerTheme(dynamicColor = false) {
                MetarPanel(icao = "EGLL", metar = metar)
            }
        }

        compose.onNode(hasContentDescription("Sky profile for EGLL", substring = true))
            .assert(hasContentDescription("Instrument Flight Rules", substring = true))
            .assert(hasContentDescription("Ceiling 800 ft", substring = true))
            .assert(hasContentDescription("visibility 2.5 SM", substring = true))
    }

    @Test
    fun `a sky profile with no report says so rather than describing hatch`() {
        compose.setContent {
            FlightPlannerTheme(dynamicColor = false) {
                MetarPanel(icao = "LOWI", metar = null)
            }
        }

        compose.onNodeWithContentDescription("Sky profile for LOWI: no report.").assertExists()
    }

    @Test
    fun `the flight rules badge is one node, with its code folded into its description`() {
        compose.setContent {
            FlightPlannerTheme(dynamicColor = false) {
                FlightRulesBadge(rules = FlightRules.VFR)
            }
        }

        // Merged: the text "VFR" lives on the node that says "Visual Flight
        // Rules", so a screen reader meets one stop and not two.
        compose.onNodeWithContentDescription("Visual Flight Rules").assert(hasText("VFR"))
    }
}
