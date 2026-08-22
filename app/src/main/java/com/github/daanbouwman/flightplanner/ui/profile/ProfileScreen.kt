package com.github.daanbouwman.flightplanner.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.daanbouwman.flightplanner.R
import com.github.daanbouwman.flightplanner.core.designsystem.components.DevicePreviews
import com.github.daanbouwman.flightplanner.core.designsystem.components.EmptyState
import com.github.daanbouwman.flightplanner.core.designsystem.components.LightDarkPreview
import com.github.daanbouwman.flightplanner.core.designsystem.theme.FlightPlannerTheme
import com.github.daanbouwman.flightplanner.ui.SettingsAction
import com.github.daanbouwman.flightplanner.ui.chrome.LocalAppChromeState
import com.github.daanbouwman.flightplanner.ui.chrome.ScreenBottomGutter
import com.github.daanbouwman.flightplanner.ui.chrome.ScreenCompactTopGutter
import com.github.daanbouwman.flightplanner.ui.chrome.ScreenHorizontalGutter
import com.github.daanbouwman.flightplanner.ui.chrome.ScreenTopGutter
import com.github.daanbouwman.flightplanner.ui.chrome.WideMaxContentWidth
import com.github.daanbouwman.flightplanner.ui.chrome.ScrollToTopOnReselect
import com.github.daanbouwman.flightplanner.ui.chrome.isCompactHeight
import com.github.daanbouwman.flightplanner.ui.chrome.rememberChromeScrollConnection
import com.github.daanbouwman.flightplanner.ui.chrome.rememberContentInsets
import com.github.daanbouwman.flightplanner.ui.logbook.LogbookRow
import com.github.daanbouwman.flightplanner.ui.logbook.LogbookScreen

/**
 * Shared container for the Logbook and Stats section screens.
 *
 * Provides consistent insets, responsive centering up to [WideMaxContentWidth],
 * app header affordance, and chrome scroll handling across both top-level
 * sections without duplicating the layout scaffolding.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    segment: ProfileSegment,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenRoute: (LogbookRow) -> Unit = {},
) {
    val listState = rememberLazyListState()
    val chrome = LocalAppChromeState.current

    // Only meaningful while Logbook is showing — listState is not attached to
    // any LazyColumn on the Stats segment, so a reselect there would have
    // nothing to scroll.
    if (segment == ProfileSegment.Logbook) {
        ScrollToTopOnReselect(listState = listState)
    }

    LaunchedEffect(segment) {
        if (segment != ProfileSegment.Logbook) chrome.show()
    }

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
        val contentPadding = PaddingValues(
            start = horizontalStart,
            end = horizontalEnd,
            top = topClearance,
            bottom = bottomPadding,
        )

        val header: @Composable () -> Unit = {
            ProfileHeader(
                titleRes = segment.labelRes,
                onOpenSettings = onOpenSettings,
            )
        }

        when (segment) {
            ProfileSegment.Logbook -> LogbookScreen(
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
                header = header,
                modifier = Modifier.padding(top = topClearance),
            )

            ProfileSegment.Stats -> StatsPlaceholder(contentPadding = contentPadding, header = header)
        }
    }
}

/** Which view Profile is currently showing. Local UI state, not a nav destination — see the class KDoc. */
enum class ProfileSegment { Logbook, Stats }

@Composable
private fun ProfileHeader(
    titleRes: Int,
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
                text = stringResource(titleRes),
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

private val ProfileSegment.iconRes: Int
    get() = when (this) {
        ProfileSegment.Logbook -> R.drawable.ic_nav_logbook
        ProfileSegment.Stats -> R.drawable.ic_nav_stats
    }

private val ProfileSegment.labelRes: Int
    get() = when (this) {
        ProfileSegment.Logbook -> R.string.destination_logbook
        ProfileSegment.Stats -> R.string.destination_stats
    }

@Composable
private fun StatsPlaceholder(
    contentPadding: PaddingValues,
    header: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding),
    ) {
        header()
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            EmptyState(
                title = stringResource(R.string.stats_empty_title),
                message = stringResource(R.string.stats_empty_message),
            )
        }
    }
}

@LightDarkPreview
@DevicePreviews
@Composable
private fun ProfileScreenPreview() {
    FlightPlannerTheme(dynamicColor = false) {
        ProfileScreen(segment = ProfileSegment.Logbook, onOpenSettings = {})
    }
}
