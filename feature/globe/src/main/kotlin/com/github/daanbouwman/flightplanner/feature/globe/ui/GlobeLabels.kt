package com.github.daanbouwman.flightplanner.feature.globe.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.daanbouwman.flightplanner.core.designsystem.components.nodeSizeFraction
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
 * ### The one thing that does move: the code slides along under its dot
 *
 * A code is centred under its dot, and a dot a few pixels inside the left or
 * right edge of the surface therefore put half its code *outside* it, where the
 * globe's own clip cut it off — RJTT read as "TT" on a long leg fitted to a phone.
 * So [Plate] slides the code sideways by exactly as much as it needs to stay a
 * gutter's width inside the surface, and **no further**. The dot does not move:
 * it stays on its projected point, and the code hangs under it off-centre the way
 * a tooltip does near a window edge. That keeps the rule above intact — the label
 * still says where the airport is, because the dot does — while the text stays
 * legible. The slide is horizontal only; vertically a plate still fades rather
 * than moves, because the top and bottom edges carry chrome and a plate nudged
 * clear of the app bar would land on top of it.
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
    val plateHalfWidthPx = with(LocalDensity.current) { PlateHalfWidth.toPx() }
    val depAlpha = limbAlpha(basis, departure.world, camera) *
        edgeAlpha(depScreen?.y, chromePx, fadePx, viewport.height, plateReservePx) *
        cornerAlpha(depScreen, reservedCorners, plateReservePx, plateHalfWidthPx)
    val destAlpha = limbAlpha(basis, destination.world, camera) *
        edgeAlpha(destScreen?.y, chromePx, fadePx, viewport.height, plateReservePx) *
        cornerAlpha(destScreen, reservedCorners, plateReservePx, plateHalfWidthPx)

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
                dot = {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Dot(departure.color)
                        Dot(destination.color)
                    }
                },
                code = { Code("${departure.icao} · ${destination.icao}") },
            )
        } else {
            depScreen?.let { p ->
                Plate(
                    x = p.x,
                    y = p.y,
                    alpha = depAlpha,
                    spoken = departure.icao,
                    dot = { Dot(departure.color) },
                    code = { Code(departure.icao) },
                )
            }
            destScreen?.let { p ->
                Plate(
                    x = p.x,
                    y = p.y,
                    alpha = destAlpha,
                    spoken = destination.icao,
                    dot = { Dot(destination.color) },
                    code = { Code(destination.icao) },
                )
            }
        }
    }
}

/**
 * A dot and the text under it, positioned by its dot and nothing else.
 *
 * The layout places the *dot* centred on `(x, y)` and hangs the code below it.
 * That is what makes "anchored to its own point by construction" literally true
 * rather than a thing to be careful about: there is no offset anywhere that could
 * drift, and a plate that grows — a longer code, a larger font scale — grows
 * downward and outward from the dot rather than moving it.
 *
 * The code is centred under the dot and then slid by [plateShiftPx] — the least
 * distance that keeps it [PlateEdgeMargin] inside the surface — while the dot is
 * placed from `x` alone and never slides. Two measurables rather than one
 * `Column`, because a column can only move both together.
 */
@Composable
private fun Plate(
    x: Float,
    y: Float,
    alpha: Float,
    spoken: String,
    dot: @Composable () -> Unit,
    code: @Composable () -> Unit,
) {
    if (alpha <= 0.01f) return
    Layout(
        content = {
            Box(modifier = Modifier.layoutId(PlateDotId)) { dot() }
            Box(modifier = Modifier.layoutId(PlateCodeId)) { code() }
        },
        modifier = Modifier
            .alpha(alpha)
            .semantics(mergeDescendants = true) { contentDescription = spoken },
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val dotPlaceable = measurables.first { it.layoutId == PlateDotId }.measure(loose)
        val codePlaceable = measurables.first { it.layoutId == PlateCodeId }.measure(loose)
        val codeLeft = x - codePlaceable.width / 2f
        val shift = plateShiftPx(
            left = codeLeft,
            width = codePlaceable.width.toFloat(),
            surfaceWidth = constraints.maxWidth.toFloat(),
            marginPx = PlateEdgeMargin.toPx(),
        )
        layout(constraints.maxWidth, constraints.maxHeight) {
            // Centred on the projected point, whatever the dot slot measured to —
            // one dot in cases 1 and 2, two side by side in case 3.
            dotPlaceable.place(
                x = (x - dotPlaceable.width / 2f).roundToInt(),
                y = (y - dotPlaceable.height / 2f).roundToInt(),
            )
            codePlaceable.place(
                x = (codeLeft + shift).roundToInt(),
                y = (y + dotPlaceable.height / 2f + PlateGap.toPx()).roundToInt(),
            )
        }
    }
}

