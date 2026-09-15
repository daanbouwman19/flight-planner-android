package com.github.daanbouwman.flightplanner.ui.a11y

import android.content.Context
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeProvider
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.daanbouwman.flightplanner.core.designsystem.theme.FlightPlannerTheme
import com.github.daanbouwman.flightplanner.model.AircraftSpec
import com.github.daanbouwman.flightplanner.model.FlightRules
import com.github.daanbouwman.flightplanner.model.Metar
import com.github.daanbouwman.flightplanner.routing.FlightTime
import com.github.daanbouwman.flightplanner.routing.WorldOutline
import com.github.daanbouwman.flightplanner.ui.fleet.FleetRowCard
import com.github.daanbouwman.flightplanner.ui.logbook.LogbookRow
import com.github.daanbouwman.flightplanner.ui.logbook.LogbookRowCard
import com.github.daanbouwman.flightplanner.ui.plan.PlanPreviewData
import com.github.daanbouwman.flightplanner.ui.plan.RouteCard
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/**
 * The three primary list cards, as an accessibility service meets them.
 *
 * Each card speaks one sentence, and the point being pinned is that **the node
 * carrying that sentence is the one that is clickable** and the one that carries
 * the swipe actions as custom actions — a description on one node and a click
 * on another is a card a service can read and cannot open.
 *
 * ### Asked of the accessibility bridge, not only of the semantics tree
 *
 * The first version of this test asked the semantics tree and passed while a
 * `uiautomator dump` of the same build said `clickable="false"` on the node
 * with the sentence. Both were right. A node that merges its descendants and
 * has a description of its own *and* children is exported by Compose's
 * accessibility delegate as two `AccessibilityNodeInfo`s: the description is
 * moved onto a synthetic first child, so a screen reader speaks it before the
 * children's text, and the click stays on the parent
 * (`AndroidComposeViewAccessibilityDelegateCompat`, the `contentDescription`
 * branch of `populateAccessibilityNodeInfoProperties`). The merged semantics
 * tree the test framework queries never shows that child, so
 * `assertHasClickAction` on the described node cannot fail for it.
 *
 * [exported] therefore asks the real bridge — the `AccessibilityNodeProvider`
 * the compose view installs — for the card's own node and asserts that the
 * description and the click are on it, and asks for the synthetic child by the
 * id Compose would give it and asserts it is not there. The last test composes
 * the old merged shape on purpose and asserts the bridge *does* split it, so
 * the assertion is known to detect what it guards against. The semantics-tree
 * assertions stay as the readable statement of the same fact.
 *
 * `GraphicsMode.NATIVE` is load-bearing. The delegate decides which nodes are
 * exported by intersecting their bounds with the space not yet covered, using
 * `android.graphics.Region`; Robolectric's default graphics mode stubs `Region`
 * so that no intersection ever succeeds, and the bridge then exports the host
 * and nothing else — every lookup below returned null until the mode was
 * switched. Native mode runs the real Skia `Region`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CardSemanticsTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var composeView: View

    private fun hasCustomAction(label: String) = SemanticsMatcher("has custom action '$label'") { node ->
        node.config.getOrNull(SemanticsActions.CustomActions)?.any { it.label == label } == true
    }

    private fun setCard(content: @Composable () -> Unit) {
        compose.setContent {
            composeView = LocalView.current
            FlightPlannerTheme(dynamicColor = false) { content() }
        }
        // With the manager disabled the delegate answers an unknown id with an
        // empty node rather than null (its Assistant path); enabled, a missing
        // node is null, which is the answer the fake-child assertion needs.
        val manager = composeView.context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        shadowOf(manager).setEnabled(true)
    }

    /**
     * What the accessibility bridge exports for the node matched by [matcher],
     * plus whatever it exports for the synthetic description child Compose
     * emits beside a merging node that has its own description.
     *
     * The framework provider, not `ViewCompat`'s wrapper: the compat class's
     * `createAccessibilityNodeInfo` is a template method that returns null
     * unless overridden, so asking the wrapper asks nobody.
     */
    private fun exported(matcher: SemanticsMatcher): Pair<AccessibilityNodeInfo, AccessibilityNodeInfo?> {
        val id = compose.onNode(matcher).fetchSemanticsNode().id
        val provider: AccessibilityNodeProvider = composeView.accessibilityNodeProvider.shouldNotBeNull()
        val own = provider.createAccessibilityNodeInfo(id).shouldNotBeNull()
        // `ContentDescriptionFakeNodeIdOffset` in Compose UI's SemanticsNode.kt: the
        // node's own id plus two billion (one billion is the Role child). Internal, so restated
        // here; the merged-shape test below is what keeps the offset honest.
        val fakeChild = provider.createAccessibilityNodeInfo(id + CONTENT_DESCRIPTION_FAKE_NODE_OFFSET)
        return own to fakeChild
    }

    private fun assertOneClickableNode(matcher: SemanticsMatcher, sentenceContains: String) {
        val (own, fakeChild) = exported(matcher)
        own.isClickable shouldBe true
        own.contentDescription.shouldNotBeNull().toString() shouldContain sentenceContains
        fakeChild.shouldBeNull()
    }

    @Test
    fun `a route card is one clickable node carrying its sentence and both swipe actions`() {
        setCard {
            RouteCard(
                row = PlanPreviewData.longHaul,
                outline = WorldOutline.Empty,
                onClick = {},
                onMarkFlown = {},
                onReplace = {},
                departureMetar = metar("EHAM", FlightRules.VFR),
            )
        }

        val card = hasContentDescription("EHAM", substring = true)
        compose.onNode(card)
            .assertHasClickAction()
            .assert(hasCustomAction("Mark flown"))
            .assert(hasCustomAction("Replace"))
        assertOneClickableNode(card, sentenceContains = "nautical miles")

        // Clearing the children's semantics is only honest if the sentence says
        // what they printed: the runway figures and the resolved category.
        val sentence = exported(card).first.contentDescription.toString()
        sentence shouldContain "longest runway"
        sentence shouldContain "EHAM Visual Flight Rules"
    }

    @Test
    fun `a fleet row is one clickable node carrying its sentence and the flown toggle`() {
        setCard { FleetRowCard(aircraft = boeing, onClick = {}, onToggleFlown = {}) }

        val card = hasContentDescription("Boeing 737-800", substring = true)
        compose.onNode(card)
            .assertHasClickAction()
            .assert(hasCustomAction("Mark flown"))
        assertOneClickableNode(card, sentenceContains = "Boeing 737-800")
    }

    @Test
    fun `a logbook row is one clickable node carrying its sentence and the delete action`() {
        setCard {
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

        val card = hasContentDescription("EHAM to EGLL", substring = true)
        compose.onNode(card)
            .assertHasClickAction()
            .assert(hasCustomAction("Delete from logbook"))
        assertOneClickableNode(card, sentenceContains = "EHAM to EGLL")
    }

    @Test
    fun `the card's click reaches the caller through its own semantics action`() {
        // `clearAndSetSemantics` drops the Card's built-in click semantics, so the
        // action a service performs is the one the card restates. It must call
        // the same lambda a tap does.
        var clicks = 0
        setCard { FleetRowCard(aircraft = boeing, onClick = { clicks++ }, onToggleFlown = {}) }

        val node = compose.onNode(hasContentDescription("Boeing 737-800", substring = true)).fetchSemanticsNode()
        node.config[SemanticsActions.OnClick].action.shouldNotBeNull().invoke() shouldBe true
        clicks shouldBe 1
    }

    @Test
    fun `a merging card with children is the shape the bridge splits, which is why the cards clear instead`() {
        // The shape the three cards had before: one merged node with its own
        // sentence over children that print. The semantics tree shows one
        // clickable node with the sentence; the bridge shows the click on the
        // card and the sentence on a synthetic child that is not clickable.
        setCard {
            Card(
                onClick = {},
                modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = "the sentence" },
            ) {
                Column { Text("a child that prints") }
            }
        }

        val card = hasContentDescription("the sentence")
        compose.onNode(card).assertHasClickAction()

        val (own, fakeChild) = exported(card)
        own.isClickable shouldBe true
        own.contentDescription.shouldBeNull()
        fakeChild.shouldNotBeNull().contentDescription.toString() shouldBe "the sentence"
        fakeChild.isClickable shouldBe false
    }

    private companion object {
        const val CONTENT_DESCRIPTION_FAKE_NODE_OFFSET = 2_000_000_000
    }
}

private fun metar(station: String, rules: FlightRules) = Metar(
    station = station,
    raw = "$station 121225Z 24012KT 9999 FEW040 18/09 Q1015",
    flightRules = rules,
)

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
