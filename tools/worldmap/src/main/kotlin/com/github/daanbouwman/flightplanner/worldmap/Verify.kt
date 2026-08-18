package com.github.daanbouwman.flightplanner.worldmap

import com.github.daanbouwman.flightplanner.routing.WorldOutline
import com.github.daanbouwman.flightplanner.routing.WorldOutlineCodec
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.security.MessageDigest
import kotlin.math.abs
import kotlin.system.exitProcess

/**
 * Verifies the checked-in world outline asset.
 *
 * Wired into `check`, so it fails the build rather than the app. Three things
 * can go wrong here and none of them throws on a device:
 *
 *  1. **The asset is stale or absent.** The app would draw cards with no world
 *     under them, which looks like a design decision rather than a bug.
 *  2. **The snapshot was swapped without the asset being rebuilt.** The hash in
 *     `source.json` is what catches that; the outline itself carries no
 *     provenance a reader could check.
 *  3. **The geometry is subtly wrong** — longitude and latitude transposed, a
 *     sign flipped, a ring table off by one. Every one of those still decodes,
 *     still fills, and still looks like *a* planet. Only asking whether known
 *     places are where they belong distinguishes them, which is what the
 *     land/water probes below do.
 */
fun main(args: Array<String>) {
    val opts = Options.parse(args)

    if (!opts.output.isFile) {
        fail("World outline asset missing: ${opts.output}. Run ./gradlew :tools:worldmap:run")
    }
    if (!opts.input.isFile) {
        fail("Source snapshot missing: ${opts.input}")
    }

    val failures = mutableListOf<String>()
    fun check(condition: Boolean, message: String) {
        if (!condition) failures += message
    }

    val sourceJson = File(opts.input.parentFile, "source.json")
    if (sourceJson.isFile) {
        val declared = Json.parseToJsonElement(sourceJson.readText())
            .jsonObject["sha256"]?.jsonPrimitive?.content
        val actual = sha256(opts.input)
        check(
            declared == null || declared == actual,
            "Source snapshot does not match source.json.\n" +
                "  declared: $declared\n" +
                "  actual  : $actual\n" +
                "  Either the snapshot was refreshed without updating source.json,\n" +
                "  or source.json was updated without refreshing the snapshot.",
        )
    }

    val outline = WorldOutlineCodec.decode(opts.output.readBytes())

    check(
        outline.ringCount >= opts.minRings,
        "Outline holds ${outline.ringCount} rings, expected at least ${opts.minRings}",
    )
    check(
        opts.output.length() <= opts.maxBytes,
        "Outline is ${opts.output.length()} bytes, over the ${opts.maxBytes}-byte budget",
    )

    for (ring in 0 until outline.ringCount) {
        val from = outline.ringStart[ring]
        val to = outline.ringStart[ring + 1]
        if (to - from < 4) {
            failures += "Ring $ring has ${to - from} points, too few to enclose anything"
            continue
        }
        // A ring that does not close leaves a straight seam across the fill.
        if (outline.lon[from] != outline.lon[to - 1] || outline.lat[from] != outline.lat[to - 1]) {
            failures += "Ring $ring is not closed"
        }
    }

    val outOfRange = (0 until outline.pointCount).count { i ->
        outline.lon[i] < -180f || outline.lon[i] > 180f || outline.lat[i] < -90f || outline.lat[i] > 90f
    }
    check(outOfRange == 0, "$outOfRange coordinates fall outside the world")

    // Natural Earth splits its polygons at the antimeridian, and the projection
    // relies on that: a ring that stepped across the seam would draw a band right
    // across the map. Antarctica legitimately spans the full longitude range —
    // it runs along the -180 edge, down to the pole and back — so the test is
    // whether consecutive points *jump*, not how wide a ring is.
    // The one legitimate 360° step is Antarctica running along the pole itself,
    // from (180, -90) to (-180, -90): that segment is the bottom edge of the map,
    // and it is what closes the continent rather than a coastline at all.
    var seamJumps = 0
    for (ring in 0 until outline.ringCount) {
        val from = outline.ringStart[ring]
        val to = outline.ringStart[ring + 1]
        for (i in from + 1 until to) {
            if (abs(outline.lon[i] - outline.lon[i - 1]) <= 180f) continue
            val alongPole = abs(outline.lat[i]) == 90f && abs(outline.lat[i - 1]) == 90f
            if (!alongPole) seamJumps++
        }
    }
    check(seamJumps == 0, "$seamJumps segments step across the ±180° seam")

    for (probe in PROBES) {
        val isLand = containsPoint(outline, probe.lon, probe.lat)
        check(
            isLand == probe.land,
            "${probe.name} (${probe.lat}, ${probe.lon}) reads as ${if (isLand) "land" else "water"}, " +
                "expected ${if (probe.land) "land" else "water"}",
        )
    }

    if (failures.isEmpty()) {
        println(
            format(
                "World outline OK: %,d rings, %,d points, %,d bytes, %d probes correct",
                outline.ringCount, outline.pointCount, opts.output.length().toInt(), PROBES.size,
            ),
        )
        return
    }
    failures.forEach { System.err.println("FAILED: $it") }
    exitProcess(1)
}

