package com.github.daanbouwman.flightplanner.ui.detail

/**
 * Whether the hero's imagery still reaches down past [depthPx] from the top of
 * the window, given how tall the hero is and how far the page has scrolled.
 *
 * A pure function of three measurements rather than a fraction read off the
 * scroll-behaviour's own state: `overlappedFraction` steps from 0 to 1 within
 * about 0.6 dp of scroll for a pinned single-row bar, which is why the flag it
 * used to drive dropped the glass chrome on the first frame past touch slop
 * while ~390 dp of photograph was still on screen. This instead asks the
 * question the chrome actually needs answered — is there still imagery behind
 * the strip in question — and it takes [depthPx] as a parameter because that
 * strip is a different height for the two callers: the whole measured app bar
 * for the chrome sitting on it, and the shorter status inset alone for what the
 * *system* draws its glyphs in.
 */
internal fun imageryCovers(heroHeightPx: Float, scrollPx: Float, depthPx: Float): Boolean =
    heroHeightPx - scrollPx > depthPx
