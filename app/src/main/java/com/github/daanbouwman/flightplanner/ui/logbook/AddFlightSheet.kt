package com.github.daanbouwman.flightplanner.ui.logbook

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.daanbouwman.flightplanner.R
import com.github.daanbouwman.flightplanner.core.designsystem.components.CompactWidthPreview
import com.github.daanbouwman.flightplanner.core.designsystem.components.FilterField
import com.github.daanbouwman.flightplanner.core.designsystem.components.FlightDatePickerDialog
import com.github.daanbouwman.flightplanner.core.designsystem.components.LightDarkPreview
import com.github.daanbouwman.flightplanner.core.designsystem.components.ValueChip
import com.github.daanbouwman.flightplanner.core.designsystem.theme.FlightPlannerTheme
import com.github.daanbouwman.flightplanner.model.AircraftSpec
import com.github.daanbouwman.flightplanner.model.Airport
import com.github.daanbouwman.flightplanner.ui.distanceText
import com.github.daanbouwman.flightplanner.ui.lengthText
import com.github.daanbouwman.flightplanner.ui.picker.PickerTarget
import com.github.daanbouwman.flightplanner.ui.picker.PlanPickerSheet
import com.github.daanbouwman.flightplanner.ui.plan.SearchScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * A form for logging a flight directly, independent of route generation —
 * task D2.
 *
 * Structurally a [ModalBottomSheet], like [com.github.daanbouwman.flightplanner.ui.fleet.AddAircraftSheet];
 * its four fields open a second, stacked sheet — [PlanPickerSheet], reused
 * from the Plan screen with a third [PickerTarget.Destination] added, rather
 * than a new picker built for this one screen.
 *
 * ### Divergence from the Rust reference
 *
 * The desktop's `AddHistoryPopup` (`add_history_popup.rs`) has no date field
 * at all — it always stamps the current date on submit. This screen adds one
 * deliberately (per `docs/UI-PLAN.md`'s D2 task spec): a flight logged after
 * the fact — the common case for backfilling a paper logbook — should carry
 * the date it actually happened on, not the day it was typed in. Every other
 * rule ([AddFlightValidation]'s required fields, distinct airports, distance
 * within range) is ported field-for-field from the same file; the date itself
 * never participates in validation, matching the reference, where it cannot.
 *
 * Submitting only inserts a logbook row — it does not mark the aircraft
 * flown. See [LogbookViewModel.addFlight] for why.
 *
 * The three result flows and [searchScope] are collected here rather than by
 * the caller, and only inside the block that shows a specific picker — the
 * same reason `PlanScreen` collects its own picker's results only while that
 * picker is open: search does no work until someone is actually looking at
 * results, and here that means not even the sheet being open is enough,
 * since the sheet can be open with no picker on top of it yet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFlightSheet(
    aircraftResults: StateFlow<List<AircraftSpec>>,
    departureResults: StateFlow<List<Airport>>,
    destinationResults: StateFlow<List<Airport>>,
    searchScope: StateFlow<SearchScope>,
    onAircraftQueryChange: (String) -> Unit,
    onDepartureQueryChange: (String) -> Unit,
    onDestinationQueryChange: (String) -> Unit,
    onRetrySearch: () -> Unit,
    onSubmit: (aircraft: AircraftSpec, departure: Airport, destination: Airport, date: LocalDate, distanceNm: Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // `remember`, not `rememberSaveable`, unlike AddAircraftSheet's fields:
    // Airport and AircraftSpec are plain :core:model classes, which the
    // module boundary in CLAUDE.md keeps free of Android imports including
    // Parcelable, so they cannot be saved across a configuration change the
    // way AddAircraftSheet's plain-String fields are. A fill-in-progress is
    // lost on rotation as a result — the same accepted gap PlanScreen's own
    // picker-open state already has, for the identical reason.
    var selectedAircraft by remember { mutableStateOf<AircraftSpec?>(null) }
    var selectedDeparture by remember { mutableStateOf<Airport?>(null) }
    var selectedDestination by remember { mutableStateOf<Airport?>(null) }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var activePicker by remember { mutableStateOf<PickerTarget?>(null) }
    var pickerQuery by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }
    var showErrors by remember { mutableStateOf(false) }

    val validation = remember(selectedAircraft, selectedDeparture, selectedDestination) {
        AddFlightValidation(selectedAircraft, selectedDeparture, selectedDestination)
    }

    fun openPicker(target: PickerTarget, currentQuery: (String) -> Unit) {
        pickerQuery = ""
        currentQuery("")
        activePicker = target
    }

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
                text = stringResource(R.string.logbook_add_title),
                style = MaterialTheme.typography.titleLarge,
            )

            AddFlightFields(
                aircraft = selectedAircraft,
                date = date,
                departure = selectedDeparture,
                destination = selectedDestination,
                onPickAircraft = { openPicker(PickerTarget.Aircraft, onAircraftQueryChange) },
                onPickDate = { showDatePicker = true },
                onPickDeparture = { openPicker(PickerTarget.Departure, onDepartureQueryChange) },
                onPickDestination = { openPicker(PickerTarget.Destination, onDestinationQueryChange) },
            )

            validation.distanceNm?.let { distance ->
                ValueChip(
                    label = stringResource(R.string.plan_chip_distance),
                    value = distanceText(distance),
                )
            }

            if (showErrors) {
                val errorMessage = when {
                    // overRange implies both aircraft and distanceNm are non-null.
                    validation.overRange -> stringResource(
                        R.string.logbook_add_error_over_range,
                        distanceText(validation.distanceNm!!),
                        distanceText(selectedAircraft!!.rangeNm),
                    )
                    validation.sameAirport -> stringResource(R.string.logbook_add_error_same_airport)
                    // Not a specific rule to explain, just fields still unset —
                    // the Rust reference greys its Add button out for this case;
                    // this sheet's button stays enabled throughout (matching
                    // AddAircraftSheet's own convention), so this line is what
                    // tells the tap it did nothing.
                    !validation.canSubmit -> stringResource(R.string.logbook_add_error_missing_fields)
                    else -> null
                }
                errorMessage?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            Button(
                onClick = {
                    val aircraft = selectedAircraft
                    val departure = selectedDeparture
                    val destination = selectedDestination
                    val distanceNm = validation.distanceNm
                    if (validation.canSubmit && aircraft != null && departure != null &&
                        destination != null && distanceNm != null
                    ) {
                        onSubmit(aircraft, departure, destination, date, distanceNm)
                    } else {
                        showErrors = true
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 16.dp),
            ) {
                Text(stringResource(R.string.logbook_add_submit))
            }
        }
    }

    activePicker?.let { target ->
        val pickerSheetState = rememberBottomSheetState(SheetValue.Hidden, setOf(SheetValue.Hidden, SheetValue.Expanded))
        val scope = rememberCoroutineScope()

        // Only the flow the open picker actually needs is collected — the
        // other airport target and the aircraft results stay untouched.
        val airports = when (target) {
            PickerTarget.Departure -> departureResults.collectAsStateWithLifecycle().value
            PickerTarget.Destination -> destinationResults.collectAsStateWithLifecycle().value
            PickerTarget.Aircraft -> emptyList()
        }
        val aircraftForPicker = if (target == PickerTarget.Aircraft) {
            aircraftResults.collectAsStateWithLifecycle().value
        } else {
            emptyList()
        }
        val searchScopeState by searchScope.collectAsStateWithLifecycle()

        PlanPickerSheet(
            target = target,
            query = pickerQuery,
            airports = airports,
            aircraft = aircraftForPicker,
            hasSelection = when (target) {
                PickerTarget.Departure -> selectedDeparture != null
                PickerTarget.Destination -> selectedDestination != null
                PickerTarget.Aircraft -> selectedAircraft != null
            },
            searchScope = searchScopeState,
            sheetState = pickerSheetState,
            onQueryChange = {
                pickerQuery = it
                when (target) {
                    PickerTarget.Departure -> onDepartureQueryChange(it)
                    PickerTarget.Destination -> onDestinationQueryChange(it)
                    PickerTarget.Aircraft -> onAircraftQueryChange(it)
                }
            },
            onPickAirport = { airport ->
                when (target) {
                    PickerTarget.Departure -> selectedDeparture = airport
                    PickerTarget.Destination -> selectedDestination = airport
                    PickerTarget.Aircraft -> Unit
                }
                scope.launch { pickerSheetState.hide() }.invokeOnCompletion { activePicker = null }
            },
            onPickAircraft = { spec ->
                selectedAircraft = spec
                scope.launch { pickerSheetState.hide() }.invokeOnCompletion { activePicker = null }
            },
            onClearSelection = {
                when (target) {
                    PickerTarget.Departure -> selectedDeparture = null
                    PickerTarget.Destination -> selectedDestination = null
                    PickerTarget.Aircraft -> selectedAircraft = null
                }
                scope.launch { pickerSheetState.hide() }.invokeOnCompletion { activePicker = null }
            },
            onRetrySearch = onRetrySearch,
            onDismiss = { activePicker = null },
        )
    }

    if (showDatePicker) {
        FlightDatePickerDialog(
            selectedDate = date,
            maxDate = LocalDate.now(),
            onDateSelected = {
                date = it
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false },
        )
    }
}

