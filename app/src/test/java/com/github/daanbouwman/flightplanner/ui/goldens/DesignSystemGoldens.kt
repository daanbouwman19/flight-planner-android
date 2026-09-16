package com.github.daanbouwman.flightplanner.ui.goldens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.daanbouwman.flightplanner.core.designsystem.components.EmptyState
import com.github.daanbouwman.flightplanner.core.designsystem.components.ErrorState
import com.github.daanbouwman.flightplanner.core.designsystem.components.FilterField
import com.github.daanbouwman.flightplanner.core.designsystem.components.FlightRulesBadge
import com.github.daanbouwman.flightplanner.core.designsystem.components.ModeOption
import com.github.daanbouwman.flightplanner.core.designsystem.components.ModeSelector
import com.github.daanbouwman.flightplanner.core.designsystem.components.MonthHeader
import com.github.daanbouwman.flightplanner.core.designsystem.components.MorphingLoadingIndicator
import com.github.daanbouwman.flightplanner.core.designsystem.components.NetworkMap
import com.github.daanbouwman.flightplanner.core.designsystem.components.NetworkNode
import com.github.daanbouwman.flightplanner.core.designsystem.components.RouteMap
import com.github.daanbouwman.flightplanner.core.designsystem.components.RunwayDiagram
import com.github.daanbouwman.flightplanner.core.designsystem.components.SkeletonCard
import com.github.daanbouwman.flightplanner.core.designsystem.components.SkyPhase
import com.github.daanbouwman.flightplanner.core.designsystem.components.SkyProfile
import com.github.daanbouwman.flightplanner.core.designsystem.components.SkyProfileHeight
import com.github.daanbouwman.flightplanner.core.designsystem.components.StatSummaryStrip
import com.github.daanbouwman.flightplanner.core.designsystem.components.StatTile
import com.github.daanbouwman.flightplanner.core.designsystem.components.ValueChip
import com.github.daanbouwman.flightplanner.model.FlightRules
import com.github.daanbouwman.flightplanner.ui.plan.PlanPreviewData
import com.github.daanbouwman.flightplanner.ui.plan.rememberPreviewWorldOutline
import org.junit.Test
import org.robolectric.annotation.Config
import java.util.Locale

/**
 * The `:core:designsystem` components, goldened from here rather than from their
 * own module: `:app` already carries the Robolectric and Compose test stack and
 * depends on the design system, so a change there fails this suite the same way.
 * Each state is a column of a family; the primary one is the atoms every screen
 * is built from.
 */
class DesignSystemGoldens : GoldenSuite(subject = "designsystem", primaryState = "atoms") {

    @Composable
    override fun Primary() {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            ModeSelector(
                options = listOf(ModeOption("All"), ModeOption("Not flown", count = 116), ModeOption("This aircraft")),
                selectedIndex = 1,
                onSelect = {},
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterField(
                    label = "DEPARTURE",
                    value = "EHAM",
                    detail = "Amsterdam Airport Schiphol",
                    selected = true,
                    onClick = {},
                    modifier = Modifier.weight(1f),
                )
                FilterField(label = "AIRCRAFT", value = "Any", selected = false, onClick = {}, modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ValueChip(label = "DIST", value = "3,451 nm")
                ValueChip(label = "TIME", value = "07:12")
                ValueChip(label = "RWY", value = "12,467 ft")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FlightRules.entries.forEach { FlightRulesBadge(it) }
            }
            StatSummaryStrip(
                tiles = listOf(
                    StatTile(label = "FLIGHTS", value = 42),
                    StatTile(label = "NM", value = 48_213, format = { String.format(Locale.ROOT, "%,d", it) }),
                    StatTile(
                        label = "HOURS",
                        value = 3_805,
                        format = { String.format(Locale.ROOT, "%d:%02d", it / 60, it % 60) },
                    ),
                ),
            )
            MonthHeader(label = "August 2026")
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                MorphingLoadingIndicator()
                MorphingLoadingIndicator(contained = true)
            }
            SkeletonCard()
        }
    }

    @Test
    @Config(qualifiers = GoldenQualifiers.COMPACT)
    fun states() = captureState("states") {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            EmptyState(
                title = "No flights logged",
                message = "Start your journey by generating a flight plan.",
                actionLabel = "Generate routes",
                onAction = {},
            )
            EmptyState(title = "Nothing here", message = "A message with no action beneath it.")
            ErrorState(
                title = "Could not load weather",
                message = "No connection to the weather service. Routes still work offline.",
                onRetry = {},
            )
        }
    }

    @Test
    @Config(qualifiers = GoldenQualifiers.COMPACT)
    fun maps() = captureState("maps") {
        val outline = rememberPreviewWorldOutline()
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(modifier = Modifier.fillMaxWidth().height(180.dp)) {
                RouteMap(arc = PlanPreviewData.longHaul.arc, outline = outline, modifier = Modifier.fillMaxSize())
            }
            Box(modifier = Modifier.fillMaxWidth().height(180.dp)) {
                NetworkMap(
                    nodes = listOf(
                        NetworkNode(PlanPreviewData.schiphol.latitude, PlanPreviewData.schiphol.longitude, visits = 6),
                        NetworkNode(PlanPreviewData.kennedy.latitude, PlanPreviewData.kennedy.longitude, visits = 2),
                        NetworkNode(PlanPreviewData.haneda.latitude, PlanPreviewData.haneda.longitude, visits = 1),
                        NetworkNode(PlanPreviewData.rome.latitude, PlanPreviewData.rome.longitude, visits = 3),
                    ),
                    legs = listOf(
                        PlanPreviewData.longHaul.arc,
                        PlanPreviewData.transatlantic.arc,
                        PlanPreviewData.northSouth.arc,
                    ),
                    outline = outline,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            RunwayDiagram(
                runways = PlanPreviewData.schipholRunways,
                contentDescription = "Runway diagram of Schiphol",
                modifier = Modifier.fillMaxWidth().height(280.dp),
            )
        }
    }

    @Test
    @Config(qualifiers = GoldenQualifiers.COMPACT)
    fun sky() = captureState("sky") {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SkyPhase.entries.forEach { phase ->
                SkyProfile(
                    metar = schipholVfrMetar,
                    contentDescription = "VFR sky, $phase",
                    phase = phase,
                    height = SkyProfileHeight.RouteDetail,
                )
            }
            SkyProfile(
                metar = heathrowIfrMetar,
                contentDescription = "IFR sky, day",
                phase = SkyPhase.DAY,
                height = SkyProfileHeight.RouteDetail,
            )
        }
    }
}
