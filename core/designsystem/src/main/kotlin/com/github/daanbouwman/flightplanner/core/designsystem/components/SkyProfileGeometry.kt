package com.github.daanbouwman.flightplanner.core.designsystem.components

import com.github.daanbouwman.flightplanner.model.CloudCover
import com.github.daanbouwman.flightplanner.model.CloudLayer
import com.github.daanbouwman.flightplanner.model.ConvectiveCloud
import com.github.daanbouwman.flightplanner.model.FlightRules
import com.github.daanbouwman.flightplanner.model.PhenomenonKind
import com.github.daanbouwman.flightplanner.model.PresentWeather
import com.github.daanbouwman.flightplanner.model.SkyCover
import com.github.daanbouwman.flightplanner.model.WeatherIntensity
import com.github.daanbouwman.flightplanner.model.WeatherPhenomenon
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.tan

/**
 * The geometry of the sky profile, as pure functions.
 *
 * Everything testable about the scene lives here rather than inside the draw
 * scope, because `:core:designsystem` has JVM unit tests only — no instrumentation
 * and no screenshot harness. A function in this file can be asserted; the same
 * arithmetic inlined into `drawWithCache` could only be looked at.
 *
 * All fractions returned here are **0 at the ground and 1 at the top of the
 * axis**, which is the opposite of Compose's Y direction. The single conversion
 * to pixels happens in [SkyProfile], so nothing in this file knows which way up
 * the screen is.
 */

/**
 * The altitude axis, as breakpoints.
 *
 * **This is the signature element of the design and it is deliberately not
 * linear.** A linear 0–45,000 ft axis would put every altitude that decides a
 * flight category inside the bottom 7 % of the frame: a 900 ft ceiling and a
 * 2,900 ft ceiling are the difference between IFR and MVFR, and on a linear scale
 * they are two hairlines 4 dp apart. So the axis is piecewise-linear with its
 * breakpoints *at* the flight-rules thresholds, spending 56 % of its height on the
 * first 3,000 ft and the remaining 44 % on the next 42,000.
 *
 * The ruler is therefore weighted by what matters to a pilot rather than by
 * physical distance — structure carrying information. The cost is that the upper
 * air is compressed, which is the right trade: nobody reads a cirrus deck's base
 * to two significant figures, and everybody reads a ceiling.
 */
internal val AxisBreakpoints: List<Pair<Int, Float>> = listOf(
    0 to 0.00f,
    500 to 0.20f,
    1_000 to 0.36f,
    3_000 to 0.56f,
    12_000 to 0.78f,
    45_000 to 1.00f,
)

/** The top of the axis. Anything above this is drawn at the top, not off it. */
internal const val AxisTopFt: Int = 45_000

/**
 * The three ceiling thresholds, each with the category that begins *below* it.
 *
 * Drawn as hairlines in the flight-rules colours — the only place those colours
 * appear in the scene, because they belong to the measuring apparatus rather than
 * to the weather. A deck sitting below the 1,000 ft line is visibly IFR without
 * anything having to say so, which is the property that makes the sun-on-an-IFR-day
 * bug unrepresentable here.
 */
internal val CeilingThresholds: List<Pair<Int, FlightRules>> = listOf(
    500 to FlightRules.LIFR,
    1_000 to FlightRules.IFR,
    3_000 to FlightRules.MVFR,
)

/**
 * Where [ft] sits on the axis, as a fraction from 0 (ground) to 1 (top).
 *
 * Monotonic and continuous. Negative altitudes clamp to 0 — a below-sea-level
 * field reports a cloud base in feet AGL, so a negative value here would be a
 * data error rather than a real basement, and drawing it below the ground would
 * make the scene lie about which side of the surface the cloud is on.
 */
internal fun altitudeToFraction(ft: Int): Float {
    if (ft <= 0) return 0f
    if (ft >= AxisTopFt) return 1f
    for (i in 0 until AxisBreakpoints.size - 1) {
        val (lowFt, lowFraction) = AxisBreakpoints[i]
        val (highFt, highFraction) = AxisBreakpoints[i + 1]
        if (ft <= highFt) {
            val t = (ft - lowFt).toFloat() / (highFt - lowFt).toFloat()
            return lowFraction + t * (highFraction - lowFraction)
        }
    }
    return 1f
}

/**
 * One drawn cloud deck, which may stand for more than one reported layer.
 *
 * [mergedCount] is 1 for a deck that is exactly what the station reported. Above
 * that, [baseFt] and [cover] are still real values taken from the reported layers
 * — see [mergeDecks] for which ones and why.
 */
internal data class CloudDeck(
    val cover: CloudCover,
    val baseFt: Int,
    val convective: ConvectiveCloud?,
    val mergedCount: Int,
)

/**
 * Collapses reported layers that would overlap on the axis into single decks.
 *
 * A METAR can carry more layers than a 200 dp frame has room for — KSYR sent BKN
 * at 3,200 / 6,000 / 12,000 / 20,000 ft on the day these fixtures were captured,
 * and the upper two land 0.03 apart once the axis compresses the high air. Drawn
 * literally they are one thick smear that reads as a single deck anyway, only
 * blurrier.
 *
 * **Two layers merge only when they agree about whether they are a ceiling.** That
 * restriction is the whole correctness argument: [CloudCover.isCeiling] is what
 * [SkyCover.ceiling] derives the ceiling from, so merging a FEW into a nearby BKN
 * would move the ceiling — downward, in the safe direction, but still to an
 * altitude the station never reported. Keeping the classes apart means the merged
 * deck list has exactly the same ceiling as the layer list it came from, which
 * [SkyProfileGeometryTest] asserts over every fixture.
 *
 * Within a class the merged deck takes the **lowest base** and the **densest
 * cover** of its members, so a merge can never make the sky look emptier or
 * higher than it is.
 */
