package com.github.daanbouwman.flightplanner.aircraftart

import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * A finished outline, in centimetres, nose up.
 *
 * The origin is the top-left of the outline's own bounding box, so x runs
 * `0..spanCm` from the aircraft's left wing tip as seen from above and y runs
 * `0..lengthCm` from the nose. One unit is a centimetre of the real aircraft:
 * true scale is the data, not a property a renderer has to be told.
 */
class Outline(val rings: List<IntArray>) {
    companion object {
        /** Reads back what [pathData] wrote. */
        fun parse(path: String): Outline {
            val rings = ArrayList<IntArray>()
            var current = ArrayList<Int>()
            var cx = 0
            var cy = 0
            var command = 'M'
            val tokens = Regex("""[MmLlZz]|-?\d+""").findAll(path).map { it.value }.toList()
            var i = 0
            while (i < tokens.size) {
                val t = tokens[i]
                if (t[0].isLetter()) {
                    command = t[0]
                    if (command == 'z' || command == 'Z') {
                        if (current.isNotEmpty()) rings += current.toIntArray()
                        cx = current.getOrElse(0) { cx }; cy = current.getOrElse(1) { cy }
                        current = ArrayList()
                    }
                    i++
                    continue
                }
                val x = t.toInt(); val y = tokens[i + 1].toInt()
                i += 2
                if (command.isLowerCase()) { cx += x; cy += y } else { cx = x; cy = y }
                current += cx; current += cy
            }
            if (current.isNotEmpty()) rings += current.toIntArray()
            return Outline(rings)
        }
    }

    val spanCm: Int = rings.maxOf { r -> (0 until r.size / 2).maxOf { r[it * 2] } }
    val lengthCm: Int = rings.maxOf { r -> (0 until r.size / 2).maxOf { r[it * 2 + 1] } }
    val pointCount: Int get() = rings.sumOf { it.size / 2 }

    /** SVG path data with relative moves, the form both SVG and Compose's `PathParser` read. */
    fun pathData(): String = buildString {
        var cx = 0
        var cy = 0
        for (ring in rings) {
            val n = ring.size / 2
            append(if (isEmpty()) "M" else "m")
            append(ring[0] - cx).append(' ').append(ring[1] - cy)
            cx = ring[0]; cy = ring[1]
            append('l')
            for (i in 1 until n) {
                val x = ring[i * 2]; val y = ring[i * 2 + 1]
                if (i > 1) append(' ')
                append(x - cx).append(' ').append(y - cy)
                cx = x; cy = y
            }
            append('z')
            // `z` returns the current point to the ring's start.
            cx = ring[0]; cy = ring[1]
        }
    }
}

/** What the pipeline reports about one shape besides the outline itself. */
class Built(val source: ShapeSource, val outline: Outline, val log: List<String>) {
    val spanM: Double get() = outline.spanCm / 100.0
    val lengthM: Double get() = outline.lengthCm / 100.0
    val spanDeviationPct: Double? get() = source.publishedSpanM?.let { (spanM - it) / it * 100 }
    val lengthDeviationPct: Double? get() = source.publishedLengthM?.let { (lengthM - it) / it * 100 }
    val withinTolerance: Boolean
        get() = listOfNotNull(spanDeviationPct, lengthDeviationPct).all { abs(it) <= source.tolerancePct }
}

/**
 * Turns a manifest block into an outline: gather what is seen from above,
 * rasterise it, clean it, trace it.
 *
 * Every shape goes through the same raster stage whatever its source, so a
 * drawn outline is cleaned and simplified by exactly the rules a traced one is
 * and the two cannot be told apart by their line quality.
 *
 * Model space is Microsoft Flight Simulator's glTF convention, checked against
 * the C172: **x is lateral with +x the left wing, y is up, z is forward.** Seen
 * from above with the nose at the top of the screen, the left wing is on the
 * left, so screen x is −x and screen y is −z. Drawn outlines are authored in
 * screen orientation directly (see [Drawn]).
 */
object Silhouette {

    /** Pixels along the longer of span and length. Two to three times what any screen draws it at. */
    private const val MAJOR_PX = 3200

    /** Pixels along the major axis for the pass that only finds where the aircraft is. */
    private const val COARSE_PX = 800

