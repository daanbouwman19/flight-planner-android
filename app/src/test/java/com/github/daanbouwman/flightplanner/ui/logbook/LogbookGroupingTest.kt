package com.github.daanbouwman.flightplanner.ui.logbook

import com.github.daanbouwman.flightplanner.model.AircraftSpec
import com.github.daanbouwman.flightplanner.model.FlightRecord
import io.kotest.matchers.shouldBe
import java.time.YearMonth
import kotlin.test.Test

private fun spec(id: Int, cruiseSpeedKt: Int = 450) = AircraftSpec(
    id = id,
    manufacturer = "Boeing",
    variant = "737-800",
    icaoCode = "B738",
    flown = true,
    rangeNm = 3000,
    category = "Jet",
    cruiseSpeedKt = cruiseSpeedKt,
    dateFlown = "2026-01-01",
    takeoffDistanceMeters = 2000,
)

private fun record(
    id: Long,
    date: String,
    distanceNm: Int? = 500,
    aircraftId: Int = 1,
) = FlightRecord(
    id = id,
    departureIcao = "EHAM",
    arrivalIcao = "EGLL",
    aircraftId = aircraftId,
    date = date,
    distanceNm = distanceNm,
)

class LogbookGroupingTest {

    private val fleet = mapOf(1 to spec(1))

    @Test
    fun `a malformed date is excluded rather than crashing`() {
        record(1, "not-a-date").toLogbookRowOrNull(fleet) shouldBe null
    }

    @Test
    fun `an unknown aircraft still produces a row, with no display name`() {
        val row = record(1, "2026-03-01", aircraftId = 99).toLogbookRowOrNull(fleet)

        row?.aircraftDisplayName shouldBe null
        // GreatCircle.flightTime falls back to the default cruise speed on its
        // own, so a missing aircraft still resolves a flight time.
        (row?.flightTime != null) shouldBe true
    }

    @Test
    fun `a known aircraft resolves its display name`() {
        val row = record(1, "2026-03-01", aircraftId = 1).toLogbookRowOrNull(fleet)

        row?.aircraftDisplayName shouldBe "Boeing 737-800"
    }

    @Test
    fun `a flight with no distance has no flight time either`() {
        val row = record(1, "2026-03-01", distanceNm = null).toLogbookRowOrNull(fleet)

        row?.flightTime shouldBe null
    }

    @Test
    fun `groupByMonth preserves newest-first order and keeps each month together`() {
        val rows = listOf(
            record(3, "2026-03-15"),
            record(2, "2026-03-01"),
            record(1, "2026-02-20"),
        ).mapNotNull { it.toLogbookRowOrNull(fleet) }

        val groups = rows.groupByMonth()

        groups.map { it.key } shouldBe listOf(YearMonth.of(2026, 3), YearMonth.of(2026, 2))
        groups[0].rows.map { it.id } shouldBe listOf(3L, 2L)
        groups[1].rows.map { it.id } shouldBe listOf(1L)
    }

    @Test
    fun `groupByMonth on an empty list returns no groups`() {
        emptyList<LogbookRow>().groupByMonth() shouldBe emptyList()
    }

    @Test
    fun `summarizeYear counts only the target year`() {
        val rows = listOf(
            record(1, "2026-01-10"),
            record(2, "2025-12-31"),
        ).mapNotNull { it.toLogbookRowOrNull(fleet) }

        rows.summarizeYear(2026).flights shouldBe 1
    }

    @Test
    fun `a flight with no distance counts toward flights but not distance or hours`() {
        val rows = listOf(
            record(1, "2026-01-10", distanceNm = null),
        ).mapNotNull { it.toLogbookRowOrNull(fleet) }

        val summary = rows.summarizeYear(2026)
        summary.flights shouldBe 1
        summary.distanceNm shouldBe 0
        summary.minutesFlown shouldBe 0
    }

    @Test
    fun `minutes flown sum across flights with different aircraft speeds`() {
        val fastFleet = mapOf(1 to spec(1, cruiseSpeedKt = 600), 2 to spec(2, cruiseSpeedKt = 300))
        val rows = listOf(
            // 1 hour at 600 kt.
            record(1, "2026-01-01", distanceNm = 600, aircraftId = 1),
            // 1 hour at 300 kt.
            record(2, "2026-01-02", distanceNm = 300, aircraftId = 2),
        ).mapNotNull { it.toLogbookRowOrNull(fastFleet) }

        rows.summarizeYear(2026).minutesFlown shouldBe 120
    }

    @Test
    fun `distance sums across the year`() {
        val rows = listOf(
            record(1, "2026-01-01", distanceNm = 400),
            record(2, "2026-06-15", distanceNm = 250),
        ).mapNotNull { it.toLogbookRowOrNull(fleet) }

        rows.summarizeYear(2026).distanceNm shouldBe 650
    }
}
