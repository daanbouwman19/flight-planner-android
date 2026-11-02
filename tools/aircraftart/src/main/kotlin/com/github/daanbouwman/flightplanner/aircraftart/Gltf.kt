package com.github.daanbouwman.flightplanner.aircraftart

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * One mesh instance in model space: every triangle a node draws, already moved
 * by the node's world transform.
 *
 * [path] is the node's own name preceded by every ancestor's, joined by `/`, so a
 * manifest regex can drop a whole subtree ("…/GROUND_EQUIPMENT/…") as easily as
 * one node.
 */
class MeshInstance(
    val path: String,
    val name: String,
    /** x, y, z per vertex, in metres, model space. */
    val positions: FloatArray,
    /** Three vertex indices per triangle into [positions]. */
    val triangles: IntArray,
)

/**
 * The part of glTF 2.0 a top-down silhouette needs: the node tree, its
 * transforms, and the triangles every mesh draws.
 *
 * Written against what Microsoft Flight Simulator 2020 actually exports rather
 * than the whole specification:
 *
 * - **Shared buffers sliced by `ASOBO_primitive`.** Every primitive of a mesh
 *   names the same `POSITION` and `indices` accessors; which part of them it
 *   draws is in `extras.ASOBO_primitive` — `StartIndex` (into the index
 *   accessor), `PrimitiveCount` (triangles) and `BaseVertexIndex` (added to each
 *   index). Reading the accessors whole, as a stock loader would, draws every
 *   primitive's triangles once per primitive and nothing is wrong except the
 *   time it takes; reading them *without* the base vertex draws garbage.
 * - **Rigid nodes at rest, skinned meshes in bind pose.** A rigid node takes its
 *   own transforms. A skinned mesh is drawn exactly as stored — its bind pose,
 *   the airframe as it was modelled — and not deformed by its joints. Both
 *   halves of that were measured, by trying the alternative:
 *   - Deforming skins by the joints' rest pose, as a stock glTF viewer would,
 *     swept the A310's wings forward: the simulator sets those joints from the
 *     wing-flex animation every frame, and their rest transforms are
 *     placeholders.
 *   - Moving the joints into bind pose instead, so rigid parts hung off them
 *     would follow, put the C172's ailerons, flaps and elevator a metre aft of
 *     the wing: they were authored against the rest pose.
 *   So bind pose is the default and the joints' rest pose is opted into per
 *   shape (`skin`), for models like the An-225 whose wing box is stored
 *   folded short and swept into place by its joints. Where a model's rest pose
 *   is wrong for a rigid part anyway, the manifest says so for that shape
 *   (`shift`, `mirror`, `exclude`).
 * - **Only triangle lists.** Mode 4. Lines and points are debug geometry.
 */
