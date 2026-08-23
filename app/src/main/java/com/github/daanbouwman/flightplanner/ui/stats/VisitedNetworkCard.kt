package com.github.daanbouwman.flightplanner.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.daanbouwman.flightplanner.R
import com.github.daanbouwman.flightplanner.core.designsystem.components.WorldMapCoastAlpha
import com.github.daanbouwman.flightplanner.core.designsystem.components.WorldMapLandAlpha
import com.github.daanbouwman.flightplanner.core.designsystem.components.toPath
import com.github.daanbouwman.flightplanner.core.designsystem.theme.asChartFigure
import com.github.daanbouwman.flightplanner.routing.MapFrame
import com.github.daanbouwman.flightplanner.routing.WorldOutline

/**
 * 2D Visited Network Map drawing the world coastlines, visited airports, and connecting flight arcs.
 */
@Composable
fun VisitedNetworkCard(
    visitedAirports: List<VisitedAirport>,
    visitedLegs: List<VisitedLeg>,
    outline: WorldOutline,
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
                    text = stringResource(R.string.stats_section_network),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.2.sp,
                )
                Text(
                    text = stringResource(
                        R.string.stats_network_summary_format,
                        visitedAirports.size,
                        visitedLegs.size,
                    ),
                    style = MaterialTheme.typography.labelSmall.asChartFigure(),
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(Modifier.height(12.dp))

            val landColor = MaterialTheme.colorScheme.onSurface.copy(alpha = WorldMapLandAlpha)
            val coastColor = MaterialTheme.colorScheme.onSurface.copy(alpha = WorldMapCoastAlpha)
            val routeColor = MaterialTheme.colorScheme.primary
            val casingColor = MaterialTheme.colorScheme.surfaceContainer

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .clearAndSetSemantics { }
                    .drawWithCache {
                        if (size.minDimension <= 0f) return@drawWithCache onDrawBehind { }

                        val lats: DoubleArray
                        val lons: DoubleArray
                        if (visitedAirports.isNotEmpty()) {
                            lats = visitedAirports.map { it.latitude }.toDoubleArray()
                            lons = visitedAirports.map { it.longitude }.toDoubleArray()
                        } else {
                            lats = doubleArrayOf(-30.0, 60.0)
                            lons = doubleArrayOf(-120.0, 120.0)
                        }

                        val frame = MapFrame.forRoute(
                            lats = lats,
                            lons = lons,
                            aspect = (size.width / size.height).toDouble(),
                        )

                        val land = frame.projectOutline(outline, margin = 0.1)
                        val landPath = land.fill.toPath(size.width, size.height, close = true).apply {
                            fillType = PathFillType.EvenOdd
                        }
                        val coastPath = land.coast.toPath(size.width, size.height, close = false)

                        val projectedLegs = visitedLegs.map { leg ->
                            val pts = frame.project(leg.arc.lats, leg.arc.lons)
                            val path = Path().apply {
                                if (pts.size >= 4) {
                                    moveTo(pts[0] * size.width, pts[1] * size.height)
                                    for (i in 2 until pts.size step 2) {
                                        lineTo(pts[i] * size.width, pts[i + 1] * size.height)
                                    }
                                }
                            }
                            path
                        }

                        val projectedAirports = visitedAirports.map { airport ->
                            Offset(
                                x = frame.x(airport.longitude) * size.width,
                                y = frame.y(airport.latitude) * size.height,
                            )
                        }

                        onDrawBehind {
                            clipRect {
                                drawPath(landPath, color = landColor)
                                drawPath(
                                    coastPath,
                                    color = coastColor,
                                    style = Stroke(width = 1.dp.toPx(), cap = StrokeCap.Butt, join = StrokeJoin.Bevel),
                                )

                                for (legPath in projectedLegs) {
                                    drawPath(
                                        legPath,
                                        color = casingColor,
                                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                                    )
                                    drawPath(
                                        legPath,
                                        color = routeColor.copy(alpha = 0.8f),
                                        style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                                    )
                                }

                                for (point in projectedAirports) {
                                    drawCircle(
                                        color = casingColor,
                                        radius = 4.dp.toPx(),
                                        center = point,
                                    )
                                    drawCircle(
                                        color = routeColor,
                                        radius = 2.5.dp.toPx(),
                                        center = point,
                                    )
                                }
                            }
                        }
                    },
            )
        }
    }
}
