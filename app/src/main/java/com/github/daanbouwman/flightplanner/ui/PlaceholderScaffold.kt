package com.github.daanbouwman.flightplanner.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.github.daanbouwman.flightplanner.core.designsystem.components.EmptyState

/**
 * The frame every not-yet-built screen shares: a title and one empty state.
 *
 * Insets are the part worth reading. The window is edge to edge, and three things
 * want to pad against the same system bars — the navigation suite, the top app bar
 * and the content — so each edge must be padded exactly once, or the layout either
 * gains a band of dead space or draws underneath the system UI.
 *
 * Hard-coding which edge belongs to whom does not work, because it changes with
 * width: on compact the navigation suite is a bottom bar and owns the bottom
 * inset, but on medium and expanded — including any phone in landscape — it is a
 * rail or drawer that owns the *start* inset and leaves the bottom to us. A fixed
 * `only(Horizontal)` is correct for the first case and wrong for the second,
 * where it leaves the gesture inset unowned and double-pads the start edge.
 *
 * So nothing is hard-coded. The scaffold claims no insets of its own, and the
 * content uses [consumeWindowInsets] plus [windowInsetsPadding], which **are**
 * consumption-aware: they pad only what the navigation suite and the app bar have
 * not already taken. That is one rule that stays correct in every configuration
 * instead of a rule per configuration. (Material3's `Scaffold` reads
 * `contentWindowInsets` directly and is *not* consumption-aware, which is why the
 * work happens on the content modifier rather than in that parameter.)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceholderScaffold(
    title: String,
    emptyTitle: String,
    emptyMessage: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    /** Null on the Settings screen itself, which has nowhere to go. */
    onOpenSettings: (() -> Unit)? = null,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(title) },
                actions = { onOpenSettings?.let { SettingsAction(onClick = it) } },
            )
        },
        contentWindowInsets = WindowInsets(0),
    ) { contentPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .consumeWindowInsets(contentPadding)
                .windowInsetsPadding(WindowInsets.safeDrawing),
            contentAlignment = Alignment.Center,
        ) {
            EmptyState(
                title = emptyTitle,
                message = emptyMessage,
                actionLabel = actionLabel,
                onAction = onAction,
            )
        }
    }
}
