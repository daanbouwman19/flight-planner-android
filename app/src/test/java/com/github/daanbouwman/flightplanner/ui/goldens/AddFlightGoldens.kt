package com.github.daanbouwman.flightplanner.ui.goldens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.daanbouwman.flightplanner.ui.logbook.AddFlightFields
import com.github.daanbouwman.flightplanner.ui.plan.PlanPreviewData
import java.time.LocalDate

/**
 * The add-flight sheet's fields, unset above set — the pairing the preview chose
 * because the set state carries the longest strings. The sheet is a separate
 * window and the date picker a dialog; neither is captured.
 */
class AddFlightGoldens : GoldenSuite(subject = "add-flight", primaryState = "fields") {

    @Composable
    override fun Primary() {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                aircraft = PlanPreviewData.boeing,
                date = LocalDate.of(2026, 8, 22),
                departure = PlanPreviewData.schiphol,
                destination = PlanPreviewData.kennedy,
                onPickAircraft = {},
                onPickDate = {},
                onPickDeparture = {},
                onPickDestination = {},
            )
        }
    }
}
