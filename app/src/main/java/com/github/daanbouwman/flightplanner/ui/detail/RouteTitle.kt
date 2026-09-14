package com.github.daanbouwman.flightplanner.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.github.daanbouwman.flightplanner.R
import com.github.daanbouwman.flightplanner.core.designsystem.theme.withTabularFigures
import com.github.daanbouwman.flightplanner.navigation.Destination
import com.github.daanbouwman.flightplanner.ui.chrome.SharedRouteKeys
import com.github.daanbouwman.flightplanner.ui.chrome.sharedRouteElement

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
 *
 * ### One title, three hosts
 *
 * The full-screen detail, the detail *pane* and the immersive globe's plate all
 * head themselves with this pair. Two of them used to draw the *spoken* string
 * instead — `"EHAM to KJFK"` — which is a sentence written for a screen reader
 * and reads as one on a display, beside a third heading that draws the arrow.
 * The shared-element modifiers are no-ops wherever no transition scope is
 * provided (the pane and the immersive plate both compose without one), so the
 * same composable serves all three without the hosts knowing the difference.
 */
@Composable
fun RouteTitle(route: Destination.RouteDetail, modifier: Modifier = Modifier) {
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
        modifier = modifier.clearAndSetSemantics { contentDescription = spoken },
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
