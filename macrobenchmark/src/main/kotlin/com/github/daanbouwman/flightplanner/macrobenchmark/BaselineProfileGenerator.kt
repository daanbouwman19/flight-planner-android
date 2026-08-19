package com.github.daanbouwman.flightplanner.macrobenchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Writes the app's own baseline profile — P1.
 *
 * A baseline profile is a list of methods and classes ART compiles ahead of time
 * at install, instead of leaving them to be interpreted until the JIT catches up.
 * The APK already carries one: AGP merges the profiles the AndroidX libraries
 * ship, which is 7,472 bytes covering Compose and friends. What it has never
 * carried is a profile for *this app's* code, and that gap is what this class
 * closes.
 *
 * ### Run it, don't build it
 *
 * ```bash
 * ./gradlew :app:generateBaselineProfile
 * ```
 *
 * This installs two APKs, drives the app on a physical device and copies the
 * result into `app/src/main/generated/baselineProfiles/`, which is committed.
 * `automaticGenerationDuringBuild` is off, so no ordinary build triggers it.
 *
 * ### Why the target is `nonMinifiedRelease`
 *
 * The rules name classes and methods, so they have to be collected from a build
 * whose names are the source's. Collected from a minified build they would read
 * `La/b/c;->d()`, which is meaningless the moment R8 shuffles the mapping on the
 * next build. `androidx.baselineprofile` creates `nonMinifiedRelease` for exactly
 * this, and it is the only thing that variant is for — every published number
 * still comes from `benchmarkRelease`.
 *
 * ### Two journeys, because startup is a different question
 *
 * [startup] is marked as a startup profile and [planScroll] is not. The
 * distinction is not cosmetic: a startup profile is the subset ART is asked to
 * treat as launch-critical, and it is what a dex layout optimisation would later
 * reorder the primary dex around. Folding a five-second fling into it would
 * declare half the app launch-critical and make that later lever useless.
 *
 * The two journeys deliberately mirror [StartupBenchmark] and
 * [PlanScrollBenchmark]. A profile is a claim about which code is hot, and the
 * only evidence this repo has for that is what those two measure — so they
 * exercise the same paths through the same helpers rather than a third, invented
 * script that drifts from both.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    /** Cold launch to a populated route list — the launch-critical path. */
    @Test
    fun startup() = rule.collect(
        packageName = TARGET_PACKAGE,
        includeInStartupProfile = true,
    ) {
        pressHome()
        startActivityAndWait()
        awaitRouteList()
    }

    /**
     * The fling, which is warm code rather than launch code.
     *
     * `awaitRouteListSettled` is here for the same reason it is in
     * `PlanScrollBenchmark`: without it the entrance animation is still running
     * and the profile records the tail of a spring as though it were scrolling.
     */
    @Test
    fun planScroll() = rule.collect(
        packageName = TARGET_PACKAGE,
        includeInStartupProfile = false,
    ) {
        pressHome()
        startActivityAndWait()
        awaitRouteListSettled()
        flingRouteList(awaitRouteList())
    }
}
