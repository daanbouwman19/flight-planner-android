package com.github.daanbouwman.flightplanner.ui.plan

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.daanbouwman.flightplanner.ui.asFigure
import com.github.daanbouwman.flightplanner.R
import com.github.daanbouwman.flightplanner.core.designsystem.components.CompactWidthPreview
import com.github.daanbouwman.flightplanner.core.designsystem.components.EmptyState
import com.github.daanbouwman.flightplanner.core.designsystem.components.LightDarkPreview
import com.github.daanbouwman.flightplanner.core.designsystem.theme.FlightPlannerTheme
import com.github.daanbouwman.flightplanner.core.designsystem.theme.withTabularFigures
import com.github.daanbouwman.flightplanner.model.AircraftSpec
import com.github.daanbouwman.flightplanner.model.Airport

/** Which picker the sheet is showing. */
enum class PickerTarget { Departure, Aircraft }

/**
 * The full-height picker behind the two filter chips.
 *
 * One sheet serves both filters rather than two nearly identical ones: the
 * frame — title, search field, a way to clear the filter, a ranked list — is
 * the same, and only the row is different. Splitting them would duplicate the
 * keyboard handling, the inset handling and the clear affordance three times
 * over by the time the Airports screen wants the same thing.
 *
 * The query lives in the ViewModel, not here, so that the ranked scan survives
 * the sheet being recomposed and so the search can be tested without Compose.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanPickerSheet(
    target: PickerTarget,
    query: String,
    airports: List<Airport>,
    aircraft: List<AircraftSpec>,
    hasSelection: Boolean,
    sheetState: SheetState,
    onQueryChange: (String) -> Unit,
    onPickAirport: (Airport) -> Unit,
    onPickAircraft: (AircraftSpec) -> Unit,
    onClearSelection: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        val focusRequester = remember { FocusRequester() }
        LaunchedEffect(target) {
            // The sheet exists to be typed into, so it takes focus — one frame
            // after it composes, so that the keyboard rises *with* the sheet.
            //
            // This waited for `SheetValue.Expanded` first, to keep the input
            // method service's start-up off the frames the slide is running on.
            // It did that, and it cost the thing the sheet is for: two
            // animations that could overlap were run end to end instead.
            // Measured on the emulator, on a debug build, first open after a
            // cold start: tap to keyboard-shown 2.25 s, of which 1.55 s was the
            // wait — a dead beat with a settled sheet and no keyboard, long
            // enough to reach for the field a second time. Requesting a frame
            // after composition instead starts the keyboard while the sheet is
            // still sliding, and the same measurement is 1.33 s. (Both figures
            // are inflated by the build — a debug APK runs interpreted — so
            // read the difference, not the absolute.) No skipped-frame warning
            // was logged either way.
            //
            // The frame is not decoration. `requestFocus` needs the node
            // attached, and going straight from the effect's first resumption
            // ties the IME start-up to the same frame as the sheet's first
            // layout and the fifty suggestion rows underneath it.
            withFrameNanos { }
            focusRequester.requestFocus()
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(SheetHeightFraction)
                .padding(horizontal = 16.dp)
                // The keyboard covers a bottom sheet by default; without this the
                // results the user is typing to reveal are the ones hidden.
                .imePadding()
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(
                    when (target) {
                        PickerTarget.Departure -> R.string.plan_picker_departure_title
                        PickerTarget.Aircraft -> R.string.plan_picker_aircraft_title
                    },
                ),
                style = MaterialTheme.typography.titleLarge,
            )

            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                singleLine = true,
                shape = MaterialTheme.shapes.large,
                placeholder = {
                    Text(
                        stringResource(
                            when (target) {
                                PickerTarget.Departure -> R.string.plan_picker_departure_hint
                                PickerTarget.Aircraft -> R.string.plan_picker_aircraft_hint
                            },
                        ),
                    )
                },
                leadingIcon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_plan_search),
                        contentDescription = null,
                    )
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_plan_clear),
                                contentDescription = stringResource(R.string.plan_picker_clear_query),
                            )
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(
                    // ICAO codes are upper case and this box is mostly used to
                    // type one; the search itself is case-insensitive either way.
                    capitalization = if (target == PickerTarget.Departure) {
                        KeyboardCapitalization.Characters
                    } else {
                        KeyboardCapitalization.Words
                    },
                    imeAction = ImeAction.Search,
                ),
            )

            if (hasSelection) {
                ListItem(
                    modifier = Modifier.clickable(onClick = onClearSelection),
                    leadingContent = {
                        Icon(
                            painter = painterResource(R.drawable.ic_plan_clear),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                ) {
                    Text(
                        text = stringResource(
                            when (target) {
                                PickerTarget.Departure -> R.string.plan_picker_clear_departure
                                PickerTarget.Aircraft -> R.string.plan_picker_clear_aircraft
                            },
                        ),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                HorizontalDivider()
            }

            when (target) {
                PickerTarget.Departure -> AirportResults(query, airports, onPickAirport)
                PickerTarget.Aircraft -> AircraftResults(query, aircraft, onPickAircraft)
            }
        }
    }
}

@Composable
private fun AirportResults(
    query: String,
    airports: List<Airport>,
    onPick: (Airport) -> Unit,
) {
    if (airports.isEmpty()) {
        NoResults(query)
        return
    }
    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        if (query.isBlank()) {
            item { SuggestionsHeader(R.string.plan_picker_largest_airports) }
        }
        items(airports, key = { it.id }) { airport ->
            ListItem(
                modifier = Modifier.clickable { onPick(airport) },
                supportingContent = {
                    Text(
                        text = listOfNotNull(airport.name, airport.municipality, airport.country)
                            .joinToString(" · "),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                trailingContent = {
                    Text(
                        text = stringResource(R.string.plan_value_feet, airport.longestRunwayFt.asFigure()),
                        style = MaterialTheme.typography.labelMedium.withTabularFigures(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            ) {
                Text(
                    text = airport.icao,
                    style = MaterialTheme.typography.titleMedium.withTabularFigures(),
                )
            }
        }
    }
}

@Composable
private fun AircraftResults(
    query: String,
    aircraft: List<AircraftSpec>,
    onPick: (AircraftSpec) -> Unit,
) {
    if (aircraft.isEmpty()) {
        NoResults(query)
        return
    }
    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        items(aircraft, key = { it.id }) { spec ->
            ListItem(
                modifier = Modifier.clickable { onPick(spec) },
                supportingContent = {
                    Text(
                        text = stringResource(
                            R.string.plan_picker_aircraft_detail,
                            spec.category,
                            spec.rangeNm.asFigure(),
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                trailingContent = {
                    if (spec.flown) {
                        Icon(
                            painter = painterResource(R.drawable.ic_plan_flown),
                            contentDescription = stringResource(R.string.plan_picker_already_flown),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
            ) {
                Text(spec.displayName)
            }
        }
    }
}

@Composable
private fun SuggestionsHeader(labelRes: Int) {
    Text(
        text = stringResource(labelRes),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun NoResults(query: String) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        EmptyState(
            title = stringResource(R.string.plan_picker_no_results_title),
            message = stringResource(R.string.plan_picker_no_results_message, query),
        )
    }
}

private const val SheetHeightFraction = 0.9f

/*
 * The sheet itself needs a `SheetState` and a host window, which the tooling
 * cannot supply, so the previews target the two result lists inside it. They are
 * the parts with layout worth checking: an airport row carries three pieces of
 * text and a runway figure on one line, which is the row most likely to overflow
 * on a narrow screen.
 */

@LightDarkPreview
@CompactWidthPreview
@Composable
private fun AirportResultsPreview() {
    FlightPlannerTheme(dynamicColor = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            AirportResults(
                query = "AMS",
                airports = listOf(
                    PlanPreviewData.schiphol,
                    PlanPreviewData.kennedy,
                    PlanPreviewData.haneda,
                ),
                onPick = {},
            )
        }
    }
}

@LightDarkPreview
@Composable
private fun AircraftResultsPreview() {
    FlightPlannerTheme(dynamicColor = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            AircraftResults(
                query = "",
                aircraft = listOf(PlanPreviewData.boeing, PlanPreviewData.cessna),
                onPick = {},
            )
        }
    }
}

@LightDarkPreview
@Composable
private fun NoResultsPreview() {
    FlightPlannerTheme(dynamicColor = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            NoResults(query = "EHZZ")
        }
    }
}
