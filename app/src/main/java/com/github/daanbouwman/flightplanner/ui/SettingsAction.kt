package com.github.daanbouwman.flightplanner.ui

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.github.daanbouwman.flightplanner.R

/**
 * The app bar's route to Settings.
 *
 * Settings used to be a destination in the navigation bar. It is here instead
 * because a bottom-bar slot crowds as items are added, and because it is not
 * the same *kind* of thing as the sections that remain (Plan, Fleet, Profile):
 * those are where the content lives, and this is a place you visit and leave.
 * Putting it in the app bar makes it reachable from every section rather than
 * from a slot of the bar shared with places the user moves between constantly.
 */
@Composable
fun SettingsAction(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            painter = painterResource(R.drawable.ic_nav_settings),
            contentDescription = stringResource(R.string.destination_settings),
        )
    }
}
