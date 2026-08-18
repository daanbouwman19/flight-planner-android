package com.github.daanbouwman.flightplanner.ui.plan

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.daanbouwman.flightplanner.R
import com.github.daanbouwman.flightplanner.core.designsystem.components.CompactWidthPreview
import com.github.daanbouwman.flightplanner.core.designsystem.components.EmptyState
import com.github.daanbouwman.flightplanner.core.designsystem.components.ErrorState
import com.github.daanbouwman.flightplanner.core.designsystem.components.LightDarkPreview
import com.github.daanbouwman.flightplanner.core.designsystem.components.ModeOption
import com.github.daanbouwman.flightplanner.core.designsystem.components.ModeSelector
import com.github.daanbouwman.flightplanner.core.designsystem.components.MorphingLoadingIndicator
import com.github.daanbouwman.flightplanner.core.designsystem.components.SkeletonCard
import com.github.daanbouwman.flightplanner.core.designsystem.components.SwipeActionBackground
import com.github.daanbouwman.flightplanner.core.designsystem.components.SwipeActionSide
import com.github.daanbouwman.flightplanner.core.designsystem.motion.FlightMotion
import com.github.daanbouwman.flightplanner.core.designsystem.theme.FlightPlannerTheme
import com.github.daanbouwman.flightplanner.core.designsystem.motion.LocalReduceMotion
import com.github.daanbouwman.flightplanner.ui.SettingsAction
import com.github.daanbouwman.flightplanner.ui.chrome.ScrollToTopOnReselect
import com.github.daanbouwman.flightplanner.ui.chrome.rememberChromeScrollConnection
import com.github.daanbouwman.flightplanner.ui.chrome.rememberContentInsets
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * The Plan screen: generate flyable routes and act on them.
 *
 * ### The screen is one surface, and the only chrome is the navigation bar
 *
 * There is no top app bar. The list fills the window and draws **under** the status
 * bar, with the system insets carried in its `contentPadding` rather than as padding
 * on its container — which is the whole distinction: padding the container stops the
 * list at the status bar, padding its content lets the first card scroll up behind
 * it. The screen's title and its controls are the list's **first item**, so they
 * scroll away with the routes at exactly the speed of the finger; see [PlanHeader]
 * for why that beats the floating bar this had first. The navigation suite is the
 * one thing that still hides and returns on a scroll signal.
 *
 * On a 360 × 780 dp phone that is about a fifth of the height handed back to the
 * thing the screen exists to show.
 *
 * ### The system bars carry no scrim
 *
 * They are genuinely transparent: a card scrolls up under the clock and down under
 * the gesture handle with nothing between them. A short gradient of the page colour
 * behind each bar was built and removed — it kept the two sets of glyphs from
 * sharing a 24 dp band, and it read on the device as exactly what it was, an opaque
 * bar. Transparent bars are the point of the layout, so the collision is accepted
 * and the legibility of the *system* glyphs is handled where it belongs: the theme
 * sets the status- and navigation-bar appearance from the scheme it resolved, so the
 * clock is dark on a light card and light on a dark one.
 *
 * ### Insets
 *
 * Which edge belongs to whom still changes with the window width — the navigation
 * suite owns the bottom on a compact phone and the start on anything wider — and
 * now it changes with the chrome state too, because a hidden suite gives the bottom
 * edge back. Nothing about that is hard-coded here: `ContentInsets` reports what
 * ancestors have already consumed and the content pads the remainder. See its KDoc.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanScreen(
    onOpenRoute: (RouteRow) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlanViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var picker by remember { mutableStateOf<PickerTarget?>(null) }
    var query by remember { mutableStateOf("") }
    // Hidden initially and with no half-expanded stop: the picker is a search
    // surface, and a sheet that opens half way puts the results the user came for
    // below the fold.
    // Positional: the second parameter is the set of stops the sheet may settle
    // at, and its name is not part of the stable API in this alpha.
    val sheetState = rememberBottomSheetState(
        SheetValue.Hidden,
        setOf(SheetValue.Hidden, SheetValue.Expanded),
    )

    val undoLabel = stringResource(R.string.plan_action_undo)
    val loggedMessage = stringResource(R.string.plan_flight_logged)
    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is PlanEvent.FlightLogged -> {
                    val result = snackbarHostState.showSnackbar(
                        message = loggedMessage.format(event.departureIcao, event.destinationIcao),
                        actionLabel = undoLabel,
                        // Long, not Short. Short is four seconds, which is about
                        // how long it takes to notice a bar has appeared, read
                        // two ICAO codes and decide the swipe was a mistake —
                        // so the window closed just as the user reached for it,
                        // and an undo that is gone when you want it is
                        // indistinguishable from an undo that does not work.
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) viewModel.undoMarkFlown()
                }
            }
        }
    }

    InfiniteScroll(listState = listState, itemCount = state.routes.size, onLoadMore = viewModel::loadMore)

    // Tapping Plan in the navigation bar while already on Plan comes back here. It
    // matters more than usual on this screen: the controls are the list's first
    // item, so this is the way back to them from row sixty.
    ScrollToTopOnReselect(listState = listState)

    // The navigation bar is the only chrome left, so this is the only scroll signal
    // that has to travel out of the screen.
    val chromeScroll = rememberChromeScrollConnection(listState = listState)
    val contentInsets = rememberContentInsets()
    val pullState = rememberPullToRefreshState()

    // A plain `Box`, not a `Scaffold`.
    //
    // There is no top app bar in the scaffold's sense — the chrome floats over the
    // content rather than displacing it — and there is no floating action button,
    // so the only thing a scaffold would still do here is place the snackbar, which
    // is one aligned modifier. Keeping it would mean taking its content padding and
    // then deliberately ignoring it, and content padding that must be ignored is a
    // sign the layout is not a scaffold's shape.
    //
    // (There *was* a FAB. Its tap generated a fresh batch, which the screen now does
    // on open and pull-to-refresh does on demand; its long press appended fifty
    // more, which the list does by itself as you approach the end. What remained was
    // a permanent 56 dp obstruction over the content, flickering into view on launch
    // a moment before the routes it was offering to generate had already arrived.)
    Box(
        modifier = modifier
            .fillMaxSize()
            .then(contentInsets.modifier)
            .nestedScroll(chromeScroll),
    ) {
        val insets = contentInsets.asPaddingValues()
        val layoutDirection = LocalLayoutDirection.current
        val contentPadding = PaddingValues(
            start = insets.calculateStartPadding(layoutDirection) + HorizontalGutter,
            end = insets.calculateEndPadding(layoutDirection) + HorizontalGutter,
            top = insets.calculateTopPadding() + TopGutter,
            bottom = insets.calculateBottomPadding() + BottomGutter,
        )

        PullToRefreshBox(
            // Always false, deliberately: the skeleton cards are this screen's
            // loading signal, and they say something a spinner cannot — where the
            // rows will land, so the layout does not jump when they arrive. Showing
            // both would be two answers to one question. The indicator still tracks
            // the *drag*, which is the gesture's own feedback rather than a report
            // on the load.
            isRefreshing = false,
            onRefresh = viewModel::generate,
            modifier = Modifier.fillMaxSize(),
            state = pullState,
            indicator = {
                PullToRefreshDefaults.Indicator(
                    state = pullState,
                    isRefreshing = false,
                    // Below the status bar. The box is full-bleed now, so the
                    // default `TopCenter` would emerge from behind the clock.
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = contentPadding.calculateTopPadding()),
                )
            },
        ) {
            PlanContent(
                state = state,
                listState = listState,
                contentPadding = contentPadding,
                header = {
                    PlanHeader(
                        state = state,
                        onOpenSettings = onOpenSettings,
                        onModeChange = viewModel::setMode,
                        onPickDeparture = {
                            query = ""
                            viewModel.setAirportQuery("")
                            picker = PickerTarget.Departure
                        },
                        onPickAircraft = {
                            query = ""
                            viewModel.setAircraftQuery("")
                            picker = PickerTarget.Aircraft
                        },
                    )
                },
                onOpenRoute = onOpenRoute,
                onMarkFlown = viewModel::markFlown,
                onReplace = viewModel::replace,
                onGenerate = viewModel::generate,
                onPickAircraft = {
                    query = ""
                    viewModel.setAircraftQuery("")
                    picker = PickerTarget.Aircraft
                },
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            // Consumption-aware: the bottom inset is the navigation suite's while
            // the suite is up, and ours the moment it is not — so a snackbar shown
            // over a scrolled list still clears the gesture handle.
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)),
        )
    }

    picker?.let { target ->
        // Collected here rather than beside `uiState`, so the search does no work
        // until a picker is actually open. Both flows are `WhileSubscribed`: read
        // at the top of the screen they awaited the airport index and ran a
        // fifty-row display query plus a full fleet read the moment Plan composed —
        // on the launch destination, competing with the first batch, for a sheet
        // most launches never open.
        val airports by viewModel.airportResults.collectAsStateWithLifecycle()
        val aircraft by viewModel.aircraftResults.collectAsStateWithLifecycle()

        PlanPickerSheet(
            target = target,
            query = query,
            airports = airports,
            aircraft = aircraft,
            hasSelection = when (target) {
                PickerTarget.Departure -> state.lockedDeparture != null
                PickerTarget.Aircraft -> state.selectedAircraft != null
            },
            sheetState = sheetState,
            onQueryChange = {
                query = it
                when (target) {
                    PickerTarget.Departure -> viewModel.setAirportQuery(it)
                    PickerTarget.Aircraft -> viewModel.setAircraftQuery(it)
                }
            },
            onPickAirport = { airport ->
                viewModel.setDeparture(airport)
                scope.launch { sheetState.hide() }.invokeOnCompletion { picker = null }
            },
            onPickAircraft = { spec ->
                viewModel.setAircraft(spec)
                scope.launch { sheetState.hide() }.invokeOnCompletion { picker = null }
            },
            onClearSelection = {
                when (target) {
                    PickerTarget.Departure -> viewModel.setDeparture(null)
                    PickerTarget.Aircraft -> viewModel.setAircraft(null)
                }
                scope.launch { sheetState.hide() }.invokeOnCompletion { picker = null }
            },
            onDismiss = { picker = null },
        )
    }
}

