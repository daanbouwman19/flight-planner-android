package com.github.daanbouwman.flightplanner.ui.plan

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
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
    searchScope: SearchScope,
    sheetState: SheetState,
    onQueryChange: (String) -> Unit,
    onPickAirport: (Airport) -> Unit,
    onPickAircraft: (AircraftSpec) -> Unit,
    onClearSelection: () -> Unit,
    onRetrySearch: () -> Unit,
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
                PickerTarget.Departure -> {
                    // Only while there is a query to narrow. With the field empty
                    // the list below is "Largest airports", which is ranked by
                    // runway length out of the in-memory index and does not touch
                    // the name index at all — so nothing is degraded and saying so
                    // would be noise on the state the sheet opens in.
                    if (query.isNotBlank()) {
                        SearchScopeNotice(searchScope, onRetrySearch)
                    }
                    AirportResults(query, airports, onPickAirport)
                }
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

/**
 * Says so when the search behind the field is narrower than the field promises.
 *
 * The placeholder offers "Code, name or city", and for the first moments after a
 * cold start — or permanently, if the build failed — only the first of the three
 * is true. Without this, typing "Schiphol" comes back empty and reads as *this app
 * does not have Schiphol*, which is the wrong conclusion about the wrong thing.
 * The point is to move the blame from the airport to the index.
 *
 * A tonal strip rather than [EmptyState] or an error colour: the rows underneath
 * it are real and pickable, so this annotates a working list rather than replacing
 * a broken one. Only the failed case gets a button, because the loading case
 * resolves itself in well under a second and a Retry on it would be an invitation
 * to press something that does nothing.
 *
 * A polite live region, because it appears and disappears on its own schedule —
 * a background build, not anything the user did — and can therefore change under
 * someone who is already typing. It has to merge its descendants to be one: a
 * live region announces the text of the node it is set on, and the [Surface]
 * carries none of its own. The Retry button stays separately focusable, because
 * a child that merges its own descendants is not absorbed by a merging parent.
 */
@Composable
private fun SearchScopeNotice(
    scope: SearchScope,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val message = when (scope) {
        // Nothing is degraded, so nothing is said. Guarded here as well as at the
        // call site so this can never draw as an empty strip.
        SearchScope.Full -> return
        SearchScope.NamesLoading -> stringResource(R.string.plan_picker_names_loading)
        SearchScope.NamesUnavailable -> stringResource(R.string.plan_picker_names_unavailable)
    }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            // Tighter on the end than the start: a text button carries its own
            // touch-target padding, and matching the two visually leaves the label
            // sitting a long way in from the edge.
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 8.dp),
            )
            if (scope == SearchScope.NamesUnavailable) {
                TextButton(onClick = onRetry) {
                    Text(stringResource(R.string.plan_picker_names_retry))
                }
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

@LightDarkPreview
@CompactWidthPreview
@Composable
private fun SearchScopeNoticePreview() {
    FlightPlannerTheme(dynamicColor = false) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Both states together: the loading one has to read as "wait a
                // moment" and the failed one as "this is not coming back on its
                // own", and the only thing separating them is wording and a button.
                SearchScopeNotice(SearchScope.NamesLoading, onRetry = {})
                SearchScopeNotice(SearchScope.NamesUnavailable, onRetry = {})
            }
        }
    }
}
