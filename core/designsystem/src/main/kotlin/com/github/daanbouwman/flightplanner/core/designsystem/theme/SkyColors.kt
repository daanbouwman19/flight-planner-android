package com.github.daanbouwman.flightplanner.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.github.daanbouwman.flightplanner.core.designsystem.components.SkyPhase
import kotlin.math.pow
import com.github.daanbouwman.flightplanner.model.GroundCondition

/**
 * One time-of-day's sky, together with the ink its clouds are drawn in.
 *
 * The cloud colours live *inside* the band rather than beside it, and that is not
 * tidiness — it is the only arrangement that can be made to work. A cloud's
 * underside is a hairline, so it needs real luminance separation from the air
 * behind it (WCAG 1.4.11's 3:1 for a graphical object), and no single ink clears
 * that against both a bright day sky and a near-black night one: a dark line that
 * reads cleanly at noon disappears at midnight, and the reverse. So a day deck is
 * drawn with a dark underside and a night deck with a pale one, exactly as they
 * appear. Pairing them here means a call site cannot combine a night sky with a
 * day cloud — the same reason [FlightRulesColorPair] travels as a pair.
 *
 * [low] is the hazy air just above the field; [high] is the thin air at the top of
 * the altitude axis. Every palette's [high] is darker than its [low], because that
 * is what the atmosphere does.
 */
@Immutable
data class SkyBand(
    val low: Color,
    val high: Color,
    val cloudBody: Color,
    val cloudEdge: Color,
    /**
     * The convective mark — the tick that rises out of a `CB` or `TCU` deck.
     *
     * **This moved in here, and it is the third time the same structure has been
     * forced.** It was a single top-level colour, which was fine until someone
     * measured it: Brand light's `#B4524A` cleared only 2.66:1 against its own
     * night band's air, and no single value could have fixed it. Clearing 3:1
     * against that band's near-black top demands a relative luminance of at least
     * 0.124, and clearing it against the *day* band's air demands at most 0.077.
     * The constraints are contradictory, exactly as they were for [cloudEdge].
     *
     * The rule this makes explicit: **anything drawn as a mark against the air
     * belongs to the band.** The escape from that is not a cleverer colour, it is
     * pairing.
     *
     * Each value is a hazard hue at its own band's [cloudEdge] luminance, so the
     * 3:1 guarantee is structural rather than lucky — `cloudEdge` is already
     * proved against both ends of every band, and matching its luminance inherits
     * that proof.
     */
    val convective: Color,
) {
    /**
     * The ink precipitation falls in.
     *
     * **[cloudEdge] itself, deliberately, rather than twelve more authored
     * values.** A falling streak is a hairline against the air, so it needs the
     * same 3:1 the deck's underside needs, against the same three airs — which is
     * to say it has the identical constraint, including the polarity reversal that
     * has now caught this palette three times. Reusing the ink inherits a proved
     * guarantee instead of making a fourth attempt at satisfying it.
     *
     * The argument for authoring it separately is that a deck's hard underside and
     * the rain out of it could then be tuned apart. That is a preference; the
     * guarantee is a constraint, and it is also physically apt — rain is the same
     * water as the cloud it fell from.
     */
    val precipitation: Color get() = cloudEdge
}

/**
 * The sun and the moon.
 *
 * [sunGlow] is a halo, not a second sun — a short radial fade that keeps the disc
 * from looking pasted on. [moonDark] is the unlit part of the lunar disc, drawn
 * rather than omitted: a phase is only readable if the whole circle is present and
 * the terminator falls somewhere inside it.
 */
@Immutable
data class CelestialInk(
    val sun: Color,
    val sunGlow: Color,
    val moonLit: Color,
    val moonDark: Color,
)

