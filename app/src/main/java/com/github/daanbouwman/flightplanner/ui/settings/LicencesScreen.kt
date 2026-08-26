package com.github.daanbouwman.flightplanner.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.daanbouwman.flightplanner.R
import com.github.daanbouwman.flightplanner.core.designsystem.components.DevicePreviews
import com.github.daanbouwman.flightplanner.core.designsystem.components.LightDarkPreview
import com.github.daanbouwman.flightplanner.core.designsystem.theme.FlightPlannerTheme

/**
 * Open-source licences and data attributions.
 *
 * Static text, hand-written rather than generated: the dependency graph is
 * almost entirely Apache License 2.0 (see `gradle/libs.versions.toml`), so a
 * generated per-artifact list would say the same thing dozens of times over.
 * OurAirports is public domain but credited anyway (`PLAN.md`'s own words for
 * it); Natural Earth is public domain and asks for no attribution at all —
 * both are listed regardless, because a licences screen that omits its own
 * data is missing the thing most likely to be asked about.
 *
 * No Esri/tile-provider entry yet: that attribution belongs to Phase G's
 * globe, which does not exist yet — an entry for a feature nobody can reach
 * would be a promise, not a licence.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicencesScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.destination_licences)) },
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
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
    ) { contentPadding ->
        LicencesContent(modifier = Modifier.padding(contentPadding))
    }
}

@Composable
private fun LicencesContent(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionLabel(stringResource(R.string.licences_open_source_title))
        Text(
            text = stringResource(R.string.licences_open_source_body),
            style = MaterialTheme.typography.bodyMedium,
        )

        SectionLabel(stringResource(R.string.licences_data_title), topPadding = 16.dp)
        Text(
            text = stringResource(R.string.licences_data_ourairports),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = stringResource(R.string.licences_data_naturalearth),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun SectionLabel(text: String, topPadding: Dp = 8.dp) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = topPadding, bottom = 8.dp),
    )
}

@LightDarkPreview
@DevicePreviews
@Composable
private fun LicencesScreenPreview() {
    FlightPlannerTheme(dynamicColor = false) {
        LicencesContent()
    }
}
