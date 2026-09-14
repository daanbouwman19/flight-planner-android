package com.github.daanbouwman.flightplanner.feature.globe.render

/**
 * The band of a globe view the system's edge gestures must keep out of.
 *
 * ### What was wrong
 *
 * About 30 dp of both window edges is the back-gesture strip, the full height
 * of the window — 14.4 % of the width on the test device. A spin that starts
 * near either edge is pilfered: the finger lands, the globe moves a few pixels,
 * and then the system decides it is a back gesture and navigates away with the
 * planet mid-turn. Nothing in the app called `systemGestureExclusionRects`.
 *
 * ### What is excluded, and what deliberately is not
 *
 * A horizontal band across the full width of the view, below the host's own
 * chrome. **Not the whole view**, for two reasons that turn out to agree.
 *
 * The platform honours at most about 200 dp of exclusion height per edge and
 * ignores the rest, so a full-height request on the immersive screen spends the
 * allowance wherever the platform chooses rather than where a thumb spins. The
 * band is therefore capped at [maxBandPx] and **centred** in the space below
 * the chrome: the middle of the sphere is where a spin starts, and the corners
 * are where nothing does.
 *
 * And the corners are where the controls live — the collapse button and the app
 * bar's back button above, the camera stack and the credit below. Leaving them
 * outside the band keeps the system's back gesture working from beside them,
 * so a user who cannot find the button still has the platform's own way out.
 * A blank full-screen rectangle with no way out is a defect this feature has
 * already shipped once.
 *
 * Pure, so the arithmetic is tested on the JVM; the host converts it to an
 * `android.graphics.Rect` at the boundary.
 */
internal data class GestureExclusion(val left: Int, val top: Int, val right: Int, val bottom: Int) {

    companion object {
        /**
         * The band for a view of [width] × [height] px whose host covers the top
         * [topChromePx] with chrome, or null when there is nothing to exclude.
         *
         * @param maxBandPx the most height the platform will honour, in px — the
         *   200 dp cap at the view's density. The band is this tall when the
         *   space below the chrome allows, and the whole of that space when not.
         */
        fun forSurface(width: Int, height: Int, topChromePx: Int, maxBandPx: Int): GestureExclusion? {
            if (width <= 0 || height <= 0 || maxBandPx <= 0) return null
            val top = topChromePx.coerceIn(0, height)
            val available = height - top
            if (available <= 0) return null
            if (available <= maxBandPx) return GestureExclusion(0, top, width, height)
            val bandTop = top + (available - maxBandPx) / 2
            return GestureExclusion(0, bandTop, width, bandTop + maxBandPx)
        }
    }
}
