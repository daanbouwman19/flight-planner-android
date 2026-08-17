package com.github.daanbouwman.flightplanner.core.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.github.daanbouwman.flightplanner.core.designsystem.theme.FlightPlannerTheme

/**
 * The shape of a route, drawn as a miniature arc.
 *
 * This is what makes a row in a list of routes read as a *route* rather than as
 * two codes and a number: at a glance you can see that one flight runs flat
 * along the equator and the next bends over the pole.
 *
 * ### Why it takes points rather than coordinates
 *
 * The obvious signature is four doubles, and it is the wrong one. Turning two
 * airports into a great-circle polyline is spherical interpolation over two
 * dozen samples; taking coordinates here would mean doing that inside a
 * composable — on the main thread, for every visible card, again on every
 * recomposition and every theme change. Taking the projected points instead
 * means the geometry is computed once per route, on a background dispatcher,
 * alongside the rest of the row's data, and this function is a loop over a
 * `FloatArray`.
 *
 * Points are interleaved `x, y` pairs normalised into the unit box, as produced
 * by `RouteArc.normalisedPath` — `y = 0` is north. An array with fewer than two
 * points draws nothing rather than throwing: a sparkline is decoration on a row
 * whose real content is the codes and the distance, and it must never be the
 * reason a list fails to render.
 *
 * The sparkline is hidden from accessibility services outright. It carries no
 * information the row does not already state in text, and a screen reader
 * stopping on an unlabelled graphic between the two airport codes would break
 * up the one part of the row that reads well.
 */
@Composable
fun RouteSparkline(
    points: FloatArray,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    endpointColor: Color = MaterialTheme.colorScheme.secondary,
) {
    Canvas(modifier = modifier.clearAndSetSemantics { }) {
        if (points.size < MIN_POINTS * 2) return@Canvas

        val strokeWidth = StrokeWidthDp.dp.toPx()
        val endpointRadius = EndpointRadiusDp.dp.toPx()
        // The path is normalised to the full box, so half a stroke and a whole
        // endpoint dot would be clipped at each extreme without this inset.
        val inset = maxOf(strokeWidth / 2f, endpointRadius)
        val usableWidth = (size.width - inset * 2f).coerceAtLeast(0f)
        val usableHeight = (size.height - inset * 2f).coerceAtLeast(0f)

        fun pointAt(index: Int) = Offset(
            x = inset + points[index * 2] * usableWidth,
            y = inset + points[index * 2 + 1] * usableHeight,
        )

        val count = points.size / 2
        val path = Path()
        val first = pointAt(0)
        path.moveTo(first.x, first.y)
        for (i in 1 until count) {
            val point = pointAt(i)
            path.lineTo(point.x, point.y)
        }

        drawPath(
            path = path,
            color = color.copy(alpha = ArcAlpha),
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
        )

        // Departure hollow, destination filled — the same "you are going there"
        // convention a chart uses for a start and an end, so the direction of
        // travel is readable without an arrowhead that would not survive being
        // drawn at this size.
        drawCircle(
            color = endpointColor,
            radius = endpointRadius,
            center = first,
            style = Stroke(width = strokeWidth),
        )
        drawCircle(
            color = endpointColor,
            radius = endpointRadius,
            center = pointAt(count - 1),
        )
    }
}

private const val MIN_POINTS = 2
private const val ArcAlpha = 0.7f
private const val StrokeWidthDp = 1.5f
private const val EndpointRadiusDp = 2.5f

@LightDarkPreview
@Composable
private fun RouteSparklinePreview() {
    FlightPlannerTheme(dynamicColor = false) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // A flat east-west hop and a bowed long-haul, so the preview shows
            // the difference the component exists to convey.
            RouteSparkline(
                points = floatArrayOf(0f, 0.5f, 0.25f, 0.5f, 0.5f, 0.5f, 0.75f, 0.5f, 1f, 0.5f),
                modifier = Modifier.width(120.dp).height(40.dp),
            )
            RouteSparkline(
                points = floatArrayOf(0f, 0.9f, 0.25f, 0.55f, 0.5f, 0.36f, 0.75f, 0.42f, 1f, 0.7f),
                modifier = Modifier.width(120.dp).height(40.dp),
            )
        }
    }
}
