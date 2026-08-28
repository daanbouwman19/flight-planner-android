package com.github.daanbouwman.flightplanner.core.designsystem.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.daanbouwman.flightplanner.core.designsystem.motion.FlightMotion
import com.github.daanbouwman.flightplanner.core.designsystem.motion.LocalReduceMotion
import com.github.daanbouwman.flightplanner.core.designsystem.theme.FlightRulesColors
import com.github.daanbouwman.flightplanner.core.designsystem.theme.LocalFlightRulesColors
import com.github.daanbouwman.flightplanner.core.designsystem.theme.LocalSkyColors
import com.github.daanbouwman.flightplanner.core.designsystem.theme.SkyBand
import com.github.daanbouwman.flightplanner.core.designsystem.theme.SkyColors
import com.github.daanbouwman.flightplanner.core.designsystem.theme.bandFor
import com.github.daanbouwman.flightplanner.core.designsystem.theme.bandsAgreeOnPolarity
import com.github.daanbouwman.flightplanner.core.designsystem.theme.blendBands
import com.github.daanbouwman.flightplanner.core.designsystem.theme.asChartFigure
import com.github.daanbouwman.flightplanner.model.Ceiling
import com.github.daanbouwman.flightplanner.model.CloudCover
import com.github.daanbouwman.flightplanner.model.GroundCondition
import com.github.daanbouwman.flightplanner.model.Metar
import com.github.daanbouwman.flightplanner.model.SkyCover
import com.github.daanbouwman.flightplanner.model.WeatherDescriptor
import com.github.daanbouwman.flightplanner.routing.CelestialState
import kotlin.math.abs

/**
 * Which sky the scene is painted in.
 *
 * A parameter rather than something derived inside the component, for the same
 * reason `RunwayDiagram` does not read a clock: a composable that computes the sun
 * position from `System.currentTimeMillis()` cannot be previewed at dusk, cannot be
 * tested, and recomposes on a schedule nobody asked for. The caller resolves this
 * from the airport's coordinates and the observation time.
 */
enum class SkyPhase { DAY, TWILIGHT, NIGHT }

/**
 * A vertical cut through the atmosphere at one airport.
 *
 * **This is not a picture of the sky.** It is a profile view, read the way an
 * approach plate's is: altitude on the Y axis, every cloud deck at its true base,
 * the ceiling thresholds as hairlines, the ground at the bottom.
 *
 * ### Why a cross-section and not a scene
 *
 * The brief asked for layers, and layer *altitude* is the whole content of that
 * word. A naturalistic sky cannot express it — decks only stack by painting order,
 * so a 700 ft overcast and a 25,000 ft cirrus look identical. On an altitude axis
 * they cannot.
 *
 * It also makes the defect that prompted this redesign unrepresentable. The old
 * glyph could draw a sun over an IFR field because the category was a label pinned
 * beside a cartoon, and the two were free to disagree. Here the category is a
 * *consequence of the geometry*: a deck below the 1,000 ft hairline is visibly
 * beneath it, and there is no arrangement of this drawing that shows a low ceiling
 * as a nice day.
 *
 * ### What honest ignorance looks like
 *
 * Three things can be unknown independently, and each is drawn as hatch rather
 * than as some plausible default:
 *
 * - **No report at all** ([metar] is null) — the whole frame hatches. Airplane mode
 *   must read as *no data*, not as fine weather.
 * - **An unreported sky** ([SkyCover.Unknown]) — the air hatches over a flat
 *   neutral, and [AirSteps] is not drawn at all. Stepped air would assert that the
 *   atmosphere is known; blue air would assert that it is nice.
 * - **An unreported surface** ([GroundCondition.Unknown]) — the ground hatches.
 *   [com.github.daanbouwman.flightplanner.core.designsystem.theme.GroundInk] has no
 *   colour to offer for it, deliberately.
 *
 * These compose: a report with a temperature but no cloud group draws a real
 * ground under hatched air, which is exactly what it knows.
 *
 * ### The axis, and what goes first under pressure
 *
 * [AxisBreakpoints] explains why the scale is piecewise-linear. The numerals are
 * the first thing dropped when there is no room for them — at font scale 2.0 a
 * `labelSmall` "3,000" wants about 70 dp, a fifth of a 360 dp phone's content
 * width — and dropping them is safe because the *hairlines* carry the bands. A
 * deck at 700 ft still sits visibly below the 1,000 ft line with no label present.
 *
 * The diagram does not mirror in RTL: a cross-section's left-right axis is
 * physical, not reading-order, the same as `RunwayDiagram`'s compass. Only the
 * numerals get [asChartFigure], which pins their direction to LTR.
 *
 * Everything that moves — the sun and moon at true positions, deck drift,
 * precipitation, the windsock and its drag — arrives separately. This draws the
 * parts that hold still.
 */
