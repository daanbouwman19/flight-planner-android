package com.github.daanbouwman.flightplanner.ui.logbook

import com.github.daanbouwman.flightplanner.model.AircraftSpec
import com.github.daanbouwman.flightplanner.model.Airport
import com.github.daanbouwman.flightplanner.routing.GreatCircle

/**
 * What [AddFlightSheet] needs to gate its submit button and explain why it is
 * gated — ported field-for-field from the desktop app's `AddHistoryPopup`
 * (`add_history_popup.rs`), which blocks on exactly these conditions and
 * shows the last two inline: aircraft, departure and destination are all
 * required; departure and destination must differ; and the great-circle
 * distance between them must not exceed the aircraft's range. The desktop
 * always stamps today's date and has no date field at all — the date picker
 * here is a deliberate Android addition (see [AddFlightSheet]'s own KDoc),
 * and a chosen date never participates in validation, matching the
 * reference, where it cannot.
 */
data class AddFlightValidation(
    val aircraft: AircraftSpec?,
    val departure: Airport?,
    val destination: Airport?,
) {
    /** Null until both airports are chosen — the value the "DIST" chip shows live. */
    val distanceNm: Int? = if (departure != null && destination != null) {
        GreatCircle.distanceNm(departure.latitude, departure.longitude, destination.latitude, destination.longitude)
    } else {
        null
    }

    val sameAirport: Boolean = departure != null && destination != null && departure.icao == destination.icao

    val overRange: Boolean = aircraft != null && distanceNm != null && distanceNm > aircraft.rangeNm

    val canSubmit: Boolean = aircraft != null && departure != null && destination != null &&
        !sameAirport && !overRange
}
