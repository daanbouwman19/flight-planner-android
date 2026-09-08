package com.github.daanbouwman.flightplanner.feature.globe.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.daanbouwman.flightplanner.core.designsystem.theme.asChartFigure
import com.github.daanbouwman.flightplanner.feature.globe.math.CameraBasis
import com.github.daanbouwman.flightplanner.feature.globe.math.GlobeCamera
import com.github.daanbouwman.flightplanner.feature.globe.math.GlobeViewport
import com.github.daanbouwman.flightplanner.feature.globe.math.RouteGeometry
import com.github.daanbouwman.flightplanner.feature.globe.math.ScreenPoint
import com.github.daanbouwman.flightplanner.feature.globe.math.Vec3
import com.github.daanbouwman.flightplanner.feature.globe.math.facingValueFast
import com.github.daanbouwman.flightplanner.feature.globe.math.latLonToWorld
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sqrt
import kotlin.math.roundToInt

/** One end of the leg, placed on the glass. */
@Immutable
internal data class GlobeLabel(
    val icao: String,
    val world: Vec3,
    val color: Color,
)

/**
 * The DEP and DEST plates, and the one rule that governs all three of their
 * cases.
 *
 * > **A label is a consequence of its point, so it never moves away from it — it
 * > drops detail instead.**
 *
 * That is the whole design, and the three cases are what it implies rather than
 * three separate behaviours:
 *
 *  1. **Both ends on the lit face.** Each code sits on a plate directly under
 *     its own dot, with no leader line. The plate is what keeps a code legible
 *     over ocean and over cloud alike — the imagery underneath is a photograph
 *     and cannot be relied on for contrast the way the flat map's 8% ink can.
 *  2. **An end near the limb.** The label fades with the facing value and is
 *     gone by the horizon. It is never clamped to the edge, because *a label for
 *     a point on the far side is a lie about where that airport is.* Re-fitting
 *     brings it back.
 *  3. **The two ends too close to separate.** They become **one** plate holding
 *     both codes, anchored to the midpoint of the leg. Nudging them apart would
 *     put a code somewhere no airport is, which is the same lie in a smaller
 *     font.
 *
 * ### This is the part of the globe TalkBack can read
 *
 * A `SurfaceView` is a blank rectangle to an accessibility service — it has no
 * children, no text and no structure. Everything the globe *says* therefore has
 * to be said by the Compose layer over it, and these two plates are it. They
 * carry the codes as real text, which is why G9 is satisfied by the chrome and
 * these labels rather than by a description bolted onto the surface.
 */
@Composable
internal fun GlobeLabels(
    departure: GlobeLabel,
    destination: GlobeLabel,
    cameraState: GlobeCameraState,
    viewport: GlobeViewport,
    modifier: Modifier = Modifier,
    /**
     * A strip along the top of the surface that the host’s own chrome covers.
     *
     * A label whose dot rises into it fades out, which is **case 2 again**: the
     * plate is not nudged clear of the app bar, because a code moved off its
     * airport is the same lie as a code clamped to the limb. It drops out
     * instead, and re-framing brings it back.
     */
    topChromeInset: Dp = 0.dp,
    /**
     * Rectangles, in this surface's own pixels, where the host has placed chrome
     * over the imagery — the camera stack in one bottom corner, the imagery
     * credit in the other.
     *
     * A plate whose dot projects into one of these fades out, **case 2 once
     * more**: the code is not shoved sideways to clear the control, because a
     * code away from its airport is the same lie as one clamped to the limb. It
     * drops, and re-framing brings it back.
     */
    reservedCorners: List<Rect> = emptyList(),
) {
    if (viewport.width < 1f || viewport.height < 1f) return

    // **The camera is read here and nowhere above.** Reading it in the
    // enclosing canvas made every layer on the glass - the limb, the input
    // box, the semantics - a subscriber, so a fling recomposed all of them at
    // animation rate. The plates genuinely are a function of the camera; the
    // rest of the glass is not.
    val camera = cameraState.camera
    val basis = remember(camera) { camera.computeBasis() }

    val chromePx = with(LocalDensity.current) { topChromeInset.toPx() }
    val fadePx = with(LocalDensity.current) { ChromeFadeSpan.toPx() }
    val depScreen = camera.worldToScreen(departure.world, viewport)
    val destScreen = camera.worldToScreen(destination.world, viewport)
    val plateReservePx = with(LocalDensity.current) { PlateReserve.toPx() }
    val depAlpha = limbAlpha(basis, departure.world, camera) *
        edgeAlpha(depScreen?.y, chromePx, fadePx, viewport.height, plateReservePx) *
        cornerAlpha(depScreen, reservedCorners, plateReservePx)
    val destAlpha = limbAlpha(basis, destination.world, camera) *
        edgeAlpha(destScreen?.y, chromePx, fadePx, viewport.height, plateReservePx) *
        cornerAlpha(destScreen, reservedCorners, plateReservePx)

    val separation = depScreen?.let { d ->
        destScreen?.let { hypot(it.x - d.x, it.y - d.y) }
    }

    Box(modifier = modifier) {
        if (separation != null && separation < MergeDistancePx) {
            // Case 3. Anchored to the true midpoint of the great circle rather
            // than to the midpoint of the two pixels: at this zoom they differ
            // by less than the plate's own width, and the arc's midpoint is the
            // one place on the sphere that is genuinely between the two ends.
            val mid = RouteGeometry.slerp(departure.world, destination.world, 0.5f)
            val midScreen = camera.worldToScreen(mid, viewport) ?: return@Box
            Plate(
                x = midScreen.x,
                y = midScreen.y,
                alpha = minOf(depAlpha, destAlpha),
                spoken = "${departure.icao} to ${destination.icao}",
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Dot(departure.color)
                    Dot(destination.color)
                }
                Code("${departure.icao} · ${destination.icao}")
            }
        } else {
            depScreen?.let { p ->
                Plate(p.x, p.y, depAlpha, departure.icao) {
                    Dot(departure.color)
                    Code(departure.icao)
                }
            }
            destScreen?.let { p ->
                Plate(p.x, p.y, destAlpha, destination.icao) {
                    Dot(destination.color)
                    Code(destination.icao)
                }
            }
        }
    }
}

