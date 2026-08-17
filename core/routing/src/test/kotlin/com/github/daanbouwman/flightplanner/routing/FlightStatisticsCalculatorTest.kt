package com.github.daanbouwman.flightplanner.routing

import com.github.daanbouwman.flightplanner.model.AircraftSpec
import com.github.daanbouwman.flightplanner.model.FlightRecord
import com.github.daanbouwman.flightplanner.model.FlightStatistics
import io.kotest.matchers.shouldBe
import kotlin.test.Test

private fun flight(
    id: Long,
    departure: String,
    arrival: String,
    aircraftId: Int = 1,
    distanceNm: Int? = 100,
    date: String = "2024-01-01",
) = FlightRecord(
    id = id,
    departureIcao = departure,
    arrivalIcao = arrival,
    aircraftId = aircraftId,
    date = date,
    distanceNm = distanceNm,
)

private fun spec(id: Int, manufacturer: String, variant: String) = AircraftSpec(
    id = id,
    manufacturer = manufacturer,
    variant = variant,
    icaoCode = "B738",
    flown = true,
    rangeNm = 3_000,
    category = "Jet",
    cruiseSpeedKt = 450,
    dateFlown = null,
    takeoffDistanceMeters = 2_000,
)

private val fleet = listOf(
    spec(3, "Airbus", "A320"),
    spec(7, "Boeing", "737-800"),
)

private fun statsOf(vararg records: FlightRecord): FlightStatistics =
    FlightStatisticsCalculator.calculate(records.toList(), fleet)

class FlightStatisticsCalculatorBasicsTest {

    @Test
    fun `an empty log produces zeros and nulls, not a division by zero`() {
        FlightStatisticsCalculator.calculate(emptyList(), fleet) shouldBe FlightStatistics()
    }

    @Test
    fun `totals and average are computed over every record`() {
        val stats = statsOf(
            flight(1, "EHAM", "EGLL", distanceNm = 100),
            flight(2, "EGLL", "LFPG", distanceNm = 200),
            flight(3, "LFPG", "EDDF", distanceNm = 300),
        )

        stats.totalFlights shouldBe 3
        stats.totalDistanceNm shouldBe 600
        stats.averageFlightDistanceNm shouldBe 200.0
    }

    @Test
    fun `a single flight is both the longest and the shortest`() {
        val stats = statsOf(flight(1, "EHAM", "EGLL", distanceNm = 250))

        stats.longestFlight shouldBe "EHAM to EGLL"
        stats.shortestFlight shouldBe "EHAM to EGLL"
    }

    @Test
    fun `a missing distance counts as zero rather than being skipped`() {
        val stats = statsOf(
            flight(1, "EHAM", "EGLL", distanceNm = 400),
            flight(2, "LFPG", "EDDF", distanceNm = null),
        )

        // Two flights, 400 NM between them: the undistanced leg still counts as a flight.
        stats.totalFlights shouldBe 2
        stats.totalDistanceNm shouldBe 400
        stats.averageFlightDistanceNm shouldBe 200.0
        stats.shortestFlight shouldBe "LFPG to EDDF"
    }

    @Test
    fun `an aircraft id that is no longer in the fleet leaves the name null`() {
        val stats = FlightStatisticsCalculator.calculate(
            listOf(flight(1, "EHAM", "EGLL", aircraftId = 99)),
            fleet,
        )

        stats.mostFlownAircraft shouldBe null
        // Everything else is still computed; a missing name is not a failure.
        stats.totalFlights shouldBe 1
    }

    @Test
    fun `the lookup overload and the fleet overload agree`() {
        val records = listOf(flight(1, "EHAM", "EGLL", aircraftId = 7))
        val byId = fleet.associateBy { it.id }

        FlightStatisticsCalculator.calculate(records) { byId[it] } shouldBe
            FlightStatisticsCalculator.calculate(records, fleet)
    }

    @Test
    fun `an airport is counted once per leg it appears on`() {
        // EHAM departs twice and arrives twice; EGLL and LFPG appear once each.
        val stats = statsOf(
            flight(1, "EHAM", "EGLL"),
            flight(2, "EGLL", "EHAM"),
            flight(3, "EHAM", "LFPG"),
            flight(4, "LFPG", "EHAM"),
        )

        stats.mostVisitedAirport shouldBe "EHAM"
    }
}

/**
 * The tie-breaks, each asserted in both input orders.
 *
 * Order-independence is the point: the desktop accumulates into a `HashMap`, so
 * any behaviour that depended on encounter order would be unreproducible. These
 * pin the deterministic rule that replaces it.
 */
class FlightStatisticsCalculatorTieBreakTest {

    @Test
    fun `most visited airport ties go to the alphabetically first icao`() {
        // EHAM and KJFK are each visited twice; EHAM sorts first.
        statsOf(
            flight(1, "EHAM", "KJFK"),
            flight(2, "KJFK", "EHAM"),
        ).mostVisitedAirport shouldBe "EHAM"

        statsOf(
            flight(1, "KJFK", "EHAM"),
            flight(2, "EHAM", "KJFK"),
        ).mostVisitedAirport shouldBe "EHAM"
    }