internal fun mergeDecks(layers: List<CloudLayer>): List<CloudDeck> {
    if (layers.isEmpty()) return emptyList()
    val sorted = layers.sortedBy { it.baseFt }
    val decks = mutableListOf<MutableList<CloudLayer>>()
    for (layer in sorted) {
        val group = decks.lastOrNull()
        val previous = group?.last()
        val mergeable = previous != null &&
            previous.cover.isCeiling == layer.cover.isCeiling &&
            altitudeToFraction(layer.baseFt) - altitudeToFraction(previous.baseFt) < MinDeckSeparation
        if (mergeable) group.add(layer) else decks.add(mutableListOf(layer))
    }
    return decks.map { group ->
        CloudDeck(
            cover = group.maxBy { it.cover.nominalOctas }.cover,
            baseFt = group.first().baseFt,
            convective = group.firstNotNullOfOrNull { it.convective },
            mergedCount = group.size,
        )
    }
}

/**
 * The drawing fraction for each deck, pushed apart so adjacent decks stay
 * separate lines.
 *
 * [mergeDecks] cannot close every gap, because it refuses to merge across the
 * ceiling boundary — a FEW at 2,900 ft and a BKN at 3,000 ft must both be drawn,
 * and they land 0.01 apart. This nudges them to [MinDeckSeparation] without
 * reordering them.
 *
 * **The nudge is a drawing adjustment and nothing else reads it.** The deck's
 * [CloudDeck.baseFt] is untouched, so the ceiling marker and every altitude label
 * still show the reported figure. A caller that used these fractions to answer a
 * question about altitude would be wrong; nothing does.
 */
internal fun deckFractions(decks: List<CloudDeck>): List<Float> {
    if (decks.isEmpty()) return emptyList()
    val fractions = decks.map { altitudeToFraction(it.baseFt) }.toMutableList()
    for (i in 1 until fractions.size) {
        val minimum = fractions[i - 1] + MinDeckSeparation
        if (fractions[i] < minimum) fractions[i] = minimum
    }
    // Pushing upward can run the top deck off the axis; pull the whole stack back
    // down by the overflow rather than clipping it, which would silently drop a
    // reported layer.
    val overflow = fractions.last() - MaxDeckFraction
    if (overflow > 0f) {
        val headroom = fractions.first()
        val shift = minOf(overflow, headroom)
        for (i in fractions.indices) fractions[i] -= shift
    }
    return fractions
}

/** A horizontal run of cloud, in fractions of the frame's width. */
internal data class DeckSpan(val start: Float, val end: Float) {
    val width: Float get() = end - start
}

/**
 * Where along the width a deck is drawn, from its cover.
 *
 * This is how FEW / SCT / BKN / OVC read as densities rather than as four labels:
 * the deck occupies the fraction of the width its octas describe, broken into
 * separate runs so that a scattered deck looks scattered. An overcast deck is one
 * unbroken span, which is what overcast means.
 *
 * **Deterministic, from the deck's own base altitude.** The runs must not move
 * between recompositions — a deck that reshuffles when an unrelated part of the
 * screen changes would be motion carrying no meaning, which the app's own rule
 * forbids. Two different decks in the same report still get different runs,
 * because the seed comes from their bases.
 */
internal fun deckSpans(deck: CloudDeck): List<DeckSpan> {
    val coverage = when (deck.cover) {
        CloudCover.FEW -> 0.22f
        CloudCover.SCATTERED -> 0.46f
        CloudCover.BROKEN -> 0.74f
        CloudCover.OVERCAST -> 1.00f
    }
    if (deck.cover == CloudCover.OVERCAST) return listOf(DeckSpan(0f, 1f))

    val runs = when (deck.cover) {
        CloudCover.FEW -> 2
        CloudCover.SCATTERED -> 3
        else -> 3
    }
    // Stratified: one run per equal cell, so the deck spreads across the frame
    // instead of clustering wherever the sequence happens to start.
    val cell = 1f / runs
    val runWidth = coverage / runs
    var seed = deck.baseFt * 2_654_435_761L + deck.cover.ordinal * 40_503L
    return (0 until runs).map { index ->
        seed = seed * 6_364_136_223_846_793_005L + 1_442_695_040_888_963_407L
        val jitter = ((seed ushr 33).toFloat() / (1L shl 31).toFloat()).coerceIn(0f, 1f)
        val slack = cell - runWidth
        val start = index * cell + jitter * slack
        DeckSpan(start, start + runWidth)
    }
}

/**
 * How thick a deck is drawn, as a fraction of the frame's height.
 *
 * A METAR reports a base and nothing else — there is no layer top in the data — so
 * this is a drawing convention, not a measurement, and it is kept deliberately
 * thin. The deck's crisp edge is its *underside*, which is the altitude the report
 * actually gives; the top fades out, so that nothing about the drawing invites the
 * eye to read a height off it.
 *
 * Denser covers are drawn slightly thicker, because an overcast deck genuinely is
 * a body of cloud while a FEW is a scatter of small ones.
 */
internal fun deckThicknessFraction(cover: CloudCover): Float = when (cover) {
    CloudCover.FEW -> 0.055f
    CloudCover.SCATTERED -> 0.065f
    CloudCover.BROKEN -> 0.078f
    CloudCover.OVERCAST -> 0.090f
}

/**
 * The opacity a deck's body is drawn at, from its octas.
 *
 * Cover and opacity are the same fact told twice — once as horizontal extent by
 * [deckSpans], once as density here — which is deliberate redundancy: at 160 dp a
 * FEW and a SCT deck differ by a couple of dp of run length, and the second
 * channel is what keeps them apart.
 */
internal fun deckOpacity(cover: CloudCover): Float =
    0.45f + 0.55f * (cover.nominalOctas.toFloat() / CloudCover.OVERCAST.nominalOctas.toFloat())

/**
 * How high the fog lies, as a fraction of the frame, or 0 when there is none.
 *
 * Two quite different things can put fog in the scene and they are not
 * interchangeable:
 *
 * - An **obscured sky** has a measured vertical visibility. That is a real
 *   altitude and goes through [altitudeToFraction] like any other, so a `VV002`
 *   field draws its fog top at the same height a 200 ft ceiling would sit — which
 *   is the honest reading, since that is exactly what an indefinite ceiling is.
 * - **Fog or mist in the present weather** with a clear or layered sky above it is
 *   shallow ground fog. There is no measured depth, so the depth is inferred from
 *   the reported visibility and capped at [MaxInferredFogFraction]. Inferred, and
 *   marked as such by the cap: the scene never draws inferred fog as tall as
 *   measured fog can be.
 *
 * Returns 0 for an unknown sky. Unknown must not draw weather of any kind.
 */
