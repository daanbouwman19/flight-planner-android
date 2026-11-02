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
    "https://raw.githubusercontent.com/nvkelso/natural-earth-vector/master/geojson/ne_50m_land.geojson"

/**
 * The lakes, from the same release at the same scale.
 *
 * Land alone draws the Netherlands without the IJsselmeer, which is most of what
 * makes it the Netherlands at a glance; the Great Lakes, Victoria and Ladoga
 * likewise. Lakes are cut out of the land as holes — see [lakeHoles].
 */
const val LAKES_URL: String =
    "https://raw.githubusercontent.com/nvkelso/natural-earth-vector/master/geojson/ne_50m_lakes.geojson"

/** The checked-in snapshots under `tools/worldmap/data`, named as upstream names them. */
const val LAND_SNAPSHOT: String = "ne_50m_land.geojson"
const val LAKES_SNAPSHOT: String = "ne_50m_lakes.geojson"

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
    println("  lakes:      ${opts.lakes ?: "none"}")
    println("  output:     ${opts.output}")
    println("  epsilon:    ${opts.epsilon}° (Douglas–Peucker tolerance)")
    println("  min span:   ${opts.minSpan}° (rings smaller than this are dropped)")
    println()

    val source = GeoJson.readLandRings(opts.input)
    val sourcePoints = source.sumOf { it.size }

    fun simplify(ring: Ring): Ring = Simplify.closed(
        Simplify.quantise(
            Simplify.douglasPeucker(ring, opts.epsilon),
            snapLon = { WorldOutlineCodec.roundTripLon(it.toFloat()).toDouble() },
            snapLat = { WorldOutlineCodec.roundTripLat(it.toFloat()).toDouble() },
        ),
    )

    var droppedTiny = 0
    var droppedDegenerate = 0
    val kept = ArrayList<Ring>(source.size)
    for (ring in source) {
        val simplified = simplify(ring)
        when {
            simplified.size < MIN_RING_POINTS -> droppedDegenerate++
            !Simplify.isVisible(simplified, opts.minSpan) -> droppedTiny++
            else -> kept += simplified
        }
    }

    opts.lakes?.let { lakesFile ->
        val lakes = GeoJson.readLandRings(lakesFile)
            .map(::simplify)
            .filter { it.size >= MIN_RING_POINTS && Simplify.isVisible(it, opts.minSpan) }
        val holes = lakeHoles(land = assemble(kept), lakes = lakes)
        println(format("Lakes:  %,d visible -> %,d cut as holes (the rest already water)", lakes.size, holes.size))
        kept += holes
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

/**
 * The lake rings to cut out of [land]: those whose inside still reads as land.
 *
 * The renderer fills even-odd, so a ring inside land is a hole — and a ring
 * that is *already* a hole in the land polygons, as the Caspian is in Eurasia,
 * would be crossed twice and fill back in as land. Each lake is therefore
 * tested against the land before any lake is added: kept where its interior
 * is land, skipped where it is already water. An island in a lake is a ring
 * of the lake feature too, and reads as land by the same test, so it is kept
 * and the even-odd fill puts it back above the water.
 */
internal fun lakeHoles(land: WorldOutline, lakes: List<Ring>): List<Ring> =
    lakes.filter { lake ->
        val point = interiorPoint(lake) ?: return@filter false
        containsPoint(land, point.first, point.second)
    }

/**
 * A point strictly inside [ring] — its vertex centroid where that falls inside,
 * as it does for nearly every lake, otherwise the first midpoint of a chord from
 * its first vertex that does. Null for a ring with no such chord, which is too
 * thin to matter.
 */
internal fun interiorPoint(ring: Ring): Pair<Double, Double>? {
    val candidates = buildList {
        add(ring.lon.average() to ring.lat.average())
        for (fraction in listOf(0.5, 0.33, 0.67, 0.25, 0.75)) {
            val k = (ring.size * fraction).toInt().coerceIn(1, ring.size - 1)
            add((ring.lon[0] + ring.lon[k]) / 2 to (ring.lat[0] + ring.lat[k]) / 2)
        }
    }
    return candidates.firstOrNull { (lon, lat) -> ringContains(ring, lon, lat) }
}

/** Even-odd point-in-polygon for one ring. */
private fun ringContains(ring: Ring, lon: Double, lat: Double): Boolean {
    var inside = false
    var j = ring.size - 1
    for (i in 0 until ring.size) {
        val yi = ring.lat[i]
        val yj = ring.lat[j]
        if ((yi > lat) != (yj > lat)) {
            val crossing = ring.lon[i] + (lat - yi) / (yj - yi) * (ring.lon[j] - ring.lon[i])
            if (lon < crossing) inside = !inside
        }
        j = i
    }
    return inside
}

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
    /** The lakes cut out of the land; null leaves the land as it is. See [lakeHoles]. */
    val lakes: File?,
    val output: File,
    /**
     * Douglas–Peucker tolerance in degrees.
     *
     * 0.01°, about 1.1 km: at the closest a card zooms — a 15° window across a
     * 328 dp card at 3×, 66 px a degree — that is under a pixel, so nothing the
     * screen can show is lost. It was 0.05° over the 1:110m source, tuned for a
     * coast that stayed *identifiable*; at the 15° floor that coast was a few
     * straight strokes and the Netherlands was not identifiable at all.
     */
    val epsilon: Double,
    /**
     * Rings whose whole extent is below this are dropped. See [Simplify.isVisible].
     *
     * 0.05°, three pixels at the closest zoom: the Wadden islands and the
     * Zeeland islands stay, a skerry goes. It was 0.75°, which dropped every
     * island smaller than Mallorca.
     */
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
                input = File(map["input"] ?: File(dataDir, LAND_SNAPSHOT).path),
                lakes = (map["lakes"] ?: File(dataDir, LAKES_SNAPSHOT).path)
                    .takeUnless { it == "none" }
                    ?.let(::File),
                output = File(map["out"] ?: OUTLINE_ASSET_PATH),
                epsilon = map["epsilon"]?.toDouble() ?: 0.01,
                minSpan = map["min-span"]?.toDouble() ?: 0.05,
                // Raised deliberately from 40 KiB with the move to 1:50m. Still a
                // rounding error next to the airport database, and the budget is
                // here so the next change to the tolerance is a decision too.
                maxBytes = map["max-bytes"]?.toInt() ?: 400 * 1024,
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