    @Test
    fun `a higher visit count beats the alphabet`() {
        // Leg 3 is a circuit, so KJFK reaches four visits against EHAM's two:
        // the count decides and the alphabet does not get a say.
        statsOf(
            flight(1, "EHAM", "KJFK"),
            flight(2, "KJFK", "EHAM"),
            flight(3, "KJFK", "KJFK"),
        ).mostVisitedAirport shouldBe "KJFK"
    }

    @Test
    fun `favourite departure ties go to the alphabetically first icao`() {
        // One departure each from EHAM and KJFK; EHAM sorts first.
        statsOf(
            flight(1, "KJFK", "LFPG"),
            flight(2, "EHAM", "LFPG"),
        ).favoriteDepartureAirport shouldBe "EHAM"

        statsOf(
            flight(1, "EHAM", "LFPG"),
            flight(2, "KJFK", "LFPG"),
        ).favoriteDepartureAirport shouldBe "EHAM"
    }

    @Test
    fun `favourite arrival ties go to the alphabetically first icao`() {
        // One arrival each at EGLL and KJFK; EGLL sorts first.
        statsOf(
            flight(1, "LFPG", "KJFK"),
            flight(2, "LFPG", "EGLL"),
        ).favoriteArrivalAirport shouldBe "EGLL"

        statsOf(
            flight(1, "LFPG", "EGLL"),
            flight(2, "LFPG", "KJFK"),
        ).favoriteArrivalAirport shouldBe "EGLL"
    }

    @Test
    fun `departure and arrival counts are tracked separately`() {
        // EHAM only ever departs, EGLL only ever arrives.
        val stats = statsOf(
            flight(1, "EHAM", "EGLL"),
            flight(2, "EHAM", "EGLL"),
        )

        stats.favoriteDepartureAirport shouldBe "EHAM"
        stats.favoriteArrivalAirport shouldBe "EGLL"
    }

    @Test
    fun `most flown aircraft ties go to the lowest id`() {
        // One flight on aircraft 7 and one on aircraft 3; id 3 is the Airbus.
        statsOf(
            flight(1, "EHAM", "EGLL", aircraftId = 7),
            flight(2, "EGLL", "EHAM", aircraftId = 3),
        ).mostFlownAircraft shouldBe "Airbus A320"

        statsOf(
            flight(1, "EHAM", "EGLL", aircraftId = 3),
            flight(2, "EGLL", "EHAM", aircraftId = 7),
        ).mostFlownAircraft shouldBe "Airbus A320"
    }

    @Test
    fun `a higher flight count beats the lowest id`() {
        // Aircraft 7 twice against aircraft 3 once: count decides.
        statsOf(
            flight(1, "EHAM", "EGLL", aircraftId = 3),
            flight(2, "EGLL", "EHAM", aircraftId = 7),
            flight(3, "EHAM", "EGLL", aircraftId = 7),
        ).mostFlownAircraft shouldBe "Boeing 737-800"
    }

    @Test
    fun `shortest keeps the first of an equal pair and longest keeps the last`() {
        // Two flights, same distance: shortest is leg 1, longest is leg 2.
        val stats = statsOf(
            flight(1, "EHAM", "EGLL", distanceNm = 300),
            flight(2, "LFPG", "EDDF", distanceNm = 300),
        )

        stats.shortestFlight shouldBe "EHAM to EGLL"
        stats.longestFlight shouldBe "LFPG to EDDF"
    }

    @Test
    fun `the shortest-longest asymmetry survives being fed the other order`() {
        // Reversing the input reverses both answers, which is exactly what an
        // order-dependent rule should do — it is not an artefact of the fixture.
        val stats = statsOf(
            flight(1, "LFPG", "EDDF", distanceNm = 300),
            flight(2, "EHAM", "EGLL", distanceNm = 300),
        )

        stats.shortestFlight shouldBe "LFPG to EDDF"
        stats.longestFlight shouldBe "EHAM to EGLL"
    }

    @Test
    fun `the first shortest and the last longest are picked out of a longer log`() {
        // Minimum 50 appears at legs 2 and 4; maximum 900 at legs 3 and 5.
        val stats = statsOf(
            flight(1, "EHAM", "EGLL", distanceNm = 400),
            flight(2, "EGLL", "LFPG", distanceNm = 50),
            flight(3, "LFPG", "EDDF", distanceNm = 900),
            flight(4, "EDDF", "LSZH", distanceNm = 50),
            flight(5, "LSZH", "LIRF", distanceNm = 900),
        )

        stats.shortestFlight shouldBe "EGLL to LFPG"
        stats.longestFlight shouldBe "LSZH to LIRF"
    }

    @Test
    fun `three equal distances still give the first as shortest and the last as longest`() {
        val stats = statsOf(
            flight(1, "AAAA", "BBBB", distanceNm = 200),
            flight(2, "CCCC", "DDDD", distanceNm = 200),
            flight(3, "EEEE", "FFFF", distanceNm = 200),
        )

        stats.shortestFlight shouldBe "AAAA to BBBB"
        stats.longestFlight shouldBe "EEEE to FFFF"
    }
}
