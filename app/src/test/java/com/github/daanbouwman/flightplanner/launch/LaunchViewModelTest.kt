package com.github.daanbouwman.flightplanner.launch

import com.github.daanbouwman.flightplanner.handoff.WatchRoute
import com.github.daanbouwman.flightplanner.model.AircraftSpec
import com.github.daanbouwman.flightplanner.model.FlightRecord
import com.github.daanbouwman.flightplanner.navigation.Destination
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import java.io.IOException
import kotlin.test.Test

/**
 * The hand-off between the Activity and the NavHost, the "Last route"
 * resolution order, and the watch's airframe matching. Composed against the
 * internal seam constructor, so no DataStore and no Room.
 */
class LaunchViewModelTest {

    private val opened = Destination.RouteDetail("EHAM", "KJFK", aircraftId = 7, distanceNm = 3_162)
    private val flight = FlightRecord(
        id = 9,
        departureIcao = "EGLL",
        arrivalIcao = "LFPG",
        aircraftId = 3,
        date = "2026-09-01",
        distanceNm = null,
    )

    private fun airframe(id: Int, manufacturer: String, variant: String, typeCode: String) = AircraftSpec(
        id = id,
        manufacturer = manufacturer,
        variant = variant,
        icaoCode = typeCode,
        flown = false,
        rangeNm = 3_000,
        category = "Narrow-body",
        cruiseSpeedKt = 450,
        dateFlown = null,
        takeoffDistanceMeters = 2_000,
    )

    /** Two airframes sharing a type code, plus one that does not — as the seed fleet has. */
    private val seedFleet = listOf(
        airframe(1, "ATR", "42-600", "AT46"),
        airframe(2, "ATR", "42-600 Highline", "AT46"),
        airframe(3, "Boeing", "737-800", "B738"),
    )

    private val fromTheWatch = WatchRoute(
        departureIcao = "EHAM",
        destinationIcao = "KJFK",
        distanceNm = 3_162,
        aircraftTypeCode = "B738",
        aircraftName = "Boeing 737-800",
    )

    private fun viewModel(
        lastRoute: suspend () -> Destination.RouteDetail? = { null },
        latestFlight: suspend () -> FlightRecord? = { null },
        fleet: suspend () -> List<AircraftSpec> = { seedFleet },
    ) = LaunchViewModel(readLastRoute = lastRoute, latestFlight = latestFlight, fleet = fleet)

    @Test
    fun `an offered request is pending until consumed, and consuming it once clears it`() {
        val model = viewModel()
        model.pending.value.shouldBeNull()

        model.offer(LaunchRequest.GenerateRoutes)
        model.pending.value shouldBe LaunchRequest.GenerateRoutes

        model.consume(LaunchRequest.GenerateRoutes)
        model.pending.value.shouldBeNull()
    }

    @Test
    fun `offering nothing changes nothing`() {
        val model = viewModel()
        model.offer(LaunchRequest.LogFlight)
        model.offer(null)
        model.pending.value shouldBe LaunchRequest.LogFlight
    }

    @Test
    fun `consuming a stale request does not discard a newer one`() {
        val model = viewModel()
        model.offer(LaunchRequest.GenerateRoutes)
        // A second Intent arrived through onNewIntent while the first was being handled.
        model.offer(LaunchRequest.LogFlight)

        model.consume(LaunchRequest.GenerateRoutes)
        model.pending.value shouldBe LaunchRequest.LogFlight
    }

    @Test
    fun `the last route is the one last opened when there is one`() = runTest {
        viewModel(lastRoute = { opened }, latestFlight = { flight }).resolveLastRoute() shouldBe opened
    }

    @Test
    fun `with nothing opened, the newest logbook flight stands in, marked already flown`() = runTest {
        viewModel(latestFlight = { flight }).resolveLastRoute() shouldBe Destination.RouteDetail(
            departureIcao = "EGLL",
            destinationIcao = "LFPG",
            aircraftId = 3,
            distanceNm = 0,
            alreadyFlown = true,
        )
    }

    @Test
    fun `with neither, there is no last route`() = runTest {
        viewModel().resolveLastRoute().shouldBeNull()
    }

    @Test
    fun `an unreadable store falls through to the logbook rather than failing the shortcut`() = runTest {
        val model = viewModel(lastRoute = { throw IOException("preferences unreadable") }, latestFlight = { flight })
        model.resolveLastRoute()?.departureIcao shouldBe "EGLL"

        viewModel(
            lastRoute = { throw IOException("preferences unreadable") },
            latestFlight = { throw IllegalStateException("database closed") },
        ).resolveLastRoute().shouldBeNull()
    }

    @Test
    fun `a watch route resolves its airframe by name`() = runTest {
        viewModel().resolveWatchRoute(fromTheWatch) shouldBe Destination.RouteDetail(
            departureIcao = "EHAM",
            destinationIcao = "KJFK",
            aircraftId = 3,
            distanceNm = 3_162,
        )
    }

    /**
     * The fallback, and why it is a fallback: `AT46` names two airframes in the
     * seed fleet alone. Matching the type code first would open a route in
     * whichever of them came out of Room first, which is not the one the wearer
     * was looking at — so the name wins whenever it matches at all.
     */
    @Test
    fun `an exact name beats a shared type code`() = runTest {
        val highline = fromTheWatch.copy(aircraftTypeCode = "AT46", aircraftName = "ATR 42-600 Highline")
        viewModel().resolveWatchRoute(highline)?.aircraftId shouldBe 2
    }

    @Test
    fun `an airframe this phone no longer has falls back to the type code`() = runTest {
        val renamed = fromTheWatch.copy(aircraftName = "Boeing 737-800 (retired)")
        viewModel().resolveWatchRoute(renamed)?.aircraftId shouldBe 3
    }

    @Test
    fun `an airframe matching nothing at all is no route`() = runTest {
        val unknown = fromTheWatch.copy(aircraftTypeCode = "SF50", aircraftName = "Cirrus Vision Jet")
        viewModel().resolveWatchRoute(unknown).shouldBeNull()
    }

    @Test
    fun `an unreadable fleet is no route rather than a crash on arrival`() = runTest {
        val model = viewModel(fleet = { throw IllegalStateException("database closed") })
        model.resolveWatchRoute(fromTheWatch).shouldBeNull()
    }
}