    /** Closing radius, as a fraction of the major axis: wide enough for a hinge gap. See [Raster.close]. */
    private const val CLOSE_FRACTION = 0.0025

    /** Opening radius, as a fraction of the major axis. See [Raster.open]. */
    private const val OPEN_FRACTION = 0.0012

    /** Douglas–Peucker tolerance, as a fraction of the major axis: about a pixel at hero size. */
    private const val SIMPLIFY_FRACTION = 0.0008

    /** Rings smaller than this fraction of the raster are specks, not parts. */
    private const val MIN_RING_FRACTION = 0.00015

    /** Thickness of a propeller seen edge-on, as a fraction of the major axis. */
    private const val PROP_BAR_FRACTION = 0.012

    /** How far either side of a propeller's axis its still mesh is kept, as a fraction of the diameter: the spinner. */
    private const val SPINNER_FRACTION = 0.1

    /** A drawn rotor blade's chord, as a fraction of the rotor's diameter. */
    private const val ROTOR_CHORD_FRACTION = 0.03

    /** Background kept around the outline beyond the morphology radii. */
    private const val PAD_MARGIN_PX = 6

    /** How far past the centreline the kept half still counts, for a fuselage that is not quite centred. */
    private const val MIRROR_SLACK_M = 0.05

    fun build(source: ShapeSource, simRoot: File, drawnDir: File): Built {
        val log = ArrayList<String>()
        // A drawn shape may also name models: their geometry is traced and the
        // drawn polygons added to it — for a model whose rigid parts can be
        // trusted and whose skinned parts cannot (the An-225's wing).
        val tracedScene = if (source.models.isNotEmpty()) traced(source, simRoot, log) else null
        val drawnScene = source.drawn?.let { Drawn.read(File(drawnDir, it)) }
        require(source.provenance != Provenance.Drawn || drawnScene != null) { "[${source.id}] is Drawn but names no 'drawn' file" }
        val scene = when {
            tracedScene != null && drawnScene != null -> Scene(
                tracedScene.triangles,
                tracedScene.polygons + drawnScene.polygons,
                tracedScene.discs + drawnScene.discs,
            )
            else -> tracedScene ?: drawnScene ?: error("[${source.id}] names neither a model nor a drawn outline")
        }
        val outline = rasterise(scene, source.mirror, log)
        return Built(source, outline, log)
    }

    /** Everything seen from above, in screen metres: x right, y aft. */
    class Scene(
        /** Three vertices per triangle: x0 y0 x1 y1 x2 y2. */
        val triangles: DoubleArray,
        val polygons: List<Pair<DoubleArray, DoubleArray>>,
        val discs: List<Disc>,
    )

    enum class DiscKind { Prop, Rotor }

    /**
     * A propeller or rotor disc centred at screen ([x], [y]) metres.
     *
     * Seen from above a propeller is edge-on — a bar its diameter wide — and a
     * rotor is the whole disc.
     */
    data class Disc(
        val kind: DiscKind,
        val x: Double,
        val y: Double,
        val diameter: Double,
        /** Model y (height) of the hub, for measuring blades in the disc's plane. */
        val height: Double = 0.0,
        /** For a rotor: how many blades to draw, and the first one's angle from the nose, clockwise. */
        val blades: Int = 0,
        val angleDeg: Double = 0.0,
        /** Sized from the still blades rather than taken from the blur mesh's bounding box. */
        val measured: Boolean = false,
    )

    /** Still propeller blades, matched on the node path; a spinner, cone, hub or shaft is not a blade. */
    private val BLADE = Regex("still|blade", RegexOption.IGNORE_CASE)
    private val NOT_BLADE = Regex("spinner|cone|hub|shaft|frost|blur|slow", RegexOption.IGNORE_CASE)

    /**
     * What a blade can be measured on: anything still, frost and blur aside.
     * Wider than [NOT_BLADE] allows for dropping, because some models keep their
     * blades inside the spinner's mesh (the SR22's `nose_cone_still`) — they can
     * be measured there, but dropping that mesh would take the spinner with it.
     */
    private val NOT_MEASURABLE = Regex("frost|blur|slow", RegexOption.IGNORE_CASE)

