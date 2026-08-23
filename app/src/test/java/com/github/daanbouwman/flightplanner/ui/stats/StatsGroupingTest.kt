package com.github.daanbouwman.flightplanner.ui.stats

import com.github.daanbouwman.flightplanner.model.AircraftSpec
import com.github.daanbouwman.flightplanner.model.Airport
import com.github.daanbouwman.flightplanner.model.AirportSizeClass
import com.github.daanbouwman.flightplanner.model.FlightRecord
import io.kotest.matchers.shouldBe
import io.kotest.matchers.nulls.shouldNotBeNull
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.Test

class StatsGroupingTest {

    private val testToday = LocalDate.of(2026, 8, 22)

    private val testFleet = listOf(
        AircraftSpec(
            id = 1,
            manufacturer = "Boeing",
            variant = "737-800",
            icaoCode = "B738",
            flown = false,
            rangeNm = 2935,
            category = "Narrow-body",
            cruiseSpeedKt = 453,
            dateFlown = null,
            takeoffDistanceMeters = 2300,
        ),
        AircraftSpec(
            id = 2,
            manufacturer = "Airbus",
            variant = "A320neo",
            icaoCode = "A20N",
            flown = false,
            rangeNm = 3500,
            category = "Narrow-body",
            cruiseSpeedKt = 450,
            dateFlown = null,
            takeoffDistanceMeters = 2100,
        ),
        AircraftSpec(
            id = 3,
            manufacturer = "Boeing",
            variant = "777-300ER",
            icaoCode = "B77W",
            flown = false,
            rangeNm = 7370,
            category = "Wide-body",
            cruiseSpeedKt = 490,
            dateFlown = null,
            takeoffDistanceMeters = 3100,
        ),
    )

    private val testAirports = mapOf(
        "EHAM" to Airport(
            id = 1,
            icao = "EHAM",
            name = "Amsterdam Airport Schiphol",
            latitude = 52.3086,
            longitude = 4.7639,
            elevationFt = -11,
            country = "NL",
            municipality = "Amsterdam",
            sizeClass = AirportSizeClass.LARGE,
            longestRunwayFt = 12467,
            runwayCount = 6,
            hasHardSurface = true,
            hasIcaoCode = true,
        ),
        "KJFK" to Airport(
            id = 2,
            icao = "KJFK",
            name = "John F Kennedy International Airport",
            latitude = 40.6398,
            longitude = -73.7789,
            elevationFt = 13,
            country = "US",
            municipality = "New York",
            sizeClass = AirportSizeClass.LARGE,
            longestRunwayFt = 14511,
            runwayCount = 4,
            hasHardSurface = true,
            hasIcaoCode = true,
        ),
        "EGLL" to Airport(
            id = 3,
            icao = "EGLL",
            name = "London Heathrow Airport",
            latitude = 51.4706,
            longitude = -0.4619,
            elevationFt = 83,
            country = "GB",
            municipality = "London",
            sizeClass = AirportSizeClass.LARGE,
            longestRunwayFt = 12802,
            runwayCount = 2,
            hasHardSurface = true,
            hasIcaoCode = true,
        ),
    )

    @Test
    fun timeframeFilter_allTime_returnsAll() {
        val records = listOf(
            FlightRecord(1, "EHAM", "KJFK", 3, "2026-08-01", 3160),
            FlightRecord(2, "EHAM", "EGLL", 1, "2025-12-15", 200),
            FlightRecord(3, "EGLL", "EHAM", 2, "2024-05-10", 200),
        )

        val allTime = StatsGrouping.filterByTimeframe(records, StatsTimeframe.ALL_TIME, testToday)
        allTime.size shouldBe 3
    }

    @Test
    fun timeframeFilter_thisYear_matchesCurrentYear() {
        val records = listOf(
            FlightRecord(1, "EHAM", "KJFK", 3, "2026-08-01", 3160),
            FlightRecord(2, "EHAM", "EGLL", 1, "2026-01-15", 200),
            FlightRecord(3, "EGLL", "EHAM", 2, "2025-12-31", 200),
        )

        val thisYear = StatsGrouping.filterByTimeframe(records, StatsTimeframe.THIS_YEAR, testToday)
        thisYear.size shouldBe 2
        thisYear.map { it.id } shouldBe listOf(1L, 2L)
    }

    @Test
    fun timeframeFilter_past12Months_matchesRolling12Months() {
        val records = listOf(
            FlightRecord(1, "EHAM", "KJFK", 3, "2026-08-01", 3160), // In (month 12: Aug 2026)
            FlightRecord(2, "EHAM", "EGLL", 1, "2026-02-15", 200),  // In (month 6: Feb 2026)
            FlightRecord(3, "EGLL", "EHAM", 2, "2025-09-10", 200),  // In (month 1: Sep 2025)
            FlightRecord(4, "EGLL", "EHAM", 2, "2025-08-31", 200),  // Out (month 0: Aug 2025, 13th month)
        )

        val past12 = StatsGrouping.filterByTimeframe(records, StatsTimeframe.PAST_12_MONTHS, testToday)
        past12.size shouldBe 3
        past12.map { it.id } shouldBe listOf(1L, 2L, 3L)
    }

