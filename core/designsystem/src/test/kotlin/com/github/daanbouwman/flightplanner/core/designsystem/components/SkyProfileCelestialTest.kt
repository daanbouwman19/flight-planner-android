package com.github.daanbouwman.flightplanner.core.designsystem.components

import com.github.daanbouwman.flightplanner.model.CloudCover
import com.github.daanbouwman.flightplanner.model.CloudLayer
import com.github.daanbouwman.flightplanner.model.PhenomenonKind
import com.github.daanbouwman.flightplanner.model.PresentWeather
import com.github.daanbouwman.flightplanner.model.SkyCover
import com.github.daanbouwman.flightplanner.model.WeatherDescriptor
import com.github.daanbouwman.flightplanner.model.WeatherIntensity
import com.github.daanbouwman.flightplanner.model.WeatherPhenomenon
import com.github.daanbouwman.flightplanner.routing.Celestial
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.floats.shouldBeGreaterThan
import io.kotest.matchers.floats.shouldBeLessThan
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlin.math.abs
import kotlin.test.Test

/**
 * The horizon rail, and the fact that makes it safe.
 *
 * The scene's Y axis is altitude in feet and a celestial body has an elevation
 * *angle*, so the one thing this layer must never do is invite a reader to drop a
 * horizontal from the disc onto the altitude scale. The rail's whole defence is
 * that it sits where no ruler is drawn — which is a property of
 * [CeilingThresholds] and is asserted here rather than assumed.
 */
class HorizonRailTest {

    @Test
    fun `the rail is clear of every mark the ruler draws`() {
        // The load-bearing claim. `CeilingThresholds` tops out at 3,000 ft, which
        // the axis puts at 0.56 — so every hairline, tick and numeral in the scene
        // sits at or below that, and the rail begins 0.30 of the frame above the
        // highest of them. If a fourth threshold is ever added, this fails, and it
        // should: the rail would need moving.
        val highestMark = CeilingThresholds.maxOf { (ft, _) -> altitudeToFraction(ft) }

        highestMark shouldBe (0.56f plusOrMinus 0.001f)
        RailHorizonFraction shouldBeGreaterThan highestMark + 0.25f
        RailZenithFraction shouldBeLessThan 1f
    }

    @Test
    fun `east is right, west is left, and north and south fold to the centre`() {
        // The projection, not an artefact: once the section is cut east-west,
        // sin(azimuth) is exactly the component of the body's direction lying in
        // the section plane, and a body due north is edge-on to it.
        railX(90.0) shouldBe (1f plusOrMinus 0.001f)
        railX(270.0) shouldBe (0f plusOrMinus 0.001f)
        railX(0.0) shouldBe (0.5f plusOrMinus 0.001f)
        railX(180.0) shouldBe (0.5f plusOrMinus 0.001f)
    }

    @Test
    fun `a body on the horizon sits on its own datum, and the rail rises with elevation`() {
        railY(0.0) shouldBe (RailHorizonFraction plusOrMinus 0.0001f)
        railY(90.0) shouldBe (RailZenithFraction plusOrMinus 0.0001f)
        // Monotonic, so a rising sun never appears to descend.
        var previous = -1f
        var elevation = 0.0
        while (elevation <= 90.0) {
            val y = railY(elevation)
            (y >= previous) shouldBe true
            previous = y
            elevation += 0.5
        }
    }

    @Test
    fun `the moon is nudged off the sun near a new moon, and the sun never moves`() {
        // Near new the elongation is under about 15 deg, so the two share an
        // azimuth and would be drawn on top of each other. The moon moves because
        // the sun's position is what the band's whole colour is a claim about.
        val nudged = separatedMoonX(sunX = 0.5f, moonX = 0.505f, minimumGap = 0.05f)

        abs(nudged - 0.5f) shouldBeGreaterThan 0.049f
        // Far apart, nothing happens.
        separatedMoonX(sunX = 0.2f, moonX = 0.8f, minimumGap = 0.05f) shouldBe 0.8f
        // And it stays inside the frame even when the sun is at an edge.
        val atEdge = separatedMoonX(sunX = 0.99f, moonX = 0.99f, minimumGap = 0.05f)
        (atEdge in 0f..1f) shouldBe true
    }

