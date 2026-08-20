package com.github.daanbouwman.flightplanner.core.designsystem.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.daanbouwman.flightplanner.core.designsystem.theme.FlightPlannerTheme

/**
 * A sticky section header naming the month the rows beneath it belong to.
 *
 * Opaque, unlike the rest of this app's chrome. The system status and
 * navigation bars stay genuinely transparent — nothing is painted behind them
 * — but that invariant is about the *window's* chrome. A sticky header inside a
 * scrolling list is a different thing: it has to stay legible over whichever
 * row is currently passing behind it, which a translucent surface cannot
 * promise for an arbitrary row's colours.
 */
@Composable
fun MonthHeader(label: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp),
        )
    }
}

@LightDarkPreview
@Composable
private fun MonthHeaderPreview() {
    FlightPlannerTheme(dynamicColor = false) {
        MonthHeader(label = "August 2026")
    }
}
