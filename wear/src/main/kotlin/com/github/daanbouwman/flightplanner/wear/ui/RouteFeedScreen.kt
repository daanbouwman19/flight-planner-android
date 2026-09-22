package com.github.daanbouwman.flightplanner.wear.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.github.daanbouwman.flightplanner.routing.WorldOutline
import com.github.daanbouwman.flightplanner.wear.R
import com.github.daanbouwman.flightplanner.wear.handoff.HandoffResult
import com.github.daanbouwman.flightplanner.wear.route.WatchRouteCard
import com.github.daanbouwman.flightplanner.wear.route.WatchRouteFeedState
import com.github.daanbouwman.flightplanner.wear.ui.theme.WearRouteType
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow

/**
 * The whole watch app: one route filling the face, a swipe up for the next, a
 * tap to open the one on screen in the phone app.
 *
 * Stateless in the usual sense — everything it needs arrives as arguments — so
 * a preview or a test can compose it without Hilt, an asset or a paired phone.
 */
@Composable
internal fun RouteFeedScreen(
    state: WatchRouteFeedState,
    handoffs: Flow<HandoffResult>,
    onPageSettled: (Int) -> Unit,
    onOpenOnPhone: (WatchRouteCard) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        when (state) {
            WatchRouteFeedState.Loading -> CentredMessage(stringResource(R.string.route_loading))

            WatchRouteFeedState.Unavailable -> CentredMessage(
                title = stringResource(R.string.route_unavailable),
                detail = stringResource(R.string.route_unavailable_detail),
            )

            is WatchRouteFeedState.Ready -> RoutePager(
                routes = state.routes,
                outline = state.outline,
                onPageSettled = onPageSettled,
                onOpenOnPhone = onOpenOnPhone,
            )
        }
        HandoffFlash(handoffs)
    }
}

@Composable
private fun RoutePager(
    routes: List<WatchRouteCard>,
    outline: WorldOutline,
    onPageSettled: (Int) -> Unit,
    onOpenOnPhone: (WatchRouteCard) -> Unit,
) {
    // The count is read through a lambda, so appending a batch widens the pager
    // in place rather than resetting the wearer to the top of the list.
    val pager = rememberPagerState { routes.size }

    LaunchedEffect(pager) {
        // `settledPage`, not `currentPage`: the feed tops up when the wearer
        // arrives somewhere, not on every frame of a flick past it.
        snapshotFlow { pager.settledPage }.collect { page -> onPageSettled(page) }
    }

    VerticalPager(
        state = pager,
        pageSize = PageSize.Fill,
        modifier = Modifier.fillMaxSize(),
        key = { page -> routes[page].key() },
    ) { page ->
        RouteFace(route = routes[page], outline = outline, onOpenOnPhone = onOpenOnPhone)
    }
}

/**
 * One route, drawn to the design: the two codes across the top, the two figures
 * and the airframe across the bottom, the great circle behind all of it.
 *
 * ### Placement
 *
 * Positions are fractions of the face rather than dp, because the design was
 * drawn against one 454 px round screen and the thing that has to stay true on
 * another is where the text sits *relative to the circle* — a fixed dp inset
 * that clears the bezel at one diameter crops at another. The horizontal insets
 * are wider than a rectangular screen would need for the same reason: at 15% and
 * 85% of the height a circle is only about three-quarters as wide as it is
 * across the middle, so text laid out to the full width would run under the
 * bezel.
 *
 * ### The tap
 *
 * The whole face is the target — there is one action, and the wearer should not
 * have to find it — and it carries no ripple. A ripple across the full face
 * would be a flash of the entire screen; the confirmation that matters here is
 * the haptic tick, and then what the phone says back through [HandoffFlash].
 */
