package com.github.daanbouwman.flightplanner.wear.route

import com.github.daanbouwman.flightplanner.model.AircraftSpec
import com.github.daanbouwman.flightplanner.model.AirportSizeClass
import com.github.daanbouwman.flightplanner.routing.AirportIndex
import com.github.daanbouwman.flightplanner.routing.AirportIndexBuilder

/**
 * A handful of real airports, close enough together that the generator finds
 * routes for the fleet below.
 *
 * The same shape as `:core:routing`'s own `TestIndex`, written again here
 * because a module's test source set is not published and `:wear` cannot see
 * it. Five fields is enough: what these tests are about is the wording and the
 * batching around a route, not the route search itself, which `:core:routing`
 * already covers in depth.
 */
private data class Field(val id: Int, val icao: String, val lat: Double, val lon: Double, val runwayFt: Int)

private val fields = listOf(
    Field(1, "EHAM", 52.31, 4.76, 12_467),
    Field(2, "EGLL", 51.47, -0.45, 12_799),
    Field(3, "EDDF", 50.03, 8.57, 13_123),
    Field(4, "LFPG", 49.01, 2.55, 13_829),
    Field(5, "EKCH", 55.62, 12.66, 11_811),
)

internal val testIndex: AirportIndex = AirportIndexBuilder(fields.size).apply {
    fields.forEach { field ->
        add(
            id = field.id,
            icao = field.icao,
            latitude = field.lat,
            longitude = field.lon,
            longestRunway = field.runwayFt,
            packedFlags = AirportIndex.packFlags(
                hasIcao = true,
                hardSurface = true,
                lighting = true,
                sizeClass = AirportSizeClass.LARGE,
            ),
        )
    }
}.build()

internal fun testAircraft(
    id: Int = 1,
    manufacturer: String = "Cessna",
    variant: String = "172S Skyhawk",
    typeCode: String = "C172",
    cruiseKt: Int = 110,
) = AircraftSpec(
    id = id,
    manufacturer = manufacturer,
    variant = variant,
    icaoCode = typeCode,
    flown = false,
    rangeNm = 4_000,
    category = "Test",
    cruiseSpeedKt = cruiseKt,
    dateFlown = null,
    takeoffDistanceMeters = 500,
)
