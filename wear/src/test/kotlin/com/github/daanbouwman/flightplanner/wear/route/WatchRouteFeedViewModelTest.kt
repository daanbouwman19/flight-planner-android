@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.github.daanbouwman.flightplanner.wear.route

import app.cash.turbine.test
import com.github.daanbouwman.flightplanner.model.AircraftSpec
import com.github.daanbouwman.flightplanner.routing.AirportIndex
import com.github.daanbouwman.flightplanner.routing.WorldOutline
import com.github.daanbouwman.flightplanner.wear.handoff.HandoffResult
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import java.time.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * The feed's state machine: what the face shows while the assets load, what it
 * shows when they will not, that swiping near the end brings more routes, and
 * that a tap reports back.
 *
 * Composed against the internal seam constructor, so no assets, no Hilt and no
 * paired phone. The generator runs on the test dispatcher too, which is what
 * makes a batch visible to `advanceUntilIdle` rather than something to sleep
 * and hope for — see `RouteGenerator`'s own KDoc on that parameter.
 */
class WatchRouteFeedViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private val testFleet = listOf(
        testAircraft(id = 1),
        testAircraft(id = 2, manufacturer = "Boeing", variant = "737-800", typeCode = "B738", cruiseKt = 450),
    )

    private fun viewModel(
        index: suspend () -> AirportIndex = { testIndex },
        fleet: suspend () -> List<AircraftSpec> = { testFleet },
        outline: suspend () -> WorldOutline = { WorldOutline.Empty },
        handoff: suspend (WatchRouteCard) -> HandoffResult = { HandoffResult.Sent },
        leadWithChallenge: Boolean = false,
    ) = WatchRouteFeedViewModel(
        loadIndex = index,
        loadFleet = fleet,
        loadOutline = outline,
        handoff = handoff,
        generatorDispatcher = dispatcher,
        leadWithChallenge = leadWithChallenge,
        today = { challengeDay },
    )

    private val challengeDay = LocalDate.of(2026, 9, 24)

    @Test
    fun `starts loading and settles on a first batch`() = runTest(dispatcher) {
        val model = viewModel()
        model.state.value shouldBe WatchRouteFeedState.Loading

        advanceUntilIdle()

        val ready = model.state.value.shouldBeInstanceOf<WatchRouteFeedState.Ready>()
        ready.routes shouldHaveSize WatchRouteFeed.BATCH_SIZE
    }

    /**
     * An asset that will not read is the wearer's only real failure mode here,
     * and it must reach the face as a state rather than as a crash: a watch app
     * that dies on launch just puts the launcher back.
     */
    @Test
    fun `an unreadable index is unavailable rather than a crash`() = runTest(dispatcher) {
        val model = viewModel(index = { throw IllegalStateException("asset truncated") })
        advanceUntilIdle()
        model.state.value shouldBe WatchRouteFeedState.Unavailable
    }

    @Test
    fun `an empty fleet is unavailable`() = runTest(dispatcher) {
        val model = viewModel(fleet = { emptyList() })
        advanceUntilIdle()
        model.state.value shouldBe WatchRouteFeedState.Unavailable
    }

    /**
     * The design is "swipe up for the next route", which has no end — so
     * arriving near the bottom of the loaded batch has to bring more.
     */
    @Test
    fun `settling near the end appends another batch`() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()

        val before = model.state.value.shouldBeInstanceOf<WatchRouteFeedState.Ready>().routes.size
        model.onPageSettled(before - WatchRouteFeed.PREFETCH_MARGIN)
        advanceUntilIdle()

        model.state.value.shouldBeInstanceOf<WatchRouteFeedState.Ready>()
            .routes shouldHaveSize before + WatchRouteFeed.BATCH_SIZE
    }

    @Test
    fun `settling early changes nothing`() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()

        val before = model.state.value.shouldBeInstanceOf<WatchRouteFeedState.Ready>().routes.size
        model.onPageSettled(0)
        advanceUntilIdle()

        model.state.value.shouldBeInstanceOf<WatchRouteFeedState.Ready>().routes shouldHaveSize before
    }

    /**
     * Two settles in a row — a flick that stops, then nudges — must not start
     * two generates and append two batches for one arrival.
     */
    @Test
    fun `a second settle while a top-up is in flight does not double it`() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()

        val before = model.state.value.shouldBeInstanceOf<WatchRouteFeedState.Ready>().routes.size
        model.onPageSettled(before - 1)
        model.onPageSettled(before - 1)
        advanceUntilIdle()

        model.state.value.shouldBeInstanceOf<WatchRouteFeedState.Ready>()
            .routes shouldHaveSize before + WatchRouteFeed.BATCH_SIZE
    }

    @Test
    fun `a tap reports what came of it`() = runTest(dispatcher) {
        val model = viewModel(handoff = { HandoffResult.Failed })
        advanceUntilIdle()
        val card = model.state.value.shouldBeInstanceOf<WatchRouteFeedState.Ready>().routes.first()

        model.handoffs.test {
            // `handoffs` replays nothing, so the collector has to be subscribed
            // before the tap: an event delivered to nobody is simply gone.
            advanceUntilIdle()
            model.openOnPhone(card)
            advanceUntilIdle()
            awaitItem() shouldBe HandoffResult.Failed
        }
    }

    /** A handoff that throws is a failure to report, not one to propagate. */
    @Test
    fun `a handoff that throws still reports a failure`() = runTest(dispatcher) {
        val model = viewModel(handoff = { throw IllegalStateException("no companion app") })
        advanceUntilIdle()
        val card = model.state.value.shouldBeInstanceOf<WatchRouteFeedState.Ready>().routes.first()

        model.handoffs.test {
            advanceUntilIdle()
            model.openOnPhone(card)
            advanceUntilIdle()
            awaitItem() shouldBe HandoffResult.Failed
        }
    }

    /**
     * The tile's tap: the route the tile showed is the page the face opens on,
     * so the wearer lands on what they tapped rather than on a fresh batch.
     */
    @Test
    fun `opened from the tile, the first page is the challenge`() = runTest(dispatcher) {
        val model = viewModel(leadWithChallenge = true)
        advanceUntilIdle()

        val routes = model.state.value.shouldBeInstanceOf<WatchRouteFeedState.Ready>().routes
        val challenge = WatchRouteFeed(testIndex, testFleet, dispatcher).challengeFor(challengeDay)
        // Compared by key rather than whole card: `GeoArc` holds arrays and is not
        // a data class, so two cards with identical arcs are never `equals`.
        routes.first().key() shouldBe challenge?.key()
        // Keyed pages: the challenge must not appear a second time behind itself.
        routes.drop(1).map { it.key() } shouldNotContain challenge?.key()
    }

    @Test
    fun `opened from the launcher, the challenge does not lead`() = runTest(dispatcher) {
        val model = viewModel(leadWithChallenge = false)
        advanceUntilIdle()

        model.state.value.shouldBeInstanceOf<WatchRouteFeedState.Ready>()
            .routes shouldHaveSize WatchRouteFeed.BATCH_SIZE
    }
}
