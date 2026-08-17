@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.github.daanbouwman.flightplanner.core.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.github.daanbouwman.flightplanner.core.designsystem.R
import com.github.daanbouwman.flightplanner.core.designsystem.theme.FlightPlannerTheme
import com.github.daanbouwman.flightplanner.core.designsystem.theme.FlightShapes

/**
 * The app's indeterminate loading indicator: a shape that morphs rather than a
 * ring that spins.
 *
 * This is Material 3 Expressive's `LoadingIndicator`, restricted to this app's
 * four-polygon sequence (see [FlightShapes.LoadingPolygons]) instead of the
 * default seven. It is wrapped here rather than called directly by screens for
 * the usual reason: `LoadingIndicator` is an alpha-only Expressive API, and this
 * module is the boundary that keeps that dependency to one place.
 *
 * Use this for work with no measurable progress — generating a batch of routes,
 * building the name index. For content that will occupy a known slot in a list,
 * prefer [SkeletonCard], which also tells the user where the content will land.
 *
 * @param contained draws the indicator on its own filled container. Use it when
 *   the indicator sits on arbitrary content and needs to separate itself from it;
 *   leave it off when it sits in space the layout has already reserved.
 */
@Composable
fun MorphingLoadingIndicator(
    modifier: Modifier = Modifier,
    contained: Boolean = false,
    contentDescription: String = stringResource(R.string.ds_loading_indicator_description),
) {
    // A loading indicator is one of the few purely decorative things that must
    // still be announced: it is the only signal a screen-reader user gets that
    // the screen is busy rather than empty.
    val semantics = modifier.semantics { this.contentDescription = contentDescription }

    if (contained) {
        ContainedLoadingIndicator(
            modifier = semantics,
            polygons = FlightShapes.LoadingPolygons,
        )
    } else {
        LoadingIndicator(
            modifier = semantics,
            polygons = FlightShapes.LoadingPolygons,
        )
    }
}

@LightDarkPreview
@Composable
private fun MorphingLoadingIndicatorPreview() {
    FlightPlannerTheme(dynamicColor = false) {
        Row(
            modifier = Modifier.padding(24.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            MorphingLoadingIndicator()
            MorphingLoadingIndicator(contained = true)
        }
    }
}
