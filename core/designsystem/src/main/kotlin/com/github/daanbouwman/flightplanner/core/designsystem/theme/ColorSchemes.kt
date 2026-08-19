package com.github.daanbouwman.flightplanner.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/*
 * The brand fallback schemes.
 *
 * These are what the app wears when dynamic colour is switched off in Settings.
 * There is no availability case to handle: minSdk is 35, so dynamic colour is
 * always present. Both are written out role by role rather than
 * letting `lightColorScheme()` fill the gaps from its purple baseline: a scheme
 * with three roles overridden and forty-five inherited is a scheme that looks
 * like Material's demo app wearing a hat.
 *
 * The tones are a CIELAB ramp (L* is the same axis Material calls "tone"), so the
 * usual role-to-tone mapping applies — primary T40/T80, containers T90/T30,
 * surfaces from the neutral ramp, and so on. Three source hues:
 *
 *  - **primary — avgas blue.** 100LL is dyed blue; it is the one colour every
 *    piston pilot associates with flying rather than with an airline.
 *  - **tertiary — runway-marking amber.** The accent, used for the things that
 *    mark a route the way paint marks a runway.
 *  - **secondary — a desaturated slate blue**, deliberately quiet, because it
 *    backs chips and containers that sit underneath dense numeric data.
 *
 * Flight-rules colours are *not* here. See [FlightRulesColors] for why.
 */

/** Brand light scheme, seeded from avgas blue with runway-amber tertiary. */
val BrandLightColorScheme: ColorScheme = lightColorScheme(
    primary = Color(0xFF1F5FA6),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD8E2FF),
    onPrimaryContainer = Color(0xFF004884),
    inversePrimary = Color(0xFFADC6FF),
    secondary = Color(0xFF39637D),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFC7E7FF),
    onSecondaryContainer = Color(0xFF1E4B64),
    tertiary = Color(0xFF815500),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDDB5),
    onTertiaryContainer = Color(0xFF624000),
    background = Color(0xFFF7F9FF),
    onBackground = Color(0xFF191B21),
    surface = Color(0xFFF7F9FF),
    onSurface = Color(0xFF191B21),
    surfaceVariant = Color(0xFFDCE2F5),
    onSurfaceVariant = Color(0xFF414756),
    surfaceTint = Color(0xFF1F5FA6),
    inverseSurface = Color(0xFF2E3036),
    inverseOnSurface = Color(0xFFEEF1F8),
    error = Color(0xFFB02C34),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD7),
    onErrorContainer = Color(0xFF92031E),
    outline = Color(0xFF717788),
    outlineVariant = Color(0xFFC0C6D9),
    scrim = Color(0xFF000005),
    surfaceBright = Color(0xFFF7F9FF),
    surfaceDim = Color(0xFFD7DAE1),
    surfaceContainer = Color(0xFFEBEEF5),
    surfaceContainerHigh = Color(0xFFE6E8F0),
    surfaceContainerHighest = Color(0xFFE0E2EA),
    surfaceContainerLow = Color(0xFFF1F3FB),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    primaryFixed = Color(0xFFD8E2FF),
    primaryFixedDim = Color(0xFFADC6FF),
    onPrimaryFixed = Color(0xFF001C3A),
    onPrimaryFixedVariant = Color(0xFF004884),
    secondaryFixed = Color(0xFFC7E7FF),
    secondaryFixedDim = Color(0xFFA3CCE9),
    onSecondaryFixed = Color(0xFF001E2D),
    onSecondaryFixedVariant = Color(0xFF1E4B64),
    tertiaryFixed = Color(0xFFFFDDB5),
    tertiaryFixedDim = Color(0xFFFCBA5B),
    onTertiaryFixed = Color(0xFF271900),
    onTertiaryFixedVariant = Color(0xFF624000),
)