    /**
     * Sizes each propeller from the still blades around its hub.
     *
     * A simulator's blurred-propeller disc is drawn for the effect, not to
     * scale: the Spitfire's is 3.49 m against blades spanning 3.26 m — the real
     * Mk IX's 10 ft 9 in — and the Fokker F.VII's 4.2 m against 3.1 m. The
     * blades are the measurement; the disc only says where the hub is.
     */
    private fun sizedFromBlades(disc: Disc, blades: List<MeshInstance>): Disc {
        if (disc.kind != DiscKind.Prop) return disc
        val hubX = -disc.x
        val hubZ = -disc.y
        val reach = disc.diameter / 2
        // A blade grows out of its own hub, so its footprint reaches the hub. One
        // that does not belongs to another engine: the Stratoliner's 5.4 m side
        // blur reaches the next engine's blades, which measured its propellers at
        // 6 m.
        val margin = 0.15 * reach
        val own = blades.filter { blade ->
            val e = Extent().apply { add(blade.positions) }
            hubX in e.minX - margin..e.maxX + margin &&
                disc.height in e.minY - margin..e.maxY + margin &&
                hubZ in e.minZ - margin..e.maxZ + margin
        }
        var radius = 0.0
        for (blade in own) {
            val p = blade.positions
            var i = 0
            while (i < p.size) {
                val x = p[i].toDouble(); val y = p[i + 1].toDouble(); val z = p[i + 2].toDouble()
                if (kotlin.math.abs(z - hubZ) < 0.4 * reach) {
                    val r = kotlin.math.hypot(x - hubX, y - disc.height)
                    if (r < 1.1 * reach && r > radius) radius = r
                }
                i += 3
            }
        }
        // Blur discs run oversized by a quarter at most (the Fokker's 3.1 m blades
        // in a 4.2 m disc); a measurement well inside that caught only part of
        // the blades, and the disc is the better figure.
        return if (radius > 0.65 * reach) disc.copy(diameter = radius * 2, measured = true) else disc
    }

