package com.github.daanbouwman.flightplanner.watch

import com.github.daanbouwman.flightplanner.core.designsystem.theme.ThemeChoice
import com.github.daanbouwman.flightplanner.core.designsystem.theme.isDark
import com.github.daanbouwman.flightplanner.handoff.WatchThemeChoice
import io.kotest.matchers.shouldBe
import kotlin.test.Test

/**
 * The one place that sees both theme enums.
 *
 * `ThemeChoice` lives in `:core:designsystem`, which is built on the phone's
 * Material and must never reach a watch APK, so `:core:handoff` mirrors it as
 * `WatchThemeChoice` — and a mirror can drift. Half of that is already the
 * compiler's problem: `watchThemeStateOf`'s `when` is exhaustive, so a new
 * `ThemeChoice` constant will not build until it is mapped. This covers the
 * other half, which nothing else can see: a constant added, removed or renamed
 * on the *handoff* side, and the two `isDark` rules disagreeing.
 */
class WatchThemeContractTest {

    @Test
    fun `the two enums name the same five choices, in the same order`() {
        WatchThemeChoice.entries.map { it.name } shouldBe ThemeChoice.entries.map { it.name }
    }

    @Test
    fun `every phone choice maps to its own name`() {
        for (choice in ThemeChoice.entries) {
            watchThemeStateOf(choice, systemDark = true).choice.name shouldBe choice.name
        }
    }

    /**
     * The rule itself is written twice — `ThemeChoice.isDark(systemDark)` on the
     * phone, `WatchThemeState.isDark` in the handoff — because the watch cannot
     * see the first. If they ever disagree the watch shows a light face where
     * the phone shows a dark one, which is precisely the thing this feature
     * exists to prevent.
     */
    @Test
    fun `both sides resolve darkness identically`() {
        for (choice in ThemeChoice.entries) {
            for (systemDark in listOf(true, false)) {
                watchThemeStateOf(choice, systemDark).isDark shouldBe choice.isDark(systemDark)
            }
        }
    }

    @Test
    fun `the phone's system dark mode is carried as it stands`() {
        watchThemeStateOf(ThemeChoice.SYSTEM, systemDark = false).systemDark shouldBe false
        watchThemeStateOf(ThemeChoice.COCKPIT, systemDark = false).systemDark shouldBe false
    }
}
