package com.github.daanbouwman.flightplanner.core.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.daanbouwman.flightplanner.core.designsystem.theme.FlightPlannerTheme

/** One choice in a [ModeSelector]. */
@Immutable
data class ModeOption(
    val label: String,
    /**
     * How many things this option would show, drawn after the label as
     * "Not flown · 116".
     *
     * Null for an option where a count would mean nothing — "All" is not
     * informative as a number, and "This aircraft" depends on a choice made
     * elsewhere.
     */
    val count: Int? = null,
    /**
     * Spoken instead of the label and count when they are ambiguous out of
     * context — "Not flown · 116" reads as a heading with a number; "Not flown,
     * 116 aircraft" is a sentence. Falls back to the label.
     */
    val contentDescription: String? = null,
    val enabled: Boolean = true,
)

/**
 * A single-choice selector: which pool of things the list below is drawn from.
 *
 * ### Why chips, and not a segmented row or a `ButtonGroup`
 *
 * The Expressive component for this job is `ButtonGroup`, and it **crashes** in
 * material3 `1.5.0-alpha26` — deterministically, at the default font scale, with
 * three items filling the width, so the screen never draws. This wrapped
 * `SingleChoiceSegmentedButtonRow` instead, which cost more than it looked:
 *
 *  - A segmented row divides the width into **equal** parts, so every option is as
 *    wide as the longest one needs and none of them can be as wide as it wants.
 *    "Not flown 116" did not fit a third of a 360 dp phone and ellipsised to
 *    "Not flown 1…", which is worse than no count at all because it reads as a
 *    *wrong number* — so the count had to be hidden in a `contentDescription`,
 *    visible only to a screen reader.
 *  - It ellipsises rather than wrapping, so at font scale 2.0 the labels are
 *    truncated instead of taking the second line they need.
 *
 * Single-select chips in a [FlowRow] fix both: each chip is as wide as its own
 * label, the count is drawn where a sighted user can read it, and a row that no
 * longer fits wraps instead of shortening its words. They are also the more honest
 * component — this filters a collection, which is what a filter chip is for.
 *
 * The selected chip is *filled and nothing else*. Material offers a leading
 * checkmark as well; the fill already says which one is on, and the tick spends
 * the width that made the count fit.
 *
 * The row is a `selectableGroup` of radio-button-role chips, so TalkBack announces
 * "2 of 3" rather than three unrelated toggles.
 *
 * @param selectedIndex which option is active. Out-of-range means none.
 * @param onSelect called with the index of the option the user chose. It is
 *   *not* called when the already-selected option is tapped: this is a
 *   single-choice control, so unselecting would leave no mode at all.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ModeSelector(
    options: List<ModeOption>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEachIndexed { index, option ->
            val selected = index == selectedIndex
            FilterChip(
                selected = selected,
                // Re-tapping the active option is a no-op rather than a
                // deselect: every caller needs exactly one mode to be true.
                onClick = { if (!selected) onSelect(index) },
                enabled = option.enabled,
                label = {
                    Text(
                        text = option.count?.let { "${option.label} · $it" } ?: option.label,
                        // A guard, not the layout: chips are sized to their content
                        // and the row wraps, so this only bites for a label longer
                        // than the whole window.
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                modifier = Modifier.semantics {
                    role = Role.RadioButton
                    option.contentDescription?.let { contentDescription = it }
                },
            )
        }
    }
}

@LightDarkPreview
@CompactWidthPreview
@Composable
private fun ModeSelectorPreview() {
    FlightPlannerTheme(dynamicColor = false) {
        ModeSelector(
            options = listOf(
                ModeOption("All"),
                ModeOption("Not flown", count = 116),
                ModeOption("This aircraft"),
            ),
            selectedIndex = 1,
            onSelect = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}
