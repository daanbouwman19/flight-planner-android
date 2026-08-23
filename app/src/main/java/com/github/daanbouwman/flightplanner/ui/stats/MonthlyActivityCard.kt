package com.github.daanbouwman.flightplanner.ui.stats

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.daanbouwman.flightplanner.R
import com.github.daanbouwman.flightplanner.core.designsystem.motion.FlightMotion
import com.github.daanbouwman.flightplanner.core.designsystem.theme.asChartFigure
import com.github.daanbouwman.flightplanner.ui.asFigure

/**
 * Monthly activity bar chart with flight count vs distance toggle.
 */
@Composable
fun MonthlyActivityCard(
    activity: List<MonthlyActivity>,
    selectedMetric: ChartMetric,
    onSelectMetric: (ChartMetric) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.stats_section_activity),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.2.sp,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChartMetric.entries.forEach { metric ->
                        FilterChip(
                            selected = metric == selectedMetric,
                            onClick = { onSelectMetric(metric) },
                            label = {
                                Text(
                                    text = stringResource(metric.labelRes),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            },
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            val maxVal = activity.maxOfOrNull {
                if (selectedMetric == ChartMetric.FLIGHTS) it.flightCount else it.distanceNm
            }?.coerceAtLeast(1) ?: 1

            val effectsSpec = FlightMotion.effects<Float>()

            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(horizontal = 4.dp),
            ) {
                items(activity, key = { it.yearMonth.toString() }) { monthItem ->
                    val currentVal = if (selectedMetric == ChartMetric.FLIGHTS) {
                        monthItem.flightCount
                    } else {
                        monthItem.distanceNm
                    }
                    val targetFraction = (currentVal.toFloat() / maxVal).coerceIn(0.04f, 1f)
                    val animatedFraction by animateFloatAsState(
                        targetValue = targetFraction,
                        animationSpec = effectsSpec,
                        label = "bar_height",
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(36.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = if (currentVal > 0) {
                                if (selectedMetric == ChartMetric.FLIGHTS) {
                                    "$currentVal"
                                } else if (currentVal < 1000) {
                                    currentVal.asFigure()
                                } else {
                                    "${Math.round(currentVal / 1000.0)}k"
                                }
                            } else {
                                ""
                            },
                            style = MaterialTheme.typography.labelSmall.asChartFigure(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )

                        Spacer(Modifier.height(4.dp))

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .width(18.dp),
                            contentAlignment = Alignment.BottomCenter,
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(animatedFraction)
                                    .clip(MaterialTheme.shapes.small)
                                    .background(
                                        if (currentVal > 0) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.surfaceContainerHighest
                                        },
                                    ),
                            )
                        }

                        Spacer(Modifier.height(6.dp))

                        Text(
                            text = monthItem.monthLabel,
                            style = MaterialTheme.typography.labelSmall.asChartFigure(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