    @Test
    fun `how much sky there is to see through is the lowest ceiling's business`() {
        // Alpha rather than painting order, because the rail sits above almost
        // every deck: paint order alone would let a cirrus hide the sun while a
        // 700 ft overcast let it shine straight through.
        celestialAlpha(SkyCover.Clear) shouldBe 1f
        celestialAlpha(SkyCover.Unknown) shouldBe 0f

        val scatteredOnly = SkyCover.Layers(listOf(CloudLayer(CloudCover.SCATTERED, 2_700)))
        val broken = SkyCover.Layers(listOf(CloudLayer(CloudCover.BROKEN, 1_300)))
        val overcast = SkyCover.Layers(listOf(CloudLayer(CloudCover.OVERCAST, 700)))

        celestialAlpha(scatteredOnly) shouldBeGreaterThan celestialAlpha(broken)
        celestialAlpha(broken) shouldBeGreaterThan celestialAlpha(overcast)
        // Never zero for a reported sky: a bright patch through an overcast is a
        // real appearance, and the reader still needs the time of day.
        celestialAlpha(overcast) shouldBeGreaterThan 0f
    }

    @Test
    fun `a real KDEN afternoon report places the sun somewhere drawable`() {
        // The case that caught a defect on a device: the bodies were painted
        // beneath the decks, and `SCT090 BKN130 BKN220` buried the afternoon sun
        // completely. The geometry was never wrong — this pins that, so a future
        // failure is read as a drawing-order question rather than a maths one.
        val state = Celestial.at(latitudeDeg = 39.86, longitudeDeg = -104.6738, epochSeconds = 1_787_869_980L)

        state.sun.elevationDeg shouldBeGreaterThan 0.0
        state.sun.isUp shouldBe true
        val x = railX(state.sun.azimuthDeg)
        val y = railY(state.sun.elevationDeg)
        (x in 0f..1f) shouldBe true
        (y in RailHorizonFraction..RailZenithFraction) shouldBe true

        val sky = SkyCover.Layers(
            listOf(
                CloudLayer(CloudCover.SCATTERED, 9_000),
                CloudLayer(CloudCover.BROKEN, 13_000),
                CloudLayer(CloudCover.BROKEN, 22_000),
            ),
        )
        celestialAlpha(sky) shouldBeGreaterThan 0f
    }
}

/**
 * The band blend, and the one place it is not continuous.
 *
 * The plan's requirement is that the palette *crossfades rather than snapping*, so
 * a snap is exactly a discontinuity in [SkyBlend.bandPosition] — which is what
 * makes it assertable at all.
 */
class SkyBlendTest {

    @Test
    fun `the blend is continuous across every threshold`() {
        // The pair (from, to) legitimately changes at -4 deg, where
        // (DAY, TWILIGHT, 1) and (TWILIGHT, NIGHT, 0) are the same sky — so a test
        // comparing pairs would see a discontinuity that is not one. The scalar
        // collapses both to 1.0 and makes the real claim checkable.
        var elevation = -90.0
        var previous = skyBlendFor(elevation).bandPosition()
        while (elevation <= 90.0) {
            elevation += 0.01
            val current = skyBlendFor(elevation).bandPosition()
            abs(current - previous) shouldBeLessThan 0.01f
            previous = current
        }
    }

    @Test
    fun `the blend is monotonic, so a setting sun never brightens`() {
        var elevation = 90.0
        var previous = skyBlendFor(elevation).bandPosition()
        while (elevation >= -90.0) {
            elevation -= 0.05
            val current = skyBlendFor(elevation).bandPosition()
            (current >= previous - 1e-4f) shouldBe true
            previous = current
        }
    }

    @Test
    fun `each authored band is actually reached, and held`() {
        // The claim with content: the four palettes' twilight bands were authored
        // for the light of civil twilight, so a mapping that never quite arrives
        // at them would mean the authored colour is never shown.
        skyBlendFor(6.0).bandPosition() shouldBe (0f plusOrMinus 0.001f)
        skyBlendFor(40.0).bandPosition() shouldBe (0f plusOrMinus 0.001f)
        skyBlendFor(-4.0).bandPosition() shouldBe (1f plusOrMinus 0.001f)
        skyBlendFor(-12.0).bandPosition() shouldBe (2f plusOrMinus 0.001f)
    }

    @Test
    fun `astronomical night saturates, so a polar winter is steady`() {
        // At PABR on 21 December the sun runs between -4.7 and -42 deg inside one
        // day. A mapping that kept darkening below the threshold would spend the
        // whole polar winter throbbing between two blacks — motion carrying no
        // meaning, which this app's own rule forbids.
        val floor = skyBlendFor(-12.0)
        skyBlendFor(-30.0) shouldBe floor
        skyBlendFor(-42.0) shouldBe floor
        skyBlendFor(-90.0) shouldBe floor
    }

