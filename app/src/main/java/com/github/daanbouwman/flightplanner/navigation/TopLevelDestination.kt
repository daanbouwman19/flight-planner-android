package com.github.daanbouwman.flightplanner.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.github.daanbouwman.flightplanner.R

/**
 * The navigation-bar entries, in bar order.
 *
 * Icons are local vector drawables. Compose ships none on this classpath —
 * `material-icons-extended` is deliberately not a dependency and material3 1.5
 * no longer brings `material-icons-core` in transitively — so drawing the seven
 * the app needs is both the only option and the better one: a paper plane for
 * Plan and an airframe for Fleet say more than two generic glyphs would.
 *
 * There is one icon per entry rather than a filled/outlined pair. Material 3
 * signals the current destination with the active indicator behind the icon, so
 * a second glyph would be restating what the indicator already says; a pair can
 * be added later without touching anything but this enum.
 */
enum class TopLevelDestination(
    val route: Destination.TopLevel,
    @param:StringRes val labelRes: Int,
    @param:DrawableRes val iconRes: Int,
    /**
     * Whether this destination gets a slot in the navigation bar.
     *
     * Only [SETTINGS] does not. It is the odd one out in kind: the other four
     * are places the content lives and the user moves between constantly, while
     * Settings is somewhere you go once and come back from. It lives in the app
     * bar instead, which is where it is reachable from every screen rather than
     * from a fifth of the bar.
     *
     * On wide screens (rail/drawer), it is included anyway since there is room
     * and it provides a stable home for the action.
     */
    val inNavigationBar: Boolean = true,
) {
    PLAN(Destination.Plan, R.string.destination_plan, R.drawable.ic_nav_plan),
    FLEET(Destination.Fleet, R.string.destination_fleet, R.drawable.ic_nav_fleet),
    LOGBOOK(Destination.Logbook, R.string.destination_logbook, R.drawable.ic_nav_logbook),
    STATS(Destination.Stats, R.string.destination_stats, R.drawable.ic_nav_stats),
    SETTINGS(
        Destination.Settings,
        R.string.destination_settings,
        R.drawable.ic_nav_settings,
        inNavigationBar = false,
    ),
    ;

    companion object {
        /** The destinations that appear in the bar, in bar order. */
        val inBar: List<TopLevelDestination> = entries.filter { it.inNavigationBar }

        /**
         * The destinations that appear in a rail or drawer on wide screens.
         *
         * Includes [SETTINGS], which on a wide screen has room to stay
         * permanently visible rather than hiding in every screen's app bar.
         */
        val inRail: List<TopLevelDestination> = entries
    }
}