    private fun traced(source: ShapeSource, simRoot: File, log: MutableList<String>): Scene {
        val discs = ArrayList<Disc>()
        val triangles = ArrayList<Double>()
        val autoProps = "noautoprops" !in source.extras
        val keepGear = "keepgear" in source.extras
        var instances = 0
        require(source.models.isNotEmpty()) { "[${source.id}] names no model" }
        fun load(path: String): Gltf {
            val file = File(simRoot, path)
            require(file.isFile) { "[${source.id}] model not found: $file" }
            return Gltf.load(file, source.poses)
        }
        val models = source.models.map(::load)
        val merged = source.merges.map { load(it).mergedOnto(models.first(), source.grafts) }
        val all = (models + merged).flatMap { it.instances(source.restSkin) }
        val largest = all.maxOfOrNull { horizontalExtent(it) } ?: 0.0
        val considered = all.filter { instance ->
            (source.include == null || source.include.containsMatchIn(instance.path)) &&
                (source.exclude == null || !source.exclude.containsMatchIn(instance.path))
        }
        // Propellers: hubs from the blurred discs, diameters from the still
        // blades, and then the blades themselves dropped — a propeller is drawn
        // edge-on as a bar, and blades left in at whatever angle the modeller
        // parked them stick out of it.
        val blades = considered.filter { BLADE.containsMatchIn(it.path) && !NOT_BLADE.containsMatchIn(it.name) }
        val measurable = considered.filter { BLADE.containsMatchIn(it.path) && !NOT_MEASURABLE.containsMatchIn(it.name) }
        if (autoProps) considered.mapNotNullTo(discs) { blurDisc(it)?.let { disc -> sizedFromBlades(disc, measurable) } }
        discs += source.extras.mapNotNull(::explicitDisc)
        val propBlades = blades.filter { blade ->
            val e = Extent().apply { add(blade.positions) }
            val cx = -(e.minX + e.maxX) / 2
            val cy = -(e.minZ + e.maxZ) / 2
            discs.any { it.kind == DiscKind.Prop && kotlin.math.hypot(cx - it.x, cy - it.y) < 0.6 * it.diameter }
        }.toSet()
        for (instance in considered) {
            // A propeller's still mesh often carries its spinner too: keep the
            // part close to the axis — spinner, hub, blade roots — and drop what
            // reaches out sideways. Dropping the whole mesh cost the TBM half a
            // metre of nose.
            val hub = if (instance in propBlades) {
                val e = Extent().apply { add(instance.positions) }
                val cx = -(e.minX + e.maxX) / 2
                discs.filter { it.kind == DiscKind.Prop }.minByOrNull { abs(it.x - cx) }
            } else {
                null
            }
            if (Manifest.GLOBAL_EXCLUDE.containsMatchIn(instance.name)) continue
            if (!keepGear && Manifest.GEAR_PATH.containsMatchIn(instance.path) &&
                horizontalExtent(instance) < Manifest.GEAR_MAX_FRACTION * largest
            ) {
                continue
            }
            if (Manifest.GROUND_EQUIPMENT_PATH.containsMatchIn(instance.path)) continue
            if (Manifest.LIGHT.containsMatchIn(instance.name) &&
                horizontalExtent(instance) < Manifest.LIGHT_MAX_FRACTION * largest
            ) {
                continue
            }
            instances++
            val shift = source.shifts.firstOrNull { it.pattern.containsMatchIn(instance.path) }
            val dx = shift?.dx ?: 0.0
            val dz = shift?.dz ?: 0.0
            val plugs = source.plugs.filter { it.pattern == null || it.pattern.containsMatchIn(instance.path) }
            val p = instance.positions
            val tri = instance.triangles
            for (i in tri.indices step 3) {
                if (hub != null) {
                    val reach = SPINNER_FRACTION * hub.diameter
                    val sideways = (0 until 3).any { k -> abs(-p[tri[i + k] * 3] - hub.x) > reach }
                    if (sideways) continue
                }
                for (k in 0 until 3) {
                    val v = tri[i + k]
                    var z = p[v * 3 + 2] + dz
                    for (plug in plugs) {
                        if (plug.fore && z > plug.station) z -= plug.metres
                        if (!plug.fore && z < plug.station) z += plug.metres
                    }
                    triangles += -(p[v * 3] + dx)
                    triangles += -z
                }
            }
        }
        require(triangles.isNotEmpty()) { "[${source.id}] no geometry left after include/exclude" }
        val unique = dedupe(discs)
        unique.forEach { log += format("%s at model x=%.2f z=%.2f, d=%.2f m", it.kind, -it.x, -it.y, it.diameter) }
        log += format("%d mesh instances, %d triangles", instances, triangles.size / 6)
        return Scene(triangles.toDoubleArray(), emptyList(), unique)
    }

    /**
     * Twice: once coarse to find where the aircraft is, once fine inside that.
     *
     * The extent of the drawn geometry is not the extent of the aircraft. A
     * single stray vertex — the Staggerwing has a two-triangle rivet 26 m behind
     * its tail — stretched the raster to three times the aircraft's length, so
     * it was traced at a third of the resolution and its propeller bar, sized to
     * the raster, came out three times too thick. The coarse pass's significant
     * rings drop such specks, and their bounds frame the fine pass.
     */
    private fun rasterise(scene: Scene, mirror: Side?, log: MutableList<String>): Outline {
        val coarse = pass(scene, mirror, bounds = null, majorPx = COARSE_PX)
        val b = coarse.bounds
        val margin = 0.01 * max(b[1] - b[0], b[3] - b[2])
        val fine = pass(scene, mirror, doubleArrayOf(b[0] - margin, b[1] + margin, b[2] - margin, b[3] + margin), MAJOR_PX)
        log += fine.summary
        return fine.outline
    }

    /** One pass's outline, the bounds of its significant rings in scene metres (minX, maxX, minY, maxY), and a log line. */
    private class Pass(val outline: Outline, val bounds: DoubleArray, val summary: String)

