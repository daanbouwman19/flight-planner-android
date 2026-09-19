package com.github.daanbouwman.flightplanner.widget

import com.github.daanbouwman.flightplanner.model.AircraftSpec
import com.github.daanbouwman.flightplanner.settings.UnitSystem
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldEndWith
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import java.io.IOException
import java.time.LocalDate
import kotlin.test.Test

/**
 * The aircraft widget's state, from a plain list — the seam constructor's whole
 * purpose. What is pinned: the figures are worded in the chosen unit, an
 * airframe with no takeoff distance shows no runway figure at all, a bare fleet
 * is seeded before being declared empty, and every failure is a state rather
 * than an exception.
 */
class DailyAircraftSourceTest {

    private val day = LocalDate.of(2026, 9, 16)

    private val boeing = AircraftSpec(
        id = 7,
        manufacturer = "Boeing",
        variant = "787-9",
        icaoCode = "B789",
        flown = false,
        rangeNm = 7_600,
        category = "Wide-body",
        cruiseSpeedKt = 488,
        dateFlown = null,
        takeoffDistanceMeters = 2_900,
    )

    /** No takeoff distance at all — the seed fleet's gliders are this shape. */
    private val glider = boeing.copy(
        id = 9,
        manufacturer = "Schleicher",
        variant = "ASK 21",
        icaoCode = "AS21",
        rangeNm = 60,
        takeoffDistanceMeters = null,
    )

    private fun source(
        fleet: suspend () -> List<AircraftSpec> = { listOf(boeing) },
        seedFleet: suspend () -> Unit = {},
    ) = DailyAircraftSource(fleet = fleet, seedFleet = seedFleet)

    @Test
    fun `a fleet gives an airframe worded in the chosen unit`() = runTest {
        val aviation = source().load(day, UnitSystem.AVIATION).shouldBeInstanceOf<AircraftState.Ready>()
        aviation.date shouldBe day
        aviation.name shouldBe "Boeing 787-9"
        aviation.typeCode shouldBe "B789"
        aviation.flown shouldBe false
        aviation.airframeId shouldBe 7
        aviation.rangeText shouldBe "7,600 NM"
        // 2,900 m of takeoff distance, through the fleet's own runway rule.
        aviation.runwayText shouldEndWith " ft"

        val metric = source().load(day, UnitSystem.METRIC).shouldBeInstanceOf<AircraftState.Ready>()
        metric.rangeText shouldEndWith " km"
        metric.runwayText shouldEndWith " m"
        metric.airframeId shouldBe aviation.airframeId
    }

    @Test
    fun `an airframe that names no takeoff distance has no runway figure`() = runTest {
        val state = source(fleet = { listOf(glider) }).load(day, UnitSystem.AVIATION)
            .shouldBeInstanceOf<AircraftState.Ready>()
        state.runwayText.shouldBeNull()
        state.rangeText shouldBe "60 NM"
    }

    @Test
    fun `the flown flag is reported as it stands`() = runTest {
        source(fleet = { listOf(boeing.copy(flown = true)) })
            .load(day, UnitSystem.AVIATION)
            .shouldBeInstanceOf<AircraftState.Ready>()
            .flown shouldBe true
    }

    @Test
    fun `an empty fleet is seeded first, and only then declared empty`() = runTest {
        var seeded = false
        val seedsInto = source(
            fleet = { if (seeded) listOf(boeing) else emptyList() },
            seedFleet = { seeded = true },
        )
        seedsInto.load(day, UnitSystem.AVIATION).shouldBeInstanceOf<AircraftState.Ready>()
        seeded shouldBe true

        source(fleet = { emptyList() }).load(day, UnitSystem.AVIATION) shouldBe AircraftState.FleetEmpty
    }

    @Test
    fun `a failed read is a state, never an exception`() = runTest {
        source(fleet = { throw IllegalStateException("database closed") })
            .load(day, UnitSystem.AVIATION) shouldBe AircraftState.Unavailable
        source(fleet = { emptyList() }, seedFleet = { throw IOException("seed asset missing") })
            .load(day, UnitSystem.AVIATION) shouldBe AircraftState.Unavailable
    }

    @Test
    fun `the same day gives the same airframe, and the fleet's order does not matter`() = runTest {
        val airbus = boeing.copy(id = 3, manufacturer = "Airbus", variant = "A320neo", icaoCode = "A20N")
        val fleet = listOf(boeing, glider, airbus)
        val forwards = source(fleet = { fleet }).load(day, UnitSystem.AVIATION)
            .shouldBeInstanceOf<AircraftState.Ready>()
        val backwards = source(fleet = { fleet.reversed() }).load(day, UnitSystem.AVIATION)
            .shouldBeInstanceOf<AircraftState.Ready>()
        backwards shouldBe forwards
    }
}