@Composable
private fun RouteFace(
    route: WatchRouteCard,
    outline: WorldOutline,
    onOpenOnPhone: (WatchRouteCard) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val interactions = remember { MutableInteractionSource() }
    val description = stringResource(
        R.string.route_face_description,
        spellOut(route.departureIcao),
        spellOut(route.destinationIcao),
        route.distanceText,
        route.aircraftName,
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = interactions,
                indication = null,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onOpenOnPhone(route)
                },
            )
            // One description for the whole face: a screen reader should read
            // out the route, not walk five fragments of it.
            .clearAndSetSemantics { contentDescription = description },
    ) {
        val face = maxHeight

        WatchRouteMap(arc = route.arc, outline = outline, modifier = Modifier.fillMaxSize())

        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(
                    top = face * TOP_GROUP_FRACTION,
                    start = face * SIDE_INSET_TOP,
                    end = face * SIDE_INSET_TOP,
                ),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = route.departureIcao,
                style = WearRouteType.code,
                color = MaterialTheme.colorScheme.onSurface,
            )
            RouteArrow(
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .size(width = 22.dp, height = 14.dp),
            )
            Text(
                text = route.destinationIcao,
                style = WearRouteType.code,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(
                    bottom = face * BOTTOM_GROUP_FRACTION,
                    start = face * SIDE_INSET_BOTTOM,
                    end = face * SIDE_INSET_BOTTOM,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ValuePlate(label = stringResource(R.string.label_distance), value = route.distanceText)
                ValuePlate(label = stringResource(R.string.label_time), value = route.eteText)
            }
            Text(
                text = route.aircraftName,
                style = WearRouteType.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

/**
 * The chevron between the two codes.
 *
 * Drawn rather than set as a glyph: `→` renders differently per font and picks
 * up the text's own weight, and this is the one place on the face where the
 * direction of travel is stated. The proportions are the design canvas's.
 */
@Composable
private fun RouteArrow(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val stroke = Stroke(
            width = ARROW_STROKE_DP.dp.toPx(),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        )
        val midY = size.height / 2f
        val head = size.width - stroke.width / 2f
        drawLine(
            color = color,
            start = Offset(stroke.width / 2f, midY),
            end = Offset(head, midY),
            strokeWidth = stroke.width,
            cap = StrokeCap.Round,
        )
        val barb = size.height * ARROW_BARB_FRACTION
        drawPath(
            path = Path().apply {
                moveTo(head - barb, midY - barb)
                lineTo(head, midY)
                lineTo(head - barb, midY + barb)
            },
            color = color,
            style = stroke,
        )
    }
}

/**
 * One figure and its caption, on a translucent plate.
 *
 * Translucent rather than opaque — the design canvas sets it at 0.72 — so the
 * coastline behind it reads as one continuous shape rather than being
 * interrupted by two solid tiles.
 */
@Composable
private fun ValuePlate(label: String, value: String) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = PLATE_ALPHA))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = label, style = WearRouteType.chipLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = WearRouteType.chipValue, color = MaterialTheme.colorScheme.onSurface)
    }
}

/**
 * What came of the last tap, shown briefly at the top of the face.
 *
 * Not a dialog: this reports on something happening on another device while the
 * wearer carries on swiping here, and a dialog would take the face away from
 * them to say so.
 */
@Composable
private fun HandoffFlash(handoffs: Flow<HandoffResult>) {
    val sent = stringResource(R.string.handoff_sent)
    val failed = stringResource(R.string.handoff_failed)
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(handoffs, sent, failed) {
        handoffs.collect { result ->
            message = if (result == HandoffResult.Sent) sent else failed
        }
    }
    LaunchedEffect(message) {
        if (message == null) return@LaunchedEffect
        delay(FLASH_MILLIS)
        message = null
    }

    val shown = message ?: return
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 20.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Text(
            text = shown,
            style = WearRouteType.caption,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun CentredMessage(title: String, detail: String? = null) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = title, style = WearRouteType.chipValue, color = MaterialTheme.colorScheme.onSurface)
        if (detail != null) {
            Text(
                text = detail,
                style = WearRouteType.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

/**
 * `EHAM` as `E H A M`, so a screen reader says the letters rather than trying to
 * pronounce the code as a word.
 */
private fun spellOut(icao: String): String = icao.toCharArray().joinToString(" ")

/** 74 / 454 in the design canvas, less a little for the code's own line height. */
private const val TOP_GROUP_FRACTION = 0.145f

/** Places the airframe line at about 364 / 454, with the plates just above it. */
private const val BOTTOM_GROUP_FRACTION = 0.12f

private const val SIDE_INSET_TOP = 0.13f
private const val SIDE_INSET_BOTTOM = 0.10f
private const val PLATE_ALPHA = 0.72f
private const val ARROW_STROKE_DP = 2.0f
private const val ARROW_BARB_FRACTION = 0.34f
private const val FLASH_MILLIS = 2_000L
