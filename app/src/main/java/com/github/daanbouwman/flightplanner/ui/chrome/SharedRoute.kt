package com.github.daanbouwman.flightplanner.ui.chrome

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import com.github.daanbouwman.flightplanner.core.designsystem.motion.FlightMotion

/**
 * The two scopes a shared element needs, carried to wherever draws one.
 *
 * Passed as composition locals rather than as parameters, which is the one place
 * this app does that for layout. A shared element's two halves are a card
 * *inside a lazy list inside a screen inside a scaffold* and a hero on another
 * screen; threading two scopes down that path would put a parameter on every
 * composable between, none of which has anything to do with the transition. The
 * app already carries its chrome state and its reselect signal this way, so this
 * is the established idiom here rather than a new one.
 *
 * Both default to null, and [sharedRouteElement] is a no-op when either is. That
 * is what lets a preview — or a screenshot test, or a card in a context that is
 * not a navigation transition at all — compose the same card without a
 * `SharedTransitionLayout` above it.
 */
val LocalSharedTransitionScope: ProvidableCompositionLocal<SharedTransitionScope?> =
    compositionLocalOf { null }

/** The destination's own visibility scope. Changes on every navigation, so not static. */
val LocalNavAnimatedVisibilityScope: ProvidableCompositionLocal<AnimatedVisibilityScope?> =
    compositionLocalOf { null }

/**
 * Marks this element as the same thing as the element with [key] on the screen
 * being navigated to or from.
 *
 * The bounds transform is the design system's, so a shared element travels on
 * the same spring as everything else that moves; `:app` names the token and
 * never the physics.
 *
 * ### [remeasure] is a real choice, and the wrong one is expensive
 *
 * **Text wants it.** The two halves of a code pair are set in different type
 * styles, and remeasuring lays the glyphs out at the size they are arriving at
 * rather than stretching the size they left.
 *
 * **A canvas does not.** Remeasuring re-lays-out the child at the animated bounds
 * on *every frame*, and `RouteMap` builds its geometry in a `drawWithCache` keyed
 * on its size — so a remeasured map re-runs `MapFrame.forRoute`, re-projects 122
 * coastline rings and rebuilds three paths every frame of the transition. That
 * cache exists precisely so this work happens when the size changes and never
 * otherwise; remeasuring turns "never" into "sixty times a second". Left alone,
 * the element scales instead, which for a map is also the more honest motion: it
 * is the same piece of world getting closer, not a different framing of it.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedRouteElement(key: String, remeasure: Boolean = true): Modifier {
    val sharedScope = LocalSharedTransitionScope.current
    val visibilityScope = LocalNavAnimatedVisibilityScope.current
    if (sharedScope == null || visibilityScope == null) return this

    val transform = FlightMotion.boundsTransform()
    return with(sharedScope) {
        val state = rememberSharedContentState(key = key)
        if (remeasure) {
            this@sharedRouteElement.sharedBounds(
                sharedContentState = state,
                animatedVisibilityScope = visibilityScope,
                boundsTransform = transform,
                resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
            )
        } else {
            this@sharedRouteElement.sharedBounds(
                sharedContentState = state,
                animatedVisibilityScope = visibilityScope,
                boundsTransform = transform,
            )
        }
    }
}

/**
 * The keys that make a card and a detail screen the same route.
 *
 * Built from the route's own identity rather than from a row id, because the
 * detail screen is entered with navigation arguments and never sees the row. The
 * three fields are exactly what `Destination.RouteDetail` carries, which is what
 * guarantees both ends can compute the same string.
 */
object SharedRouteKeys {

    fun map(departureIcao: String, destinationIcao: String, aircraftId: Int): String =
        "route-map:${route(departureIcao, destinationIcao, aircraftId)}"

    fun aircraft(departureIcao: String, destinationIcao: String, aircraftId: Int): String =
        "route-aircraft:${route(departureIcao, destinationIcao, aircraftId)}"

    fun departure(departureIcao: String, destinationIcao: String, aircraftId: Int): String =
        "route-departure:${route(departureIcao, destinationIcao, aircraftId)}"

    fun destination(departureIcao: String, destinationIcao: String, aircraftId: Int): String =
        "route-destination:${route(departureIcao, destinationIcao, aircraftId)}"

    private fun route(departureIcao: String, destinationIcao: String, aircraftId: Int): String =
        "$departureIcao>$destinationIcao@$aircraftId"
}

/** Provides both scopes to everything beneath. Used once, around the navigation graph. */
@Composable
fun ProvideSharedRouteScopes(
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalSharedTransitionScope provides sharedTransitionScope,
        LocalNavAnimatedVisibilityScope provides animatedVisibilityScope,
        content = content,
    )
}
