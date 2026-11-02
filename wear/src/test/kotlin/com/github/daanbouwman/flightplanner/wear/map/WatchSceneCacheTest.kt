package com.github.daanbouwman.flightplanner.wear.map

import com.github.daanbouwman.flightplanner.aircraftart.AircraftShape
import com.github.daanbouwman.flightplanner.routing.GeoArc
import com.github.daanbouwman.flightplanner.routing.WorldOutline
import com.github.daanbouwman.flightplanner.wear.route.WatchRouteCard
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test

/**
 * The watch's scene cache: a hit is never a rebuild, everything a scene depends
 * on is in its key, the oldest scene is the one that goes, two threads asking
 * at once get one scene, and a prefetch never builds what another thread is
 * already building — while a draw never waits for it.
 *
 * The "scene" here is a string naming what it was built from, so a test can
 * see which build it got back; the real one is a `RouteMapScene`, built by the
 * same cache through the same call.
 */
class WatchSceneCacheTest {

    private val face = WatchFace(extentPx = 480, density = 2.125f)
    private val outline = WorldOutline.Empty
    private val arc = GeoArc(doubleArrayOf(52.31, 51.47), doubleArrayOf(4.76, -0.45))

    private fun card(to: String, type: String = "A320", shape: AircraftShape? = null) = WatchRouteCard(
        departureIcao = "EHAM",
        destinationIcao = to,
        distanceNm = 200,
        distanceText = "200 NM",
        eteText = "0:45",
        aircraftName = "Test",
        aircraftTypeCode = type,
        arc = arc,
        shape = shape,
    )

    private fun shape(id: String) = AircraftShape(id, id, 100, 100, AircraftShape.Provenance.Drawn, "M0 0 L100 100 Z")

    private val builds = AtomicInteger()
    private val cache = WatchSceneCache<String>(capacity = 3) { route, _, built ->
        builds.incrementAndGet()
        "${route.key()}/${route.shape?.id}/${built.extentPx}@${built.density}"
    }

    @Test
    fun `a second look-up is served without a rebuild`() {
        val first = cache.get(card("EGLL"), outline, face)
        cache.get(card("EGLL"), outline, face) shouldBeSameInstanceAs first
        builds.get() shouldBe 1
    }

    /** The face the draw works out is a new object every time; it is the same key. */
    @Test
    fun `an equal face is the same scene`() {
        val first = cache.get(card("EGLL"), outline, face)
        cache.get(card("EGLL"), outline, face.copy()) shouldBeSameInstanceAs first
        cache.prefetch(card("EGLL"), outline, face.copy())
        builds.get() shouldBe 1
    }

    @Test
    fun `a prefetch keeps what it builds and skips what is already here`() {
        cache.prefetch(card("EGLL"), outline, face)
        cache.prefetch(card("EGLL"), outline, face)
        cache.get(card("EGLL"), outline, face) shouldBe "EHAM>EGLL@A320/null/480@2.125"
        builds.get() shouldBe 1
    }

    /**
     * A relaunched prefetch reaching a page the cancelled one is still building
     * must not build it again beside it — the start-up and top-up overlaps.
     */
    @Test
    fun `a prefetch skips a scene another thread is still building`() {
        blockingFirstBuild { cache, started, release ->
            val pool = Executors.newSingleThreadExecutor()
            try {
                val first = pool.submit<Unit> { cache.prefetch(card("EGLL"), outline, face) }
                started.await(10, TimeUnit.SECONDS) shouldBe true

                // Returns at once, without building, while the first is held.
                cache.prefetch(card("EGLL"), outline, face)
                builds.get() shouldBe 1

                release.countDown()
                first.get(10, TimeUnit.SECONDS)
                cache.get(card("EGLL"), outline, face)
                builds.get() shouldBe 1
            } finally {
                pool.shutdownNow()
            }
        }
    }