internal fun fogHeightFraction(
    skyCover: SkyCover,
    presentWeather: List<PresentWeather>,
    visibilityStatuteMiles: Double?,
): Float {
    if (skyCover is SkyCover.Obscured) {
        val depth = skyCover.verticalVisibilityFt ?: return MaxInferredFogFraction
        return altitudeToFraction(depth)
    }
    if (skyCover is SkyCover.Unknown) return 0f
    if (presentWeather.none { it.isFogOrMist }) return 0f
    val visibility = visibilityStatuteMiles ?: return MaxInferredFogFraction * 0.5f
    // Thicker fog at lower visibility, flattening out above 3 SM where "mist" stops
    // meaning much about depth.
    val thickness = ((3.0 - visibility) / 3.0).coerceIn(0.0, 1.0).toFloat()
    return MaxInferredFogFraction * thickness
}

/**
 * Minimum gap between two drawn decks, as a fraction of the frame's height.
 *
 * Set from the smallest gap that still reads as two decks rather than one thick
 * one at the shortest size the scene ships at (160 dp on route detail): 0.06 is
 * about 10 dp there, which clears a deck's own drawn thickness.
 */
internal const val MinDeckSeparation: Float = 0.06f

/** The highest a deck may be drawn, leaving the top of the axis for the label. */
internal const val MaxDeckFraction: Float = 0.94f

/** The ceiling on *inferred* fog depth. See [fogHeightFraction]. */
internal const val MaxInferredFogFraction: Float = 0.14f

/**
 * How far in from each end a deck's underside line is drawn, as a fraction of the
 * run.
 *
 * A sparse deck gets a shorter base than its body, so that FEW reads as wisps with
 * soft edges and OVC reads as a slab with a hard bottom. Extent and opacity already
 * say the same thing twice; at 168 dp they need a third channel, because two runs
 * that differ by 4 dp of length do not.
 */
internal fun baseInsetFor(cover: CloudCover): Float = when (cover) {
    CloudCover.FEW -> 0.22f
    CloudCover.SCATTERED -> 0.12f
    CloudCover.BROKEN -> 0.04f
    CloudCover.OVERCAST -> 0f
}

/**
 * Each deck's drawn thickness, clamped so a deck cannot swallow the one above it.
 *
 * [deckThicknessFraction] gives what a deck wants in isolation, and for an OVC that
 * is 0.075 — larger than [MinDeckSeparation]. A deck's body grows *upward* from its
 * base, so an unclamped overcast deck 0.06 below the next one paints straight over
 * that deck's underside, and the two arrive as a single thick smear. Seen on a
 * device: KHPN's BKN013 over OVC019 came out as one line.
 *
 * So a deck gets the lesser of what it wants and [MaxThicknessOfGap] of the room it
 * actually has. The topmost deck has the rest of the axis and is never clamped.
 */
internal fun deckThicknesses(decks: List<CloudDeck>, fractions: List<Float>): List<Float> =
    decks.indices.map { i ->
        val nominal = deckThicknessFraction(decks[i].cover)
        if (i + 1 >= fractions.size) return@map nominal
        val room = (fractions[i + 1] - fractions[i]) * MaxThicknessOfGap
        minOf(nominal, room)
    }

/** How much of the gap to the next deck a deck's body may fill. See [deckThicknesses]. */
internal const val MaxThicknessOfGap: Float = 0.65f

/**
 * How many lobes a deck's top is built from, per run.
 *
 * A deck is drawn as a **flat base with a lumpy top**, which is not stylisation —
 * it is what a deck looks like from underneath, and the flat base is the one
 * altitude the report actually gives. So the silhouette puts its only straight
 * edge on the only measured number.
 *
 * The lobe count, [deckLobeAmplitude] and [deckShoulder] together are what makes
 * density a *shape* rather than a label: a couple of tall separate puffs for FEW, a
 * long slab with slight texture for OVC. Extent and opacity say the same thing, but
 * silhouette is the channel that reads first and at any size.
 */
internal fun deckLobes(cover: CloudCover): Int = when (cover) {
    CloudCover.FEW -> 2
    CloudCover.SCATTERED -> 3
    CloudCover.BROKEN -> 4
    CloudCover.OVERCAST -> 6
}

/** How much of a deck's thickness its tallest lobe reaches. See [deckLobes]. */
internal fun deckLobeAmplitude(cover: CloudCover): Float = when (cover) {
    CloudCover.FEW -> 1.00f
    CloudCover.SCATTERED -> 0.92f
    CloudCover.BROKEN -> 0.86f
    CloudCover.OVERCAST -> 0.80f
}

/**
 * Where the valleys between lobes sit, as a fraction of **the lobe peaks**.
 *
 * A fraction of the peaks rather than of the thickness, and that is not a detail:
 * expressed against thickness, OVC's shoulder (0.55) exceeded its own lobe amplitude
 * (0.42), so its valleys sat *above* its peaks and the whole top inverted into a
 * faint ripple. Against the peaks the relation cannot come apart — a valley is
 * always some fraction of the crown above it.
 *
 * This is the parameter that turns puffs into a deck. Near 0.1 the lobes almost part
 * company and read as separate clouds with sky between them, which is what FEW
 * means; near 0.8 they are ripples on a continuous mass, which is what OVC means.
 */
internal fun deckShoulder(cover: CloudCover): Float = when (cover) {
    CloudCover.FEW -> 0.12f
    CloudCover.SCATTERED -> 0.32f
    CloudCover.BROKEN -> 0.58f
    CloudCover.OVERCAST -> 0.80f
}

/**
 * The height of lobe [index] of [lobes], as a fraction of a deck's thickness.
 *
 * Deterministic and seeded from the deck's own base, for the reason [deckSpans]
 * gives: a silhouette that reshuffles when an unrelated part of the screen
 * recomposes would be motion carrying no meaning. Varied, because lobes of
 * identical height read as a machined edge rather than as cloud.
 */
