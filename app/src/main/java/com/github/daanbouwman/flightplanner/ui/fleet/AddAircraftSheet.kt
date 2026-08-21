package com.github.daanbouwman.flightplanner.ui.fleet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.github.daanbouwman.flightplanner.R
import com.github.daanbouwman.flightplanner.model.AircraftSpec

/**
 * A form for adding a user-defined airframe.
 *
 * Structurally follows [com.github.daanbouwman.flightplanner.ui.plan.PlanPickerSheet]'s
 * conventions — a full-height `ModalBottomSheet`, `imePadding` plus
 * `navigationBarsPadding` so the keyboard never covers the field being typed
 * into — but it is a data-entry form rather than a search list, so it has no
 * ranked results and validates locally instead.
 *
 * [id] and [AircraftSpec.isCustom] are not asked for: [FleetRepository.add]
 * assigns the id and forces `isCustom = true` for every airframe reaching it
 * through this path, so the spec built here carries throwaway values for both.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAircraftSheet(
    existingCategories: List<String>,
    onSubmit: (AircraftSpec) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var manufacturer by rememberSaveable { mutableStateOf("") }
    var variant by rememberSaveable { mutableStateOf("") }
    var icaoCode by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("") }
    var rangeNm by rememberSaveable { mutableStateOf("") }
    var cruiseSpeedKt by rememberSaveable { mutableStateOf("") }
    var takeoffDistanceM by rememberSaveable { mutableStateOf("") }
    var showErrors by rememberSaveable { mutableStateOf(false) }

    val range = rangeNm.toIntOrNull()
    val cruise = cruiseSpeedKt.toIntOrNull()
    // Optional: a blank field means "unknown", exactly as the bundled CSV
    // treats a missing takeoff distance — not zero, which would make every
    // runway look long enough.
    val takeoff = takeoffDistanceM.toIntOrNull()
    val fieldsValid = manufacturer.isNotBlank() && variant.isNotBlank() &&
        icaoCode.isNotBlank() && category.isNotBlank() &&
        range != null && range > 0 && cruise != null && cruise > 0 &&
        (takeoffDistanceM.isBlank() || (takeoff != null && takeoff > 0))

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .imePadding()
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.fleet_add_title),
                style = MaterialTheme.typography.titleLarge,
            )

            val requiredMessage = stringResource(R.string.fleet_add_error_required)
            val positiveMessage = stringResource(R.string.fleet_add_error_positive_number)

            LabelledField(
                value = manufacturer,
                onValueChange = { manufacturer = it },
                label = stringResource(R.string.fleet_add_manufacturer),
                error = showErrors && manufacturer.isBlank(),
                supportingText = requiredMessage,
                capitalization = KeyboardCapitalization.Words,
            )
            LabelledField(
                value = variant,
                onValueChange = { variant = it },
                label = stringResource(R.string.fleet_add_variant),
                error = showErrors && variant.isBlank(),
                supportingText = requiredMessage,
                capitalization = KeyboardCapitalization.Words,
            )
            LabelledField(
                value = icaoCode,
                onValueChange = { icaoCode = it },
                label = stringResource(R.string.fleet_add_icao_code),
                error = showErrors && icaoCode.isBlank(),
                supportingText = requiredMessage,
                capitalization = KeyboardCapitalization.Characters,
            )
            CategoryField(
                value = category,
                onValueChange = { category = it },
                existingCategories = existingCategories,
                error = showErrors && category.isBlank(),
                supportingText = requiredMessage,
            )

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

            Button(
                onClick = {
                    if (!fieldsValid) {
                        showErrors = true
                        return@Button
                    }
                    onSubmit(
                        AircraftSpec(
                            id = 0,
                            manufacturer = manufacturer.trim(),
                            variant = variant.trim(),
                            icaoCode = icaoCode.trim().uppercase(),
                            flown = false,
                            rangeNm = range ?: 0,
                            category = category.trim(),
                            cruiseSpeedKt = cruise ?: 0,
                            dateFlown = null,
                            takeoffDistanceMeters = takeoff,
                        ),
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 16.dp),
            ) {
                Text(stringResource(R.string.fleet_add_submit))
            }
        }
    }
}

/**
 * The category field: a free-text `OutlinedTextField` with a picker attached,
 * rather than a plain field or a closed dropdown.
 *
 * Category is not a fixed enum anywhere in this app — [AircraftSpec.category]
 * is free text seeded from the bundled CSV, same as the desktop's own table —
 * so a picker that could only choose from [existingCategories] would make it
 * impossible to add a genuinely new one. `ExposedDropdownMenuBox` in its
 * editable form is the standard M3 shape for exactly this: type to filter the
 * existing set, tap a suggestion to accept it, or keep typing something that
 * matches nothing and it is accepted as a new category on submit.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryField(
    value: String,
    onValueChange: (String) -> Unit,
    existingCategories: List<String>,
    error: Boolean,
    supportingText: String,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val suggestions = remember(value, existingCategories) {
        if (value.isBlank()) {
            existingCategories
        } else {
            existingCategories.filter { it.contains(value, ignoreCase = true) }
        }
    }

    ExposedDropdownMenuBox(
        expanded = expanded && suggestions.isNotEmpty(),
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                onValueChange(it)
                expanded = true
            },
            label = { Text(stringResource(R.string.fleet_add_category)) },
            isError = error,
            supportingText = if (error) { { Text(supportingText) } } else null,
            singleLine = true,
            shape = MaterialTheme.shapes.large,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
        )
        // Plain `DropdownMenuItem`s, not a `LazyColumn` — `ExposedDropdownMenuBox`
        // measures its menu content intrinsically to size itself, and a
        // `SubcomposeLayout`-based list (which a lazy list is) cannot answer an
        // intrinsic measurement; it crashed the moment this opened. The menu
        // already scrolls its own content when it overflows, and a fleet's
        // distinct categories are a handful of rows, not thousands, so there is
        // nothing a lazy list would have bought here anyway.
        ExposedDropdownMenu(expanded = expanded && suggestions.isNotEmpty(), onDismissRequest = { expanded = false }) {
            suggestions.forEach { suggestion ->
                DropdownMenuItem(
                    text = { Text(suggestion) },
                    onClick = {
                        onValueChange(suggestion)
                        expanded = false
                    },
                )
            }
        }
    }
}

/** Shared with [EditEnvelopeSheet] — one validated-field look for every aircraft form. */
@Composable
internal fun LabelledField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    error: Boolean,
    supportingText: String,
    modifier: Modifier = Modifier,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        isError = error,
        supportingText = if (error) { { Text(supportingText) } } else null,
        singleLine = true,
        shape = MaterialTheme.shapes.large,
        keyboardOptions = KeyboardOptions(
            capitalization = capitalization,
            keyboardType = keyboardType,
            imeAction = imeAction,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}