/**
 * How far a code has to slide to sit at least [marginPx] inside a surface
 * [surfaceWidth] wide, given that centring it under its dot would put its left
 * edge at [left].
 *
 * Zero whenever the centred position already fits, so a label anywhere in the
 * body of the surface is exactly where it always was; and zero again when the
 * code is wider than the surface less both margins, because there is then no
 * position that satisfies the rule and sliding would only choose which end to
 * clip. In pixels, signed: positive slides right.
 */
internal fun plateShiftPx(left: Float, width: Float, surfaceWidth: Float, marginPx: Float): Float {
    val minLeft = marginPx
    val maxLeft = surfaceWidth - width - marginPx
    if (maxLeft < minLeft) return 0f
    return left.coerceIn(minLeft, maxLeft) - left
}

private const val PlateDotId = "dot"
private const val PlateCodeId = "code"

/** Between the dot and the code under it. */
private val PlateGap = 4.dp

/**
 * How far inside the surface a code stops. The same 12 dp gutter the glass
 * controls keep from the edge of the surface, so a slid label lines up with them.
 */
private val PlateEdgeMargin = 12.dp

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
 * Within a reserved rect's horizontal span the fade starts one plate-height
 * *above* the rect's top edge, because the plate hangs below its dot: a dot level
 * with the top of the stack still puts its code over the stack. Zero once the dot
 * is at or below that edge.
 *
 * **The span is the rect widened by half a plate, not the rect.** [Plate] centres
 * its content on the dot, so the code reaches [plateHalfWidthPx] either side of
 * it; testing the dot x against the bare rect let a dot a few pixels outside
 * the stack draw the inner half of its code straight over it — the exact overlap
 * this fade exists to prevent. [edgeAlpha] already gives the same kind of slack
 * vertically through `plateReservePx`; this is its horizontal twin.
 */
internal fun cornerAlpha(
    point: ScreenPoint?,
    reserved: List<Rect>,
    plateReservePx: Float,
    plateHalfWidthPx: Float,
): Float {
    if (point == null || reserved.isEmpty()) return 1f
    var alpha = 1f
    for (rect in reserved) {
        if (rect.isEmpty) continue
        if (point.x < rect.left - plateHalfWidthPx) continue
        if (point.x > rect.right + plateHalfWidthPx) continue
        val above = rect.top - point.y
        alpha = minOf(alpha, (above / plateReservePx).coerceIn(0f, 1f))
    }
    return alpha
}

/** How much room below its dot a plate needs. A dot and a line of code. */
private val PlateReserve = 40.dp

/**
 * How far a plate reaches either side of its dot.
 *
 * Sized from the same measurement [MergeDistancePx] cites — two four-character
 * codes at `labelMedium` are about 44 dp wide — halved, plus the plate's own 6 dp
 * of horizontal padding.
 */
private val PlateHalfWidth = 28.dp

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
 * carry no plates. What they carry instead is **size**, by the design system's
 * `nodeSizeFraction` — the same rule the flat `NetworkMap` draws by, so the two
 * views of one logbook agree: the least-visited field is the small dot, the
 * most-visited the large one, and between them the radius goes as the square
 * root of the visits above the least, which is the only scaling that makes the
 * *area* of the dot proportional to the number it stands for. A log where every
 * field has the same count is all small dots; scaling from zero instead drew a
 * one-visit-each logbook entirely at the maximum, and two fields 90 NM apart
 * merged into one blob on the sphere.
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
        val minVisits = nodes.minOfOrNull { it.visits } ?: 0
        val maxVisits = nodes.maxOfOrNull { it.visits } ?: 0
        nodes.map { node ->
            NodeMark(
                icao = node.icao,
                world = latLonToWorld(node.latitude.toFloat(), node.longitude.toFloat()),
                size = NodeMinSize +
                    (NodeMaxSize - NodeMinSize) * nodeSizeFraction(node.visits, minVisits, maxVisits),
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
