package com.github.daanbouwman.flightplanner.widget

import com.github.daanbouwman.flightplanner.model.AircraftSpec
import com.github.daanbouwman.flightplanner.model.AirportSizeClass
import com.github.daanbouwman.flightplanner.routing.AirportIndex
import com.github.daanbouwman.flightplanner.routing.AirportIndexBuilder
import com.github.daanbouwman.flightplanner.settings.UnitSystem
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldEndWith
import io.kotest.matchers.string.shouldMatch
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import java.io.IOException
import java.time.LocalDate
import kotlin.test.Test

/**
 * The widget's state, from an in-memory index and a list — the seam
 * constructor's whole purpose. What is pinned: the figures are worded in the
 * chosen unit, a bare fleet is seeded before being declared empty, and every
 * failure is a state rather than an exception.
 */
class DailyChallengeSourceTest {

    private val day = LocalDate.of(2026, 9, 16)

    private val world: AirportIndex = AirportIndexBuilder(4).apply {
        add(1, "EHAM", 52.3086, 4.7639, 12_000, flags())
        add(2, "KJFK", 40.6398, -73.7789, 14_000, flags())
        add(3, "EGLL", 51.4700, -0.4543, 12_800, flags())
        add(4, "LFPG", 49.0097, 2.5479, 13_800, flags())
    }.build()

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

    private fun source(
        index: suspend () -> AirportIndex = { world },
        fleet: suspend () -> List<AircraftSpec> = { listOf(boeing) },
        seedFleet: suspend () -> Unit = {},
    ) = DailyChallengeSource(index = index, fleet = fleet, seedFleet = seedFleet, dispatcher = Dispatchers.Default)

    @Test
    fun `a fleet and an index give a route worded in the chosen unit`() = runTest {
        val aviation = source().load(day, UnitSystem.AVIATION, icaoOnly = false).shouldBeInstanceOf<ChallengeState.Ready>()
        aviation.departureIcao.length shouldBe 4
        aviation.destinationIcao.length shouldBe 4
        aviation.aircraftName shouldBe "Boeing 787-9"
        aviation.distanceText shouldEndWith " NM"
        aviation.eteText shouldMatch Regex("\\d+:\\d\\d")
        aviation.route.aircraftId shouldBe 7
        aviation.route.departureIcao shouldBe aviation.departureIcao
        aviation.date shouldBe day
        // The map's arc: the route card's sampling, starting at the departure.
        aviation.arc.size shouldBe 128
        val departureSlot = (0 until world.size).first { world.icaoOf(it) == aviation.departureIcao }
        aviation.arc.lats[0] shouldBe world.latDegOf(departureSlot)

        val metric = source().load(day, UnitSystem.METRIC, icaoOnly = false).shouldBeInstanceOf<ChallengeState.Ready>()
        metric.distanceText shouldEndWith " km"
        metric.route shouldBe aviation.route
    }

    @Test
    fun `an empty fleet is seeded first, and only then declared empty`() = runTest {
        var seeded = false
        val seedsInto = source(
            fleet = { if (seeded) listOf(boeing) else emptyList() },
            seedFleet = { seeded = true },
        )
        seedsInto.load(day, UnitSystem.AVIATION, icaoOnly = false).shouldBeInstanceOf<ChallengeState.Ready>()
        seeded shouldBe true

        source(fleet = { emptyList() }).load(day, UnitSystem.AVIATION, icaoOnly = false) shouldBe ChallengeState.FleetEmpty
    }

    @Test
    fun `a failed read is a state, never an exception`() = runTest {
        source(index = { throw IOException("asset missing") })
            .load(day, UnitSystem.AVIATION, icaoOnly = false) shouldBe ChallengeState.Unavailable
        source(fleet = { throw IllegalStateException("database closed") })
            .load(day, UnitSystem.AVIATION, icaoOnly = false) shouldBe ChallengeState.Unavailable
    }

    private fun flags() = AirportIndex.packFlags(
        hasIcao = true,
        hardSurface = true,
        lighting = true,
        sizeClass = AirportSizeClass.LARGE,
    )
}
