package com.github.daanbouwman.flightplanner.feature.globe.tile

import io.kotest.matchers.shouldBe
import kotlin.test.Test

/**
 * When the globe may say "imagery unavailable offline", and when it must not.
 * The predicate is the whole of what the overlay is gated on, so every branch
 * of it is a user-visible sentence being shown or withheld.
 */
class TileStatsTest {

    @Test
    fun `nothing decoded and the last failure a network one is offline`() {
        TileStats(errors = 21, lastFailure = TileFailure.Network).imageryUnreachable shouldBe true
    }

    @Test
    fun `a fresh loader that has not failed yet is not offline`() {
        // Before the first request comes back there is nothing to say.
        TileStats().imageryUnreachable shouldBe false
    }

    @Test
    fun `one decoded tile means the globe draws, whatever failed since`() {
        // Offline with a warm disk cache: the pinned floor came from disk and
        // the deeper tiles fail. That is a working globe, not an unavailable one.
        TileStats(decoded = 1, errors = 40, lastFailure = TileFailure.Network).imageryUnreachable shouldBe false
    }

    @Test
    fun `a provider that answers with errors is not the user's connection`() {
        TileStats(errors = 21, lastFailure = TileFailure.Server).imageryUnreachable shouldBe false
        TileStats(errors = 21, lastFailure = TileFailure.Decode).imageryUnreachable shouldBe false
    }

    @Test
    fun `a success clears the failure, so the last word is the newest`() {
        // What the loader records after a decode: null, not the failure before it.
        TileStats(decoded = 0, errors = 3, lastFailure = null).imageryUnreachable shouldBe false
    }
}
