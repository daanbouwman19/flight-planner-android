package com.github.daanbouwman.flightplanner.wear.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme
import com.github.daanbouwman.flightplanner.handoff.WatchThemeChoice
import com.github.daanbouwman.flightplanner.handoff.WatchThemeState

/**
 * The watch app's colours, taken from the phone.
 *
 * Each scheme below is its `:core:designsystem` counterpart, ported role for
 * role rather than reinvented, so the two apps are recognisably one product: the
 * same avgas blue, the same runway amber, the same paper and ink. They are
 * copied rather than depended on because `ColorScheme` here is
 * `androidx.wear.compose.material3`'s, a **different class** from the phone's,
 * in a module a watch APK must not carry — see CLAUDE.md on the watch's Material
 * surface. Only the roles this app paints are named; everything else falls back
 * to Wear Compose's own defaults, so a component added later still lands
 * somewhere sensible rather than on an uninitialised colour.
 *
 * ### Two divergences from the phone, both about the device
 *
 * **The dark backgrounds are true black.** The phone's brand dark sits on
 * `#111319` and Cockpit on `#110A02`; on this OLED display an unlit pixel costs
 * nothing and a near-black one does, on a screen that is on the wearer's wrist
 * all day. It also lets the round bezel disappear, which the full-bleed map
 * wants. Chart is untouched: its identity is a specific paper, and a paper chart
 * that went black would simply be a different theme.
 *
 * **Dynamic colour is not reproduced.** The phone can derive its scheme from the
 * wallpaper; a watch has no wallpaper to derive the same one from, so a phone on
 * dynamic colour lands on the brand scheme of the matching tone here. That is
 * the one case where the two are deliberately not identical, and approximating a
 * palette that exists to match a specific home screen would be worse than
 * falling back cleanly. [WatchThemeState] carries no dynamic-colour flag for
 * this reason.
 */
internal val WearBrandDarkColorScheme: ColorScheme = ColorScheme(
    primary = Color(0xFFADC6FF),
    onPrimary = Color(0xFF00315D),
    background = Color(0xFF000000),
    onBackground = Color(0xFFE0E2EA),
    onSurface = Color(0xFFE0E2EA),
    onSurfaceVariant = Color(0xFFC0C6D9),
    surfaceContainer = Color(0xFF1D1F25),
    outline = Color(0xFF8A91A2),
)

internal val WearBrandLightColorScheme: ColorScheme = ColorScheme(
    primary = Color(0xFF1F5FA6),
    onPrimary = Color(0xFFFFFFFF),
    background = Color(0xFFF7F9FF),
    onBackground = Color(0xFF191B21),
    onSurface = Color(0xFF191B21),
    onSurfaceVariant = Color(0xFF414756),
    surfaceContainer = Color(0xFFEBEEF5),
    outline = Color(0xFF717788),
)

/** The night panel: amber on black, the colours a backlit gauge is allowed to be. */
internal val WearCockpitColorScheme: ColorScheme = ColorScheme(
    primary = Color(0xFFFCBA5B),
    onPrimary = Color(0xFF352200),
    background = Color(0xFF000000),
    onBackground = Color(0xFFE8E1DB),
    onSurface = Color(0xFFE8E1DB),
    onSurfaceVariant = Color(0xFFCABFB4),
    surfaceContainer = Color(0xFF1B1712),
    outline = Color(0xFF847B71),
)

/** The paper chart: the one theme here that stays light, because that is what it is. */
internal val WearChartColorScheme: ColorScheme = ColorScheme(
    primary = Color(0xFF123A5E),
    onPrimary = Color(0xFFF4EFE4),
    background = Color(0xFFF4EFE4),
    onBackground = Color(0xFF1E1B12),
    onSurface = Color(0xFF1E1B12),
    onSurfaceVariant = Color(0xFF4B4438),
    surfaceContainer = Color(0xFFE9E2D3),
    outline = Color(0xFF7C7566),
)

/**
 * The scheme a synced theme resolves to.
 *
 * The same shape as `:core:designsystem`'s `resolveColorScheme`, minus the
 * dynamic-colour branch it has and this cannot. Cockpit and Chart ignore
 * darkness for the same reason they do on the phone: they are a look, not a
 * tone mapping.
 */
internal fun schemeFor(state: WatchThemeState): ColorScheme = when (state.choice) {
    WatchThemeChoice.COCKPIT -> WearCockpitColorScheme
    WatchThemeChoice.CHART -> WearChartColorScheme
    WatchThemeChoice.SYSTEM,
    WatchThemeChoice.LIGHT,
    WatchThemeChoice.DARK,
    -> if (state.isDark) WearBrandDarkColorScheme else WearBrandLightColorScheme
}

/**
 * The type the route face is set in.
 *
 * Explicit styles rather than Wear Compose's typography roles, because this
 * screen is one bespoke full-bleed composition and not a stack of list
 * components: the design fixes the ICAO codes at a size that fills the top of a
 * 454 px face, and picking the nearest named role would be a worse fit dressed
 * up as a convention. Anything built here that *is* a list should take the roles.
 */
internal object WearRouteType {

    /** The two airport codes. The face's headline, and its largest element. */
    val code: TextStyle = TextStyle(
        fontSize = 30.sp,
        lineHeight = 34.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.5.sp,
        textAlign = TextAlign.Center,
    )

    /** A chip's caption — `DIST`, `TIME`. */
    val chipLabel: TextStyle = TextStyle(
        fontSize = 10.sp,
        lineHeight = 12.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 1.sp,
        textAlign = TextAlign.Center,
    )

    /** A chip's figure. */
    val chipValue: TextStyle = TextStyle(
        fontSize = 15.sp,
        lineHeight = 17.sp,
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center,
    )

    /** The airframe under the chips, and every other secondary line. */
    val caption: TextStyle = TextStyle(
        fontSize = 13.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Normal,
        textAlign = TextAlign.Center,
    )
}

@Composable
internal fun WearFlightPlannerTheme(
    state: WatchThemeState,
    content: @Composable () -> Unit,
) {
    MaterialTheme(colorScheme = schemeFor(state), content = content)
}
