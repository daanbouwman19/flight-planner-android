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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.pager.VerticalPager
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.material3.AnimatedPage
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.PagerScaffoldDefaults
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

    // Wear's own `VerticalPager`, not the one in `androidx.compose.foundation`.
    // It is the same idea tuned for a watch, and two of those differences are
    // the reason it is here:
    //
    //  - it takes a [RotaryScrollableBehavior], which is what makes the bezel
    //    work. Every rotary input a watch has — a crown, a rotating bezel, or
    //    the capacitive ring on a Galaxy Watch that has no moving part —
    //    arrives as the same `RotaryScrollEvent`, so wiring this one parameter
    //    covers all three. Without it the rim is simply dead, and a touch swipe
    //    is the only way through the feed.
    //  - it uses a larger touch slop (`CustomTouchSlopMultiplier`) and a fling
    //    tuned to a page that fills a round face, so a flick no longer has to
    //    beat the system's own edge gestures to register.
    //
    // `pageSize` is gone because a Wear page always fills the face, which is
    // what `PageSize.Fill` was asking for.
    VerticalPager(
        state = pager,
        modifier = Modifier.fillMaxSize(),
        key = { page -> routes[page].key() },
        flingBehavior = PagerScaffoldDefaults.snapWithSpringFlingBehavior(pager),
        rotaryScrollableBehavior = RotaryScrollableDefaults.snapBehavior(pager),
    ) { page ->
        // [AnimatedPage] is the transition, and it is taken rather than written
        // because a watch page turn is not a slide: the outgoing face scales
        // down and fades as it leaves while the incoming one comes up to meet
        // it, which is what stops two full-bleed maps from shearing past one
        // another. The curve is Wear's own, which also keeps this module clear
        // of the raw `spring()` the invariants forbid outside
        // `:core:designsystem` — a facade `:wear` deliberately cannot reach.
        AnimatedPage(pageIndex = page, pagerState = pager) {
            RouteFace(route = routes[page], outline = outline, onOpenOnPhone = onOpenOnPhone)
        }
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

        WatchRouteMap(
            arc = route.arc,
            outline = outline,
            palette = rememberWatchMapPalette(),
            modifier = Modifier.fillMaxSize(),
        )

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
            // Each code gets half of what the arrow leaves, rather than taking
            // what it needs in turn. A plain `Row` hands the first child the
            // whole remaining width and the second whatever survives, so it was
            // always the *destination* that ran out of room and wrapped —
            // `ZYBA` came out as `ZYB` over `A`. `fill = false` keeps a short
            // code its natural width, so the pair stays optically centred.
            IcaoCode(code = route.departureIcao, modifier = Modifier.weight(1f, fill = false))
            RouteArrow(
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(horizontal = ARROW_GAP_DP.dp)
                    .size(width = 18.dp, height = 12.dp),
            )
            IcaoCode(code = route.destinationIcao, modifier = Modifier.weight(1f, fill = false))
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
                textAlign = TextAlign.Center,
                // Inset further than the column it sits in — see
                // [AIRFRAME_SIDE_INSET]. The padding is the difference, because
                // the column has already applied [SIDE_INSET_BOTTOM].
                modifier = Modifier.padding(
                    top = 10.dp,
                    start = face * (AIRFRAME_SIDE_INSET - SIDE_INSET_BOTTOM),
                    end = face * (AIRFRAME_SIDE_INSET - SIDE_INSET_BOTTOM),
                ),
            )
        }
    }
}

/**
 * One airport code, which may shrink but may never wrap.
 *
 * Every ICAO ident is four characters and the two of them plus the arrow are
 * the widest thing on the face, so at the design's 30 sp they did not fit the
 * width the round face leaves at that height — measured on a 480 px watch, the
 * pair needs about 187 dp against roughly 181 dp of usable chord. Dropping to a
 * fixed smaller size would only move the cliff: `SAVC` measures 74 dp where a
 * letter-heavy code like `EDMM` is wider still, so any constant is a size that
 * some real code overflows.
 *
 * [BasicText] with [TextAutoSize] instead keeps 30 sp whenever it fits — which
 * is the ordinary case — and steps down only for the codes that need it. The
 * floor is 22 sp because below that the code stops being readable at a glance,
 * which is the whole job of this line. `maxLines = 1` is what makes a miss
 * impossible: with autosizing above it, the text has no way to become two lines.
 */
