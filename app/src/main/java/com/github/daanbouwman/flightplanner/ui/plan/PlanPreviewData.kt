package com.github.daanbouwman.flightplanner.ui.plan

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.github.daanbouwman.flightplanner.model.AircraftSpec
import com.github.daanbouwman.flightplanner.model.Airport
import com.github.daanbouwman.flightplanner.model.AirportSizeClass
import com.github.daanbouwman.flightplanner.routing.GreatCircle
import com.github.daanbouwman.flightplanner.routing.RouteArc
import com.github.daanbouwman.flightplanner.routing.WorldOutline
import com.github.daanbouwman.flightplanner.routing.WorldOutlineCodec

/**
 * Sample data for the `@Preview`s in this package.
 *
 * Real airports, real coordinates and the real geometry functions, rather than
 * invented numbers. It costs nothing — the arc is computed by the same
 * `RouteArc` the app uses — and it means a preview shows what the screen will
 * actually look like: Amsterdam to Tokyo really does bow that far north, and a
 * preview drawn from made-up points would quietly hide it if the projection
 * broke.
 *
 * This lives in `main` because Compose previews are rendered from the main
 * source set. It is `internal`, referenced only by preview functions, and so is
 * removed by R8 along with them.
 */
internal object PlanPreviewData {

    val schiphol = airport(1, "EHAM", "Amsterdam Airport Schiphol", 52.3086, 4.7639, 12467)
    val haneda = airport(2, "RJTT", "Tokyo Haneda International", 35.5533, 139.7811, 11811)
    val kennedy = airport(3, "KJFK", "John F Kennedy International", 40.6398, -73.7789, 14511)
    val innsbruck = airport(4, "LOWI", "Innsbruck", 47.2602, 11.3439, 6562)

    val boeing = aircraft(1, "Boeing", "777-300ER", "B77W", "Wide-body", range = 7370, cruise = 490)
    val cessna = aircraft(2, "Textron Aviation", "Cessna 172", "C172", "Light", range = 640, cruise = 122)

    /** A long haul that bows visibly north — the case the sparkline exists for. */
    val longHaul = row(id = 1, aircraft = boeing, from = schiphol, to = haneda)

    /** A shorter westbound leg, for a second shape in the same preview. */
    val transatlantic = row(id = 2, aircraft = boeing, from = schiphol, to = kennedy)

    /**
     * A route whose destination is far too short for the airframe.
     *
     * Only reachable behind a locked departure in the real app, which makes it
     * exactly the state that never shows up while clicking around — so it gets
     * its own preview.
     */
    val runwayTooShort = row(id = 3, aircraft = boeing, from = schiphol, to = innsbruck)

    val batch: List<RouteRow> = listOf(longHaul, transatlantic, runwayTooShort)

    private fun row(id: Long, aircraft: AircraftSpec, from: Airport, to: Airport): RouteRow {
        val distance = GreatCircle.distanceNm(from.latitude, from.longitude, to.latitude, to.longitude)
        return RouteRow(
            id = id,
            aircraft = aircraft,
            departure = from,
            destination = to,
            distanceNm = distance,
            flightTime = GreatCircle.flightTime(distance.toDouble(), aircraft.cruiseSpeedKt),
            departureRunwayFt = from.longestRunwayFt,
            destinationRunwayFt = to.longestRunwayFt,
            arc = RouteArc.sampleGeographic(
                depLat = from.latitude,
                depLon = from.longitude,
                destLat = to.latitude,
                destLon = to.longitude,
                samples = RouteArc.CARD_SAMPLES,
            ),
        )
    }

    private fun airport(
        id: Int,
        icao: String,
        name: String,
        latitude: Double,
        longitude: Double,
        runwayFt: Int,
    ) = Airport(
        id = id,
        icao = icao,
        name = name,
        latitude = latitude,
        longitude = longitude,
        elevationFt = 0,
        country = "NL",
        municipality = name,
        sizeClass = AirportSizeClass.LARGE,
        longestRunwayFt = runwayFt,
        runwayCount = 4,
        hasHardSurface = true,
        hasIcaoCode = true,
    )

    private fun aircraft(
        id: Int,
        manufacturer: String,
        variant: String,
        code: String,
        category: String,
        range: Int,
        cruise: Int,
    ) = AircraftSpec(
        id = id,
        manufacturer = manufacturer,
        variant = variant,
        icaoCode = code,
        flown = false,
        rangeNm = range,
        category = category,
        cruiseSpeedKt = cruise,
        dateFlown = null,
        // 3,000 m, so a 6,562 ft runway reads as too short and the warning state
        // has something to show.
        takeoffDistanceMeters = 3_000,
    )
}

/**
 * The real coastline, for previews.
 *
 * A preview that drew invented land would be judging the design against a
 * coastline the app never shows — and the whole question the map answers is
 * whether a real coast is *recognisable* at 8 % contrast. The asset is in the
 * module's own assets, so the preview renderer can open it exactly as the app
 * does; if it cannot, the cards preview over an empty ocean rather than failing
 * to render.
 */
@Composable
internal fun rememberPreviewWorldOutline(): WorldOutline {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            WorldOutlineCodec.decode(context.assets.open("maps/land.outline").use { it.readBytes() })
        }.getOrDefault(WorldOutline.Empty)
    }
}
