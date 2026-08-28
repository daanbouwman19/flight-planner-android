package com.github.daanbouwman.flightplanner.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.daanbouwman.flightplanner.R
import com.github.daanbouwman.flightplanner.core.designsystem.theme.FlightPlannerTheme
import com.github.daanbouwman.flightplanner.core.designsystem.theme.ThemeChoice
import com.github.daanbouwman.flightplanner.core.designsystem.components.DevicePreviews
import com.github.daanbouwman.flightplanner.core.designsystem.components.LightDarkPreview
import com.github.daanbouwman.flightplanner.settings.UnitSystem
import com.github.daanbouwman.flightplanner.settings.WeatherProvider
import com.github.daanbouwman.flightplanner.ui.asFigure
import com.github.daanbouwman.flightplanner.ui.detail.rememberRouteActionLauncher

/**
 * Appearance settings, and the on-device self-check.
 *
 * Two things here are not decoration.
 *
 * **It has a back arrow.** Settings is reached from an icon in every section's
 * app bar and keeps the navigation bar visible with nothing selected — which was
 * meant to stop the screen becoming a dead end and achieved the opposite: no
 * selected destination, no back affordance, and a bar in a state Material has no
 * meaning for. The arrow is the way out, exactly as on the route detail screen.
 *
 * **The theme choice is the whole point.** The app ships a hand-built palette —
 * avgas blue, runway-marking amber — and a Cockpit theme meant for flying at
 * night, and until this screen existed neither could be selected: the app always
 * asked for the defaults, so dynamic colour won on every device and the brand
 * work was invisible.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenSelfCheck: () -> Unit,
    onOpenLicences: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val datasetInfo by viewModel.datasetInfo.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.destination_settings)) },
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
        // The navigation suite still owns the bottom edge here, so this scaffold
        // takes only the sides.
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_appearance),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 8.dp),
            )

            Column(modifier = Modifier.selectableGroup()) {
                ThemeChoice.entries.forEach { choice ->
                    ThemeRow(
                        choice = choice,
                        selected = settings.themeChoice == choice,
                        onSelect = { viewModel.setThemeChoice(choice) },
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            SwitchRow(
                title = stringResource(R.string.settings_dynamic_colour),
                // Cockpit and Chart each resolve their own scheme, so the toggle
                // would do nothing while either is selected. Disabled and explained
                // rather than hidden: a control that vanishes is a control the user
                // goes looking for.
                detail = when (settings.themeChoice) {
                    ThemeChoice.COCKPIT -> stringResource(R.string.settings_dynamic_colour_cockpit)
                    ThemeChoice.CHART -> stringResource(R.string.settings_dynamic_colour_chart)
                    else -> stringResource(R.string.settings_dynamic_colour_detail)
                },
                checked = settings.dynamicColour,
                enabled = !settings.themeChoice.ignoresDynamicColour,
                onCheckedChange = viewModel::setDynamicColour,
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Text(
                text = stringResource(R.string.settings_units),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 8.dp),
            )

            Column(modifier = Modifier.selectableGroup()) {
                UnitSystem.entries.forEach { system ->
                    UnitRow(
                        system = system,
                        selected = settings.unitSystem == system,
                        onSelect = { viewModel.setUnitSystem(system) },
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Text(
                text = stringResource(R.string.settings_airports),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 8.dp),
            )

            SwitchRow(
                title = stringResource(R.string.settings_icao_only),
                detail = stringResource(R.string.settings_icao_only_detail),
                checked = settings.icaoOnly,
                enabled = true,
                onCheckedChange = viewModel::setIcaoOnly,
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Text(
                text = stringResource(R.string.settings_weather),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 8.dp),
            )

            Column(modifier = Modifier.selectableGroup()) {
                WeatherProviderRow(
                    label = stringResource(R.string.settings_weather_provider_noaa),
                    detail = stringResource(R.string.settings_weather_provider_noaa_detail),
                    selected = settings.weatherProvider == WeatherProvider.NOAA,
                    onSelect = { viewModel.setWeatherProvider(WeatherProvider.NOAA) },
                )
                WeatherProviderRow(
                    label = stringResource(R.string.settings_weather_provider_avwx),
                    detail = stringResource(R.string.settings_weather_provider_avwx_detail),
                    selected = settings.weatherProvider == WeatherProvider.AVWX,
                    onSelect = { viewModel.setWeatherProvider(WeatherProvider.AVWX) },
                )
            }

            AnimatedVisibility(visible = settings.weatherProvider == WeatherProvider.AVWX) {
                AvwxApiKeyField(
                    stored = settings.avwxApiKey.orEmpty(),
                    onCommit = viewModel::setAvwxApiKey,
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Text(
                text = stringResource(R.string.settings_about),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 8.dp),
            )

            datasetInfo?.let { info ->
                DatasetInfoBlock(info)
            }

            OutlinedButton(
                onClick = onOpenLicences,
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Text(stringResource(R.string.settings_licences_action))
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Text(
                text = stringResource(R.string.settings_empty_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedButton(
                onClick = onOpenSelfCheck,
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Text(stringResource(R.string.settings_self_check_action))
            }

        }
    }
}

@Composable
private fun UnitRow(system: UnitSystem, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(modifier = Modifier.weight(1f)) {
            Text(text = stringResource(system.labelRes), style = MaterialTheme.typography.bodyLarge)
            Text(
                text = stringResource(system.detailRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WeatherProviderRow(label: String, detail: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            Text(text = detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * The masked AVWX key field, matching the desktop reference's own affordances
 * (`settings_popup.rs`): a show/hide toggle, a clear action once there is
 * something to clear, and a link to get a key. The desktop's copy-with-
 * "Copied!"-feedback button is deliberately not ported — Android already
 * toasts on every clipboard write, the same reason the route-plan copy
 * action carries no in-app confirmation of its own (see `RouteActions.kt`).
 */