    private fun pass(scene: Scene, mirror: Side?, bounds: DoubleArray?, majorPx: Int): Pass {
        // Screen x is the aircraft's own left negative: its left wing is drawn
        // on the left. Mirroring keeps that half (or the other) and reflects it.
        fun kept(x: Double) = when (mirror) {
            null -> true
            Side.Left -> x <= MIRROR_SLACK_M
            Side.Right -> x >= -MIRROR_SLACK_M
        }
        var minX = Double.POSITIVE_INFINITY; var maxX = Double.NEGATIVE_INFINITY
        var minY = Double.POSITIVE_INFINITY; var maxY = Double.NEGATIVE_INFINITY
        fun extend(x: Double, y: Double) {
            if (!kept(x)) return
            minX = min(minX, x); maxX = max(maxX, x); minY = min(minY, y); maxY = max(maxY, y)
        }
        val t = scene.triangles
        val discs = if (mirror == null) scene.discs else scene.discs.filter { kept(it.x) }.flatMap {
            if (abs(it.x) < MIRROR_SLACK_M) listOf(it) else listOf(it, it.copy(x = -it.x))
        }
        if (bounds != null) {
            minX = bounds[0]; maxX = bounds[1]; minY = bounds[2]; maxY = bounds[3]
        } else {
            for (i in t.indices step 2) extend(t[i], t[i + 1])
            for ((xs, ys) in scene.polygons) for (i in xs.indices) extend(xs[i], ys[i])
            for (d in discs) {
                val r = d.diameter / 2
                extend(d.x - r, d.y - if (d.kind == DiscKind.Rotor) r else 0.0)
                extend(d.x + r, d.y + if (d.kind == DiscKind.Rotor) r else 0.0)
            }
        }
        if (mirror != null) {
            val half = max(abs(minX), abs(maxX))
            minX = -half
            maxX = half
        }
        val major = max(maxX - minX, maxY - minY)
        val scale = majorPx / major
        // Clear of both morphology radii with room to spare. The closing dilates
        // first and erodes back, and the erosion can only measure distance to
        // background that exists inside the image: a pad no wider than the
        // closing radius left every outline that reached the edge fattened by up
        // to twice that radius — sixteen pixels, half a percent of the span.
        val pad = kotlin.math.ceil((CLOSE_FRACTION + OPEN_FRACTION) * majorPx).toInt() + PAD_MARGIN_PX
        // When mirroring, x = 0 falls exactly on the boundary between the two
        // middle columns, so column c reflects onto width - 1 - c with no seam.
        val halfWidth = kotlin.math.ceil((maxX - minX) / 2 * scale).toInt() + pad
        val raster = Raster(halfWidth * 2, ((maxY - minY) * scale).toInt() + pad * 2)
        val centreX = (minX + maxX) / 2
        fun px(x: Double) = (x - centreX) * scale + halfWidth
        fun py(y: Double) = (y - minY) * scale + pad

        for (i in t.indices step 6) {
            raster.fillTriangle(px(t[i]), py(t[i + 1]), px(t[i + 2]), py(t[i + 3]), px(t[i + 4]), py(t[i + 5]))
        }
        for ((xs, ys) in scene.polygons) {
            raster.fillPolygon(DoubleArray(xs.size) { px(xs[it]) }, DoubleArray(ys.size) { py(ys[it]) })
        }
        if (mirror != null) raster.reflect(keepLeft = mirror == Side.Left)
        raster.close(CLOSE_FRACTION * majorPx)
        raster.open(OPEN_FRACTION * majorPx)

        // Discs go in after the clean-up: a propeller edge-on is thinner than
        // the opening radius at some scales, and it is drawn to be seen.
        val bar = PROP_BAR_FRACTION * majorPx
        for (d in discs) {
            val cx = px(d.x); val cy = py(d.y); val r = d.diameter / 2 * scale
            when (d.kind) {
                DiscKind.Prop -> raster.fillRect(cx - r, cy - bar / 2, cx + r, cy + bar / 2)
                DiscKind.Rotor -> {
                    // Each blade a bar from the hub to the tip, its chord a
                    // fraction of the diameter — about the real ratio.
                    val half = max(bar, d.diameter * ROTOR_CHORD_FRACTION * scale) / 2
                    for (b in 0 until d.blades) {
                        val a = Math.toRadians(d.angleDeg + b * 360.0 / d.blades)
                        val ux = kotlin.math.sin(a); val uy = -kotlin.math.cos(a)
                        val px = -uy; val py = ux
                        raster.fillPolygon(
                            doubleArrayOf(cx + px * half, cx + ux * r + px * half, cx + ux * r - px * half, cx - px * half),
                            doubleArrayOf(cy + py * half, cy + uy * r + py * half, cy + uy * r - py * half, cy - py * half),
                        )
                    }
                }
            }
        }

        val rings = Trace.rings(raster)
        val area = raster.width.toDouble() * raster.height
        val significant = rings.filter { abs(it.signedArea()) >= MIN_RING_FRACTION * area }
        val simplified = significant.map { Simplify.closed(it, SIMPLIFY_FRACTION * majorPx) }.filter { it.size >= 3 }

        var ox = Double.POSITIVE_INFINITY; var oy = Double.POSITIVE_INFINITY
        for (r in simplified) for (i in 0 until r.size) { ox = min(ox, r.x[i]); oy = min(oy, r.y[i]) }
        val cmPerPx = 100.0 / scale
        val cmRings = simplified
            .sortedByDescending { abs(it.signedArea()) }
            .map { r ->
                val out = ArrayList<Int>(r.size * 2)
                for (i in 0 until r.size) {
                    val x = ((r.x[i] - ox) * cmPerPx).roundToInt()
                    val y = ((r.y[i] - oy) * cmPerPx).roundToInt()
                    if (out.size >= 2 && out[out.size - 2] == x && out[out.size - 1] == y) continue
                    out += x; out += y
                }
                out.toIntArray()
            }
            .filter { it.size >= 6 }
        var bx0 = Double.POSITIVE_INFINITY; var bx1 = Double.NEGATIVE_INFINITY
        var by0 = Double.POSITIVE_INFINITY; var by1 = Double.NEGATIVE_INFINITY
        for (r in simplified) for (i in 0 until r.size) {
            val x = (r.x[i] - halfWidth) / scale + centreX
            val y = (r.y[i] - pad) / scale + minY
            bx0 = min(bx0, x); bx1 = max(bx1, x); by0 = min(by0, y); by1 = max(by1, y)
        }
        return Pass(
            Outline(cmRings),
            doubleArrayOf(bx0, bx1, by0, by1),
            format("raster %dx%d, %d rings traced, %d kept", raster.width, raster.height, rings.size, cmRings.size),
        )
    }

