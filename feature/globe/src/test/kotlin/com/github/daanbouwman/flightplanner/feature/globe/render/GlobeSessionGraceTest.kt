package com.github.daanbouwman.flightplanner.feature.globe.render

import io.kotest.matchers.shouldBe
import kotlin.test.Test

/**
 * The teardown grace a releasing view asks for.
 *
 * The `Handler`/`Looper` teardown itself cannot run on the JVM — that timing is a
 * device check — but the one decision this module makes about it can: a globe the
 * host disposes and recomposes as it scrolls (a `LazyColumn` item, which the
 * Stats visited-network card is) must outlive a scroll to the end of the list and
 * back, where the 2 s default let it rebuild the engine and the 32 MB atlas on
 * the main thread.
 */
class GlobeSessionGraceTest {

    @Test
    fun `an embedded globe gets the longer grace`() {
        val listGrace = GlobeSession.graceFor(embedded = true)
        val default = GlobeSession.graceFor(embedded = false)
        (listGrace > default) shouldBe true
    }

    @Test
    fun `everything else keeps the prompt default`() {
        // A navigation disposes the outgoing screen just after composing the
        // incoming one, so the default only has to cover that overlap; backing
        // out of the route detail should give the 32 MB back quickly.
        GlobeSession.graceFor(embedded = false) shouldBe 2_000L
    }
}
