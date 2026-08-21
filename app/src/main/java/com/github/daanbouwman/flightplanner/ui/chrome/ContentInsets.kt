package com.github.daanbouwman.flightplanner.ui.chrome

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.onConsumedWindowInsetsChanged
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/**
 * The system-bar insets a screen still owns, as a [PaddingValues] it can hand to a
 * scrolling list's `contentPadding`.
 *
 * ### Why this is not one line
 *
 * An immersive list wants its insets as **content padding**, not as padding on the
 * container: padding the container stops the list at the status bar, whereas
 * padding its content lets the first card scroll up *behind* it. That much is easy.
 * The hard half is that only some of the insets belong to the screen. The
 * navigation suite consumes the bottom edge when it is a bar and the start edge
 * when it is a rail, and it consumes nothing at all once it is hidden — so which
 * edges are the screen's changes with the window width *and* with the chrome state.
 *
 * The consumption-aware modifiers — `windowInsetsPadding`, `consumeWindowInsets` —
 * solve exactly this, but they produce a *modifier*, and a modifier cannot become a
 * list's content padding. `WindowInsets.safeDrawing.asPaddingValues()` reads the
 * raw window insets and knows nothing about what an ancestor already took, so on
 * its own it double-pads under a rail.
 *
 * So this reads the consumption directly: [modifier] observes what ancestors have
 * consumed at that point in the tree, and [asPaddingValues] subtracts it. One rule
 * that stays correct in every configuration, rather than a rule per configuration.
 *
 * ```kotlin
 * val contentInsets = rememberContentInsets()
 * Box(Modifier.fillMaxSize().then(contentInsets.modifier)) {
 *     val insets = contentInsets.asPaddingValues()
 *     LazyColumn(contentPadding = PaddingValues(bottom = insets.calculateBottomPadding())) { … }
 * }
 * ```
 *
 * [modifier] must be attached *outside* the composable that reads [asPaddingValues],
 * which the shape above gets right: the padding is read inside the `Box` whose
 * consumption the modifier reports.
 */
@Stable
class ContentInsets internal constructor() {

    private var consumed by mutableStateOf(WindowInsets(0))

    /** Attach to the container whose remaining insets you want. */
    @OptIn(ExperimentalLayoutApi::class)
    val modifier: Modifier = Modifier.onConsumedWindowInsetsChanged { consumed = it }

    /** [insets] minus whatever the ancestors of [modifier] have already consumed. */
    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    fun asPaddingValues(insets: WindowInsets = WindowInsets.safeDrawing): PaddingValues =
        insets.exclude(consumed).asPaddingValues()
}

/** Remembers a [ContentInsets] for this screen. */
@Composable
fun rememberContentInsets(): ContentInsets = remember { ContentInsets() }
