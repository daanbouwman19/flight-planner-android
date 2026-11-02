package com.github.daanbouwman.flightplanner.aircraftart

import com.github.daanbouwman.flightplanner.aircraftart.AircraftShape.Provenance
import com.github.daanbouwman.flightplanner.model.AircraftSpec
import com.github.daanbouwman.flightplanner.model.FleetCsv
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.io.File
import kotlin.math.abs
import kotlin.test.Test

/**
 * The generated table against the fleet the app actually seeds.
 *
 * These stand in for a verify task: `AircraftShapes.kt` is generated on a
 * machine with the simulator, CI has neither, and what can go wrong between the
 * two — a seed row renamed without regenerating, a path truncated, a span
 * written against the wrong outline — all shows up here.
 */
class AircraftArtTest {

    private val seed: List<AircraftSpec> by lazy {
        val path = System.getProperty("flightplanner.seedCsv") ?: error("flightplanner.seedCsv is not set")
        FleetCsv.parse(File(path).readText()).aircraft
    }

    @Test
    fun `every seed row has an outline of its own`() {
        val missing = seed.filter { spec ->
            AircraftShapes.rows[AircraftArt.rowKey(spec.manufacturer, spec.variant)] == null
        }.map { it.displayName }
        missing.shouldBeEmpty()

        val onFallback = seed.filter { AircraftArt.shapeFor(it).provenance == Provenance.Fallback }.map { it.displayName }
        onFallback.shouldBeEmpty()
    }

    @Test
    fun `a hand-added airframe resolves by its ICAO type designator`() {
        val shape = AircraftArt.shapeFor(custom(icaoCode = "b738", category = "Whatever"))
        shape.id shouldBe AircraftShapes.icaoDefaults.getValue("B738")
        shape.provenance shouldNotBe Provenance.Fallback
    }

    @Test
    fun `an unknown designator falls back by category, ignoring case`() {
        val shape = AircraftArt.shapeFor(custom(icaoCode = "XXXX", category = " narrow-BODY "))
        shape.id shouldBe AircraftShapes.categories.getValue("narrow-body")
        shape.provenance shouldBe Provenance.Fallback
    }

    @Test
    fun `nothing known falls back to the last resort`() {
        AircraftArt.shapeFor(custom(icaoCode = "", category = "Spaceship")).id shouldBe AircraftShapes.LAST_RESORT
    }

    @Test
    fun `no two outlines share an id`() {
        val duplicates = AircraftShapes.all.groupBy { it.id }.filterValues { it.size > 1 }.keys
        duplicates.shouldBeEmpty()
    }

    @Test
    fun `every table entry names an outline that exists`() {
        val ids = AircraftShapes.all.map { it.id }.toSet()
        val dangling = (AircraftShapes.rows.values + AircraftShapes.icaoDefaults.values + AircraftShapes.categories.values)
            .filter { it !in ids }
        dangling.shouldBeEmpty()
        (AircraftShapes.LAST_RESORT in ids) shouldBe true
    }

    @Test
    fun `every path parses and fills exactly its declared box`() {
        for (shape in AircraftShapes.all) {
            val box = bounds(shape.pathData)
            abs(box[0]).shouldBeLessThanOrEqual(1)
            abs(box[1]).shouldBeLessThanOrEqual(1)
            abs(box[2] - shape.spanCm).shouldBeLessThanOrEqual(1)
            abs(box[3] - shape.lengthCm).shouldBeLessThanOrEqual(1)
        }
    }

    /** minX, minY, maxX, maxY of a path in the generator's dialect: M, m, l and z with integer operands. */
    private fun bounds(path: String): IntArray {
        val tokens = Regex("""[MmLlZz]|-?\d+""").findAll(path).map { it.value }.toList()
        require(tokens.isNotEmpty() && tokens[0] == "M") { "path must start with an absolute move: ${path.take(20)}" }
        var x = 0; var y = 0; var startX = 0; var startY = 0
        var minX = Int.MAX_VALUE; var minY = Int.MAX_VALUE; var maxX = Int.MIN_VALUE; var maxY = Int.MIN_VALUE
        var command = 'M'
        var i = 0
        while (i < tokens.size) {
            val t = tokens[i]
            if (t[0].isLetter()) {
                command = t[0]
                if (command == 'z' || command == 'Z') { x = startX; y = startY }
                i++
                continue
            }
            val dx = t.toInt(); val dy = tokens[i + 1].toInt()
            i += 2
            when (command) {
                'M' -> { x = dx; y = dy; startX = x; startY = y; command = 'L' }
                'm' -> { x += dx; y += dy; startX = x; startY = y; command = 'l' }
                'l' -> { x += dx; y += dy }
                'L' -> { x = dx; y = dy }
                else -> error("unexpected command $command")
            }
            minX = minOf(minX, x); minY = minOf(minY, y); maxX = maxOf(maxX, x); maxY = maxOf(maxY, y)
        }
        return intArrayOf(minX, minY, maxX, maxY)
    }

    private fun custom(icaoCode: String, category: String) = AircraftSpec(
        id = 0,
        manufacturer = "Homebuilt",
        variant = "One-off",
        icaoCode = icaoCode,
        flown = false,
        rangeNm = 500,
        category = category,
        cruiseSpeedKt = 120,
        dateFlown = null,
        takeoffDistanceMeters = 400,
        isCustom = true,
    )
}
