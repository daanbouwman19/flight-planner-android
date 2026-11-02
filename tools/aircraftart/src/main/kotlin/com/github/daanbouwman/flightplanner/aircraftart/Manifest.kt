package com.github.daanbouwman.flightplanner.aircraftart

import java.io.File

/** Where a shape's geometry came from. Mirrors `AircraftShape.Provenance` in `:core:aircraftart`. */
enum class Provenance { Traced, Derived, Drawn, Fallback }

/**
 * One block of `tools/aircraftart/data/shapes.ini`: one distinct outline, how to
 * build it, and which seed rows are drawn with it.
 */
data class ShapeSource(
    val id: String,
    val label: String,
    val provenance: Provenance,
    /** Model files relative to the sim root. Several are drawn together — an airframe, its engines, its wing tips. */
    val models: List<String>,
    /** Models merged onto the first of [models], node by node name — MSFS `<MergeModel>`. */
    val merges: List<String>,
    /**
     * For merged models whose parts are authored around their own origin: the
     * merged node named by the key hangs under the base node named by the value.
     */
    val grafts: Map<String, String>,
    /** Node paths must match this to be drawn. Absent draws everything not excluded. */
    val include: Regex?,
    /** Node paths matching this are dropped, on top of [Manifest.GLOBAL_EXCLUDE]. */
    val exclude: Regex?,
    val publishedSpanM: Double?,
    val publishedLengthM: Double?,
    /** Where the published figures come from. */
    val figuresSource: String,
    /** Allowed deviation from the published figures, in percent. */
    val tolerancePct: Double,
    /**
     * Extra geometry and switches: `prop(x,z,d)` adds a propeller seen edge-on at
     * model x, z with diameter d (metres); `rotor(x,z,d)` a rotor disc;
     * `noautoprops` turns off blur-disc detection.
     */
    val extras: List<String>,
    /** For [Provenance.Drawn]: the outline file under `data/drawn/`. */
    val drawn: String?,
    /** For [Provenance.Fallback]: the free-text categories this outline stands in for. */
    val fallbackFor: List<String>,
    /** Seed rows drawn with this outline, as `Manufacturer / Variant`. */
    val rows: List<RowKey>,
    val notes: String,
    /**
     * Corrections for parts a model places wrongly in its static pose: every
     * node whose path matches is moved by (dx, dy, dz) model metres. Each one is
     * a measured error in the model, and its block says why.
     */
    val shifts: List<Shift>,
    /**
     * Keep one half of the model and reflect it across the centreline. For a
     * model whose rig is consistent on one side only — an airframe is
     * symmetric from directly above, its rig sometimes is not.
     */
    val mirror: Side?,
    /**
     * Skinned meshes to deform by their joints' rest pose rather than draw in
     * bind pose — for a model that stores skinned parts flattened and relies on
     * its joints to sweep them into place.
     */
    val restSkin: Regex?,
    /** Animations to pose the model with, each at a fraction of its time range: `pose = WingFlex_Left 0.5`. */
    val poses: Map<String, Double>,
    /**
     * Fuselage sections removed, for an outline derived from a stretched or
     * shortened sibling: Boeing builds the 737-600 from the -700's airframe less
     * a plug ahead of the wing and one behind it.
     */
    val plugs: List<Plug>,
    /** Set once the outline has been looked at, alone and full size, and its figures checked. */
    val verified: Boolean,
)

/** The aircraft's own left or right, as a pilot sitting in it would say. */
enum class Side { Left, Right }

/**
 * Remove [metres] of fuselage at model station [station] (z, metres): with
 * [fore], everything ahead of the station moves aft; otherwise everything behind
 * it moves forward. Only nodes matching [pattern] move, when it is given — an
 * outboard wing that reaches behind an aft plug's station stays where it is.
 */
data class Plug(val fore: Boolean, val station: Double, val metres: Double, val pattern: Regex?)

/** Move the nodes matching [pattern] by ([dx], [dy], [dz]) metres in model space. */
data class Shift(val pattern: Regex, val dx: Double, val dy: Double, val dz: Double)