/**
 * The screen's name, the way to Settings, and the controls — as the **first item of
 * the list**, not as a bar above it.
 *
 * ### Why this is content and not chrome
 *
 * It was chrome first: an overlay that faded its container in once the list moved,
 * dropped the title at one scroll threshold and the controls at another. Three
 * things were wrong with it on a device, and they were all the same thing.
 *
 *  - **It could not feel attached to the finger.** A threshold means nothing happens
 *    for 48 dp and then a spring runs on its own clock. Chrome that retracts *after*
 *    the scroll reads as lag, however fast the spring is.
 *  - **The container had to appear from nowhere.** An overlay needs a background the
 *    moment a card is behind it, so the top of the screen tinted itself as soon as
 *    the list moved — a highlight the user never asked for.
 *  - **Its bottom edge cut the cards in half.** An opaque block over a scrolling list
 *    ends in a straight horizontal line, and a card sliced by it looks broken rather
 *    than layered.
 *
 * As the list's first item all three stop existing rather than getting fixed. It
 * moves at exactly the speed of the finger because it *is* the content; it needs no
 * background because nothing passes behind it; and it has no edge because there is
 * nothing to have an edge against. The code that made it work — a measured height,
 * a reserved band of padding, two thresholds and two transitions — is gone with it.
 *
 * ### What that costs, and why it is worth it
 *
 * The controls no longer come back on an upward flick from deep in the list; you
 * scroll to the top for them, the way you do for the filters at the top of any list.
 * That is the trade for the feel, and it is the right way round: filters are set
 * when you arrive, and the rest of the session is spent reading routes.
 *
 * The navigation bar still hides and returns on the scroll signal — it is at the
 * other end of the screen, it overlays nothing, and it is the one piece of chrome
 * that must stay reachable.
 *
 * The title is a **heading**, not an app bar: it scrolls away for good, and the
 * navigation bar already names the section, so there is no reason for it to be a
 * permanent 64 dp. Being content rather than a bar, it can also be set at the size a
 * page opening deserves.
 */
