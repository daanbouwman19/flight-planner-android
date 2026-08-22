package com.github.daanbouwman.flightplanner.core.designsystem.components

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.github.daanbouwman.flightplanner.core.designsystem.R
import com.github.daanbouwman.flightplanner.core.designsystem.theme.FlightPlannerTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * A single-date picker, gated to [maxDate] or earlier.
 *
 * `DatePicker`/`DatePickerDialog` compile clean under plain
 * `ExperimentalMaterial3Api` on the pinned `material3:1.5.0-alpha26` — no
 * `ExperimentalMaterial3ExpressiveApi` opt-in needed, confirmed by a compile
 * probe (see `docs/API-GROUND-TRUTH.md`). This wrapper exists for the same
 * reason [ConfirmationDialog] does: dialogs live in the design system, not
 * because the Expressive-containment rule is load-bearing here.
 *
 * [selectedDate]/[onDateSelected] speak [LocalDate]; the material3 state
 * underneath works in UTC epoch millis, and converting through the *device*
 * zone would shift the date by one day near midnight in a negative-UTC-offset
 * zone — the conversion here is pinned to [ZoneOffset.UTC] on both ends for
 * exactly that reason.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlightDatePickerDialog(
    selectedDate: LocalDate,
    maxDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val maxDateMillis = maxDate.toEpochMillisUtc()
    val state = rememberDatePickerState(
        initialSelectedDateMillis = selectedDate.toEpochMillisUtc(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= maxDateMillis
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        confirmButton = {
            TextButton(
                onClick = {
                    state.selectedDateMillis?.let { onDateSelected(it.toLocalDateUtc()) } ?: onDismiss()
                },
            ) { Text(stringResource(R.string.ds_date_picker_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.ds_confirmation_dialog_cancel)) }
        },
    ) {
        DatePicker(state = state)
    }
}

private fun LocalDate.toEpochMillisUtc(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toLocalDateUtc(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

@LightDarkPreview
@Composable
private fun FlightDatePickerDialogPreview() {
    FlightPlannerTheme(dynamicColor = false) {
        FlightDatePickerDialog(
            selectedDate = LocalDate.of(2026, 8, 12),
            maxDate = LocalDate.of(2026, 8, 22),
            onDateSelected = {},
            onDismiss = {},
        )
    }
}
