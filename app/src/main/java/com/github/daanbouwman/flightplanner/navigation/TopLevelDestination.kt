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
) {
    PLAN(Destination.Plan, R.string.destination_plan, R.drawable.ic_nav_plan),
    LOGBOOK(Destination.Logbook, R.string.destination_logbook, R.drawable.ic_nav_logbook),
    FLEET(Destination.Fleet, R.string.destination_fleet, R.drawable.ic_nav_fleet),
    AIRPORTS(Destination.Airports, R.string.destination_airports, R.drawable.ic_nav_airports),
    STATS(Destination.Stats, R.string.destination_stats, R.drawable.ic_nav_stats),
    SETTINGS(Destination.Settings, R.string.destination_settings, R.drawable.ic_nav_settings),
}