/** A seed fleet row, by the two fields that identify it. */
data class RowKey(val manufacturer: String, val variant: String) {
    override fun toString() = "$manufacturer / $variant"
}

object Manifest {

    /**
     * Nodes no silhouette ever wants, matched against the node's own name.
     *
     * Blurred and slow propellers (the still one stays, and the disc is added
     * back edge-on — see [Silhouette]), frost overlays, light cones and lenses,
     * effect anchors, decals, and the ground equipment a parked model carries:
     * chocks, covers, tie-downs, ladders, flags.
     * And spoilers and Krueger flaps: they lie inside the wing's planform, or
     * under its leading edge, whatever their pose,
     * so they can only ever add an artefact — and several models park them, in
     * their static pose, somewhere other than on the wing.
     *
     * Not crew or passengers by name: they sit inside the outline whatever
     * happens to them, and "passenger" is how the A310 names its fuselage.
     */
    val GLOBAL_EXCLUDE = Regex(
        "blur|frost|_slow|slow_|^fx_|registration|wire|chock|cover|tie_?down|ladder|" +
            "remove_?before|flag|gpu|gse|stairs|^ground|_ground|character|spoiler|krueger|time_of_day|light_?block|_bent|prop[^/]*side|jeep|flame|collision",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Subtrees no silhouette wants, matched against the whole node path: the
     * landing gear. It sits under the wing or the fuselage, so it never shapes an
     * outline seen from above, and it is the one part whose pose a model's rest
     * transforms cannot be trusted for — the simulator places it through the gear
     * animation, and the C172's nose-gear pivot rested 1.3 m ahead of the
     * propeller. `extra = keepgear` keeps it, for skids that do show.
     */
    val GEAR_PATH = Regex("gear|landing|wheel|tire|tyre", RegexOption.IGNORE_CASE)

    /**
     * A gear-path mesh larger than this fraction of the largest mesh is airframe,
     * not gear: the Spirit of St. Louis hangs its whole body under an armature
     * root called `Armature_gear_suspensions`.
     */
    const val GEAR_MAX_FRACTION = 0.35

    /**
     * Ground equipment, matched against the whole node path so a parent takes its
     * subtree with it: the Stratoliner parks a fuel truck off its nose and a set
     * of steps — with suitcases and a cabin attendant — by its door; the E-Jets
     * open their doors onto airstairs behind a barrier ribbon; the MD-11 has an
     * air start unit at its nose and the C-47 a power cord. Luggage and baggage
     * *doors* are airframe, not equipment: dropping the DC-3's closed luggage
     * door cut a hole in its fuselage.
     */
    val GROUND_EQUIPMENT_PATH = Regex(
        "steps|stair|suitcase|(lug|bag)gage(?!_?door)|attendant|truck|vehicle|tug|dolly|(?<![a-z])cart(?![a-z])|" +
            "grnd|ribbon|barrier|barier|(?<![a-z])asu(?![a-z])|air_?start|power_?cord|tow_?(bar|car)|jeep|mech_?stand|oil_?pan|drip_?pan",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Lights — lenses, glows and the light cones some models carry, up to three
     * metres long on the A320's tail — matched by name, and dropped only when
     * small beside the airframe. AIG names meshes after their material, and its
     * MAX 8's whole fuselage and wing is a mesh called `cabin_lights_VC`.
     */
    val LIGHT = Regex("(?<!f)light", RegexOption.IGNORE_CASE)

    /** A light-named mesh smaller than this fraction of the largest mesh is a light, not airframe. */
    const val LIGHT_MAX_FRACTION = 0.2

    const val DEFAULT_TOLERANCE_PCT = 2.0

    private val LIST_KEYS = setOf("model", "merge", "graft", "row", "extra", "fallback", "shift", "pose", "plug")

    /**
     * Reads the manifest: `[shape_id]` opens a block, `key = value` lines fill
     * it, and `model`, `row`, `extra` and `fallback` may repeat. `#` starts a
     * comment line.
     */
    fun read(file: File): List<ShapeSource> {
        val blocks = ArrayList<Pair<String, MutableMap<String, MutableList<String>>>>()
        file.readLines().forEachIndexed { index, raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEachIndexed
            if (line.startsWith("[") && line.endsWith("]")) {
                blocks += line.substring(1, line.length - 1).trim() to LinkedHashMap()
                return@forEachIndexed
            }
            val eq = line.indexOf('=')
            require(eq > 0 && blocks.isNotEmpty()) { "${file.name}:${index + 1}: expected 'key = value' inside a [block]" }
            val key = line.substring(0, eq).trim()
            val value = line.substring(eq + 1).trim()
            val block = blocks.last().second
            require(key in LIST_KEYS || key !in block) { "${file.name}:${index + 1}: '$key' given twice in [${blocks.last().first}]" }
            block.getOrPut(key) { ArrayList() } += value
        }
        return blocks.map { (id, b) ->
            fun one(key: String) = b[key]?.single()?.takeIf { it.isNotEmpty() }
            ShapeSource(
                id = id,
                label = one("label") ?: error("[$id] has no label"),
                provenance = Provenance.valueOf(one("provenance") ?: "Traced"),
                models = b["model"].orEmpty(),
                merges = b["merge"].orEmpty(),
                grafts = b["graft"].orEmpty().associate { line ->
                    val parts = line.trim().split(" ").filter { it.isNotEmpty() }
                    require(parts.size == 2) { "[$id] graft '$line' is not 'child parent'" }
                    parts[0] to parts[1]
                },
                include = one("include")?.let { Regex(it, RegexOption.IGNORE_CASE) },
                exclude = one("exclude")?.let { Regex(it, RegexOption.IGNORE_CASE) },
                publishedSpanM = one("span")?.toDouble(),
                publishedLengthM = one("length")?.toDouble(),
                figuresSource = one("figures").orEmpty(),
                tolerancePct = one("tolerance")?.toDouble() ?: DEFAULT_TOLERANCE_PCT,
                extras = b["extra"].orEmpty(),
                drawn = one("drawn"),
                fallbackFor = b["fallback"].orEmpty(),
                rows = b["row"].orEmpty().map { row ->
                    val parts = row.split(" / ", limit = 2)
                    require(parts.size == 2) { "[$id] row '$row' is not 'Manufacturer / Variant'" }
                    RowKey(parts[0].trim(), parts[1].trim())
                },
                notes = one("notes").orEmpty(),
                verified = one("verified") == "yes",
                poses = b["pose"].orEmpty().associate { line ->
                    val parts = line.trim().split(" ").filter { it.isNotEmpty() }
                    require(parts.size == 2) { "[$id] pose '$line' is not 'animation fraction'" }
                    parts[0] to parts[1].toDouble()
                },
                plugs = b["plug"].orEmpty().map { line ->
                    val parts = line.trim().split(" ").filter { it.isNotEmpty() }
                    require(parts.size in 3..4 && parts[0] in setOf("fore", "aft")) { "[$id] plug '$line' is not 'fore|aft station metres [regex]'" }
                    Plug(parts[0] == "fore", parts[1].toDouble(), parts[2].toDouble(), parts.getOrNull(3)?.let { Regex(it, RegexOption.IGNORE_CASE) })
                },
                restSkin = one("skin")?.let { Regex(it, RegexOption.IGNORE_CASE) },
                mirror = one("mirror")?.let { m -> Side.entries.single { it.name.equals(m, ignoreCase = true) } },
                shifts = b["shift"].orEmpty().map { line ->
                    val parts = line.split(Regex("""\s+"""))
                    require(parts.size == 4) { "[$id] shift '$line' is not 'pattern dx dy dz'" }
                    Shift(Regex(parts[0], RegexOption.IGNORE_CASE), parts[1].toDouble(), parts[2].toDouble(), parts[3].toDouble())
                },
            )
        }
    }
}
