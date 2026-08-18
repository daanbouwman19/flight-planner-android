package com.github.daanbouwman.flightplanner.worldmap

import com.github.daanbouwman.flightplanner.routing.WorldOutline
import com.github.daanbouwman.flightplanner.routing.WorldOutlineCodec
import java.io.File
import kotlin.system.exitProcess

/**
 * Where the snapshot in `tools/worldmap/data` came from.
 *
 * Natural Earth is **public domain**: nothing to attribute, nothing to add to a
 * licences screen. That is the reason to prefer it over an OpenStreetMap
 * coastline extract for this job, which would oblige the app to carry ODbL
 * attribution for a silhouette drawn at 8 % opacity.
 */
const val SOURCE_URL: String =
    "https://raw.githubusercontent.com/nvkelso/natural-earth-vector/master/geojson/ne_110m_land.geojson"

/** Name of the shipped outline asset, shared with the app. */
const val OUTLINE_ASSET_PATH: String = "app/src/main/assets/maps/land.outline"

/**
 * Builds the shipped world outline from the Natural Earth land polygons.
 *
 * Run manually rather than as part of `assemble`:
 *
 *     ./gradlew :tools:worldmap:run
 *
 * The snapshot is checked in and read from disk rather than fetched here, for
 * the same reason `:tools:airportdb` checks in its CSVs: a build that reaches
 * the network is not reproducible and makes CI depend on a third party. The
 * output is a pure function of the input, so regenerating it without changing
 * the snapshot produces no diff.
 */
fun main(args: Array<String>) {
    val opts = Options.parse(args)

    println("Flight Planner — world outline builder")
    println("  input:      ${opts.input}")
    println("  output:     ${opts.output}")
    println("  epsilon:    ${opts.epsilon}° (Douglas–Peucker tolerance)")
    println("  min span:   ${opts.minSpan}° (rings smaller than this are dropped)")
    println()

    val source = GeoJson.readLandRings(opts.input)
    val sourcePoints = source.sumOf { it.size }

    var droppedTiny = 0
    var droppedDegenerate = 0
    val kept = ArrayList<Ring>(source.size)
    for (ring in source) {
        val simplified = Simplify.closed(
            Simplify.quantise(
                Simplify.douglasPeucker(ring, opts.epsilon),
                snapLon = { WorldOutlineCodec.roundTripLon(it.toFloat()).toDouble() },
                snapLat = { WorldOutlineCodec.roundTripLat(it.toFloat()).toDouble() },
            ),
        )
        when {
            simplified.size < MIN_RING_POINTS -> droppedDegenerate++
            !Simplify.isVisible(simplified, opts.minSpan) -> droppedTiny++
            else -> kept += simplified
        }
    }

    val outline = assemble(kept)
    val bytes = WorldOutlineCodec.encode(outline)
    opts.output.parentFile.mkdirs()
    opts.output.writeBytes(bytes)

    val largest = kept.maxByOrNull { it.size }
    println(
        format(
            "Rings:  %,d in -> %,d out (%,d below %.2f deg, %,d degenerate)",
            source.size, kept.size, droppedTiny, opts.minSpan, droppedDegenerate,
        ),
    )
    println(
        format(
            "Points: %,d in -> %,d out (%.1f%% kept)",
            sourcePoints, outline.pointCount, 100.0 * outline.pointCount / sourcePoints,
        ),
    )
    println(format("Largest ring: %,d points", largest?.size ?: 0))
    println(format("Wrote %s (%,d bytes, %.1f KiB)", opts.output, bytes.size, bytes.size / 1024.0))

    // The whole design rests on this file staying a rounding error next to the
    // 6 MB airport database. A tolerance change that quietly tripled it would
    // otherwise only show up as a bigger APK.
    if (bytes.size > opts.maxBytes) {
        System.err.println(
            "FAILED: outline is ${bytes.size} bytes, over the ${opts.maxBytes}-byte budget. " +
                "Raise --epsilon or --min-span, or raise the budget deliberately.",
        )
        exitProcess(1)
    }
    // And a coastline that collapsed to nothing would still encode, still load,
    // and simply draw an empty planet.
    if (outline.ringCount < opts.minRings) {
        System.err.println(
            "FAILED: only ${outline.ringCount} rings survived, expected at least ${opts.minRings}. " +
                "The upstream format may have changed.",
        )
        exitProcess(1)
    }
}

/** Fewer than a triangle cannot enclose anything, so it cannot be filled. */
private const val MIN_RING_POINTS = 4 // three corners plus the repeated closing point

/** Flattens the kept rings into the columns the codec writes. */
fun assemble(rings: List<Ring>): WorldOutline {
    val total = rings.sumOf { it.size }
    val lon = FloatArray(total)
    val lat = FloatArray(total)
    val ringStart = IntArray(rings.size + 1)

    var out = 0
    rings.forEachIndexed { index, ring ->
        ringStart[index] = out
        for (i in 0 until ring.size) {
            lon[out] = ring.lon[i].toFloat()
            lat[out] = ring.lat[i].toFloat()
            out++
        }
    }
    ringStart[rings.size] = out
    return WorldOutline(lon = lon, lat = lat, ringStart = ringStart)
}

data class Options(
    val input: File,
    val output: File,
    /**
     * Douglas–Peucker tolerance in degrees.
     *
     * 0.05° is about 5.5 km, which is a third of a pixel at the closest a card
     * ever zooms and invisible at every wider window. Tuned against the output:
     * it removes four fifths of the source's points while leaving the North Sea,
     * the Aegean and the Great Lakes recognisable, which is the shape of the test
     * — a coast that stays *identifiable*, not one that stays accurate.
     */
    val epsilon: Double,
    /** Rings whose whole extent is below this are dropped. See [Simplify.isVisible]. */
    val minSpan: Double,
    val maxBytes: Int,
    val minRings: Int,
) {
    companion object {
        fun parse(args: Array<String>): Options {
            val map = HashMap<String, String>()
            var i = 0
            while (i < args.size) {
                val key = args[i]
                require(key.startsWith("--")) { "Unexpected argument '$key'" }
                require(i + 1 < args.size) { "Missing value for $key" }
                map[key.removePrefix("--")] = args[i + 1]
                i += 2
            }

            val dataDir = File(map["data-dir"] ?: "tools/worldmap/data")
            return Options(
                input = File(map["input"] ?: File(dataDir, "ne_110m_land.geojson").path),
                output = File(map["out"] ?: OUTLINE_ASSET_PATH),
                epsilon = map["epsilon"]?.toDouble() ?: 0.05,
                minSpan = map["min-span"]?.toDouble() ?: 0.75,
                maxBytes = map["max-bytes"]?.toInt() ?: 40 * 1024,
                minRings = map["min-rings"]?.toInt() ?: 50,
            )
        }
    }
}

/**
 * Formats a report line in a fixed locale.
 *
 * `String.format` without one groups digits the way the *build machine* is
 * configured to, so the same build prints "5,143" here and "5.143" there. A
 * build log is a thing people compare across machines.
 */
internal fun format(template: String, vararg args: Any?): String =
    String.format(java.util.Locale.ROOT, template, *args)
