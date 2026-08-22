package com.github.daanbouwman.flightplanner.ui.fleet

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.daanbouwman.flightplanner.R
import com.github.daanbouwman.flightplanner.core.designsystem.components.CompactWidthPreview
import com.github.daanbouwman.flightplanner.core.designsystem.components.ConfirmationDialog
import com.github.daanbouwman.flightplanner.core.designsystem.components.DevicePreviews
import com.github.daanbouwman.flightplanner.core.designsystem.components.EmptyState
import com.github.daanbouwman.flightplanner.core.designsystem.components.LightDarkPreview
import com.github.daanbouwman.flightplanner.core.designsystem.components.ModeOption
import com.github.daanbouwman.flightplanner.core.designsystem.components.ModeSelector
import com.github.daanbouwman.flightplanner.core.designsystem.components.MonthHeader
import com.github.daanbouwman.flightplanner.core.designsystem.components.SkeletonCard
import com.github.daanbouwman.flightplanner.core.designsystem.motion.FlightMotion
import com.github.daanbouwman.flightplanner.core.designsystem.theme.FlightPlannerTheme
import com.github.daanbouwman.flightplanner.core.designsystem.theme.FlightShapes
import com.github.daanbouwman.flightplanner.core.designsystem.theme.asChartFigure
import com.github.daanbouwman.flightplanner.core.designsystem.theme.rememberMorphShape
import com.github.daanbouwman.flightplanner.model.AircraftSpec
import com.github.daanbouwman.flightplanner.ui.SettingsAction
import com.github.daanbouwman.flightplanner.ui.asFigure
import com.github.daanbouwman.flightplanner.ui.chrome.MaxContentWidth
import com.github.daanbouwman.flightplanner.ui.chrome.ScreenBottomGutter
import com.github.daanbouwman.flightplanner.ui.chrome.ScreenCompactTopGutter
import com.github.daanbouwman.flightplanner.ui.chrome.ScreenHorizontalGutter
import com.github.daanbouwman.flightplanner.ui.chrome.ScreenTopGutter
import com.github.daanbouwman.flightplanner.ui.chrome.ScrollToTopOnReselect
import com.github.daanbouwman.flightplanner.ui.chrome.isCompactHeight
import com.github.daanbouwman.flightplanner.ui.chrome.rememberChromeScrollConnection
import com.github.daanbouwman.flightplanner.ui.chrome.rememberContentInsets
import kotlinx.coroutines.delay
import androidx.compose.runtime.LaunchedEffect

/**
 * The Fleet screen: every airframe, filterable and toggled flown with a tap.
 *
 * Structured like [com.github.daanbouwman.flightplanner.ui.plan.PlanScreen] —
 * the header and the mode selector are the list's own first item rather than a
 * bar over it, and the navigation suite is the only chrome that hides on a
 * scroll signal — because Fleet is a top-level bar destination in the same
 * sense Plan is, not a segment hosted inside Profile. It carries none of
 * Plan's infinite-scroll or pull-to-refresh machinery: the fleet is a few
 * hundred rows at most, loaded in one read.
 */