    /**
     * A blurred-propeller mesh is a flat disc of the propeller's true diameter:
     * thin along the direction of flight, round across it. That is a better
     * measurement of the propeller than the still blades, which sit at whatever
     * angle the modeller left them. A rotor's blur disc is the same, lying flat.
     */
    private fun blurDisc(instance: MeshInstance): Disc? {
        if (!Regex("blur", RegexOption.IGNORE_CASE).containsMatchIn(instance.name)) return null
        // A "slow" blur is one blade's smear, not the disc: the Beaver's three
        // read as half-size propellers either side of the real one.
        if (Regex("cone|spinner|hub|wheel|tire|tyre|slow", RegexOption.IGNORE_CASE).containsMatchIn(instance.name)) return null
        val e = Extent().apply { add(instance.positions) }
        val dx = e.maxX - e.minX; val dy = e.maxY - e.minY; val dz = e.maxZ - e.minZ
        val cx = -(e.minX + e.maxX) / 2
        val cy = -(e.minZ + e.maxZ) / 2
        // Side blurs count too, and slightly oval discs (a three-blade blur is not
        // round in its bounding box): the blades size the propeller afterwards,
        // so the disc only has to say where the hub is.
        val across = max(dx, dy)
        if (across > 0.3 && dz < 0.25 * across && abs(dx - dy) < 0.3 * across) {
            return Disc(DiscKind.Prop, cx, cy, across, height = (e.minY + e.maxY) / 2)
        }
        // A rotor's blur disc is not drawn: from above a rotor is its blades,
        // and a ring round the aircraft read as an artefact, not a helicopter.
        // Where the model's still blades are spread they are drawn as modelled;
        // where they rest stacked, the manifest draws them (`rotor(…, n, angle)`).
        return null
    }

    /** The longer side of a mesh's footprint seen from above, in metres. */
    private fun horizontalExtent(instance: MeshInstance): Double {
        val e = Extent().apply { add(instance.positions) }
        return max(e.maxX - e.minX, e.maxZ - e.minZ)
    }

