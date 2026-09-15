package com.github.daanbouwman.flightplanner.ui.stats

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.daanbouwman.flightplanner.R
import com.github.daanbouwman.flightplanner.core.designsystem.components.CompactWidthPreview
import com.github.daanbouwman.flightplanner.core.designsystem.components.DevicePreviews
import com.github.daanbouwman.flightplanner.core.designsystem.components.EmptyState
import com.github.daanbouwman.flightplanner.core.designsystem.components.LightDarkPreview
import com.github.daanbouwman.flightplanner.core.designsystem.components.SkeletonBox
import com.github.daanbouwman.flightplanner.core.designsystem.components.SkeletonCard
import com.github.daanbouwman.flightplanner.core.designsystem.theme.FlightPlannerTheme
import com.github.daanbouwman.flightplanner.model.AircraftSpec
import com.github.daanbouwman.flightplanner.routing.WorldOutline
import com.github.daanbouwman.flightplanner.ui.SettingsAction
import com.github.daanbouwman.flightplanner.ui.chrome.ScreenBottomGutter
import com.github.daanbouwman.flightplanner.ui.chrome.ScreenCompactTopGutter
import com.github.daanbouwman.flightplanner.ui.chrome.ScreenHorizontalGutter
import com.github.daanbouwman.flightplanner.ui.chrome.ScreenTopGutter
import com.github.daanbouwman.flightplanner.ui.chrome.ScrollToTopOnReselect
import com.github.daanbouwman.flightplanner.ui.chrome.WideMaxContentWidth
import com.github.daanbouwman.flightplanner.ui.chrome.fadeUnderStatusBar
import com.github.daanbouwman.flightplanner.ui.chrome.isCompactHeight
import com.github.daanbouwman.flightplanner.ui.chrome.rememberChromeScrollConnection
import com.github.daanbouwman.flightplanner.ui.chrome.rememberContentInsets
import java.time.YearMonth

/**
 * The Stats Dashboard screen.
 */
@Composable
fun StatsScreen(
    uiState: StatsUiState,
    onSelectTimeframe: (StatsTimeframe) -> Unit,
    onSelectMetric: (ChartMetric) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenRoute: (LegStat) -> Unit,
    onOpenAircraft: (AircraftSpec) -> Unit,
    onPlanFlight: () -> Unit,
    worldOutline: WorldOutline,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
) {
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

        when (uiState) {
            is StatsUiState.Loading -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            start = horizontalStart,
                            end = horizontalEnd,
                            top = topClearance,
                            bottom = bottomPadding,
                        ),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    StatsHeader(onOpenSettings = onOpenSettings)
                    StatsLoadingSkeleton()
                }
            }

            is StatsUiState.Empty -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            start = horizontalStart,
                            end = horizontalEnd,
                            top = topClearance,
                            bottom = bottomPadding,
                        ),
                ) {
                    StatsHeader(onOpenSettings = onOpenSettings)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        EmptyState(
                            title = stringResource(R.string.stats_empty_title),
                            message = stringResource(R.string.stats_empty_message),
                            actionLabel = stringResource(R.string.stats_empty_action),
                            onAction = onPlanFlight,
                        )
                    }
                }
            }

            is StatsUiState.Success -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .fadeUnderStatusBar(insets.calculateTopPadding()),
                    contentPadding = PaddingValues(
                        start = horizontalStart,
                        end = horizontalEnd,
                        top = topClearance,
                        bottom = bottomPadding,
                    ),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    item(key = "header") {
                        StatsHeader(onOpenSettings = onOpenSettings)
                    }

                    item(key = "timeframe_filter") {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            items(StatsTimeframe.entries) { timeframe ->
                                FilterChip(
                                    selected = timeframe == uiState.timeframe,
                                    onClick = { onSelectTimeframe(timeframe) },
                                    label = { Text(stringResource(timeframe.labelRes)) },
                                )
                            }
                        }
                    }

                    if (uiState.totalFlights == 0) {
                        item(key = "timeframe_empty") {
                            EmptyState(
                                title = stringResource(R.string.stats_empty_timeframe_title),
                                message = stringResource(R.string.stats_empty_timeframe_message),
                            )
                        }
                    } else {
                        item(key = "hero_distance") {
                            HeroDistanceCard(
                                totalDistanceNm = uiState.totalDistanceNm,
                                earthCircumferences = uiState.earthCircumferences,
                            )
                        }

                        item(key = "monthly_activity") {
                            MonthlyActivityCard(
                                activity = uiState.monthlyActivity,
                                selectedMetric = uiState.chartMetric,
                                onSelectMetric = onSelectMetric,
                            )
                        }

                        if (uiState.visitedAirports.isNotEmpty()) {
                            item(key = "visited_network") {
                                VisitedNetworkCard(
                                    visitedAirports = uiState.visitedAirports,
                                    visitedLegs = uiState.visitedLegs,
                                    outline = worldOutline,
                                )
                            }
                        }

                        item(key = "metric_grid") {
                            MetricGrid(
                                totalFlights = uiState.totalFlights,
                                averageDistanceNm = uiState.averageDistanceNm,
                                longestFlight = uiState.longestFlight,
                                shortestFlight = uiState.shortestFlight,
                                onOpenRoute = onOpenRoute,
                            )
                        }

                        item(key = "top_aircraft") {
                            TopAircraftCard(
                                topAircraft = uiState.topAircraft,
                                onAircraftClick = onOpenAircraft,
                            )
                        }

                        item(key = "airport_highlights") {
                            AirportHighlightsCard(
                                favoriteDeparture = uiState.favoriteDeparture,
                                favoriteArrival = uiState.favoriteArrival,
                                mostVisitedAirport = uiState.mostVisitedAirport,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * The loaded list's silhouette, before the list is there.
 *
 * Three identical generic cards used to stand in for seven of different
 * shapes, and the point of a skeleton — that the layout does not jump when the
 * content lands — was lost at every one of them. Each placeholder here is a
 * card of the loaded list at roughly its resting height, in the loaded list's
 * order and spacing: the timeframe chips, the hero, the activity chart, the
 * network map, and the first row of the metric grid, which is as far down as a
 * phone shows before the fold. Nothing below it is drawn, because a placeholder
 * nobody sees is only a layout cost.
 */
@Composable
private fun StatsLoadingSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // The timeframe chips: three pills at a FilterChip's height.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SkeletonBox(modifier = Modifier.width(84.dp).height(32.dp), shape = MaterialTheme.shapes.small)
            SkeletonBox(modifier = Modifier.width(92.dp).height(32.dp), shape = MaterialTheme.shapes.small)
            SkeletonBox(modifier = Modifier.width(128.dp).height(32.dp), shape = MaterialTheme.shapes.small)
        }
        // The hero: a label over a display figure over a chip.
        SkeletonCard(modifier = Modifier.height(SkeletonHeroHeight))
        // The activity chart: a heading row over 150 dp of bars.
        SkeletonCard(modifier = Modifier.height(SkeletonActivityHeight))
        // The network: heading, mode chips, a 180 dp map.
        SkeletonCard(modifier = Modifier.height(SkeletonNetworkHeight))
        // The first row of the metric grid.
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SkeletonCard(modifier = Modifier.weight(1f).height(SkeletonMetricHeight))
            SkeletonCard(modifier = Modifier.weight(1f).height(SkeletonMetricHeight))
        }
    }
}