@Composable
fun SkyProfile(
    metar: Metar?,
    modifier: Modifier = Modifier,
    /**
     * Where the Sun and the Moon stood at the moment of the observation, or null
     * when the report carries no position or no timestamp.
     *
     * Resolved by the caller, from `Metar.latitude`, `Metar.longitude` and
     * `Metar.observationEpochSeconds`, at the **observation** instant rather than
     * at now — see the class KDoc on why this composable takes no clock, and note
     * that drawing the current sun over a three-day-old report would put two
     * different moments in one frame.
     *
     * When null, [phase] decides the band and no bodies are drawn.
     */
    celestial: CelestialState? = null,
    /**
     * The band to paint when [celestial] is null.
     *
     * Still here, and still the reason this composable is testable and previewable
     * at dusk. With a [celestial] it is ignored: the band is then a continuous
     * blend from the sun's real elevation, which is what "crossfades rather than
     * snapping" means.
     */
    phase: SkyPhase = SkyPhase.DAY,
    height: Dp = SkyProfileHeight.AirportDetail,
    showAltitudeLabels: Boolean = true,
    colors: SkyColors = LocalSkyColors.current,
    rulesColors: FlightRulesColors = LocalFlightRulesColors.current,
    axisColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    unknownAirColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
) {
    val skyCover = metar?.skyCover ?: SkyCover.Unknown
    val decks = remember(skyCover) {
        (skyCover as? SkyCover.Layers)?.let { mergeDecks(it.layers) }.orEmpty()
    }
    val fractions = remember(decks) { deckFractions(decks) }
    val thicknesses = remember(decks, fractions) { deckThicknesses(decks, fractions) }
    val fogFraction = remember(metar) {
        metar?.let {
            fogHeightFraction(it.skyCover, it.presentWeather, it.visibilityStatuteMiles)
        } ?: 0f
    }
    // The band, blended from the sun's real elevation where there is one.
    //
    // The crossing between two bands that draw their cloud ink opposite ways round
    // cannot be served at any intermediate colour — `blendBands` proves why — so
    // where the polarity reverses the weight is driven by a short spring instead of
    // by the sun, turning twenty minutes of an invisible deck edge into a few
    // hundred milliseconds of one.
    val blend = celestial?.let { skyBlendFor(it.sun.elevationDeg) }
        ?: SkyBlend(from = phase, to = phase, weight = 0f)
    val bandFrom = colors.bandFor(blend.from)
    val bandTo = colors.bandFor(blend.to)
    val polarityAgrees = bandsAgreeOnPolarity(bandFrom, bandTo)
    // Called unconditionally and selected afterwards, never inside the branch: a
    // composable call that appears and disappears across recompositions corrupts
    // the slot table, and `polarityAgrees` changes whenever the theme does. This
    // works in every preview and fails on a theme switch if written the other way.
    val steppedWeight by animateFloatAsState(
        targetValue = if (blend.weight < 0.5f) 0f else 1f,
        animationSpec = FlightMotion.effects(),
        label = "skyBandPolarity",
    )
    val band = blendBands(bandFrom, bandTo, if (polarityAgrees) blend.weight else steppedWeight)
    val nightWeight = blend.bandPosition() / 2f
    val airKnown = skyCover !is SkyCover.Unknown
    val ground = metar?.groundCondition ?: GroundCondition.Unknown
    val groundColor = colors.ground[ground]

    val textMeasurer = rememberTextMeasurer()
    // Measured without a colour, and inked at draw time from the band. The numerals
    // sit on the sky rather than on the card surface, so `onSurfaceVariant` is
    // proved against a surface these pixels are nowhere near — on the night band it
    // lands around 2.3:1. [SkyBand.cloudEdge] is already proved against both ends of
    // every band, which is exactly the guarantee a numeral on the air needs. Keeping
    // the colour out of the measured style also means a band change costs a repaint
    // rather than a re-measure.
    val labelStyle = MaterialTheme.typography.labelSmall.asChartFigure()

    // The drift is the wind. A `State<Float>` rather than a `Float`, following
    // `Skeleton`: unwrapping it here would recompose the whole panel on every frame,
    // where reading it in the draw scope costs a redraw and nothing else.
    val reduceMotion = LocalReduceMotion.current
    val driftDirection = remember(metar) { sectionDrift(metar?.windDirectionDeg) }
    val driftPeriod = if (airKnown && decks.isNotEmpty() && driftDirection != 0f) {
        driftPeriodMillis(metar?.windSpeedKt ?: 0)
    } else {
        0
    }
    val driftPhase: State<Float> = if (reduceMotion || driftPeriod == 0) {
        // Off entirely, not merely slower. A calm field and a reduce-motion device
        // both get a still sky, which is the honest rendering of both.
        remember { mutableFloatStateOf(0f) }
    } else {
        rememberInfiniteTransition(label = "skyProfileDrift").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                // Linear and restarting, because this is translation rather than a
                // gesture response: a spring would make the air surge and ease, and
                // wind does not. The restart is seamless because each deck is drawn
                // twice, one frame width apart — see the draw block.
                animation = tween(durationMillis = driftPeriod, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "drift",
        )
    }

    // Precipitation is pinned rather than switched off under reduce motion, which
    // is a deliberate divergence from how the deck drift is handled. The drift is
    // the wind told twice — the sock says it too — so dropping it costs nothing.
    // The field is the *only* place in the scene that says it is raining, and a
    // reduce-motion setting is a request about motion, not about content.
    val precipPhase: State<Float> = if (reduceMotion) {
        remember { mutableFloatStateOf(0f) }
    } else {
        rememberInfiniteTransition(label = "skyProfilePrecipitation").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = PrecipPeriodMillis, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "fall",
        )
    }

    // The strike schedule, pinned rather than switched off under reduce motion for
    // the same reason the field is, and more strongly: a strike is the *only* thing
    // in the frame that says the cell is convective, and a device asking for less
    // motion must not be told there is no storm. Note that freezing the *phase* is
    // not enough and is the trap here: the envelope spends four fifths of its cycle
    // dark, so a pinned phase deletes the hazard four times out of five. The draw
    // block holds the strikes at full opacity instead.
    val boltPhase: State<Float> = if (reduceMotion) {
        remember { mutableFloatStateOf(0f) }
    } else {
        rememberInfiniteTransition(label = "skyProfileStrikes").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = BoltCycleMillis, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "strike",
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .drawWithCache {
                if (size.minDimension <= 0f) return@drawWithCache onDrawBehind { }

                // --- everything below is the build phase: no drawing, only ---
                // --- measurement, dp conversion and path construction.     ---

                val groundHeight = GroundHeightDp.dp.toPx()
                val airTop = 0f
                val airBottom = size.height - groundHeight
                val airHeight = airBottom - airTop

                val labels: List<Pair<TextLayoutResult, Int>> = if (showAltitudeLabels) {
                    CeilingThresholds.map { (ft, _) ->
                        textMeasurer.measure(ft.axisLabel(), labelStyle) to ft
                    }
                } else {
                    emptyList()
                }
                // The numerals go only when they would eat the frame. Measured, not
                // guessed from the font scale: a translated label or a wide face
                // costs width the scale factor alone does not predict.
                val widestLabel = labels.maxOfOrNull { it.first.size.width.toFloat() } ?: 0f
                val labelsFit = widestLabel <= size.width * MaxLabelWidthFraction

                /** Fraction 0..1 above the ground to a Y in the air band. */
                fun yOf(fraction: Float): Float = airBottom - fraction.coerceIn(0f, 1f) * airHeight

                val hairline = HairlineDp.dp.toPx()
                val deckStroke = DeckEdgeDp.dp.toPx()
                val hatchStroke = HatchDp.dp.toPx()
                val shadeDepth = ShadeDp.dp.toPx()
                val hatch = hatchPath(size, HatchSpacingDp.dp.toPx())

                // The air, as four flat steps rather than as a wash. [AirSteps] has
                // the argument; what happens here is only that each step's own tone
                // is resolved, plus the warm lift below.
                //
                // **The warmth is folded into the steps rather than laid over them.**
                // Even a cold sky is warmer near the ground — that is the longer path
                // through the atmosphere, the same reason a low sun reddens — and it
                // is the one thing that took this scene off flat. Drawn as a gradient
                // it would put back exactly the wash the steps exist to remove, so it
                // arrives as a tint on each step's colour instead, strongest at the
                // bottom and effectively absent at the top. It follows the blend
                // rather than switching off at night, because the quantity it stands
                // for is continuous and there is no light down there to warm.
                val airStepColors = AirSteps.map { step ->
                    val air = lerp(band.low, band.high, step.mix)
                    val reach = (1f - step.mix) * (1f - step.mix)
                    lerp(air, colors.celestial.sunGlow, HorizonWarmth * (1f - nightWeight) * reach)
                }
                val fogTopY = yOf(fogFraction)
                val ceilingFt = (metar?.ceiling as? Ceiling.At)?.ft

                // The celestial layer, positioned in the build phase because none
                // of it changes between frames: the bodies are placed from the
                // observation instant, so they do not move at all.
                val bodyAlpha = if (airKnown) celestialAlpha(skyCover) else 0f
                val railGap = MinBodyGapDp.dp.toPx() / size.width
                // The disc plus its ring, so a body at a due-east or due-west
                // azimuth keeps its whole circle inside the frame — and plus the
                // ceiling wedge's depth on top of that, because the wedge occupies
                // the right edge of the scene and is drawn over the bodies. A clear
                // sky puts the wedge at the top of the axis, which is exactly where
                // a high moon at a westerly azimuth sits: the full moon over EHAM
                // came out with a green triangle through it.
                val railMargin =
                    (BodyRadiusDp.dp.toPx() + CeilingWedgeDp.dp.toPx() + hairline) / size.width
                val sunX = celestial?.sun?.let { railXInset(railX(it.azimuthDeg), railMargin) }
                val moonX = celestial?.moon?.let { body ->
                    val raw = railXInset(railX(body.azimuthDeg), railMargin)
                    if (sunX != null && celestial.sun.isUp) {
                        separatedMoonX(sunX, raw, railGap, railMargin, 1f - railMargin)
                    } else {
                        raw
                    }
                }

                // The precipitation field, likewise: the particle set is fixed and
                // only its phase advances.
                val precipForm = metar?.let { precipitationForm(it.presentWeather) }
                val precipFrom = if (precipForm == null || !airKnown) {
                    null
                } else {
                    precipitationSourceFraction(
                        skyCover = skyCover,
                        decks = decks,
                        deckFractions = fractions,
                        isConvective = metar.presentWeather.any {
                            it.isThunderstorm || it.descriptor == WeatherDescriptor.SHOWERS
                        },
                    )
                }
                val precipSlant = precipForm?.let {
                    fallDriftFraction(metar?.windDirectionDeg, metar?.windSpeedKt, it)
                } ?: 0f
                val precipCount = if (precipForm == null) {
                    0
                } else {
                    precipitationCount(precipitationIntensity(metar!!.presentWeather), precipForm)
                }

                // The deck silhouettes, built here rather than in the draw block:
                // a `Path` per run is real allocation and real construction work, and
                // none of it changes between frames.
                val deckShapes: List<DeckShape> = if (!airKnown) {
                    emptyList()
                } else {
                    decks.flatMapIndexed { index, deck ->
                        val baseY = yOf(fractions[index])
                        val thickness = thicknesses[index] * airHeight
                        val opacity = deckOpacity(deck.cover)
                        deckSpans(deck).mapIndexed { runIndex, span ->
                            val left = span.start * size.width
                            val right = span.end * size.width
                            val inset = (right - left) * baseInsetFor(deck.cover)
                            DeckShape(
                                path = cloudPath(left, right, baseY, thickness, deck.cover, deck.baseFt, runIndex),
                                topY = baseY - thickness,
                                baseY = baseY,
                                baseStart = Offset(left + inset, baseY),
                                baseEnd = Offset(right - inset, baseY),
                                opacity = opacity,
                                drift = driftFactor(fractions[index]),
                            )
                        }
                    }
                }
                // The strikes, built once because a `Path` per bolt is real
                // construction work and only the opacity changes between frames. Kept
                // apart from the runs, because the hazard belongs to the deck rather
                // than to any one lump of it — and, unlike the runs, a strike does not
                // drift: it marks the cell, and a marker that slid off the thing it
                // marks would be decoration.
                val bolts: List<Pair<Path, Strike>> = if (!airKnown) {
                    emptyList()
                } else {
                    decks.flatMapIndexed { index, deck ->
                        if (deck.convective == null) return@flatMapIndexed emptyList()
                        val baseY = yOf(fractions[index])
                        val gap = airBottom - baseY
                        val boltWidth = BoltWidthDp.dp.toPx()
                        ConvectiveStrikes.map { strike ->
                            boltPath(
                                left = size.width * strike.x - boltWidth / 2f,
                                top = baseY + gap * strike.drop,
                                width = boltWidth,
                                height = (gap * strike.reach).coerceAtLeast(MinBoltDp.dp.toPx()),
                            ) to strike
                        }
                    }
                }

                onDrawBehind {
                    // 1 · the air, in four flat steps. See [AirSteps].
                    if (airKnown) {
                        AirSteps.forEachIndexed { index, step ->
                            val top = yOf(step.topFraction)
                            drawRect(
                                color = airStepColors[index],
                                topLeft = Offset(0f, top),
                                size = Size(size.width, yOf(step.bottomFraction) - top),
                            )
                        }
                    } else {
                        drawRect(color = unknownAirColor, size = Size(size.width, airBottom))
                        clipRect(right = size.width, bottom = airBottom) {
                            drawPath(hatch, color = axisColor.copy(alpha = HatchAlpha), style = Stroke(hatchStroke))
                        }
                    }

                    // 2 · the decks: a flat base with a lumpy top, which is what a
                    // deck looks like from a field and also puts the silhouette's one
                    // straight edge on its one measured number. How lumpy *is* the
                    // density — see `deckLobes`.
                    //
                    // Full width now, with no gutter reserved for the numerals: the
                    // graticule rides *over* the scene rather than beside it, which is
                    // where the width the decks used to give up went. See step 8.
                    clipRect(left = 0f, right = size.width, bottom = airBottom) {
                        val period = size.width
                        val driftProgress = driftPhase.value
                        deckShapes.forEach { shape ->
                            val body = band.cloudBody.copy(alpha = shape.opacity)
                            val brush = Brush.verticalGradient(
                                // Only a slight lift toward the top, not a fade to
                                // nothing: the silhouette carries the form now, so the
                                // fill can stay a solid mass. Fading it out was what
                                // made the first version read as a lit panel.
                                0f to body.copy(alpha = shape.opacity * TopLift),
                                1f to body,
                                startY = shape.topY,
                                endY = shape.baseY,
                            )
                            // Along the wind's own component in the section plane,
                            // not a fixed left-to-right. A westerly pushes the
                            // decks east, an easterly west, and a northerly barely
                            // at all — because a wind along the section's line of
                            // sight has nothing to contribute to it. Wrapped into
                            // 0..period so the two drawn copies below cover the
                            // frame whichever way it is going.
                            val travel = driftProgress * driftDirection * shape.drift
                            val offset = ((travel % 1f) + 1f) % 1f * period
                            // Twice, one period apart: what leaves the right edge is
                            // already re-entering on the left, so the restart at the
                            // end of the cycle is invisible. The run pattern repeats
                            // with exactly this period, which is what makes the seam
                            // land on itself.
                            for (copy in intArrayOf(0, -1)) {
                                translate(left = offset + copy * period) {
                                    drawPath(path = shape.path, brush = brush)
                                    // A deck shades the air under it. Two dp of it,
                                    // which is what stops the decks reading as
                                    // stickers laid on a flat gradient.
                                    drawRect(
                                        brush = Brush.verticalGradient(
                                            0f to band.cloudEdge.copy(alpha = shape.opacity * ShadeAlpha),
                                            1f to Color.Transparent,
                                            startY = shape.baseY,
                                            endY = shape.baseY + shadeDepth,
                                        ),
                                        topLeft = Offset(shape.baseStart.x, shape.baseY),
                                        size = Size(shape.baseEnd.x - shape.baseStart.x, shadeDepth),
                                    )
                                    // The underside, inset on a sparse deck: a shorter
                                    // base line is what keeps FEW and SCT apart once
                                    // extent and opacity have run out at this size.
                                    drawLine(
                                        color = band.cloudEdge.copy(alpha = shape.opacity),
                                        start = shape.baseStart,
                                        end = shape.baseEnd,
                                        strokeWidth = deckStroke,
                                        cap = StrokeCap.Round,
                                    )
                                }
                            }
                        }
                    }

                    // 3 · the strikes.
                    //
                    // A convective deck gets lightning out of it — the one hazard in
                    // the sky that is not an altitude, so it is drawn as a mark rather
                    // than as a colour. Struck *down toward the field* rather than
                    // hung under the base, because the reach across the gap is what
                    // makes it read as a strike instead of as a decoration on a cloud.
                    //
                    // Outside the deck clip, so a bolt can cross the whole gap.
                    if (bolts.isNotEmpty()) {
                        val master = boltPhase.value
                        bolts.forEach { (path, strike) ->
                            val opacity = if (reduceMotion) {
                                1f
                            } else {
                                boltOpacity(master * strike.rate + strike.phase)
                            }
                            if (opacity > 0f) {
                                drawPath(
                                    path = path,
                                    color = band.convective.copy(alpha = opacity),
                                )
                                drawPath(
                                    path = path,
                                    color = band.convective.copy(alpha = opacity),
                                    style = Stroke(width = deckStroke, join = StrokeJoin.Round),
                                )
                            }
                        }
                    }

                    // 4a · the sun and the moon, on the horizon rail.
                    //
                    // Above the ruler and clear of it — see [railY] for why that
                    // placement is the whole argument, and the note above
                    // `drawSun` for the local datum that used to stand under each
                    // body and why it was removed.
                    //
                    // **Drawn over the decks, not under them**, and `bodyAlpha`
                    // is the only thing that says how much sky there is to see
                    // through. Painting underneath was tried first and is wrong
                    // twice over: the rail sits at 0.86 while a deck may reach
                    // 0.94, so a high-cloud report buries the bodies outright —
                    // KDEN's `SCT090 BKN130 BKN220` hid the afternoon sun
                    // completely — and, worse, it lets a cirrus wisp occlude the
                    // sun while a 700 ft overcast leaves it shining, which is the
                    // drawing lying in the exact shape this redesign removes.
                    // One channel, keyed to the lowest ceiling, says it honestly.
                    if (bodyAlpha > 0f && celestial != null) {
                        if (celestial.moon.isUp && moonX != null) {
                            drawMoon(
                                centre = Offset(moonX * size.width, yOf(railY(celestial.moon.elevationDeg))),
                                radius = BodyRadiusDp.dp.toPx(),
                                illuminated = celestial.moonIlluminatedFraction.toFloat(),
                                litLimbOnRight = celestial.moonLitLimbOnRight,
                                colors = colors,
                                band = band,
                                alpha = bodyAlpha,
                                stroke = hairline,
                            )
                        }
                        if (celestial.sun.isUp && sunX != null) {
                            drawSun(
                                centre = Offset(sunX * size.width, yOf(railY(celestial.sun.elevationDeg))),
                                radius = BodyRadiusDp.dp.toPx(),
                                colors = colors,
                                band = band,
                                alpha = bodyAlpha,
                                stroke = hairline,
                            )
                        }
                    }


                    // 5 · precipitation, falling out of the deck that produced it.
                    //
                    // Seamless without any integer-cycle constraint, which is the
                    // unusual part: `x` is derived from `y` rather than being a
                    // second translation, so at the end of a cycle every particle's
                    // y has wrapped back to its own start and its x follows. The
                    // usual fix — make the travel a whole number of tiles — cannot
                    // work here, because the horizontal travel is the frame height
                    // times the slant and that is not a whole number of widths for
                    // any angle the data produces. See `AnimatedWeatherGlyph`'s
                    // deleted fog bug, which is this family.
                    if (precipForm != null && precipFrom != null && precipCount > 0) {
                        val topY = yOf(precipFrom)
                        val fallHeight = airBottom - topY
                        if (fallHeight > 0f) {
                            clipRect(left = 0f, right = size.width, top = topY, bottom = airBottom) {
                                drawPrecipitation(
                                    count = precipCount,
                                    form = precipForm,
                                    progress = precipPhase.value,
                                    slant = precipSlant,
                                    left = 0f,
                                    top = topY,
                                    width = size.width,
                                    height = fallHeight,
                                    color = band.precipitation,
                                    markLength = PrecipMarkDp.dp.toPx(),
                                    stroke = PrecipStrokeDp.dp.toPx(),
                                )
                            }
                        }
                    }

                    // 6 · fog: an opaque slab with a hard top edge at the vertical
                    // visibility.
                    //
                    // **Drawn as the obscuration it is, rather than faded into the
                    // air.** The first version was a gradient from nothing at the fog
                    // top to 0.85 at the ground, on the reasoning that fog thickens
                    // downward — which is true and was still the wrong mark. It met
                    // the air at about 1.1:1, so the headline case, a half-mile
                    // `FG VV002` field, looked like a clear day with a slight wash at
                    // the bottom. Fog is not a tint on the air; it is a surface, with
                    // a top you can see from above and cannot see through.
                    //
                    // The hairline along that top is `cloudEdge` for the reason every
                    // other edge in this scene is: it is the one ink proved to clear
                    // 3:1 against both ends of every band, and the fog top has exactly
                    // that requirement — it is a deck's underside, upside down.
                    if (fogFraction > 0f) {
                        drawRect(
                            color = colors.ground.fog,
                            topLeft = Offset(0f, fogTopY),
                            size = Size(size.width, airBottom - fogTopY),
                        )
                        drawLine(
                            color = band.cloudEdge,
                            start = Offset(0f, fogTopY),
                            end = Offset(size.width, fogTopY),
                            strokeWidth = deckStroke,
                        )
                    }

                    // 7 · the ceiling marker: a solid wedge pointing in at the
                    // reported ceiling, in the category's own colour.
                    //
                    // On the right edge, away from the numerals, and a filled shape
                    // rather than a tick — the first version was a 10 dp stub in the
                    // gutter and read as an artefact rather than as a marker. This is
                    // the one number in the scene the whole flight category rests on,
                    // so it gets a mark that looks placed. Above the fog, because a
                    // `VV002` field has an indefinite ceiling and the marker is where
                    // that number is stated.
                    //
                    // **An unlimited ceiling gets the same wedge, at the top of the
                    // axis.** That is the real fix for a clear sky reading as an
                    // empty frame: `SkyCover.Clear` is an affirmative measurement,
                    // and before this it drew nothing at all — so it differed from
                    // `SkyCover.Unknown` only by the *absence* of hatch, which is
                    // absence of evidence one layer up from the bug that started
                    // this redesign. A wedge at the top says "the ceiling is above
                    // this frame", which is exactly true, and it generalises to a
                    // `SCT027`-only report where an unlimited ceiling arrives with
                    // decks already drawn.
                    val wedgeFraction = when (val ceiling = metar?.ceiling) {
                        is Ceiling.At -> altitudeToFraction(ceiling.ft)
                        Ceiling.Unlimited -> 1f
                        else -> null
                    }
                    if (wedgeFraction != null) {
                        // Kept whole inside the frame, and clear of the card's own
                        // corner radius at the top. An unlimited ceiling sits at
                        // fraction 1.0 and a 45,000 ft one at the same place, so an
                        // unclamped wedge loses its top half off the edge and reads
                        // as a stray notch — which is what it did on a device. Merely
                        // clamping it to its own depth was not enough: the scene runs
                        // edge to edge inside a `shapes.large` card, so a wedge in the
                        // top 16 dp is half eaten by the rounding, and the clear-sky
                        // case is exactly the one where it is the only mark in the
                        // upper half of the frame.
                        val depth = CeilingWedgeDp.dp.toPx()
                        val y = yOf(wedgeFraction).coerceIn(CornerClearanceDp.dp.toPx(), airBottom - depth)
                        val ink = metar?.flightRules?.let { rulesColors[it].onContainer } ?: axisColor
                        drawPath(
                            path = Path().apply {
                                moveTo(size.width, y - depth)
                                lineTo(size.width - depth, y)
                                lineTo(size.width, y + depth)
                                close()
                            },
                            color = ink,
                        )
                    }

                    // 8 · the ground: the surface, then the earth falling away under
                    // it, or hatch where the surface state is unreported.
                    //
                    // A gradient into the subsurface rather than two flat bands. Flat
                    // bands read as a plank laid across the bottom of the frame, which
                    // is what the first version looked like; a surface that darkens
                    // downward reads as ground the section was cut through.
                    drawRect(
                        brush = Brush.verticalGradient(
                            0f to colors.ground.subsurface,
                            1f to lerp(colors.ground.subsurface, Color.Black, SubsurfaceDepth),
                            startY = airBottom,
                            endY = size.height,
                        ),
                        topLeft = Offset(0f, airBottom),
                        size = Size(size.width, groundHeight),
                    )
                    if (groundColor != null) {
                        drawRect(
                            brush = Brush.verticalGradient(
                                0f to groundColor,
                                1f to groundColor.copy(alpha = 0f),
                                startY = airBottom,
                                endY = airBottom + groundHeight * SurfaceFraction,
                            ),
                            topLeft = Offset(0f, airBottom),
                            size = Size(size.width, groundHeight * SurfaceFraction),
                        )
                    } else {
                        clipRect(top = airBottom, right = size.width, bottom = size.height) {
                            drawPath(hatch, color = axisColor.copy(alpha = HatchAlpha), style = Stroke(hatchStroke))
                        }
                    }
                    drawLine(
                        color = axisColor.copy(alpha = SurfaceLineAlpha),
                        start = Offset(0f, airBottom),
                        end = Offset(size.width, airBottom),
                        strokeWidth = hairline,
                    )

                    // 9 · the graticule, last of all: the threshold hairlines and
                    // their numerals, over every layer of the scene.
                    //
                    // **Over, and that is the change.** These used to be drawn under
                    // the decks, on the reasoning that a ruler belongs behind the
                    // thing it measures. It fails in exactly the case the ruler is
                    // for: an overcast lid at 900 ft covers the 1,000 ft hairline, so
                    // the band structure disappears precisely when the deck's position
                    // relative to it is the whole content of the frame. On a printed
                    // chart the graticule's ink is a different system from the data's
                    // and rides above it, and that is what this is.
                    //
                    // A faint solid rule rather than a dashed one: the dashes competed
                    // with the decks for attention and read like a spreadsheet border.
                    val chipInk = lerp(band.low, band.high, AxisChipMix)
                    CeilingThresholds.forEach { (ft, rules) ->
                        val y = yOf(altitudeToFraction(ft))
                        drawLine(
                            color = rulesColors[rules].onContainer.copy(alpha = ThresholdAlpha),
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = hairline,
                        )
                    }
                    // The numerals sit on a chip of their own, which is what lets them
                    // ride over the scene at all: a numeral straight onto the air has
                    // to clear its bound against whichever step, deck or fog slab it
                    // happens to land on. See [AxisChipMix].
                    labels.takeIf { labelsFit }?.forEach { (layout, ft) ->
                        val centreY = yOf(altitudeToFraction(ft))
                        val padX = LabelChipPadXDp.dp.toPx()
                        val padY = LabelChipPadYDp.dp.toPx()
                        val left = LabelInsetDp.dp.toPx()
                        val width = layout.size.width + padX * 2f
                        val height = layout.size.height + padY * 2f
                        drawRoundRect(
                            color = chipInk,
                            topLeft = Offset(left, centreY - height / 2f),
                            size = Size(width, height),
                            cornerRadius = CornerRadius(LabelChipRadiusDp.dp.toPx()),
                        )
                        drawText(
                            textLayoutResult = layout,
                            color = band.cloudEdge,
                            topLeft = Offset(left + padX, centreY - layout.size.height / 2f),
                        )
                    }
                }
            },
    )
}