    @Test
    fun `a Dutch midsummer never reaches full night`() {
        // Closed form: the sun's minimum elevation at EHAM on the June solstice is
        // lat + declination - 90 = 52.3086 + 23.4381 - 90 = -14.25 deg. That is
        // past -12, so the Netherlands does reach the night band in June — which
        // is worth pinning, because the *sky* there famously does not go fully
        // dark, and the difference is the airglow the -12 threshold deliberately
        // stops short of claiming.
        val midnight = skyBlendFor(-14.25)

        midnight.from shouldBe SkyPhase.TWILIGHT
        midnight.to shouldBe SkyPhase.NIGHT
        midnight.weight shouldBe (1f plusOrMinus 0.001f)
    }
}

/** What falls, from where, and which way the wind lays it. */
class PrecipitationGeometryTest {

    private fun weather(
        code: String,
        intensity: WeatherIntensity = WeatherIntensity.MODERATE,
        descriptor: WeatherDescriptor? = null,
        vararg phenomena: WeatherPhenomenon,
    ) = PresentWeather(
        intensity = intensity,
        descriptor = descriptor,
        phenomena = phenomena.toList(),
        code = code,
    )

    @Test
    fun `the form comes from what is falling, and freezing rain is rain`() {
        precipitationForm(listOf(weather("-RA", phenomena = arrayOf(WeatherPhenomenon.RAIN)))) shouldBe
            PrecipitationForm.RAIN
        precipitationForm(listOf(weather("SN", phenomena = arrayOf(WeatherPhenomenon.SNOW)))) shouldBe
            PrecipitationForm.SNOW
        precipitationForm(listOf(weather("DZ", phenomena = arrayOf(WeatherPhenomenon.DRIZZLE)))) shouldBe
            PrecipitationForm.DRIZZLE
        precipitationForm(
            listOf(weather("RASN", phenomena = arrayOf(WeatherPhenomenon.RAIN, WeatherPhenomenon.SNOW))),
        ) shouldBe PrecipitationForm.MIXED
        precipitationForm(listOf(weather("GR", phenomena = arrayOf(WeatherPhenomenon.HAIL)))) shouldBe
            PrecipitationForm.HAIL

        // Freezing rain is liquid *in the air* — it freezes on contact with the
        // surface, which the scene already says through `GroundCondition.Icy`.
        // Drawing it as ice in flight would be inventing a phenomenon.
        precipitationForm(
            listOf(
                weather(
                    "FZRA",
                    descriptor = WeatherDescriptor.FREEZING,
                    phenomena = arrayOf(WeatherPhenomenon.RAIN),
                ),
            ),
        ) shouldBe PrecipitationForm.RAIN
    }

    @Test
    fun `nothing falls for an obscuration alone, or for weather in the vicinity`() {
        precipitationForm(listOf(weather("BR", phenomena = arrayOf(WeatherPhenomenon.MIST)))).shouldBeNull()
        precipitationForm(emptyList()).shouldBeNull()

        val nearby = PresentWeather(
            inVicinity = true,
            phenomena = listOf(WeatherPhenomenon.RAIN),
            code = "VCSH",
        )
        precipitationForm(listOf(nearby)).shouldBeNull()
    }

    @Test
    fun `showers fall out of the convective deck, and everything else out of the ceiling`() {
        val decks = listOf(
            CloudDeck(CloudCover.FEW, baseFt = 1_500, convective = null, mergedCount = 1),
            CloudDeck(
                CloudCover.BROKEN,
                baseFt = 4_300,
                convective = com.github.daanbouwman.flightplanner.model.ConvectiveCloud.CUMULONIMBUS,
                mergedCount = 1,
            ),
        )
        val fractions = deckFractions(decks)
        val sky = SkyCover.Layers(
            listOf(CloudLayer(CloudCover.FEW, 1_500), CloudLayer(CloudCover.BROKEN, 4_300)),
        )

        // A shower comes out of the CB, not out of the FEW below it.
        precipitationSourceFraction(sky, decks, fractions, isConvective = true) shouldBe fractions[1]
        // Steady rain comes out of the lowest deck that is a ceiling — still not
        // the FEW, which is not thick enough to be the source.
        precipitationSourceFraction(sky, decks, fractions, isConvective = false) shouldBe fractions[1]
    }