@Composable
private fun PlanHeader(
    state: PlanUiState,
    onOpenSettings: () -> Unit,
    onModeChange: (PlanMode) -> Unit,
    onPickDeparture: () -> Unit,
    onPickAircraft: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.destination_plan),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            SettingsAction(onClick = onOpenSettings)
        }
        Spacer(Modifier.height(12.dp))
        PlanControls(
            state = state,
            onModeChange = onModeChange,
            onPickDeparture = onPickDeparture,
            onPickAircraft = onPickAircraft,
        )
    }
}

@Composable
private fun PlanControls(
    state: PlanUiState,
    onModeChange: (PlanMode) -> Unit,
    onPickDeparture: () -> Unit,
    onPickAircraft: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // No horizontal padding of its own: this sits inside the list, whose
    // `contentPadding` already holds the gutter the cards line up with.
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val options = listOf(
            ModeOption(stringResource(R.string.plan_mode_all)),
            ModeOption(
                label = stringResource(R.string.plan_mode_not_flown),
                // Drawn as well as spoken. It used to be spoken only: a segmented
                // row gave each option an equal third of the width, and
                // "Not flown 116" ellipsised to "Not flown 1…" — a wrong number,
                // which is worse than none. Chips are sized to their own label, so
                // the figure fits and the sentence stays for TalkBack.
                count = state.notFlownCount,
                contentDescription = pluralStringResource(
                    R.plurals.plan_mode_not_flown_description,
                    state.notFlownCount,
                    state.notFlownCount,
                ),
                enabled = state.notFlownCount > 0,
            ),
            ModeOption(stringResource(R.string.plan_mode_this_aircraft)),
        )
        ModeSelector(
            options = options,
            selectedIndex = state.mode.ordinal,
            onSelect = { onModeChange(PlanMode.entries[it]) },
            modifier = Modifier.fillMaxWidth(),
        )
        PlanFilterFields(
            lockedDeparture = state.lockedDeparture,
            selectedAircraft = state.selectedAircraft,
            onPickDeparture = onPickDeparture,
            onPickAircraft = onPickAircraft,
        )
    }
}