internal fun lobeHeights(cover: CloudCover, baseFt: Int, runIndex: Int): List<Float> {
    val lobes = deckLobes(cover)
    val amplitude = deckLobeAmplitude(cover)
    var seed = baseFt * 2_654_435_761L + runIndex * 97_003L + cover.ordinal * 7_919L
    return (0 until lobes).map {
        seed = seed * 6_364_136_223_846_793_005L + 1_442_695_040_888_963_407L
        val unit = ((seed ushr 33).toFloat() / (1L shl 31).toFloat()).coerceIn(0f, 1f)
        // Never below 60 % of the tallest: a lobe much shorter than its neighbours
        // reads as a gap in the deck rather than as its texture.
        amplitude * (0.60f + 0.40f * unit)
    }
}

/**
 * How long a deck takes to drift one frame width, in milliseconds.
 *
 * **The drift is the wind, which is why it exists at all.** The app's rule is that
 * motion explains rather than performs, and a sky that drifted at a fixed rate
 * would be performing — it would look alive while saying nothing. Tied to the
 * reported speed it becomes the one thing a still cross-section cannot show: a calm
 * field is visibly still, a gusty one visibly moving.
 *
 * Slow on purpose. Even a 40 kt gale takes [MinDriftPeriodMillis] to cross the
 * frame, because the drift has to be noticeable when watched and invisible when
 * not; anything faster turns a weather panel into a screensaver.
 */
internal fun driftPeriodMillis(windSpeedKt: Int): Int {
    if (windSpeedKt <= 0) return 0
    val scaled = ReferenceDriftPeriodMillis * ReferenceWindKt / windSpeedKt
    return scaled.coerceIn(MinDriftPeriodMillis, MaxDriftPeriodMillis)
}

/**
 * How much faster a deck at [fraction] drifts than one on the ground.
 *
 * Higher decks move more, and that is physics rather than parallax: wind speed
 * rises with altitude, so in a cross-section — which is a slice of the real air,
 * not a perspective view — the upper decks genuinely travel further in the same
 * time. It reads as depth as a side effect, which is a pleasant accident and not
 * the reason.
 */
internal fun driftFactor(fraction: Float): Float =
    MinDriftFactor + (1f - MinDriftFactor) * fraction.coerceIn(0f, 1f)

/** A 10 kt wind crosses the frame in this long. See [driftPeriodMillis]. */
internal const val ReferenceWindKt: Int = 10
internal const val ReferenceDriftPeriodMillis: Int = 110_000
internal const val MinDriftPeriodMillis: Int = 34_000
internal const val MaxDriftPeriodMillis: Int = 260_000

/** The drift a deck at ground level would get. See [driftFactor]. */
internal const val MinDriftFactor: Float = 0.45f

// ---------------------------------------------------------------------------
// The section plane, and what follows from committing to it
// ---------------------------------------------------------------------------

/**
 * Which way something moving horizontally travels across the frame, from the
 * direction the wind blows **from**.
 *
 * **This is where the drawing commits to being a section cut east–west through
 * the field**, and once it does, three separate things stop being free choices.
 * A wind direction is where the wind blows *from*, so it blows *toward*
 * `windFromDeg + 180`, and `sin(windFromDeg + 180) = -sin(windFromDeg)`. A
 * westerly (270°) gives +1 and travels right — east, which is where a westerly
 * goes. An easterly gives −1. **A northerly or southerly gives zero**, not
 * because the wind is weak but because a wind along the section's line of sight
 * has no component in the section plane at all.
 *
 * The same projection places the sun and the moon (see [railX]) and slants the
 * precipitation (see [fallDriftFraction]). Using it once and deriving everything
 * from it is what stops the rain and the deck it fell out of slanting different
 * ways, which is what they did while the drift direction was a constant.
 *
 * Returns 0 for a wind with no reported direction. A variable wind has no
 * component to take.
 */
internal fun sectionDrift(windFromDeg: Int?): Float {
    if (windFromDeg == null) return 0f
    return -sin(Math.toRadians(windFromDeg.toDouble())).toFloat()
}

// ---------------------------------------------------------------------------
// The horizon rail
// ---------------------------------------------------------------------------

/**
 * Where the sun and the moon are drawn, and why it is nowhere near the ruler.
 *
 * The scene's Y axis is altitude in feet, and a celestial body has an *elevation
 * angle* rather than an altitude — so the one thing this layer must not do is
 * invite the reader to drop a horizontal from the disc onto the altitude scale.
 *
 * The decisive fact is that **the ruler is only drawn in the bottom 56 % of the
 * frame**: [CeilingThresholds] carries 500, 1,000 and 3,000 ft, so every
 * hairline, every threshold tick and every numeral sits at or below fraction
 * 0.56. The rail spans [RailHorizonFraction] to [RailZenithFraction], where no
 * drawn mark exists — reading a body's height off the ruler would mean
 * extrapolating a scale that stopped a third of the frame below it.
 *
 * The alternative that lost is the beautiful one: an angular arc centred on the
 * observer at the ground line. Its chord runs straight through the ruler at
 * exactly the elevations a mid-latitude sun spends its day at, which is the
 * collision this design exists to prevent. It also needs a *drawn diurnal path*
 * to be self-describing, and a diurnal path needs rise and set times —
 * quantities `Celestial` deliberately does not compute, because the polar
 * stations in the airport set have none on some dates.
 */
internal const val RailHorizonFraction: Float = 0.86f
internal const val RailZenithFraction: Float = 0.935f

/**
 * Where a body of [azimuthDeg] sits across the frame, as a fraction 0..1.
 *
 * `sin(azimuth)` — east to the right, west to the left, **north and south both
 * folded to the centre**. The fold is not a loss: once the section is declared to
 * be cut east–west, `sin(azimuth)` is exactly the component of the body's
 * direction lying in the section plane, and a body due north is edge-on to it.
 * See [sectionDrift], which is the same projection applied to the wind.
 */
internal fun railX(azimuthDeg: Double): Float =
    0.5f + 0.5f * sin(Math.toRadians(azimuthDeg)).toFloat()

/**
 * Where a body of [elevationDeg] sits up the rail, as a fraction of the frame.
 *
 * A shallow dedicated rise rather than a share of the altitude axis, for the
 * reason [RailHorizonFraction] gives. Clamped at both ends: a body below the
 * horizon is not drawn at all (the band's own colour is already the report of
 * where the sun is), and nothing reaches the zenith at the latitudes this app
 * covers often enough to matter.
 */
