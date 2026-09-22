package com.github.daanbouwman.flightplanner.wear.route

import com.github.daanbouwman.flightplanner.handoff.WatchRoute
import com.github.daanbouwman.flightplanner.handoff.WatchRouteLink
import com.github.daanbouwman.flightplanner.model.AircraftSpec
import com.github.daanbouwman.flightplanner.routing.GeneratedRoute
import com.github.daanbouwman.flightplanner.routing.RouteArc
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlin.test.Test

/** Turning a generated route into the thing the face draws and the phone receives. */
class WatchRouteCardTest {

    private fun route(aircraft: AircraftSpec = testAircraft()) =
        GeneratedRoute(
            aircraft = aircraft,
            departureSlot = testIndex.slotOf("EHAM"),
            destinationSlot = testIndex.slotOf("EGLL"),
            distanceNm = 200,
            departureRunwayFt = 12_467,
            destinationRunwayFt = 12_799,
        )

    @Test
    fun `resolves codes, words the figures and samples the arc`() {
        val card = route().toCard(testIndex)

        card.departureIcao shouldBe "EHAM"
        card.destinationIcao shouldBe "EGLL"
        card.distanceNm shouldBe 200
        card.distanceText shouldBe "200 NM"
        card.aircraftName shouldBe "Cessna 172S Skyhawk"
        card.aircraftTypeCode shouldBe "C172"
        card.arc.size shouldBe RouteArc.CARD_SAMPLES
    }

    /**
     * 200 NM at 110 kt is 1 h 49 m. The figure itself belongs to
     * `GreatCircle.flightTime`; what this pins is that the card carries the
     * airframe's own cruise speed into it rather than a default.
     */
    @Test
    fun `the time comes from the airframe's cruise speed`() {
        route(testAircraft(cruiseKt = 110)).toCard(testIndex).eteText shouldBe "1:49"
        route(testAircraft(cruiseKt = 450)).toCard(testIndex).eteText shouldNotBe "1:49"
    }

    /**
     * `icao_code` is optional in the fleet CSV, and a blank one would make
     * [WatchRouteLink] reject the link outright — the field is required there.
     * So a blank is named on the way in, where the reason is visible.
     */
    @Test
    fun `an airframe with no type code still produces a link the phone accepts`() {
        val untyped = route(testAircraft(typeCode = "")).toCard(testIndex)
        untyped.aircraftTypeCode shouldBe "ZZZZ"

        WatchRouteLink.parse(WatchRouteLink.build(untyped.asHandoff())) shouldBe WatchRoute(
            departureIcao = "EHAM",
            destinationIcao = "EGLL",
            distanceNm = 200,
            aircraftTypeCode = "ZZZZ",
            aircraftName = "Cessna 172S Skyhawk",
        )
    }

    /**
     * The key is the pager's, so it has to tell two routes apart by everything
     * that makes them different routes — and treat the same route in the same
     * airframe as one page, however many times the generator produces it.
     */
    @Test
    fun `the key identifies a route by its ends and its airframe`() {
        val cessna = route().toCard(testIndex)
        val boeing = route(testAircraft(manufacturer = "Boeing", variant = "737-800", typeCode = "B738"))
            .toCard(testIndex)

        cessna.key() shouldBe "EHAM>EGLL@C172"
        cessna.key() shouldNotBe boeing.key()
        cessna.key() shouldBe route().toCard(testIndex).key()
    }
}