class Gltf private constructor(
    private val json: JsonObject,
    private val buffers: List<ByteBuffer>,
    /**
     * For a model merged into another (`<MergeModel>` in an MSFS model XML):
     * the base model's world matrices by node name. A merged model carries
     * copies of the base model's skeleton nodes, and the simulator attaches each
     * one to the base node of the same name — which is how Fenix hangs one
     * engine file and one wing-tip file on three different airframes.
     */
    private val attach: Map<String, Mat4> = emptyMap(),
    /**
     * Animations to pose the model with, by name, each at a fraction of its own
     * time range — `WingFlex_Left` at 0.5 is the An-225's wing at 1 g. The
     * simulator drives some skeletons entirely from animation, and neither their
     * rest transforms nor their skins' bind pose is a pose the aircraft is ever
     * seen in.
     */
    private val poses: Map<String, Double> = emptyMap(),
) {
    private val accessors = json["accessors"]?.jsonArray ?: JsonArray(emptyList())
    private val bufferViews = json["bufferViews"]?.jsonArray ?: JsonArray(emptyList())
    private val meshes = json["meshes"]?.jsonArray ?: JsonArray(emptyList())
    private val nodes = json["nodes"]?.jsonArray ?: JsonArray(emptyList())

    /** Node transform components the posed animations set: node index to path ("translation" …) to value. */
    private val posed: Map<Int, Map<String, DoubleArray>> by lazy {
        val out = HashMap<Int, HashMap<String, DoubleArray>>()
        val animations = json["animations"]?.jsonArray ?: return@lazy out
        val found = HashSet<String>()
        for (animationElement in animations) {
            val animation = animationElement.jsonObject
            val name = animation["name"]?.jsonPrimitive?.content ?: continue
            val fraction = poses[name] ?: continue
            found += name
            val samplers = animation["samplers"]!!.jsonArray
            for (channelElement in animation["channels"]!!.jsonArray) {
                val channel = channelElement.jsonObject
                val target = channel["target"]!!.jsonObject
                val node = target["node"]?.jsonPrimitive?.int ?: continue
                val path = target["path"]!!.jsonPrimitive.content
                if (path == "weights") continue
                val sampler = samplers[channel["sampler"]!!.jsonPrimitive.int].jsonObject
                val components = if (path == "rotation") 4 else 3
                val value = sample(sampler, fraction, components)
                out.getOrPut(node) { HashMap() }[path] = value
            }
        }
        val missing = poses.keys - found
        require(missing.isEmpty()) { "No animation named ${missing.joinToString()} to pose with" }
        out
    }

    /** One sampler's value at [fraction] of its time range: linear between keys, quaternions renormalised. */
    private fun sample(sampler: JsonObject, fraction: Double, components: Int): DoubleArray {
        val times = readFloats(sampler["input"]!!.jsonPrimitive.int, 1)
        val raw = readFloats(sampler["output"]!!.jsonPrimitive.int, components)
        val interpolation = sampler["interpolation"]?.jsonPrimitive?.content ?: "LINEAR"
        // A cubic spline stores in-tangent, value, out-tangent per key; keep the values.
        val stride = if (interpolation == "CUBICSPLINE") 3 else 1
        val valueOffset = if (stride == 3) 1 else 0
        fun value(key: Int) = DoubleArray(components) { raw[(key * stride + valueOffset) * components + it].toDouble() }
        val t = times.first() + fraction * (times.last() - times.first())
        var k = 0
        while (k < times.size - 2 && times[k + 1] < t) k++
        if (times.size == 1 || interpolation == "STEP") return value(k)
        val span = (times[k + 1] - times[k]).toDouble()
        val u = if (span <= 0.0) 0.0 else ((t - times[k]) / span).coerceIn(0.0, 1.0)
        val a = value(k)
        val b = value(k + 1)
        if (components == 4 && a[0] * b[0] + a[1] * b[1] + a[2] * b[2] + a[3] * b[3] < 0) for (i in b.indices) b[i] = -b[i]
        val out = DoubleArray(components) { a[it] + (b[it] - a[it]) * u }
        if (components == 4) {
            val n = kotlin.math.sqrt(out.sumOf { it * it })
            if (n > 0) for (i in out.indices) out[i] /= n
        }
        return out
    }

    /**
     * Every drawable node, depth first, in the order the default scene lists them.
     *
     * Skinned meshes whose path matches [restSkin] are deformed by their joints'
     * rest pose; every other skinned mesh is drawn in bind pose. See the class
     * KDoc for why neither can be the rule for every model.
     */
    fun instances(restSkin: Regex? = null): List<MeshInstance> {
        val out = ArrayList<MeshInstance>()
        for (root in roots()) visit(root, "", restSkin, out)
        return out
    }

    private val skinCache = HashMap<Int, List<Mat4>>()

    /** Each joint's skinning matrix at rest: its world matrix times its inverse bind matrix. */
    private fun restSkinMatrices(skinIndex: Int): List<Mat4> = skinCache.getOrPut(skinIndex) {
        val skin = json["skins"]!!.jsonArray[skinIndex].jsonObject
        val joints = skin["joints"]!!.jsonArray.map { it.jsonPrimitive.int }
        val ibm = skin["inverseBindMatrices"]?.jsonPrimitive?.int?.let { readFloats(it, 16) }
        joints.mapIndexed { j, node ->
            val inverseBind = ibm?.let { m -> Mat4.columnMajor(DoubleArray(16) { m[j * 16 + it].toDouble() }) } ?: Mat4.IDENTITY
            (restWorld[node] ?: Mat4.IDENTITY) * inverseBind
        }
    }

    /**
     * The default scene's roots — or, for a model merged into another, every
     * parentless node. Fenix's merged files list a single node in their scene
     * (the engine file one of 127, the wing-tip file one of a pair) and the
     * simulator merges the rest anyway.
     */
    private fun roots(): List<Int> {
        val scenes = json["scenes"]?.jsonArray
        val sceneIndex = json["scene"]?.jsonPrimitive?.int ?: 0
        val sceneRoots = scenes?.getOrNull(sceneIndex)?.jsonObject?.get("nodes")?.jsonArray?.map { it.jsonPrimitive.int }
        if (sceneRoots == null || attach.isNotEmpty()) return (sceneRoots.orEmpty() + rootsWithoutScene()).distinct()
        return sceneRoots
    }

    /** World matrix of every named node, for models merged into this one. The first node of a name wins. */
    fun worldByName(): Map<String, Mat4> {
        val out = LinkedHashMap<String, Mat4>()
        nodes.forEachIndexed { index, node ->
            val name = node.jsonObject["name"]?.jsonPrimitive?.content ?: return@forEachIndexed
            restWorld[index]?.let { out.putIfAbsent(name, it) }
        }
        return out
    }

    /**
     * This model's file merged onto [base]: see [attach]. A node named by a key
     * of [grafts] hangs under the base node named by its value — for parts
     * authored around their own origin, whose attachment nothing in the files
     * states.
     */
    fun mergedOnto(base: Gltf, grafts: Map<String, String> = emptyMap()): Gltf {
        val baseWorld = base.worldByName()
        val attach = HashMap(baseWorld)
        for ((child, parent) in grafts) {
            // A shape's grafts are listed once for all its merged files; each
            // applies to the file that has the node.
            val index = nodes.indexOfFirst { it.jsonObject["name"]?.jsonPrimitive?.content == child }.takeIf { it >= 0 } ?: continue
            val parentWorld = requireNotNull(baseWorld[parent]) { "No node '$parent' in the base model to graft '$child' onto" }
            attach[child] = parentWorld * localTransform(nodes[index].jsonObject, index)
        }
        return Gltf(json, buffers, attach, poses)
    }

    /** Every node's world matrix in the rest pose its own transforms describe. */
    private val restWorld: Array<Mat4?> by lazy {
        val out = arrayOfNulls<Mat4>(nodes.size)
        fun walk(index: Int, parent: Mat4) {
            val node = nodes[index].jsonObject
            val name = node["name"]?.jsonPrimitive?.content
            val world = name?.let(attach::get) ?: (parent * localTransform(node, index))
            out[index] = world
            node["children"]?.jsonArray?.forEach { walk(it.jsonPrimitive.int, world) }
        }
        for (root in roots()) walk(root, Mat4.IDENTITY)
        out
    }

    private fun rootsWithoutScene(): List<Int> {
        val children = HashSet<Int>()
        nodes.forEach { node -> node.jsonObject["children"]?.jsonArray?.forEach { children += it.jsonPrimitive.int } }
        return nodes.indices.filter { it !in children }
    }

    private fun visit(index: Int, parentPath: String, restSkin: Regex?, out: MutableList<MeshInstance>) {
        val node = nodes[index].jsonObject
        val name = node["name"]?.jsonPrimitive?.content ?: "node$index"
        val path = if (parentPath.isEmpty()) name else "$parentPath/$name"

        node["mesh"]?.jsonPrimitive?.int?.let { meshIndex ->
            // A skinned mesh is drawn in bind pose — as stored, the node's own
            // transform ignored as the specification says — unless the shape
            // asks for its joints' rest pose. See the class KDoc.
            val skinIndex = node["skin"]?.jsonPrimitive?.int
            val skin = skinIndex?.takeIf { restSkin?.containsMatchIn(path) == true }?.let(::restSkinMatrices)
            val transform = if (skinIndex != null) Mat4.IDENTITY else restWorld[index] ?: Mat4.IDENTITY
            readMesh(meshIndex, transform, skin)?.let { (positions, triangles) ->
                out += MeshInstance(path = path, name = name, positions = positions, triangles = triangles)
            }
        }
        node["children"]?.jsonArray?.forEach { visit(it.jsonPrimitive.int, path, restSkin, out) }
    }

    private fun localTransform(node: JsonObject, index: Int): Mat4 {
        val pose = posed[index]
        if (pose == null) node["matrix"]?.jsonArray?.let { m -> return Mat4.columnMajor(DoubleArray(16) { m[it].jsonPrimitive.double }) }
        val t = pose?.get("translation") ?: node["translation"]?.jsonArray?.doubles() ?: doubleArrayOf(0.0, 0.0, 0.0)
        val r = pose?.get("rotation") ?: node["rotation"]?.jsonArray?.doubles() ?: doubleArrayOf(0.0, 0.0, 0.0, 1.0)
        val s = pose?.get("scale") ?: node["scale"]?.jsonArray?.doubles() ?: doubleArrayOf(1.0, 1.0, 1.0)
        return Mat4.trs(t, r, s)
    }

    /**
     * A mesh's triangles in model space: every vertex moved by [transform], or,
     * with [skin], by its joints' matrices weighted per vertex.
     */
    private fun readMesh(meshIndex: Int, transform: Mat4, skin: List<Mat4>? = null): Pair<FloatArray, IntArray>? {
        val primitives = meshes[meshIndex].jsonObject["primitives"]?.jsonArray ?: return null
        val positionCache = HashMap<Int, FloatArray>()
        val indexCache = HashMap<Int, IntArray>()
        val vertexOut = ArrayList<Float>()
        val triangleOut = ArrayList<Int>()
        // Model-space vertex index for each (accessor, source vertex), so a vertex
        // shared by several primitives is transformed once.
        val remap = HashMap<Long, Int>()

        for (primitiveElement in primitives) {
            val primitive = primitiveElement.jsonObject
            val mode = primitive["mode"]?.jsonPrimitive?.int ?: 4
            if (mode != 4) continue
            val positionAccessor = primitive["attributes"]?.jsonObject?.get("POSITION")?.jsonPrimitive?.int ?: continue
            val positions = positionCache.getOrPut(positionAccessor) { readFloats(positionAccessor, 3) }
            val vertexCount = positions.size / 3

            val asobo = primitive["extras"]?.jsonObject?.get("ASOBO_primitive")?.jsonObject
            val baseVertex = asobo?.get("BaseVertexIndex")?.jsonPrimitive?.int ?: 0
            val attributes = primitive["attributes"]!!.jsonObject
            val joints = skin?.let { attributes["JOINTS_0"]?.jsonPrimitive?.int }?.let { readFloats(it, 4) }
            val weights = skin?.let { attributes["WEIGHTS_0"]?.jsonPrimitive?.int }?.let { readFloats(it, 4) }
            fun place(source: Int): DoubleArray {
                val x = positions[source * 3].toDouble()
                val y = positions[source * 3 + 1].toDouble()
                val z = positions[source * 3 + 2].toDouble()
                if (skin == null || joints == null || weights == null) return transform.apply(x, y, z)
                val out = DoubleArray(3)
                var total = 0.0
                for (k in 0 until 4) {
                    val w = weights[source * 4 + k].toDouble()
                    if (w == 0.0) continue
                    val p = skin[joints[source * 4 + k].toInt()].apply(x, y, z)
                    out[0] += w * p[0]; out[1] += w * p[1]; out[2] += w * p[2]
                    total += w
                }
                if (total == 0.0) return doubleArrayOf(x, y, z)
                return doubleArrayOf(out[0] / total, out[1] / total, out[2] / total)
            }

            val indices: IntArray
            val start: Int
            val count: Int
            val indicesAccessor = primitive["indices"]?.jsonPrimitive?.int
            if (indicesAccessor != null) {
                indices = indexCache.getOrPut(indicesAccessor) { readIndices(indicesAccessor) }
                start = asobo?.get("StartIndex")?.jsonPrimitive?.int ?: 0
                val triangles = asobo?.get("PrimitiveCount")?.jsonPrimitive?.int
                count = triangles?.let { it * 3 } ?: (indices.size - start)
            } else {
                indices = IntArray(vertexCount) { it }
                start = 0
                count = vertexCount
            }

            var i = start
            val end = minOf(start + count, indices.size)
            while (i + 2 < end) {
                for (k in 0 until 3) {
                    val source = indices[i + k] + baseVertex
                    require(source in 0 until vertexCount) {
                        "Mesh $meshIndex: index $source outside $vertexCount vertices (base $baseVertex)"
                    }
                    val key = (positionAccessor.toLong() shl 32) or source.toLong()
                    val target = remap.getOrPut(key) {
                        val p = place(source)
                        vertexOut += p[0].toFloat(); vertexOut += p[1].toFloat(); vertexOut += p[2].toFloat()
                        vertexOut.size / 3 - 1
                    }
                    triangleOut += target
                }
                i += 3
            }
        }
        if (triangleOut.isEmpty()) return null
        return vertexOut.toFloatArray() to triangleOut.toIntArray()
    }

    private fun readFloats(accessorIndex: Int, components: Int): FloatArray {
        val accessor = accessors[accessorIndex].jsonObject
        val componentType = accessor["componentType"]!!.jsonPrimitive.int
        val count = accessor["count"]!!.jsonPrimitive.int
        val normalized = accessor["normalized"]?.jsonPrimitive?.content == "true"
        val (buffer, offset, stride) = view(accessor, components * componentSize(componentType))
        val out = FloatArray(count * components)
        for (v in 0 until count) {
            for (c in 0 until components) {
                val at = offset + v * stride + c * componentSize(componentType)
                out[v * components + c] = when (componentType) {
                    FLOAT -> buffer.getFloat(at)
                    SHORT -> buffer.getShort(at).toFloat().let { if (normalized) maxOf(it / 32767f, -1f) else it }
                    UNSIGNED_SHORT -> (buffer.getShort(at).toInt() and 0xFFFF).toFloat().let { if (normalized) it / 65535f else it }
                    BYTE -> buffer.get(at).toFloat().let { if (normalized) maxOf(it / 127f, -1f) else it }
                    UNSIGNED_BYTE -> (buffer.get(at).toInt() and 0xFF).toFloat().let { if (normalized) it / 255f else it }
                    else -> error("Accessor $accessorIndex: unsupported component type $componentType")
                }
            }
        }
        return out
    }

    private fun readIndices(accessorIndex: Int): IntArray {
        val accessor = accessors[accessorIndex].jsonObject
        val componentType = accessor["componentType"]!!.jsonPrimitive.int
        val count = accessor["count"]!!.jsonPrimitive.int
        val size = componentSize(componentType)
        val (buffer, offset, stride) = view(accessor, size)
        return IntArray(count) { i ->
            val at = offset + i * stride
            when (componentType) {
                UNSIGNED_INT -> buffer.getInt(at)
                UNSIGNED_SHORT -> buffer.getShort(at).toInt() and 0xFFFF
                UNSIGNED_BYTE -> buffer.get(at).toInt() and 0xFF
                else -> error("Accessor $accessorIndex: unsupported index type $componentType")
            }
        }
    }

    private data class View(val buffer: ByteBuffer, val offset: Int, val stride: Int)

    private fun view(accessor: JsonObject, elementSize: Int): View {
        val viewIndex = accessor["bufferView"]!!.jsonPrimitive.int
        val view = bufferViews[viewIndex].jsonObject
        val buffer = buffers[view["buffer"]?.jsonPrimitive?.int ?: 0]
        val offset = (view["byteOffset"]?.jsonPrimitive?.int ?: 0) + (accessor["byteOffset"]?.jsonPrimitive?.int ?: 0)
        val stride = view["byteStride"]?.jsonPrimitive?.int ?: elementSize
        return View(buffer, offset, stride)
    }

    companion object {
        private const val BYTE = 5120
        private const val UNSIGNED_BYTE = 5121
        private const val SHORT = 5122
        private const val UNSIGNED_SHORT = 5123
        private const val UNSIGNED_INT = 5125
        private const val FLOAT = 5126

        private fun componentSize(type: Int) = when (type) {
            BYTE, UNSIGNED_BYTE -> 1
            SHORT, UNSIGNED_SHORT -> 2
            UNSIGNED_INT, FLOAT -> 4
            else -> error("Unsupported component type $type")
        }

        fun load(file: File, poses: Map<String, Double> = emptyMap()): Gltf {
            val json = Json.parseToJsonElement(file.readText()).jsonObject
            val buffers = json["buffers"]?.jsonArray?.map { element ->
                val uri = element.jsonObject["uri"]?.jsonPrimitive?.content
                    ?: error("${file.name}: embedded GLB buffers are not supported")
                require(!uri.startsWith("data:")) { "${file.name}: data-URI buffers are not supported" }
                val bin = File(file.parentFile, java.net.URLDecoder.decode(uri, Charsets.UTF_8))
                require(bin.isFile) { "${file.name}: buffer ${bin.name} is missing (encrypted package?)" }
                ByteBuffer.wrap(bin.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
            } ?: emptyList()
            return Gltf(json, buffers, poses = poses)
        }

        private fun JsonArray.doubles(): DoubleArray = DoubleArray(size) { this[it].jsonPrimitive.double }
    }
}

/** A 4 × 4 affine transform, row-major in memory. Just enough to compose glTF node transforms. */
class Mat4 private constructor(private val m: DoubleArray) {

    operator fun times(other: Mat4): Mat4 {
        val r = DoubleArray(16)
        for (row in 0 until 4) for (col in 0 until 4) {
            var sum = 0.0
            for (k in 0 until 4) sum += m[row * 4 + k] * other.m[k * 4 + col]
            r[row * 4 + col] = sum
        }
        return Mat4(r)
    }

    fun apply(x: Double, y: Double, z: Double): DoubleArray = doubleArrayOf(
        m[0] * x + m[1] * y + m[2] * z + m[3],
        m[4] * x + m[5] * y + m[6] * z + m[7],
        m[8] * x + m[9] * y + m[10] * z + m[11],
    )

    companion object {
        val IDENTITY = Mat4(doubleArrayOf(1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0))

        /** glTF stores `matrix` column-major. */
        fun columnMajor(c: DoubleArray) = Mat4(DoubleArray(16) { i -> c[(i % 4) * 4 + i / 4] })

        /** translation · rotation (unit quaternion x, y, z, w) · scale, as glTF composes them. */
        fun trs(t: DoubleArray, q: DoubleArray, s: DoubleArray): Mat4 {
            val (x, y, z, w) = q.toList()
            val r = doubleArrayOf(
                1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w),
                2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w),
                2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y),
            )
            return Mat4(
                doubleArrayOf(
                    r[0] * s[0], r[1] * s[1], r[2] * s[2], t[0],
                    r[3] * s[0], r[4] * s[1], r[5] * s[2], t[1],
                    r[6] * s[0], r[7] * s[1], r[8] * s[2], t[2],
                    0.0, 0.0, 0.0, 1.0,
                ),
            )
        }
    }
}
