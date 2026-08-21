package com.github.daanbouwman.flightplanner.core.designsystem.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.github.daanbouwman.flightplanner.core.designsystem.R
import com.github.daanbouwman.flightplanner.core.designsystem.theme.FlightPlannerTheme

/**
 * A prompt before an action that is awkward or impossible to reverse.
 *
 * Plain `AlertDialog` rather than an Expressive dialog — the pinned material3
 * alpha has no Expressive dialog surface, and a dialog is not itself an
 * Expressive symbol, so there is nothing here for `:core:designsystem`'s
 * containment rule to guard against.
 *
 * [title], [message] and [confirmLabel] are always screen-supplied — the
 * component owns no opinion about what it is confirming — but the dismissal
 * itself reads the same everywhere, so "Cancel" lives here rather than being
 * threaded through every caller.
 */
@Composable
fun ConfirmationDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.ds_confirmation_dialog_cancel))
            }
        },
    )
}

@LightDarkPreview
@Composable
private fun ConfirmationDialogPreview() {
    FlightPlannerTheme(dynamicColor = false) {
        ConfirmationDialog(
            title = "Mark all aircraft not flown?",
            message = "This clears the flown flag and date for every airframe in your fleet. " +
                "Logged flights are not affected.",
            confirmLabel = "Mark all not flown",
            onConfirm = {},
            onDismiss = {},
        )
    }
}
