package com.github.daanbouwman.flightplanner.settings

/**
 * How distances, runway lengths and cruise speeds are displayed.
 *
 * Unlike [com.github.daanbouwman.flightplanner.core.designsystem.theme.ThemeChoice],
 * this has no reason to live in `:core:designsystem` — nothing there draws a
 * number, so it stays where it is read: display formatting in `:app`
 * ([com.github.daanbouwman.flightplanner.ui.Figures]).
 *
 * There is no Rust precedent for this choice; the desktop app has no unit
 * toggle either. [AVIATION] is the default because it matches the app's
 * existing, un-toggleable behaviour.
 */
enum class UnitSystem {
    /** Nautical miles, feet, knots — the units aviation charts use. */
    AVIATION,

    /** Kilometres, metres, kilometres per hour. */
    METRIC,
}
