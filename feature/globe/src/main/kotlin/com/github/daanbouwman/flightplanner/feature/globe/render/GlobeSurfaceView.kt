package com.github.daanbouwman.flightplanner.feature.globe.render

import android.content.Context
import android.view.SurfaceView

/**
 * The globe in a hardware layer of its own, composited **below** the window.
 *
 * A hole is punched through the window where this view sits, and Compose content
 * drawn later in the tree — the controls, the labels, the attribution — lands on
 * top of it. This is how a video player puts controls over its surface, and it is
 * the reason none of the glass chrome has to be drawn by the renderer. It costs
 * nothing per frame: there is no copy, the compositor simply shows the layer.
 *
 * ### What it costs instead, and when that matters
 *
 * **A separate compositor layer is not clipped, faded or transformed by its
 * Compose ancestors.** A Compose `clip`, `alpha` or corner radius is a render-node
 * property of the *window*, and this view's pixels are not in the window. The
 * consequences are all one fact:
 *
 *  - the layer is not rounded by a rounded container;
 *  - a Compose alpha on the node hosting it never reaches the layer, which is why
 *    G8's crossfade fades the still map *out* over the globe rather than fading
 *    the globe in;
 *  - and — the one that shipped a bug — where an ancestor clip cannot be reduced
 *    to a plain rectangle the platform gives up on cropping the layer altogether
 *    and composites the **whole buffer at its unclipped position**. In a
 *    `LazyColumn` that put the Stats globe over the header and the status bar,
 *    and the overscroll stretch toggled the layer's visibility on top of that,
 *    which is the white flash.
 *
 * So this view is for the hosts that are **full-bleed and square**: the route
 * detail's deep hero and the immersive screen. Both fill their width, neither
 * sits in a rounded container, and neither is a lazy item. A globe inside a card
 * in a scrolling list wants [GlobeTextureView] instead, which is ordinary view
 * content and clips like any of it.
 *
 * Everything that actually renders lives in [GlobeRenderHost]; this class is the
 * `SurfaceView` half of the two hosts that share it.
 */
internal class GlobeSurfaceView(context: Context) : SurfaceView(context), GlobeHostView {

    override val host = GlobeRenderHost(this) { helper -> helper.attachTo(this) }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        host.onAttached()
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        host.onWindowVisibility(isVisible)
    }

    override fun onDetachedFromWindow() {
        host.onDetached()
        super.onDetachedFromWindow()
    }
}