    /**
     * The draw never waits on another thread: a page caught mid-prefetch is
     * built by the draw itself, and both callers end up with the one kept.
     */
    @Test
    fun `a draw that catches a scene mid-prefetch builds it rather than wait`() {
        blockingFirstBuild { cache, started, release ->
            val pool = Executors.newSingleThreadExecutor()
            try {
                val first = pool.submit<Unit> { cache.prefetch(card("EGLL"), outline, face) }
                started.await(10, TimeUnit.SECONDS) shouldBe true

                // The prefetch is still held; if the draw waited for it, this
                // would never return.
                val drawn = cache.get(card("EGLL"), outline, face)
                builds.get() shouldBe 2

                release.countDown()
                first.get(10, TimeUnit.SECONDS)
                cache.get(card("EGLL"), outline, face) shouldBeSameInstanceAs drawn
                builds.get() shouldBe 2
            } finally {
                release.countDown()
                pool.shutdownNow()
            }
        }
    }

    /**
     * Runs [block] with a cache whose first build counts down the first latch
     * and then holds until the second is released; later builds return at once.
     * Every build is counted in [builds], as the shared cache's are.
     */
    private fun blockingFirstBuild(
        block: (WatchSceneCache<String>, CountDownLatch, CountDownLatch) -> Unit,
    ) {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val held = WatchSceneCache<String> { route, _, built ->
            if (builds.incrementAndGet() == 1) {
                started.countDown()
                release.await(10, TimeUnit.SECONDS)
            }
            "${route.key()}/${built.extentPx}#${builds.get()}"
        }
        block(held, started, release)
    }

    @Test
    fun `another face size or density is another scene`() {
        cache.get(card("EGLL"), outline, face)
        cache.get(card("EGLL"), outline, face.copy(extentPx = 454))
        cache.get(card("EGLL"), outline, face.copy(density = 2f))
        builds.get() shouldBe 3
    }

    @Test
    fun `two airframes sharing a type code are two scenes`() {
        // An untyped airframe is "ZZZZ" whatever it is, so the card key alone
        // would hand one airframe's outline to the other.
        val a = cache.get(card("EGLL", type = "ZZZZ", shape = shape("glider")), outline, face)
        val b = cache.get(card("EGLL", type = "ZZZZ", shape = shape("balloon")), outline, face)
        a shouldBe "EHAM>EGLL@ZZZZ/glider/480@2.125"
        b shouldBe "EHAM>EGLL@ZZZZ/balloon/480@2.125"
    }

    @Test
    fun `the least recently used scene is the one that goes`() {
        cache.get(card("EGLL"), outline, face)
        cache.get(card("EDDF"), outline, face)
        cache.get(card("LFPG"), outline, face)
        // Touch the oldest, so the next one in pushes out EDDF instead.
        cache.get(card("EGLL"), outline, face)
        cache.get(card("EKCH"), outline, face)
        builds.get() shouldBe 4

        cache.get(card("EGLL"), outline, face)
        builds.get() shouldBe 4
        cache.get(card("EDDF"), outline, face)
        builds.get() shouldBe 5
    }

    @Test
    fun `nothing to draw is remembered too`() {
        val calls = AtomicInteger()
        val empty = WatchSceneCache<String> { _, _, _ ->
            calls.incrementAndGet()
            null
        }
        empty.get(card("EGLL"), outline, face).shouldBeNull()
        empty.get(card("EGLL"), outline, face).shouldBeNull()
        calls.get() shouldBe 1
    }

    @Test
    fun `threads that miss together all get the one scene that was kept`() {
        val threads = 8
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(threads)
        try {
            val results = (1..threads).map {
                pool.submit<String> {
                    start.await()
                    cache.get(card("EGLL"), outline, face)
                }
            }
            start.countDown()
            val scenes = results.map { it.get(10, TimeUnit.SECONDS) }

            scenes shouldHaveSize threads
            scenes.forEach { it shouldBeSameInstanceAs scenes.first() }
        } finally {
            pool.shutdownNow()
        }
    }
}
