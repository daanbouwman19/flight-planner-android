package com.github.daanbouwman.flightplanner.ui.stats

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.daanbouwman.flightplanner.R
import com.github.daanbouwman.flightplanner.core.designsystem.motion.FlightMotion
import com.github.daanbouwman.flightplanner.core.designsystem.theme.asChartFigure
import com.github.daanbouwman.flightplanner.ui.asFigure

/**
 * 2x2 grid of key flight metric summary cards.
 */
@Composable
fun MetricGrid(
    totalFlights: Int,
    averageDistanceNm: Double,
    longestFlight: LegStat?,
    shortestFlight: LegStat?,
    onOpenRoute: (LegStat) -> Unit,
    modifier: Modifier = Modifier,
) {
    val animatedFlights = FlightMotion.rememberCountUp(target = totalFlights)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MetricTile(
                title = stringResource(R.string.stats_metric_total_flights),
                value = animatedFlights.asFigure(),
                modifier = Modifier.weight(1f),
            )
            MetricTile(
                title = stringResource(R.string.stats_metric_avg_distance),
                value = stringResource(
                    R.string.plan_value_nautical_miles,
                    averageDistanceNm.toInt().asFigure(),
                ),
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MetricTile(
                title = stringResource(R.string.stats_metric_longest_flight),
                value = longestFlight?.legDisplayName ?: "—",
                subtitle = longestFlight?.let {
                    stringResource(R.string.plan_value_nautical_miles, it.distanceNm.asFigure())
                },
                onClick = longestFlight?.let { { onOpenRoute(it) } },
                modifier = Modifier.weight(1f),
            )
            MetricTile(
                title = stringResource(R.string.stats_metric_shortest_flight),
                value = shortestFlight?.legDisplayName ?: "—",
                subtitle = shortestFlight?.let {
                    stringResource(R.string.plan_value_nautical_miles, it.distanceNm.asFigure())
                },
                onClick = shortestFlight?.let { { onOpenRoute(it) } },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun MetricTile(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
) {
    Card(
        modifier = modifier
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.0.sp,
                maxLines = 1,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.asChartFigure(),
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall.asChartFigure(),
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                )
            }
        }
    }
}
