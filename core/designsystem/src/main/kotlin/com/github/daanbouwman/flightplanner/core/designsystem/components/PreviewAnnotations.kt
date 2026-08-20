package com.github.daanbouwman.flightplanner.core.designsystem.components

import android.content.res.Configuration
import androidx.compose.ui.tooling.preview.Preview

/**
 * Renders a preview twice, light and dark.
 *
 * Every atom in this package carries it, and so does every screen component. A
 * component that has only ever been looked at in one theme is a component whose
 * second theme is broken and nobody has noticed yet — most often because a
 * colour was taken from the wrong role, which is invisible until the roles swap.
 *
 * Public rather than internal: screens live in `:app` and need the same pairing,
 * and one shared annotation is what keeps every preview in the project framed
 * the same way.
 */
@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
annotation class LightDarkPreview

/**
 * A preview at the narrow end of the range the app actually ships to.
 *
 * 360 dp is what a 1080 × 2340 phone at 480 dpi reports, which is the most
 * common phone width there is — and the width at which this app's dense rows
 * first start to overflow. Previewing at the tooling default hides exactly the
 * problems worth catching, so anything with a horizontal layout gets this too.
 */
@Preview(name = "Compact 360dp", showBackground = true, widthDp = 360)
@Preview(
    name = "Compact 360dp · font 2.0",
    showBackground = true,
    widthDp = 360,
    fontScale = 2.0f,
)
annotation class CompactWidthPreview

/**
 * Previews for different window sizes, from phone to tablet and desktop.
 *
 * It covers the breakpoints at which the navigation suite and the screen
 * layouts change: a bottom bar becomes a rail, and a single pane becomes two.
 */
@Preview(name = "Phone", device = "spec:width=360dp,height=800dp,dpi=480", showBackground = true)
@Preview(name = "Tablet", device = "spec:width=1280dp,height=800dp,dpi=240", showBackground = true)
@Preview(name = "Desktop", device = "spec:width=1920dp,height=1080dp,dpi=160", showBackground = true)
annotation class DevicePreviews