/**
 * Whatever the screen currently has to show, under [header] and the same padding.
 *
 * [contentPadding] reaches the list as `contentPadding`, so cards scroll under the
 * status bar, and every other state as ordinary padding, because there is nothing
 * to scroll there.
 *
 * The header is emitted by both halves rather than hoisted above them: in the list
 * it has to be an *item* — that is the whole point of it — and in a state with no
 * list there is nothing to make it an item of.
 */
@Composable
private fun PlanContent(
    state: PlanUiState,
    listState: LazyListState,
    contentPadding: PaddingValues,
    header: @Composable () -> Unit,
    onOpenRoute: (RouteRow) -> Unit,
    onMarkFlown: (RouteRow) -> Unit,
    onReplace: (RouteRow) -> Unit,
    onGenerate: () -> Unit,
    onPickAircraft: () -> Unit,
) {
    when {
        state.awaitingAircraftChoice -> HeadedState(header, contentPadding) {
            EmptyState(
                title = stringResource(R.string.plan_pick_aircraft_title),
                message = stringResource(R.string.plan_pick_aircraft_message),
                actionLabel = stringResource(R.string.plan_pick_aircraft_action),
                onAction = onPickAircraft,
            )
        }

        state.status is PlanStatus.Failed -> HeadedState(header, contentPadding) {
            ErrorState(
                title = stringResource(R.string.plan_error_title),
                message = stringResource(
                    when (state.status.reason) {
                        PlanFailure.IndexUnavailable -> R.string.plan_error_index
                        PlanFailure.FleetEmpty -> R.string.plan_error_fleet
                        PlanFailure.Unknown -> R.string.plan_error_unknown
                    },
                ),
                onRetry = onGenerate,
            )
        }

        state.status == PlanStatus.Generating && state.routes.isEmpty() ->
            HeadedState(header, contentPadding) { DelayedSkeletonList() }

        state.status == PlanStatus.Idle -> HeadedState(header, contentPadding) {
            EmptyState(
                title = stringResource(R.string.plan_empty_title),
                // The desktop application's wording, kept deliberately.
                message = stringResource(R.string.plan_empty_message),
                actionLabel = stringResource(R.string.plan_action_generate),
                onAction = onGenerate,
            )
        }

        state.isEmptyResult -> HeadedState(header, contentPadding) {
            EmptyState(
                title = stringResource(R.string.plan_no_matches_title),
                message = stringResource(R.string.plan_no_matches_message),
                actionLabel = stringResource(R.string.plan_action_generate),
                onAction = onGenerate,
            )
        }

        else -> RouteList(
            state = state,
            listState = listState,
            contentPadding = contentPadding,
            header = header,
            onOpenRoute = onOpenRoute,
            onMarkFlown = onMarkFlown,
            onReplace = onReplace,
        )
    }
}

/**
 * The header, then whatever fills the rest of the window.
 *
 * The controls stay usable in every state that is not a list — an empty result is
 * exactly when the user wants to change a filter, and a screen that hides its
 * controls when it has nothing to show asks them to undo something they cannot see.
 */
@Composable
private fun HeadedState(
    header: @Composable () -> Unit,
    contentPadding: PaddingValues,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
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

/**
 * Skeleton cards, but only if the wait is long enough to be worth showing.
 *
 * Generating a batch is a millisecond of arithmetic plus one query, so it
 * usually finishes within a few frames. Drawing skeletons unconditionally means
 * they appear and are overwritten almost immediately, and the eye reads that as
 * the screen flickering on launch rather than as a loading state — a placeholder
 * that is gone before it can be understood is pure noise.
 *
 * So nothing is drawn for the first [SkeletonDelayMillis]. Below that the
 * content simply arrives; above it the skeletons appear and stay long enough to
 * mean something. The blank in between is shorter than a frame budget's worth of
 * perception.
 */
@Composable
private fun DelayedSkeletonList(modifier: Modifier = Modifier) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(SkeletonDelayMillis)
        visible = true
    }
    if (!visible) return

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(SkeletonCount) { SkeletonCard() }
    }
}

