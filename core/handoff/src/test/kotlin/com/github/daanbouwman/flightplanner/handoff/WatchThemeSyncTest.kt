package com.github.daanbouwman.flightplanner.handoff

import io.kotest.matchers.shouldBe
import kotlin.test.Test

/** The theme contract both apps compile against: what survives a round trip, and what a bad item does. */
class WatchThemeSyncTest {

    @Test
    fun `every choice survives being written and read back`() {
        for (choice in WatchThemeChoice.entries) {
            WatchThemeSync.read(choice.name, systemDark = false) shouldBe
                WatchThemeState(choice, systemDark = false)
        }
    }

    /**
     * The tolerance that keeps a watch running against a phone it does not
     * understand. A newer phone naming a choice this build has never heard of
     * must land on the default, not on an exception thrown at launch.
     */
    @Test
    fun `an unknown or missing choice falls back rather than failing`() {
        WatchThemeSync.read("NEON", systemDark = true) shouldBe WatchThemeSync.Unsynced
        WatchThemeSync.read(null, systemDark = true) shouldBe WatchThemeSync.Unsynced
        WatchThemeSync.read("system", systemDark = true) shouldBe WatchThemeSync.Unsynced
    }

    /** A missing flag is not a false one: an item written before this key existed must read as the default. */
    @Test
    fun `a missing dark flag takes the default rather than false`() {
        WatchThemeSync.read(WatchThemeChoice.SYSTEM.name, systemDark = null).systemDark shouldBe true
    }

    @Test
    fun `only SYSTEM consults the phone's dark mode`() {
        WatchThemeState(WatchThemeChoice.SYSTEM, systemDark = true).isDark shouldBe true
        WatchThemeState(WatchThemeChoice.SYSTEM, systemDark = false).isDark shouldBe false

        // The other four are a fixed answer, whatever the phone's system setting says.
        for (systemDark in listOf(true, false)) {
            WatchThemeState(WatchThemeChoice.LIGHT, systemDark).isDark shouldBe false
            WatchThemeState(WatchThemeChoice.DARK, systemDark).isDark shouldBe true
            WatchThemeState(WatchThemeChoice.COCKPIT, systemDark).isDark shouldBe true
            WatchThemeState(WatchThemeChoice.CHART, systemDark).isDark shouldBe false
        }
    }
}
