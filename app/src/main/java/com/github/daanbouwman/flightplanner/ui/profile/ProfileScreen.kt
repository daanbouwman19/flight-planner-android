package com.github.daanbouwman.flightplanner.ui.profile

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.daanbouwman.flightplanner.R
import com.github.daanbouwman.flightplanner.core.designsystem.components.DevicePreviews
import com.github.daanbouwman.flightplanner.core.designsystem.components.LightDarkPreview
import com.github.daanbouwman.flightplanner.core.designsystem.theme.FlightPlannerTheme
import com.github.daanbouwman.flightplanner.ui.SettingsAction
import com.github.daanbouwman.flightplanner.ui.chrome.ScreenBottomGutter
import com.github.daanbouwman.flightplanner.ui.chrome.ScreenCompactTopGutter
import com.github.daanbouwman.flightplanner.ui.chrome.ScreenHorizontalGutter
import com.github.daanbouwman.flightplanner.ui.chrome.ScreenTopGutter
import com.github.daanbouwman.flightplanner.ui.chrome.ScrollToTopOnReselect
import com.github.daanbouwman.flightplanner.ui.chrome.WideMaxContentWidth
import com.github.daanbouwman.flightplanner.ui.chrome.isCompactHeight
import com.github.daanbouwman.flightplanner.ui.chrome.rememberChromeScrollConnection
import com.github.daanbouwman.flightplanner.ui.chrome.rememberContentInsets
import com.github.daanbouwman.flightplanner.ui.logbook.LogbookRow
import com.github.daanbouwman.flightplanner.ui.logbook.LogbookScreen

/**
 * The Logbook's frame: insets, centring up to [WideMaxContentWidth], the app
 * header and the chrome-retracting scroll connection.
 *
 * It was the "Profile" section's container, with a `ProfileSegment` enum that
 * switched between Logbook and Stats. Stats then became a section of its own and
 * the enum was left with one value — a `when` with one branch, a segmented
 * control that was never drawn, a `LaunchedEffect` that could not fire. Only
 * the frame was ever load-bearing, and it is what is left. Kept as its own
 * composable rather than folded into `LogbookScreen` because `LogbookRoute`
 * composes it on both sides of its pane split.
 */
@Composable
fun ProfileScreen(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenRoute: (LogbookRow) -> Unit = {},
) {
    val listState = rememberLazyListState()
    ScrollToTopOnReselect(listState = listState)

    val chromeScroll = rememberChromeScrollConnection(listState = listState)
    val contentInsets = rememberContentInsets()

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .then(contentInsets.modifier)
            .nestedScroll(chromeScroll),
    ) {
        val availableWidth = maxWidth
        val insets = contentInsets.asPaddingValues()
        val layoutDirection = LocalLayoutDirection.current
        val compactHeight = isCompactHeight()
        val slack = if (compactHeight) {
            0.dp
        } else {
            ((availableWidth - WideMaxContentWidth) / 2).coerceAtLeast(0.dp)
        }
        val horizontalStart = insets.calculateStartPadding(layoutDirection) + ScreenHorizontalGutter + slack
        val horizontalEnd = insets.calculateEndPadding(layoutDirection) + ScreenHorizontalGutter + slack
        val topClearance = insets.calculateTopPadding() +
            if (compactHeight) ScreenCompactTopGutter else ScreenTopGutter
        val bottomPadding = insets.calculateBottomPadding() + ScreenBottomGutter

        LogbookScreen(
            onOpenRoute = onOpenRoute,
            listState = listState,
            // A sticky month header pins to the LazyColumn's own top edge, not
            // to its contentPadding — Compose's stickyHeader ignores content
            // padding when it clamps a header's pinned offset, so the top
            // clearance moves to real modifier padding below, which a pinned
            // header cannot cross. contentPadding's own top is zeroed out here
            // so the clearance isn't reserved twice.
            contentPadding = PaddingValues(
                start = horizontalStart,
                end = horizontalEnd,
                bottom = bottomPadding,
            ),
            header = { ProfileHeader(onOpenSettings = onOpenSettings) },
            modifier = Modifier.padding(top = topClearance),
        )
    }
}

@Composable
private fun ProfileHeader(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val compactHeight = isCompactHeight()
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.destination_logbook),
                style = if (compactHeight) {
                    MaterialTheme.typography.titleLarge
                } else {
                    MaterialTheme.typography.headlineMedium
                },
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            SettingsAction(onClick = onOpenSettings)
        }
        Spacer(Modifier.height(if (compactHeight) 6.dp else 12.dp))
    }
}

@LightDarkPreview
@DevicePreviews
@Composable
private fun ProfileScreenPreview() {
    FlightPlannerTheme(dynamicColor = false) {
        ProfileScreen(onOpenSettings = {})
    }
}