@Composable
private fun RouteList(
    state: PlanUiState,
    listState: LazyListState,
    contentPadding: PaddingValues,
    header: @Composable () -> Unit,
    onOpenRoute: (RouteRow) -> Unit,
    onMarkFlown: (RouteRow) -> Unit,
    onReplace: (RouteRow) -> Unit,
) {
    // Which rows have already played their entrance.
    //
    // A `LaunchedEffect` inside a lazy item runs again every time that item is
    // recomposed, and scrolling an item off screen and back destroys and rebuilds
    // it — so an entrance animation driven from inside the item replays every
    // time the user scrolls past it, which is the single most common way to make
    // a list feel broken. This set lives outside the items and outlives their
    // composition, so a row animates exactly once.
    val entered = remember { mutableSetOf<Long>() }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Keyed, so it survives the list being regenerated under it and does not
        // get rebuilt every time a batch arrives.
        item(key = HeaderKey) { header() }

        itemsIndexed(state.routes, key = { _, row -> row.id }) { index, row ->
            SwipeableRoute(
                row = row,
                onOpen = { onOpenRoute(row) },
                onMarkFlown = { onMarkFlown(row) },
                onReplace = { onReplace(row) },
                // Placement animation belongs on the item root: it is what moves
                // the neighbours up when a swiped row is removed, and what lets
                // the replacement expand into the gap.
                modifier = Modifier
                    // Placement only. `animateItem` fades items in and out by
                    // default, and this row already has its own staggered
                    // entrance — two independent fades over the same alpha is
                    // what made a fresh batch pop rather than arrive. Placement
                    // is the part worth keeping: it is what slides the
                    // neighbours up when a swiped row is removed.
                    .animateItem(
                        fadeInSpec = null,
                        fadeOutSpec = null,
                        placementSpec = FlightMotion.spatial(),
                    )
                    .rowEntrance(index = index, row = row, entered = entered),
            )
        }

        if (state.status == PlanStatus.Appending) {
            item(key = AppendingKey) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    MorphingLoadingIndicator()
                }
            }
        }
    }
}

/**
 * How a row arrives — which is two different animations, because a row arrives
 * for two different reasons.
 *
 * ### A batch: staggered, fading and rising
 *
 * The stagger flattens after [com.github.daanbouwman.flightplanner.core.designsystem.motion.FlightMotion.EnterStaggerCap]
 * items, which is what keeps a fifty-row batch from becoming a one-and-a-half
 * second performance — past the cap every row shares the last delay and arrives
 * together. That also makes an *appended* batch, whose indices are all well past
 * the cap, land as one block rather than cascading below the fold where nobody
 * is looking.
 *
 * ### A replacement: dealt in from the trailing edge, at once
 *
 * A replacement is not a small batch and the batch entrance was wrong for it in
 * three separate ways. It inherited a stagger delay from its *index*, so
 * replacing row six sat still for 180 ms first, and replacing anything past the
 * cap got no animation at all and simply popped. It rose from below, which says
 * "this list is arriving" when the list is already here. And it faded up from
 * nothing in a slot the user had just emptied, so the sequence read as a hole
 * that something grew back into.
 *
 * What actually happened is that one card was thrown off the start edge and
 * another took its place, so the replacement comes in across the same axis, from
 * the opposite edge — the deck advancing by one. It starts immediately, whatever
 * its index: the user is looking straight at this slot, which is exactly the
 * case a stagger is wrong for.
 *
 * ### Both
 *
 * Fade runs on the effects spring and the movement on the spatial one, which is
 * the design system's split applied to a single entrance: the alpha must not
 * overshoot, the movement should. The travel is a fraction of the row's own
 * width rather than a fixed distance, so it reads the same on a phone and on a
 * tablet.
 *
 * A staggered sequence is a chain of *delays*, and the system animator scale does
 * not scale delays — so reduce-motion has to switch this off explicitly rather
 * than rely on Compose shortening it.
 */
