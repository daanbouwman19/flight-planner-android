package com.github.daanbouwman.flightplanner.wear.route

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import java.util.Collections
import java.util.concurrent.Executors
import kotlin.test.Test

/** Where a batch's work runs. */
class WatchRouteFeedTest {

    /**
     * Wording a card samples its great circle and resolves its airframe's
     * outline, and on the first batch initialises the whole generated outline
     * table. It used to run after the generator returned, on the caller's
     * context — the ViewModel's main thread — so this pins it to the generator's
     * dispatcher: a caller on another thread must never see it run there.
     */
    @Test
    fun `cards are worded on the generator's dispatcher, not the caller's`() {
        // Compared by identity, not by name: coroutine debug mode renames a
        // thread while a coroutine runs on it.
        val generatorThreads = Collections.synchronizedList(mutableListOf<Thread>())
        val pool = Executors.newSingleThreadExecutor { task -> Thread(task).also(generatorThreads::add) }
        try {
            val wordedOn = Collections.synchronizedList(mutableListOf<Thread>())
            val feed = WatchRouteFeed(
                index = testIndex,
                fleet = listOf(testAircraft()),
                dispatcher = pool.asCoroutineDispatcher(),
                word = { route, index ->
                    wordedOn += Thread.currentThread()
                    route.toCard(index)
                },
            )

            val batch = runBlocking { feed.nextBatch() }

            batch.shouldNotBeEmpty()
            wordedOn shouldHaveSize batch.size
            wordedOn.toSet() shouldBe generatorThreads.toSet()
        } finally {
            pool.shutdownNow()
        }
    }
}
