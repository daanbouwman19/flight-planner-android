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
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
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