internal fun railY(elevationDeg: Double): Float {
    val fraction = (elevationDeg / 90.0).coerceIn(0.0, 1.0).toFloat()
    return RailHorizonFraction + fraction * (RailZenithFraction - RailHorizonFraction)
}

/**
 * The moon's X, nudged along the rail until it clears the sun.
 *
 * Near a new moon the elongation is under about 15°, so the two share an azimuth
 * *and* an elevation and would be drawn on top of each other. The **moon** moves
 * rather than the sun, for two reasons: the sun's position is the one the band's
 * whole colour is a claim about, and a new moon that close to the sun is
 * invisible in reality anyway, so nudging it is much the smaller lie.
 *
 * **The nudge is a drawing adjustment and nothing else reads it**, exactly as
 * [deckFractions]' separation is. A caller that used this to answer a question
 * about an azimuth would be wrong; nothing does.
 */
internal fun separatedMoonX(
    sunX: Float,
    moonX: Float,
    minimumGap: Float,
    minimumX: Float = 0f,
    maximumX: Float = 1f,
): Float {
    val gap = moonX - sunX
    if (abs(gap) >= minimumGap) return moonX.coerceIn(minimumX, maximumX)
    // Away from the sun in whichever direction it already leans, and toward the
    // middle of the frame when the two coincide exactly.
    val direction = if (gap >= 0f) 1f else -1f
    return (sunX + direction * minimumGap).coerceIn(minimumX, maximumX)
}

/**
 * A body's X, held far enough from the edge that its whole disc fits.
 *
 * [railX] returns where the body *is*, and at an azimuth near due east or due west
 * that is hard against the frame — so half the disc falls outside it. Seen in the
 * gallery: a waxing crescent at an azimuth of about 272° lost its left half to the
 * card's edge and read as a drawing fault rather than as a moon.
 *
 * Inset rather than clipped, because the alternative is worse in the way that
 * matters: a body clipped at the edge still *reads* as a body in the wrong place,
 * while one nudged inward is only a degree or two from where it belongs on a rail
 * that spans 180° of azimuth. [marginFraction] is the disc's own radius plus its
 * ring, as a fraction of the frame.
 */
internal fun railXInset(x: Float, marginFraction: Float): Float =
    x.coerceIn(marginFraction, 1f - marginFraction)

/**
 * How strongly the celestial layer shows through the reported sky.
 *
 * **Alpha rather than painting order, and the difference is a correctness one.**
 * The rail sits above almost every deck on this axis, so letting paint order
 * decide would let a cirrus wisp hide the sun while a 700 ft overcast let it
 * shine straight through — the drawing lying, in the exact shape this redesign
 * exists to remove. Keyed to the *lowest ceiling* instead, it is an honest
 * statement about how much sky there is to see through.
 *
 * It has a useful consequence: the layer is at full strength precisely when the
 * frame is emptiest. Never zero for a *reported* sky, because a bright patch
 * through an overcast is a real appearance and a reader still needs the time of
 * day; zero only for [SkyCover.Unknown], which draws no weather of any kind.
 */
internal fun celestialAlpha(skyCover: SkyCover): Float = when (skyCover) {
    SkyCover.Unknown -> 0f
    SkyCover.Clear -> 1f
    is SkyCover.Obscured -> 0.10f
    is SkyCover.Layers -> when (skyCover.layers.filter { it.cover.isCeiling }.minByOrNull { it.baseFt }?.cover) {
        null -> 0.85f
        CloudCover.BROKEN -> 0.35f
        else -> 0.18f
    }
}

// ---------------------------------------------------------------------------
// Precipitation
// ---------------------------------------------------------------------------

/**
 * What is falling, as the shape it is drawn with.
 *
 * **Distinguished by mark shape and rate, never by hue.** Shape is the channel
 * this scene already trusts first — the deck silhouettes carry density before
 * opacity or extent do — and a streak and a dot are unmistakable at 168 dp where
 * two inks at the same luminance are not. It also means one ink per band rather
 * than two, which matters because every ink drawn against the air has to clear
 * 3:1 against three different airs.
 *
 * [terminalSpeedKt] is the real fall speed, rounded. It is what turns the
 * reported wind into a slant: snow at 3 kt is laid nearly flat by a 15 kt wind
 * while rain at 18 kt is barely tilted, which is exactly what the two look like.
 */
internal enum class PrecipitationForm(val terminalSpeedKt: Int, val frozen: Boolean) {
    DRIZZLE(terminalSpeedKt = 8, frozen = false),
    RAIN(terminalSpeedKt = 18, frozen = false),
    SNOW(terminalSpeedKt = 3, frozen = true),
    MIXED(terminalSpeedKt = 10, frozen = true),
    HAIL(terminalSpeedKt = 30, frozen = true),
}

/**
 * The form falling at the station, or null when nothing is.
 *
 * **`FZRA` is drawn as rain**, and that is worth stating because drawing it as
 * ice would be inventing a phenomenon: freezing rain is liquid *in the air* and
 * freezes on contact with the surface. The scene already says that at the
 * surface, through [GroundCondition.Icy]. The descriptor changes the ground, not
 * the air.
 *
 * Vicinity weather (`VC`) is not falling *here*, so it draws nothing —
 * [PresentWeather.isPrecipitating] already excludes it.
 */
internal fun precipitationForm(presentWeather: List<PresentWeather>): PrecipitationForm? {
    val falling = presentWeather.filter { it.isPrecipitating }
    if (falling.isEmpty()) return null

    val phenomena = falling.flatMap { it.phenomena }
        .filter { it.kind == PhenomenonKind.PRECIPITATION }
    if (phenomena.isEmpty()) return null

    val hasFrozen = phenomena.any { it.frozen }
    val hasLiquid = phenomena.any { !it.frozen }

    return when {
        phenomena.any { it == WeatherPhenomenon.HAIL || it == WeatherPhenomenon.SMALL_HAIL } ->
            PrecipitationForm.HAIL
        hasFrozen && hasLiquid -> PrecipitationForm.MIXED
        hasFrozen -> PrecipitationForm.SNOW
        phenomena.all { it == WeatherPhenomenon.DRIZZLE } -> PrecipitationForm.DRIZZLE
        else -> PrecipitationForm.RAIN
    }
}

