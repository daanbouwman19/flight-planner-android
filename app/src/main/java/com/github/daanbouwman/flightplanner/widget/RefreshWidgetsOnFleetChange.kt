package com.github.daanbouwman.flightplanner.widget

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.github.daanbouwman.flightplanner.ui.runCatchingCancellable
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop

/**
 * Re-renders both home-screen widgets when the fleet changes under them.
 *
 * Each widget is a function of the date and the fleet, and the alarm covers
 * the date. The fleet changes only from inside the app — an airframe marked
 * flown, added, removed, the defaults restored — and without this the card
 * would state yesterday's fleet until midnight: "NOT FLOWN" on an airframe
 * flown an hour ago, or a challenge for an airframe that no longer exists.
 *
 * Composed by `MainActivity` beside [PublishWidgetPreview], for the same
 * reason: after the app's own content, so nothing here precedes the first
 * frame. The first emission is the fleet as it stands, which the widgets
 * already show, so it is dropped; every later one is a change and goes through
 * [FleetRevision], which is what makes a session that is still open reload. A
 * home screen without either widget costs one dropped emission and a lookup
 * that finds no instances, nothing more.
 */
@Composable
fun RefreshWidgetsOnFleetChange() {
    val context = LocalContext.current.applicationContext
    LaunchedEffect(Unit) {
        WidgetEntryPoint.from(context).fleetRepository().observeFleet()
            .distinctUntilChanged()
            .drop(1)
            .collect {
                // Per emission, so one refresh that fails — a state write
                // under low storage, an id removed mid-bump — is one change
                // missed, not every change after it.
                runCatchingCancellable {
                    FleetRevision.bump(context, AircraftWidget())
                    FleetRevision.bump(context, ChallengeWidget())
                }
            }
    }
}