/**
 * The ground, its surface states, and the fog that lies on it.
 *
 * [subsurface] is the solid earth below the surface line; the rest are the states
 * [GroundCondition] can report. There is no colour for [GroundCondition.Unknown]
 * on purpose — an unknown surface is drawn as hatch in the graticule's own ink, so
 * that it reads as *missing* rather than as some particular kind of ground. That is
 * the whole shape of the bug this redesign fixes, expressed in the palette.
 */
@Immutable
data class GroundInk(
    val subsurface: Color,
    val dry: Color,
    val wet: Color,
    val snow: Color,
    val frost: Color,
    val icy: Color,
    val fog: Color,
) {
    /**
     * The surface colour for [condition], or `null` when there is none to give.
     *
     * Returning `null` for [GroundCondition.Unknown] rather than a grey is
     * deliberate: a grey is a colour the scene could paint, and painting anything
     * would assert that the surface state is known. The caller must reach for the
     * hatch instead.
     */
    operator fun get(condition: GroundCondition): Color? = when (condition) {
        GroundCondition.Unknown -> null
        GroundCondition.Dry -> dry
        GroundCondition.Wet -> wet
        is GroundCondition.Snow -> snow
        GroundCondition.Icy -> icy
        GroundCondition.Frost -> frost
    }
}

/**
 * The windsock: its mast and the two alternating bands of its cone.
 *
 * Orange and white is the sock's real livery, not a styling choice, so it survives
 * into every palette — dimmed for Cockpit, printed for Chart, but never recoloured
 * into something a pilot would not recognise on a field.
 */
@Immutable
data class SockInk(
    val mast: Color,
    val band: Color,
    val alternateBand: Color,
)

/**
 * The scenery palette the sky profile is drawn with.
 *
 * ### Why this is authored per theme and not derived from Material roles
 *
 * The glyph this replaces took its sun from `colorScheme.tertiary`. In Cockpit
 * `tertiary` is **blue** (`#82CFFF`) and `primary` is the amber — inverted from
 * Brand — so that one line drew a blue sun on the night-flying theme. No role
 * mapping survives all four schemes, and [ChartColorScheme]'s own rule ("no amber
 * anywhere: that accent belongs to the runway") rules out reaching for `primary`
 * as a fix. So there are four palettes here, written out.
 *
 * ### How this differs from [FlightRulesColors], and why
 *
 * Flight-rules colours are safety data: the night theme may restyle the app, but it
 * may not restate the weather, which is why Cockpit shares the dark set unchanged.
 * **The sky is scenery.** Cockpit exists to protect a pilot's dark adaptation, so a
 * bright day gradient there would be an actual defect rather than a stylistic
 * mismatch — and since blue light costs dark adaptation most, Cockpit's sky is
 * warm-shifted as well as dim. Scenery gets a per-theme palette; safety signals do
 * not.
 *
 * Flight-rules colours therefore appear in the scene **only in the measuring
 * apparatus** — the threshold hairlines and the ceiling marker on the axis — never
 * in a sky, cloud or ground fill. The graticule's ink is a different system from
 * the data's ink, exactly as on a printed chart. Nothing in this file is a
 * flight-rules colour.
 *
 * ### What is enforced rather than asserted
 *
 * `SkyColorsContrastTest` checks all six claims made here and in the palettes
 * below: cloud edges clear 3:1 against both ends of their own band, the five ground
 * states are mutually distinguishable, the moon's two halves are, the three
 * horizons are, Cockpit never exceeds its luminance ceiling, and Chart contains no
 * amber. Retune a value here and that test decides whether you may.
 */
