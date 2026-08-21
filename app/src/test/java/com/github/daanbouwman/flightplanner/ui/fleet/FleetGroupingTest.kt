package com.github.daanbouwman.flightplanner.ui.fleet

import com.github.daanbouwman.flightplanner.model.AircraftSpec
import io.kotest.matchers.shouldBe
import kotlin.test.Test

private fun spec(
    id: Int,
    manufacturer: String = "Boeing",
    variant: String = "737-800",
    category: String = "Jet",
    flown: Boolean = false,
) = AircraftSpec(
    id = id,
    manufacturer = manufacturer,
    variant = variant,
    icaoCode = "B738",
    flown = flown,
    rangeNm = 3000,
    category = category,
    cruiseSpeedKt = 450,
    dateFlown = if (flown) "2026-01-01" else null,
    takeoffDistanceMeters = 2000,
)

class FleetGroupingTest {

    @Test
    fun `filterByMode All returns everything`() {
        val fleet = listOf(spec(1, flown = true), spec(2, flown = false))
        fleet.filterByMode(FleetMode.All) shouldBe fleet
    }

    @Test
    fun `filterByMode Flown keeps only flown aircraft`() {
        val flown = spec(1, flown = true)
        val notFlown = spec(2, flown = false)
        listOf(flown, notFlown).filterByMode(FleetMode.Flown) shouldBe listOf(flown)
    }

    @Test
    fun `filterByMode NotFlown keeps only unflown aircraft`() {
        val flown = spec(1, flown = true)
        val notFlown = spec(2, flown = false)
        listOf(flown, notFlown).filterByMode(FleetMode.NotFlown) shouldBe listOf(notFlown)
    }

    @Test
    fun `groupByCategory groups by category in category order`() {
        val fleet = listOf(
            spec(1, category = "Sport"),
            spec(2, category = "Jet"),
            spec(3, category = "Cargo"),
        )

        val groups = fleet.groupByCategory()

        groups.map { it.category } shouldBe listOf("Cargo", "Jet", "Sport")
    }

    @Test
    fun `groupByCategory sorts within a group by manufacturer then variant`() {
        val fleet = listOf(
            spec(1, manufacturer = "Cessna", variant = "172"),
            spec(2, manufacturer = "Boeing", variant = "777-300ER"),
            spec(3, manufacturer = "Boeing", variant = "737-800"),
        )

        val groups = fleet.groupByCategory()

        groups.single().aircraft.map { it.id } shouldBe listOf(3, 2, 1)
    }

    @Test
    fun `groupByCategory on an empty list returns no groups`() {
        emptyList<AircraftSpec>().groupByCategory() shouldBe emptyList()
    }
}
