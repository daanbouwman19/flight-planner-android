package com.github.daanbouwman.flightplanner.ui.a11y

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import com.github.daanbouwman.flightplanner.core.designsystem.theme.FlightPlannerTheme
import com.github.daanbouwman.flightplanner.model.AircraftSpec
import com.github.daanbouwman.flightplanner.ui.stats.ChartMetric
import com.github.daanbouwman.flightplanner.ui.stats.LegStat
import com.github.daanbouwman.flightplanner.ui.stats.MetricGrid
import com.github.daanbouwman.flightplanner.ui.stats.MonthlyActivity
import com.github.daanbouwman.flightplanner.ui.stats.MonthlyActivityCard
import com.github.daanbouwman.flightplanner.ui.stats.TopAircraftCard
import com.github.daanbouwman.flightplanner.ui.stats.TopAircraftStat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.YearMonth

/**
 * The Stats dashboard's clickable rows and its bars, as a screen reader meets
 * them: one node each, one sentence each, and the click — where there is one —
 * on that same node.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StatsSemanticsTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `a metric tile is one node, clickable exactly when it opens a route`() {
        compose.setContent {
            FlightPlannerTheme(dynamicColor = false) {
                MetricGrid(
                    totalFlights = 3,
                    averageDistanceNm = 1_200.0,
                    longestFlight = LegStat("EHAM", "KJFK", aircraftId = 1, distanceNm = 3_163),
                    shortestFlight = null,
                    onOpenRoute = {},
                )
            }
        }

        compose.onNode(hasContentDescription("LONGEST FLIGHT", substring = true))
            .assert(hasContentDescription("EHAM → KJFK", substring = true))
            .assert(hasContentDescription("3,163 NM", substring = true))
            .assertHasClickAction()
        // No leg to open: a tile that says "—" must not offer a button that does nothing.
        compose.onNode(hasContentDescription("SHORTEST FLIGHT", substring = true)).assertHasNoClickAction()
    }

    @Test
    fun `a top-aircraft row is one clickable node reading rank, name and figures`() {
        compose.setContent {
            FlightPlannerTheme(dynamicColor = false) {
                TopAircraftCard(
                    topAircraft = listOf(TopAircraftStat(boeing, flightCount = 3, totalDistanceNm = 900)),
                    onAircraftClick = {},
                )
            }
        }

        compose.onNodeWithContentDescription("1. Boeing 737-800, 3 flights · 900 NM").assertHasClickAction()
    }

    @Test
    fun `an activity bar reads as its month and its figure`() {
        compose.setContent {
            FlightPlannerTheme(dynamicColor = false) {
                MonthlyActivityCard(
                    activity = listOf(
                        MonthlyActivity(YearMonth.of(2026, 8), monthLabel = "Aug", flightCount = 3, distanceNm = 900),
                        MonthlyActivity(YearMonth.of(2026, 9), monthLabel = "Sep", flightCount = 1, distanceNm = 226),
                    ),
                    selectedMetric = ChartMetric.FLIGHTS,
                    onSelectMetric = {},
                )
            }
        }

        compose.onNodeWithContentDescription("Aug: 3 flights").assertExists()
        compose.onNodeWithContentDescription("Sep: 1 flight").assertExists()
    }
}

private val boeing = AircraftSpec(
    id = 1,
    manufacturer = "Boeing",
    variant = "737-800",
    icaoCode = "B738",
    flown = false,
    rangeNm = 2935,
    category = "Jet",
    cruiseSpeedKt = 460,
    dateFlown = null,
    takeoffDistanceMeters = 2000,
)
