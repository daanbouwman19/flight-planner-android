package com.github.daanbouwman.flightplanner.handoff

/**
 * Which look the user has chosen, as it travels between the two apps.
 *
 * A deliberate mirror of `:core:designsystem`'s `ThemeChoice`, name for name,
 * and **not** that enum: `ThemeChoice` lives in a module built on the phone's
 * `androidx.compose.material3`, which a watch APK must not carry (see CLAUDE.md
 * on the watch's Material surface). `:core:handoff` depends on nothing, which is
 * the whole reason both APKs can compile against it.
 *
 * The cost of a second enum is that the two can drift apart silently. That is
 * what `WatchThemeContractTest` in `:app` is for — it is the one place that sees
 * both, and it fails if a constant is added, removed or renamed on either side.
 */
enum class WatchThemeChoice { SYSTEM, LIGHT, DARK, COCKPIT, CHART }

/**
 * The phone's appearance, as much of it as is meaningful on a watch.
 *
 * [systemDark] is the phone's answer to "is the system in dark mode", carried
 * because [WatchThemeChoice.SYSTEM] has no meaning on its own and the watch
 * cannot work it out for itself: Wear OS has no light/dark system setting to
 * consult. Without it a phone set to SYSTEM would leave the watch guessing, and
 * the point of this is that the two are in sync.
 *
 * Dynamic colour is deliberately **not** carried. It is a scheme derived from
 * the phone's wallpaper, and a watch has no wallpaper to derive the same one
 * from; a watch cannot reproduce it, only approximate it, and an approximation
 * of a colour that exists to match a specific home screen is worse than the
 * brand scheme it falls back to. See `WearFlightPlannerTheme`.
 */
data class WatchThemeState(
    val choice: WatchThemeChoice = WatchThemeChoice.SYSTEM,
    val systemDark: Boolean = true,
) {
    /**
     * Whether this resolves to a dark look.
     *
     * The same rule as `ThemeChoice.isDark`, ported field for field: only
     * [WatchThemeChoice.SYSTEM] consults [systemDark], and Cockpit and Chart are
     * a night panel and a paper chart rather than a preference.
     */
    val isDark: Boolean
        get() = when (choice) {
            WatchThemeChoice.SYSTEM -> systemDark
            WatchThemeChoice.LIGHT -> false
            WatchThemeChoice.DARK -> true
            WatchThemeChoice.COCKPIT -> true
            WatchThemeChoice.CHART -> false
        }
}

/**
 * Where the phone puts its appearance and where the watch looks for it.
 *
 * This is a Wearable Data Layer item rather than a message, which is the whole
 * reason it works: a `DataItem` is replicated to the watch and **persists
 * there**, so the watch reads the last known theme at launch with the phone out
 * of range, off, or simply not paired at that moment. A message would only
 * arrive if both were connected at the instant the user changed the setting,
 * which is the one moment the wearer is looking at the phone rather than the
 * watch.
 *
 * It is also why nothing here is cached again on the watch side: the Data Layer
 * is already a local store, and a second copy would be a second answer to keep
 * in sync.
 */
object WatchThemeSync {

    /**
     * The item's path. Namespaced, because the Data Layer is a per-node space
     * shared with every other app the watch is paired with.
     */
    const val PATH: String = "/flightplanner/theme"

    /** The [WatchThemeChoice] name. */
    const val KEY_CHOICE: String = "choice"

    /** The phone's system dark-mode answer. See [WatchThemeState.systemDark]. */
    const val KEY_SYSTEM_DARK: String = "system_dark"

    /**
     * What the watch shows before the phone has ever told it anything: a dark
     * face. Chosen over the brand light scheme because a watch that has never
     * been paired is more likely to be in a cockpit at night than on a desk,
     * and because an OLED watch showing white at 3 a.m. is a torch.
     */
    val Unsynced: WatchThemeState = WatchThemeState(WatchThemeChoice.SYSTEM, systemDark = true)

    /**
     * Reads a stored item back.
     *
     * Tolerant on purpose, in both directions: a phone running a newer build may
     * name a choice this watch has never heard of, and a watch may read an item
     * written before a key existed. Either way the wearer gets a working face in
     * the default look rather than a crash on launch — a watch app that dies at
     * startup just puts the launcher back.
     */
    fun read(choice: String?, systemDark: Boolean?): WatchThemeState = WatchThemeState(
        choice = WatchThemeChoice.entries.firstOrNull { it.name == choice } ?: Unsynced.choice,
        systemDark = systemDark ?: Unsynced.systemDark,
    )
}
