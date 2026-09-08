package com.github.daanbouwman.flightplanner.core.designsystem.theme

import io.kotest.matchers.shouldBe
import kotlin.test.Test

/**
 * The arbitration behind [SystemBarsOverMedia], and the regression that made it
 * necessary.
 *
 * The mechanism used to be a single shared `MutableState<Boolean>` that every
 * caller wrote `false` into on dispose. That is fine while there is one caller
 * and wrong the moment there are two whose compositions overlap — which is
 * exactly what a navigation transition is, because the incoming screen is
 * composed before the outgoing one is disposed. On a device the immersive globe
 * asked for light glyphs, the route detail underneath it then disposed and wrote
 * `false` over that request, and the status bar sat dark over full-bleed
 * satellite imagery until the app was restarted.
 *
 * So the rule this class has to hold is not "the last writer wins" but "is
 * anybody still asking" — and [oneCallerLoweringDoesNotClearAnother] is that
 * sentence written as a test.
 */
class BarsOverMediaRequestsTest {

    @Test
    fun `no requests means not raised`() {
        BarsOverMediaRequests().isRaised shouldBe false
    }

    @Test
    fun `one caller raises and lowers`() {
        val requests = BarsOverMediaRequests()

        requests.raise()
        requests.isRaised shouldBe true

        requests.lower()
        requests.isRaised shouldBe false
    }

    @Test
    fun `one caller lowering does not clear another`() {
        // The transition, in order: the hero is asking, the immersive screen
        // composes and asks too, and only then does the hero's own composition
        // go away. The dark status bar over the immersive globe was this
        // sequence ending in `false`.
        val requests = BarsOverMediaRequests()

        requests.raise() // route detail's hero, over imagery
        requests.raise() // the immersive globe, composed over it
        requests.lower() // the hero's dispose, as the transition settles

        requests.isRaised shouldBe true
    }

    @Test
    fun `all callers lowering clears it`() {
        val requests = BarsOverMediaRequests()

        requests.raise()
        requests.raise()
        requests.lower()
        requests.lower()

        requests.isRaised shouldBe false
    }

    @Test
    fun `lowering without a matching raise does not go negative`() {
        // A dispose that never had a raise — a caller composed with `active`
        // false, or an effect torn down twice — must not leave the count owing
        // a raise, or the next screen that genuinely wants light glyphs is
        // ignored for reasons nothing on screen can explain.
        val requests = BarsOverMediaRequests()

        requests.lower()
        requests.raise()

        requests.isRaised shouldBe true
    }

    @Test
    fun `the same caller flipping off and on ends raised`() {
        // RouteDetailScreen's own `active` steps as the hero scrolls under the
        // status bar and back out. Each flip restarts its DisposableEffect,
        // which is a lower followed by a raise.
        val requests = BarsOverMediaRequests()

        requests.raise()
        requests.lower()
        requests.raise()

        requests.isRaised shouldBe true
    }
}