@Immutable
data class SkyColors(
    val day: SkyBand,
    val twilight: SkyBand,
    val night: SkyBand,
    val celestial: CelestialInk,
    val ground: GroundInk,
    val sock: SockInk,
) {
    /** The three bands, labelled, for iteration in tests and the theme gallery. */
    val bands: List<Pair<String, SkyBand>>
        get() = listOf("day" to day, "twilight" to twilight, "night" to night)

    /**
     * The five reportable ground states, labelled.
     *
     * [GroundInk.subsurface] and [GroundInk.fog] are absent because they are not
     * states — they are the earth under the surface and the air above it, and both
     * are drawn alongside whichever state is in effect rather than instead of it.
     */
    val groundStates: List<Pair<String, Color>>
        get() = listOf(
            "dry" to ground.dry,
            "wet" to ground.wet,
            "snow" to ground.snow,
            "frost" to ground.frost,
            "icy" to ground.icy,
        )

    /**
     * Colours that cover large areas of the frame.
     *
     * Split from [marks] only for Cockpit's luminance ceiling, and the split is
     * about area rather than importance: Cockpit's own `onSurface` is far brighter
     * than anything permitted here, because a glyph a few hundred pixels across
     * costs a dark-adapted eye almost nothing while a fill spanning the frame costs
     * it real time to recover. Same theme, same eye, different budget.
     */
    val fills: List<Pair<String, Color>>
        get() = bands.flatMap { (name, band) ->
            listOf(
                "$name.low" to band.low,
                "$name.high" to band.high,
                "$name.cloudBody" to band.cloudBody,
            )
        } + listOf(
            "ground.subsurface" to ground.subsurface,
            "ground.fog" to ground.fog,
        ) + groundStates.map { (name, color) -> "ground.$name" to color }

    /** Colours drawn as lines, discs and small shapes. See [fills]. */
    val marks: List<Pair<String, Color>>
        get() = bands.flatMap { (name, band) ->
            listOf("$name.cloudEdge" to band.cloudEdge, "$name.convective" to band.convective)
        } + listOf(
            "celestial.sun" to celestial.sun,
            "celestial.sunGlow" to celestial.sunGlow,
            "celestial.moonLit" to celestial.moonLit,
            "celestial.moonDark" to celestial.moonDark,
            "sock.mast" to sock.mast,
            "sock.band" to sock.band,
            "sock.alternateBand" to sock.alternateBand,
        )

    /** Every colour in the palette, labelled. */
    val all: List<Pair<String, Color>> get() = fills + marks
}

/**
 * Brand light: the app's own surfaces, read as atmosphere.
 *
 * The horizon starts a shade off `surfaceContainer` and the air deepens into the
 * `outline` family, so the scene sits inside the screen's palette instead of
 * arriving from a weather app. The ground is slate rather than soil — a section
 * fill through the earth, the way a profile view draws it, not a picture of mud.
 *
 * The one place the day band's [SkyBand.high] is darker than pure restraint would
 * ask is a constraint rather than a choice: a cloud's underside has to clear 3:1
 * against the air behind it, and at any paler value the hairline goes.
 */
val BrandLightSkyColors: SkyColors = SkyColors(
    day = SkyBand(
        low = Color(0xFFE8EDF6),
        high = Color(0xFF8E9CB8),
        cloudBody = Color(0xFF95A0B5),
        cloudEdge = Color(0xFF3A4256),
        convective = Color(0xFF702E23),
    ),
    twilight = SkyBand(
        low = Color(0xFFD9CBD2),
        high = Color(0xFF8B8FA8),
        cloudBody = Color(0xFF9E9098),
        cloudEdge = Color(0xFF33334A),
        convective = Color(0xFF5A251C),
    ),
    night = SkyBand(
        low = Color(0xFF2B3345),
        high = Color(0xFF12161F),
        cloudBody = Color(0xFF5A6478),
        cloudEdge = Color(0xFFBFC7D6),
        convective = Color(0xFFE8BDB6),
    ),
    celestial = CelestialInk(
        sun = Color(0xFFE6BE7A),
        sunGlow = Color(0xFFF2DFC0),
        moonLit = Color(0xFFE9EDF4),
        moonDark = Color(0xFF333B4C),
    ),
    ground = GroundInk(
        subsurface = Color(0xFF4A505E),
        dry = Color(0xFF6E7686),
        wet = Color(0xFF3E4A57),
        snow = Color(0xFFEDF1F7),
        frost = Color(0xFFBFCBD6),
        icy = Color(0xFF7FA3B4),
        fog = Color(0xFFDDE3EB),
    ),
    sock = SockInk(
        mast = Color(0xFF5C6472),
        band = Color(0xFFD9603F),
        alternateBand = Color(0xFFEDF1F7),
    ),
)

