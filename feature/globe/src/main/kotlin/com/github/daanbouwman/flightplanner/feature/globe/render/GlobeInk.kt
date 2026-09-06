package com.github.daanbouwman.flightplanner.feature.globe.render

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Every colour the sphere is drawn in — and **not one of them is a literal**.
 *
 * That is the rule the design states and it is the reason this type exists at
 * all: the renderer could perfectly well have held a dark blue for the backdrop
 * and a yellow for the arc, the way the desktop original does, and then Chart's
 * paper planet and Cockpit's warm near-black one would have needed a second set
 * of colours somewhere. Instead every field is filled from the active theme by
 * `rememberGlobeInk`, and the renderer knows only that it has been handed four
 * colours and a dim.
 *
 * The mapping, stated once so it is not re-derived per theme:
 *
 * | Field | Comes from |
 * | --- | --- |
 * | [backdrop] | the theme's own day sky, at its high-altitude end |
 * | [route] | `primary` |
 * | [routeCasing] | `surfaceContainer` — the same casing the flat `RouteMap` uses |
 * | [limb] | `outline` |
 * | [atmosphere] | `primary`, at low alpha |
 * | [imageryDim] | 1 everywhere except Cockpit |
 *
 * ### Imagery is imagery
 *
 * [imageryDim] multiplies the tiles and nothing else. It exists for Cockpit and
 * it is a **dim, not a tint**: a satellite photograph of the Alps is the same
 * photograph at night, and recolouring it amber would be inventing terrain
 * colour that is not there. What Cockpit changes is how much light the panel
 * throws at a dark-adapted eye, which is exactly a uniform multiply.
 */
@Immutable
internal data class GlobeInk(
    val backdrop: Color,
    val route: Color,
    val routeCasing: Color,
    val limb: Color,
    val atmosphere: Color,
    /**
     * What is drawn where the planet is not.
     *
     * The page, not black. A globe zoomed out until it no longer fills its
     * box is a body on the screen it is on; a black rectangle around it would
     * make the hero a window into somewhere else, and the app has no such
     * somewhere else. Black is also what a renderer clears to when nobody has
     * decided, which is the reason to decide.
     */
    val space: Color,
    val imageryDim: Float = 1f,
)
