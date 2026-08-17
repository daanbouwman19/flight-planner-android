package com.github.daanbouwman.flightplanner.core.designsystem.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.daanbouwman.flightplanner.core.designsystem.theme.FlightPlannerTheme

/** One choice in a [ModeSelector]. */
@Immutable
data class ModeOption(
    val label: String,
    /**
     * Spoken instead of [label] when the label alone is ambiguous out of
     * context — "Not flown 12" reads as a heading; "Not flown, 12 aircraft" is a
     * sentence. Falls back to [label].
     */
    val contentDescription: String? = null,
    val enabled: Boolean = true,
)

/**
 * A single-choice selector.
 *
 * ### Why this is not `ButtonGroup`
 *
 * The Expressive component for this job is `ButtonGroup`, and that is what this
 * originally used. **`ButtonGroup` in material3 `1.5.0-alpha26` crashes.** With
 * three `toggleableItem`s and an overflow indicator, filling the width of an
 * ordinary phone at the default font scale, `ButtonGroupMeasurePolicy.measure`
 * builds a `Constraints` with a negative width and throws
 * `IllegalArgumentException: maxWidth must be >= than minWidth`. It is
 * deterministic, not a size edge case — the app cannot draw the Plan screen at
 * all.
 *
 * So this wraps the stable `SingleChoiceSegmentedButtonRow` instead. That is a
 * real loss: the button group squashes its neighbours as an item is pressed, and
 * it moves items that no longer fit into an overflow menu rather than letting
 * them shrink. A segmented row does neither, so at a large font scale the labels
 * ellipsise instead of overflowing gracefully.
 *
 * That trade is why this component exists. The rule that screens reach Expressive
 * only through `:core:designsystem` is what makes a crashing alpha component a
 * change to one file rather than to every screen that used it, and it is what
 * will make going back to `ButtonGroup` a change to one file too, once the alpha
 * is fixed. Re-test with a long third label at font scale 2.0 before switching
 * back. See docs/API-GROUND-TRUTH.md.
 *
 * @param selectedIndex which option is active. Out-of-range means none.
 * @param onSelect called with the index of the option the user chose. It is
 *   *not* called when the already-selected option is tapped: this is a
 *   single-choice control, so unselecting would leave no mode at all.
 */
@Composable
fun ModeSelector(
    options: List<ModeOption>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        options.forEachIndexed { index, option ->
            val selected = index == selectedIndex
            SegmentedButton(
                selected = selected,
                // Re-tapping the active option is a no-op rather than a
                // deselect: every caller needs exactly one mode to be true.
                onClick = { if (!selected) onSelect(index) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                enabled = option.enabled,
                modifier = Modifier.semantics {
                    option.contentDescription?.let { contentDescription = it }
                },
            ) {
                Text(
                    text = option.label,
                    // Ellipsise rather than wrap. A segmented control that grows
                    // to two lines shoves the list below it down the screen, and
                    // the first characters of these labels are the distinguishing
                    // ones anyway.
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@LightDarkPreview
@Composable
private fun ModeSelectorPreview() {
    FlightPlannerTheme(dynamicColor = false) {
        ModeSelector(
            options = listOf(
                ModeOption("Any"),
                ModeOption("Not flown 12"),
                ModeOption("This aircraft"),
            ),
            selectedIndex = 1,
            onSelect = {},
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
        )
    }
}