/** Brand dark scheme — the same three hues, tone-inverted. */
val BrandDarkColorScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFFADC6FF),
    onPrimary = Color(0xFF00315D),
    primaryContainer = Color(0xFF004884),
    onPrimaryContainer = Color(0xFFD8E2FF),
    inversePrimary = Color(0xFF1F5FA6),
    secondary = Color(0xFFA3CCE9),
    onSecondary = Color(0xFF00344A),
    secondaryContainer = Color(0xFF1E4B64),
    onSecondaryContainer = Color(0xFFC7E7FF),
    tertiary = Color(0xFFFCBA5B),
    onTertiary = Color(0xFF442B00),
    tertiaryContainer = Color(0xFF624000),
    onTertiaryContainer = Color(0xFFFFDDB5),
    background = Color(0xFF111319),
    onBackground = Color(0xFFE0E2EA),
    surface = Color(0xFF111319),
    onSurface = Color(0xFFE0E2EA),
    surfaceVariant = Color(0xFF414756),
    onSurfaceVariant = Color(0xFFC0C6D9),
    surfaceTint = Color(0xFFADC6FF),
    inverseSurface = Color(0xFFE0E2EA),
    inverseOnSurface = Color(0xFF2E3036),
    error = Color(0xFFFFB3AD),
    onError = Color(0xFF690012),
    errorContainer = Color(0xFF92031E),
    onErrorContainer = Color(0xFFFFDAD7),
    outline = Color(0xFF8A91A2),
    outlineVariant = Color(0xFF414756),
    scrim = Color(0xFF000005),
    surfaceBright = Color(0xFF37393F),
    surfaceDim = Color(0xFF111319),
    surfaceContainer = Color(0xFF1D1F25),
    surfaceContainerHigh = Color(0xFF282A2F),
    surfaceContainerHighest = Color(0xFF33353A),
    surfaceContainerLow = Color(0xFF191B21),
    surfaceContainerLowest = Color(0xFF0B0E15),
    primaryFixed = Color(0xFFD8E2FF),
    primaryFixedDim = Color(0xFFADC6FF),
    onPrimaryFixed = Color(0xFF001C3A),
    onPrimaryFixedVariant = Color(0xFF004884),
    secondaryFixed = Color(0xFFC7E7FF),
    secondaryFixedDim = Color(0xFFA3CCE9),
    onSecondaryFixed = Color(0xFF001E2D),
    onSecondaryFixedVariant = Color(0xFF1E4B64),
    tertiaryFixed = Color(0xFFFFDDB5),
    tertiaryFixedDim = Color(0xFFFCBA5B),
    onTertiaryFixed = Color(0xFF271900),
    onTertiaryFixedVariant = Color(0xFF624000),
)

/**
 * Cockpit: a night-flying instrument panel, not a third dark mode.
 *
 * The surfaces are a warm near-black — an unlit panel, not a grey card stack —
 * and the primary is the amber of a backlit gauge. Green and blue take the
 * secondary and tertiary slots because those are the other two colours an
 * instrument panel is allowed to be.
 *
 * It **ignores dynamic colour by construction**: the whole point of the theme is
 * that the screen stops competing with the pilot's dark adaptation, and a
 * wallpaper-derived scheme cannot promise that. [FlightPlannerTheme] enforces it.
 */
val CockpitColorScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFFFCBA5B),
    onPrimary = Color(0xFF352200),
    primaryContainer = Color(0xFF5C3B00),
    onPrimaryContainer = Color(0xFFFFE4C5),
    inversePrimary = Color(0xFF815500),
    secondary = Color(0xFF9CD3AA),
    onSecondary = Color(0xFF002D12),
    secondaryContainer = Color(0xFF124726),
    onSecondaryContainer = Color(0xFFBDF5CA),
    tertiary = Color(0xFF82CFFF),
    onTertiary = Color(0xFF00293C),
    tertiaryContainer = Color(0xFF00425D),
    onTertiaryContainer = Color(0xFFD2ECFF),
    background = Color(0xFF110A02),
    onBackground = Color(0xFFE8E1DB),
    surface = Color(0xFF110A02),
    onSurface = Color(0xFFE8E1DB),
    surfaceVariant = Color(0xFF2E271E),
    onSurfaceVariant = Color(0xFFCABFB4),
    surfaceTint = Color(0xFFFCBA5B),
    inverseSurface = Color(0xFFE8E1DB),
    inverseOnSurface = Color(0xFF2B2722),
    error = Color(0xFFFFB3AD),
    onError = Color(0xFF55000A),
    errorContainer = Color(0xFF810019),
    onErrorContainer = Color(0xFFFFE2DF),
    outline = Color(0xFF847B71),
    outlineVariant = Color(0xFF3B332B),
    scrim = Color(0xFF010000),
    surfaceBright = Color(0xFF302B26),
    surfaceDim = Color(0xFF0D0600),
    surfaceContainer = Color(0xFF1B1712),
    surfaceContainerHigh = Color(0xFF211D18),
    surfaceContainerHighest = Color(0xFF27231E),
    surfaceContainerLow = Color(0xFF17130C),
    surfaceContainerLowest = Color(0xFF010000),
    primaryFixed = Color(0xFFFFDDB5),
    primaryFixedDim = Color(0xFFFCBA5B),
    onPrimaryFixed = Color(0xFF271900),
    onPrimaryFixedVariant = Color(0xFF624000),
    secondaryFixed = Color(0xFFB8F0C5),
    secondaryFixedDim = Color(0xFF9CD3AA),
    onSecondaryFixed = Color(0xFF002209),
    onSecondaryFixedVariant = Color(0xFF1D502E),
    tertiaryFixed = Color(0xFFC7E7FF),
    tertiaryFixedDim = Color(0xFF82CFFF),
    onTertiaryFixed = Color(0xFF001E2D),
    onTertiaryFixedVariant = Color(0xFF014C6A),
)