/**
 * One strike's outline, [BoltOutline] fitted to a box [width] by [height] with its
 * top left corner at ([left], [top]).
 *
 * **Stretched vertically rather than scaled uniformly**, which was tried first and
 * is wrong: the height is the gap between the cloud and the field, so a uniform
 * scale makes the bolt as wide as it is tall on a high CB. A 4,300 ft cumulonimbus
 * produced a maroon slab a fifth of the frame across, which reads as a mountain
 * rather than as lightning. A strike is a filament — its width says nothing about
 * the cloud it came out of, so it does not take the cloud's dimension.
 */
private fun boltPath(left: Float, top: Float, width: Float, height: Float): Path {
    val scaleX = width / BoltBoxWidth
    val scaleY = height / BoltBoxHeight
    return Path().apply {
        BoltOutline.forEachIndexed { index, (x, y) ->
            val px = left + x * scaleX
            val py = top + y * scaleY
            if (index == 0) moveTo(px, py) else lineTo(px, py)
        }
        close()
    }
}

/** One drawn run of a deck: its silhouette, and the underside line that anchors it. */
private class DeckShape(
    val path: Path,
    val topY: Float,
    val baseY: Float,
    val baseStart: Offset,
    val baseEnd: Offset,
    val opacity: Float,
    /** How much of the frame this deck crosses per drift cycle. See `driftFactor`. */
    val drift: Float,
)

