package com.github.daanbouwman.flightplanner.startup

import io.kotest.matchers.shouldBe
import kotlin.test.Test

/**
 * The splash's keep-on-screen rule. The case it exists for: a first-launch
 * database copy that outlived the 800 ms deadline used to release the splash
 * and then block the main thread for the rest of the copy behind Plan's first
 * frame. The install is now waited for without the deadline; the index and the
 * settings still respect it, because for them the deadline is what bounds a
 * broken asset.
 */
class SplashHoldTest {

    private val deadline = 1_800L

    @Test
    fun `nothing settled before the deadline holds the splash`() {
        splashShouldHold(
            indexSettled = false,
            installSettled = false,
            settingsLoaded = false,
            now = 1_000L,
            deadline = deadline,
        ) shouldBe true
    }

    @Test
    fun `everything settled releases the splash at once, deadline or not`() {
        splashShouldHold(true, true, true, now = 1_000L, deadline = deadline) shouldBe false
        splashShouldHold(true, true, true, now = 5_000L, deadline = deadline) shouldBe false
    }

    @Test
    fun `an install still running holds the splash past the deadline`() {
        // The first-launch copy: index and settings are long settled, the
        // ~30 MB copy is not, and the deadline has gone by. Releasing here is
        // what put runBlocking on the main thread behind a half-drawn Plan.
        splashShouldHold(
            indexSettled = true,
            installSettled = false,
            settingsLoaded = true,
            now = 4_000L,
            deadline = deadline,
        ) shouldBe true
    }

    @Test
    fun `the index and the settings are still bounded by the deadline`() {
        // A corrupt index asset never settles; past the deadline the app appears
        // and shows skeletons rather than a splash forever.
        splashShouldHold(indexSettled = false, installSettled = true, settingsLoaded = true, now = 1_000L, deadline = deadline) shouldBe true
        splashShouldHold(indexSettled = false, installSettled = true, settingsLoaded = true, now = 1_800L, deadline = deadline) shouldBe false
        splashShouldHold(indexSettled = true, installSettled = true, settingsLoaded = false, now = 1_000L, deadline = deadline) shouldBe true
        splashShouldHold(indexSettled = true, installSettled = true, settingsLoaded = false, now = 2_500L, deadline = deadline) shouldBe false
    }

    @Test
    fun `a settled install is a settled install whether it succeeded or failed`() {
        // `isSettled` is true for Ready and for Failed alike, so the predicate
        // sees only the boolean: a failed install releases the splash so the
        // self-check screen can be reached to report it. Asserted through the
        // same boolean the activity reads.
        splashShouldHold(indexSettled = true, installSettled = true, settingsLoaded = true, now = 100L, deadline = deadline) shouldBe false
    }
}
