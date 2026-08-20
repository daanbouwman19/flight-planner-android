package com.github.daanbouwman.flightplanner.ui.logbook

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.daanbouwman.flightplanner.R
import com.github.daanbouwman.flightplanner.core.designsystem.components.CompactWidthPreview
import com.github.daanbouwman.flightplanner.core.designsystem.components.DevicePreviews
import com.github.daanbouwman.flightplanner.core.designsystem.components.EmptyState
import com.github.daanbouwman.flightplanner.core.designsystem.components.LightDarkPreview
import com.github.daanbouwman.flightplanner.core.designsystem.components.MonthHeader
import com.github.daanbouwman.flightplanner.core.designsystem.components.SkeletonCard
import com.github.daanbouwman.flightplanner.core.designsystem.components.StatSummaryStrip
import com.github.daanbouwman.flightplanner.core.designsystem.components.StatTile
import com.github.daanbouwman.flightplanner.core.designsystem.components.SwipeActionBackground
import com.github.daanbouwman.flightplanner.core.designsystem.components.SwipeActionSide
import com.github.daanbouwman.flightplanner.core.designsystem.motion.FlightMotion
import com.github.daanbouwman.flightplanner.core.designsystem.theme.FlightPlannerTheme
import com.github.daanbouwman.flightplanner.core.designsystem.theme.asChartFigure
import com.github.daanbouwman.flightplanner.core.designsystem.theme.withTabularFigures
import com.github.daanbouwman.flightplanner.routing.FlightTime
import com.github.daanbouwman.flightplanner.ui.asFigure
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.abs

/** The Hilt-wired Logbook segment: collects [LogbookViewModel.uiState] and renders it. */
@Composable
fun LogbookScreen(
    onOpenRoute: (LogbookRow) -> Unit,
    listState: LazyListState,
    contentPadding: PaddingValues,
    header: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LogbookViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val deletedMessage = stringResource(R.string.logbook_flight_deleted)
    val undoLabel = stringResource(R.string.plan_action_undo)

    LaunchedEffect(viewModel.events) {
        viewModel.events.collectLatest { event ->
            when (event) {
                LogbookEvent.FlightDeleted -> {
                    val result = snackbarHostState.showSnackbar(
                        message = deletedMessage,
                        actionLabel = undoLabel,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.undoDelete()
                    }
                }
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LogbookContent(
            state = state,
            onOpenRoute = onOpenRoute,
            onDeleteRoute = viewModel::delete,
            listState = listState,
            contentPadding = contentPadding,
            header = header,
        )

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp),
        )
    }
}

