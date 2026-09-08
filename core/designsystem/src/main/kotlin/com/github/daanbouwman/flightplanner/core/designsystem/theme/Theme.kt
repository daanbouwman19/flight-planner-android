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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
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
 * Which theme is in effect, for the few things that cannot be expressed as a
 * colour role.
 *
 * Almost nothing should read this. A component that branches on the theme has
 * usually failed to find the role it wanted, and the whole design system exists
 * so that Chart and Cockpit come out right without anyone asking which one is
 * on.
 *
 * The exception it exists for is the globe. Cockpit dims the satellite imagery,
 * and a dim is not a colour: it is a decision about how much light the panel
 * throws at a dark-adapted eye, and there is no role in a Material scheme that
 * means "how bright may a photograph be". Everything else about that sphere —
 * its backdrop, its limb, its arc — is a role, and 1H's claim that no value in
 * it is a literal holds because of it.
 */
val LocalThemeChoice: ProvidableCompositionLocal<ThemeChoice> =
    staticCompositionLocalOf { ThemeChoice.SYSTEM }

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
 * The sky-profile scenery bypasses it too, through [LocalSkyColors], but varies by
 * [themeChoice] rather than by tone mapping — Cockpit and Chart each get a palette
 * of their own. [SkyColors] explains why those two cannot be a light or a dark
 * variant of anything.
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
    // Remembered on its four inputs. `ColorScheme` has no `equals`, and
    // `MaterialTheme`'s colour local is a `staticCompositionLocalOf`, so a
    // freshly built instance on every recomposition of this function
    // invalidated every consumer in the tree by identity alone — including on
    // every recomposition [SystemBarsAppearance] below used to cause here,
    // before it was split out.
    val colorScheme: ColorScheme = remember(themeChoice, dynamicColor, dark, context) {
        when {
            themeChoice == ThemeChoice.COCKPIT -> CockpitColorScheme
            themeChoice == ThemeChoice.CHART -> ChartColorScheme
            dynamicColor ->
                if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            dark -> BrandDarkColorScheme
            else -> BrandLightColorScheme
        }
    }

    // Which screens currently have a photograph under the status bar. Held here,
    // next to [LocalBarsOverMedia] that carries it down to
    // [SystemBarsOverMedia]'s callers. A count rather than a flag, because two
    // of those callers overlap during a navigation — see [BarsOverMediaRequests].
    val overMedia = remember { BarsOverMediaRequests() }

    // Scenery, unlike the flight-rules colours, varies per *theme* rather than per
    // tone mapping: Cockpit needs a dim warm sky to protect dark adaptation and
    // Chart needs a printed one, and neither is reachable by picking a light or a
    // dark variant. Dynamic colour is ignored here for the same reason it is for
    // the flight-rules palette — a wallpaper-derived sky is not a sky.
    val skyColors = when {
        themeChoice == ThemeChoice.COCKPIT -> CockpitSkyColors
        themeChoice == ThemeChoice.CHART -> ChartSkyColors
        dark -> BrandDarkSkyColors
        else -> BrandLightSkyColors
    }

    CompositionLocalProvider(
        LocalThemeChoice provides themeChoice,
        LocalBarsOverMedia provides overMedia,
        LocalFlightRulesColors provides if (dark) DarkFlightRulesColors else LightFlightRulesColors,
        LocalSkyColors provides skyColors,
        // Resolved once here, for the whole tree. Every component that needs it
        // reads LocalReduceMotion; resolving it per component would register a
        // ContentObserver per component.
        LocalReduceMotion provides rememberReduceMotion(),
    ) {
        // Split out so that raising or lowering [SystemBarsOverMedia]'s flag
        // recomposes only this one small function rather than the whole of
        // [FlightPlannerTheme] — which used to also rebuild [colorScheme] on
        // every such step and invalidate the entire subtree by identity. See
        // the doc on `colorScheme` above.
        SystemBarsAppearance(dark = dark, overMedia = overMedia)
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
 * Writes the status- and navigation-bar appearance flags, and is the only
 * reader of [overMedia] — see [FlightPlannerTheme]'s call site.
 */
@Composable
private fun SystemBarsAppearance(dark: Boolean, overMedia: BarsOverMediaRequests) {
    val view = LocalView.current
    // **Read here, in composition, and not inside the `SideEffect`.** A
    // snapshot read inside an effect is not a composition dependency, so
    // writing the flag never invalidated this function and the effect never
    // re-ran — the override was inert and the bars kept whatever glyphs the
    // theme had last set. Reading it here is what subscribes.
    val barsOverMedia = overMedia.isRaised
    if (view.isInEditMode) return
    // A `SideEffect` rather than a `LaunchedEffect`: this is a write to a
    // platform object that must agree with what was just composed, and it is two
    // field writes, so re-applying it on a recomposition is cheaper than keying
    // an effect correctly. `findActivity` is nullable rather than a cast because
    // a Robolectric test or a dialog window need not have one.
    SideEffect {
        val window = view.context.findActivity()?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            // Taking the flag from composition rather than letting a screen
            // write the controller itself is what makes this the **only**
            // writer. Two writers meant the theme won whenever it recomposed
            // for a reason the override did not share — switching LIGHT to
            // CHART leaves `dark` unchanged, so the bars flipped back to dark
            // glyphs over a photograph and stayed there.
            isAppearanceLightStatusBars = if (barsOverMedia) false else !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
}

/**
 * Light status-bar glyphs while a photograph is running under the status bar.
 *
 * **This is not a scrim, and it is not a retreat from the empty-bars invariant.**
 * Nothing is painted: the imagery still runs unbroken up past the clock. What
 * changes is only which of the two colours the *system* draws its own glyphs in,
 * which is the one lever the platform gives for exactly this case.
 *
 * The app’s own chrome over that imagery does not use this. It sits on plates,
 * the way the globe’s labels and its imagery credit do, because a plate is legible
 * over ice and over ocean alike and a colour choice is only ever legible over one
 * of them. The system’s glyphs cannot be plated, so they get the choice; ours do
 * not need it.
 *
 * Only the status bar. The navigation bar sits over ordinary page content on every
 * screen that uses this, so flipping it would make the gesture handle the harder
 * of the two to see rather than the easier.
 *
 * This does not touch the window. It **registers a request** that
 * [FlightPlannerTheme] reads, because the theme writes the insets controller on
 * every one of its own recompositions and a second writer simply loses the next
 * one it does not share a key with.
 *
 * A request rather than a flag, and that distinction is load-bearing: more than
 * one screen calls this, and during a navigation their compositions overlap —
 * the immersive globe is composed while the route detail underneath it is still
 * there. The first build of this wrote `false` from every caller's `onDispose`,
 * so the outgoing screen's teardown silently cancelled the incoming screen's
 * request and the globe ran full-bleed satellite imagery under dark glyphs.
 * [BarsOverMediaRequests] counts instead, so the answer is "is anybody still
 * asking" rather than "who wrote last".
 */
@Composable
fun SystemBarsOverMedia(active: Boolean) {
    val overMedia = LocalBarsOverMedia.current
    DisposableEffect(overMedia, active) {
        if (active) overMedia.raise()
        onDispose { if (active) overMedia.lower() }
    }
}

/**
 * The live requests for light glyphs over media. See [SystemBarsOverMedia].
 *
 * Shared state rather than a call into the window, so that [FlightPlannerTheme]
 * stays the only thing that writes the insets controller and the two cannot race.
 */
private val LocalBarsOverMedia: ProvidableCompositionLocal<BarsOverMediaRequests> =
    staticCompositionLocalOf { BarsOverMediaRequests() }

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