    @Test
    fun `a clear sky reporting rain falls from off the top of the frame`() {
        // Not a contradiction: an automated station sees no cloud below 12,000 ft,
        // so rain from above that is exactly what the pair of groups means.
        precipitationSourceFraction(SkyCover.Clear, emptyList(), emptyList(), isConvective = false) shouldBe 1f
        // And an unknown sky draws no weather of any kind.
        precipitationSourceFraction(SkyCover.Unknown, emptyList(), emptyList(), isConvective = false)
            .shouldBeNull()
    }

    @Test
    fun `the wind lays the fall along the section, and a northerly not at all`() {
        // A wind direction is where the wind blows *from*, so it blows toward
        // windFromDeg + 180 and the sign is negated. Getting this backwards
        // produces an answer exactly wrong rather than obviously wrong.
        val westerly = fallDriftFraction(270, 15, PrecipitationForm.SNOW)
        val easterly = fallDriftFraction(90, 15, PrecipitationForm.SNOW)
        val northerly = fallDriftFraction(360, 15, PrecipitationForm.SNOW)

        westerly shouldBeGreaterThan 0f // a westerly carries it east, to the right
        easterly shouldBeLessThan 0f
        northerly shouldBe (0f plusOrMinus 0.001f)

        // Snow at 3 kt terminal is laid far flatter than rain at 18 kt by the same
        // wind, which is what the two actually look like.
        abs(fallDriftFraction(270, 15, PrecipitationForm.SNOW)) shouldBeGreaterThan
            abs(fallDriftFraction(270, 15, PrecipitationForm.RAIN))
    }

    @Test
    fun `the fall angle is clamped, so a gale is not a hatch pattern`() {
        // Past about 62 deg a streak stops reading as falling and starts reading as
        // horizontal motion blur — and the wind is already told twice over by the
        // deck drift and by the sock.
        val gale = fallDriftFraction(270, 60, PrecipitationForm.SNOW)
        val limit = kotlin.math.tan(Math.toRadians(MaxFallAngleDeg.toDouble())).toFloat()

        gale shouldBe (limit plusOrMinus 0.001f)
    }

    @Test
    fun `the drift direction is the same projection the bodies use`() {
        // One commitment, used three times: it places the sun, it slants the rain,
        // and it pushes the decks. Before this they could disagree.
        sectionDrift(270) shouldBe (1f plusOrMinus 0.001f)
        sectionDrift(90) shouldBe (-1f plusOrMinus 0.001f)
        sectionDrift(0) shouldBe (0f plusOrMinus 0.001f)
        sectionDrift(null) shouldBe 0f

        // And it agrees with railX: a body due east and a wind from the west both
        // point to the right of the frame.
        (railX(90.0) > 0.5f) shouldBe true
        (sectionDrift(270) > 0f) shouldBe true
    }

    @Test
    fun `heavier weather draws more, and hail draws less`() {
        precipitationCount(WeatherIntensity.HEAVY, PrecipitationForm.RAIN) shouldBeGreaterThanCount
            precipitationCount(WeatherIntensity.LIGHT, PrecipitationForm.RAIN)
        // Hail is sparse and large; as dense as drizzle it reads as static.
        precipitationCount(WeatherIntensity.MODERATE, PrecipitationForm.HAIL) shouldBeLessThanCount
            precipitationCount(WeatherIntensity.MODERATE, PrecipitationForm.RAIN)
    }

    @Test
    fun `the heaviest reported group decides the density`() {
        val groups = listOf(
            weather("-RA", intensity = WeatherIntensity.LIGHT, phenomena = arrayOf(WeatherPhenomenon.RAIN)),
            weather("+SN", intensity = WeatherIntensity.HEAVY, phenomena = arrayOf(WeatherPhenomenon.SNOW)),
        )

        precipitationIntensity(groups) shouldBe WeatherIntensity.HEAVY
    }

    @Test
    fun `the particle field is seamless at the end of a cycle`() {
        // The `drawFog` bug's family, and the usual fix cannot work here: the
        // horizontal travel is the frame height times the slant, which is not a
        // whole number of widths for any angle the data produces. Deriving x from
        // y instead makes the point *set* invariant — at the end of a cycle every
        // particle is back at its own start.
        //
        // The per-drop speed is in the expression on purpose. It is the second half
        // of the same claim and the half that is easy to break: a drop moves
        // `progress × speed` per cycle, so a fractional speed leaves it part-way down
        // the frame when the phase restarts and the whole field jumps at once. See
        // [PrecipSpeeds].
        val slant = 0.6f
        val width = 300f
        val height = 200f

        fun positions(progress: Float): List<Pair<Float, Float>> = (0 until 40).map { index ->
            val particle = precipitationParticle(index, salt = 1)
            val y = ((particle.y + progress * particle.speed) % 1f) * height
            val x = (((particle.x + (y / width) * slant) % 1f + 1f) % 1f) * width
            x to y
        }

        val start = positions(0f)
        val end = positions(0.99999f)
        start.zip(end).forEach { (a, b) ->
            abs(a.first - b.first) shouldBeLessThan 0.05f
            abs(a.second - b.second) shouldBeLessThan 0.05f
        }
    }

