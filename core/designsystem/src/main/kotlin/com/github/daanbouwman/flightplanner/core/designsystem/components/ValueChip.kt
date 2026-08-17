package com.github.daanbouwman.flightplanner.core.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.github.daanbouwman.flightplanner.core.designsystem.theme.FlightPlannerTheme

/**
 * A labelled figure — "DIST 3,451 nm", "RWY 12,467 ft".
 *
 * The workhorse of every dense surface in the app, which is why the value is
 * rendered in `labelLarge`: that slot carries tabular figures, so a row of chips
 * stays aligned and a value that changes does not shove its neighbours sideways.
 *
 * ### Label left, value right
 *
 * When the chip is sized to its content the two arrangements are identical, and
 * this only matters once a caller stretches it — a grid of equal-width chips,
 * say. There the choice is between centring the pair and pinning it to both
 * edges, and pinning wins for a column of figures: centred content moves left
 * and right as the value's width changes, so "13 NM" and "2,990 NM" sit at
 * different offsets and the eye has to re-find the number on every row. Pinned,
 * the labels align down one edge and the digits down the other, which is how a
 * table of numbers has always been set and the reason the figures are tabular in
 * the first place.
 *
 * Semantics are merged so a screen reader announces "DIST, 3,451 nm" as one
 * thing rather than as two unrelated fragments.
 */
@Composable
fun ValueChip(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.semantics(mergeDescendants = true) {},
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                // A guaranteed gap: `SpaceBetween` leaves none of its own once
                // the content fills the chip, and a label touching its value
                // reads as one word.
                modifier = Modifier.padding(end = 8.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(text = value, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@LightDarkPreview
@Composable
private fun ValueChipPreview() {
    FlightPlannerTheme(dynamicColor = false) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ValueChip(label = "DIST", value = "3,451 nm")
            ValueChip(label = "TIME", value = "07:12")
            ValueChip(label = "RWY", value = "12,467 ft")
        }
    }
}
