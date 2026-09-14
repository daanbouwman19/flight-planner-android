package com.github.daanbouwman.flightplanner.ui

import com.github.daanbouwman.flightplanner.model.AltimeterConvention
import com.github.daanbouwman.flightplanner.settings.UnitSystem
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeInRange
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

/**
 * The aircraft forms' conversions, both ways round.
 *
 * The stored units are NM, kt and metres; the forms show and take the active
 * unit system's. Reopening a sheet must show the figures the airframe has, and
 * saving a sheet nobody edited must not move them -- that is the stored ->
 * display -> stored direction, exact everywhere. The typed direction is allowed
 * to come back off by up to half a stored unit where the typed unit is the
 * finer one, and these pin what that is in each display unit.
 */
class UnitConversionRoundTripTest {

    @Test
    fun `under Aviation, range and cruise are the stored units and pass straight through`() {
        for (value in listOf(1, 300, 2_935, 7_200)) {
            nmToDisplayDistance(value, UnitSystem.AVIATION) shouldBe value
            displayDistanceToNm(value, UnitSystem.AVIATION) shouldBe value
            ktToDisplaySpeed(value, UnitSystem.AVIATION) shouldBe value
            displaySpeedToKt(value, UnitSystem.AVIATION) shouldBe value
        }
    }

    @Test
    fun `under Metric, a stored range or cruise survives display and back unchanged`() {
        // Every value a fleet could plausibly hold, not a sample: the claim is
        // "exact", and a boundary that drifts would drift at one value in 1,852.
        for (nm in 1..8_000) {
            displayDistanceToNm(nmToDisplayDistance(nm, UnitSystem.METRIC), UnitSystem.METRIC) shouldBe nm
        }
        for (kt in 1..700) {
            displaySpeedToKt(ktToDisplaySpeed(kt, UnitSystem.METRIC), UnitSystem.METRIC) shouldBe kt
        }
    }

    @Test
    fun `under Metric, a typed range or cruise comes back within one unit`() {
        // The direction that can drift: a kilometre is finer than the nautical
        // mile it is stored as, so what was typed reopens rounded to the NM.
        for (km in 1..15_000) {
            val back = nmToDisplayDistance(displayDistanceToNm(km, UnitSystem.METRIC), UnitSystem.METRIC)
            back shouldBeInRange (km - 1)..(km + 1)
        }
        // And the drift is real, not a theoretical bound: 1,001 km is 540.5 NM,
        // stores as 540, and reopens as 1,000 km.
        nmToDisplayDistance(displayDistanceToNm(1_001, UnitSystem.METRIC), UnitSystem.METRIC) shouldBe 1_000
    }

    @Test
    fun `takeoff distance is exact under Metric, the stored unit, in both directions`() {
        for (meters in listOf(1, 450, 2_000, 3_500)) {
            takeoffMetersToDisplayLength(meters, UnitSystem.METRIC) shouldBe meters
            displayLengthToTakeoffMeters(meters, UnitSystem.METRIC) shouldBe meters
        }
    }

    @Test
    fun `under Aviation, a stored takeoff distance survives feet and back unchanged`() {
        for (meters in 1..5_000) {
            displayLengthToTakeoffMeters(
                takeoffMetersToDisplayLength(meters, UnitSystem.AVIATION),
                UnitSystem.AVIATION,
            ) shouldBe meters
        }
        // The same figure the hero chip shows, by construction.
        takeoffMetersToDisplayLength(2_000, UnitSystem.AVIATION) shouldBe 6_562
    }

    @Test
    fun `under Aviation, a typed takeoff distance comes back within half a metre`() {
        // Half a metre is 1.64 ft, so the bound is two feet rather than one: the
        // metre is the coarsest stored unit relative to its display unit. From
        // two feet, because one foot is the case the floor below owns.
        for (feet in 2..16_000) {
            val back = takeoffMetersToDisplayLength(displayLengthToTakeoffMeters(feet, UnitSystem.AVIATION), UnitSystem.AVIATION)
            back shouldBeInRange (feet - 2)..(feet + 2)
        }
        // And it does reach two: 5 ft is 1.5 m, stores as 2 m, reopens as 7 ft.
        takeoffMetersToDisplayLength(displayLengthToTakeoffMeters(5, UnitSystem.AVIATION), UnitSystem.AVIATION) shouldBe 7
    }

    @Test
    fun `a positive typed takeoff distance never stores as no requirement`() {
        // 1 ft is 0.3 m and rounds to 0, which AircraftSpec reads as "no
        // requirement" -- the opposite of a figure somebody went to the trouble
        // of typing.
        displayLengthToTakeoffMeters(1, UnitSystem.AVIATION) shouldBeGreaterThan 0
    }
}
