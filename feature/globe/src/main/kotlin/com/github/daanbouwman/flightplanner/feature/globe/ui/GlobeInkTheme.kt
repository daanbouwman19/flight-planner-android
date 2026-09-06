package com.github.daanbouwman.flightplanner.feature.globe.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.github.daanbouwman.flightplanner.core.designsystem.theme.LocalSkyColors
import com.github.daanbouwman.flightplanner.core.designsystem.theme.LocalThemeChoice
import com.github.daanbouwman.flightplanner.core.designsystem.theme.ThemeChoice
import com.github.daanbouwman.flightplanner.feature.globe.render.GlobeInk

/**
 * The active theme, read as the six values the sphere is drawn in.
 *
 * This is the whole of 1H's claim — *the sphere is themed too; no value in it is
 * a literal* — in one function. Chart gets a paper planet in navy ink and
 * Cockpit a warm near-black one, and neither needed a colour of its own, because
 * every value here is a role rather than a shade.
 *
 * The backdrop takes the theme's own day sky at its **high** end, which is the
 * thin air at the top of the altitude axis — the deepest colour in that band,
 * and the one a planet seen from outside its atmosphere actually is. Reaching
 * for `surface` instead would have made the globe a hole in the page rather than
 * a body in front of it.
 */
@Composable
internal fun rememberGlobeInk(): GlobeInk {
    val scheme = MaterialTheme.colorScheme
    val sky = LocalSkyColors.current
    val dimImagery = LocalThemeChoice.current == ThemeChoice.COCKPIT
    return remember(scheme, sky, dimImagery) {
        GlobeInk(
            backdrop = sky.day.high,
            route = scheme.primary,
            routeCasing = scheme.surfaceContainer,
            limb = scheme.outline,
            atmosphere = scheme.primary,
            space = scheme.surface,
            // Cockpit's dim, and only Cockpit's. A satellite photograph is the
            // same photograph at night; what changes is how much light the panel
            // throws at a dark-adapted eye.
            imageryDim = if (dimImagery) CockpitImageryDim else 1f,
        )
    }
}

/**
 * How far Cockpit dims the imagery.
 *
 * The concept draws a 34% scrim over the sphere. A scrim and a multiply differ:
 * a scrim adds a layer of the theme's own near-black over live imagery, which
 * tints it warm, while a multiply only takes light away. The design's own
 * sentence — *imagery is imagery, it is not re-tinted for night* — settles which
 * of the two this is, so the 34% survives as a factor rather than as an overlay.
 */
private const val CockpitImageryDim = 0.66f
