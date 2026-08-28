package com.github.daanbouwman.flightplanner.core.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.github.daanbouwman.flightplanner.core.designsystem.R
import com.github.daanbouwman.flightplanner.core.designsystem.motion.FlightMotion
import com.github.daanbouwman.flightplanner.core.designsystem.motion.LocalReduceMotion
import com.github.daanbouwman.flightplanner.core.designsystem.theme.FlightPlannerTheme
import com.github.daanbouwman.flightplanner.core.designsystem.theme.LocalSkyColors
import com.github.daanbouwman.flightplanner.core.designsystem.theme.asChartFigure
import com.github.daanbouwman.flightplanner.model.Runway
import com.github.daanbouwman.flightplanner.model.SurfaceKind
import com.github.daanbouwman.flightplanner.routing.GreatCircle
import com.github.daanbouwman.flightplanner.routing.SurfaceWind
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * A chart of the runways at one airport — a true relative plan when the
 * dataset publishes threshold coordinates for every end, and a compass-style
 * schematic when it does not.
 *
 * There is no desktop precedent to port — the reference's airport detail is
 * a plain text table via `format_runway()`. The model already splits a
 * physical strip into two ends (see [Runway]'s own KDoc), and
 * [pairPhysicalRunways] reunites them — by opposite heading and matching
 * length, not by parsing `09`/`27` or `18R`/`36L` idents.
 *
 * **True positions, when the data has them.** [Runway.latitude] and
 * [Runway.longitude] are the real threshold coordinates OurAirports
 * publishes for well-documented fields — every major hub checked while
 * building this (Schiphol, Denver, Heathrow, LAX, JFK, Charles de Gaulle,
 * Edwards Air Force Base) has them for every end, though the dataset's own
 * KDoc on [Runway] warns they are "null upstream for most runways" overall —
 * small and GA fields much less consistently. When every diagrammed end has
 * both, [positionedRays] projects the real coordinates onto a small local
 * plane (equirectangular; the airport's own extent is a few kilometres, far
 * too small for the earth's curvature to matter) and draws each physical
 * runway as the true segment between its two thresholds, scaled to fit — a
 * genuine miniature of the airport, not a device explaining it.
 *
 * **Otherwise, a compass schematic — and why it must not draw every line
 * through one shared point.** A first version did exactly that: one ray per
 * end, from a shared centre, at its heading. It looked like a starburst for
 * any airport with several near-parallel or identical-heading runways —
 * Edwards Air Force Base's three, Denver's twin clusters of four — because
 * real parallel runways run *beside* each other, never through one point.
 * [layoutRunways] separates a "roughly parallel" family (within
 * [ParallelBandDeg] of each other) into evenly spaced lanes, perpendicular to
 * their shared direction, and leaves runways whose headings differ enough to
 * plausibly cross drawn through the centre, exactly as before.
 *
 * Runway ends with no published [Runway.trueHeadingDeg] (real on grass
 * strips) cannot be drawn either way and are skipped, with a one-line count
 * underneath rather than a silent omission — they still appear in the
 * caller's own textual runway list.
 */
@Composable
fun RunwayDiagram(
    runways: List<Runway>,
    modifier: Modifier = Modifier,
    /**
     * The reported surface wind, if there is one.
     *
     * When present the diagram grows a windsock and highlights the end a pilot
     * would most likely use. **This is the reason the wind belongs here rather than
     * only in the weather panel:** a direction in degrees has to be compared against
     * a runway heading in the reader's head, and the comparison is the whole
     * question. Drawn in the same frame as the runways it is not a comparison any
     * more, it is a picture — the sock points down the strip or across it.
     */
    wind: DiagramWind? = null,
    hardColor: Color = MaterialTheme.colorScheme.onSurface,
    softColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    litColor: Color = MaterialTheme.colorScheme.primary,
) {
    val diagrammed = remember(runways) { runways.filter { it.trueHeadingDeg != null } }
    val excludedCount = runways.size - diagrammed.size
    val maxLengthFt = remember(diagrammed) { diagrammed.maxOfOrNull { it.lengthFt } ?: 0 }
    val positioned = remember(diagrammed) {
        diagrammed.isNotEmpty() && diagrammed.all { it.latitude != null && it.longitude != null }
    }
    val laneOffsets = remember(diagrammed, positioned) {
        if (positioned) null else layoutRunways(diagrammed)
    }
    // **Both** paths need the pairing. The positioned one draws each strip
    // threshold-to-threshold between its two ends; the lane-schematic one needs to
    // know whether an end *has* an opposite number, because that decides where its
    // designator goes — see [laneRay].
    val groups = remember(diagrammed) { pairPhysicalRunways(diagrammed) }
    val pairedEnds = remember(groups) {
        groups.filter { it.runwayIndices.size > 1 }.flatMap { it.runwayIndices }.toSet()
    }

    val textMeasurer = rememberTextMeasurer()
    val identStyle = MaterialTheme.typography.labelSmall.asChartFigure()
        .copy(color = MaterialTheme.colorScheme.onSurface)

    // The end a pilot would use, or null when the wind cannot decide. Computed over
    // the diagrammed ends only, so the index lines up with `rays`.
    val favouredEnd = remember(diagrammed, wind) {
        SurfaceWind.favouredEnd(
            // To the nearest degree: half a degree of runway heading is 0.17 kt of
            // crosswind in a 20 kt wind, well under the resolution of the report it
            // is being compared against.
            runwayHeadingsDeg = diagrammed.map { requireNotNull(it.trueHeadingDeg).roundToInt() },
            windFromDeg = wind?.directionFromDeg?.takeIf { wind.hasDirection },
            windSpeedKt = wind?.speedKt,
        )
    }
    val sockInk = LocalSkyColors.current.sock
    // The preferred end, said in the ident rather than only in the halo under the
    // line: the halo tells you *which strip*, the bold orange numeral tells you which
    // *end of it*, and the second question is the one a pilot is actually asking.
    val favouredIdentStyle = identStyle.copy(color = sockInk.band, fontWeight = FontWeight.Bold)
    val reduceMotion = LocalReduceMotion.current
    // The flutter: a couple of degrees of sway, and only when the sock is actually
    // being held out by something. A sock that waves in a calm is a decoration, and
    // the period shortens with speed because a real one snaps rather than swings.
    val flutterEnabled = !reduceMotion && wind != null && wind.speedKt > SurfaceWind.SockLimpKt
    val flutter: State<Float> = if (!flutterEnabled) {
        remember { mutableFloatStateOf(0f) }
    } else {
        rememberInfiniteTransition(label = "windsock").animateFloat(
            initialValue = -1f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = flutterPeriodMillis(requireNotNull(wind).speedKt)),
                // Reverse rather than Restart: a sock sways back through where it
                // came from. Restart would make it jump back and read as a stutter.
                repeatMode = RepeatMode.Reverse,
            ),
            label = "windsockSway",
        )
    }

    // The sock's drag — the app's first custom gesture, and the one piece of the
    // weather feature that is interaction for its own sake. It is defensible
    // because a sock is a physical object a pilot already knows the feel of, and
    // because **it springs back to the truth**: the toy cannot leave a false
    // reading on screen. Everything computed from the wind — `favouredEnd`, the
    // halo, the bold ident — is derived from `wind` and never from this, so a
    // swung sock cannot move the recommendation. That falls out of the structure
    // rather than needing a rule.
    val haptics = LocalHapticFeedback.current
    val dragged = remember { Animatable(0f) }
    val dragScope = rememberCoroutineScope()
    val draggable = wind != null
    // Captured out here because `FlightMotion` is `@Composable` and the release
    // runs inside a coroutine launched from a pointer callback, where there is no
    // composition to read it from.
    val releaseSpec = FlightMotion.spatial<Float>()

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clearAndSetSemantics { }
                .then(
                    if (!draggable) {
                        Modifier
                    } else {
                        Modifier.pointerInput(wind, reduceMotion) {
                            // `detectHorizontalDragGestures`, **not**
                            // `detectDragGestures`. All three of this component's
                            // hosts sit inside a `verticalScroll`, and the
                            // unqualified detector claims both axes on the first
                            // touch slop — so a user trying to scroll past an
                            // airport would find the page stuck and a sock
                            // twitching under their thumb. The horizontal detector
                            // yields the vertical axis to the scroll container,
                            // which is the behaviour a nested gesture owes its
                            // host.
                            var accumulated = 0f
                            detectHorizontalDragGestures(
                                onDragStart = {
                                    accumulated = 0f
                                    // A tick on commitment, never on browse: one
                                    // here, one when it settles, and nothing in
                                    // between however far the finger travels.
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                },
                                onHorizontalDrag = { change, amount ->
                                    change.consume()
                                    accumulated += amount
                                    // Tracked with no spring while the finger is
                                    // down. A spring lags, and a surface being
                                    // dragged has to feel attached to the finger —
                                    // the same rule `SwipeActionBackground` states
                                    // for its own `progress`.
                                    dragScope.launch {
                                        dragged.snapTo(
                                            sockDragOffsetDeg(accumulated, requireNotNull(wind).speedKt),
                                        )
                                    }
                                },
                                onDragEnd = {
                                    dragScope.launch {
                                        // The release is a discrete event and gets
                                        // a sprung response. Under reduce motion it
                                        // returns immediately: the drag itself is
                                        // direct manipulation and stays, but an
                                        // unrequested animation does not.
                                        if (reduceMotion) {
                                            dragged.snapTo(0f)
                                        } else {
                                            dragged.animateTo(0f, animationSpec = releaseSpec)
                                        }
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                },
                                onDragCancel = {
                                    dragScope.launch {
                                        if (reduceMotion) dragged.snapTo(0f) else dragged.animateTo(0f, releaseSpec)
                                    }
                                },
                            )
                        }
                    },
                )
                .drawWithCache {
                    if (diagrammed.isEmpty() || size.minDimension <= 0f) return@drawWithCache onDrawBehind { }

                    val center = Offset(size.width / 2f, size.height / 2f)
                    val labelMargin = LabelMarginDp.dp.toPx()
                    val radius = (size.minDimension / 2f) - labelMargin

                    val rays = if (positioned) {
                        positionedRays(diagrammed, groups, center, radius, maxLengthFt)
                    } else {
                        val laneSpacingPx = LaneSpacingFraction * radius
                        diagrammed.mapIndexed { index, runway ->
                            laneRay(
                                runway = runway,
                                laneOffsetPx = requireNotNull(laneOffsets)[index] * laneSpacingPx,
                                center = center,
                                radius = radius,
                                maxLengthFt = maxLengthFt,
                                paired = index in pairedEnds,
                            )
                        }
                    }
                    // At the strip's **threshold**, pushed a little further out
                    // along the strip so it clears the line.
                    //
                    // The threshold is the convention every real diagram uses — see
                    // [RunwayRay] for the report that proved this diagram was
                    // contradicting it. The outward push is a separate, older fix:
                    // centred on the anchor, every ident sat on top of the line it
                    // labelled, and at `labelSmall` over a 3 dp stroke the digits lost
                    // their counters — `08` and `06` stopped being distinguishable,
                    // which is the one thing a runway ident has to be. `LabelMarginDp`
                    // already holds this space back from the radius, so pushing out
                    // costs nothing.
                    //
                    // The push runs from the tip *toward* the threshold, so it points
                    // away from the strip in both layouts. Taking it along the ray's
                    // own direction would push each label back across the runway it
                    // labels.
                    val labelPush = LabelPushDp.dp.toPx()
                    val labels = diagrammed.mapIndexed { index, runway ->
                        val ray = rays[index]
                        val style = if (index == favouredEnd) favouredIdentStyle else identStyle
                        val outward = ray.threshold - ray.tip
                        val length = hypot(outward.x, outward.y)
                        val anchor = if (length <= 0f) {
                            ray.threshold
                        } else {
                            Offset(
                                ray.threshold.x + outward.x / length * labelPush,
                                ray.threshold.y + outward.y / length * labelPush,
                            )
                        }
                        textMeasurer.measure(runway.ident, style) to anchor
                    }

                    // The sock stands in a corner rather than at the centre: the
                    // middle is where the runways cross, and a field's real sock sits
                    // off to the side of them anyway.
                    // Down and to the left of the runways, at the rim rather than in
                    // the corner: a field's sock sits beside the strips, and out in the
                    // corner of a square frame it read as a stray glyph rather than as
                    // part of the airport.
                    val mast = Offset(
                        center.x - radius * SockOffsetFraction,
                        center.y + radius * SockOffsetFraction,
                    )
                    val mastRadius = size.minDimension * SockMastRadiusFraction
                    val sockLength = size.minDimension * SockLengthFraction
                    val sockThroat = size.minDimension * SockThroatFraction
                    val favouredStroke = FavouredStrokeDp.dp.toPx()

                    onDrawBehind {
                        rays.forEachIndexed { index, ray ->
                            val runway = diagrammed[index]
                            val hard = runway.surfaceKind == SurfaceKind.HARD
                            // The favoured end gets a halo under its own line rather
                            // than a different colour: surface and lighting already
                            // own colour here, and a fourth meaning for it would make
                            // all four harder to read.
                            if (index == favouredEnd) {
                                drawLine(
                                    color = sockInk.band.copy(alpha = FavouredAlpha),
                                    start = ray.origin,
                                    end = ray.tip,
                                    strokeWidth = favouredStroke,
                                    cap = StrokeCap.Round,
                                )
                            }
                            drawLine(
                                color = if (hard) hardColor else softColor,
                                start = ray.origin,
                                end = ray.tip,
                                strokeWidth = if (runway.widthFt >= WideRunwayFt) WideStrokeDp.dp.toPx() else NarrowStrokeDp.dp.toPx(),
                                pathEffect = if (hard) null else dashEffect,
                            )
                            if (runway.lighted) {
                                drawCircle(color = litColor, radius = LitDotRadiusDp.dp.toPx(), center = ray.tip)
                            }
                        }
                        labels.forEach { (layout, anchor) ->
                            drawText(
                                textLayoutResult = layout,
                                topLeft = Offset(
                                    anchor.x - layout.size.width / 2f,
                                    anchor.y - layout.size.height / 2f,
                                ),
                            )
                        }

                        // The sock last, over everything: it is the only thing here
                        // that is a physical object rather than a projection, and it
                        // should look like it is standing on the field.
                        if (wind != null) {
                            // The mast, seen from directly above: a dot. See
                            // `windsockPath` on why it is not a line.
                            drawCircle(
                                color = sockInk.mast,
                                radius = mastRadius,
                                center = mast,
                            )
                            val lift = SurfaceWind.sockLift(wind.speedKt)
                            // A variable wind gets a lifted sock with no bearing to
                            // point along, so it is drawn across the mast — a sock
                            // being pushed around, which is what VRB means. Never
                            // pointing: a direction the station did not report is the
                            // one thing this must not draw.
                            // The drag is added to the drawn bearing and to nothing
                            // else. It is the last term for a reason: everything
                            // upstream — the favoured end, the halo, the bold
                            // ident — reads the reported wind, so swinging the sock
                            // moves the sock and cannot move the recommendation.
                            val bearing = if (wind.hasDirection) {
                                requireNotNull(wind.directionFromDeg) + 180f +
                                    flutter.value * FlutterDeg + dragged.value
                            } else {
                                VariableBearingDeg + flutter.value * VariableFlutterDeg + dragged.value
                            }
                            drawPath(
                                path = windsockPath(mast, sockLength, sockThroat, bearing, lift),
                                color = sockInk.band,
                            )
                            WindsockStripes.forEach { (from, to) ->
                                drawPath(
                                    path = windsockStripe(
                                        mast, sockLength, sockThroat, bearing, lift, from, to,
                                    ),
                                    color = sockInk.alternateBand,
                                )
                            }
                        }
                    }
                },
        )
        if (excludedCount > 0) {
            Text(
                text = pluralStringResource(R.plurals.ds_runway_diagram_excluded, excludedCount, excludedCount),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

/**
 * One runway end's line: where it starts, where it tips, and where its
 * designator belongs.
 *
 * ### Why [threshold] is carried rather than derived
 *
 * Every real airport diagram — FAA, Jeppesen, the plate in a pilot's bag —
 * paints the designator **at the threshold**: the end you cross on landing and
 * start the roll from on takeoff. This diagram used to paint it at [tip], and in
 * both layouts the tip is the end the runway *points toward*, so every ident sat
 * at the wrong end of its own strip.
 *
 * That was invisible until the favoured-end highlight arrived and made it
 * consequential. Reported as *"the preferred runway is showing the one going
 * along with the wind instead of against it"* — and neither the sock nor the
 * highlight was wrong. The wind was from about 295°, runway 30 was nearly
 * straight into it, and `favouredEnd` picked 30 correctly. What misled was
 * seeing `30` painted at the north-west end, applying the convention every
 * diagram uses, and concluding the 30 threshold was there — so that departing 30
 * meant rolling south-east, downwind.
 *
 * **No information is lost by moving it**, which settles the obvious objection
 * that a label at the far end shows which way the runway points: a designator is
 * self-describing. `30` *means* heading 300°, so the direction is still readable
 * off the digits once the label is where the convention says it goes.
 *
 * The two layouts reach that end differently and neither can be inferred from
 * the ray alone, which is why this is a field:
 *
 * - **Positioned** — [origin] already *is* this end's own published threshold,
 *   both for a paired runway and for an unpaired stub.
 * - **Lane schematic** — the two paired rays of one strip *share* an origin, so
 *   `origin` names the lane rather than an end. The tip is mirrored about it
 *   instead, which puts `30` where `12`'s tip is and vice versa.
 */
internal data class RunwayRay(val origin: Offset, val tip: Offset, val threshold: Offset)

/**
 * The true relative layout: each physical runway (see [pairPhysicalRunways])
 * drawn as the real segment between its two thresholds, projected onto a
 * small local plane and scaled so the single farthest point lands exactly on
 * [radius]. A physical runway with no partner among [runways] — its own
 * opposite end lacks a published heading — draws as a stub from its one real
 * point, at its own heading, the same length logic [laneRay] uses.
 *
 * Every entry in [runways] must have both [Runway.latitude] and
 * [Runway.longitude]; the caller only takes this path once it has checked
 * that. [groups] is [pairPhysicalRunways] run over the same [runways] —
 * passed in rather than recomputed here so the caller can `remember` it
 * across recompositions, the same way the lane-schematic path already does.
 */
internal fun positionedRays(
    runways: List<Runway>,
    groups: List<PhysicalRunway>,
    center: Offset,
    radius: Float,
    maxLengthFt: Int,
): List<RunwayRay> {
    val refLat = runways.map { requireNotNull(it.latitude) }.average()
    val refLon = runways.map { requireNotNull(it.longitude) }.average()
    val local = runways.map { projectLocal(requireNotNull(it.latitude), requireNotNull(it.longitude), refLat, refLon) }

    val maxExtent = local.maxOf { hypot(it.x, it.y) }
    val scale = if (maxExtent > 0f) radius / maxExtent else 1f
    fun toScreen(p: Offset) = Offset(center.x + p.x * scale, center.y + p.y * scale)

    val rays = arrayOfNulls<RunwayRay>(runways.size)
    for (group in groups) {
        if (group.runwayIndices.size == 2) {
            val (i, j) = group.runwayIndices
            val a = toScreen(local[i])
            val b = toScreen(local[j])
            // Each end's own published coordinate is its threshold, so the ray
            // runs threshold-to-threshold and the designator goes at the origin.
            rays[i] = RunwayRay(origin = a, tip = b, threshold = a)
            rays[j] = RunwayRay(origin = b, tip = a, threshold = b)
        } else {
            val i = group.runwayIndices.single()
            val origin = toScreen(local[i])
            val heading = requireNotNull(runways[i].trueHeadingDeg)
            rays[i] = RunwayRay(
                origin = origin,
                tip = extend(origin, heading, radius, runways[i].lengthFt, maxLengthFt),
                threshold = origin,
            )
        }
    }
    return rays.map { requireNotNull(it) }
}

/**
 * A local, small-scale projection of ([lat], [lon]) around ([refLat],
 * [refLon]): equirectangular, with longitude compressed by the cosine of the
 * reference latitude, exactly as [com.github.daanbouwman.flightplanner.routing.MapFrame]
 * does at a larger scale. An airport's own extent is at most a few
 * kilometres — far too small for the earth's curvature to matter — so a full
 * great-circle projection would be doing work this never needs.
 *
 * Units are metres, +x east — and +y **south**, not north: a point north of
 * the reference already comes out at negative y here, matching the screen's
 * own y-down convention directly, so the caller can add this straight onto
 * a pixel offset with no second flip.
 *
 * `internal` for [RunwayDiagramLayoutTest].
 */
internal fun projectLocal(lat: Double, lon: Double, refLat: Double, refLon: Double): Offset {
    val x = (lon - refLon) * MetersPerDegreeLat * cos(Math.toRadians(refLat))
    val y = (lat - refLat) * MetersPerDegreeLat
    return Offset(x.toFloat(), -y.toFloat())
}

/** Metres per degree of latitude — constant enough at any one airport's scale. */
private const val MetersPerDegreeLat = 111_320.0

/**
 * [runway]'s line from a point [laneOffsetPx] to one side of [center] — see
 * [layoutRunways] — at its own true heading and scaled by its length
 * relative to [maxLengthFt].
 *
 * The lane offset is applied perpendicular to [Runway.trueHeadingDeg]
 * *folded to a half-circle* (see [foldToHalfCircle]), not to the heading
 * itself, so both ends of one physical strip — whose headings are ~180°
 * apart — displace to the *same* point rather than in opposite directions.
 *
 * [paired] is whether this end has an opposite number among the diagrammed
 * runways, and it decides where the designator goes. **It is not an optimisation
 * and the mirror is wrong without it** — see below.
 */
internal fun laneRay(
    runway: Runway,
    laneOffsetPx: Double,
    center: Offset,
    radius: Float,
    maxLengthFt: Int,
    paired: Boolean,
): RunwayRay {
    val heading = requireNotNull(runway.trueHeadingDeg)
    val foldedRad = Math.toRadians(foldToHalfCircle(heading))
    val perpX = cos(foldedRad).toFloat()
    val perpY = sin(foldedRad).toFloat()
    val origin = Offset(
        center.x + perpX * laneOffsetPx.toFloat(),
        center.y + perpY * laneOffsetPx.toFloat(),
    )
    val tip = extend(origin, heading, radius, runway.lengthFt, maxLengthFt)
    // A paired end's threshold is mirrored about the origin rather than taken
    // from it: both ends of one strip share this origin, so it names the lane and
    // not an end. Reflecting the tip through it lands each designator where its
    // opposite number's ray tip is, which is the threshold a diagram paints it at.
    //
    // **An unpaired end must not be mirrored.** `pairPhysicalRunways` produces a
    // physical runway of one whenever the reciprocal end has no published heading,
    // and then only the `origin → tip` half is ever drawn — so the mirror lands on
    // empty canvas on the far side of the origin, and the label, pushed a further
    // `LabelPushDp` outward from there, ends up detached from its own line and
    // possibly sitting on a neighbouring strip. Its origin *is* its threshold, the
    // same conclusion `positionedRays` reaches for the same case.
    return RunwayRay(
        origin = origin,
        tip = tip,
        threshold = if (paired) origin * 2f - tip else origin,
    )
}

/**
 * A point [lengthFt] (as a fraction of [maxLengthFt], floored at
 * [MinLengthFraction]) along [headingDeg] from [origin] — 0° is up (true
 * north), clockwise, the standard compass-to-screen mapping.
 */
private fun extend(origin: Offset, headingDeg: Double, radius: Float, lengthFt: Int, maxLengthFt: Int): Offset {
    val angleRad = Math.toRadians(headingDeg)
    val dirX = sin(angleRad).toFloat()
    val dirY = -cos(angleRad).toFloat()
    val fraction = if (maxLengthFt > 0) {
        (lengthFt.toFloat() / maxLengthFt).coerceIn(MinLengthFraction, 1f)
    } else {
        1f
    }
    val length = radius * fraction
    return Offset(origin.x + dirX * length, origin.y + dirY * length)
}

/** A heading folded onto [0, 180) — opposite ends of one strip fold to the same value. */
private fun foldToHalfCircle(headingDeg: Double): Double {
    val folded = headingDeg % 180.0
    return if (folded < 0.0) folded + 180.0 else folded
}

/** The shortest way from [b] to [a] on a circle, in degrees, always non-negative. */
private fun angularDifference(a: Double, b: Double): Double {
    val diff = ((a - b) % 360.0 + 540.0) % 360.0 - 180.0
    return abs(diff)
}

/**
 * One physical runway: the index (or two indices, one per end) of
 * [runways] it corresponds to, and the heading — folded to a half-circle —
 * its lane is laid out against.
 *
 * `internal` for [RunwayDiagramLayoutTest].
 */
internal data class PhysicalRunway(val runwayIndices: List<Int>, val orientationDeg: Double)

/**
 * Reunites [runways]' ends into physical strips, by opposite heading and
 * matching length rather than by parsing idents — see [RunwayDiagram]'s own
 * KDoc for why. Two ends pair when their headings are within
 * [OppositeToleranceDeg] of exactly 180° apart and their lengths are within
 * [lengthToleranceFt] of [Runway.lengthFt]'s own value; an end with no
 * matching partner (its own opposite end lacks a published heading, say)
 * becomes a physical runway of one.
 *
 * **More than one candidate can pass that test** — two side-by-side physical
 * runways sharing both a heading pair and a length, such as Zahedan
 * International's 17R/35L and 17L/35R, both 14,042 ft. Heading and length
 * alone cannot tell them apart, so [bearingDeviation] breaks the tie using
 * real position where it exists: the true reciprocal end is the one whose
 * actual bearing from this end matches [headingI] most closely, which is
 * exactly what a physical runway's own heading means. Found live against
 * Zahedan (OIZH) — the database query backing [RunwayDiagram]'s caller orders
 * same-length ends alphabetically by ident (17L, 17R, 35L, 35R rather than
 * true-pair order), and the old first-match rule paired 17L with 35L instead
 * of 35R, drawing the two strips crossing like an X instead of running
 * parallel.
 *
 * `internal` for [RunwayDiagramLayoutTest].
 */
internal fun pairPhysicalRunways(runways: List<Runway>): List<PhysicalRunway> {
    val used = BooleanArray(runways.size)
    val groups = mutableListOf<PhysicalRunway>()
    for (i in runways.indices) {
        if (used[i]) continue
        used[i] = true
        val headingI = requireNotNull(runways[i].trueHeadingDeg)
        val candidates = (i + 1 until runways.size).filter { j ->
            if (used[j]) return@filter false
            val headingJ = requireNotNull(runways[j].trueHeadingDeg)
            val opposite = angularDifference(headingI + 180.0, headingJ) < OppositeToleranceDeg
            val tolerance = lengthToleranceFt(minOf(runways[i].lengthFt, runways[j].lengthFt))
            val sameLength = abs(runways[i].lengthFt - runways[j].lengthFt) <= tolerance
            opposite && sameLength
        }
        val partner = when (candidates.size) {
            0 -> -1
            1 -> candidates.single()
            else -> requireNotNull(
                candidates.minByOrNull { j -> bearingDeviation(runways[i], runways[j], headingI) },
            )
        }
        val indices = if (partner >= 0) {
            used[partner] = true
            listOf(i, partner)
        } else {
            listOf(i)
        }
        groups += PhysicalRunway(indices, foldToHalfCircle(headingI))
    }
    return groups
}

/**
 * How far [to]'s real bearing from [from] departs from [headingDeg] — the
 * disambiguator [pairPhysicalRunways] uses once heading and length alone
 * leave more than one candidate. [Double.MAX_VALUE] when either end lacks
 * real coordinates, so an undecidable candidate sorts last rather than
 * winning a tie it cannot actually settle; when *no* candidate has position
 * data every one ties at [Double.MAX_VALUE] and `minByOrNull`'s stability
 * picks the first, exactly the prior first-match behaviour.
 */
private fun bearingDeviation(from: Runway, to: Runway, headingDeg: Double): Double {
    val lat = from.latitude
    val lon = from.longitude
    val toLat = to.latitude
    val toLon = to.longitude
    if (lat == null || lon == null || toLat == null || toLon == null) return Double.MAX_VALUE
    val bearing = GreatCircle.initialBearingDeg(lat1 = lat, lon1 = lon, lat2 = toLat, lon2 = toLon)
    return angularDifference(headingDeg, bearing)
}

private fun lengthToleranceFt(lengthFt: Int): Int = maxOf(MinLengthToleranceFt, (lengthFt * LengthToleranceFraction).toInt())

/**
 * A lane offset for each of [runways], as a multiple of the diagram's lane
 * spacing — 0 for a runway with nothing near its heading, a symmetric
 * integer step otherwise. Both ends of one physical strip always share the
 * same offset, so they draw as one continuous, merely-displaced line.
 *
 * Physical runways within [ParallelBandDeg] of each other (by their folded
 * heading) are one "roughly parallel" family and are laned side by side, in
 * heading order; a family of one physical runway needs no lane and stays at
 * offset 0, which is also what keeps two runways whose headings genuinely
 * differ — and which therefore plausibly cross in reality — drawn through
 * the shared centre exactly as before.
 *
 * Does not special-case a family straddling exactly 0°/180° (e.g. 178° and
 * 2°) — not exercised by the shipped dataset, where headings at or near due
 * north fold to values near 0 rather than split across the seam, and a
 * visual quirk rather than a correctness bug if it ever is.
 *
 * `internal` for [RunwayDiagramLayoutTest].
 */
internal fun layoutRunways(runways: List<Runway>): List<Double> {
    val offsets = DoubleArray(runways.size)
    if (runways.size <= 1) return offsets.toList()

    val groups = pairPhysicalRunways(runways)
    if (groups.size <= 1) return offsets.toList()

    val order = groups.indices.sortedBy { groups[it].orientationDeg }
    var i = 0
    while (i < order.size) {
        var j = i
        while (j + 1 < order.size &&
            groups[order[j + 1]].orientationDeg - groups[order[i]].orientationDeg < ParallelBandDeg
        ) {
            j++
        }
        val laneCount = j - i + 1
        if (laneCount > 1) {
            for (rank in i..j) {
                val lane = rank - i - (laneCount - 1) / 2.0
                groups[order[rank]].runwayIndices.forEach { index -> offsets[index] = lane }
            }
        }
        i = j + 1
    }
    return offsets.toList()
}

/** Physical runways closer together than this, by folded heading, share a lane family. */
private const val ParallelBandDeg = 45.0

/** Ends within this of exactly 180° apart are considered the same physical strip. */
private const val OppositeToleranceDeg = 3.0

/** Length tolerance for pairing ends, as a fraction of the shorter end's own length. */
private const val LengthToleranceFraction = 0.05

/** Length tolerance floor, so a short strip's rounding doesn't fail to pair at all. */
private const val MinLengthToleranceFt = 50

/** Perpendicular spacing between adjacent lanes, as a fraction of the diagram's radius. */
private const val LaneSpacingFraction = 0.14

private val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))

/** Runways at or above this width draw with the wider stroke bucket. */
private const val WideRunwayFt = 100

private const val NarrowStrokeDp = 2f
private const val WideStrokeDp = 4f
private const val LitDotRadiusDp = 2.5f

/** Room reserved around the circle for ident labels at the ray tips. */
private const val LabelMarginDp = 18f

/** A floor on stub length, so an unpaired end — real position or not — stays visible. */
private const val MinLengthFraction = 0.35f

@LightDarkPreview
@Composable
private fun RunwayDiagramPreview() {
    FlightPlannerTheme(dynamicColor = false) {
        RunwayDiagram(
            runways = listOf(
                previewRunway(ident = "09", heading = 90.0, lengthFt = 12_467, widthFt = 200, lit = true),
                previewRunway(ident = "27", heading = 270.0, lengthFt = 12_467, widthFt = 200, lit = true),
                previewRunway(ident = "18R", heading = 183.0, lengthFt = 8_202, widthFt = 150, lit = true),
                previewRunway(ident = "36L", heading = 3.0, lengthFt = 8_202, widthFt = 150, lit = false),
                previewRunway(
                    ident = "RWY",
                    heading = null,
                    lengthFt = 2_400,
                    widthFt = 60,
                    lit = false,
                    surfaceKind = SurfaceKind.GRASS,
                ),
            ),
            modifier = Modifier.padding(16.dp),
        )
    }
}

/**
 * Schiphol's real threshold coordinates — the same shape [RunwayDiagram]'s
 * own KDoc names, and the case [positionedRays] exists for: three
 * near-parallel runways a real lane layout could only approximate, drawn
 * here to their true relative positions.
 */
@LightDarkPreview
@Composable
private fun RunwayDiagramPositionedPreview() {
    FlightPlannerTheme(dynamicColor = false) {
        RunwayDiagram(
            runways = listOf(
                previewRunway("09", 87.0, 11_318, 148, lit = true, lat = 52.3166, lon = 4.74635),
                previewRunway("27", 267.0, 11_318, 148, lit = true, lat = 52.3184, lon = 4.79689),
                previewRunway("06", 58.0, 11_283, 148, lit = true, lat = 52.2879, lon = 4.73402),
                previewRunway("24", 238.0, 11_283, 148, lit = true, lat = 52.3046, lon = 4.77752),
                previewRunway("18L", 183.0, 11_155, 148, lit = true, lat = 52.3213, lon = 4.77996),
                previewRunway("36R", 3.0, 11_155, 148, lit = true, lat = 52.2908, lon = 4.77735),
                previewRunway("18C", 183.0, 10_826, 148, lit = true, lat = 52.3314, lon = 4.74003),
                previewRunway("36C", 3.0, 10_826, 148, lit = true, lat = 52.3018, lon = 4.73750),
                previewRunway("18R", 183.0, 12_467, 198, lit = true, lat = 52.3627, lon = 4.71193),
                previewRunway("36L", 3.0, 12_467, 198, lit = true, lat = 52.3286, lon = 4.70884),
                previewRunway("04", 41.0, 6_627, 148, lit = true, lat = 52.3004, lon = 4.78348),
                previewRunway("22", 221.0, 6_627, 148, lit = true, lat = 52.3140, lon = 4.80302),
            ),
            modifier = Modifier.padding(16.dp),
        )
    }
}

private fun previewRunway(
    ident: String,
    heading: Double?,
    lengthFt: Int,
    widthFt: Int,
    lit: Boolean,
    surfaceKind: SurfaceKind = SurfaceKind.HARD,
    lat: Double? = null,
    lon: Double? = null,
): Runway = Runway(
    id = ident.hashCode(),
    airportId = 0,
    ident = ident,
    trueHeadingDeg = heading,
    lengthFt = lengthFt,
    widthFt = widthFt,
    surface = "ASPH",
    surfaceKind = surfaceKind,
    latitude = lat,
    longitude = lon,
    elevationFt = 0,
    lighted = lit,
)

/**
 * How fast the sock sways, in milliseconds per half-cycle.
 *
 * Shorter at higher speeds: a light-air sock swings lazily, a gusty one snaps. The
 * floor stops a gale turning it into a vibration, which would read as an error
 * state rather than as weather.
 */
private fun flutterPeriodMillis(windSpeedKt: Int): Int =
    (FlutterReferenceMillis * SurfaceWind.SockFullKt / windSpeedKt.coerceAtLeast(1))
        .coerceIn(FlutterMinMillis, FlutterMaxMillis)

/** Where the sock stands, and how big it is, as fractions of the diagram. */
private const val SockOffsetFraction = 0.62f
private const val SockMastRadiusFraction = 0.011f

private const val SockLengthFraction = 0.22f
private const val SockThroatFraction = 0.021f

/** How far past its ray tip an ident is drawn. */
private const val LabelPushDp = 11f

private const val FavouredStrokeDp = 7f
private const val FavouredAlpha = 0.30f

/** How many degrees the sock sways either side of the reported direction. */
private const val FlutterDeg = 4.5f

/** Where a sock with no reported direction points, and how far it wanders. */
private const val VariableBearingDeg = 90f
private const val VariableFlutterDeg = 55f

private const val FlutterReferenceMillis = 1_500
private const val FlutterMinMillis = 420
private const val FlutterMaxMillis = 2_600
