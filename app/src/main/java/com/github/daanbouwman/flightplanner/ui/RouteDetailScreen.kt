package com.github.daanbouwman.flightplanner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.daanbouwman.flightplanner.R
import com.github.daanbouwman.flightplanner.core.designsystem.components.RouteMap
import com.github.daanbouwman.flightplanner.core.designsystem.theme.withTabularFigures
import com.github.daanbouwman.flightplanner.navigation.Destination

import com.github.daanbouwman.flightplanner.ui.chrome.SharedRouteKeys
import com.github.daanbouwman.flightplanner.ui.chrome.sharedRouteElement
import com.github.daanbouwman.flightplanner.ui.detail.RouteDetailContent
import com.github.daanbouwman.flightplanner.ui.detail.RouteDetailViewModel

/**
 * One route, at the size a route deserves when it is the only thing on screen.
 *
 * ### The leg is the page's structure
 *
 * Almost every route screen sets departure and destination as two equal panels
 * with an arrow between them. That is the obvious layout and it throws away
 * something true: **the initial bearing belongs to the departure and the final
 * bearing belongs to the destination**, and the distance and the estimate belong
 * to neither — they belong to the span between them. Two equal panels have
 * nowhere to put any of that except a row of chips, where all four figures read
 * as properties of the route in general.
 *
 * So the page is built on a spine: a hairline rail down the leading edge, a
 * hollow ring at the departure end and a filled dot at the destination end. Those
 * are the *same two markers [RouteMap] draws on the arc above*, so the page's
 * structure and the map's legend are one vocabulary rather than two. Each figure
 * then sits at the point on the leg where it is true.
 *
 * The two `ValueChip`s that used to sit under the hero are gone: their figures
 * moved onto the spine. The screen says more than it did and has one row fewer.
 *
 * ### Predictive back is the NavHost's, and this screen must keep out of its way
 *
 * There is deliberately **no `PredictiveBackHandler` here**. There was one, and it
 * was worse than nothing: it drew a peek — the screen shrinking, rounding and
 * drifting off the swiped edge — over a black background, because the screen you
 * are going *back to* was not composed. That is a screen leaving, not a preview of
 * where you are going, and "predictive" is the second thing.
 *
 * `NavHost` already does the real version: a back gesture seeks the pop transition
 * to the gesture's own progress, so the Plan screen is composed and fades in
 * underneath while the detail fades out over it, and the shared elements travel
 * back to the card as the finger moves. Registering a handler inside the
 * destination **consumes the gesture before the NavHost sees it**, which is
 * precisely how the peek came to be drawn over nothing.
 *
 * So the rule for this app is: let the navigation host own the back gesture, and
 * spend the effort on transitions worth seeking rather than on a local imitation.
 *
 * **None of it is visible under three-button navigation**, where there is no back
 * gesture for the platform to report progress for. Check that before concluding
 * predictive back is broken.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteDetailScreen(
    route: Destination.RouteDetail,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Logs the flight through the list that generated it, returning false when
     * the route is no longer in that list — after process death, or after the
     * batch was regenerated.
     *
     * It goes through the Plan screen's ViewModel rather than writing here so
     * there is **one** mark-flown path and one undo. Two writers would mean two
     * copies of "log the flight, stamp the airframe, be able to reverse both",
     * and the second copy is the one that drifts. When the route has left the
     * list there is nothing to remove and nothing to offer an undo on, so the
     * action says so instead of logging a flight the user cannot retract.
     */
    onMarkFlown: () -> Boolean = { false },
    viewModel: RouteDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        modifier = modifier
            .fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    RouteTitle(route = route)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        // No navigation suite is shown over a detail screen, so unlike the
        // section scaffolds this one owns the bottom inset as well.
        contentWindowInsets = WindowInsets.safeDrawing
            .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
    ) { contentPadding ->
        // The column stops widening past `MaxContentWidth` and centres, which is
        // the same bound the Plan list uses. In landscape the window is 800 dp
        // wide and an airport's full name would otherwise be set on a single line
        // running the whole way across — a line length nobody reads comfortably,
        // and a runway row whose fields end up further apart than they are tall.
        // Beyond the bound the extra width becomes margin.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.TopCenter,
        ) {
            RouteDetailContent(
                route = route,
                state = state,
                onMarkFlown = onMarkFlown,
                onFlownConfirmed = onBack,
                snackbarHostState = snackbarHostState,
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

/**
 * The two codes and the arrow between them, as three nodes rather than one
 * formatted string.
 *
 * Split so each code can be a shared element travelling from its end of the card
 * — a single `Text` can only move as a block, which would drag the arrow out of a
 * card that never had one. TalkBack reads the merged sentence instead, because
 * "EHAM arrow KJFK" is not how anyone says it.
 *
 * **The app bar is where the pair lands, not the spine.** The obvious target is
 * the two codes on the spine below, which are the same two codes at the same
 * hierarchy — but the destination's block can sit under the fold, and a shared
 * element flying to something off screen reads as a glitch rather than as
 * continuity. The title is on screen at both ends of the journey, every time.
 */
@Composable
private fun RouteTitle(route: Destination.RouteDetail) {
    val spoken = stringResource(
        R.string.route_detail_title_spoken,
        route.departureIcao,
        route.destinationIcao,
    )

    // Derived from the codes' own type size, so the correction tracks the text it
    // is correcting instead of being a dp that is right at one scale only.
    val arrowLift = with(LocalDensity.current) {
        MaterialTheme.typography.titleLarge.fontSize.toPx().toDp() * ArrowOpticalLift
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clearAndSetSemantics { contentDescription = spoken },
    ) {
        Text(
            text = route.departureIcao,
            style = MaterialTheme.typography.titleLarge.withTabularFigures(),
            modifier = Modifier.sharedRouteElement(
                SharedRouteKeys.departure(
                    route.departureIcao,
                    route.destinationIcao,
                    route.aircraftId,
                ),
            ),
        )
        Text(
            // Mirrored by hand. The `Row` reverses under an RTL layout, so the
            // departure ends up on the right and the arrow has to point that way
            // too — otherwise it points from the destination back to the
            // departure, which is the one thing this glyph is here to say.
            text = if (LocalLayoutDirection.current == LayoutDirection.Rtl) {
                TitleArrowRtl
            } else {
                TitleArrow
            },
            // The same style as the two codes, de-emphasised by colour alone —
            // and then lifted, because matching the style is necessary and not
            // sufficient.
            //
            // `CenterVertically` centres *line boxes*. A line box is asymmetric
            // about the glyphs it holds: it reserves descender room below the
            // baseline that a run of capitals and digits never uses, so the
            // optical centre of `CN19` sits above the centre of its own box. The
            // arrow is worse — U+2192 is drawn on the font's math axis, lower
            // still. Two correct boxes, centred against each other, therefore put
            // the arrow visibly low, which is exactly how it looked.
            //
            // The lift is a fraction of the font size rather than a fixed dp, so
            // it holds at every font scale. Verified by eye at 1.0 and 2.0 —
            // optical alignment has no other test.
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.offset(y = -arrowLift),
        )
        Text(
            text = route.destinationIcao,
            style = MaterialTheme.typography.titleLarge.withTabularFigures(),
            modifier = Modifier.sharedRouteElement(
                SharedRouteKeys.destination(
                    route.departureIcao,
                    route.destinationIcao,
                    route.aircraftId,
                ),
            ),
        )
    }
}

/** Drawn rather than translated: it is a glyph, not a word — but it does mirror. */
private const val TitleArrow = "→"
private const val TitleArrowRtl = "←"

/**
 * How far the arrow is lifted, as a fraction of the codes' font size.
 *
 * Optical, not geometric: it is the gap between a line box's centre and the
 * centre of the capitals inside it, plus the arrow glyph's own seat on the math
 * axis. Tuned by eye against `CN19 → KLLJ`, which is what optical alignment is.
 */
private const val ArrowOpticalLift = 0.08f