@Composable
private fun AvwxApiKeyField(stored: String, onCommit: (String) -> Unit, modifier: Modifier = Modifier) {
    var visible by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val launcher = rememberRouteActionLauncher(scope = scope, onNoHandler = {})

    // **The field owns its text; the store is written on the way out.**
    //
    // It used to be fully controlled off `settings`, which is a `StateFlow` fed by
    // DataStore — so every keystroke went out to disk and the field then redrew
    // from whatever emission had come back. It is the only high-frequency writer in
    // a screen of one-shot toggles, and typing a 40-character key at speed into a
    // control that redraws from an asynchronous round trip drops characters and
    // jumps the cursor. The remedy is not a faster write, it is not writing on a
    // keystroke at all: a key is entered once and read by nothing until a request
    // is made.
    //
    // Keyed on [stored], so a change from *outside* this field — another process
    // editing the store, or the clear button — reseats the draft. Typing does not
    // change `stored`, so it never clobbers an edit in progress.
    var draft by rememberSaveable(stored) { mutableStateOf(stored) }
    val latestDraft by rememberUpdatedState(draft)
    val latestStored by rememberUpdatedState(stored)
    val latestCommit by rememberUpdatedState(onCommit)

    // Focus loss is the ordinary path and covers tapping away or dismissing the
    // keyboard. It does *not* cover leaving the screen, where the field is disposed
    // with the focus never changing — and losing a pasted key because the user hit
    // back is the same defect wearing a different hat.
    DisposableEffect(Unit) {
        onDispose { if (latestDraft != latestStored) latestCommit(latestDraft) }
    }

    Column(
        modifier = modifier.fillMaxWidth().padding(top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            label = { Text(stringResource(R.string.settings_avwx_api_key_label)) },
            placeholder = { Text(stringResource(R.string.settings_avwx_api_key_placeholder)) },
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (draft != stored) onCommit(draft) }),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { if (!it.isFocused && latestDraft != latestStored) latestCommit(latestDraft) },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = { visible = !visible }) {
                Text(
                    stringResource(
                        if (visible) R.string.settings_avwx_api_key_hide else R.string.settings_avwx_api_key_show,
                    ),
                )
            }
            if (draft.isNotBlank()) {
                // Clears both, and immediately: this is an explicit action rather
                // than an edit in progress, so there is nothing to defer.
                TextButton(
                    onClick = {
                        draft = ""
                        onCommit("")
                    },
                ) {
                    Text(stringResource(R.string.settings_avwx_api_key_clear))
                }
            }
        }
        TextButton(onClick = { launcher.open(AvwxSignupUrl) }) {
            Text(stringResource(R.string.settings_avwx_get_key_action))
        }
    }
}

