package com.github.daanbouwman.flightplanner.ui.goldens

import com.github.daanbouwman.flightplanner.model.CloudCover
import com.github.daanbouwman.flightplanner.model.CloudLayer
import com.github.daanbouwman.flightplanner.model.FlightRules
import com.github.daanbouwman.flightplanner.model.Metar
import com.github.daanbouwman.flightplanner.model.SkyCover

/**
 * Two reports for the goldens, neither carrying an observation time — the Sky
 * Profile is drawn from the layers and the visibility, and nothing counts the
 * minutes since. A report without layers draws the *unknown* hatch, which is
 * correct and not what a golden of the profile is for.
 */

/** Schiphol on a good day: a few clouds at 4,000 ft, ten miles or more, a westerly. */
internal val schipholVfrMetar = Metar(
    station = "EHAM",
    raw = "EHAM 121325Z 27012KT 9999 FEW040 18/09 Q1015",
    flightRules = FlightRules.VFR,
    skyCover = SkyCover.Layers(listOf(CloudLayer(cover = CloudCover.FEW, baseFt = 4_000))),
    visibilityStatuteMiles = 10.0,
    visibilityIsOrGreater = true,
    windDirectionDeg = 270,
    windSpeedKt = 12,
    temperatureC = 18.0,
    dewpointC = 9.0,
)

/** Heathrow under an 800 ft broken deck in 4,000 m: IFR, the profile's low band. */
internal val heathrowIfrMetar = Metar(
    station = "EGLL",
    raw = "EGLL 121320Z 24008KT 4000 BKN008 12/11 Q1002",
    flightRules = FlightRules.IFR,
    skyCover = SkyCover.Layers(listOf(CloudLayer(cover = CloudCover.BROKEN, baseFt = 800))),
    visibilityStatuteMiles = 4000.0 / 1609.344,
    windDirectionDeg = 240,
    windSpeedKt = 8,
    temperatureC = 12.0,
    dewpointC = 11.0,
)
