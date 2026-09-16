package com.github.daanbouwman.flightplanner.launch

import com.github.daanbouwman.flightplanner.model.FlightRecord
import com.github.daanbouwman.flightplanner.navigation.Destination
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import java.io.IOException
import kotlin.test.Test

/**
 * The hand-off between the Activity and the NavHost, and the "Last route"
 * resolution order. Composed against the internal seam constructor, so no
 * DataStore and no Room.
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

    private fun viewModel(
        lastRoute: suspend () -> Destination.RouteDetail? = { null },
        latestFlight: suspend () -> FlightRecord? = { null },
    ) = LaunchViewModel(readLastRoute = lastRoute, latestFlight = latestFlight)

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
}
