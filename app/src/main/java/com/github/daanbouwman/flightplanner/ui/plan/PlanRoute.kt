package com.github.daanbouwman.flightplanner.ui.plan

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.daanbouwman.flightplanner.R
import com.github.daanbouwman.flightplanner.core.designsystem.motion.FlightMotion
import com.github.daanbouwman.flightplanner.ui.chrome.LocalNavAnimatedVisibilityScope
import com.github.daanbouwman.flightplanner.ui.chrome.LocalSharedTransitionScope
import com.github.daanbouwman.flightplanner.ui.chrome.MaxContentWidth
import com.github.daanbouwman.flightplanner.core.designsystem.theme.withTabularFigures
import com.github.daanbouwman.flightplanner.navigation.Destination
import com.github.daanbouwman.flightplanner.ui.detail.RouteDetailContent
import com.github.daanbouwman.flightplanner.ui.detail.RouteDetailPaneState
import com.github.daanbouwman.flightplanner.ui.detail.RouteDetailPaneViewModel
import kotlinx.coroutines.launch

/**
 * The Plan section, in however many panes the window has room for.
 *
 * ### One decision, made once
 *
 * A phone gets exactly what it had: the list, and a tap that *navigates* to a
 * full-screen detail with an app bar, predictive back and the shared elements
 * that carry a route across the transition. A window wide enough for two panes
 * gets the list and the detail side by side, and a tap changes what the second
 * pane shows without navigating anywhere.
 *
 * The branch is `maxHorizontalPartitions`, which is the adaptive library's own
 * answer to "does this window have room for two panes" — the same family of
 * decision the navigation suite already makes for bar-versus-rail, so the two
 * cannot disagree about what kind of window this is.
 *
 * **`ListDetailPaneScaffold` is not used on compact on purpose.** It would happily
 * collapse to one pane and swap the detail in over the list, and that is a worse
 * phone experience than the one that exists: no app bar, a back gesture that is
 * binary rather than progressive, and no shared element — the route would simply
 * appear instead of arriving from the card. The scaffold earns its place where it
 * has something to offer, which is the second pane.
 *
 * ### Two panes means no shared elements at all
 *
 * The scopes are cleared for everything under the scaffold, so `sharedRouteElement`
 * is a no-op in both panes. Nothing here travels — a tap changes what the second
 * pane shows and the pane cross-fades it — so there is nothing for a shared
 * element to do, and leaving the scopes in place would be actively wrong: a card
 * and the pane showing that same route hold **the same keys at the same time**,
 * which is the one thing a shared element cannot have. Two halves of a pair are
 * meant to be two screens' worth apart in time, not side by side on one.
 *
 * ### The list pane is the whole Plan screen
 *
 * Not a stripped-down version of it. The header, the filters, the generate action,
 * the swipe grammar and the undo snackbar are what the Plan screen *is*, and a
 * tablet has no less use for them. The only thing that changes is what a tap on a
 * card means, which is a lambda.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun PlanRoute(
    onOpenSettings: () -> Unit,
    onOpenRoute: (RouteRow) -> Unit,
    viewModel: PlanViewModel,
    modifier: Modifier = Modifier,
) {
    val twoPanes = calculatePaneScaffoldDirective(currentWindowAdaptiveInfo())
        .maxHorizontalPartitions > 1

    if (!twoPanes) {
        PlanScreen(
            onOpenRoute = onOpenRoute,
            onOpenSettings = onOpenSettings,
            viewModel = viewModel,
            modifier = modifier,
        )
        return
    }

    val navigator = rememberListDetailPaneScaffoldNavigator<String>()
    val paneViewModel: RouteDetailPaneViewModel = hiltViewModel()
    val detail by paneViewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    // The pane's own, because the list pane's snackbar is inside the list pane and
    // an action taken in the detail pane should report where it was taken.
    val paneSnackbarHostState = remember { SnackbarHostState() }

    CompositionLocalProvider(
        LocalSharedTransitionScope provides null,
        LocalNavAnimatedVisibilityScope provides null,
    ) {
        ListDetailPaneScaffold(
            directive = navigator.scaffoldDirective,
            value = navigator.scaffoldValue,
            modifier = modifier,
            listPane = {
                AnimatedPane {
                    PlanScreen(
                        onOpenRoute = { row ->
                            val route = row.toDestination()
                            paneViewModel.select(route)
                            scope.launch {
                                navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, route.key())
                            }
                        },
                        onOpenSettings = onOpenSettings,
                        viewModel = viewModel,
                    )
                }
            },
            detailPane = {
                AnimatedPane {
                    RouteDetailPane(
                        state = detail,
                        snackbarHostState = paneSnackbarHostState,
                        onMarkFlown = { route ->
                            viewModel.markFlown(
                                departureIcao = route.departureIcao,
                                destinationIcao = route.destinationIcao,
                                aircraftId = route.aircraftId,
                            )
                        },
                        // The route has just left the list, so a pane still showing
                        // it would be describing something the user can no longer
                        // act on.
                        onFlownConfirmed = paneViewModel::clear,
                    )
                }
            },
        )
    }
}

/**
 * The detail pane: a route, or an invitation to pick one.
 *
 * It carries a heading of its own rather than an app bar. An app bar implies a
 * screen you can leave, and this pane is not somewhere you went — it is the other
 * half of the screen you are already on.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RouteDetailPane(
    state: RouteDetailPaneState?,
    snackbarHostState: SnackbarHostState,
    onMarkFlown: (Destination.RouteDetail) -> Boolean,
    onFlownConfirmed: () -> Unit,
) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        // The pane sits inside a window the section scaffold has already inset, so
        // it takes nothing further — see ContentInsets for why insets are consumed
        // once and only once on the way down.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.surface,
    ) { contentPadding ->
        // Keyed on the **selection**, not on what has been loaded from it. One
        // selection publishes three times — the codes alone, then the airports,
        // then the runways — so a key read off the loaded state is absent on the
        // first publish and the panel cross-fades twice per tap, through a blank
        // skeleton, which is the smear this key exists to prevent. The route is
        // known the instant the user taps and never changes; the rest is content.
        //
        // The transform is read outside the spec lambda, which is not composable —
        // the same reason the navigation graph resolves its transitions up front.
        val paneTransform = FlightMotion.paneContent()
        AnimatedContent(
            targetState = state,
            transitionSpec = { paneTransform },
            contentKey = { it?.route?.key() },
            label = "route detail pane",
        ) { current ->
            RouteDetailPaneContent(
                state = current,
                contentPadding = contentPadding,
                snackbarHostState = snackbarHostState,
                onMarkFlown = onMarkFlown,
                onFlownConfirmed = onFlownConfirmed,
            )
        }
    }
}

/**
 * One route's worth of pane, or the empty invitation.
 *
 * Split out of [RouteDetailPane] so that `AnimatedContent` composes a whole copy
 * of it per route: the outgoing copy keeps showing the route it was given while
 * it fades, rather than re-rendering with the incoming route's data half way
 * through its own exit.
 */