@Composable
private fun IcaoCode(code: String, modifier: Modifier = Modifier) {
    BasicText(
        text = code,
        modifier = modifier,
        style = WearRouteType.code.copy(color = MaterialTheme.colorScheme.onSurface),
        maxLines = 1,
        autoSize = TextAutoSize.StepBased(
            minFontSize = CODE_MIN_SP.sp,
            maxFontSize = CODE_MAX_SP.sp,
            stepSize = 1.sp,
        ),
    )
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
    // Centred, where it used to sit 20 dp from the top.
    //
    // Two measurements moved it. It had no width bound, and 20 dp down a 480 px
    // circle the chord is only about 129 dp, so the pill's ends ran under the
    // bezel exactly as the codes and the airframe line did. Lowering it far
    // enough to have a chord to sit on then put it straight through the two
    // codes, which is worse: the pill is opaque, so the header showed as a
    // stray letter either side of it.
    //
    // There is no third position along the top — a round face simply has no
    // band above a full-width header. The middle is the one place on this
    // layout with nothing in it but map, and it is the widest line on the
    // circle, so the message sets on one line and clears both groups. It stays
    // a flash and not a dialog: no scrim, no buttons, gone on its own.
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val face = maxHeight
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = shown,
                style = WearRouteType.caption,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .widthIn(max = face * (1f - 2 * FLASH_SIDE_INSET))
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
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

/**
 * 74 / 454 in the design canvas, nudged down to buy the codes width.
 *
 * Near the top of a circle a few pixels of height are worth a lot of chord, so
 * dropping the row from 0.145 widens the usable line by about 22 px — enough
 * that the codes need only step from 30 sp to roughly 27 sp rather than down
 * near their floor. At 0.145 the tops of the outer letters cleared the glass by
 * a single pixel, which reads on the device as the text touching the bezel.
 */
private const val TOP_GROUP_FRACTION = 0.17f

/** Places the airframe line at about 364 / 454, with the plates just above it. */
private const val BOTTOM_GROUP_FRACTION = 0.12f

/**
 * Sized so the outer letters keep about 9 dp of glass, not so the row is as wide
 * as it can be.
 *
 * The constraint is not the middle of the codes but the *top* corners of the
 * outer two letters, where the chord is at its narrowest across the glyphs. 0.13
 * was too tight for 30 sp text and wrapped the destination; 0.10 fitted the text
 * but left it one pixel off the circle, measured. 0.135 with [TOP_GROUP_FRACTION]
 * lowered is the pair that clears the glass — the codes give up about 3 sp to
 * [TextAutoSize] for it, which is the right trade on a round face.
 */
private const val SIDE_INSET_TOP = 0.135f
private const val SIDE_INSET_BOTTOM = 0.10f

/**
 * The airframe line's own inset, wider than [SIDE_INSET_BOTTOM].
 *
 * It sits below the two plates, and on a circle that lower line is markedly
 * narrower: measured on this face the plates' row has about 181 dp to play with
 * where the bottom of the airframe glyphs has about 147 dp. Sharing the plates'
 * inset is what let `McDonnell Douglas MD-11 GE` set one line 357 px wide
 * across a 348 px chord, so its ends ran under the bezel. A longer name wraps
 * instead, which the line already did and which the column has room for.
 */
private const val AIRFRAME_SIDE_INSET = 0.20f

/** Half the gap around the arrow. Tightened with [SIDE_INSET_TOP] to buy the codes width. */
private const val ARROW_GAP_DP = 5

private const val CODE_MAX_SP = 30
private const val CODE_MIN_SP = 22
private const val PLATE_ALPHA = 0.72f
private const val ARROW_STROKE_DP = 2.0f
private const val ARROW_BARB_FRACTION = 0.34f
private const val FLASH_MILLIS = 2_000L

/** Caps the flash's width so it cannot reach the bezel — see [HandoffFlash]. */
private const val FLASH_SIDE_INSET = 0.14f
