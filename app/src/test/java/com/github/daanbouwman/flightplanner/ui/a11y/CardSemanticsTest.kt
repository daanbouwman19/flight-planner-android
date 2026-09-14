package com.github.daanbouwman.flightplanner.ui.a11y

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.daanbouwman.flightplanner.core.designsystem.theme.FlightPlannerTheme
import com.github.daanbouwman.flightplanner.model.AircraftSpec
import com.github.daanbouwman.flightplanner.routing.FlightTime
import com.github.daanbouwman.flightplanner.routing.WorldOutline
import com.github.daanbouwman.flightplanner.ui.fleet.FleetRowCard
import com.github.daanbouwman.flightplanner.ui.logbook.LogbookRow
import com.github.daanbouwman.flightplanner.ui.logbook.LogbookRowCard
import com.github.daanbouwman.flightplanner.ui.plan.PlanPreviewData
import com.github.daanbouwman.flightplanner.ui.plan.RouteCard
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * The three primary list cards, as a screen reader meets them.
 *
 * Each card merges its children into one node with one spoken sentence, and the
 * point being pinned is that **the node carrying that sentence is the one that
 * is clickable** and the one that carries the swipe actions as custom actions.
 * A description on one node and a click on a child is a card TalkBack reads
 * and cannot open; the device reviewer's `uiautomator dump` reported exactly
 * that shape, so this asks the same question of the semantics tree directly.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CardSemanticsTest {

    @get:Rule
    val compose = createComposeRule()

    private fun hasCustomAction(label: String) = SemanticsMatcher("has custom action '$label'") { node ->
        node.config.getOrNull(SemanticsActions.CustomActions)?.any { it.label == label } == true
    }

    @Test
    fun `a route card is one clickable node carrying its sentence and both swipe actions`() {
        compose.setContent {
            FlightPlannerTheme(dynamicColor = false) {
                RouteCard(
                    row = PlanPreviewData.longHaul,
                    outline = WorldOutline.Empty,
                    onClick = {},
                    onMarkFlown = {},
                    onReplace = {},
                )
            }
        }

        compose.onNode(hasContentDescription("EHAM", substring = true))
            .assertHasClickAction()
            .assert(hasCustomAction("Mark flown"))
            .assert(hasCustomAction("Replace"))
    }

    @Test
    fun `a fleet row is one clickable node carrying its sentence and the flown toggle`() {
        compose.setContent {
            FlightPlannerTheme(dynamicColor = false) {
                FleetRowCard(aircraft = boeing, onClick = {}, onToggleFlown = {})
            }
        }

        compose.onNode(hasContentDescription("Boeing 737-800", substring = true))
            .assertHasClickAction()
            .assert(hasCustomAction("Mark flown"))
    }

    @Test
    fun `a logbook row is one clickable node carrying its sentence and the delete action`() {
        compose.setContent {
            FlightPlannerTheme(dynamicColor = false) {
                LogbookRowCard(
                    row = LogbookRow(
                        id = 1,
                        departureIcao = "EHAM",
                        arrivalIcao = "EGLL",
                        date = LocalDate.of(2026, 8, 12),
                        aircraftId = 1,
                        aircraftDisplayName = "Boeing 737-800",
                        distanceNm = 226,
                        flightTime = FlightTime(0, 40, 450.0),
                    ),
                    onClick = {},
                    onDelete = {},
                )
            }
        }

        compose.onNode(hasContentDescription("EHAM to EGLL", substring = true))
            .assertHasClickAction()
            .assert(hasCustomAction("Delete from logbook"))
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