@Composable
fun FleetScreen(
    onOpenAircraft: (AircraftSpec) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FleetViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    ScrollToTopOnReselect(listState = listState)
    val chromeScroll = rememberChromeScrollConnection(listState = listState)
    val contentInsets = rememberContentInsets()

    var showAddSheet by rememberSaveable { mutableStateOf(false) }
    var pendingAction by rememberSaveable { mutableStateOf<FleetManagementAction?>(null) }

    Box(modifier = modifier.fillMaxSize()) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .then(contentInsets.modifier)
                .nestedScroll(chromeScroll),
        ) {
            val availableWidth = maxWidth
            val insets = contentInsets.asPaddingValues()
            val layoutDirection = LocalLayoutDirection.current
            val compactHeight = isCompactHeight()
            val slack = if (compactHeight) {
                0.dp
            } else {
                ((availableWidth - MaxContentWidth) / 2).coerceAtLeast(0.dp)
            }
            // A sticky category header pins to the LazyColumn's own top edge, not
            // to its contentPadding — Compose's stickyHeader ignores contentPadding
            // entirely when it clamps a header's pinned offset, so padding meant to
            // clear the status bar would apply to ordinary rows and be skipped by
            // the one element that actually needs it. Reserved as real layout space
            // on the list itself instead, below, which a pinned header cannot cross.
            // Cards never reach that edge anyway: whichever group is active always
            // has its own header pinned there first, so nothing is lost by not
            // letting content pass under the clock here the way Plan's cards do.
            val topClearance = insets.calculateTopPadding() +
                if (compactHeight) ScreenCompactTopGutter else ScreenTopGutter
            val contentPadding = PaddingValues(
                start = insets.calculateStartPadding(layoutDirection) + ScreenHorizontalGutter + slack,
                end = insets.calculateEndPadding(layoutDirection) + ScreenHorizontalGutter + slack,
                // Room for the FAB, on top of the list's own bottom gutter — the last
                // card would otherwise sit half-covered by it, which no scroll
                // position brings back.
                bottom = insets.calculateBottomPadding() + ScreenBottomGutter + FabClearance,
            )

            FleetContent(
                state = state,
                listState = listState,
                contentPadding = contentPadding,
                modifier = Modifier.padding(top = topClearance),
                header = {
                    FleetHeader(
                        state = state,
                        onModeChange = viewModel::setMode,
                        onOpenSettings = onOpenSettings,
                        onMarkAllNotFlown = { pendingAction = FleetManagementAction.MarkAllNotFlown },
                        onRestoreDefaults = { pendingAction = FleetManagementAction.RestoreDefaults },
                    )
                },
                onOpenAircraft = onOpenAircraft,
                onToggleFlown = viewModel::toggleFlown,
            )
        }

        // The default (M3 spec calls it "medium") `FloatingActionButton` size —
        // "large" was tried first and was wrong: the spec's own default *is*
        // this size, not large, so sizing it up was a genuine miss rather than
        // a stylistic choice, and it read as an oversized button with nothing
        // backing the size up. What Expressive actually prescribes for a FAB
        // is motion: its shape morphs on press. `FlightShapes.Circle` →
        // `FlightShapes.Cookie` is that exact pair, already named in this
        // design system for precisely this button (its own KDoc still says
        // "the generate FAB" — orphaned when Plan's FAB was cut, and legitimate
        // to revive here). `MorphShape` is the piece material3 does not ship:
        // a `Shape` that reads a `Morph` at an animated progress, built for
        // exactly `Box(Modifier.clip(rememberMorphShape(Morph(...), progress)))`.
        val fabInteractionSource = remember { MutableInteractionSource() }
        val fabPressed by fabInteractionSource.collectIsPressedAsState()
        val fabMorphProgress by animateFloatAsState(
            targetValue = if (fabPressed) 1f else 0f,
            animationSpec = FlightMotion.spatialFast(),
            label = "fab-morph",
        )
        val fabMorph = remember { Morph(FlightShapes.Circle, FlightShapes.Cookie) }

        FloatingActionButton(
            onClick = { showAddSheet = true },
            interactionSource = fabInteractionSource,
            shape = rememberMorphShape(fabMorph, fabMorphProgress),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                .padding(16.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_fleet_add),
                contentDescription = stringResource(R.string.fleet_action_add_aircraft),
            )
        }
    }

    if (showAddSheet) {
        AddAircraftSheet(
            existingCategories = state.categories,
            onSubmit = { spec ->
                viewModel.addAircraft(spec)
                showAddSheet = false
            },
            onDismiss = { showAddSheet = false },
        )
    }

    pendingAction?.let { action ->
        ConfirmationDialog(
            title = stringResource(action.titleRes),
            message = stringResource(action.messageRes),
            confirmLabel = stringResource(action.confirmRes),
            onConfirm = {
                when (action) {
                    FleetManagementAction.MarkAllNotFlown -> viewModel.markAllNotFlown()
                    FleetManagementAction.RestoreDefaults -> viewModel.restoreDefaults()
                }
                pendingAction = null
            },
            onDismiss = { pendingAction = null },
        )
    }
}

/** One overflow action behind a confirmation, and the copy it confirms with. */
private enum class FleetManagementAction(val titleRes: Int, val messageRes: Int, val confirmRes: Int) {
    MarkAllNotFlown(
        R.string.fleet_confirm_mark_all_not_flown_title,
        R.string.fleet_confirm_mark_all_not_flown_message,
        R.string.fleet_confirm_mark_all_not_flown_action,
    ),
    RestoreDefaults(
        R.string.fleet_confirm_restore_defaults_title,
        R.string.fleet_confirm_restore_defaults_message,
        R.string.fleet_confirm_restore_defaults_action,
    ),
}

