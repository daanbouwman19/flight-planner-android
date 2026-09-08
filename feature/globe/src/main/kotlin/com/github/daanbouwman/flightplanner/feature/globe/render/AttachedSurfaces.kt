package com.github.daanbouwman.flightplanner.feature.globe.render

/**
 * A surface holding Filament objects of its own that must go before the shared
 * engine they point into does.
 *
 * Implemented by [GlobeSurfaceView], which owns a `Renderer`, a `View`, a camera
 * and — the one that bites — a [RouteRibbon] carrying two `MaterialInstance`s of
 * a material [GlobeScene] destroys.
 */
internal fun interface BackgroundReleasable {
    fun releaseForBackground()
}

/**
 * The surfaces currently attached to the one session.
 *
 * Registration is by identity and a set, so a view that re-registers when it
 * rebuilds after a background release is still released exactly once — releasing
 * the same Filament objects twice is a double free.
 */
internal class AttachedSurfaces {
    private val surfaces = linkedSetOf<BackgroundReleasable>()

    fun register(surface: BackgroundReleasable) {
        surfaces += surface
    }

    fun unregister(surface: BackgroundReleasable) {
        surfaces -= surface
    }

    /**
     * Copied before iterating: a surface may detach — and so unregister — from
     * inside its own release, and a `ConcurrentModificationException` in a
     * teardown path is a crash in the same place the crash this exists to
     * prevent was.
     */
    fun releaseAll() {
        surfaces.toList().forEach(BackgroundReleasable::releaseForBackground)
    }
}

/**
 * Release every attached surface, **then** destroy what they were pointing into.
 * Never the reverse.
 *
 * The reverse is a real crash rather than a tidiness argument: Filament refuses
 * `destroyMaterial` while any `MaterialInstance` of it is alive, so destroying
 * the session while a surface still holds its ribbon kills the app with
 * `destroying material "globeOverlay" but 2 instances still alive`. It used to
 * be impossible to get wrong, because the only teardown ran after the last view
 * had detached and a detach is what destroys a ribbon. Backgrounding is a
 * teardown with nothing detached, which is what made the order explicit.
 */
internal fun forcedTeardown(surfaces: AttachedSurfaces, destroySession: () -> Unit) {
    surfaces.releaseAll()
    destroySession()
}
