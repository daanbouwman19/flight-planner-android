@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.github.daanbouwman.flightplanner.core.designsystem.motion

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/**
 * The app's motion vocabulary.
 *
 * Every member is a **thin named alias over [MaterialTheme.motionScheme]**. None
 * of them writes a spring constant, and that is the entire point.
 *
 * `MotionScheme.expressive()` already carries the tuned pairs — spatial
 * 0.8 / 380, 0.6 / 800, 0.8 / 200; effects 1.0 / 1600, 1.0 / 3800, 1.0 / 800 —
 * and re-deriving them by hand at each call site would guarantee they drift.
 * Worse, it would push the wrong decision onto the screen: a screen knows it is
 * moving a card, not that it wants `dampingRatio = 0.8f`. So screens name an
 * *intent* — "this is spatial", "this is an effect" — and the physics stays in
 * one place where it can be retuned for the whole app at once.
 *
 * ### Why spatial and effects are damped differently
 *
 * **Spatial springs are underdamped (ratio 0.8, and 0.6 for the fast one).**
 * They overshoot slightly and settle back. That overshoot *is* the expressive
 * character: a card that arrives, leans past its resting place and returns reads
 * as having mass. Use them for anything that moves or resizes.
 *
 * **Effects springs are critically damped (ratio 1.0).** A colour or an alpha
 * that overshoots does not read as momentum, it reads as a flicker — the eye
 * sees a value that got brighter than it was ever meant to be and then dimmed.
 * Use them for fades, colour changes and elevation.
 *
 * ### Reduce motion
 *
 * Compose already shortens spring-driven animations to nothing when the system
 * animator duration scale is zero, so these specs need no special-casing. What
 * *does* need it is anything that runs forever or stages itself — shimmer,
 * stagger, count-ups. Those must consult [rememberReduceMotion] and switch off,
 * not merely speed up.
 */
object FlightMotion {

    /** Default spatial spring. Anything that moves or resizes. */
    @Composable
    fun <T> spatial(): FiniteAnimationSpec<T> = MaterialTheme.motionScheme.defaultSpatialSpec()

    /** Fast spatial spring, for small, frequent movement — a chip, a toggle, the FAB morph. */
    @Composable
    fun <T> spatialFast(): FiniteAnimationSpec<T> = MaterialTheme.motionScheme.fastSpatialSpec()

    /** Slow spatial spring, for large surfaces — a sheet, a detail pane. */
    @Composable
    fun <T> spatialSlow(): FiniteAnimationSpec<T> = MaterialTheme.motionScheme.slowSpatialSpec()

    /** Default effects spring. Fades, colour, alpha, elevation. */
    @Composable
    fun <T> effects(): FiniteAnimationSpec<T> = MaterialTheme.motionScheme.defaultEffectsSpec()

    /** Fast effects spring, for state a user triggers repeatedly — press, hover, selection. */
    @Composable
    fun <T> effectsFast(): FiniteAnimationSpec<T> = MaterialTheme.motionScheme.fastEffectsSpec()

    /** Slow effects spring, for a fade that is meant to be noticed — a chip resolving from unknown. */
    @Composable
    fun <T> effectsSlow(): FiniteAnimationSpec<T> = MaterialTheme.motionScheme.slowEffectsSpec()

    /** Delay between successive list items entering. */
    const val EnterStaggerMillis: Int = 30

    /**
     * How many items the stagger applies to before it flattens out.
     *
     * A stagger that keeps growing turns a 50-item batch into a 1.5-second
     * cascade, which is a performance in a list the user scrolls a hundred times
     * a session. Past this index every item shares the same delay and arrives
     * together.
     */
    const val EnterStaggerCap: Int = 8

    /**
     * Upper bound for a one-shot emphasis — a value counting up, a confirmation
     * pulse. Long enough to be seen, short enough not to be waited on.
     */
    const val EmphasisMillis: Int = 500

    /** Entrance delay for the item at [index], capped per [EnterStaggerCap]. */
    fun enterDelayMillis(index: Int): Int =
        index.coerceIn(0, EnterStaggerCap) * EnterStaggerMillis

    /**
     * Screen entrance: a fade-through with a slight scale-up.
     *
     * The fade runs on an effects spring (no alpha overshoot) and the scale on a
     * spatial one (a little overshoot), which is the whole spatial/effects split
     * expressed in a single transition. Where a real element persists across the
     * navigation, prefer `sharedBounds` over this — a shared element explains the
     * move, a fade only covers it.
     */
    @Composable
    fun navEnter(): EnterTransition =
        fadeIn(animationSpec = effects()) + scaleIn(animationSpec = spatial(), initialScale = 0.94f)

    /** Screen exit, the mirror of [navEnter]. */
    @Composable
    fun navExit(): ExitTransition =
        fadeOut(animationSpec = effects()) + scaleOut(animationSpec = spatial(), targetScale = 1.04f)
}

/**
 * Whether the user has asked the system for no animation.
 *
 * **Read this, do not call [rememberReduceMotion].** [FlightPlannerTheme] resolves
 * the setting once and provides it here, so a screen full of components shares one
 * observer. Calling the `remember` function per component instead registers a
 * `ContentObserver` per component — a ten-card skeleton list is thirty binder
 * round-trips on appear and thirty more on dispose, all on the loading path that
 * the skeletons exist to keep smooth.
 *
 * Defaults to `false` outside a theme, which is the safe direction: animations
 * play. A preview or a test that has not gone through [FlightPlannerTheme] should
 * look normal rather than mysteriously static.
 */
val LocalReduceMotion: ProvidableCompositionLocal<Boolean> = staticCompositionLocalOf { false }

/**
 * Resolves the reduce-motion setting, observing changes.
 *
 * Call this **once**, in [FlightPlannerTheme], and read [LocalReduceMotion]
 * everywhere else — see the note there.
 *
 * Reads `Settings.Global.ANIMATOR_DURATION_SCALE`; zero means "off". Compose
 * honours that scale for its own spring and tween animations already, so this is
 * not needed to shorten them. It is needed for the things the scale cannot
 * reach:
 *
 *  - **infinite animations** — a shimmer at scale zero does not stop, it runs at
 *    an undefined speed. It has to be switched off explicitly.
 *  - **staged sequences** — a stagger is a series of delays, not an animation,
 *    and delays are not scaled.
 *  - **count-ups**, which are a data presentation that happens to be animated.
 *
 * The value is observed, not sampled once: the user can change it in Developer
 * Options while the app is running and the UI should follow without a restart.
 */
@Composable
fun rememberReduceMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    var reduceMotion by remember(resolver) { mutableStateOf(animatorDurationScaleIsZero(resolver)) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reduceMotion = animatorDurationScaleIsZero(resolver)
            }
        }
        resolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            observer,
        )
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return reduceMotion
}

private fun animatorDurationScaleIsZero(resolver: android.content.ContentResolver): Boolean =
    Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