@Composable
private fun Modifier.rowEntrance(index: Int, row: RouteRow, entered: MutableSet<Long>): Modifier {
    val rowId = row.id
    val replacing = row.arrivedAsReplacement
    val reduceMotion = LocalReduceMotion.current
    // Read and record in one step, during composition, so the row knows whether
    // this is its first appearance before any effect has had a chance to run.
    val alreadyEntered = remember(rowId) { !entered.add(rowId) }

    // Only the first screenful of a batch animates.
    //
    // A staggered entrance explains where a *new list* came from. It explains
    // nothing about row 63 of an appended batch, which the user is already
    // scrolling towards at speed — there it is just a delay between arriving at
    // a row and being able to read it, which is exactly the "cards slowly pop
    // in" that makes a fast fling feel broken. Past the stagger cap, rows are
    // simply there. A replacement is exempt: its index says where it is in the
    // list, not how far it is from the user's attention.
    val animates = replacing || index < FlightMotion.EnterStaggerCap

    var visible by remember(rowId) { mutableStateOf(alreadyEntered || reduceMotion || !animates) }
    LaunchedEffect(rowId) {
        if (!visible) {
            if (!replacing) delay(FlightMotion.enterDelayMillis(index).toLong())
            visible = true
        }
    }

    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = FlightMotion.effects(),
        label = "rowEntranceAlpha",
    )
    // One progress value for either axis: 0 is "off its mark", 1 is home. The
    // spatial spring carries it past 1 and back, which is the overshoot that
    // gives the card its weight — on the rise it is a settle, on the slide it is
    // the card knocking against the edge it came to rest at.
    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = FlightMotion.spatial(),
        label = "rowEntranceTravel",
    )

    val rise = with(LocalDensity.current) { EntranceRise.toPx() }
    // A swipe carries the card towards the start edge, so its replacement comes
    // from the end one — which is the right-hand side only in a left-to-right
    // layout.
    val fromEnd = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f

    return graphicsLayer {
        this.alpha = alpha
        if (replacing) {
            translationX = (1f - progress) * size.width * ReplacementSlideFraction * fromEnd
        } else {
            translationY = (1f - progress) * rise
        }
    }
}