/**
 * How many particles the field carries, from the reported intensity.
 *
 * Deliberately modest. The field has to say *it is raining* at a glance and then
 * stop competing with the decks and the ruler, which are the measured content.
 */
internal fun precipitationCount(intensity: WeatherIntensity, form: PrecipitationForm): Int {
    val base = when (intensity) {
        WeatherIntensity.LIGHT -> 46
        WeatherIntensity.MODERATE -> 84
        WeatherIntensity.HEAVY -> 132
    }
    // Hail is sparse and large; a field of it as dense as drizzle reads as static.
    return if (form == PrecipitationForm.HAIL) base / 3 else base
}

/**
 * The heaviest intensity reported among the precipitating groups.
 *
 * A report can carry `-RA` and `SN` together, and the field should be drawn at
 * the heavier of them rather than at whichever the parser happened to list first.
 */
internal fun precipitationIntensity(presentWeather: List<PresentWeather>): WeatherIntensity =
    presentWeather.filter { it.isPrecipitating }
        .maxByOrNull { it.intensity.ordinal }
        ?.intensity
        ?: WeatherIntensity.MODERATE

/**
 * The fraction of the frame the precipitation falls **from**, or null when there
 * is nothing to fall from.
 *
 * The source deck matters and is not arbitrary:
 *
 * - **Showers and thunderstorms come out of convective cloud**, and the report
 *   names which deck is convective — so `-SHRA` under `BKN043CB` visibly falls
 *   out of the CB. That is the one hazard in the sky that is not an altitude,
 *   finally doing something.
 * - Otherwise it is the lowest deck thick enough to precipitate, and
 *   [CloudCover.isCeiling] is the best proxy the report offers. A FEW at 1,500 ft
 *   under an OVC at 4,000 is not the source. [mergeDecks] preserves ceiling
 *   class, so "lowest deck that is a ceiling" is well defined on the merged list.
 * - Failing both, the lowest deck of any cover.
 * - An **obscured** sky precipitates from the top of the obscuration.
 * - An affirmatively **clear** sky that is also reporting rain is not a
 *   contradiction: an automated station sees no cloud below 12,000 ft, and rain
 *   from above that is exactly what the pair of groups means. It falls from off
 *   the top of the frame, which says so.
 * - An **unknown** sky draws nothing, as everything else in this scene does not.
 */
internal fun precipitationSourceFraction(
    skyCover: SkyCover,
    decks: List<CloudDeck>,
    deckFractions: List<Float>,
    isConvective: Boolean,
): Float? {
    if (skyCover is SkyCover.Unknown) return null
    if (skyCover is SkyCover.Clear) return 1f
    if (skyCover is SkyCover.Obscured) {
        return skyCover.verticalVisibilityFt?.let { altitudeToFraction(it) } ?: MaxInferredFogFraction
    }
    if (decks.isEmpty()) return null

    val convectiveIndex = decks.indexOfFirst { it.convective != null }
    if (isConvective && convectiveIndex >= 0) return deckFractions[convectiveIndex]

    val ceilingIndex = decks.indexOfFirst { it.cover.isCeiling }
    return deckFractions[if (ceilingIndex >= 0) ceilingIndex else 0]
}

/**
 * How far sideways a particle travels per unit of fall, as a tangent.
 *
 * `sectionDrift × windSpeed / terminalSpeed` — the same projection onto the
 * section plane that places the celestial bodies, so a northerly wind drops the
 * rain vertically for the identical reason a northerly sun sits at the centre of
 * the rail. Getting the sign wrong here produces an answer that is *exactly*
 * wrong rather than obviously wrong, which is the failure mode `SurfaceWind`'s
 * KDoc is entirely about.
 *
 * Clamped to [MaxFallAngleDeg] from vertical: past about 62° the streaks stop
 * reading as falling and start reading as horizontal motion blur, and the wind is
 * already told twice over by the deck drift and by the sock.
 */
internal fun fallDriftFraction(windFromDeg: Int?, windSpeedKt: Int?, form: PrecipitationForm): Float {
    val speed = windSpeedKt ?: return 0f
    val raw = sectionDrift(windFromDeg) * speed.toFloat() / form.terminalSpeedKt.toFloat()
    val limit = tan(Math.toRadians(MaxFallAngleDeg.toDouble())).toFloat()
    return raw.coerceIn(-limit, limit)
}

/** Past this from vertical a streak reads as motion blur rather than as falling. */
internal const val MaxFallAngleDeg: Float = 62f

// ---------------------------------------------------------------------------
// The band blend
// ---------------------------------------------------------------------------

/**
 * Which two authored bands the sky is between, and how far.
 *
 * [weight] is 0 at [from] and 1 at [to].
 */
internal data class SkyBlend(val from: SkyPhase, val to: SkyPhase, val weight: Float)

/**
 * The sky the sun's [elevationDeg] puts the field in.
 *
 * The breakpoints are **+6°, −4° and −12°**, and none of them is the astronomical
 * threshold a reader might expect:
 *
 * - **+6° rather than 0° for full day**, because the sky is visibly warming well
 *   before the disc touches the ground, and reaching the day band the instant it
 *   clears the horizon would make sunrise a step.
 * - **−4° for full twilight**, which is mid-civil-twilight — the blue hour the
 *   four palettes' twilight bands were actually authored against. Spending that
 *   band on a fade instead of holding it would mean the authored colour is never
 *   quite shown.
 * - **−12° for full night**, the end of nautical twilight, which is where the sky
 *   stops carrying solar light. Stretching to the astronomical −18° would leave a
 *   mauve sky over a field where a pilot already sees stars.
 *
 * **Saturating below −12° is what makes a polar winter work.** At `PABR` on 21
 * December the sun runs between −4.7° and −42° inside one day; a mapping that
 * kept darkening below the threshold would spend the whole polar winter throbbing
 * between two blacks, which is motion carrying no meaning.
 */