/**
 * Brand dark: the same restraint, one register down.
 *
 * Not the light palette inverted — the bands are *narrower* here. On a dark card
 * a wide gradient has to start bright to end dark, and a bright band is the one
 * thing a dark theme cannot spend; so each band covers less range and separates
 * from its neighbours by hue instead. That also settles the cloud ink: with the
 * air this dim the underside is drawn pale, which is how a deck actually looks
 * against a dark sky.
 */
val BrandDarkSkyColors: SkyColors = SkyColors(
    day = SkyBand(
        low = Color(0xFF5E6B84),
        high = Color(0xFF3E4657),
        cloudBody = Color(0xFF98A4B8),
        cloudEdge = Color(0xFFC6CEDB),
        convective = Color(0xFFEBC4BF),
    ),
    twilight = SkyBand(
        low = Color(0xFF836A74),
        high = Color(0xFF3D3F52),
        cloudBody = Color(0xFFB0A0A8),
        cloudEdge = Color(0xFFE4D8DC),
        convective = Color(0xFFF1D5D1),
    ),
    night = SkyBand(
        low = Color(0xFF1B2130),
        high = Color(0xFF080A11),
        cloudBody = Color(0xFF48536A),
        cloudEdge = Color(0xFFAFB9CB),
        convective = Color(0xFFE2ABA3),
    ),
    celestial = CelestialInk(
        sun = Color(0xFFE6BE7A),
        sunGlow = Color(0xFFB8934F),
        moonLit = Color(0xFFDFE5EF),
        moonDark = Color(0xFF1C222E),
    ),
    ground = GroundInk(
        subsurface = Color(0xFF2E333D),
        dry = Color(0xFF525A69),
        wet = Color(0xFF252E37),
        snow = Color(0xFFCDD5E0),
        frost = Color(0xFF9AA6B4),
        icy = Color(0xFF5A8394),
        fog = Color(0xFF6E7885),
    ),
    sock = SockInk(
        mast = Color(0xFF7A8291),
        band = Color(0xFFD25939),
        alternateBand = Color(0xFFDFE5EF),
    ),
)

/**
 * Cockpit: the same cross-section, drawn for an eye that must stay dark-adapted.
 *
 * Two things follow from that and neither is decoration. **Everything is dim** — no
 * fill exceeds a relative luminance of 0.42, no mark 0.58, which is what makes this
 * a night-flying theme rather than a dark one. And **everything is warm** — short
 * wavelengths cost rod sensitivity most, so where the other palettes reach for
 * slate this one reaches for warm graphite. Only ice and wet asphalt stay cool,
 * because those two states are *about* being cool and a warm one would say the
 * wrong thing.
 *
 * The visible consequence is that time of day separates by hue inside a narrow
 * luminance window instead of by brightness: a warm graphite day, a rust dusk, a
 * near-black night. At the top of the axis all three converge, which is honest —
 * thin air is dark at any hour.
 */
