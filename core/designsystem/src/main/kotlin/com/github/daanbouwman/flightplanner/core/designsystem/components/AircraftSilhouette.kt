package com.github.daanbouwman.flightplanner.core.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.github.daanbouwman.flightplanner.aircraftart.AircraftArt
import com.github.daanbouwman.flightplanner.aircraftart.AircraftShape
import com.github.daanbouwman.flightplanner.core.designsystem.theme.FlightPlannerTheme
import com.github.daanbouwman.flightplanner.mapscene.silhouettePath
import kotlin.math.ceil
import kotlin.math.min

/**
 * An aircraft seen from directly above, nose up, fitted inside its bounds.
 *
 * The outline is traced from the simulator's own model of the type, so it is
 * a measurement, not an illustration, and it is drawn like one: one flat fill
 * in [color], with no outline, shadow or gradient. It keeps its proportions,
 * so a glider is a thin wide bar in a square slot and an airliner nearly fills
 * it. Every outline is fitted to the slot it is given, never drawn to a common
 * scale, because a Cessna beside a 777 at true scale is a speck.
 *
 * **Tint.** [color] defaults to the content colour, which is how the logbook
 * and the statistics use it: a glyph in the colour of the line it sits on.
 * Where flown status varies along a list — the fleet, the widget — the caller
 * passes `primary` for a type that has been flown and keeps the caption colour
 * for one that has not.
 *
 * It has no semantics. Every host already names the aircraft in text, and a
 * second announcement of "Boeing 737-800" adds nothing.
 */
@Composable
fun AircraftSilhouette(
    shape: AircraftShape,
    modifier: Modifier = Modifier,
    color: Color = LocalContentColor.current,
) {
    Box(
        modifier = modifier.drawWithCache {
            val path = silhouettePath(shape)
            val scale = min(size.width / shape.spanCm, size.height / shape.lengthCm)
            val left = (size.width - shape.spanCm * scale) / 2f
            val top = (size.height - shape.lengthCm * scale) / 2f
            onDrawBehind {
                // The canvas is scaled rather than the path: one parsed Path per
                // outline is shared by every row, card and map in the process.
                withTransform({
                    translate(left, top)
                    scale(scale, scale, pivot = Offset.Zero)
                }) {
                    drawPath(path, color)
                }
            }
        },
    )
}

/**
 * [shape] as an alpha mask: white where the aircraft is, transparent elsewhere,
 * as large as fits inside [maxWidthPx] × [maxHeightPx] and cropped to the
 * outline's own proportions. Null when there is no room at all.
 *
 * For a surface that colours an image at display time rather than at render
 * time — the home-screen widget, whose Glance tint resolves to the launcher's
 * day or night palette when it is shown — as [renderRouteMapLayers] does for
 * the map. Render it at the largest size it will be shown at: a launcher only
 * ever scales the bitmap down then, which costs no sharpness.
 */
fun renderSilhouetteMask(shape: AircraftShape, maxWidthPx: Int, maxHeightPx: Int): ImageBitmap? {
    if (maxWidthPx <= 0 || maxHeightPx <= 0) return null
    val scale = min(maxWidthPx.toFloat() / shape.spanCm, maxHeightPx.toFloat() / shape.lengthCm)
    val width = ceil(shape.spanCm * scale).toInt().coerceIn(1, maxWidthPx)
    val height = ceil(shape.lengthCm * scale).toInt().coerceIn(1, maxHeightPx)
    val bitmap = ImageBitmap(width, height)
    // Pixels in, pixels out: no dp in this drawing, so the density is unity.
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(width.toFloat(), height.toFloat())) {
        withTransform({ scale(scale, scale, pivot = Offset.Zero) }) {
            drawPath(silhouettePath(shape), Color.White)
        }
    }
    return bitmap
}

@LightDarkPreview
@Composable
private fun AircraftSilhouettePreview() {
    FlightPlannerTheme(dynamicColor = false) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            listOf("c172", "b738", "b77w", "g2ca", "ls8").forEach { id ->
                AircraftArt.byId(id)?.let { shape ->
                    AircraftSilhouette(
                        shape = shape,
                        modifier = Modifier.size(48.dp),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}
