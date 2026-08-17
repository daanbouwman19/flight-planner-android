package com.github.daanbouwman.flightplanner.core.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.github.daanbouwman.flightplanner.core.designsystem.theme.FlightPlannerTheme

/**
 * The "something went wrong" state, with a way to try again.
 *
 * The title is drawn in `error` rather than `onSurface`, which is the one place
 * that colour is used in this app's ordinary chrome — so it reads as a failure at
 * a glance without needing an icon or a red banner.
 *
 * Marked as a polite live region. An error usually appears *after* the screen has
 * settled, replacing content the user was already looking at, so a screen-reader
 * user who is not currently focused on this subtree would otherwise never learn
 * the load failed. "Polite" rather than "assertive" because a failed METAR fetch
 * should not interrupt what is being read aloud.
 *
 * Distinct from [EmptyState] on purpose — see the note there.
 */
@Composable
fun ErrorState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 24.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (onRetry != null) {
            FilledTonalButton(
                onClick = onRetry,
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Text("Try again")
            }
        }
    }
}

@LightDarkPreview
@Composable
private fun ErrorStatePreview() {
    FlightPlannerTheme(dynamicColor = false) {
        ErrorState(
            title = "Could not load weather",
            message = "No connection to the weather service. Routes still work offline.",
            onRetry = {},
        )
    }
}
