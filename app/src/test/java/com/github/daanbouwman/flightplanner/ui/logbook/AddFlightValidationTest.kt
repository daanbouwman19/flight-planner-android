package com.github.daanbouwman.flightplanner.ui.logbook

import com.github.daanbouwman.flightplanner.model.AircraftSpec
import com.github.daanbouwman.flightplanner.model.Airport
import com.github.daanbouwman.flightplanner.model.AirportSizeClass
import com.github.daanbouwman.flightplanner.routing.GreatCircle
import io.kotest.matchers.shouldBe
import kotlin.test.Test

private fun airport(id: Int, icao: String, lat: Double, lon: Double) = Airport(
    id = id,
    icao = icao,
    name = "$icao Airport",
    latitude = lat,
    longitude = lon,
    elevationFt = 0,
    country = "NL",
    municipality = null,
    sizeClass = AirportSizeClass.LARGE,
    longestRunwayFt = 10000,
    runwayCount = 1,
    hasHardSurface = true,
    hasIcaoCode = true,
)

private fun aircraft(rangeNm: Int) = AircraftSpec(
    id = 1,
    manufacturer = "Boeing",
    variant = "737-800",
    icaoCode = "B738",
    flown = false,
    rangeNm = rangeNm,
    category = "Jet",
    cruiseSpeedKt = 450,
    dateFlown = null,
    takeoffDistanceMeters = 2000,
)

private val schiphol = airport(1, "EHAM", 52.3086, 4.7639)
private val kennedy = airport(2, "KJFK", 40.6398, -73.7789)

class AddFlightValidationTest {

    @Test
    fun `nothing selected cannot be submitted`() {
        val validation = AddFlightValidation(aircraft = null, departure = null, destination = null)

        validation.canSubmit shouldBe false
        validation.distanceNm shouldBe null
    }

    @Test
    fun `an aircraft alone, with no airports, cannot be submitted`() {
        val validation = AddFlightValidation(aircraft = aircraft(rangeNm = 3000), departure = null, destination = null)

        validation.canSubmit shouldBe false
        validation.distanceNm shouldBe null
    }

    @Test
    fun `the same airport as both ends is rejected`() {
        val validation = AddFlightValidation(
            aircraft = aircraft(rangeNm = 3000),
            departure = schiphol,
            destination = schiphol.copy(id = 99),
        )

        validation.sameAirport shouldBe true
        validation.canSubmit shouldBe false
    }

    @Test
    fun `a distance beyond the aircraft's range is rejected`() {
        val distance = GreatCircle.distanceNm(
            schiphol.latitude,
            schiphol.longitude,
            kennedy.latitude,
            kennedy.longitude,
        )
        val validation = AddFlightValidation(
            aircraft = aircraft(rangeNm = distance - 1),
            departure = schiphol,
            destination = kennedy,
        )

        validation.overRange shouldBe true
        validation.canSubmit shouldBe false
    }

    @Test
    fun `a distance exactly equal to the range is not over range`() {
        val distance = GreatCircle.distanceNm(
            schiphol.latitude,
            schiphol.longitude,
            kennedy.latitude,
            kennedy.longitude,
        )
        val validation = AddFlightValidation(
            aircraft = aircraft(rangeNm = distance),
            departure = schiphol,
            destination = kennedy,
        )

        validation.overRange shouldBe false
        validation.canSubmit shouldBe true
    }

    @Test
    fun `a fully valid selection can be submitted and carries the live distance`() {
        val expected = GreatCircle.distanceNm(
            schiphol.latitude,
            schiphol.longitude,
            kennedy.latitude,
            kennedy.longitude,
        )
        val validation = AddFlightValidation(
            aircraft = aircraft(rangeNm = 4000),
            departure = schiphol,
            destination = kennedy,
        )

        validation.canSubmit shouldBe true
        validation.distanceNm shouldBe expected
    }
}
