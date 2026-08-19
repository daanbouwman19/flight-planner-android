@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package com.github.daanbouwman.flightplanner.core.designsystem.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.github.daanbouwman.flightplanner.core.designsystem.motion.LocalReduceMotion
import com.github.daanbouwman.flightplanner.core.designsystem.motion.rememberReduceMotion

/**
 * The five looks the app offers.
 *
 * [COCKPIT] is not a third dark mode. It is a near-black instrument panel with
 * amber accents, meant for flying at night, and it deliberately ignores dynamic
 * colour — see [FlightPlannerTheme]. [CHART] is its light-side counterpart: chart
 * paper and navy ink, for the same reason and the same exemption.
 */
enum class ThemeChoice { SYSTEM, LIGHT, DARK, COCKPIT, CHART }

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
 * 1. [ThemeChoice.COCKPIT] and [ThemeChoice.CHART] always win and always use
 *    [CockpitColorScheme] or [ChartColorScheme] respectively. Dynamic colour is
 *    ignored on purpose for both: each theme's identity is a specific pair of
 *    surfaces and ink, and a scheme derived from whatever the wallpaper happens
 *    to be cannot promise that.
 * 2. Otherwise, if [dynamicColor] is on, the wallpaper scheme. No version guard:
 *    minSdk is 35, so dynamic colour (API 31+) is always available.
 * 3. Otherwise the brand fallback — avgas blue with runway-amber accents.
 *
 * Flight-rules colours bypass all of that and are provided through
 * [LocalFlightRulesColors]; they vary only between the light and dark tone
 * mappings. [FlightRulesColors] explains why.
 *
 * ### The system bars
 *
 * The window is edge to edge and the bars are transparent, so the clock and the
 * gesture handle are drawn over whatever the app has put there. The only thing
 * keeping them legible is the *appearance* flag, and it has to follow the scheme
 * this function resolved rather than the system's night setting — those are the
 * same thing today only because nothing sets [themeChoice] yet. The moment a
 * settings screen offers Light, Dark or Cockpit, a dark app under a light system
 * would get dark icons on a near-black status bar and lose them entirely.
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
        ThemeChoice.CHART -> false
    }
    val context = LocalContext.current
    val colorScheme: ColorScheme = when {
        themeChoice == ThemeChoice.COCKPIT -> CockpitColorScheme
        themeChoice == ThemeChoice.CHART -> ChartColorScheme
        dynamicColor ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> BrandDarkColorScheme
        else -> BrandLightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        // A `SideEffect` rather than a `LaunchedEffect`: this is a write to a
        // platform object that must agree with what was just composed, and it is two
        // field writes, so re-applying it on a recomposition is cheaper than keying
        // an effect correctly. `findActivity` is nullable rather than a cast because
        // a Robolectric test or a dialog window need not have one.
        SideEffect {
            val window = view.context.findActivity()?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

    CompositionLocalProvider(
        LocalFlightRulesColors provides if (dark) DarkFlightRulesColors else LightFlightRulesColors,
        // Resolved once here, for the whole tree. Every component that needs it
        // reads LocalReduceMotion; resolving it per component would register a
        // ContentObserver per component.
        LocalReduceMotion provides rememberReduceMotion(),
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

/**
 * The [Activity] a view belongs to, if any.
 *
 * A compose view's context is not necessarily the activity — it is often a
 * `ContextThemeWrapper` around it — so a plain cast works until it does not.
 */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