/** The stateless half, so a preview can render every state without a Hilt ViewModel. */
@Composable
internal fun LogbookContent(
    state: LogbookUiState,
    onOpenRoute: (LogbookRow) -> Unit,
    onDeleteRoute: (LogbookRow) -> Unit,
    listState: LazyListState,
    contentPadding: PaddingValues,
    header: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        state.status == LogbookStatus.Loading -> HeadedState(header, contentPadding, modifier) {
            DelayedSkeletonList()
        }

        state.isEmpty -> HeadedState(header, contentPadding, modifier) {
            EmptyState(
                title = stringResource(R.string.logbook_empty_title),
                message = stringResource(R.string.logbook_empty_message),
            )
        }

        else -> LogbookList(
            state = state,
            onOpenRoute = onOpenRoute,
            onDeleteRoute = onDeleteRoute,
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
    var visible by remember { mutableStateOf(false) }
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
private fun LogbookList(
    state: LogbookUiState,
    onOpenRoute: (LogbookRow) -> Unit,
    onDeleteRoute: (LogbookRow) -> Unit,
    listState: LazyListState,
    contentPadding: PaddingValues,
    header: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val flightsLabel = stringResource(R.string.logbook_summary_flights)
    val distanceLabel = stringResource(R.string.logbook_summary_distance)
    val hoursLabel = stringResource(R.string.logbook_summary_hours)

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = HeaderKey) { header() }

        item(key = SummaryKey) {
            StatSummaryStrip(
                tiles = listOf(
                    StatTile(label = flightsLabel, value = state.summary.flights),
                    StatTile(
                        label = distanceLabel,
                        value = state.summary.distanceNm,
                        format = { it.asFigure() },
                    ),
                    StatTile(
                        label = hoursLabel,
                        value = state.summary.minutesFlown,
                        // Reuses FlightTime's own "2:35" convention rather than
                        // re-deriving it — effectiveSpeedKt is meaningless for a
                        // summed duration, so it's a throwaway 0.0 here.
                        format = { minutes -> FlightTime(minutes / 60, minutes % 60, 0.0).format() },
                    ),
                ),
            )
        }

        state.groups.forEach { group ->
            stickyHeader(key = "month-${group.key}") {
                MonthHeader(label = group.key.monthLabel())
            }

            items(group.rows, key = { it.id }) { row ->
                SwipeToDeleteRow(
                    onDelete = { onDeleteRoute(row) },
                ) {
                    LogbookRowCard(
                        row = row,
                        onClick = { onOpenRoute(row) },
                        onDelete = { onDeleteRoute(row) },
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToDeleteRow(
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    var widthPx by remember { mutableIntStateOf(0) }

    val dismissState = rememberSwipeToDismissBoxState(
        positionalThreshold = { distance -> distance * CommitFraction },
    )

    fun dragFraction(): Float {
        if (widthPx <= 0) return 0f
        val offset = runCatching { dismissState.requireOffset() }.getOrDefault(0f)
        return (abs(offset) / widthPx).coerceIn(0f, 1f)
    }

    val commitProgress = (dragFraction() / CommitFraction).coerceIn(0f, 1f)
    val committed = commitProgress >= 1f

    LaunchedEffect(dismissState) {
        if (dismissState.currentValue != SwipeToDismissBoxValue.Settled) {
            runCatching { dismissState.reset() }
        }
    }

    LaunchedEffect(dismissState) {
        snapshotFlow { dismissState.targetValue }
            .distinctUntilChanged()
            .filter { it != SwipeToDismissBoxValue.Settled }
            .collect { haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick) }
    }

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier.onSizeChanged { widthPx = it.width },
        enableDismissFromStartToEnd = false,
        onDismiss = { direction ->
            if (direction == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
            }
        },
        backgroundContent = {
            if (dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart) {
                SwipeActionBackground(
                    side = SwipeActionSide.End,
                    icon = painterResource(R.drawable.ic_plan_clear),
                    label = stringResource(R.string.logbook_swipe_delete),
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                    progress = commitProgress,
                    committed = committed,
                )
            }
        },
        content = { content() },
    )
}

/** "August 2026" — prose, not a chart figure, so it follows the device locale rather than [Locale.ROOT]. */
private fun YearMonth.monthLabel(): String =
    format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()))

@Composable
private fun LogbookRowCard(
    row: LogbookRow,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val aircraftName = row.aircraftDisplayName
        ?: stringResource(R.string.logbook_aircraft_unknown_format, row.aircraftId)
    val distanceText = row.distanceNm?.let { stringResource(R.string.plan_value_nautical_miles, it.asFigure()) }
        ?: stringResource(R.string.logbook_row_distance_placeholder)
    val durationText = row.flightTime?.format() ?: stringResource(R.string.logbook_row_duration_placeholder)

    // Spoken separately from what is drawn: a screen reader needs a sentence,
    // not the fixed-locale chart figures the row itself shows.
    val distanceSpoken = row.distanceNm?.let { stringResource(R.string.logbook_row_distance_spoken, it) }
        ?: stringResource(R.string.logbook_row_distance_unknown)
    val durationSpoken = row.flightTime?.let {
        stringResource(R.string.logbook_row_duration_spoken, it.hours, it.minutes)
    } ?: stringResource(R.string.logbook_row_duration_unknown)
    val dateSpoken = row.date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG))
    val description = stringResource(
        R.string.logbook_row_content_description,
        aircraftName,
        row.departureIcao,
        row.arrivalIcao,
        dateSpoken,
        distanceSpoken,
        durationSpoken,
    )
    val deleteActionLabel = stringResource(R.string.logbook_action_delete)

    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = description
                customActions = listOf(
                    CustomAccessibilityAction(deleteActionLabel) {
                        onDelete()
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
            // The sticky header above already names the month and year, so the
            // day is all this needs to say.
            Text(
                text = row.date.dayOfMonth.toString(),
                style = MaterialTheme.typography.headlineSmall.withTabularFigures(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(32.dp),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "${row.departureIcao} → ${row.arrivalIcao}",
                    style = MaterialTheme.typography.titleSmall.asChartFigure(),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = aircraftName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = distanceText,
                    style = MaterialTheme.typography.labelSmall.asChartFigure(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = durationText,
                    style = MaterialTheme.typography.labelSmall.asChartFigure(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private const val SkeletonCount = 5

/**
 * How far a card must travel before releasing commits.
 *
 * Matches PlanScreen's own bound.
 */
private const val CommitFraction = 0.33f

/** Below this, the read arrives before a placeholder could be understood. Matches PlanScreen's own bound. */
private const val SkeletonDelayMillis = 150L

private const val HeaderKey = "header"
private const val SummaryKey = "summary"

/*
 * Previews.
 *
 * `LogbookScreen` takes a Hilt ViewModel and cannot be rendered by the
 * tooling, so these target `LogbookContent`, the stateless half — the same
 * split `PlanScreen`/`PlanContent` uses and for the same reason.
 */

@Composable
private fun PreviewLogbook(state: LogbookUiState) {
    FlightPlannerTheme(dynamicColor = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            LogbookContent(
                state = state,
                onOpenRoute = {},
                onDeleteRoute = {},
                listState = rememberLazyListState(),
                contentPadding = PaddingValues(16.dp),
                header = {
                    Text(
                        text = "Profile",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                },
            )
        }
    }
}

private val previewRows = listOf(
    LogbookRow(
        id = 1,
        departureIcao = "EHAM",
        arrivalIcao = "EGLL",
        date = LocalDate.of(2026, 8, 12),
        aircraftId = 1,
        aircraftDisplayName = "Boeing 737-800",
        distanceNm = 226,
        flightTime = FlightTime(0, 40, 450.0),
    ),
    LogbookRow(
        id = 2,
        departureIcao = "EDDF",
        arrivalIcao = "LFPG",
        date = LocalDate.of(2026, 8, 3),
        aircraftId = 2,
        aircraftDisplayName = null,
        distanceNm = null,
        flightTime = null,
    ),
    LogbookRow(
        id = 3,
        departureIcao = "KJFK",
        arrivalIcao = "EHAM",
        date = LocalDate.of(2026, 7, 20),
        aircraftId = 1,
        aircraftDisplayName = "Boeing 737-800",
        distanceNm = 3159,
        flightTime = FlightTime(7, 1, 450.0),
    ),
)

@LightDarkPreview
@CompactWidthPreview
@DevicePreviews
@Composable
private fun LogbookPopulatedPreview() {
    PreviewLogbook(
        LogbookUiState(
            groups = previewRows.groupByMonth(),
            summary = previewRows.summarizeYear(2026),
            status = LogbookStatus.Ready,
        ),
    )
}

@LightDarkPreview
@Composable
private fun LogbookEmptyPreview() {
    PreviewLogbook(LogbookUiState(status = LogbookStatus.Ready))
}

@LightDarkPreview
@Composable
private fun LogbookLoadingPreview() {
    PreviewLogbook(LogbookUiState(status = LogbookStatus.Loading))
}
