package com.github.daanbouwman.flightplanner.ui

import com.github.daanbouwman.flightplanner.model.AltimeterConvention
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlin.test.Test

/** 9,000 m and 9,999 m, as `MetarParser` normalises them. */
private const val M_9000_IN_SM = 9_000.0 / 1_609.344
private const val M_9999_IN_SM = 9_999.0 / 1_609.344

class VisibilityFigureTest {

    @Test
    fun `a US station's fractions come back out as the fractions it sent`() {
        // `1/2SM` rendered as `0.5 SM` is a conversion the pilot has to undo.
        statuteMilesFigure(0.5) shouldBe "1/2"
        statuteMilesFigure(0.25) shouldBe "1/4"
        statuteMilesFigure(0.75) shouldBe "3/4"
        statuteMilesFigure(2.5) shouldBe "2 1/2"
        statuteMilesFigure(1.25) shouldBe "1 1/4"
        // A whole number below three is a whole number, not `1 0/16`.
        statuteMilesFigure(2.0) shouldBe "2"
    }

    @Test
    fun `a visibility converted from metres is not a fraction anybody reported`() {
        // 1,500 m is 0.932 SM — not a sixteenth, so it prints as a decimal
        // rather than being snapped to `15/16` and claiming a report that never
        // happened.
        statuteMilesFigure(1_500.0 / 1_609.344) shouldBe "0.9"
    }

    @Test
    fun `above three miles a whole number is printed only when the value is one`() {
        // The defect this test exists for, found on a device against a live
        // `VVPQ … 9000`: rounding to the nearest mile printed 5.59 as `6`, and
        // would have printed a 5.4 SM report as `5` — the MVFR/VFR visibility
        // boundary. A VFR field reading as marginal is the same class of defect
        // as an IFR field drawing a sun, only smaller.
        statuteMilesFigure(M_9000_IN_SM) shouldBe "5.6"
        statuteMilesFigure(5.4) shouldBe "5.4"
        statuteMilesFigure(M_9999_IN_SM) shouldBe "6.2"

        // A US `10SM` is genuinely ten and says so without a decimal.
        statuteMilesFigure(10.0) shouldBe "10"
    }

    @Test
    fun `metres below five kilometres, kilometres above`() {
        metresFigure(800.0) shouldBe "800 m"
        metresFigure(4_800.0) shouldBe "4,800 m"
        metresFigure(5_000.0) shouldBe "5 km"
        metresFigure(10_000.0) shouldBe "10 km"
    }

    @Test
    fun `a metric visibility round-trips through the statute miles the parser stores`() {
        // The property that makes the conversion honest: a station that reported
        // `9999` gets `10 km` back, not `9,999 m` and not `16 km`.
        metresFigure(M_9999_IN_SM * 1_609.344) shouldBe "10 km"
        metresFigure(M_9000_IN_SM * 1_609.344) shouldBe "9 km"
    }

    @Test
    fun `a US mile converted to metres claims no more precision than the report has`() {
        // 2 SM is 3,218.688 m, and printing that would claim a resolution no
        // station transmits.
        metresFigure(2.0 * 1_609.344) shouldBe "3,200 m"
    }
}

class AltimeterTextTest {

    @Test
    fun `the station's own convention decides, not the reader's unit setting`() {
        // A European field's `Q1008` shown as `29.77 inHg` is a conversion the
        // pilot has to undo before setting the subscale, and an opportunity to
        // mis-set it that the report never offered.
        altimeterText(AltimeterConvention.HECTOPASCALS, inHg = null, hectopascals = 1008.0) shouldBe "1008 hPa"
        altimeterText(AltimeterConvention.INCHES_MERCURY, inHg = 30.02, hectopascals = null) shouldBe "30.02 inHg"
    }

    @Test
    fun `a report with no altimeter group says nothing`() {
        altimeterText(convention = null, inHg = null, hectopascals = null).shouldBeNull()
        // A convention with no matching value is also nothing rather than a
        // figure taken from the other unit.
        altimeterText(AltimeterConvention.HECTOPASCALS, inHg = 30.02, hectopascals = null).shouldBeNull()
    }
}

/**
 * What the `WIND` chip decides to say, before any string is chosen.
 *
 * The defect this was written for: the chip required *both* a direction and a speed,
 * so `VRB05KT` — a null direction with `windVariable` set — rendered as no chip at
 * all. A station reporting a swinging wind was shown as a station reporting no wind,
 * which is the same class of mistake as the one that started this whole feature:
 * absence of a field read as absence of the thing.
 */
class WindReadingTest {

    @Test
    fun `a variable wind is a reading, not a gap`() {
        // `VRB05KT`. The direction is genuinely null and that is the point — the
        // wind has no settled one — so a classification keyed on the direction being
        // present drops it. It matters operationally: a variable wind is a crosswind
        // on every runway at the field.
        windReading(directionDeg = null, variable = true, speedKt = 5) shouldBe WindReading.VARIABLE
    }

    @Test
    fun `calm is decided by the speed, never by the direction`() {
        // `00000KT` parses to a direction of **0**, not to null, so a check that
        // looks at the direction first prints `0° 0 kt` — which reads as a light
        // wind out of the north. The speed has to be tested before the bearing.
        windReading(directionDeg = 0, variable = false, speedKt = 0) shouldBe WindReading.CALM
    }

    @Test
    fun `a bearing of zero degrees still cannot occur with a speed`() {
        // The other half of the same fact. A station with wind genuinely from north
        // transmits `36008KT`, not `00008KT`, so 0° with a non-zero speed is not a
        // real report — and if one ever arrives, calling it a bearing of north is
        // the harmless reading. Recorded so the branch order is not "fixed".
        windReading(directionDeg = 0, variable = false, speedKt = 8) shouldBe WindReading.BEARING
    }

    @Test
    fun `a speed with no direction and no VRB says only what it knows`() {
        windReading(directionDeg = null, variable = false, speedKt = 12) shouldBe WindReading.SPEED_ONLY
    }

    @Test
    fun `an ordinary wind is a bearing`() {
        windReading(directionDeg = 270, variable = false, speedKt = 8) shouldBe WindReading.BEARING
    }

    @Test
    fun `a bearing wins over the variable flag if both somehow arrive`() {
        // Defensive only in the sense that it pins the order: a measured direction is
        // more informative than "it varies", so it is preferred rather than dropped.
        windReading(directionDeg = 310, variable = true, speedKt = 14) shouldBe WindReading.BEARING
    }
}

/**
 * The `TEMP` chip's figure.
 *
 * `Double.toInt()` truncates toward zero, and `MetarSupplement.temperatureC` carries
 * tenths where NOAA's `T` remark group decoded them. That combination has a sign
 * error hiding in it, which is what these pin.
 */
class TemperatureTextTest {

    @Test
    fun `a fraction of a degree below zero keeps its minus sign`() {
        // The defect. Truncation gives `0°/0°C` for a field that is below freezing
        // on both figures — the sign disappears entirely, and the chip a pilot reads
        // for airframe icing and for frost says the opposite of the truth.
        temperatureText(-0.4, -0.9) shouldBe "0°/-1°C"
    }

    @Test
    fun `rounding goes to the nearest degree, not toward zero`() {
        temperatureText(-2.7, -3.2) shouldBe "-3°/-3°C"
        temperatureText(20.6, 12.4) shouldBe "21°/12°C"
    }

    @Test
    fun `a whole-degree report is unchanged`() {
        // The common case: most stations transmit whole degrees, and this must not
        // move them.
        temperatureText(21.0, 12.0) shouldBe "21°/12°C"
    }
}
