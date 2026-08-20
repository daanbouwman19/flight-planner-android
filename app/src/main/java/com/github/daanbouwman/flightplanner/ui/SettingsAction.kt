package com.github.daanbouwman.flightplanner.ui

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.github.daanbouwman.flightplanner.R

/**
 * The app bar's route to Settings.
 *
 * Visible only on phones, where Settings does not have a slot in the bottom
 * navigation bar. On tablets it stays permanently visible in the rail, so the
 * icon here would be redundant.
 */
@Composable
fun SettingsAction(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val adaptiveType = NavigationSuiteScaffoldDefaults.navigationSuiteType(currentWindowAdaptiveInfoV2())
    val isBar = adaptiveType == NavigationSuiteType.ShortNavigationBarCompact ||
        adaptiveType == NavigationSuiteType.ShortNavigationBarMedium

    if (!isBar) return

    IconButton(onClick = onClick, modifier = modifier) {
        Icon(
            painter = painterResource(R.drawable.ic_nav_settings),
            contentDescription = stringResource(R.string.destination_settings),
        )
    }
}