/**
 * The silhouette of one run of cloud: flat along the bottom, lobed across the top.
 *
 * Built from cubic segments rather than circles so the lobes meet in smooth valleys
 * instead of cusps — a row of tangent circles reads as a chain of bubbles, which is
 * a different thing from a cloud.
 *
 * The flat base is not a simplification. A deck's base genuinely is flat, because it
 * is the condensation level — the altitude at which rising air reaches saturation,
 * which is the same everywhere over a field. It is also the only altitude the report
 * gives, so the one straight edge in the shape sits on the one measured number.
 *
 * Density arrives entirely through [deckLobes], [deckLobeAmplitude] and
 * [deckShoulder]: two tall puffs almost parting company for FEW, six shallow ripples
 * on a continuous mass for OVC.
 */
private fun cloudPath(
    left: Float,
    right: Float,
    baseY: Float,
    thickness: Float,
    cover: CloudCover,
    baseFt: Int,
    runIndex: Int,
): Path {
    val heights = lobeHeights(cover, baseFt, runIndex)
    // The valleys, measured against the crown rather than the frame — see deckShoulder.
    val shoulderY = baseY - thickness * deckLobeAmplitude(cover) * deckShoulder(cover)
    val width = right - left
    val lobeWidth = width / heights.size

    return Path().apply {
        moveTo(left, baseY)
        lineTo(left, shoulderY)
        heights.forEachIndexed { index, height ->
            val x0 = left + index * lobeWidth
            val x1 = x0 + lobeWidth
            val peakY = baseY - thickness * height
            // Control points pulled inward from the lobe's edges, which is what
            // rounds the crown instead of peaking it.
            cubicTo(
                x0 + lobeWidth * LobeControl, peakY,
                x1 - lobeWidth * LobeControl, peakY,
                x1, shoulderY,
            )
        }
        lineTo(right, baseY)
        close()
    }
}