internal fun skyBlendFor(elevationDeg: Double): SkyBlend = when {
    elevationDeg >= DayElevationDeg -> SkyBlend(SkyPhase.DAY, SkyPhase.TWILIGHT, 0f)
    elevationDeg >= TwilightElevationDeg -> SkyBlend(
        from = SkyPhase.DAY,
        to = SkyPhase.TWILIGHT,
        weight = ((DayElevationDeg - elevationDeg) /
            (DayElevationDeg - TwilightElevationDeg)).toFloat(),
    )
    elevationDeg >= NightElevationDeg -> SkyBlend(
        from = SkyPhase.TWILIGHT,
        to = SkyPhase.NIGHT,
        weight = ((TwilightElevationDeg - elevationDeg) /
            (TwilightElevationDeg - NightElevationDeg)).toFloat(),
    )
    else -> SkyBlend(SkyPhase.TWILIGHT, SkyPhase.NIGHT, 1f)
}

internal const val DayElevationDeg: Double = 6.0
internal const val TwilightElevationDeg: Double = -4.0
internal const val NightElevationDeg: Double = -12.0

/**
 * The blend resolved to one number, so continuity can be asserted.
 *
 * DAY is 0, TWILIGHT 1, NIGHT 2. [skyBlendFor]'s *pair* legitimately changes at
 * −4°, where `(DAY, TWILIGHT, 1)` and `(TWILIGHT, NIGHT, 0)` are the same sky —
 * so a test that compared pairs would see a discontinuity that is not one. This
 * collapses both to 1.0 and makes the real claim assertable: the sky must never
 * snap, at any elevation.
 */
internal fun SkyBlend.bandPosition(): Float = when (from) {
    SkyPhase.DAY -> weight
    SkyPhase.TWILIGHT -> 1f + weight
    SkyPhase.NIGHT -> 2f
}

// ---------------------------------------------------------------------------
// The air, as steps
// ---------------------------------------------------------------------------

/**
 * One flat tonal step of air: the slice of the axis it fills, and how far it has
 * been mixed from [SkyBand.low] toward [SkyBand.high].
 *
 * Both bounds are on [altitudeToFraction]'s scale — 0 at the ground, 1 at the top of
 * the axis — like everything else in this file.
 */
internal data class AirStep(val bottomFraction: Float, val topFraction: Float, val mix: Float)

/**
 * How far each step is pulled toward [SkyBand.high], listed from the ground up.
 *
 * Every palette's `high` is darker than its `low`, because that is what the
 * atmosphere does, so this rising sequence is the air thinning with height. The
 * lowest band takes none of it: the air just above the field *is* [SkyBand.low].
 */
private val AirStepMixes = listOf(0.00f, 0.20f, 0.44f, 0.78f)

/**
 * The air, in four flat steps rather than as a gradient.
 *
 * A gradient is what the sky actually does, and it was the first thing drawn here.
 * It is still the wrong mark for this app: the route card's map beside it is a flat
 * silhouette, and a soft atmospheric wash next to a flat silhouette reads as a
 * different application pasted into the same screen.
 *
 * **The steps break at [CeilingThresholds] and nowhere else**, which is what makes
 * this more than a stylistic swap. Each step is the air inside one flight-rules
 * band, so a tone change and a hairline are the same edge stated twice, and the
 * scene's coarsest visual channel — how dark the air is where a deck sits — now
 * answers the question the whole diagram is for. A reader who cannot resolve a 1 dp
 * hairline, or is looking at the card from across a cockpit, still sees which band a
 * ceiling is in.
 *
 * That also settles the spacing, which no longer needs a designer's judgement: the
 * steps are uneven because the axis is, and the axis is uneven for the reason
 * [AxisBreakpoints] gives.
 */
internal val AirSteps: List<AirStep> = run {
    // Ascending, so a step's bounds are its own band's floor and ceiling.
    val edges = listOf(0f) + CeilingThresholds.map { (ft, _) -> altitudeToFraction(ft) } + listOf(1f)
    edges.zipWithNext().mapIndexed { index, (bottom, top) ->
        AirStep(bottomFraction = bottom, topFraction = top, mix = AirStepMixes[index])
    }
}

/**
 * How far an altitude numeral's chip is mixed toward [SkyBand.high].
 *
 * The chip exists so the numeral has a *known* backing. Without one the ink has to
 * clear its contrast bound against whichever of the four steps it happens to land
 * on, which changes with the font scale and with the threshold. One mix, sat on by
 * every numeral, reduces that to a single pair — and it is a pair
 * [SkyBand.cloudEdge] is already proved against, since the chip lies between the
 * band's two ends by construction.
 */
internal const val AxisChipMix: Float = 0.38f

// ---------------------------------------------------------------------------
// Convective strikes
// ---------------------------------------------------------------------------

/**
 * One lightning strike out of a convective deck.
 *
 * [x] is a fraction of the width, [reach] a fraction of the gap between the deck's
 * base and the ground, and [drop] how far below that base the bolt starts, as a
 * fraction of the same gap.
 *
 * [rate] is how many times this bolt runs its own cycle inside one master cycle —
 * a whole number, so the master's restart lands on the bolt's own restart and the
 * loop is seamless. Different rates are the point: two bolts on one period strike
 * in unison, which reads as a blinking indicator rather than as weather.
 */
internal data class Strike(
    val x: Float,
    val drop: Float,
    val reach: Float,
    val rate: Int,
    val phase: Float,
)

/**
 * The strikes a convective deck gets: **two**, and never more.
 *
 * A cumulonimbus is the one thing in this frame that is dangerous, and the mark it
 * had before — a short tick in the band's convective ink, rising out of the deck —
 * was the quietest possible way to say so. It was also the least legible: at 168 dp
 * a 2 dp line is a smudge, and it competed with the silhouette it stood on.
 *
 * A frame full of bolts is the opposite failure and is a cartoon. Two, struck down
 * toward the field rather than hanging off the base, at rates that never coincide.
 */
internal val ConvectiveStrikes: List<Strike> = listOf(
    Strike(x = 0.30f, drop = 0.03f, reach = 0.72f, rate = 7, phase = 0.00f),
    Strike(x = 0.64f, drop = 0.06f, reach = 0.56f, rate = 5, phase = 0.39f),
)

/**
 * How long every strike's schedule repeats over.
 *
 * The master cycle rather than a bolt's own: each [Strike] runs [Strike.rate]
 * cycles inside it, so this is the interval after which the whole pattern repeats.
 * Long, because a storm that strobes reads as a loading state.
 */
