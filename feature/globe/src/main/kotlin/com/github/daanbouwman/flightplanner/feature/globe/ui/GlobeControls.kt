package com.github.daanbouwman.flightplanner.feature.globe.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.github.daanbouwman.flightplanner.core.designsystem.theme.asChartFigure
import com.github.daanbouwman.flightplanner.feature.globe.R
import com.github.daanbouwman.flightplanner.feature.globe.tile.ImageryAttribution
import com.github.daanbouwman.flightplanner.feature.globe.tile.TileProviders
import kotlin.math.roundToInt

/**
 * The camera controls, as one instrument rather than four buttons.
 *
 * ### What was wrong with the buttons
 *
 * They were 44 dp circles, filled and ringed, carrying Google's own `zoom_in`,
 * `zoom_out` and `my_location` glyphs. That is the chrome of every map on the
 * platform, and it sat eight pixels from this app's own label plates and imagery
 * credit — small translucent rectangles with tabular figures on them. Two design
 * languages on one pane of glass, and the borrowed one was the louder.
 *
 * ### The plate this uses instead
 *
 * The **same plate the labels and the credit already use**: `surfaceContainer` at
 * [PlateAlpha], an `extraSmall` corner, one hairline of `outlineVariant`. Every
 * mark the app makes over live imagery is now that plate, at that alpha, and a
 * pilot reading the glass sees one surface rather than a map's chrome plus an
 * app's annotations. The cells are divided by the same hairline instead of by
 * eight pixels of sky, so the stack is one object that can be aimed at rather
 * than three that have to be aimed at separately.
 *
 * ### The zoom marks are type, not icons
 *
 * A plus and a minus, drawn as two strokes at the weight of the app's hairlines.
 * There is nothing in a `zoom_in` vector that a cross does not say, and the
 * vector said it in another product's hand.
 *
 * ### The heading cell is the signature
 *
 * When the view has been turned, the plate grows a cell carrying a needle **and
 * the bearing as a figure** — `047°` — set in the tabular face this app states
 * every other angle in. Two hundred pixels below it the same screen reads
 * `DEP HDG 319°`; this is that sentence, about the camera. It is a readout and a
 * control at once: it says which way the globe is facing, and tapping it puts it
 * back. It appears rather than greys out, because a "face north" control on a
 * view already facing north is a control that does nothing.
 *
 * ### Why buttons exist here at all
 *
 * Not for symmetry with a desktop toolbar. **A `SurfaceView` is a blank rectangle
 * to TalkBack** — no children, no text, no gesture an accessibility service can
 * synthesise — so pinch cannot be the only way to zoom and a drag cannot be the
 * only way to re-frame. Every camera move the globe supports has to be reachable
 * by a tap on something with a label, and these are those things.
 *
 * The concept's mock puts a backdrop blur behind the plate. There is no such
 * thing in Compose, and faking one costs a render pass over live imagery every
 * frame. The intent is *a plate that reads over anything underneath it*, and
 * translucency plus a defined edge delivers that; the blur was the web's way of
 * getting there.
 */
@Composable
fun GlobeCameraControls(
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onResetNorth: (() -> Unit)?,
    onRefit: () -> Unit,
    bearingDegrees: Float,
    modifier: Modifier = Modifier,
    /**
     * Called with this stack's bounds in its parent whenever they change, so the
     * globe can fade a label plate that would otherwise draw its code behind the
     * stack. Null on a host that does not need it. See [GlobeControlsHandle].
     */
    boundsReporter: ((Rect) -> Unit)? = null,
) {
    // Nothing at all while the globe has no imagery and the network is why:
    // every cell here moves a camera that is rendering to a surface the still
    // map is drawn over at full opacity, so the stack would be controls for a
    // picture that is not there. The host does not have to know - the globe
    // says so through the local, from inside its own box. See GlobeSurface.
    if (LocalGlobeImageryOffline.current) return

    GlassPlate(modifier = modifier.width(ControlSize).reportBoundsInParent(boundsReporter)) {
        PlateCell(
            onClick = onZoomIn,
            contentDescription = stringResource(R.string.globe_zoom_in),
        ) { tint ->
            drawZoomMark(tint, withVertical = true)
        }
        CellRule()
        PlateCell(
            onClick = onZoomOut,
            contentDescription = stringResource(R.string.globe_zoom_out),
        ) { tint ->
            drawZoomMark(tint, withVertical = false)
        }
        CellRule()
        PlateCell(
            onClick = onRefit,
            contentDescription = stringResource(R.string.globe_refit_route),
        ) { tint ->
            drawFrameBrackets(tint)
        }
        if (onResetNorth != null) {
            CellRule()
            HeadingCell(bearingDegrees = bearingDegrees, onClick = onResetNorth)
        }
    }
}