/** The stateless half, so a preview can render every state without a Hilt ViewModel. */
@Composable
internal fun FleetContent(
    state: FleetUiState,
    listState: LazyListState,
    contentPadding: PaddingValues,
    header: @Composable () -> Unit,
    onOpenAircraft: (AircraftSpec) -> Unit,
    onToggleFlown: (AircraftSpec) -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        state.status == FleetStatus.Loading -> HeadedState(header, contentPadding, modifier) {
            DelayedSkeletonList()
        }

        state.isEmpty -> HeadedState(header, contentPadding, modifier) {
            EmptyState(
                title = stringResource(R.string.fleet_empty_title),
                message = stringResource(R.string.fleet_empty_message),
            )
        }

        state.isNoMatch -> HeadedState(header, contentPadding, modifier) {
            EmptyState(
                title = stringResource(R.string.fleet_no_match_title),
                message = stringResource(R.string.fleet_no_match_message),
            )
        }

        else -> FleetList(
            state = state,
            onOpenAircraft = onOpenAircraft,
            onToggleFlown = onToggleFlown,
            listState = listState,
            contentPadding = contentPadding,
            header = header,
            modifier = modifier,
        )
    }
}

@Composable
private fun HeadedState(
    header: @Composable () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding),
    ) {
        header()
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}

/** As [com.github.daanbouwman.flightplanner.ui.plan.PlanScreen]'s own delayed skeleton: nothing flashes for one frame. */
@Composable
private fun DelayedSkeletonList(modifier: Modifier = Modifier) {
    var visible by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(SkeletonDelayMillis)
        visible = true
    }
    if (!visible) return

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(SkeletonCount) { SkeletonCard() }
    }
}

@Composable
private fun FleetList(
    state: FleetUiState,
    onOpenAircraft: (AircraftSpec) -> Unit,
    onToggleFlown: (AircraftSpec) -> Unit,
    listState: LazyListState,
    contentPadding: PaddingValues,
    header: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = HeaderKey) { header() }

        state.groups.forEach { group ->
            stickyHeader(key = "category-${group.category}") {
                MonthHeader(label = group.category)
            }

            items(group.aircraft, key = { it.id }) { aircraft ->
                FleetRowCard(
                    aircraft = aircraft,
                    onClick = { onOpenAircraft(aircraft) },
                    onToggleFlown = { onToggleFlown(aircraft) },
                    modifier = Modifier.animateItem(
                        fadeInSpec = null,
                        fadeOutSpec = null,
                        placementSpec = FlightMotion.spatial(),
                    ),
                )
            }
        }
    }
}

@Composable
private fun FleetHeader(
    state: FleetUiState,
    onModeChange: (FleetMode) -> Unit,
    onOpenSettings: () -> Unit,
    onMarkAllNotFlown: () -> Unit,
    onRestoreDefaults: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val compactHeight = isCompactHeight()
    var menuExpanded by rememberSaveable { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.destination_fleet),
                style = if (compactHeight) {
                    MaterialTheme.typography.titleLarge
                } else {
                    MaterialTheme.typography.headlineMedium
                },
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_fleet_more),
                        contentDescription = stringResource(R.string.fleet_overflow_content_description),
                    )
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.fleet_action_mark_all_not_flown)) },
                        onClick = { menuExpanded = false; onMarkAllNotFlown() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.fleet_action_restore_defaults)) },
                        onClick = { menuExpanded = false; onRestoreDefaults() },
                    )
                }
            }
            SettingsAction(onClick = onOpenSettings)
        }
        Spacer(Modifier.height(if (compactHeight) 6.dp else 12.dp))
        ModeSelector(
            options = listOf(
                ModeOption(stringResource(R.string.fleet_mode_all)),
                ModeOption(stringResource(R.string.fleet_mode_flown), count = state.flownCount),
                ModeOption(stringResource(R.string.fleet_mode_not_flown), count = state.notFlownCount),
            ),
            selectedIndex = state.mode.ordinal,
            onSelect = { index -> onModeChange(FleetMode.entries[index]) },
        )
    }
}

