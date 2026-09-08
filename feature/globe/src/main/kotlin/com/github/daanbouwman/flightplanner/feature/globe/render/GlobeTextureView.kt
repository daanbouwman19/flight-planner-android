package com.github.daanbouwman.flightplanner.feature.globe.render

import android.content.Context
import android.view.TextureView

/**
 * The globe as ordinary view content, for the hosts that are clipped.
 *
 * ### Why this exists at all
 *
 * A `TextureView` draws into the window like any other view, so **everything
 * Compose does to its ancestors reaches it**: a clip, a corner radius, an alpha,
 * the offscreen layer `fadeUnderStatusBar` puts over a list, and the overscroll
 * stretch's `RenderEffect`. [GlobeSurfaceView] gets none of those, because its
 * pixels are in a compositor layer of their own rather than in the window.
 *
 * That difference is not a nicety here, it is the bug. The Stats visited-network
 * globe is a `LazyColumn` item inside a rounded `Card`. With a `SurfaceView` the
 * platform could not express the ancestor clip as a layer crop, so it composited
 * the **whole 260 dp buffer at its unclipped position** — the sphere painted over
 * the card's own header and up across the status bar the moment the card scrolled
 * past the top of the viewport. Pulling at the end of the list then toggled that
 * same layer's visibility through the stretch effect, which is the white flash.
 * Neither is reachable from app code: `SurfaceView.setClipBounds` is inert without
 * the `@hide` `setEnableSurfaceClipping`, and `setCornerRadius` is `@hide` *and*
 * turns the automatic crop off outright.
 *
 * ### What it costs
 *
 * A `TextureView` is copied into the window each frame instead of being
 * composited straight to the display, and it composites on the UI thread's
 * hardware canvas. That is a real cost and it is why the full-bleed hosts keep
 * the `SurfaceView`: at 44% of the window, per frame, the hole punch is worth
 * having. At a 260 dp band inside a card it is not worth a class of bug that
 * cannot be fixed from app code.
 *
 * Everything that actually renders lives in [GlobeRenderHost]; this class is the
 * `TextureView` half of the two hosts that share it.
 */
internal class GlobeTextureView(context: Context) : TextureView(context), GlobeHostView {

    override val host = GlobeRenderHost(this) { helper -> helper.attachTo(this) }

    init {
        // The globe fills its box with its own backdrop, so there is nothing to
        // blend against and the opaque path is the cheaper one. This mirrors
        // `UiHelper.isOpaque` in the render host; the two must agree.
        isOpaque = true
    }

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
