package com.github.daanbouwman.flightplanner.ui.chrome

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
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
 *
 * ### [clipShape] is not decoration — without it the element has square corners
 *
 * **A shared element is drawn in an overlay above both screens, and no ancestor
 * clip reaches into it.** The card's rounded corners are the `Card`'s clip and
 * the hero's are the `Surface`'s; both are ancestors, so for the length of the
 * transition neither applies and the travelling element is a rectangle. It is
 * most visible in the frames where it has already arrived — the element sits at
 * exactly the card's bounds with square corners until the overlay is torn down,
 * and then snaps round. Naming the shape the element rests in here gives the
 * overlay the same clip its host would have applied, so the corners are right
 * for the whole flight and there is nothing to snap.
 *
 * The clip follows the *animated* bounds, so the radius stays a constant number
 * of dp rather than being stretched with the element.
 *
 * **The default is no clip at all, and that is deliberate.** `sharedBounds`
 * defaults to `ParentClip`, which clips an element to the nearest enclosing
 * shared element. That is right for a badge riding on a card; it is wrong for
 * every element here that travels *out* of the card it starts in — the two ICAO
 * codes leave the card's face for the detail screen's spine, and inheriting the
 * card's clip would cut them off at its edge. So an element that wants a clip
 * says which one, and an element that says nothing keeps the freedom it has to
 * cross the screen.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedRouteElement(
    key: String,
    remeasure: Boolean = true,
    clipShape: Shape? = null,
): Modifier {
    val sharedScope = LocalSharedTransitionScope.current
    val visibilityScope = LocalNavAnimatedVisibilityScope.current
    if (sharedScope == null || visibilityScope == null) return this

    val transform = FlightMotion.boundsTransform()
    return with(sharedScope) {
        val state = rememberSharedContentState(key = key)
        // Held across recompositions because the implementation reuses one `Path`
        // rather than allocating a new one per frame, which is the whole reason
        // the API hands out an object instead of a lambda.
        val clip = remember(clipShape) { clipShape?.let { OverlayClip(it) } ?: NoOverlayClip }
        if (remeasure) {
            this@sharedRouteElement.sharedBounds(
                sharedContentState = state,
                animatedVisibilityScope = visibilityScope,
                boundsTransform = transform,
                resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
                clipInOverlayDuringTransition = clip,
            )
        } else {
            this@sharedRouteElement.sharedBounds(
                sharedContentState = state,
                animatedVisibilityScope = visibilityScope,
                boundsTransform = transform,
                clipInOverlayDuringTransition = clip,
            )
        }
    }
}

/**
 * The clip an element that travels across the screen wants: none.
 *
 * This is what stands in for `sharedBounds`' `ParentClip` default, so that
 * nesting one shared element inside another does not silently confine the inner
 * one to the outer one's bounds. See the `clipShape` section of
 * [sharedRouteElement] for why that default is the wrong one here.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
private val NoOverlayClip: SharedTransitionScope.OverlayClip =
    object : SharedTransitionScope.OverlayClip {
        override fun getClipPath(
            sharedContentState: SharedTransitionScope.SharedContentState,
            bounds: Rect,
            layoutDirection: LayoutDirection,
            density: Density,
        ): Path? = null
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

    /**
     * The card's whole face, and the hero it becomes.
     *
     * Named for what travels rather than for what is drawn on it. On the detail
     * screen the element really is only the map; on the card it is the map *and
     * everything printed over it*, because the card's figures have to stay in
     * front of their own background while the pair is in the overlay. See the
     * note in `RouteCard`.
     */
    fun face(departureIcao: String, destinationIcao: String, aircraftId: Int): String =
        "route-face:${route(departureIcao, destinationIcao, aircraftId)}"

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