/**
 * A dot and the text under it, positioned by its dot and nothing else.
 *
 * The layout modifier places the *dot* at `(x, y)` and lets the plate hang below
 * it, centred. That is what makes "anchored to its own point by construction"
 * literally true rather than a thing to be careful about: there is no offset
 * anywhere that could drift, and a plate that grows — a longer code, a larger
 * font scale — grows downward and outward from the dot rather than moving it.
 */
@Composable
private fun Plate(
    x: Float,
    y: Float,
    alpha: Float,
    spoken: String,
    content: @Composable () -> Unit,
) {
    if (alpha <= 0.01f) return
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                layout(constraints.maxWidth, constraints.maxHeight) {
                    placeable.place(
                        x = (x - placeable.width / 2f).roundToInt(),
                        // The dot is the first child and is DotSize tall, so
                        // lifting by half of it puts the dot's centre on the
                        // projected point.
                        y = (y - DotSize.toPx() / 2f).roundToInt(),
                    )
                }
            }
            .alpha(alpha)
            .semantics(mergeDescendants = true) { contentDescription = spoken },
        content = { content() },
    )
}

@Composable
private fun Dot(color: Color) {
    Box(
        modifier = Modifier
            .size(DotSize)
            .background(color, CircleShape)
            .clearAndSetSemantics { },
    )
}

@Composable
private fun Code(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium.asChartFigure(),
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .background(
                // The plate. Translucent so the imagery reads through it — this
                // is a layer over a photograph, not a panel bolted onto it — and
                // the same treatment the chips on the glass get.
                color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = PlateAlpha),
                shape = MaterialTheme.shapes.extraSmall,
            )
            .padding(horizontal = 6.dp, vertical = 1.dp)
            .clearAndSetSemantics { },
    )
}

/**
 * How a label fades out as it goes under the host’s chrome.
 *
 * The plate hangs *below* its dot, so a dot at the very bottom of the chrome
 * strip still puts readable text under it. The fade therefore starts at the
 * bottom edge of the strip rather than above it, and is done over roughly the
 * height of one plate.
 */
internal fun edgeAlpha(
    y: Float?,
    chromePx: Float,
    fadePx: Float,
    heightPx: Float,
    plateReservePx: Float,
): Float {
    if (y == null) return 1f
    val underChrome = if (chromePx <= 0f) 1f else ((y - chromePx) / fadePx).coerceIn(0f, 1f)
    // The plate hangs below its dot, so a dot in the last plate-height of the
    // surface has nowhere to put one. The globe clips to its own bounds, so
    // without this the plate would be cut in half by the edge - which reads as a
    // rendering fault rather than as a label that has run out of room.
    val overEdge = ((heightPx - y) / plateReservePx).coerceIn(0f, 1f)
    return underChrome * overEdge
}

/**
 * How a label fades out as its dot approaches a corner the host has covered.
 *
 * Only dots within a reserved rect's own horizontal span are affected — a plate
 * to the side of the camera stack is fine. Within that span the fade starts one
 * plate-height *above* the rect's top edge, because the plate hangs below its
 * dot: a dot level with the top of the stack still puts its code over the stack.
 * Zero once the dot is at or below that edge.
 */
internal fun cornerAlpha(
    point: ScreenPoint?,
    reserved: List<Rect>,
    plateReservePx: Float,
): Float {
    if (point == null || reserved.isEmpty()) return 1f
    var alpha = 1f
    for (rect in reserved) {
        if (rect.isEmpty || point.x < rect.left || point.x > rect.right) continue
        val above = rect.top - point.y
        alpha = minOf(alpha, (above / plateReservePx).coerceIn(0f, 1f))
    }
    return alpha
}

/** How much room below its dot a plate needs. A dot and a line of code. */
private val PlateReserve = 40.dp

/** How far below the chrome a label is fully in. Roughly one plate. */
private val ChromeFadeSpan = 28.dp