private val SkeletonHeroHeight = 136.dp
private val SkeletonActivityHeight = 230.dp
private val SkeletonNetworkHeight = 286.dp
private val SkeletonMetricHeight = 96.dp

@Composable
private fun StatsHeader(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val compactHeight = isCompactHeight()
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.destination_stats),
            style = if (compactHeight) {
                MaterialTheme.typography.titleLarge
            } else {
                MaterialTheme.typography.headlineMedium
            },
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
        )
        SettingsAction(onClick = onOpenSettings)
    }
}

@LightDarkPreview
@CompactWidthPreview
@DevicePreviews
@Composable
private fun StatsScreenPreview() {
    FlightPlannerTheme(dynamicColor = false) {
        val sampleAircraft = AircraftSpec(
            id = 1,
            manufacturer = "Boeing",
            variant = "777-300ER",
            icaoCode = "B77W",
            flown = true,
            rangeNm = 7370,
            category = "Wide-body",
            cruiseSpeedKt = 490,
            dateFlown = "2026-08-01",
            takeoffDistanceMeters = 3100,
        )

        val sampleSuccess = StatsUiState.Success(
            timeframe = StatsTimeframe.ALL_TIME,
            chartMetric = ChartMetric.FLIGHTS,
            totalDistanceNm = 34820,
            earthCircumferences = 1.61,
            totalFlights = 28,
            averageDistanceNm = 1243.5,
            longestFlight = LegStat("EHAM", "RJTT", 1, 5180),
            shortestFlight = LegStat("EHAM", "EGLL", 1, 200),
            monthlyActivity = listOf(
                MonthlyActivity(YearMonth.of(2026, 1), "Jan", 4, 6000),
                MonthlyActivity(YearMonth.of(2026, 2), "Feb", 6, 8200),
                MonthlyActivity(YearMonth.of(2026, 3), "Mar", 2, 2400),
                MonthlyActivity(YearMonth.of(2026, 4), "Apr", 8, 11000),
                MonthlyActivity(YearMonth.of(2026, 5), "May", 5, 7220),
            ),
            topAircraft = listOf(
                TopAircraftStat(sampleAircraft, 18, 24000),
            ),
            favoriteDeparture = AirportCount("EHAM", "Amsterdam Schiphol", 14),
            favoriteArrival = AirportCount("KJFK", "New York JFK", 6),
            mostVisitedAirport = AirportCount("EHAM", "Amsterdam Schiphol", 20),
            visitedAirports = emptyList(),
            visitedLegs = emptyList(),
        )

        StatsScreen(
            uiState = sampleSuccess,
            onSelectTimeframe = {},
            onSelectMetric = {},
            onOpenSettings = {},
            onOpenRoute = {},
            onOpenAircraft = {},
            onPlanFlight = {},
            worldOutline = WorldOutline.Empty,
        )
    }
}

@LightDarkPreview
@Composable
private fun StatsScreenLoadingPreview() {
    FlightPlannerTheme(dynamicColor = false) {
        StatsScreen(
            uiState = StatsUiState.Loading,
            onSelectTimeframe = {},
            onSelectMetric = {},
            onOpenSettings = {},
            onOpenRoute = {},
            onOpenAircraft = {},
            onPlanFlight = {},
            worldOutline = WorldOutline.Empty,
        )
    }
}
