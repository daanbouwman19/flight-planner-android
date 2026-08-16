package com.github.daanbouwman.flightplanner.routing

import com.github.daanbouwman.flightplanner.model.AircraftSpec
import com.github.daanbouwman.flightplanner.model.AirportSizeClass
import kotlin.random.Random

/** A minimal airport description for building test fixtures. */
data class TestAirport(
    val icao: String,
    val lat: Double,
    val lon: Double,
    val longestRunwayFt: Int,
    val hasIcao: Boolean = true,
    val hardSurface: Boolean = true,
    val name: String = "$icao Airport",
)

fun buildIndex(airports: List<TestAirport>): AirportIndex {
    val builder = AirportIndexBuilder(airports.size)
    airports.forEachIndexed { i, a ->
        builder.add(
            id = i + 1,
            icao = a.icao,
            latitude = a.lat,
            longitude = a.lon,
            longestRunway = a.longestRunwayFt,
            packedFlags = AirportIndex.packFlags(
                hasIcao = a.hasIcao,
                hardSurface = a.hardSurface,
                lighting = true,
                sizeClass = AirportSizeClass.MEDIUM,
            ),
        )
    }
    return builder.build()
}

/**
 * A pseudo-random world.
 *
 * Latitudes are generated uniformly in degrees rather than by area, which
 * deliberately over-samples the poles — that is where the longitude-window
 * arithmetic is most likely to be wrong.
 */
fun randomWorld(count: Int, seed: Int = 1): AirportIndex {
    val random = Random(seed)
    val airports = (0 until count).map { i ->
        TestAirport(
            // Decoding the row number yields a distinct, valid four-character
            // code, which is what the index requires.
            icao = IcaoCode.decode(i),
            lat = random.nextDouble(-89.5, 89.5),
            lon = random.nextDouble(-180.0, 180.0),
            longestRunwayFt = random.nextInt(1_000, 15_000),
            hasIcao = i % 3 != 0,
            hardSurface = i % 4 != 0,
        )
    }
    return buildIndex(airports)
}

fun aircraft(
    id: Int = 1,
    rangeNm: Int,
    takeoffMeters: Int? = null,
    flown: Boolean = false,
    cruiseKt: Int = 300,
) = AircraftSpec(
    id = id,
    manufacturer = "Test",
    variant = "A$id",
    icaoCode = "TST$id",
    flown = flown,
    rangeNm = rangeNm,
    category = "Test",
    cruiseSpeedKt = cruiseKt,
    dateFlown = null,
    takeoffDistanceMeters = takeoffMeters,
)

/** Double-precision haversine, used as an independent reference for the ported Float version. */
fun referenceDistanceNm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r1 = Math.toRadians(lat1)
    val r2 = Math.toRadians(lat2)
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
        Math.cos(r1) * Math.cos(r2) * Math.sin(dLon / 2) * Math.sin(dLon / 2)
    return 2.0 * 3440.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
}