/**
 * A route card that can be swiped away in either direction.
 *
 * The two directions are deliberately asymmetric in weight. Right — towards the
 * confirming end — logs the flight, which writes to the database, so it gets an
 * undo. Left discards this one route and generates another in its place, which
 * is cheap and repeatable, so it does not.
 *
 * ### When the action fires
 *
 * From the component's own `onDismiss`, which is an *event*: it fires once, when a
 * dismiss actually completes. Every other candidate is a state, and states are
 * wrong here for reasons worth writing down, because each one shipped as a bug
 * first:
 *
 *  - `currentValue` moves *during* the drag, so acting on it logs the flight while
 *    the finger is still down and dragging back to cancel does not undo it.
 *  - `settledValue` is correct about timing but is still a state, and this state is
 *    saveable and keyed by list item — so a row restored by an undo comes back
 *    holding `StartToEnd` and gets read as a fresh gesture, re-running the action
 *    the undo just reversed.
 *  - `confirmValueChange` is consulted repeatedly while the gesture settles, so a
 *    database write there logs the same flight twice. It is also deprecated.
 *
 * `targetValue` remains the right choice for the *background*, which is a state:
 * it flips as the drag crosses the threshold, which is exactly when the colour
 * should say the action has become committal.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableRoute(
    row: RouteRow,
    onOpen: () -> Unit,
    onMarkFlown: () -> Unit,
    onReplace: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current

    // The card's own width, so a drag can be reported as a fraction of it.
    var widthPx by remember { mutableIntStateOf(0) }

    // The stock component, on its current API.
    //
    // The commit distance is raised from the default, which is short enough that
    // nudging a card aside to read the one behind it logs a flight. Note the
    // draggable also settles on velocity, so a deliberate fast flick still
    // commits below this distance — that is the platform's behaviour and the
    // reason mark-as-flown has an undo.
    val dismissState = rememberSwipeToDismissBoxState(
        positionalThreshold = { distance -> distance * CommitFraction },
    )

    fun dragFraction(): Float {
        if (widthPx <= 0) return 0f
        // requireOffset throws until the anchors have been laid out.
        val offset = runCatching { dismissState.requireOffset() }.getOrDefault(0f)
        return (abs(offset) / widthPx).coerceIn(0f, 1f)
    }

    // Normalised so that 1.0 is exactly the point at which releasing would
    // commit. That is what lets the colour tell the truth about the gesture
    // rather than fading in against some unrelated scale — at half strength the
    // action is genuinely half way to happening.
    val commitProgress = (dragFraction() / CommitFraction).coerceIn(0f, 1f)
    val committed = commitProgress >= 1f

    LaunchedEffect(dismissState) {
        // A row that comes back after an undo comes back *already dismissed*.
        //
        // `rememberSwipeToDismissBoxState` is saveable and the list keys items by
        // route id, so re-inserting a row restores the state it had when it left:
        // `StartToEnd`, which draws the card shoved off to one side. Putting it
        // back at rest is all that is needed — the action itself is driven by
        // `onDismiss` below, which is an event rather than a state, so a restored
        // value cannot be mistaken for a fresh gesture.
        if (dismissState.currentValue != SwipeToDismissBoxValue.Settled) {
            runCatching { dismissState.reset() }
        }
    }

    // The threshold crossing is the moment the gesture becomes committal, and it
    // is the only point at which a haptic is warranted — the rest of a swipe is
    // browsing.
    LaunchedEffect(dismissState) {
        snapshotFlow { dismissState.targetValue }
            .distinctUntilChanged()
            .filter { it != SwipeToDismissBoxValue.Settled }
            .collect { haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick) }
    }

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier.onSizeChanged { widthPx = it.width },
        // The component's own completion callback, rather than an observer over
        // its state. It fires once, when a dismiss actually happens, which is
        // both what this needs and the reason the restored-state problem above
        // stays a cosmetic one.
        onDismiss = { direction ->
            when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> onMarkFlown()
                SwipeToDismissBoxValue.EndToStart -> onReplace()
                SwipeToDismissBoxValue.Settled -> Unit
            }
        },
        backgroundContent = {
            // Keyed on `dismissDirection`, which follows the finger from the
            // first pixel, rather than on `targetValue`, which only flips once
            // the threshold is crossed — that discontinuity is what made the
            // action appear fully formed out of nowhere half way through a drag.
            //
            // The colours are the *solid* roles, not the containers. A pale
            // container behind a `surfaceContainer` card is two neighbouring
            // greys, and the reveal was invisible until it was almost complete;
            // `tertiary` and `secondary` read instantly against the card at any
            // strength, which is what a background that means "this is about to
            // happen" has to do.
            when (dismissState.dismissDirection) {
                SwipeToDismissBoxValue.StartToEnd -> SwipeActionBackground(
                    side = SwipeActionSide.Start,
                    icon = painterResource(R.drawable.ic_plan_flown),
                    label = stringResource(R.string.plan_swipe_mark_flown),
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    contentColor = MaterialTheme.colorScheme.onTertiary,
                    progress = commitProgress,
                    committed = committed,
                )

                SwipeToDismissBoxValue.EndToStart -> SwipeActionBackground(
                    side = SwipeActionSide.End,
                    icon = painterResource(R.drawable.ic_plan_replace),
                    label = stringResource(R.string.plan_swipe_replace),
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                    progress = commitProgress,
                    committed = committed,
                )

                SwipeToDismissBoxValue.Settled -> Unit
            }
        },
    ) {
        RouteCard(row = row, onClick = onOpen)
    }
}

/**
 * Requests another batch as the end of the list comes into view.
 *
 * Driven by `snapshotFlow` over the last visible index rather than by an effect
 * inside the item composable. An effect per item fires once per *composition* of
 * that item, so scrolling back and forth over the tail requests a batch every
 * time — the list grows without bound while the user is merely looking at it.
 * One observer outside the list asks once per genuine arrival at the end, and
 * the ViewModel drops the request if a batch is already in flight.
 */
@Composable
private fun InfiniteScroll(
    listState: LazyListState,
    itemCount: Int,
    onLoadMore: () -> Unit,
) {
    LaunchedEffect(listState, itemCount) {
        snapshotFlow {
            // Recomputed on every scroll *and* whenever the list grows, which is
            // what makes this self-correcting. A single edge-triggered effect —
            // fire once when the end comes into view — cannot recover from a
            // request the ViewModel drops because a batch is already running, so
            // a fast fling asks once, is refused, and then waits at the bottom of
            // the list for a scroll that never comes. Asking repeatedly is free:
            // `loadMore` ignores anything that arrives while a batch is in flight.
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            last >= 0 && itemCount > 0 && last >= itemCount - LoadMoreThreshold
        }
            .distinctUntilChanged()
            .filter { it }
            .collect { onLoadMore() }
    }
}

private const val SkeletonCount = 5

/** Below this, the batch arrives before a placeholder could be understood. */
private const val SkeletonDelayMillis = 150L