/**
 * Diagonal hatch across [size], as one path built once.
 *
 * Diagonal rather than a stipple or a grey wash: on a chart, hatching means *no
 * information here*, and it is the one fill that cannot be mistaken for weather.
 * A grey wash reads as overcast; a stipple reads as precipitation.
 */
private fun hatchPath(size: Size, spacing: Float): Path {
    val path = Path()
    val extent = size.width + size.height
    var x = -size.height
    while (x < extent) {
        path.moveTo(x, size.height)
        path.lineTo(x + size.height, 0f)
        x += spacing
    }
    return path
}

/** `3,000` — grouped, in a fixed locale, because it is a chart figure. */
private fun Int.axisLabel(): String = String.format(java.util.Locale.ROOT, "%,d", this)

/**
 * The two sizes the scene ships at.
 *
 * [AirportDetail] is the full hero. [RouteDetail] is shorter because two of them
 * stack there, one per end of the route, and both have to be comparable without a
 * scroll — the whole point of showing both is that no tap is needed.
 */
object SkyProfileHeight {
    val AirportDetail: Dp = 220.dp
    val RouteDetail: Dp = 168.dp
}



private const val GroundHeightDp = 16f
private const val ShadeDp = 3f
private const val HairlineDp = 1f
private const val DeckEdgeDp = 1.5f
private const val HatchDp = 1f
private const val HatchSpacingDp = 7f
private const val CeilingWedgeDp = 6f

