package com.github.daanbouwman.flightplanner.ui.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.daanbouwman.flightplanner.ui.distanceText
import com.github.daanbouwman.flightplanner.ui.lengthText
import com.github.daanbouwman.flightplanner.R
import com.github.daanbouwman.flightplanner.core.designsystem.components.CompactWidthPreview
import com.github.daanbouwman.flightplanner.core.designsystem.components.FilterField
import com.github.daanbouwman.flightplanner.core.designsystem.components.LightDarkPreview
import com.github.daanbouwman.flightplanner.core.designsystem.theme.FlightPlannerTheme
import com.github.daanbouwman.flightplanner.model.AircraftSpec
import com.github.daanbouwman.flightplanner.model.Airport

/**
 * The two constraints that replace the desktop application's 250-pixel sidebar:
 * where a route may start, and what flies it.
 *
 * ### Each field states the envelope it imposes
 *
 * A filter that says "Boeing 737-600" answers *what* is set. The fact worth the
 * space is what that **does** to the list: 3,010 NM of range and 6,900 ft of runway
 * is the box every route in front of the user was generated inside, and it is the
 * reason a list of short domestic hops looks the way it does. The same for a locked
 * departure — the code is what you scan for, the airport's name is what confirms you
 * picked the right one of the four airports called Springfield.
 *
 * ### Which half of an airframe's name is shown
 *
 * The variant, not the display name. "Boeing 737-600" does not fit half of a 360 dp
 * window and truncates to "Boeing 737-6…", which spends the whole field on the half
 * of the name that carries the least — every 737 shares "Boeing 737". "737-600"
 * fits, identifies the airframe, and leaves the line beneath free for the envelope.
 * The manufacturer is on the route card and in the picker.
 *
 * The two fields split the width evenly and fill the row, so their outer edges line
 * up with the scope chips above and with the card column below. Sized to their
 * content they left a ragged edge, and the block read as three unrelated things
 * rather than as one set of controls.
 */
@Composable
fun PlanFilterFields(
    lockedDeparture: Airport?,
    selectedAircraft: AircraftSpec?,
    onPickDeparture: () -> Unit,
    onPickAircraft: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val unset = stringResource(R.string.plan_filter_unset)

    Row(
        // Intrinsic height plus `fillMaxHeight` on each field, so the pair stays
        // level when only one of them carries a detail line. Without it a set
        // departure beside an unset aircraft draws one tall box and one short one.
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterField(
            label = stringResource(R.string.plan_filter_departure_label),
            value = lockedDeparture?.icao ?: unset,
            detail = lockedDeparture?.name,
            selected = lockedDeparture != null,
            onClick = onPickDeparture,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        )
        FilterField(
            label = stringResource(R.string.plan_filter_aircraft_label),
            value = selectedAircraft?.variant ?: unset,
            detail = selectedAircraft?.let {
                stringResource(R.string.plan_filter_envelope, distanceText(it.rangeNm), lengthText(it.requiredRunwayFt))
            },
            selected = selectedAircraft != null,
            onClick = onPickAircraft,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        )
    }
}

@LightDarkPreview
@CompactWidthPreview
@Composable
private fun PlanFilterFieldsPreview() {
    FlightPlannerTheme(dynamicColor = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Unset, then set: the set state carries the long strings — a full
                // airport name and a two-figure envelope — so this is the pairing
                // that shows whether the fields hold at 360 dp and at font scale 2.0.
                PlanFilterFields(
                    lockedDeparture = null,
                    selectedAircraft = null,
                    onPickDeparture = {},
                    onPickAircraft = {},
                )
                PlanFilterFields(
                    lockedDeparture = PlanPreviewData.schiphol,
                    selectedAircraft = PlanPreviewData.boeing,
                    onPickDeparture = {},
                    onPickAircraft = {},
                )
            }
        }
    }
}