/**
 * How many rows from the end the next batch is requested.
 *
 * Twenty, not five. Five is under two screenfuls at this card height, and a
 * fling covers that before a batch can be generated and its airport rows
 * fetched — so the user arrives at the bottom of the list and waits, which is
 * the one thing an infinite list must never do. Twenty buys enough runway that
 * the next batch is already in place by the time the fling reaches it.
 */
private const val LoadMoreThreshold = 20

private const val AppendingKey = "appending"

private const val HeaderKey = "header"

/** How far a row rises into place. Small: this is a hint of arrival, not a slide-in. */
private val EntranceRise = 12.dp

/**
 * How far a replacement travels in, as a fraction of the row's width.
 *
 * Larger than [EntranceRise] is to a row's height, and deliberately: this one is
 * a slide-in. The card it replaces left across the whole width, so a hint would
 * not read as an answer to it — but a full width would make a repeatable action
 * into a performance, and the card would spend most of the animation off screen
 * where it cannot be read. A third is far enough to have an obvious direction.
 */
private const val ReplacementSlideFraction = 0.33f

/** The card column's margin. Named because the header is an item inside it. */
private val HorizontalGutter = 16.dp

/** Space between the status bar and the heading. */
private val TopGutter = 8.dp

/** Space below the last card, so it does not end flush against the bottom inset. */
private val BottomGutter = 24.dp

/**
 * How far a card must travel before releasing commits.
 *
 * A third of the width. Small enough to be a flick rather than a haul, large
 * enough that it cannot be reached while scrolling or while nudging a card
 * aside to read the one behind it.
 */
private const val CommitFraction = 0.33f

/*
 * Previews.
 *
 * `PlanScreen` itself takes a Hilt ViewModel and cannot be rendered by the
 * tooling, so the previews target the stateless halves it delegates to. That is
 * not a workaround — it is the reason those two are stateless. Between them they
 * cover every state the screen has, including the four that are awkward to reach
 * by hand: a failed index, an empty fleet, a filter combination that matches
 * nothing, and "this aircraft" with no aircraft chosen.
 *
 * `PlanContent` draws the header itself, at the top of whatever it is showing, so a
 * preview of it is a preview of the whole screen at rest — which is what the screen
 * *is* now that the header scrolls with the content instead of floating over it.
 */

@Composable
private fun PreviewPlan(state: PlanUiState) {
    FlightPlannerTheme(dynamicColor = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            PlanContent(
                state = state,
                listState = rememberLazyListState(),
                contentPadding = PaddingValues(
                    start = HorizontalGutter,
                    end = HorizontalGutter,
                    top = TopGutter,
                    bottom = BottomGutter,
                ),
                header = {
                    PlanHeader(
                        state = state,
                        onOpenSettings = {},
                        onModeChange = {},
                        onPickDeparture = {},
                        onPickAircraft = {},
                    )
                },
                onOpenRoute = {},
                onMarkFlown = {},
                onReplace = {},
                onGenerate = {},
                onPickAircraft = {},
            )
        }
    }
}

@LightDarkPreview
@CompactWidthPreview
@Composable
private fun PlanRoutesPreview() {
    PreviewPlan(
        PlanUiState(
            routes = PlanPreviewData.batch,
            status = PlanStatus.Ready,
            notFlownCount = 12,
        ),
    )
}

@LightDarkPreview
@Composable
private fun PlanWithFiltersPreview() {
    PreviewPlan(
        PlanUiState(
            mode = PlanMode.SelectedAircraft,
            lockedDeparture = PlanPreviewData.schiphol,
            selectedAircraft = PlanPreviewData.boeing,
            routes = PlanPreviewData.batch,
            status = PlanStatus.Ready,
        ),
    )
}

@LightDarkPreview
@Composable
private fun PlanNoMatchesPreview() {
    PreviewPlan(PlanUiState(routes = emptyList(), status = PlanStatus.Ready))
}

@LightDarkPreview
@Composable
private fun PlanAwaitingAircraftPreview() {
    PreviewPlan(PlanUiState(mode = PlanMode.SelectedAircraft, selectedAircraft = null))
}

@LightDarkPreview
@Composable
private fun PlanIndexFailedPreview() {
    PreviewPlan(PlanUiState(status = PlanStatus.Failed(PlanFailure.IndexUnavailable)))
}

@LightDarkPreview
@Composable
private fun PlanEmptyFleetPreview() {
    PreviewPlan(PlanUiState(status = PlanStatus.Failed(PlanFailure.FleetEmpty)))
}
