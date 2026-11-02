@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.github.daanbouwman.flightplanner.wear.route

import app.cash.turbine.test
import com.github.daanbouwman.flightplanner.mapscene.RouteMapScene
import com.github.daanbouwman.flightplanner.model.AircraftSpec
import com.github.daanbouwman.flightplanner.routing.AirportIndex
import com.github.daanbouwman.flightplanner.routing.WorldOutline
import com.github.daanbouwman.flightplanner.wear.handoff.HandoffResult
import com.github.daanbouwman.flightplanner.wear.map.WatchFace
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
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
        buildScene: (WatchRouteCard, WorldOutline, WatchFace) -> RouteMapScene? = { _, _, _ -> null },
    ) = WatchRouteFeedViewModel(
        loadIndex = index,
        loadFleet = fleet,
        loadOutline = outline,
        handoff = handoff,
        generatorDispatcher = dispatcher,
        buildScene = buildScene,
    )

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

    // --- The maps, built ahead of the wearer ---

    private val face = WatchFace(extentPx = 480, density = 2.125f)

    /** One face built: whose map, for which face, and what the feed was showing at the time. */
    private class Build(val route: String, val face: WatchFace, val shown: WatchRouteFeedState)

    /**
     * A view model whose scene builds are recorded. The builder returns no scene
     * — the cache remembers "nothing to draw" as readily as a scene — so this
     * counts builds without a renderer.
     */
    private fun recorded(): Pair<WatchRouteFeedViewModel, List<Build>> {
        val builds = mutableListOf<Build>()
        lateinit var model: WatchRouteFeedViewModel
        model = viewModel(buildScene = { route, _, face -> builds += Build(route.key(), face, model.state.value); null })
        return model to builds
    }

    private fun WatchRouteFeedState.keysOf(vararg pages: Int): List<String> {
        val routes = shouldBeInstanceOf<WatchRouteFeedState.Ready>().routes
        return pages.map { routes[it].key() }
    }

    /**
     * The face is measured on the loading screen, long before the routes exist.
     * The first page's map is then built before the feed is shown, so the
     * feed's first frame does not also build it, and the next two follow.
     */
    @Test
    fun `a face measured while loading has its first map built before the feed is shown`() = runTest(dispatcher) {
        val (model, builds) = recorded()
        model.onFaceMeasured(face)
        advanceUntilIdle()

        builds.first().route shouldBe model.state.value.keysOf(0).single()
        builds.first().shown shouldBe WatchRouteFeedState.Loading
        builds.map { it.route } shouldBe model.state.value.keysOf(0, 1, 2).distinct()
        builds.all { it.face == face } shouldBe true
    }

    @Test
    fun `nothing is built until the face is known, then the settled page and its neighbours are`() = runTest(dispatcher) {
        val (model, builds) = recorded()
        advanceUntilIdle()
        model.onPageSettled(3)
        advanceUntilIdle()
        builds shouldHaveSize 0

        model.onFaceMeasured(face)
        advanceUntilIdle()

        builds.map { it.route } shouldBe model.state.value.keysOf(3, 4, 2, 5).distinct()
    }

    @Test
    fun `settling on a page builds the one after it, the one before and the one after that`() = runTest(dispatcher) {
        val (model, builds) = recorded()
        model.onFaceMeasured(face)
        advanceUntilIdle()
        val before = builds.map { it.route }.toSet()

        model.onPageSettled(8)
        advanceUntilIdle()

        builds.map { it.route }.drop(before.size) shouldBe
            model.state.value.keysOf(8, 9, 7, 10).distinct().filterNot { it in before }
    }

    /**
     * A repeat report is not guarded against: it prefetches the window again,
     * and that has to cost nothing. So this fails if an equal face is not the
     * same key, or if a prefetch rebuilds a page it already has.
     */
    @Test
    fun `the same face reported twice builds nothing twice`() = runTest(dispatcher) {
        val (model, builds) = recorded()
        model.onFaceMeasured(face)
        advanceUntilIdle()
        val count = builds.size

        model.onFaceMeasured(face.copy())
        advanceUntilIdle()

        builds shouldHaveSize count
    }

    /**
     * A rotary flick settles on page after page; each settle replaces the last
     * one's prefetch, so the maps built are the ones around where the wearer
     * stopped, not a queue of every page passed on the way.
     */
    @Test
    fun `a newer settle cancels the prefetch of the one before it`() = runTest(dispatcher) {
        val (model, builds) = recorded()
        model.onFaceMeasured(face)
        advanceUntilIdle()
        val before = builds.map { it.route }.toSet()

        model.onPageSettled(3)
        model.onPageSettled(12)
        advanceUntilIdle()

        builds.map { it.route }.drop(before.size) shouldBe
            model.state.value.keysOf(12, 13, 11, 14).distinct().filterNot { it in before }
    }

    /**
     * The settle that cancels a prefetch can land while it is building a page.
     * That build finishes and is kept — the wearer may swipe straight back to
     * it — and nothing after it in the old window is built.
     */
    @Test
    fun `a build the cancelled prefetch had started is finished and kept`() = runTest(dispatcher) {
        val builds = mutableListOf<String>()
        lateinit var model: WatchRouteFeedViewModel
        // The page whose build the wearer interrupts, and where they go instead.
        var interruptOn: String? = null
        var movedTo = -1
        model = viewModel(
            buildScene = { route, _, _ ->
                builds += route.key()
                if (route.key() == interruptOn) {
                    interruptOn = null
                    model.onPageSettled(movedTo)
                }
                null
            },
        )
        model.onFaceMeasured(face)
        advanceUntilIdle()
        val before = builds.toSet()
        val ready = model.state.value.shouldBeInstanceOf<WatchRouteFeedState.Ready>()

        // The generator is random and the test world small, so routes repeat:
        // settle on the first page whose map does not exist yet, so the
        // prefetch's first act is to build it. Far enough down that the page
        // moved to shares no page with its window, and short of a top-up.
        val page = (3..8).first { ready.routes[it].key() !in before }
        movedTo = page + 6
        interruptOn = ready.routes[page].key()

        model.onPageSettled(page)
        advanceUntilIdle()

        interruptOn shouldBe null
        // The interrupted page, then the new window's; the rest of the old
        // window — the page after, the one before, the one after that — is not.
        builds.drop(before.size) shouldBe
            (listOf(ready.routes[page].key()) + ready.keysOf(movedTo, movedTo + 1, movedTo - 1, movedTo + 2))
                .distinct()
                .filterNot { it in before }

        // Kept: asking for that page again is a hit.
        val count = builds.size
        model.scenes.scene(ready.routes[page], ready.outline, face)
        builds shouldHaveSize count
    }
}
