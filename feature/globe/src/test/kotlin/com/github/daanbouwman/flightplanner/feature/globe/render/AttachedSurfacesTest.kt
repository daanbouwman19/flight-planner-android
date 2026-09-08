package com.github.daanbouwman.flightplanner.feature.globe.render

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlin.test.Test

/**
 * The teardown ordering rule, and the crash that proves it is worth stating.
 *
 * Every attached surface owns a [RouteRibbon], and a ribbon holds two
 * `MaterialInstance`s of the shared `globeOverlay` material. Filament refuses to
 * destroy a material while an instance of it is alive, so the surfaces have to
 * let go of theirs *before* `GlobeScene.destroy()` frees the material.
 *
 * That ordering used to be implicit in "the last view detaches, then the timer
 * runs", which held only because a detach is what destroys a ribbon. Then
 * backgrounding was given its own teardown — and backgrounding detaches nothing,
 * so the session destroyed the material out from under two live instances and
 * the app died with `destroying material "globeOverlay" but 2 instances still
 * alive`. Every launch, on Home, with a globe on screen.
 *
 * Filament itself cannot be reached from a JVM test, but the rule it enforced
 * can: [forcedTeardown] releases every surface first, always, and
 * `forcedTeardown releases every surface before destroying the session` is that
 * sentence written down.
 */
class AttachedSurfacesTest {

    /** Records what happened, in the order it happened, across surfaces and session. */
    private val log = mutableListOf<String>()

    private fun surface(name: String, onRelease: () -> Unit = {}) =
        BackgroundReleasable {
            log += "release:$name"
            onRelease()
        }

    @Test
    fun `releaseAll releases every registered surface`() {
        val surfaces = AttachedSurfaces()
        surfaces.register(surface("hero"))
        surfaces.register(surface("immersive"))

        surfaces.releaseAll()

        log shouldContainExactly listOf("release:hero", "release:immersive")
    }

    @Test
    fun `forcedTeardown releases every surface before destroying the session`() {
        // The crash, as an ordering assertion: both releases must land before
        // the destroy, because the destroy is what frees the material their
        // ribbons hold instances of.
        val surfaces = AttachedSurfaces()
        surfaces.register(surface("hero"))
        surfaces.register(surface("immersive"))

        forcedTeardown(surfaces) { log += "destroy:session" }

        log shouldContainExactly listOf(
            "release:hero",
            "release:immersive",
            "destroy:session",
        )
    }

    @Test
    fun `an unregistered surface is not released`() {
        val surfaces = AttachedSurfaces()
        val detached = surface("detached")
        surfaces.register(detached)
        surfaces.unregister(detached)

        surfaces.releaseAll()

        log.shouldBeEmpty()
    }

    @Test
    fun `registering the same surface twice releases it once`() {
        // A view re-registers when it rebuilds after a background release; that
        // must not queue a second release of the same Filament objects, which
        // is a double free.
        val surfaces = AttachedSurfaces()
        val hero = surface("hero")
        surfaces.register(hero)
        surfaces.register(hero)

        surfaces.releaseAll()

        log shouldContainExactly listOf("release:hero")
    }

    @Test
    fun `a surface unregistering itself while releasing does not throw`() {
        // onDetachedFromWindow can land in the middle of a teardown — the
        // iteration has to be over a copy, not the live set.
        val surfaces = AttachedSurfaces()
        lateinit var selfRemoving: BackgroundReleasable
        selfRemoving = surface("selfRemoving") { surfaces.unregister(selfRemoving) }
        surfaces.register(selfRemoving)
        surfaces.register(surface("other"))

        surfaces.releaseAll()

        log shouldContainExactly listOf("release:selfRemoving", "release:other")
    }

    @Test
    fun `forcedTeardown with no surfaces still destroys the session`() {
        // The ordinary path — the timer firing after the last view detached —
        // has nothing left to release and must be unaffected.
        forcedTeardown(AttachedSurfaces()) { log += "destroy:session" }

        log shouldContainExactly listOf("destroy:session")
    }

    private fun MutableList<String>.shouldBeEmpty() {
        isEmpty() shouldBe true
    }
}
