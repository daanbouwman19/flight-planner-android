package com.github.daanbouwman.flightplanner.ui.airports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.daanbouwman.flightplanner.R
import com.github.daanbouwman.flightplanner.core.designsystem.components.CompactWidthPreview
import com.github.daanbouwman.flightplanner.core.designsystem.components.EmptyState
import com.github.daanbouwman.flightplanner.core.designsystem.components.LightDarkPreview
import com.github.daanbouwman.flightplanner.core.designsystem.theme.FlightPlannerTheme
import com.github.daanbouwman.flightplanner.model.Airport
import com.github.daanbouwman.flightplanner.ui.AirportRow
import com.github.daanbouwman.flightplanner.ui.SuggestionsHeader
import com.github.daanbouwman.flightplanner.ui.picker.SearchScopeNotice
import com.github.daanbouwman.flightplanner.ui.plan.PlanPreviewData
import com.github.daanbouwman.flightplanner.ui.plan.SearchScope

/**
 * Airports browse (E1): ranked type-ahead over the whole index, plus the
 * desktop reference's Random-50 action.
 *
 * A flat push destination reached from Plan's own header overflow — not a
 * bar-hosted screen — so it does not need the immersive chrome machinery
 * (`rememberChromeScrollConnection`/`ContentInsets`) B17 built specifically
 * for the five bar screens. A plain `Scaffold` with a `TopAppBar` is the
 * right complexity match, the same shape `FleetDetailScreen` uses for its
 * own flat destination.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AirportsScreen(
    onBack: () -> Unit,
    onOpenAirport: (Airport) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AirportsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.airports_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing
            .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
    ) { contentPadding ->
        AirportsContent(
            state = state,
            onQueryChange = viewModel::setQuery,
            onRandomize = viewModel::randomize,
            onRetrySearch = viewModel::retryNameSearch,
            onOpenAirport = onOpenAirport,
            modifier = Modifier.padding(contentPadding),
        )
    }
}

/**
 * The screen minus its ViewModel — `AirportsScreen` takes a Hilt ViewModel
 * and cannot be rendered by the tooling, so previews target this.
 */
@Composable
internal fun AirportsContent(
    state: AirportsUiState,
    onQueryChange: (String) -> Unit,
    onRandomize: () -> Unit,
    onRetrySearch: () -> Unit,
    onOpenAirport: (Airport) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                shape = MaterialTheme.shapes.large,
                placeholder = { Text(stringResource(R.string.plan_picker_departure_hint)) },
                leadingIcon = {
                    Icon(painter = painterResource(R.drawable.ic_plan_search), contentDescription = null)
                },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_plan_clear),
                                contentDescription = stringResource(R.string.plan_picker_clear_query),
                            )
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    imeAction = ImeAction.Search,
                ),
            )
            FilledTonalButton(onClick = onRandomize) {
                Text(stringResource(R.string.airports_action_random))
            }
        }

        if (state.query.isNotBlank()) {
            SearchScopeNotice(
                scope = state.searchScope,
                onRetry = onRetrySearch,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        if (state.airports.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                EmptyState(
                    title = stringResource(R.string.plan_picker_no_results_title),
                    message = stringResource(R.string.plan_picker_no_results_message, state.query),
                )
            }
            return@Column
        }

        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            if (state.query.isBlank()) {
                val labelRes = if (state.isRandomBatch) {
                    R.string.airports_random_header
                } else {
                    R.string.plan_picker_largest_airports
                }
                item { SuggestionsHeader(stringResource(labelRes)) }
            }
            items(state.airports, key = { it.id }) { airport ->
                AirportRow(airport = airport, onClick = { onOpenAirport(airport) })
            }
        }
    }
}

@LightDarkPreview
@CompactWidthPreview
@Composable
private fun AirportsContentSuggestionsPreview() {
    FlightPlannerTheme(dynamicColor = false) {
        AirportsContent(
            state = AirportsUiState(
                airports = listOf(PlanPreviewData.schiphol, PlanPreviewData.kennedy, PlanPreviewData.haneda),
            ),
            onQueryChange = {},
            onRandomize = {},
            onRetrySearch = {},
            onOpenAirport = {},
        )
    }
}

@LightDarkPreview
@Composable
private fun AirportsContentRandomPreview() {
    FlightPlannerTheme(dynamicColor = false) {
        AirportsContent(
            state = AirportsUiState(
                airports = listOf(PlanPreviewData.innsbruck, PlanPreviewData.kennedy),
                isRandomBatch = true,
            ),
            onQueryChange = {},
            onRandomize = {},
            onRetrySearch = {},
            onOpenAirport = {},
        )
    }
}

@LightDarkPreview
@Composable
private fun AirportsContentEmptyPreview() {
    FlightPlannerTheme(dynamicColor = false) {
        AirportsContent(
            state = AirportsUiState(query = "ZZZZ"),
            onQueryChange = {},
            onRandomize = {},
            onRetrySearch = {},
            onOpenAirport = {},
        )
    }
}

@LightDarkPreview
@Composable
private fun AirportsContentDegradedSearchPreview() {
    FlightPlannerTheme(dynamicColor = false) {
        AirportsContent(
            state = AirportsUiState(
                query = "AMS",
                airports = listOf(PlanPreviewData.schiphol),
                searchScope = SearchScope.NamesUnavailable,
            ),
            onQueryChange = {},
            onRandomize = {},
            onRetrySearch = {},
            onOpenAirport = {},
        )
    }
}
