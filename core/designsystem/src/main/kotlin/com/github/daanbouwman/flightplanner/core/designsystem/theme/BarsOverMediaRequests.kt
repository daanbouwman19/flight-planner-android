package com.github.daanbouwman.flightplanner.core.designsystem.theme

import androidx.compose.runtime.mutableStateOf

/**
 * How many callers currently want light status-bar glyphs over their own content.
 *
 * **A count, not a flag.** [SystemBarsOverMedia] has more than one caller, and
 * their compositions overlap: a navigation transition composes the incoming
 * screen before it disposes the outgoing one, so the outgoing screen's dispose
 * runs *after* the incoming screen has already asked. A boolean each caller
 * writes on the way out cannot say "somebody else still wants this" — it can
 * only say who wrote last — and the immersive globe shipped with dark glyphs
 * over full-bleed satellite imagery for exactly that reason, taking the route
 * detail's own status bar down with it until the app was restarted.
 *
 * Raising and lowering are therefore symmetric and order-independent: whichever
 * order two callers arrive and leave in, the glyphs follow whether *any* of them
 * is still asking. `BarsOverMediaRequestsTest` is that sentence as a test.
 */
internal class BarsOverMediaRequests {
    private val raised = mutableStateOf(0)

    /**
     * Whether anything currently wants light glyphs.
     *
     * Backed by snapshot state, so reading this from composition is what
     * subscribes [FlightPlannerTheme]'s bar writer to changes — see
     * `SystemBarsAppearance`, which must read it in composition rather than
     * inside its `SideEffect`.
     */
    val isRaised: Boolean get() = raised.value > 0

    fun raise() {
        raised.value++
    }

    /**
     * Coerced at zero: a dispose without a matching raise must not leave the
     * count *owing* a raise, or the next screen that genuinely wants light
     * glyphs is ignored for a reason nothing on screen can explain.
     */
    fun lower() {
        raised.value = (raised.value - 1).coerceAtLeast(0)
    }
}