internal const val BoltCycleMillis: Int = 31_000

/**
 * A strike's opacity at [cycle], a position through **its own** cycle.
 *
 * Dark for most of it and then a double tick: a real strike is a leader and a
 * return stroke a few tens of milliseconds apart, and the eye reads that pair as
 * lightning where it reads a single fade as a pulse. The envelope is piecewise
 * linear, so this function is the shape rather than a description of it.
 */
internal fun boltOpacity(cycle: Float): Float {
    val t = ((cycle % 1f) + 1f) % 1f
    fun ramp(from: Float, to: Float, a: Float, b: Float): Float = a + (t - from) / (to - from) * (b - a)
    return when {
        t < 0.78f -> 0f
        t < 0.80f -> ramp(0.78f, 0.80f, 0f, 1f)
        t < 0.83f -> ramp(0.80f, 0.83f, 1f, 0.15f)
        t < 0.86f -> ramp(0.83f, 0.86f, 0.15f, 1f)
        t < 0.93f -> ramp(0.86f, 0.93f, 1f, 0f)
        else -> 0f
    }
}

/**
 * The bolt's outline, in a box [BoltBoxWidth] wide and [BoltBoxHeight] tall with
 * its origin at the top left.
 *
 * Held as plain numbers rather than as a `Path` so the shape is data a test can
 * read, and so the path can be built once per convective deck at the call site
 * instead of once per frame.
 */
internal val BoltOutline: List<Pair<Float, Float>> = listOf(
    7.5f to 0f,
    1f to 19f,
    5.5f to 19f,
    4f to 34f,
    11f to 14f,
    6.5f to 14f,
)

internal const val BoltBoxWidth: Float = 12f
internal const val BoltBoxHeight: Float = 34f

// ---------------------------------------------------------------------------
// Precipitation particles
// ---------------------------------------------------------------------------

/**
 * One drop or flake: where it starts, and the three ways it differs from its
 * neighbours.
 *
 * The field used to read as pen hatching, and the diagnosis was that every stroke
 * was the same length, the same weight, the same opacity and falling at the same
 * speed — which is a texture rather than a scatter. [vigour] fixes most of it by
 * tying length *and* speed to one number, because that is how rain looks: the
 * streak a fast drop leaves in the eye is long precisely because it is fast, and a
 * long streak falling slowly is the one combination that cannot occur.
 */
internal data class PrecipParticle(
    /** Start position, both fractions of the field. */
    val x: Float,
    val y: Float,
    /** Whole crossings of the field per master cycle — one of [PrecipSpeeds]. */
    val speed: Int,
    /** How much of the ink it carries. */
    val opacity: Float,
    /** Its stroke weight, or a flake's diameter, as a multiple of the nominal. */
    val gauge: Float,
)

/**
 * How many times a drop crosses the field in one master cycle.
 *
 * **Whole numbers, and that constraint is the whole design of this.** A field
 * animated by one shared phase puts every particle at `(start + progress × speed)
 * mod 1`, so at the end of the cycle a particle has moved `speed` and is back at its
 * own start only if `speed` is an integer. Any other value leaves it part-way down
 * the frame when the phase restarts, and the entire field jumps at once — once every
 * cycle, forever. It is the same family as `AnimatedWeatherGlyph`'s deleted fog bug.
 *
 * The design this came from gave every drop its own CSS animation with its own
 * duration, which has independent timelines and no such constraint. One shared
 * transition is worth the coarser ladder: three speeds is enough to break the
 * texture, and 240 independent animations to run a rain shower is not.
 *
 * Two to four, so the slowest drop still reaches the ground within a cycle and the
 * fastest crosses at twice its rate rather than at a speed the eye cannot follow.
 */
internal val PrecipSpeeds: IntArray = intArrayOf(2, 3, 4)

/** The speed a drop of median vigour falls at; [PrecipPeriodMillis] is set from it. */
internal const val MedianPrecipSpeed: Int = 3

/**
 * The particle at [index], deterministic for the reason [deckSpans] gives: a field
 * that reshuffled when an unrelated part of the screen recomposed would be motion
 * carrying no meaning.
 *
 * All five values come off **one** stream rather than two seeded streams. Two were
 * tried first, on the reasoning that a drop's speed should be drawn independently of
 * its position; because the two seeds differed by a constant, the generator's own
 * linearity carried straight through it and the fast drops ended up biased to one
 * side of the frame — a diagonal band of rain rather than a field. Successive draws
 * from one stream are the thing this generator is actually good at.
 */
internal fun precipitationParticle(index: Int, salt: Int): PrecipParticle {
    var seed = index * 2_654_435_761L + salt * 40_503L
    fun next(): Float {
        seed = seed * 6_364_136_223_846_793_005L + 1_442_695_040_888_963_407L
        return ((seed ushr 33).toFloat() / (1L shl 31).toFloat()).coerceIn(0f, 1f)
    }
    val x = next()
    val y = next()
    val pace = next()
    val weight = next()
    return PrecipParticle(
        x = x,
        y = y,
        speed = PrecipSpeeds[(pace * PrecipSpeeds.size).toInt().coerceIn(0, PrecipSpeeds.size - 1)],
        opacity = MinPrecipOpacity + weight * (MaxPrecipOpacity - MinPrecipOpacity),
        gauge = MinPrecipGauge + weight * (MaxPrecipGauge - MinPrecipGauge),
    )
}

private const val MinPrecipOpacity = 0.50f
private const val MaxPrecipOpacity = 0.95f
private const val MinPrecipGauge = 0.75f
private const val MaxPrecipGauge = 1.25f

/**
 * How long a drop's streak is, as a multiple of the nominal mark length.
 *
 * **Proportional to its speed**, which is not a stylistic choice: the streak is what
 * the eye keeps of a drop that has already moved on, so it is long *because* the
 * drop is fast. A long streak falling slowly is the one combination that cannot
 * occur, and a field where length and speed vary independently is what made this
 * read as pen hatching rather than as rain.
 */
internal fun precipitationMarkScale(speed: Int): Float = speed / MedianPrecipSpeed.toFloat()
