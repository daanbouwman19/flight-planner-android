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
import com.github.daanbouwman.flightplanner.ui.LocalUnitSystem
import com.github.daanbouwman.flightplanner.ui.displayDistanceToNm
import com.github.daanbouwman.flightplanner.ui.displayLengthToTakeoffMeters
import com.github.daanbouwman.flightplanner.ui.displaySpeedToKt
import com.github.daanbouwman.flightplanner.ui.distanceUnitSuffix
import com.github.daanbouwman.flightplanner.ui.ktToDisplaySpeed
import com.github.daanbouwman.flightplanner.ui.lengthUnitSuffix
import com.github.daanbouwman.flightplanner.ui.nmToDisplayDistance
import com.github.daanbouwman.flightplanner.ui.speedUnitSuffix
import com.github.daanbouwman.flightplanner.ui.takeoffMetersToDisplayLength

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
 * ### The fields are in the reader's unit, not the stored one
 *
 * The three figures are shown and typed in the **active unit system** and
 * converted on the way in and out through the conversions in `Figures.kt`, so
 * under Aviation the takeoff field says "Takeoff distance (ft)" and shows the
 * same figure the hero chip above it does, and under Metric all three read in
 * km, km/h and m. It used to ask for metres under a fixed "(m)" label beside a
 * chip that said feet, on the reasoning that the field should match what
 * [AircraftSpec] stores -- but the stored unit is the *database's* concern, and
 * a form that shows one unit and asks for another is a conversion the user has
 * to do in their head. See the round-trip note on those conversions for the
 * one place a typed figure can come back a unit off.
 *
 * The `> 0` validation runs on the typed figure, which is unit-invariant; any
 * future threshold that is not applies to the stored value after conversion.
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

    // Keyed on the unit as well as the airframe: a figure typed as kilometres
    // must not survive a switch to Aviation and be read back as nautical miles.
    val unit = LocalUnitSystem.current
    var rangeText by rememberSaveable(aircraft.id, unit) {
        mutableStateOf(nmToDisplayDistance(aircraft.rangeNm, unit).toString())
    }
    var cruiseText by rememberSaveable(aircraft.id, unit) {
        mutableStateOf(ktToDisplaySpeed(aircraft.cruiseSpeedKt, unit).toString())
    }
    var takeoffText by rememberSaveable(aircraft.id, unit) {
        mutableStateOf(
            aircraft.takeoffDistanceMeters?.let { takeoffMetersToDisplayLength(it, unit) }?.toString().orEmpty(),
        )
    }
    var showErrors by rememberSaveable(aircraft.id) { mutableStateOf(false) }

    val range = rangeText.toIntOrNull()
    val cruise = cruiseText.toIntOrNull()
    // Optional: a blank field means "unknown", not zero — see AddAircraftSheet.
    val takeoff = takeoffText.toIntOrNull()
    val fieldsValid = range != null && range > 0 && cruise != null && cruise > 0 &&
        (takeoffText.isBlank() || (takeoff != null && takeoff > 0))

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
                    value = rangeText,
                    onValueChange = { rangeText = it.filter(Char::isDigit) },
                    label = unitFieldLabel(R.string.fleet_add_range, distanceUnitSuffix(unit)),
                    error = showErrors && (range == null || range <= 0),
                    supportingText = positiveMessage,
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.weight(1f),
                )
                LabelledField(
                    value = cruiseText,
                    onValueChange = { cruiseText = it.filter(Char::isDigit) },
                    label = unitFieldLabel(R.string.fleet_add_cruise, speedUnitSuffix(unit)),
                    error = showErrors && (cruise == null || cruise <= 0),
                    supportingText = positiveMessage,
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.weight(1f),
                )
            }
            LabelledField(
                value = takeoffText,
                onValueChange = { takeoffText = it.filter(Char::isDigit) },
                label = unitFieldLabel(R.string.fleet_add_takeoff, lengthUnitSuffix(unit)),
                error = showErrors && takeoffText.isNotBlank() && (takeoff == null || takeoff <= 0),
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
                        onSave(
                            displayDistanceToNm(range ?: 0, unit),
                            displaySpeedToKt(cruise ?: 0, unit),
                            takeoff?.let { displayLengthToTakeoffMeters(it, unit) },
                        )
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
