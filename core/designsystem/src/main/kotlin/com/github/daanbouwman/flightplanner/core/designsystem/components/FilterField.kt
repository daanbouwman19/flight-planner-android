package com.github.daanbouwman.flightplanner.core.designsystem.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.daanbouwman.flightplanner.core.designsystem.motion.FlightMotion
import com.github.daanbouwman.flightplanner.core.designsystem.theme.FlightPlannerTheme

/**
 * One field of a filter: what it filters, what it is set to, and what that costs.
 *
 * ```
 * ┌──────────────────────┐
 * │ AIRCRAFT             │  label   — what this constrains
 * │ 737-600              │  value   — what it is set to, or "Any"
 * │ 3,010 NM · 6,900 ft  │  detail  — the constraint that follows from it
 * └──────────────────────┘
 * ```
 *
 * This is the app's own dense-data grammar — the tiny wide-tracked caps label over
 * a short identifier in tabular figures, exactly as [ValueChip] sets `DIST 645 NM`
 * — made tappable. A control that filters a list of figures should be set like
 * those figures; when it is not, a screen reads as two dialects arguing.
 *
 * ### The detail line is the point
 *
 * A filter chip can say *what* is set. A field can say what that **means**:
 * "3,010 NM · 6,900 ft" is the envelope every route in the list was generated
 * inside, and it is the reason the list looks the way it does. Nothing else in the
 * app shows it. Pass whatever plays that role — an airport's name for a departure,
 * range and required runway for an airframe.
 *
 * There is **no line held for it when there is nothing to show**, which is the
 * opposite of what the route card does with its flight-rules slot — and the
 * difference is who acted. Weather arrives on its own, so a card that grows when it
 * lands moves under a reader who did nothing; a filter changes because the user just
 * chose something, so the field growing *is* the answer to their tap. Holding the
 * line here would only mean two tall boxes of nothing in the state the screen
 * launches in, which is the emptiest state shouting loudest. Give the field
 * `fillMaxHeight` inside a `Modifier.height(IntrinsicSize.Min)` row to keep a pair
 * level when only one of them is set.
 *
 * ### Outline means unset, filled means set
 *
 * One selection language, shared with [ModeSelector]: a hairline outline is "no
 * constraint", a filled container is "constrained". Both are legible on any surface,
 * which is what lets this be drawn straight onto the page background with nothing
 * behind it — a fill chosen one step off a container colour would disappear there.
 *
 * Semantics are merged, so a screen reader announces "AIRCRAFT, 737-600, 3,010 NM ·
 * 6,900 ft, button" as one thing.
 */
@Composable
fun FilterField(
    label: String,
    value: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    detail: String? = null,
) {
    val container by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            Color.Transparent
        },
        animationSpec = FlightMotion.effects(),
        label = "filterFieldContainer",
    )
    // Unset draws in the variant colour, not the full one. An unset filter is the
    // absence of a constraint, and it should recede — the field that is doing
    // something to the list is the one that gets the contrast.
    val content by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = FlightMotion.effects(),
        label = "filterFieldContent",
    )
    // Animated to transparent rather than removed, so setting a filter does not
    // relayout the field by a hairline as it fills.
    val border by animateColorAsState(
        targetValue = if (selected) Color.Transparent else MaterialTheme.colorScheme.outlineVariant,
        animationSpec = FlightMotion.effects(),
        label = "filterFieldBorder",
    )
    val secondary by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = FlightMotion.effects(),
        label = "filterFieldSecondary",
    )

    Surface(
        onClick = onClick,
        modifier = modifier.semantics(mergeDescendants = true) {},
        shape = MaterialTheme.shapes.medium,
        color = container,
        contentColor = content,
        border = BorderStroke(1.dp, border),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(
                text = label,
                // Wider than `labelSmall` ships. Letter-spaced caps is what makes a
                // three-letter word read as a *field name* rather than as shouting,
                // and it is the one typographic liberty this component takes.
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = LabelTracking),
                color = secondary,
                maxLines = 1,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            detail?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = secondary,
                    // Wraps where the value ellipsises, because this line is the
                    // only place its content appears. At font scale 2.0 a single
                    // line clips "950 NM · 2,723 ft" to "950 NM · 2,7…", which
                    // throws away the runway figure the line exists to give; the
                    // value above it truncates safely, since a half-shown type code
                    // still identifies the airframe and the picker has it in full.
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Tracking for the field label. Roughly a tenth of an em, which is where caps stop crowding. */
private val LabelTracking = 1.2.sp

@LightDarkPreview
@CompactWidthPreview
@Composable
private fun FilterFieldPreview() {
    FlightPlannerTheme(dynamicColor = false) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Set and unset side by side, which is the only pairing that shows
            // whether the two states read as one control at two settings.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterField(
                    label = "DEPARTURE",
                    value = "EHAM",
                    detail = "Amsterdam Airport Schiphol",
                    selected = true,
                    onClick = {},
                    modifier = Modifier.weight(1f),
                )
                FilterField(
                    label = "AIRCRAFT",
                    value = "Any",
                    selected = false,
                    onClick = {},
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterField(
                    label = "DEPARTURE",
                    value = "Any",
                    selected = false,
                    onClick = {},
                    modifier = Modifier.weight(1f),
                )
                FilterField(
                    label = "AIRCRAFT",
                    value = "737-600",
                    detail = "3,010 NM · 6,900 ft",
                    selected = true,
                    onClick = {},
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