/**
 * How far below the top of the scene a mark has to sit to clear the card's corner.
 *
 * `shapes.large` plus the wedge's own half height. The scene deliberately runs edge
 * to edge inside its card — see `MetarPanel` — which buys the horizon meeting the
 * rounded corners and costs exactly this.
 */
private const val CornerClearanceDp = 22f

/** How far in from the left edge a numeral's chip sits, and how it is padded. */
private const val LabelInsetDp = 8f
private const val LabelChipPadXDp = 4f
private const val LabelChipPadYDp = 1f
private const val LabelChipRadiusDp = 4f

/** Alpha on a threshold hairline: present, but never competing with a deck. */
private const val ThresholdAlpha = 0.34f

/** How dark a deck shades the air just beneath it. */
private const val ShadeAlpha = 0.30f

/**
 * How far the lowest step of air is tinted toward [SkyColors.celestial]'s glow.
 *
 * Small, and it is the whole of what stops four flat greys reading as a chart of
 * something rather than as air. See the build phase for why it is a tint on the
 * steps rather than a gradient over them.
 */
private const val HorizonWarmth = 0.16f

/** How far the subsurface darkens toward black over the ground band. */
private const val SubsurfaceDepth = 0.35f
private const val HatchAlpha = 0.30f
private const val SurfaceLineAlpha = 0.45f

