@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package com.github.daanbouwman.flightplanner.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext

/**
 * The four looks the app offers.
 *
 * [COCKPIT] is not a third dark mode. It is a near-black instrument panel with
 * amber accents, meant for flying at night, and it deliberately ignores dynamic
 * colour — see [FlightPlannerTheme].
 */
enum class ThemeChoice { SYSTEM, LIGHT, DARK, COCKPIT }

/**
 * The app's theme. Everything Material 3 Expressive enters the app through here.
 *
 * Wraps [MaterialExpressiveTheme] rather than `MaterialTheme` for one concrete
 * reason: only the Expressive theme installs a [MotionScheme], and the motion
 * scheme is what [com.github.daanbouwman.flightplanner.core.designsystem.motion.FlightMotion]
 * reads. Use plain `MaterialTheme` and every animation in the app silently falls
 * back to standard springs — nothing breaks, it just stops feeling like this app.
 *
 * Colour resolution, in order:
 *
 * 1. [ThemeChoice.COCKPIT] always wins and always uses [CockpitColorScheme].
 *    Dynamic colour is ignored on purpose: the theme exists so the screen stops
 *    competing with the pilot's dark adaptation, and a scheme derived from
 *    whatever the wallpaper happens to be cannot promise that.
 * 2. Otherwise, if [dynamicColor] is on, the wallpaper scheme. No version guard:
 *    minSdk is 36, so dynamic colour (API 31+) is always available.
 * 3. Otherwise the brand fallback — avgas blue with runway-amber accents.
 *
 * Flight-rules colours bypass all of that and are provided through
 * [LocalFlightRulesColors]; they vary only between the light and dark tone
 * mappings. [FlightRulesColors] explains why.
 */
@Composable
fun FlightPlannerTheme(
    themeChoice: ThemeChoice = ThemeChoice.SYSTEM,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val dark = when (themeChoice) {
        ThemeChoice.SYSTEM -> isSystemInDarkTheme()
        ThemeChoice.LIGHT -> false
        ThemeChoice.DARK -> true
        ThemeChoice.COCKPIT -> true
    }
    val context = LocalContext.current
    val colorScheme: ColorScheme = when {
        themeChoice == ThemeChoice.COCKPIT -> CockpitColorScheme
        dynamicColor ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> BrandDarkColorScheme
        else -> BrandLightColorScheme
    }

    CompositionLocalProvider(
        LocalFlightRulesColors provides if (dark) DarkFlightRulesColors else LightFlightRulesColors,
    ) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            motionScheme = MotionScheme.expressive(),
            shapes = FlightShapeScale,
            typography = FlightTypography,
            content = content,
        )
    }
}