val CockpitSkyColors: SkyColors = SkyColors(
    day = SkyBand(
        low = Color(0xFF413A33),
        high = Color(0xFF211D18),
        cloudBody = Color(0xFF655C52),
        cloudEdge = Color(0xFFAEA294),
        convective = Color(0xFFD39774),
    ),
    twilight = SkyBand(
        low = Color(0xFF5A2A1E),
        high = Color(0xFF261B1E),
        cloudBody = Color(0xFF6B564C),
        cloudEdge = Color(0xFFB79880),
        convective = Color(0xFFD08F6A),
    ),
    night = SkyBand(
        low = Color(0xFF231A14),
        high = Color(0xFF0A0705),
        cloudBody = Color(0xFF453B32),
        cloudEdge = Color(0xFF978673),
        convective = Color(0xFFC57546),
    ),
    celestial = CelestialInk(
        sun = Color(0xFFD89A3C),
        sunGlow = Color(0xFF54391A),
        moonLit = Color(0xFFC2B8AB),
        moonDark = Color(0xFF1C1813),
    ),
    ground = GroundInk(
        subsurface = Color(0xFF1F1A15),
        dry = Color(0xFF4E463B),
        wet = Color(0xFF1F262A),
        snow = Color(0xFF9C968D),
        frost = Color(0xFF726D64),
        icy = Color(0xFF3C5C68),
        fog = Color(0xFF443E37),
    ),
    sock = SockInk(
        mast = Color(0xFF7C736A),
        band = Color(0xFFB84E28),
        alternateBand = Color(0xFFAEA69C),
    ),
)

/**
 * Chart: the cross-section as a printed figure.
 *
 * The air is a wash over paper rather than a colour — the day sky starts almost at
 * `surface` and deepens only as far as a light navy dilution, so the figure reads
 * as printed on the screen it sits on.
 *
 * **This is the palette that takes a risk, and the risk is the sun.** It is drawn
 * in navy ink with a paper-toned halo rather than as a warm disc, because Chart is
 * not a light theme with beige in it — it is chart paper and ink, and a chart does
 * not have a yellow sun on it. A printed figure indicates the sun the way it
 * indicates everything else: with a line. The alternative was worse than dull, it
 * was unavailable: [ChartColorScheme] reserves amber for the runway, and a
 * yellow-orange disc is the one thing this palette may not contain.
 *
 * Night is still a dark ink wash, because a moon over pale paper would mean
 * nothing. The windsock keeps its vermilion — orange is not amber, and the sock is
 * a legend symbol a pilot already reads in that colour.
 */
val ChartSkyColors: SkyColors = SkyColors(
    day = SkyBand(
        low = Color(0xFFEAE6DA),
        high = Color(0xFFA3AFB9),
        cloudBody = Color(0xFFBCBDB4),
        cloudEdge = Color(0xFF39434F),
        convective = Color(0xFF693229),
    ),
    twilight = SkyBand(
        low = Color(0xFFD4C6B4),
        high = Color(0xFF8E8C9C),
        cloudBody = Color(0xFFB5ACA0),
        cloudEdge = Color(0xFF302E42),
        convective = Color(0xFF4E241E),
    ),
    night = SkyBand(
        low = Color(0xFF39415A),
        high = Color(0xFF1B2031),
        cloudBody = Color(0xFF6C7591),
        cloudEdge = Color(0xFFDCE0E8),
        convective = Color(0xFFF0DCD8),
    ),
    celestial = CelestialInk(
        sun = Color(0xFF465162),
        sunGlow = Color(0xFFD6CDB9),
        moonLit = Color(0xFFFBF8F1),
        moonDark = Color(0xFF2A3242),
    ),
    ground = GroundInk(
        subsurface = Color(0xFFB3AC9C),
        dry = Color(0xFFC4BCAA),
        wet = Color(0xFF78838C),
        snow = Color(0xFFFCFAF4),
        frost = Color(0xFFD1D9D8),
        icy = Color(0xFF93B1BC),
        fog = Color(0xFFE9E3D6),
    ),
    sock = SockInk(
        mast = Color(0xFF4A4438),
        band = Color(0xFFBC5138),
        alternateBand = Color(0xFFFCFAF4),
    ),
)


/**
 * The scenery palette in scope.
 *
 * `static` for the same reason as [LocalFlightRulesColors]: it changes only when
 * the theme does, and the whole tree recomposes then anyway.
 */
