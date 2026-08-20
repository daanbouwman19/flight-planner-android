package com.github.daanbouwman.flightplanner.core.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.github.daanbouwman.flightplanner.core.designsystem.theme.FlightPlannerTheme

/**
 * The "there is nothing here yet" state, with an optional way out of it.
 *
 * Distinct from [ErrorState] on purpose. An empty list and a failed load look
 * identical to a naive implementation — both render no rows — but they are
 * opposite situations: one is the app working correctly and waiting for the user
 * to do something, the other is the app having failed and needing to retry. Using
 * one component for both trains the user to ignore the message.
 *
 * The [actionLabel] is the difference between an empty state and a dead end.
 * Prefer supplying one: "no routes yet" is a description, "no routes yet —
 * Generate" is a next step.
 */
@Composable
fun EmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    icon: Painter? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (icon != null) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.size(72.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp),
                    )
                }
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        // Both halves are required: a label with no handler is a button that does
        // nothing, and a handler with no label is a button nobody can find.
        if (actionLabel != null && onAction != null) {
            Button(
                onClick = onAction,
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Text(actionLabel)
            }
        }
    }
}

@LightDarkPreview
@Composable
private fun EmptyStatePreview() {
    FlightPlannerTheme(dynamicColor = false) {
        EmptyState(
            title = "No flights logged",
            message = "Start your journey by generating a flight plan.",
            actionLabel = "Generate routes",
            onAction = {},
        )
    }
}

@LightDarkPreview
@Composable
private fun EmptyStateNoActionPreview() {
    FlightPlannerTheme(dynamicColor = false) {
        EmptyState(
            title = "No results",
            message = "No airports match “EHZZ”.",
        )
    }
}