/** How much of the ground band is the surface state rather than the earth under it. */
private const val SurfaceFraction = 0.45f

/** The numerals go if the widest of them would take more than this of the width. */
private const val MaxLabelWidthFraction = 0.18f

/** How far the control points are pulled in from a lobe's edges. */
private const val LobeControl = 0.18f

/** How much of its opacity a deck keeps at the top of the silhouette. */
private const val TopLift = 0.82f

/**
 * A strike's width, and the shortest one that still reads as a strike.
 *
 * The width is fixed in dp rather than taken from the height — see [boltPath].
 */
private const val BoltWidthDp = 10f
private const val MinBoltDp = 26f

/*
 * A body used to carry a short datum line at the rail's base with a stem up to it,
 * on the argument that a disc sitting above a mark drawn directly beneath it is
 * manifestly measured against *that* mark rather than against the altitude ruler
 * elsewhere in the frame — an elevation is an angle, not a height in feet, and the
 * one thing this layer must never invite is reading it off the axis.
 *
 * **It was removed because it does not survive the shipped height.** The rail spans
 * fractions 0.86 to 0.935, which on the 168 dp route-detail scene is about 11 dp
 * top to bottom, against a 7 dp disc radius. So the datum is never more than a few
 * dp below the disc's own edge and at low elevation is behind it: what it actually
 * draws is a short line stuck under the sun, which reads as an artefact or a
 * shadow — it was reported as "a weird line under the sun" — and not as a baseline.
 * Apparatus that is not legible as apparatus is just a mark.
 *
 * The rail's real defence was never the datum: it is that the rail sits *where no
 * ruler is drawn*. `CeilingThresholds` tops out at 3,000 ft = fraction 0.56, so
 * every hairline and numeral in the scene is a third of the frame below the lowest
 * a body can sit, and `HorizonRailTest` asserts exactly that rather than assuming
 * it. Anything reinstating a local datum needs a taller rail first, which moves
 * every body and is a change to make deliberately rather than as a side effect.
 */

/**
 * The sun: a disc, a short halo, and a ring.
 *
 * **The ring is a legibility fix, not an ornament**, and it came out of arithmetic
 * rather than out of looking. Brand light's sun (#E6BE7A) clears only **1.58:1**
 * against its own day band's upper air, and Chart's navy sun only **2.56:1**
 * against its twilight air — and the rail sits at 0.86, which is exactly where the
 * air is nearest [SkyBand.high]. Drawing the outline in [SkyBand.cloudEdge] moves
 * the legibility requirement off the fill and onto a stroke that
 * `SkyColorsContrastTest` already proves clears 3:1 against both ends of every
 * band. One rule fixes both palettes, no colour is retuned, and Chart's
 * deliberate navy sun is not quietly diluted to pass a bound.
 */
private fun DrawScope.drawSun(
    centre: Offset,
    radius: Float,
    colors: SkyColors,
    band: SkyBand,
    alpha: Float,
    stroke: Float,
) {
    drawCircle(
        brush = Brush.radialGradient(
            0f to colors.celestial.sunGlow.copy(alpha = alpha * SunGlowAlpha),
            1f to Color.Transparent,
            center = centre,
            radius = radius * SunGlowRadius,
        ),
        radius = radius * SunGlowRadius,
        center = centre,
    )
    drawCircle(color = colors.celestial.sun.copy(alpha = alpha), radius = radius, center = centre)
    drawCircle(
        color = band.cloudEdge.copy(alpha = alpha),
        radius = radius,
        center = centre,
        style = Stroke(width = stroke),
    )
}