    @Test
    fun monthlyActivity_thisYear_fills12Months() {
        val records = listOf(
            FlightRecord(1, "EHAM", "KJFK", 3, "2026-01-10", 3160),
            FlightRecord(2, "EHAM", "EGLL", 1, "2026-01-20", 200),
            FlightRecord(3, "EGLL", "EHAM", 2, "2026-08-15", 200),
        )

        val monthly = StatsGrouping.buildMonthlyActivity(records, StatsTimeframe.THIS_YEAR, testToday)
        monthly.size shouldBe 12
        monthly[0].yearMonth shouldBe YearMonth.of(2026, 1)
        monthly[0].flightCount shouldBe 2
        monthly[0].distanceNm shouldBe 3360

        // Empty months in between have 0 flights
        monthly[1].yearMonth shouldBe YearMonth.of(2026, 2)
        monthly[1].flightCount shouldBe 0
        monthly[1].distanceNm shouldBe 0

        monthly[7].yearMonth shouldBe YearMonth.of(2026, 8)
        monthly[7].flightCount shouldBe 1
        monthly[7].distanceNm shouldBe 200
    }

    @Test
    fun topAircraft_breaksTiesByLowestId() {
        // Aircraft 2 and 1 both have 2 flights, aircraft 3 has 1 flight
        val records = listOf(
            FlightRecord(1, "EHAM", "EGLL", 2, "2026-01-10", 200),
            FlightRecord(2, "EGLL", "EHAM", 2, "2026-01-11", 200),
            FlightRecord(3, "EHAM", "EGLL", 1, "2026-01-12", 200),
            FlightRecord(4, "EGLL", "EHAM", 1, "2026-01-13", 200),
            FlightRecord(5, "EHAM", "KJFK", 3, "2026-01-14", 3160),
        )

        val top = StatsGrouping.computeTopAircraft(records, testFleet)
        top.size shouldBe 3
        // Aircraft 1 has id 1, aircraft 2 has id 2 -> id 1 wins tie-break if counts & distances equal
        top[0].aircraft.id shouldBe 1
        top[1].aircraft.id shouldBe 2
        top[2].aircraft.id shouldBe 3
    }

    @Test
    fun airportHighlights_breaksTiesByAlphabeticalIcao() {
        val records = listOf(
            FlightRecord(1, "KJFK", "EGLL", 3, "2026-01-10", 3000),
            FlightRecord(2, "EHAM", "EGLL", 1, "2026-01-11", 200),
        )

        val (favDep, favArr, mostVisited) = StatsGrouping.computeAirportHighlights(records, testAirports)
        // Departures: EHAM (1), KJFK (1) -> EHAM wins alphabetically
        favDep.shouldNotBeNull()
        favDep.icao shouldBe "EHAM"
        favDep.count shouldBe 1

        // Arrivals: EGLL (2)
        favArr.shouldNotBeNull()
        favArr.icao shouldBe "EGLL"
        favArr.count shouldBe 2

        // Most visited: EGLL (2 visits), EHAM (1 visit), KJFK (1 visit)
        mostVisited.shouldNotBeNull()
        mostVisited.icao shouldBe "EGLL"
        mostVisited.count shouldBe 2
    }

    @Test
    fun longestAndShortest_asymmetryPreserved() {
        val records = listOf(
            FlightRecord(1, "EHAM", "EGLL", 1, "2026-01-10", 200), // First min
            FlightRecord(2, "EGLL", "EHAM", 2, "2026-01-11", 200), // Second min
            FlightRecord(3, "EHAM", "KJFK", 3, "2026-01-12", 3160), // First max
            FlightRecord(4, "KJFK", "EHAM", 3, "2026-01-13", 3160), // Second max
        )

        val shortest = StatsGrouping.computeShortestFlight(records)
        val longest = StatsGrouping.computeLongestFlight(records)

        // Shortest keeps first minimum (EHAM -> EGLL)
        shortest?.departureIcao shouldBe "EHAM"
        shortest?.arrivalIcao shouldBe "EGLL"

        // Longest keeps last maximum (KJFK -> EHAM)
        longest?.departureIcao shouldBe "KJFK"
        longest?.arrivalIcao shouldBe "EHAM"
    }

    @Test
    fun visitedNetwork_buildsDeduplicatedLegsAndAirports() {
        val records = listOf(
            FlightRecord(1, "EHAM", "EGLL", 1, "2026-01-10", 200),
            FlightRecord(2, "EGLL", "EHAM", 2, "2026-01-11", 200), // Reverse leg, should be deduplicated
            FlightRecord(3, "EHAM", "KJFK", 3, "2026-01-12", 3160),
        )

        val (airports, legs) = StatsGrouping.buildVisitedNetwork(records, testAirports)
        airports.size shouldBe 3
        legs.size shouldBe 2 // EHAM-EGLL and EHAM-KJFK
        (legs.any { it.departureIcao == "EGLL" || it.arrivalIcao == "EGLL" }) shouldBe true
        (legs.any { it.departureIcao == "KJFK" || it.arrivalIcao == "KJFK" }) shouldBe true
    }
}
