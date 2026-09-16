package com.github.daanbouwman.flightplanner.widget

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.color.ColorProviders
import androidx.glance.material3.ColorProviders
import androidx.glance.unit.ColorProvider
import com.github.daanbouwman.flightplanner.R
import com.github.daanbouwman.flightplanner.core.designsystem.theme.ThemeChoice
import com.github.daanbouwman.flightplanner.core.designsystem.theme.isDark
import com.github.daanbouwman.flightplanner.core.designsystem.theme.resolveColorScheme
import com.github.daanbouwman.flightplanner.settings.AppSettings

/**
 * The widget's colours, following the app's own theme choice.
 *
 * [colors] is what `GlanceTheme` takes: null means "let Glance decide", which
 * is its dynamic Material You palette — the same wallpaper scheme
 * `FlightPlannerTheme` resolves when dynamic colour is on and the theme follows
 * the system. Every other choice is built from `resolveColorScheme`, the
 * theme's own rule: a forced Light or Dark hands the same scheme to both
 * slots, and Cockpit and Chart are one look whatever the system's night mode
 * says.
 *
 * [card] and [chip] exist because Glance's role list stops at `surface` and
 * `surfaceVariant`, and the app's route card sits on neither: it is
 * `surfaceContainer`, with `surfaceContainerHigh` chips. Under dynamic colour
 * these come from the platform — Android 14 publishes the full Material 3 role
 * set as `@android:color/system_*` resources — through two shape drawables
 * whose solid colour is `R.color.widget_card` / `widget_chip`, with the light
 * or dark half chosen by the *launcher's* night mode at inflate time. A
 * drawable and not a colour provider because Glance's resource-id
 * `ColorProvider` is restricted to the library and lint refuses it. For the
 * app's own schemes they are the same two roles read off the resolved
 * `ColorScheme`s, day and night. Either way the home-screen card is the tone
 * the in-app card is.
 *
 * [casing] is the card's tone as a *tint*, for the halo under the route, which
 * is an image and can only be coloured by a provider. Under dynamic colour it
 * is the two platform tones resolved when the widget renders, so unlike the
 * card itself it is pinned until the next render; a 1.5 dp halo a shade off
 * for at most a day is the price of not suppressing lint.
 */
class WidgetPalette(
    val colors: ColorProviders?,
    val card: WidgetSurface,
    val chip: WidgetSurface,
    val casing: ColorProvider,
)

/** How a widget surface is painted: a resource the launcher resolves, or a colour we resolved. */
sealed interface WidgetSurface {
    class Drawable(@param:DrawableRes val res: Int) : WidgetSurface
    class Flat(val color: ColorProvider) : WidgetSurface
}

fun GlanceModifier.surface(surface: WidgetSurface): GlanceModifier = when (surface) {
    is WidgetSurface.Drawable -> background(ImageProvider(surface.res))
    is WidgetSurface.Flat -> background(surface.color)
}

fun widgetPalette(settings: AppSettings, context: Context): WidgetPalette {
    if (settings.dynamicColour && settings.themeChoice == ThemeChoice.SYSTEM) {
        return WidgetPalette(
            colors = null,
            card = WidgetSurface.Drawable(R.drawable.widget_card_background),
            chip = WidgetSurface.Drawable(R.drawable.widget_chip_background),
            casing = ColorProvider(
                day = Color(context.getColor(android.R.color.system_surface_container_light)),
                night = Color(context.getColor(android.R.color.system_surface_container_dark)),
            ),
        )
    }
    fun scheme(systemDark: Boolean) = resolveColorScheme(
        themeChoice = settings.themeChoice,
        dynamicColor = settings.dynamicColour,
        dark = settings.themeChoice.isDark(systemDark),
        context = context,
    )
    val light = scheme(systemDark = false)
    val dark = scheme(systemDark = true)
    val card = ColorProvider(day = light.surfaceContainer, night = dark.surfaceContainer)
    return WidgetPalette(
        colors = ColorProviders(light = light, dark = dark),
        card = WidgetSurface.Flat(card),
        chip = WidgetSurface.Flat(ColorProvider(day = light.surfaceContainerHigh, night = dark.surfaceContainerHigh)),
        casing = card,
    )
}