@Composable
private fun FleetRowCard(
    aircraft: AircraftSpec,
    onClick: () -> Unit,
    onToggleFlown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val envelope = stringResource(
        R.string.plan_filter_envelope,
        aircraft.rangeNm.asFigure(),
        aircraft.requiredRunwayFt.asFigure(),
    )
    val flownLabel = stringResource(
        if (aircraft.flown) R.string.fleet_row_flown_spoken else R.string.fleet_row_not_flown_spoken,
    )
    val description = stringResource(
        R.string.fleet_row_content_description,
        aircraft.displayName,
        aircraft.category,
        envelope,
        flownLabel,
    )
    val toggleActionLabel = stringResource(
        if (aircraft.flown) R.string.fleet_action_mark_not_flown else R.string.fleet_action_mark_flown,
    )

    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = description
                customActions = listOf(
                    CustomAccessibilityAction(toggleActionLabel) {
                        onToggleFlown()
                        true
                    },
                )
            },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = aircraft.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = envelope,
                    style = MaterialTheme.typography.bodySmall.asChartFigure(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // A `FilterChip`, not a `Switch`: the mode row above already
            // establishes this screen's own selection grammar — filled and
            // nothing else means "on" — and a stock system switch reads as
            // settings chrome dropped into an otherwise chart-styled screen.
            // Reusing the chip carries the state in the same vocabulary the
            // header just taught, rather than introducing a second one.
            FilterChip(
                selected = aircraft.flown,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    onToggleFlown()
                },
                label = {
                    Text(
                        stringResource(
                            if (aircraft.flown) R.string.fleet_detail_flown else R.string.fleet_detail_not_flown,
                        ),
                    )
                },
                // The row already carries the toggle as a custom accessibility
                // action, merged onto the card's own node — a nested clickable
                // chip would otherwise announce as a second, redundant control.
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
    }
}

private const val SkeletonCount = 5
private const val SkeletonDelayMillis = 150L
private const val HeaderKey = "header"

/** Extra bottom room so the FAB (56 dp, plus its own margin) never sits over the last card. */
private val FabClearance = 112.dp

/*
 * Previews.
 *
 * `FleetScreen` takes a Hilt ViewModel and cannot be rendered by the tooling,
 * so these target `FleetContent`, the stateless half — the same split
 * `PlanScreen`/`PlanContent` and `LogbookScreen`/`LogbookContent` use.
 */

@Composable
private fun PreviewFleet(state: FleetUiState) {
    FlightPlannerTheme(dynamicColor = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            FleetContent(
                state = state,
                listState = rememberLazyListState(),
                contentPadding = PaddingValues(16.dp),
                header = {
                    Text(
                        text = "Fleet",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                },
                onOpenAircraft = {},
                onToggleFlown = {},
            )
        }
    }
}

private val previewFleet = listOf(
    AircraftSpec(
        id = 1,
        manufacturer = "Boeing",
        variant = "737-800",
        icaoCode = "B738",
        flown = true,
        rangeNm = 3115,
        category = "Jet",
        cruiseSpeedKt = 450,
        dateFlown = "2026-08-01",
        takeoffDistanceMeters = 2500,
    ),
    AircraftSpec(
        id = 2,
        manufacturer = "Cessna",
        variant = "172",
        icaoCode = "C172",
        flown = false,
        rangeNm = 640,
        category = "General aviation",
        cruiseSpeedKt = 122,
        dateFlown = null,
        takeoffDistanceMeters = 400,
    ),
)

@LightDarkPreview
@CompactWidthPreview
@DevicePreviews
@Composable
private fun FleetPopulatedPreview() {
    PreviewFleet(
        FleetUiState(
            groups = previewFleet.groupByCategory(),
            mode = FleetMode.All,
            totalCount = previewFleet.size,
            flownCount = 1,
            notFlownCount = 1,
            status = FleetStatus.Ready,
        ),
    )
}

@LightDarkPreview
@Composable
private fun FleetEmptyPreview() {
    PreviewFleet(FleetUiState(status = FleetStatus.Ready))
}

@LightDarkPreview
@Composable
private fun FleetNoMatchPreview() {
    PreviewFleet(FleetUiState(mode = FleetMode.Flown, totalCount = 2, status = FleetStatus.Ready))
}

@LightDarkPreview
@Composable
private fun FleetLoadingPreview() {
    PreviewFleet(FleetUiState(status = FleetStatus.Loading))
}
