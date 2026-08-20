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
import com.github.daanbouwman.flightplanner.core.designsystem.components.EmptyState
import com.github.daanbouwman.flightplanner.ui.SettingsAction
import com.github.daanbouwman.flightplanner.ui.chrome.LocalAppChromeState
import com.github.daanbouwman.flightplanner.ui.chrome.MaxContentWidth
import com.github.daanbouwman.flightplanner.ui.chrome.ScrollToTopOnReselect
import com.github.daanbouwman.flightplanner.ui.chrome.isCompactHeight
import com.github.daanbouwman.flightplanner.ui.chrome.rememberChromeScrollConnection
import com.github.daanbouwman.flightplanner.ui.chrome.rememberContentInsets
import com.github.daanbouwman.flightplanner.ui.logbook.LogbookScreen

/**
 * The Profile section: your flying, at two levels of zoom, with the way to
 * Settings one tap deeper than either.
 *
 * ### Why one screen with a switch, not two more destinations
 *
 * Logbook and Stats are the same subject at two levels of zoom — the records
 * and the summary drawn from them — so they are views inside one section
 * rather than two more stops on the navigation bar. [ProfileSegment] is plain
 * [rememberSaveable] state, not a nav destination and not a ViewModel's: both
 * segments' data sources exist regardless of which is showing, so switching
 * between them is exactly the kind of thing
 * [com.github.daanbouwman.flightplanner.ui.plan.PlanScreen] keeps local rather
 * than routing through a ViewModel — see its own picker state for the same
 * reasoning.
 *
 * A single [androidx.compose.foundation.lazy.LazyListState], content-inset
 * calculation and chrome connection are owned once here and handed to whichever
 * segment renders, so both read the same insets, the same centring and the
 * same scroll-hiding behaviour without either duplicating the plumbing
 * [com.github.daanbouwman.flightplanner.ui.plan.PlanScreen] owns for its own,
 * single list.
 *
 * ### The trap a segment switch falls into
 *
 * [rememberChromeScrollConnection]'s own auto-reveal fires only from an
 * attached list's layout events. Scroll the Logbook down — hiding the
 * navigation bar — then switch to Stats, which has no list of its own, and
 * nothing would ever bring the bar back: no layout event would arrive to
 * trigger it. The effect below force-shows the chrome on every segment change
 * away from Logbook, the same principle [rememberChromeScrollConnection]
 * already applies at the top of a list and on leaving the screen entirely.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var segment by rememberSaveable { mutableStateOf(ProfileSegment.Logbook) }
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
            ((availableWidth - MaxContentWidth) / 2).coerceAtLeast(0.dp)
        }
        val contentPadding = PaddingValues(
            start = insets.calculateStartPadding(layoutDirection) + HorizontalGutter + slack,
            end = insets.calculateEndPadding(layoutDirection) + HorizontalGutter + slack,
            top = insets.calculateTopPadding() + if (compactHeight) CompactTopGutter else TopGutter,
            bottom = insets.calculateBottomPadding() + BottomGutter,
        )

        val header: @Composable () -> Unit = {
            ProfileHeader(
                segment = segment,
                onSegmentChange = { segment = it },
                onOpenSettings = onOpenSettings,
            )
        }

        when (segment) {
            ProfileSegment.Logbook -> LogbookScreen(
                listState = listState,
                contentPadding = contentPadding,
                header = header,
            )

            ProfileSegment.Stats -> StatsPlaceholder(contentPadding = contentPadding, header = header)
        }
    }
}

/** Which view Profile is currently showing. Local UI state, not a nav destination — see the class KDoc. */
enum class ProfileSegment { Logbook, Stats }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileHeader(
    segment: ProfileSegment,
    onSegmentChange: (ProfileSegment) -> Unit,
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
                text = stringResource(R.string.destination_profile),
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
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            ProfileSegment.entries.forEachIndexed { index, option ->
                val selected = segment == option
                SegmentedButton(
                    selected = selected,
                    onClick = { if (!selected) onSegmentChange(option) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = ProfileSegment.entries.size),
                    icon = {
                        Icon(
                            painter = painterResource(option.iconRes),
                            // The label is drawn alongside, so describing the
                            // icon too would read the segment twice.
                            contentDescription = null,
                        )
                    },
                ) {
                    Text(stringResource(option.labelRes))
                }
            }
        }
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

/** The card column's margin. Matches PlanScreen's own — see that file for why this is duplicated rather than shared. */
private val HorizontalGutter = 16.dp

/** Space between the status bar and the heading. */
private val TopGutter = 8.dp

/** Nothing to spare above the title in a short window. */
private val CompactTopGutter = 0.dp

/** Space below the last card, so it does not end flush against the bottom inset. */
private val BottomGutter = 24.dp