/**
 * Places whose land-or-water answer is not in dispute.
 *
 * Chosen to be far from any coast at this scale — the point is to catch a
 * transposed or mirrored world, not to audit a shoreline — and spread across
 * both hemispheres in both axes, because a sign flip in one coordinate alone
 * still leaves half of these correct.
 */
private data class Probe(val name: String, val lat: Double, val lon: Double, val land: Boolean)

private val PROBES = listOf(
    Probe("the Sahara", 22.0, 15.0, land = true),
    Probe("central Siberia", 62.0, 95.0, land = true),
    Probe("the Amazon basin", -5.0, -62.0, land = true),
    Probe("the Australian interior", -24.0, 133.0, land = true),
    Probe("Kansas", 38.0, -99.0, land = true),
    Probe("East Antarctica", -78.0, 60.0, land = true),
    Probe("the central Pacific", 0.0, -150.0, land = false),
    Probe("the central Atlantic", 20.0, -40.0, land = false),
    Probe("the southern Indian Ocean", -40.0, 80.0, land = false),
    Probe("the Arctic Ocean north of Siberia", 84.0, 100.0, land = false),
    Probe("the Gulf of Guinea", 0.0, 0.0, land = false),
)

/**
 * Even-odd point-in-polygon over every ring at once.
 *
 * The same rule the renderer fills with, so this answers the question the map
 * will answer: a lake enclosed by a continent counts as water because it is
 * crossed twice.
 */
internal fun containsPoint(outline: WorldOutline, lon: Double, lat: Double): Boolean {
    var inside = false
    for (ring in 0 until outline.ringCount) {
        if (lon < outline.ringMinLon[ring] || lon > outline.ringMaxLon[ring]) continue
        if (lat < outline.ringMinLat[ring] || lat > outline.ringMaxLat[ring]) continue

        val from = outline.ringStart[ring]
        val to = outline.ringStart[ring + 1]
        var j = to - 1
        for (i in from until to) {
            val yi = outline.lat[i].toDouble()
            val yj = outline.lat[j].toDouble()
            if ((yi > lat) != (yj > lat)) {
                val xi = outline.lon[i].toDouble()
                val xj = outline.lon[j].toDouble()
                val crossing = xi + (lat - yi) / (yj - yi) * (xj - xi)
                if (lon < crossing) inside = !inside
            }
            j = i
        }
    }
    return inside
}

private fun sha256(file: File): String =
    MessageDigest.getInstance("SHA-256").digest(file.readBytes())
        .joinToString("") { "%02x".format(it) }

private fun fail(message: String): Nothing {
    System.err.println("FAILED: $message")
    exitProcess(1)
}
