package com.github.daanbouwman.flightplanner.ui.fleet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.github.daanbouwman.flightplanner.R
import com.github.daanbouwman.flightplanner.model.AircraftSpec

/**
 * Range, cruise and takeoff distance, editable — the one thing about an
 * existing airframe this app lets a user change.
 *
 * Deliberately narrower than [AddAircraftSheet]: D5 scopes editing to the
 * envelope alone, not identity (manufacturer, variant, category, ICAO code),
 * which stays whatever the bundled seed or the original add specified. A
 * sheet of its own rather than an inline form on the detail screen — the app
 * already has one place a user types aircraft numbers into, and reusing that
 * shape here means there is one input pattern instead of two.
 *
 * Takeoff distance is asked for in metres, not the feet
 * [com.github.daanbouwman.flightplanner.ui.fleet.FleetDetailContent]'s hero
 * chip shows — [AircraftSpec]'s own KDoc documents that asymmetry as
 * inherited from the desktop's CSV format and kept deliberately, so the field
 * matches the unit that is actually stored.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditEnvelopeSheet(
    aircraft: AircraftSpec,
    onSave: (rangeNm: Int, cruiseSpeedKt: Int, takeoffDistanceMeters: Int?) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var rangeNm by rememberSaveable(aircraft.id) { mutableStateOf(aircraft.rangeNm.toString()) }
    var cruiseSpeedKt by rememberSaveable(aircraft.id) { mutableStateOf(aircraft.cruiseSpeedKt.toString()) }
    var takeoffDistanceM by rememberSaveable(aircraft.id) {
        mutableStateOf(aircraft.takeoffDistanceMeters?.toString().orEmpty())
    }
    var showErrors by rememberSaveable(aircraft.id) { mutableStateOf(false) }

    val range = rangeNm.toIntOrNull()
    val cruise = cruiseSpeedKt.toIntOrNull()
    // Optional: a blank field means "unknown", not zero — see AddAircraftSheet.
    val takeoff = takeoffDistanceM.toIntOrNull()
    val fieldsValid = range != null && range > 0 && cruise != null && cruise > 0 &&
        (takeoffDistanceM.isBlank() || (takeoff != null && takeoff > 0))

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .imePadding()
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.fleet_detail_edit_title),
                style = MaterialTheme.typography.titleLarge,
            )

            val positiveMessage = stringResource(R.string.fleet_add_error_positive_number)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                LabelledField(
                    value = rangeNm,
                    onValueChange = { rangeNm = it.filter(Char::isDigit) },
                    label = stringResource(R.string.fleet_add_range),
                    error = showErrors && (range == null || range <= 0),
                    supportingText = positiveMessage,
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.weight(1f),
                )
                LabelledField(
                    value = cruiseSpeedKt,
                    onValueChange = { cruiseSpeedKt = it.filter(Char::isDigit) },
                    label = stringResource(R.string.fleet_add_cruise),
                    error = showErrors && (cruise == null || cruise <= 0),
                    supportingText = positiveMessage,
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.weight(1f),
                )
            }
            LabelledField(
                value = takeoffDistanceM,
                onValueChange = { takeoffDistanceM = it.filter(Char::isDigit) },
                label = stringResource(R.string.fleet_add_takeoff),
                error = showErrors && takeoffDistanceM.isNotBlank() && (takeoff == null || takeoff <= 0),
                supportingText = positiveMessage,
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done,
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
            ) {
                Button(
                    onClick = {
                        if (!fieldsValid) {
                            showErrors = true
                            return@Button
                        }
                        onSave(range ?: 0, cruise ?: 0, takeoff)
                    },
                ) {
                    Text(stringResource(R.string.fleet_detail_action_save))
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.fleet_detail_action_cancel))
                }
            }
        }
    }
}