    /** `prop(x,z,d)` or `rotor(x,z,d,blades[,angle])`, in model metres and degrees from the nose, clockwise. */
    private fun explicitDisc(extra: String): Disc? {
        val match = Regex("""(prop|rotor)\(([^)]*)\)""").matchEntire(extra.replace(" ", "")) ?: return null
        val kind = match.groupValues[1]
        val args = match.groupValues[2].split(',').map { it.toDouble() }
        require(args.size >= 3) { "'$extra' needs x, z and a diameter" }
        if (kind == "prop") return Disc(DiscKind.Prop, -args[0], -args[1], args[2])
        require(args.size >= 4) { "'$extra' needs a blade count" }
        return Disc(DiscKind.Rotor, -args[0], -args[1], args[2], blades = args[3].toInt(), angleDeg = args.getOrElse(4) { 0.0 })
    }

    /**
     * The same disc often exists twice — a blurred and a "very blurred" mesh, or
     * a front blur and a side blur. Keep one per position: one the blades sized
     * if there is one, since a side blur can run half as large again as the
     * propeller (the DC-3's is 5.79 m round blades spanning 3.4 m), and the
     * largest otherwise.
     */
    private fun dedupe(discs: List<Disc>): List<Disc> {
        val out = ArrayList<Disc>()
        for (d in discs.sortedWith(compareByDescending<Disc> { it.measured }.thenByDescending { it.diameter })) {
            val near = out.any { it.kind == d.kind && abs(it.x - d.x) < 0.25 * it.diameter && abs(it.y - d.y) < 0.25 * it.diameter }
            if (!near) out += d
        }
        return out.sortedWith(compareBy({ it.y }, { it.x }))
    }
}

/**
 * A hand-drawn outline, for the few airframes whose models cannot be read.
 *
 * Authored in metres in screen orientation — x to the right of the centreline
 * as seen from above, y aft from the nose — from published dimensions and
 * three-view drawings:
 *
 *     # comment
 *     poly  x,y x,y x,y …        a polygon
 *     mirror x,y x,y …           a polygon and its mirror image across x = 0
 *     prop  x,y,d                a propeller edge-on (mirror with "mprop")
 *     rotor x,y,d,n[,a]          a rotor of n blades, the first at a degrees from the nose
 *
 * Only the right half of a symmetric part is written, with `mirror`, so the two
 * sides cannot disagree.
 */
object Drawn {

    fun read(file: File): Silhouette.Scene {
        require(file.isFile) { "Drawn outline not found: $file" }
        val polygons = ArrayList<Pair<DoubleArray, DoubleArray>>()
        val discs = ArrayList<Silhouette.Disc>()
        file.readLines().forEachIndexed { index, raw ->
            val line = raw.substringBefore('#').trim()
            if (line.isEmpty()) return@forEachIndexed
            val parts = line.split(Regex("\\s+"))
            val points = parts.drop(1).map { p -> p.split(',').map { it.toDouble() } }
            fun where() = "${file.name}:${index + 1}"
            when (parts[0]) {
                "poly", "mirror" -> {
                    require(points.size >= 3 && points.all { it.size == 2 }) { "${where()}: a polygon needs three or more x,y points" }
                    val xs = DoubleArray(points.size) { points[it][0] }
                    val ys = DoubleArray(points.size) { points[it][1] }
                    polygons += xs to ys
                    if (parts[0] == "mirror") polygons += DoubleArray(xs.size) { -xs[it] } to ys.copyOf()
                }
                "prop", "mprop" -> {
                    require(points.size == 1 && points[0].size == 3) { "${where()}: expected x,y,d" }
                    val (x, y, d) = points[0]
                    discs += Silhouette.Disc(Silhouette.DiscKind.Prop, x, y, d)
                    if (parts[0] == "mprop") discs += Silhouette.Disc(Silhouette.DiscKind.Prop, -x, y, d)
                }
                "rotor" -> {
                    require(points.size == 1 && points[0].size in 4..5) { "${where()}: expected x,y,d,blades[,angle]" }
                    val p = points[0]
                    discs += Silhouette.Disc(
                        Silhouette.DiscKind.Rotor, p[0], p[1], p[2],
                        blades = p[3].toInt(), angleDeg = p.getOrElse(4) { 0.0 },
                    )
                }
                else -> error("${where()}: unknown directive '${parts[0]}'")
            }
        }
        return Silhouette.Scene(DoubleArray(0), polygons, discs)
    }
}