/**
 * The re-frame control on its own — the one control an embedded globe keeps.
 *
 * A globe in a card inside a scrolling list does not carry the full stack: the
 * stack sat on the data (over a leg, hiding an airport on the Stats band), a
 * pinch already zooms, and TalkBack reaches every camera move through the
 * surface's own custom actions, so the two zoom cells were the only visible way
 * to do something that already had two other ways. What has no gesture is
 * *getting back*: after a pan and a pinch the whole network is somewhere off the
 * card, and this is the way home. Same plate, same cell, same brackets as the
 * stack's own refit cell, at one cell's length.
 *
 * @param contentDescription what re-framing means here — "Frame the whole
 *   network" on the Stats band, where the stack's "Frame the whole route" would
 *   be wrong.
 */
@Composable
fun GlobeRefitControl(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    GlassPlate(modifier = modifier.width(ControlSize)) {
        PlateCell(onClick = onClick, contentDescription = contentDescription) { tint ->
            drawFrameBrackets(tint)
        }
    }
}

/**
 * One control on its own — the immersive screen's collapse action.
 *
 * The same plate as a cell of the stack, so a single control and a stack of them
 * are the same object at two lengths.
 */
@Composable
fun GlobeControlButton(
    iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.size(ControlSize),
        shape = MaterialTheme.shapes.extraSmall,
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = PlateAlpha),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = contentDescription,
                modifier = Modifier.size(GlyphSize),
            )
        }
    }
}

/**
 * The bearing, and the offer to give it up.
 *
 * The needle is drawn rather than iconified for the reason the runway diagram is:
 * it is showing a measured angle, and an angle wants a line at that angle. Its
 * north half takes `onSurface` and its south half `outlineVariant`, which is the
 * two-tone card every compass rose in the world uses and costs no colour the
 * theme has not already chosen.
 */
@Composable
private fun HeadingCell(bearingDegrees: Float, onClick: () -> Unit) {
    val north = MaterialTheme.colorScheme.onSurface
    val south = MaterialTheme.colorScheme.outlineVariant
    val spoken = stringResource(R.string.globe_reset_north)
    Surface(
        onClick = onClick,
        modifier = Modifier
            .width(ControlSize)
            .height(HeadingCellHeight),
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            // Cleared: the needle and the figure are one control, and the
            // figure is a duplicate of what the surface itself already
            // announces as its camera state.
            modifier = Modifier.clearAndSetSemantics { contentDescription = spoken },
        ) {
            Box(
                modifier = Modifier
                    .size(NeedleBox)
                    // The needle turns opposite to the camera: the globe was
                    // rotated by the bearing, so north is now that far the other
                    // way round from up.
                    .rotate(-bearingDegrees)
                    .drawBehind { drawNeedle(north, south) },
            )
            Text(
                text = "${bearingDegrees.normalisedDegrees()}°",
                style = MaterialTheme.typography.labelSmall.asChartFigure(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Reports this node's bounds in its parent whenever they change, if anyone asked.
 *
 * The globe fades a label plate that would otherwise draw its code behind the
 * chrome, and the chrome is the only thing that knows where it ended up — at what
 * font scale, and with or without the heading cell. Null reporter, no modifier at
 * all: the immersive screen folds its own controls into `topChromeInset` instead
 * and has nothing to say here.
 */
private fun Modifier.reportBoundsInParent(reporter: ((Rect) -> Unit)?): Modifier =
    if (reporter == null) this else onGloballyPositioned { reporter(it.boundsInParent()) }

/** The translucent plate every mark this app makes over imagery is made on. */
@Composable
private fun GlassPlate(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraSmall,
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = PlateAlpha),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) { content() }
    }
}

/** One square cell of the plate, drawing its mark in the plate's own ink. */
@Composable
private fun PlateCell(
    onClick: () -> Unit,
    contentDescription: String,
    mark: DrawScope.(Color) -> Unit,
) {
    val tint = MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        onClick = onClick,
        modifier = Modifier.size(ControlSize),
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.semantics { this.contentDescription = contentDescription },
        ) {
            Box(Modifier.size(GlyphSize).drawBehind { mark(tint) })
        }
    }
}