/**
 * The moon: a disc with a vertical terminator, and the same ring the sun gets.
 *
 * The terminator is an ellipse across an upright disc, its half-width `(1 - 2k)r`
 * — so a full moon has none, a half moon has a straight one, and a crescent has
 * one bowed most of the way across. Keying the shape off *k* rather than off the
 * waxing flag matters at exactly one moment: `waxing` flips at full and at new,
 * and a renderer driven by the flag would snap at the one instant nothing should
 * move.
 *
 * **Which limb is lit follows the observer's hemisphere** — a waxing moon is lit
 * on the right in Amsterdam and on the left in Santiago. The disc is deliberately
 * *not* tilted, because a schematic cross-section has no sky orientation to tilt
 * against; but the lit side is a fact rather than a tilt, and ignoring it is wrong
 * for half the world.
 *
 * Below [MoonSliverFraction] the lit sliver is sub-pixel at this radius, so the
 * disc is drawn dark with only its ring — which is what a new moon is.
 */
private fun DrawScope.drawMoon(
    centre: Offset,
    radius: Float,
    illuminated: Float,
    litLimbOnRight: Boolean,
    colors: SkyColors,
    band: SkyBand,
    alpha: Float,
    stroke: Float,
) {
    val ink = colors.celestial
    drawCircle(color = ink.moonDark.copy(alpha = alpha), radius = radius, center = centre)

    val lit = illuminated.coerceIn(0f, 1f)
    if (lit > MoonSliverFraction) {
        clipRect(
            left = if (litLimbOnRight) centre.x else centre.x - radius,
            right = if (litLimbOnRight) centre.x + radius else centre.x,
            top = centre.y - radius,
            bottom = centre.y + radius,
        ) {
            drawCircle(color = ink.moonLit.copy(alpha = alpha), radius = radius, center = centre)
        }
        // The terminator, bowed back across the lit half for a crescent and
        // forward over the dark half for a gibbous. `(1 - 2k)` changes sign at
        // half, which is what lets one expression serve both.
        val bow = abs(1f - 2f * lit) * radius
        drawOval(
            color = if (lit > 0.5f) ink.moonLit.copy(alpha = alpha) else ink.moonDark.copy(alpha = alpha),
            topLeft = Offset(centre.x - bow, centre.y - radius),
            size = Size(bow * 2f, radius * 2f),
        )
    }
    drawCircle(
        color = band.cloudEdge.copy(alpha = alpha),
        radius = radius,
        center = centre,
        style = Stroke(width = stroke),
    )
}

/**
 * The precipitation field.
 *
 * Liquid falls as streaks and frozen as dots, which is the channel this scene
 * already trusts first — the deck silhouettes carry density before opacity or
 * extent do — and a streak and a dot are unmistakable at 168 dp where two inks at
 * the same luminance are not. It also means one ink per band instead of two, which
 * matters because every ink drawn against the air must clear 3:1 against three
 * different airs.
 *
 * See the call site on why the phase needs no integer-cycle constraint.
 */
private fun DrawScope.drawPrecipitation(
    count: Int,
    form: PrecipitationForm,
    progress: Float,
    slant: Float,
    left: Float,
    top: Float,
    width: Float,
    height: Float,
    color: Color,
    markLength: Float,
    stroke: Float,
) {
    val hailScale = if (form == PrecipitationForm.HAIL) HailMarkScale else 1f
    for (index in 0 until count) {
        val particle = precipitationParticle(index, form.ordinal)
        // y wraps, and x is a function of y — so at the end of a cycle every
        // particle is back where it started and the point set is invariant. Each
        // drop advances at its own speed, which does not disturb that: the wrap is
        // per particle, not shared.
        val y = ((particle.y + progress * particle.speed) % 1f) * height
        val x = (((particle.x + (y / width) * slant) % 1f + 1f) % 1f) * width
        val cx = left + x
        val cy = top + y
        val ink = color.copy(alpha = particle.opacity * PrecipAlpha)
        if (form.frozen) {
            drawCircle(
                color = ink,
                radius = stroke * hailScale * particle.gauge,
                center = Offset(cx, cy),
            )
        } else {
            val length = markLength * hailScale * precipitationMarkScale(particle.speed)
            val head = Offset(cx + slant * length * 0.5f, cy + length * 0.5f)
            val tail = Offset(cx - slant * length * 0.5f, cy - length * 0.5f)
            // Faded at the tail, solid at the head. A streak is what the eye keeps
            // of a drop that has already moved on, so it is brightest where the drop
            // is now and thins out behind it — and a field of even-weight strokes is
            // precisely what made this read as pen hatching.
            drawLine(
                brush = Brush.linearGradient(
                    0f to Color.Transparent,
                    1f to ink,
                    start = tail,
                    end = head,
                ),
                start = tail,
                end = head,
                strokeWidth = stroke * particle.gauge,
                cap = StrokeCap.Round,
            )
        }
    }
}

/** The disc radius, and how far two bodies must stay apart. */
private const val BodyRadiusDp = 7f
private const val MinBodyGapDp = 17f
private const val SunGlowRadius = 2.4f
private const val SunGlowAlpha = 0.30f

/** Below this the lit sliver is sub-pixel at [BodyRadiusDp], which is a new moon. */
internal const val MoonSliverFraction = 0.04f

private const val PrecipMarkDp = 7f
private const val PrecipStrokeDp = 1.2f
private const val PrecipAlpha = 0.55f
private const val HailMarkScale = 1.8f

/**
 * How long the whole precipitation field takes to repeat.
 *
 * The **master** cycle, not one drop's fall: a drop crosses the frame [PrecipSpeeds]
 * times inside it, so a median drop at [MedianPrecipSpeed] falls in 2.4 s, a slow one
 * in 3.6 s and a fast one in 1.8 s. Set as a whole multiple of that median fall for
 * the reason [PrecipSpeeds] gives.
 *
 * Much faster than the deck drift, and that is the point: a deck crossing the frame
 * in a minute and a half is ambient, while rain has to read as falling.
 */
private const val PrecipPeriodMillis = 2_400 * MedianPrecipSpeed