/**
 * How a label fades out toward the limb.
 *
 * The same facing value the renderer fades the tiles with, mapped over the last
 * few degrees before the horizon so the label is gone by the time its point is.
 * Tying it to the geometry rather than to a screen-space distance is what makes
 * it correct under tilt, where "near the edge of the view" and "near the edge of
 * the planet" are different things.
 */
private fun limbAlpha(basis: CameraBasis, world: Vec3, camera: GlobeCamera): Float {
    val facing = facingValueFast(basis, world)
    val horizon = 1f / camera.distance
    // Fully out at the horizon, fully in a little inside it.
    val span = abs(horizon) * LabelFadeSpan + 1e-4f
    return ((facing - horizon) / span).coerceIn(0f, 1f)
}

/**
 * Below this separation in pixels the two plates would overlap, so they merge.
 *
 * Sized from the plate rather than guessed: two four-character codes at
 * `labelMedium` are about 44 dp wide each, so anything under about ninety pixels
 * between the dots puts one plate on top of the other.
 */
private const val MergeDistancePx = 96f

/** Fraction of the horizon value over which a label fades. Roughly 12°. */
private const val LabelFadeSpan = 0.12f

private val DotSize = 10.dp

/**
 * The visited network's airports, as dots on the sphere — 1G's markers.
 *
 * ### No labels, and a radius that means something
 *
 * A hundred four-letter codes over a planet is a word cloud, not a map, so these
 * carry no plates. What they carry instead is **size**: the radius goes as the
 * square root of the visit count, which is the only scaling that makes the
 * *area* of the dot proportional to the number it stands for. Scaling the radius
 * linearly would make a field visited nine times look nine times as big as it
 * should — the classic bubble-chart error, and one an eye reads as area whether
 * or not it was drawn as one.
 *
 * The codes are still announced. These dots are the only thing on this surface
 * an accessibility service can reach, and a sphere with a hundred unnamed marks
 * on it says nothing at all.
 *
 * Culled at the limb by facing value rather than faded: a dot is small enough
 * that a fade reads as a rendering fault, and unlike a label it has no detail to
 * drop on the way out.
 */
@Composable
internal fun GlobeNodes(
    nodes: List<GlobeNode>,
    color: Color,
    cameraState: GlobeCameraState,
    viewport: GlobeViewport,
    modifier: Modifier = Modifier,
) {
    if (viewport.width < 1f || viewport.height < 1f) return
    // Read here rather than in the canvas above, for the reason [GlobeLabels]
    // gives: this is the one layer that is a function of where the camera is.
    val camera = cameraState.camera
    val basis = remember(camera) { camera.computeBasis() }
    val threshold = camera.cullThreshold()
    // Hoisted out of the loop deliberately. A `remember` keyed on the node from
    // inside a loop over a list that can change length is memoised by slot
    // position, so it hands the wrong vector to a node the moment the list
    // shortens. One list built once has no such trap, and the whole computation
    // is a few hundred trig calls - cheaper than the guard would be.
    val marks = remember(nodes) {
        val maxVisits = (nodes.maxOfOrNull { it.visits } ?: 1).coerceAtLeast(1).toFloat()
        nodes.map { node ->
            NodeMark(
                icao = node.icao,
                world = latLonToWorld(node.latitude.toFloat(), node.longitude.toFloat()),
                size = NodeMinSize +
                    (NodeMaxSize - NodeMinSize) * sqrt(node.visits.toFloat() / maxVisits),
            )
        }
    }

    Box(modifier = modifier) {
        for (mark in marks) {
            if (facingValueFast(basis, mark.world) <= threshold) continue
            val screen = camera.worldToScreen(mark.world, viewport) ?: continue
            key(mark.icao) {
                NodeDot(
                    x = screen.x,
                    y = screen.y,
                    size = mark.size,
                    color = color,
                    spoken = mark.icao,
                )
            }
        }
    }
}

/** One node, with everything that does not depend on the camera already worked out. */
@Immutable
private data class NodeMark(val icao: String, val world: Vec3, val size: Dp)

/**
 * One node, placed by its centre.
 *
 * Cased the way the flat map cases its markers — a ring of the surface colour
 * under the dot — so a mark over a bright coastline and one over dark ocean are
 * equally legible without either being given its own colour.
 */
@Composable
private fun NodeDot(x: Float, y: Float, size: Dp, color: Color, spoken: String) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                layout(constraints.maxWidth, constraints.maxHeight) {
                    placeable.place(
                        x = (x - placeable.width / 2f).roundToInt(),
                        y = (y - placeable.height / 2f).roundToInt(),
                    )
                }
            }
            .size(size + NodeCasing * 2)
            .background(MaterialTheme.colorScheme.surfaceContainer, CircleShape)
            .semantics(mergeDescendants = true) { contentDescription = spoken },
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .background(color, CircleShape)
                .clearAndSetSemantics { },
        )
    }
}

/** A field visited once. Small, but never so small it disappears. */
private val NodeMinSize = 5.dp

/** The most-visited field in the set. */
private val NodeMaxSize = 13.dp

/** The casing ring under every dot. */
private val NodeCasing = 1.5.dp
