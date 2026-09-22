package com.github.daanbouwman.flightplanner.wear.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme

/**
 * The watch app's colours.
 *
 * Ported from `:core:designsystem`'s `BrandDarkColorScheme` rather than
 * reinvented, so the two apps are recognisably one product — the same blue, the
 * same cool grey text — with **two deliberate divergences**, both of which are
 * about the device rather than about taste:
 *
 * - **The background is true black, not `#111319`.** The Galaxy Watch 9's
 *   display is OLED, where a black pixel is an unlit pixel; the phone's near-black
 *   costs battery on a screen that is on the wearer's wrist all day and spends
 *   much of it in ambient. It also makes the round bezel disappear, which the
 *   design's full-bleed map depends on.
 * - **There is no light scheme and no dynamic colour.** Wear OS has no wallpaper
 *   palette to derive one from, and a light watch face is a torch in a dark
 *   cockpit. The theme choice the phone offers (`system`, Cockpit, Chart) has no
 *   counterpart here yet; when it gets one it should be the phone's setting read
 *   over the Data Layer rather than a second setting to keep in sync.
 *
 * Only the roles this app actually paints are named. Everything else falls back
 * to Wear Compose's own defaults, so a component added later still lands
 * somewhere sensible instead of on an uninitialised colour.
 */
internal val WearBrandColorScheme: ColorScheme = ColorScheme(
    primary = Color(0xFFADC6FF),
    onPrimary = Color(0xFF00315D),
    background = Color(0xFF000000),
    onBackground = Color(0xFFE0E2EA),
    onSurface = Color(0xFFE0E2EA),
    onSurfaceVariant = Color(0xFFC0C6D9),
    surfaceContainer = Color(0xFF1D1F25),
)

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
internal fun WearFlightPlannerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = WearBrandColorScheme, content = content)
}