private const val AvwxSignupUrl = "https://account.avwx.rest/"

@Composable
private fun DatasetInfoBlock(info: DatasetInfo) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = stringResource(R.string.settings_dataset_snapshot, info.upstreamModified),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = stringResource(
                R.string.settings_dataset_counts,
                info.airportCount.asFigure(),
                info.runwayCount.asFigure(),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.settings_dataset_notice),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val UnitSystem.labelRes: Int
    get() = when (this) {
        UnitSystem.AVIATION -> R.string.settings_unit_aviation
        UnitSystem.METRIC -> R.string.settings_unit_metric
    }

private val UnitSystem.detailRes: Int
    get() = when (this) {
        UnitSystem.AVIATION -> R.string.settings_unit_aviation_detail
        UnitSystem.METRIC -> R.string.settings_unit_metric_detail
    }

@Composable
private fun ThemeRow(choice: ThemeChoice, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // The whole row is the target, and it carries the role — so TalkBack
            // announces one radio button per theme rather than a button beside an
            // unrelated pair of labels.
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(modifier = Modifier.weight(1f)) {
            Text(text = stringResource(choice.labelRes), style = MaterialTheme.typography.bodyLarge)
            Text(
                text = stringResource(choice.detailRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    detail: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

/** The label and the one-line explanation each theme choice carries. */
private val ThemeChoice.labelRes: Int
    get() = when (this) {
        ThemeChoice.SYSTEM -> R.string.settings_theme_system
        ThemeChoice.LIGHT -> R.string.settings_theme_light
        ThemeChoice.DARK -> R.string.settings_theme_dark
        ThemeChoice.COCKPIT -> R.string.settings_theme_cockpit
        ThemeChoice.CHART -> R.string.settings_theme_chart
    }

private val ThemeChoice.detailRes: Int
    get() = when (this) {
        ThemeChoice.SYSTEM -> R.string.settings_theme_system_detail
        ThemeChoice.LIGHT -> R.string.settings_theme_light_detail
        ThemeChoice.DARK -> R.string.settings_theme_dark_detail
        ThemeChoice.COCKPIT -> R.string.settings_theme_cockpit_detail
        ThemeChoice.CHART -> R.string.settings_theme_chart_detail
    }

/** Cockpit and Chart each resolve their own scheme; see [FlightPlannerTheme]. */
private val ThemeChoice.ignoresDynamicColour: Boolean
    get() = this == ThemeChoice.COCKPIT || this == ThemeChoice.CHART

@LightDarkPreview
@DevicePreviews
@Composable
private fun SettingsPreview() {
    FlightPlannerTheme(dynamicColor = false) {
        SettingsContentPreview()
    }
}

/**
 * The screen minus its ViewModel.
 *
 * `SettingsScreen` takes a Hilt ViewModel and cannot be rendered by the tooling,
 * so the preview draws the same rows against fixed state — which is also the only
 * way to see all four themes listed at once.
 */
@Composable
private fun SettingsContentPreview() {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ThemeChoice.entries.forEach { choice ->
            ThemeRow(choice = choice, selected = choice == ThemeChoice.COCKPIT, onSelect = {})
        }
        SwitchRow(
            title = stringResource(R.string.settings_dynamic_colour),
            detail = stringResource(R.string.settings_dynamic_colour_detail),
            checked = true,
            enabled = true,
            onCheckedChange = {},
        )
        UnitSystem.entries.forEach { system ->
            UnitRow(system = system, selected = system == UnitSystem.AVIATION, onSelect = {})
        }
        SwitchRow(
            title = stringResource(R.string.settings_icao_only),
            detail = stringResource(R.string.settings_icao_only_detail),
            checked = false,
            enabled = true,
            onCheckedChange = {},
        )
        DatasetInfoBlock(
            info = DatasetInfo(
                source = "https://ourairports.com",
                upstreamModified = "2026-06-01",
                airportCount = 24_073,
                runwayCount = 41_812,
            ),
        )
    }
}