@Composable
private fun RouteDetailPaneContent(
    state: RouteDetailPaneState?,
    contentPadding: PaddingValues,
    snackbarHostState: SnackbarHostState,
    onMarkFlown: (Destination.RouteDetail) -> Boolean,
    onFlownConfirmed: () -> Unit,
) {
    run {
        if (state == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.route_detail_pane_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            return@run
        }

        // Both come from the selection, so the heading states the real codes from
        // the first frame rather than waiting for a database read to name them.
        val route = state.route
        val detail = state.detail
        // Centred, because the content caps its own line length and a pane on a
        // desktop-sized window is wider than that cap. Left-aligned, the spine
        // would sit against the divider with a hand's width of nothing to its
        // right.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(
                    R.string.route_detail_title_spoken,
                    route.departureIcao,
                    route.destinationIcao,
                ),
                style = MaterialTheme.typography.titleLarge.withTabularFigures(),
                modifier = Modifier
                    .widthIn(max = MaxContentWidth)
                    .fillMaxWidth(),
            )

            RouteDetailContent(
                route = route,
                state = detail,
                onMarkFlown = { onMarkFlown(route) },
                onFlownConfirmed = onFlownConfirmed,
                snackbarHostState = snackbarHostState,
                // The pane cross-fades between routes; staging each block in on
                // top of that would be two motions explaining one change.
                animateEntrance = false,
            )
        }
    }
}

/** The navigation arguments a row would travel with, for a pane that never travels. */
private fun RouteRow.toDestination(): Destination.RouteDetail = Destination.RouteDetail(
    departureIcao = departure.icao,
    destinationIcao = destination.icao,
    aircraftId = aircraft.id,
    distanceNm = distanceNm,
)

/**
 * A stable identity for the scaffold navigator.
 *
 * The navigator saves its history across configuration changes, so the key has to
 * be something trivially savable — a string, not a route object whose saver would
 * be one more thing to keep correct. The route itself lives in the pane's
 * ViewModel, which survives the same changes without being serialised at all.
 */
private fun Destination.RouteDetail.key(): String =
    "$departureIcao>$destinationIcao@$aircraftId"
