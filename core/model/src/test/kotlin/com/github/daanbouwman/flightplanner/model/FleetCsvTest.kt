package com.github.daanbouwman.flightplanner.model

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlin.test.Test

private const val HEADER =
    "manufacturer,variant,icao_code,flown,aircraft_range,category,cruise_speed,date_flown,takeoff_distance"

class FleetCsvTest {

    @Test
    fun `parses the desktop app's format`() {
        val result = FleetCsv.parse(
            """
            $HEADER
            ATR,42-600,AT46,0,703,Regional,300,,1107
            Boeing,737-800,B738,1,2935,Narrow-body,450,2026-03-01,2300
            """.trimIndent(),
        )

        result.warnings shouldHaveSize 0
        result.aircraft shouldHaveSize 2

        val atr = result.aircraft.first()
        atr.manufacturer shouldBe "ATR"
        atr.variant shouldBe "42-600"
        atr.icaoCode shouldBe "AT46"
        atr.flown shouldBe false
        atr.rangeNm shouldBe 703
        atr.cruiseSpeedKt shouldBe 300
        atr.dateFlown.shouldBeNull()
        atr.takeoffDistanceMeters shouldBe 1107

        val boeing = result.aircraft[1]
        boeing.flown shouldBe true
        boeing.dateFlown shouldBe "2026-03-01"
        boeing.displayName shouldBe "Boeing 737-800"
    }

    @Test
    fun `takeoff distance is metres and converts to a runway requirement in feet`() {
        // 1107 m of takeoff distance needs 3632 ft of runway. Getting this
        // conversion wrong silently changes which airports qualify.
        val atr = FleetCsv.parse("$HEADER\nATR,42-600,AT46,0,703,Regional,300,,1107").aircraft.single()
        atr.requiredRunwayFt shouldBe 3632
    }

    @Test
    fun `a missing takeoff distance means no runway requirement`() {
        val glider = FleetCsv.parse("$HEADER\nSchleicher,ASK 21,,0,50,Sport,60,,").aircraft.single()
        glider.takeoffDistanceMeters.shouldBeNull()
        glider.requiredRunwayFt shouldBe 0
    }

    @Test
    fun `column order does not matter and extra columns are ignored`() {
        val result = FleetCsv.parse(
            """
            aircraft_range,variant,manufacturer,notes
            703,42-600,ATR,something
            """.trimIndent(),
        )
        result.aircraft.single().manufacturer shouldBe "ATR"
        result.aircraft.single().rangeNm shouldBe 703
    }

    @Test
    fun `quoted fields containing commas survive`() {
        val result = FleetCsv.parse("$HEADER\n\"Beech, Inc\",\"18, Twin Beech\",BE18,0,1200,Vintage,150,,900")
        result.aircraft.single().manufacturer shouldBe "Beech, Inc"
        result.aircraft.single().variant shouldBe "18, Twin Beech"
    }

    @Test
    fun `a bad row is skipped with a warning instead of failing the whole import`() {
        val result = FleetCsv.parse(
            """
            $HEADER
            ATR,42-600,AT46,0,703,Regional,300,,1107
            Broken,Thing,XXXX,0,not-a-number,Regional,300,,1000
            Boeing,737-800,B738,1,2935,Narrow-body,450,,2300
            """.trimIndent(),
        )
        result.aircraft shouldHaveSize 2
        result.warnings shouldHaveSize 1
        result.warnings.single() shouldContain "Line 3"
    }

    @Test
    fun `missing required columns are reported rather than silently producing nothing`() {
        val result = FleetCsv.parse("manufacturer,variant\nATR,42-600")
        result.aircraft shouldHaveSize 0
        result.warnings.single() shouldContain "aircraft_range"
    }

    @Test
    fun `export round-trips through parse`() {
        val original = FleetCsv.parse(
            """
            $HEADER
            "Beech, Inc","18, Twin Beech",BE18,1,1200,Vintage,150,2026-01-02,900
            ATR,42-600,AT46,0,703,Regional,300,,1107
            """.trimIndent(),
        ).aircraft

        val reparsed = FleetCsv.parse(FleetCsv.write(original)).aircraft
        reparsed shouldBe original
    }
}
