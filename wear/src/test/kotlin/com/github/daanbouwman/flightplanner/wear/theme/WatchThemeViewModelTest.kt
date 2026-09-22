@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.github.daanbouwman.flightplanner.wear.theme

import com.github.daanbouwman.flightplanner.handoff.WatchThemeChoice
import com.github.daanbouwman.flightplanner.handoff.WatchThemeState
import com.github.daanbouwman.flightplanner.handoff.WatchThemeSync
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * What the face is coloured with before, during and after the phone tells it
 * anything. Composed against the internal seam constructor, so no Play Services
 * and no paired phone.
 */
class WatchThemeViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    /**
     * The watch has no splash screen to hold, so the first frame is drawn in the
     * default rather than waited for — the opposite of the phone, which holds
     * its splash precisely to avoid drawing in the wrong colours once.
     */
    @Test
    fun `starts on the default before anything has arrived`() = runTest(dispatcher) {
        val model = WatchThemeViewModel(theme = MutableSharedFlow())
        model.theme.value shouldBe WatchThemeSync.Unsynced
    }

    @Test
    fun `takes what the phone published`() = runTest(dispatcher) {
        val chart = WatchThemeState(WatchThemeChoice.CHART, systemDark = true)
        val model = WatchThemeViewModel(theme = flow { emit(chart) })

        advanceUntilIdle()

        model.theme.value shouldBe chart
    }

    /** Changing the setting on the phone recolours the face without reopening it. */
    @Test
    fun `follows a change`() = runTest(dispatcher) {
        val published = MutableSharedFlow<WatchThemeState>(replay = 1)
        val model = WatchThemeViewModel(theme = published)

        published.emit(WatchThemeState(WatchThemeChoice.COCKPIT, systemDark = true))
        advanceUntilIdle()
        model.theme.value.choice shouldBe WatchThemeChoice.COCKPIT

        published.emit(WatchThemeState(WatchThemeChoice.LIGHT, systemDark = true))
        advanceUntilIdle()
        model.theme.value.choice shouldBe WatchThemeChoice.LIGHT
    }
}
