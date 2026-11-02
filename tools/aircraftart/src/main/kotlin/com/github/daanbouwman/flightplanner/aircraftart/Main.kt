package com.github.daanbouwman.flightplanner.aircraftart

import com.github.daanbouwman.flightplanner.model.FleetCsv
import java.io.File
import java.util.Locale
import kotlin.system.exitProcess

/**
 * Builds the shipped aircraft silhouettes from Microsoft Flight Simulator's own
 * 3D models.
 *
 *     ./gradlew :tools:aircraftart:run --args="build"
 *     ./gradlew :tools:aircraftart:run --args="build --only ^a32"
 *     ./gradlew :tools:aircraftart:run --args="inspect <path to .gltf> [node regex]"
 *
 * `inspect` lists a model's nodes with their extents, which is how a manifest
 * block's include and exclude patterns are written. `build` runs the manifest,
 * caches each outline, and rewrites the contact sheet from every cached one.
 */
fun main(args: Array<String>) {
    when (args.firstOrNull()) {
        "inspect" -> inspect(args.drop(1))
        "build" -> build(Options.parse(args.drop(1)))
        else -> {
            System.err.println("usage: inspect <gltf> [node-regex] | build [--sim-root DIR] [--only REGEX]")
            exitProcess(2)
        }
    }
}

private fun inspect(args: List<String>) {
    val file = File(args.firstOrNull() ?: error("inspect needs a .gltf path"))
    val filter = args.getOrNull(1)?.takeIf { it.isNotEmpty() }?.let { Regex(it, RegexOption.IGNORE_CASE) }
    val restSkin = args.getOrNull(2)?.let { Regex(it, RegexOption.IGNORE_CASE) }
    val instances = Gltf.load(file).instances(restSkin)
    val all = Extent()
    for (instance in instances) {
        val e = Extent().apply { add(instance.positions) }
        all.add(instance.positions)
        if (filter != null && !filter.containsMatchIn(instance.path)) continue
        val dropped = if (Manifest.GLOBAL_EXCLUDE.containsMatchIn(instance.name) || Manifest.GEAR_PATH.containsMatchIn(instance.path) || Manifest.GROUND_EQUIPMENT_PATH.containsMatchIn(instance.path)) "-" else " "
        println(
            format(
                "%s%6d tris  x %7.2f..%7.2f  y %7.2f..%7.2f  z %7.2f..%7.2f  %s",
                dropped, instance.triangles.size / 3, e.minX, e.maxX, e.minY, e.maxY, e.minZ, e.maxZ, instance.path,
            ),
        )
    }
    println(
        format(
            "ALL %d instances  x %.2f..%.2f (%.2f)  y %.2f..%.2f (%.2f)  z %.2f..%.2f (%.2f)",
            instances.size, all.minX, all.maxX, all.maxX - all.minX, all.minY, all.maxY, all.maxY - all.minY,
            all.minZ, all.maxZ, all.maxZ - all.minZ,
        ),
    )
}

/** Command-line options for `build`. All paths default to repo-relative locations. */
data class Options(
    val simRoot: File,
    val manifest: File,
    val drawnDir: File,
    val only: Regex?,
    val buildDir: File,
    val seed: File,
    /** Where to write `AircraftShapes.kt`; null with `--table none`. */
    val table: File?,
    /** Shape ids to render side by side into look.png, for checking a batch by eye. */
    val look: List<String>,
    /** `id:x0,y0,x1,y1` (metres from the outline's top-left): render that region into zoom.png. */
    val zoom: String?,
) {
    companion object {
        fun parse(args: List<String>): Options {
            val map = HashMap<String, String>()
            var i = 0
            while (i < args.size) {
                val key = args[i]
                require(key.startsWith("--") && i + 1 < args.size) { "Unexpected argument '$key'" }
                map[key.removePrefix("--")] = args[i + 1]
                i += 2
            }
            return Options(
                simRoot = File(map["sim-root"] ?: "H:/flightsim"),
                manifest = File(map["manifest"] ?: "tools/aircraftart/data/shapes.ini"),
                drawnDir = File(map["drawn"] ?: "tools/aircraftart/data/drawn"),
                only = map["only"]?.let { Regex(it) },
                buildDir = File(map["out"] ?: "tools/aircraftart/build/silhouettes"),
                seed = File(map["seed"] ?: "app/src/main/assets/seed/aircrafts.csv"),
                table = (map["table"] ?: TABLE_PATH).takeUnless { it == "none" }?.let(::File),
                zoom = map["zoom"],
                look = map["look"]?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty(),
            )
        }
    }
}