/** The hairline between two cells, inset so it reads as a rule and not as a border. */
@Composable
private fun CellRule() {
    HorizontalDivider(
        modifier = Modifier
            .width(ControlSize)
            .padding(horizontal = RuleInset),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

/**
 * The imagery credit.
 *
 * Drawn in every layout because the provider requires it, not because it was
 * chosen — and hidden from accessibility, because it is a licence notice rather
 * than something a user navigating by TalkBack is looking for. It appears in
 * Settings' About section and on the Licences screen as well, where somebody
 * actually going looking for it will find it in reading order.
 *
 * Two lines when the provider has two things to say. Esri's terms want both
 * "Powered by Esri" *and* the data providers' names on the map itself, and the
 * second is a list of four organisations, so the plate wraps it under the label
 * inside a bounded width rather than running a single line across the whole
 * hero. NASA's acknowledgement is a sentence and belongs on the Licences screen;
 * on the glass its [ImageryAttribution.credit] is null and the plate is one line.
 */
@Composable
fun GlobeAttribution(
    attribution: ImageryAttribution,
    modifier: Modifier = Modifier,
    /** See [GlobeCameraControls]'s `boundsReporter`; the credit is the other bottom corner. */
    boundsReporter: ((Rect) -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .reportBoundsInParent(boundsReporter)
            .clearAndSetSemantics { }
            .background(
                color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = PlateAlpha),
                shape = MaterialTheme.shapes.extraSmall,
            )
            .padding(horizontal = 7.dp, vertical = 2.dp)
            .widthIn(max = AttributionMaxWidth),
    ) {
        Text(
            text = attribution.label,
            style = MaterialTheme.typography.labelSmall.asChartFigure(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        attribution.credit?.let { credit ->
            Text(
                text = credit,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * How wide the credit plate may grow before its second line wraps.
 *
 * Wide enough for "Esri, Vantor, Earthstar Geographics, and the GIS User
 * Community" to take two lines at `labelSmall`, narrow enough that on a compact
 * hero it stops well short of the camera stack in the opposite corner.
 */
private val AttributionMaxWidth = 232.dp

/** A plus, or its horizontal bar alone. Two strokes at the app's hairline weight. */
private fun DrawScope.drawZoomMark(tint: Color, withVertical: Boolean) {
    val half = size.minDimension / 2f
    val arm = half * MarkArm
    val stroke = MarkStrokeDp.toPx()
    drawLine(
        color = tint,
        start = Offset(half - arm, half),
        end = Offset(half + arm, half),
        strokeWidth = stroke,
        cap = StrokeCap.Round,
    )
    if (withVertical) {
        drawLine(
            color = tint,
            start = Offset(half, half - arm),
            end = Offset(half, half + arm),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

/**
 * Four corner brackets — what "frame the route" means on a chart.
 *
 * A crop mark rather than a crosshair. A crosshair says *centre on me*, which is
 * a different promise: this control re-frames the whole leg, and the corners are
 * where the frame is.
 */
private fun DrawScope.drawFrameBrackets(tint: Color) {
    val stroke = MarkStrokeDp.toPx()
    val inset = stroke / 2f
    val arm = size.minDimension * BracketArm
    val path = Path().apply {
        moveTo(inset, inset + arm); lineTo(inset, inset); lineTo(inset + arm, inset)
        moveTo(size.width - inset - arm, inset)
        lineTo(size.width - inset, inset)
        lineTo(size.width - inset, inset + arm)
        moveTo(size.width - inset, size.height - inset - arm)
        lineTo(size.width - inset, size.height - inset)
        lineTo(size.width - inset - arm, size.height - inset)
        moveTo(inset + arm, size.height - inset)
        lineTo(inset, size.height - inset)
        lineTo(inset, size.height - inset - arm)
    }
    drawPath(path, color = tint, style = Stroke(width = stroke, cap = StrokeCap.Round))
}

/** The two-tone compass card, pointing up when the globe faces north. */
private fun DrawScope.drawNeedle(north: Color, south: Color) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val length = size.minDimension / 2f
    val waist = size.minDimension * NeedleWaist
    drawPath(
        Path().apply {
            moveTo(cx, cy - length)
            lineTo(cx - waist, cy)
            lineTo(cx + waist, cy)
            close()
        },
        color = north,
    )
    drawPath(
        Path().apply {
            moveTo(cx, cy + length)
            lineTo(cx - waist, cy)
            lineTo(cx + waist, cy)
            close()
        },
        color = south,
    )
}

/** Degrees clockwise from north, wrapped into 0–359 and rounded for display. */
private fun Float.normalisedDegrees(): String {
    val wrapped = ((roundToInt() % 360) + 360) % 360
    return wrapped.toString().padStart(3, '0')
}

/**
 * A 44 dp touch target, which is the floor for a control on a moving surface.
 *
 * **Public because it is the glass's row height, not just this file's.** A host
 * that puts its own chrome over the imagery — the route detail's app bar puts a
 * back button, a title and two actions there — has to line up with the plates the
 * globe draws, and a title plate two-thirds the height of the button beside it
 * reads as two systems again, which is the thing the plate treatment exists to
 * stop. One number, one edge.
 */
val ControlSize = 44.dp

/** The gap between two plates that are not one plate — the credit and the stack. */
internal val ControlGap = 8.dp

/**
 * How opaque every plate over the imagery is.
 *
 * One value, shared by the camera stack, the airport labels and the credit.
 * Enough that a four-letter code stays legible over both ocean and cloud, little
 * enough that it reads as a layer over a photograph rather than as a panel
 * bolted onto one.
 *
 * Public alongside [ControlSize], and for the same reason: a host drawing its own
 * chrome over the imagery has to make the same plate, not a similar one.
 */
const val PlateAlpha = 0.82f

/** The square a cell's mark is drawn in. Well inside the 44 dp it is tapped in. */
private val GlyphSize = 18.dp

/** How far the rule between two cells is held off the plate edge. */
private val RuleInset = 8.dp

/** Taller than a square cell: a needle over a figure. */
private val HeadingCellHeight = 52.dp

private val NeedleBox = 16.dp

/** The app's hairline, which is what every other stroke on the glass is. */
private val MarkStrokeDp = 1.5.dp

/** How far a zoom mark's arms reach into its box. */
private const val MarkArm = 0.78f

/** How long a corner bracket's arms are, as a fraction of the box. */
private const val BracketArm = 0.32f

/** Half the width of the compass card at its waist. */
private const val NeedleWaist = 0.16f

/**
 * The credit the imagery provider requires, as something a host can draw.
 *
 * Public, and the one thing about the provider that is, because a host needs it
 * to place the credit in its own layout — and making the whole provider public to
 * say one sentence would open the tile pipeline to `:app` for no other reason. It
 * reads the provider the build actually selected, so it changes when the provider
 * does: with an ArcGIS key in `local.properties` this is Esri's credit, without
 * one it is NASA's. It used to be a `const val` naming one provider, which is how
 * the credit stayed on NASA while the tiles could have come from anywhere.
 */
object GlobeImagery {
    /** What the active provider requires drawn over its imagery. */
    val attribution: ImageryAttribution get() = TileProviders.active.attribution

    /**
     * The credit a clone without an ArcGIS key shows — NASA's. The same on every
     * machine, which is what a screenshot golden of a screen that prints the
     * credit needs: [attribution] follows `local.properties`, and a golden
     * recorded beside a key and verified on a runner without one would differ
     * in the text alone.
     */
    val keyless: ImageryAttribution get() = TileProviders.select(null).attribution
}
