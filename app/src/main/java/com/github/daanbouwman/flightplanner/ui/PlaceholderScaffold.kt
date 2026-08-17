package com.github.daanbouwman.flightplanner.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
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
 * Insets are the part worth reading. The window is edge to edge, and three
 * things want to pad against the same system bars — the navigation suite, the
 * top app bar and the content — so exactly one must own each edge or the padding
 * is applied twice and the layout gains a visible band of dead space.
 *
 * Top belongs to [TopAppBar], which pads for the status bar itself. Bottom
 * belongs to the navigation suite, which sits in that space on compact widths.
 * That leaves the horizontal edges — cutouts in landscape, and gesture insets —
 * to this scaffold, which is exactly what [contentWindowInsets] is narrowed to.
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
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text(title) }) },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
    ) { contentPadding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(contentPadding),
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