private fun build(opts: Options) {
    val sources = Manifest.read(opts.manifest)
    val seed = FleetCsv.parse(opts.seed.readText()).aircraft
    checkManifest(sources, seed)

    val cacheDir = File(opts.buildDir, "shapes")
    var failures = 0
    for (source in sources.filter { opts.only == null || opts.only.containsMatchIn(it.id) }) {
        val built = runCatching { Silhouette.build(source, opts.simRoot, opts.drawnDir) }
            .onFailure { error ->
                failures++
                println("FAIL ${source.id}: $error")
                error.stackTrace.take(4).forEach { println("         at $it") }
            }
            .getOrNull() ?: continue
        val previous = File(cacheDir, "${source.id}.json").takeIf { it.isFile }?.let(CachedShape::read)
        val fresh = CachedShape.of(built)
        val change = when {
            previous == null -> "new"
            previous.path == fresh.path -> "unchanged"
            else -> (if (source.verified) "CHANGED (was verified: look again)" else "changed") +
                format(" - was %.2f x %.2f m, %d pts", previous.spanM, previous.lengthM, previous.points)
        }
        fresh.write(cacheDir)
        Preview.png(built.outline, File(opts.buildDir, "preview/${source.id}.png"))
        val verdict = if (built.withinTolerance) "ok  " else "OFF "
        println(
            format(
                "%s %-24s span %6.2f m (%s)  length %6.2f m (%s)  %4d pts",
                verdict, source.id, built.spanM, pct(built.spanDeviationPct), built.lengthM, pct(built.lengthDeviationPct),
                built.outline.pointCount,
            ),
        )
        println("       $change")
        built.log.forEach { println("       $it") }
    }
    if (failures > 0) println("$failures shape(s) failed")

    val ids = sources.map { it.id }.toSet()
    // An id that left the manifest (merged into another, renamed) leaves its
    // preview and cache behind, and a stale preview reads as a live defect.
    for ((dir, extension) in listOf(cacheDir to "json", File(opts.buildDir, "preview") to "png")) {
        dir.listFiles { f -> f.extension == extension && f.nameWithoutExtension !in ids }?.forEach { stale ->
            println("Removed stale ${stale.name}")
            stale.delete()
        }
    }
    val cached = CachedShape.readAll(cacheDir).filterKeys { it in ids }
    val sheet = File(opts.buildDir, "contact-sheet.html")
    ContactSheet.write(sheet, sources, cached, seed)
    println("Contact sheet: ${sheet.absolutePath} (${cached.size} of ${sources.size} outlines)")

    // Montages for looking at a batch in one image: everything, and the ones
    // whose measurements disagree with the published figures.
    val outlines = sources.mapNotNull { s -> cached[s.id]?.let { s to Outline.parse(it.path) } }
    outlines.chunked(MONTAGE_CHUNK).forEachIndexed { index, chunk ->
        Preview.montage(chunk.map { (s, o) -> s.id to o }, File(opts.buildDir, "montage-${index + 1}.png"))
    }
    val flagged = outlines.filter { (s, o) -> !Built(s, o, emptyList()).withinTolerance }
    if (flagged.isNotEmpty()) {
        Preview.montage(flagged.map { (s, o) -> s.id to o }, File(opts.buildDir, "montage-flagged.png"))
    }

    opts.zoom?.let { spec ->
        val (id, box) = spec.split(':', limit = 2)
        val region = box.split(',').map { it.trim().toDouble() }.toDoubleArray()
        Preview.zoom(Outline.parse(requireNotNull(cached[id]) { "No outline [$id] built" }.path), region, File(opts.buildDir, "zoom.png"))
    }

    if (opts.look.isNotEmpty()) {
        val items = opts.look.map { id -> id to Outline.parse(requireNotNull(cached[id]) { "No outline [$id] built" }.path) }
        Preview.montage(items, File(opts.buildDir, "look.png"), columns = minOf(3, items.size), cell = 330)
    }

    if (opts.table != null) {
        KotlinTable.write(opts.table, sources, cached, seed)
        println("Table: ${opts.table.absolutePath}")
    }
}

/** Ids are unique, every row names a real seed row, and no seed row is claimed twice. */
private fun checkManifest(sources: List<ShapeSource>, seed: List<com.github.daanbouwman.flightplanner.model.AircraftSpec>) {
    val problems = ArrayList<String>()
    sources.groupBy { it.id }.filterValues { it.size > 1 }.keys.forEach { problems += "duplicate shape id [$it]" }
    val seedKeys = seed.map { RowKey(it.manufacturer, it.variant) }.toSet()
    val claimed = HashMap<RowKey, String>()
    for (source in sources) for (row in source.rows) {
        if (row !in seedKeys) problems += "[${source.id}] row '$row' is not in the seed fleet"
        claimed.put(row, source.id)?.let { problems += "row '$row' is claimed by [$it] and [${source.id}]" }
    }
    require(problems.isEmpty()) { "Manifest problems:\n  " + problems.joinToString("\n  ") }
}

/** The generated table `:core:aircraftart` compiles. */
const val TABLE_PATH = "core/aircraftart/src/main/kotlin/com/github/daanbouwman/flightplanner/aircraftart/AircraftShapes.kt"

private const val MONTAGE_CHUNK = 36

private fun pct(value: Double?): String = value?.let { format("%+5.1f%%", it) } ?: "  n/a "

/** Axis-aligned extent of a point cloud. */
class Extent {
    var minX = Double.POSITIVE_INFINITY; var maxX = Double.NEGATIVE_INFINITY
    var minY = Double.POSITIVE_INFINITY; var maxY = Double.NEGATIVE_INFINITY
    var minZ = Double.POSITIVE_INFINITY; var maxZ = Double.NEGATIVE_INFINITY

    fun add(positions: FloatArray) {
        var i = 0
        while (i < positions.size) {
            val x = positions[i].toDouble(); val y = positions[i + 1].toDouble(); val z = positions[i + 2].toDouble()
            if (x < minX) minX = x; if (x > maxX) maxX = x
            if (y < minY) minY = y; if (y > maxY) maxY = y
            if (z < minZ) minZ = z; if (z > maxZ) maxZ = z
            i += 3
        }
    }
}

internal fun format(pattern: String, vararg args: Any?): String = String.format(Locale.ROOT, pattern, *args)
