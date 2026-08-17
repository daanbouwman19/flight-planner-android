package com.github.daanbouwman.flightplanner.core.designsystem.components

import android.content.res.Configuration
import androidx.compose.ui.tooling.preview.Preview

/**
 * Renders a preview twice, light and dark.
 *
 * Every atom in this package carries it. A component that has only ever been
 * looked at in one theme is a component whose second theme is broken and nobody
 * has noticed yet — most often because a colour was taken from the wrong role,
 * which is invisible until the roles swap.
 */
@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
internal annotation class LightDarkPreview
