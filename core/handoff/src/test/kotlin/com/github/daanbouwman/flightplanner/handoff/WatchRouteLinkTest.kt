package com.github.daanbouwman.flightplanner.handoff

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class WatchRouteLinkTest {

    private val route = WatchRoute(
        departureIcao = "EHAM",
        destinationIcao = "EGLL",
        distanceNm = 200,
        aircraftTypeCode = "C172",
        aircraftName = "Cessna 172S Skyhawk",
    )

    @Test
    fun `round-trips a route`() {
        WatchRouteLink.parse(WatchRouteLink.build(route)) shouldBe route
    }

    @Test
    fun `builds the documented grammar`() {
        WatchRouteLink.build(route) shouldBe
            "flightplanner://route/EHAM/EGLL?nm=200&ac=C172&name=Cessna+172S+Skyhawk"
    }

    /**
     * The airframe name comes from a CSV the user can hand-edit, so it reaches
     * this codec as free text. `Beech 18, Twin Beech` and `PC-6 100%` are both
     * real shapes; neither may change what the link says.
     */
    @Test
    fun `round-trips a name carrying separators`() {
        val awkward = route.copy(aircraftName = "Beech 18, Twin Beech / 100% & more?")
        WatchRouteLink.parse(WatchRouteLink.build(awkward)) shouldBe awkward
    }

    @Test
    fun `uppercases and trims codes on the way out`() {
        val parsed = WatchRouteLink.parse(WatchRouteLink.build(route.copy(departureIcao = " eham ")))
        parsed?.departureIcao shouldBe "EHAM"
    }

    @Test
    fun `rejects another app's scheme`() {
        WatchRouteLink.parse("https://route/EHAM/EGLL?nm=1&ac=C172&name=x").shouldBeNull()
    }

    @Test
    fun `rejects another host under our scheme`() {
        WatchRouteLink.parse("flightplanner://aircraft/EHAM/EGLL?nm=1&ac=C172&name=x").shouldBeNull()
    }

    @Test
    fun `rejects a path that does not name exactly two airports`() {
        WatchRouteLink.parse("flightplanner://route/EHAM?nm=1&ac=C172&name=x").shouldBeNull()
        WatchRouteLink.parse("flightplanner://route/EHAM/EGLL/KJFK?nm=1&ac=C172&name=x").shouldBeNull()
    }

    @Test
    fun `rejects a link with no airframe`() {
        WatchRouteLink.parse("flightplanner://route/EHAM/EGLL?nm=200").shouldBeNull()
        WatchRouteLink.parse("flightplanner://route/EHAM/EGLL?nm=200&ac=C172").shouldBeNull()
    }

    /**
     * Absent is 0 — the phone recomputes the real figure from its own index —
     * but present-and-unreadable is malformed, and must not silently become 0.
     */
    @Test
    fun `tolerates an absent distance and rejects an unreadable one`() {
        WatchRouteLink.parse("flightplanner://route/EHAM/EGLL?ac=C172&name=x")?.distanceNm shouldBe 0
        WatchRouteLink.parse("flightplanner://route/EHAM/EGLL?nm=soon&ac=C172&name=x").shouldBeNull()
        WatchRouteLink.parse("flightplanner://route/EHAM/EGLL?nm=-5&ac=C172&name=x").shouldBeNull()
    }

    @Test
    fun `is null on junk rather than throwing`() {
        WatchRouteLink.parse(null).shouldBeNull()
        WatchRouteLink.parse("").shouldBeNull()
        WatchRouteLink.parse("not a uri at all").shouldBeNull()
        // A truncated escape is not a URI at all, so it never reaches the field checks.
        WatchRouteLink.parse("flightplanner://route/%/EGLL?nm=1&ac=C172&name=x").shouldBeNull()
    }

    /**
     * The property that actually matters: whatever the watch puts in, the phone
     * gets back. Codes are generated short because [WatchRouteLink] rejects a
     * segment longer than an airport code can be.
     */
    @Test
    fun `round-trips arbitrary airframes`() = runTest {
        checkAll(
            Arb.string(1..4),
            Arb.string(1..4),
            Arb.int(0..20_000),
            Arb.string(1..8),
            Arb.string(1..40),
        ) { departure, destination, distance, typeCode, name ->
            val original = WatchRoute(
                departureIcao = departure.trim().uppercase(),
                destinationIcao = destination.trim().uppercase(),
                distanceNm = distance,
                aircraftTypeCode = typeCode,
                aircraftName = name,
            )
            // A code or a name that is only whitespace is not one, and the codec
            // says so by refusing the link; the property is about the ones that are.
            if (original.departureIcao.isEmpty() || original.destinationIcao.isEmpty()) return@checkAll
            if (typeCode.isBlank() || name.isBlank()) return@checkAll

            WatchRouteLink.parse(WatchRouteLink.build(original)) shouldBe original
        }
    }
}