    private infix fun Int.shouldBeGreaterThanCount(other: Int) { (this > other) shouldBe true }
    private infix fun Int.shouldBeLessThanCount(other: Int) { (this < other) shouldBe true }
}

/**
 * The moon's terminator, as the arithmetic behind a 14 dp disc.
 *
 * Too small to judge by eye on a device — a full moon and a half moon differ by
 * about ten pixels — so the shape is asserted here instead. The construction is
 * the standard one: the lit half of the disc, plus (gibbous) or minus (crescent)
 * a terminator ellipse of half-width `|1 - 2k|·r`.
 */
class MoonTerminatorTest {

    private fun bow(illuminated: Float, radius: Float): Float = abs(1f - 2f * illuminated) * radius

    @Test
    fun `a full moon is lit right across, and a half moon has a straight terminator`() {
        // At k = 1 the ellipse spans the whole disc, so the lit half plus the
        // ellipse is the whole disc — a full moon is fully pale. This is the case
        // that would look like a *half* moon if the ellipse were dropped, which is
        // the plausible mistake.
        bow(1f, 10f) shouldBe (10f plusOrMinus 0.001f)
        // At k = 0.5 the ellipse is degenerate, leaving exactly the lit half.
        bow(0.5f, 10f) shouldBe (0f plusOrMinus 0.001f)
    }

    @Test
    fun `a crescent and a gibbous of equal offset are mirror images`() {
        // |1 - 2k| is symmetric about a half moon, which is what lets one
        // expression serve both — the *fill colour* is what differs, not the shape.
        bow(0.25f, 10f) shouldBe (bow(0.75f, 10f) plusOrMinus 0.001f)
    }

    @Test
    fun `the sliver threshold is below what the disc can draw`() {
        // Under 4 % illumination the lit sliver is thinner than a pixel at the
        // shipped radius, so the disc is drawn dark with only its ring — which is
        // what a new moon is, rather than a disc with an invisible smudge on it.
        val radius = 7f * 3.5f // BodyRadiusDp at a 3.5x density
        val sliverWidth = radius - bow(MoonSliverFraction, radius)

        sliverWidth shouldBeLessThan 2f
    }
}

/**
 * A body near the frame's edge keeps its whole disc.
 *
 * Found on a device, in a since-removed gallery screen that drew every weather state
 * one per tap: a waxing crescent at an azimuth of about 272° sat at a rail fraction
 * of 0.02 and lost its left half to the card's edge. Nothing about the astronomy was
 * wrong — the moon genuinely was almost due west — and no test could have caught it,
 * because the position was right and only the drawing was clipped.
 *
 * The margin the drawing passes in is the disc **plus the ceiling wedge's depth**,
 * which is a second case of the same bug: a clear sky puts the wedge at the top of
 * the axis, and a high moon at a westerly azimuth sits in that same corner, so the
 * full moon over EHAM came out with a green triangle through it.
 */
class RailInsetTest {

    @Test
    fun `a body at the extremes of the rail is held clear of both edges`() {
        val margin = 0.05f

        railXInset(railX(90.0), margin) shouldBe (1f - margin plusOrMinus 0.001f)
        railXInset(railX(270.0), margin) shouldBe (margin plusOrMinus 0.001f)
        // And a body nowhere near an edge is not moved at all.
        railXInset(railX(0.0), margin) shouldBe (0.5f plusOrMinus 0.001f)
    }

    @Test
    fun `separating the moon respects the same edges`() {
        // The nudge and the inset have to agree, or the fix for one reintroduces
        // the other: pushing the moon off the sun must not push it off the frame.
        val margin = 0.05f
        val nudged = separatedMoonX(
            sunX = 1f - margin,
            moonX = 1f - margin,
            minimumGap = 0.08f,
            minimumX = margin,
            maximumX = 1f - margin,
        )

        (nudged in margin..(1f - margin)) shouldBe true
    }
}
