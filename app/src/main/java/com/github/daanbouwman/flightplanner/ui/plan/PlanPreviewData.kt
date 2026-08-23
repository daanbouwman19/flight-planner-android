package com.github.daanbouwman.flightplanner.ui.plan

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.github.daanbouwman.flightplanner.model.AircraftSpec
import com.github.daanbouwman.flightplanner.model.Airport
import com.github.daanbouwman.flightplanner.model.AirportSizeClass
import com.github.daanbouwman.flightplanner.model.Runway
import com.github.daanbouwman.flightplanner.model.SurfaceKind
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

    /**
     * Schiphol's real runway layout — six physical runways, twelve ends, and
     * one with no published heading — for the Airport detail (E2) previews.
     * Real geometry, like everything else here, and chosen deliberately:
     * Schiphol is exactly the shape [RunwayDiagram]'s lane layout exists
     * for — three runways (18L/36R, 18C/36C, 18R/36L) run near-parallel a
     * few degrees apart, which used to draw as a starburst through one
     * point that isn't real before [layoutRunways] laned them — alongside
     * three more (09/27, 04/22, 06/24) spread widely enough to plausibly
     * cross.
     */
    val schipholRunways: List<Runway> = listOf(
        // Buitenveldertbaan
        runway(1, "09", 87.0, 11318, 147, lit = true),
        runway(2, "27", 267.0, 11318, 147, lit = true),
        // Oostbaan
        runway(3, "04", 41.0, 10827, 147, lit = true),
        runway(4, "22", 221.0, 10827, 147, lit = true),
        // Kaagbaan
        runway(5, "06", 57.0, 11483, 147, lit = true),
        runway(6, "24", 237.0, 11483, 147, lit = true),
        // Aalsmeerbaan
        runway(7, "18L", 183.0, 11155, 147, lit = true),
        runway(8, "36R", 3.0, 11155, 147, lit = true),
        // Zwanenburgbaan
        runway(9, "18C", 183.0, 10827, 147, lit = true),
        runway(10, "36C", 3.0, 10827, 147, lit = true),
        // Polderbaan, the longest — and "36L" is left without a heading on
        // purpose, so this fixture also covers the "excluded from the
        // diagram" note.
        runway(11, "18R", 183.0, 12467, 197, lit = true),
        runway(12, "36L", null, 12467, 197, lit = false),
    )

    private fun runway(
        id: Int,
        ident: String,
        heading: Double?,
        lengthFt: Int,
        widthFt: Int,
        lit: Boolean,
        surfaceKind: SurfaceKind = SurfaceKind.HARD,
    ) = Runway(
        id = id,
        airportId = schiphol.id,
        ident = ident,
        trueHeadingDeg = heading,
        lengthFt = lengthFt,
        widthFt = widthFt,
        surface = if (surfaceKind == SurfaceKind.HARD) "ASPH" else "GRASS",
        surfaceKind = surfaceKind,
        latitude = null,
        longitude = null,
        elevationFt = schiphol.elevationFt,
        lighted = lit,
    )

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
