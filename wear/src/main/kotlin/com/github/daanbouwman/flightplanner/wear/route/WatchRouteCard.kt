package com.github.daanbouwman.flightplanner.wear.route

import com.github.daanbouwman.flightplanner.handoff.WatchRoute
import com.github.daanbouwman.flightplanner.model.AircraftSpec
import com.github.daanbouwman.flightplanner.routing.AirportIndex
import com.github.daanbouwman.flightplanner.routing.GeneratedRoute
import com.github.daanbouwman.flightplanner.routing.GeoArc
import com.github.daanbouwman.flightplanner.routing.GreatCircle
import com.github.daanbouwman.flightplanner.routing.RouteArc

/**
 * One route, as the watch face shows it.
 *
 * A [GeneratedRoute] names its airports by slot in the index that produced it
 * and carries no text at all; this is the same route with its codes resolved,
 * its figures worded and its great circle sampled, so the composable draws
 * rather than computes. That split is what lets a whole batch be prepared on
 * the generator's dispatcher while the face stays a pure function of state.
 *
 * Distances are nautical miles, without a unit setting. The phone reads one
 * from DataStore and can show kilometres; the watch has no settings screen to
 * offer the choice and no way to read the phone's yet, and inventing a second
 * place to set it would mean two answers to one question. NM is the aviation
 * default and the one the route generator works in.
 */
data class WatchRouteCard(
    val departureIcao: String,
    val destinationIcao: String,
    val distanceNm: Int,
    /** `"200 NM"` — already worded, because Compose should not format. */
    val distanceText: String,
    /** `"1:49"`, from [GreatCircle.flightTime]. */
    val eteText: String,
    val aircraftName: String,
    val aircraftTypeCode: String,
    /** The great circle between the ends, at the card's sampling, for the map. */
    val arc: GeoArc,
) {

    /**
     * The route's identity as one string, in the same shape as the phone's
     * `Destination.RouteDetail.key()`: departure, destination and airframe.
     * Used as the pager's key, so that appending a batch does not re-key the
     * page under the wearer's thumb.
     */
    fun key(): String = "$departureIcao>$destinationIcao@$aircraftTypeCode"

    /** What crosses to the phone when the wearer taps. */
    fun asHandoff(): WatchRoute = WatchRoute(
        departureIcao = departureIcao,
        destinationIcao = destinationIcao,
        distanceNm = distanceNm,
        aircraftTypeCode = aircraftTypeCode,
        aircraftName = aircraftName,
    )
}

/**
 * Resolves a generated route against the index it came from.
 *
 * Kept a free function over plain arguments rather than a method on either
 * type, because it is the one place `:core:routing`'s slot-and-number world
 * meets the watch's text-and-pixels one, and it is worth being able to call it
 * from a test with a two-airport index.
 */
fun GeneratedRoute.toCard(index: AirportIndex): WatchRouteCard {
    val departure = index.icaoOf(departureSlot)
    val destination = index.icaoOf(destinationSlot)
    return WatchRouteCard(
        departureIcao = departure,
        destinationIcao = destination,
        distanceNm = distanceNm,
        distanceText = "$distanceNm NM",
        eteText = GreatCircle.flightTime(distanceNm.toDouble(), aircraft.cruiseSpeedKt).format(),
        aircraftName = aircraft.displayName,
        aircraftTypeCode = aircraft.icaoCode.ifBlank { UNTYPED_AIRFRAME },
        arc = RouteArc.sampleGeographic(
            index.latDegOf(departureSlot),
            index.lonDegOf(departureSlot),
            index.latDegOf(destinationSlot),
            index.lonDegOf(destinationSlot),
            samples = RouteArc.CARD_SAMPLES,
        ),
    )
}

/**
 * Stands in for an airframe whose CSV row has no `icao_code`.
 *
 * The column is optional in the fleet format and `FleetCsv` leaves it empty
 * rather than guessing. An empty type code would make
 * [com.github.daanbouwman.flightplanner.handoff.WatchRouteLink] reject the link
 * — it requires the field — so the blank is named here, where the reason is
 * visible, instead of failing silently at the tap. The phone falls back to
 * matching on [AircraftSpec.displayName], which such a row still has.
 */
private const val UNTYPED_AIRFRAME = "ZZZZ"