/**
 * The four fields: what flies, when, and the leg it flew — extracted from
 * [AddFlightSheet] so the layout (two rows of two, the shape [FilterField]
 * itself expects) can be previewed without a `SheetState`.
 */
@Composable
private fun AddFlightFields(
    aircraft: AircraftSpec?,
    date: LocalDate,
    departure: Airport?,
    destination: Airport?,
    onPickAircraft: () -> Unit,
    onPickDate: () -> Unit,
    onPickDeparture: () -> Unit,
    onPickDestination: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val unset = stringResource(R.string.plan_filter_unset)
    val dateFormatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterField(
                label = stringResource(R.string.logbook_add_field_aircraft),
                value = aircraft?.variant ?: unset,
                detail = aircraft?.let {
                    stringResource(R.string.plan_filter_envelope, distanceText(it.rangeNm), lengthText(it.requiredRunwayFt))
                },
                selected = aircraft != null,
                onClick = onPickAircraft,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            // A date is never "unset" — it defaults to today — so this field is
            // always drawn filled, unlike every other field in the sheet.
            FilterField(
                label = stringResource(R.string.logbook_add_field_date),
                value = date.format(dateFormatter),
                selected = true,
                onClick = onPickDate,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterField(
                label = stringResource(R.string.logbook_add_field_departure),
                value = departure?.icao ?: unset,
                detail = departure?.name,
                selected = departure != null,
                onClick = onPickDeparture,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            FilterField(
                label = stringResource(R.string.logbook_add_field_destination),
                value = destination?.icao ?: unset,
                detail = destination?.name,
                selected = destination != null,
                onClick = onPickDestination,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
    }
}

@LightDarkPreview
@CompactWidthPreview
@Composable
private fun AddFlightFieldsPreview() {
    FlightPlannerTheme(dynamicColor = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Unset, then set: the set state carries the longest strings —
                // full airport names and a two-figure envelope — so this is the
                // pairing that shows whether the fields hold at 360 dp and font
                // scale 2.0, matching PlanFilterFieldsPreview's own reasoning.
                AddFlightFields(
                    aircraft = null,
                    date = LocalDate.of(2026, 8, 22),
                    departure = null,
                    destination = null,
                    onPickAircraft = {},
                    onPickDate = {},
                    onPickDeparture = {},
                    onPickDestination = {},
                )
                AddFlightFields(
                    aircraft = PreviewAircraft,
                    date = LocalDate.of(2026, 8, 22),
                    departure = PreviewSchiphol,
                    destination = PreviewKennedy,
                    onPickAircraft = {},
                    onPickDate = {},
                    onPickDeparture = {},
                    onPickDestination = {},
                )
            }
        }
    }
}

private val PreviewAircraft = AircraftSpec(
    id = 1,
    manufacturer = "Boeing",
    variant = "737-600",
    icaoCode = "B736",
    flown = false,
    rangeNm = 3010,
    category = "Jet",
    cruiseSpeedKt = 450,
    dateFlown = null,
    takeoffDistanceMeters = 2100,
)

private val PreviewSchiphol = Airport(
    id = 1,
    icao = "EHAM",
    name = "Amsterdam Airport Schiphol",
    latitude = 52.3086,
    longitude = 4.7639,
    elevationFt = -11,
    country = "NL",
    municipality = "Amsterdam",
    sizeClass = com.github.daanbouwman.flightplanner.model.AirportSizeClass.LARGE,
    longestRunwayFt = 12467,
    runwayCount = 6,
    hasHardSurface = true,
    hasIcaoCode = true,
)

private val PreviewKennedy = Airport(
    id = 2,
    icao = "KJFK",
    name = "John F Kennedy International",
    latitude = 40.6398,
    longitude = -73.7789,
    elevationFt = 13,
    country = "US",
    municipality = "New York",
    sizeClass = com.github.daanbouwman.flightplanner.model.AirportSizeClass.LARGE,
    longestRunwayFt = 14511,
    runwayCount = 4,
    hasHardSurface = true,
    hasIcaoCode = true,
)