val LocalSkyColors: ProvidableCompositionLocal<SkyColors> =
    staticCompositionLocalOf { BrandLightSkyColors }

/**
 * WCAG relative luminance, for the polarity test below.
 *
 * `SkyColorsContrastTest` computes the same quantity, and the duplication is
 * deliberate: a test that imported the implementation's own luminance would be
 * checking that function against itself.
 */
private fun Color.relativeLuminance(): Float {
    fun channel(value: Float) =
        if (value <= 0.03928f) value / 12.92f else ((value + 0.055f) / 1.055f).toDouble().pow(2.4).toFloat()
    return 0.2126f * channel(red) + 0.7152f * channel(green) + 0.0722f * channel(blue)
}

/**
 * Whether a band's cloud ink is darker than its air.
 *
 * Day and twilight bands in the light-family palettes draw a **dark** underside on
 * bright air; every night band draws a **pale** one on dark air. That reversal is
 * the whole reason [SkyBand.cloudEdge] lives inside the band, and it is what makes
 * [blendBands] refuse to interpolate across it.
 */
internal fun SkyBand.edgeIsDarkerThanAir(): Boolean =
    cloudEdge.relativeLuminance() < low.relativeLuminance()

/** Whether two bands draw their cloud ink the same way round. See [blendBands]. */
internal fun bandsAgreeOnPolarity(from: SkyBand, to: SkyBand): Boolean =
    from.edgeIsDarkerThanAir() == to.edgeIsDarkerThanAir()

/**
 * Two authored bands, mixed.
 *
 * ### Why this is not simply safe to call at any [t]
 *
 * **Interpolating across a polarity reversal destroys the 3:1 guarantee, provably
 * and maximally.** In Brand light the twilight band's `cloudEdge` (`#33334A`) is
 * darker than its air and the night band's (`#BFC7D6`) is lighter than its own.
 * Both the ink's luminance and the air's are continuous in `t`, so by the
 * intermediate value theorem there is a `t` at which they are *equal* and the
 * deck's underside is a 1.0:1 line — invisible. Chart reverses the same way.
 *
 * Nor can a cleverer ink rescue it. Measured at `t = 0.5` for Brand light's
 * twilight-to-night: the twilight ink gives 3.22:1 against the blended horizon but
 * 1.56:1 against the blended top, and the night ink gives 2.27:1 and 4.63:1 — and
 * no third ink exists, because clearing 3:1 against the twilight top demands a
 * luminance of at most 0.06 while clearing it against the night top demands at
 * least 0.142. The constraints contradict, so the middle of that blend is simply
 * unservable.
 *
 * What is left is to control **how long the scene sits in it**. [bandsAgreeOnPolarity]
 * is what the caller checks: where the two bands agree, `t` may be the sun's
 * elevation and the blend is continuous; where they disagree, the caller drives `t`
 * from a `FlightMotion.effects()` traversal instead, so the crossing takes a few
 * hundred milliseconds rather than the twenty minutes the sun takes to cross it.
 * A defect lasting three frames of a transition is not the same defect as one
 * lasting a quarter of an hour, and this is the honest way to say so.
 */
internal fun blendBands(from: SkyBand, to: SkyBand, t: Float): SkyBand {
    val fraction = t.coerceIn(0f, 1f)
    return SkyBand(
        low = lerp(from.low, to.low, fraction),
        high = lerp(from.high, to.high, fraction),
        cloudBody = lerp(from.cloudBody, to.cloudBody, fraction),
        cloudEdge = lerp(from.cloudEdge, to.cloudEdge, fraction),
        convective = lerp(from.convective, to.convective, fraction),
    )
}

/** The authored band for one time of day. */
internal fun SkyColors.bandFor(phase: SkyPhase): SkyBand =
    when (phase) {
        SkyPhase.DAY -> day
        SkyPhase.TWILIGHT -> twilight
        SkyPhase.NIGHT -> night
    }
