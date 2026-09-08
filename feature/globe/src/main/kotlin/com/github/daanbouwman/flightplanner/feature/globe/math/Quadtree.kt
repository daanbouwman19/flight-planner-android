package com.github.daanbouwman.flightplanner.feature.globe.math

import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.asinh
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sinh
import kotlin.math.sqrt

/**
 * Screen-space-error driven tile selection, ported from the Rust original.
 *
 * Instead of picking one global level of detail and enumerating every tile
 * inside the visible lat/lon box — which explodes combinatorially at high zoom
 * and fetches the horizon at nadir resolution — the visible set is found by
 * walking the slippy-map quadtree from the root: a tile subdivides only while
 * its projected screen size exceeds a threshold *and* it survives horizon and
 * viewport culling. That naturally yields high detail at the nadir, coarse tiles
 * at the horizon, and a tile count bounded by what is actually on screen.
 *
 * ### The ceiling is the provider's
 *
 * `maxLod` is a parameter, passed by the scene from the loader's provider. It
 * used to be a constant of 8 here and a separate guard in the loader, and the
 * two could disagree silently. The traversal does not know which provider is
 * live and does not need to; it is handed a number, and this package stays pure.
 *
 * ### The budget is the atlas's, and it is structural
 *
 * Every visited node is requested — not only the leaves — and every requested
 * node above the pinned levels takes an atlas slot when it lands. The old budget
 * counted leaves, so a frame at a phone-sized viewport asked for 238 distinct
 * tiles above the pinned floor against an atlas with 171 slots to give: on a
 * parked camera the atlas evicted about six tiles a frame, forever, the loader
 * never ran out of work, the render loop never settled, and the victims were
 * the z4–z7 ancestors — the fallback pyramid itself.
 *
 * Subdivision is now gated on the count of non-pinned nodes this traversal has
 * requested plus everything still queued, against the slots the atlas can
 * actually evict. That makes "the atlas can hold this frame" a property of the
 * traversal rather than a hope. The leaf cap is kept at the reference's 256, but
 * the atlas constraint is the one that binds.
 *
 * ### Order of spending, which is the whole design
 *
 * At `MAX_TILT` a correct traversal wants roughly **1,960** non-pinned tiles
 * against the atlas's **235** evictable slots — an eight-fold shortfall. Which
 * tiles get those slots therefore decides what the frame looks like, and this is
 * the one part of the traversal the reference is no guide for: it has no atlas
 * and gates on leaf count alone.
 *
 * **Breadth-first spends them uniformly**, and that is exactly wrong. When the
 * budget runs out at level *k* every remaining branch stops at level *k*, so the
 * near field — which is closest and has earned the most detail — is truncated to
 * the same depth as the horizon. The nodes are therefore held in a max-heap
 * ordered by [TraversalFrame.priority] and the budget is spent largest-first: the
 * near field keeps its depth and the horizon stays coarse, which is what a tilted
 * view should look like. See `NodeHeap`.
 *
 * ### Incidence orders the spend; it does not gate the split
 *
 * A tile seen edge-on at the limb covers fewer pixels than its chord suggests,
 * and this used to scale the **split** estimate by the cosine of the viewing
 * angle at the tile's centre. That was a defect, and the paragraph arguing for it
 * refuted itself in its own last sentence: it kept the *unscaled* estimate for
 * the viewport margin "because that margin is about the tile's extent on screen,
 * which incidence does not shrink" — and the split test is equally an extent
 * test. Foreshortening compresses one screen axis; the texel budget is set by the
 * uncompressed one, so multiplying an isotropic extent by `cos θ` is
 * dimensionally wrong.
 *
 * What it did in practice: at the screen centre the incidence is exactly
 * `cos(tilt)`, so at `MAX_TILT` it was 0.309 and the whole view lost two LOD
 * levels — a four-fold blur that swept in from the horizon as the user tilted.
 * [INCIDENCE_FLOOR] never even engaged. The term had been introduced as a
 * *demand reducer* for the atlas, not as a resolution feature.
 *
 * So the split test is the reference's again — raw [TraversalFrame.screenPx],
 * which `chord` already takes as `max(ewArc, nsArc)`, the maximum on-screen
 * extent — and the incidence factor moves to [TraversalFrame.priority], where it
 * decides the order the scarce atlas slots are spent in. It is a bad predictor of
 * a tile's screen *extent* and a good one of its screen *area*, which is the
 * question a budget actually asks.
 *
 * Simply deleting it would have made things **worse**, not better: demand jumps
 * to 1,960 against 235 slots and breadth-first truncation then takes the median
 * leaf from z8 down to z7. The metric and the spend order had to change together.
 *
 * ### Prefetch
 *
 * A leaf that is nearly ready to split — more than [PREFETCH_FRACTION] of the
 * way to the threshold — is the tile a zoom is about to want four children of.
 * They are handed to a separate callback after the traversal, within whatever
 * atlas capacity this frame's visible set left over and never more than
 * [PREFETCH_CAP], so a prefetch can neither evict a visible tile nor delay one.
 *
 * ### Order
 *
 * The leaves come back sorted by `(z, x, y)`, **not** back to front as the
 * reference does. Tiles are one primitive drawn without depth writes, they do
 * not overlap except at sub-pixel bulge slivers, and the coarse and sharp layers
 * of one tile are ordered by emission within the buffer — so a depth sort buys
 * nothing visible. What it cost was real: the scene's mesh signature is
 * order-sensitive, and the sort changed the order on 49 of 60 frames of a slow
 * pan when the visible *set* changed on 7.
 *
 * Breadth-first order happened to be a function of the visible set, which is what
 * the signature needs. **A priority order is not** — it is a function of
 * continuous float priorities, so a sub-pixel camera move could reorder two
 * near-ties and force a rebuild of geometry that did not change. Sorting by key
 * makes the order a pure function of the set, which is strictly stronger than
 * what the FIFO gave for free, and keeps coarse tiles emitted before fine ones.
 *
 * The requests themselves go out in traversal order too. Coarse-before-fine
 * *fetching* is the loader's queue's responsibility, not this function's; what a
 * test can and does hold this to is that every ancestor of every leaf is among
 * the requests.
 */
internal object Quadtree {

    /** Native tile texture edge, pixels. */
    private const val TILE_PX = 256f

    /**
     * Angular size of one texel of the deepest tile a provider publishing to
     * [maxLod] can hold, in radians on the unit sphere.
     *
     * [maxLod] splits the equator into `2^maxLod` tiles of [TILE_PX] each, so
     * this is `2π` over their product. It is the finest detail the imagery can
     * ever hold, and therefore the point past which moving the camera closer adds
     * blur and nothing else — which is what [GlobeFit] uses it for. It lives here
     * because the tile size lives here.
     */
    fun finestTexelRadians(maxLod: Int): Float =
        (2.0 * Math.PI / ((1 shl maxLod) * TILE_PX.toDouble())).toFloat()

    /**
     * A tile subdivides while its projected size exceeds this.
     *
     * Slightly above the texture size, so rendered tiles land between about 50%
     * and 112% texel density — under-sampling on the near side of the split
     * rather than over-sampling on the far side, because a slightly soft tile
     * reads better than a slightly aliased one.
     */
    private const val SPLIT_SCREEN_PX = TILE_PX * 1.125f

    /**
     * Hard cap on leaves per frame — the reference's figure.
     *
     * It used to be 160 here on the argument that the atlas had 171 evictable
     * slots. That accounting ignored the ancestors, which are requested and take
     * slots too, so it was both too high to protect the atlas and too low for the
     * middle distance. The atlas is protected structurally now; this is a
     * backstop against a pathological camera, and it is not what binds.
     *
     * **It becomes what binds the moment the atlas grows.** The worst leaf count
     * over the shipping sweep is 174 against 256, so today `evictableSlots` is
     * the only real constraint — but at a second 4096² atlas page (512 slots) this
     * cap pins the result and the extra slots do nothing. Anyone adding one has to
     * move this too. Judged from what `TiltDetailTest` prints, not by estimate.
     */
    const val MAX_VISIBLE_TILES: Int = 256

    /** Prefetch requests per frame, at most. */
    const val PREFETCH_CAP: Int = 64

    /** How close to the split threshold a leaf must be for its children to be prefetched. */
    private const val PREFETCH_FRACTION = 0.75f

    /**
     * The least the incidence factor may scale a tile's spend priority by.
     *
     * A chosen floor, not a derived one, and its job changed when incidence moved
     * out of the split test: a limb tile's cosine is nearly zero, and without a
     * floor it would sort to the very back of the heap and be **starved** of a
     * slot rather than, as before, *collapsed* to a coarse level. Same number,
     * different failure it prevents. A zero-priority node is still popped — the
     * heap drains completely — it is simply popped after the budget is gone.
     */
    private const val INCIDENCE_FLOOR = 0.25f

    /** Extra angle past the horizon cone before a tile is culled, so the limb can fade. */
    private const val HORIZON_CULL_MARGIN = 0.08f

    /**
     * Viewport culling applies only to tiles below this angular radius; larger
     * ones curve too much for a screen-space box to say anything true about.
     */
    private const val VIEWPORT_CULL_MAX_ANG_RADIUS = 1.0f

    /** Floor for the camera-to-tile distance in the screen-size estimate. */
    private const val MIN_DEPTH = 1e-4f

    /**
     * A projected sample this close to or behind the camera plane cannot be
     * bounded, and the tile is kept. The same constant `GlobeCamera.project`
     * uses to return null — the semantics must match exactly, because dropping
     * such a tile re-opens a hole in the near field.
     */
    private const val BEHIND_CAMERA_DEPTH = 1e-6f

    private const val DEG_TO_RAD = (PI / 180.0).toFloat()

    private const val PI_F = PI.toFloat()

    /** Inverse Mercator: tile-Y to latitude in degrees, at a given level. */
    fun tileYToLat(y: Float, numTiles: Float): Float = tileYToLat(y.toDouble(), numTiles).toFloat()

    /**
     * As above, in `Double` both ways, for the mesh's rows.
     *
     * A whole-tile `y` is exact in a `Float` at every level the packing allows,
     * but `y + 0.2f` at z18 is not: past 2¹⁷ a `Float` steps in sixty-fourths,
     * so the mesh's row fractions were being rounded to the nearest 1/64 of a
     * tile while its texture coordinates were not — a registration error of up
     * to 1/128 of a tile, two pixels on a tile drawn at the split size. The
     * result stays a `Double` too: a z18 tile at 85° is thirteen metres on the
     * ground and a `Float` degree there steps in 0.8 m, which is a third of one
     * row of the grid. The sines and cosines the mesh takes from it round to a
     * `Float` on the unit sphere instead, where the last place is under half a
     * metre everywhere.
     */
    fun tileYToLat(y: Double, numTiles: Float): Double {
        val n = PI - 2.0 * PI * y / numTiles
        return (180.0 / PI) * atan(sinh(n))
    }

    /** Forward Mercator: latitude in degrees to tile-Y, at a given level. */
    fun latToTileY(latDeg: Float, numTiles: Float): Float {
        val lat = latDeg.toDouble() * PI / 180.0
        val n = asinh(kotlin.math.tan(lat))
        return ((PI - n) * numTiles / (2.0 * PI)).toFloat()
    }

    /**
     * Walks the quadtree in descending screen-space-error order and returns the
     * leaves to render, sorted by `(z, x, y)`.
     *
     * The atlas budget is spent near-field first — see the class note on the
     * order of spending, which is why this is a heap and not a queue.
     *
     * [request] is called for **every visited node** — leaves and their
     * ancestors alike — which is what guarantees every leaf has a resident
     * ancestor to fall back on while it loads. [prefetch] is called after the
     * walk for the children of leaves that are nearly ready to split; see the
     * class note.
     *
     * @param maxLod the deepest level the provider publishes; nothing deeper is visited
     * @param pinnedMaxLevel the atlas's permanently held levels, which cost no evictable slot
     * @param evictableSlots how many tiles above [pinnedMaxLevel] the atlas can hold at once
     */
    fun collectVisibleTiles(
        camera: GlobeCamera,
        basis: CameraBasis,
        viewport: GlobeViewport,
        maxLod: Int,
        pinnedMaxLevel: Int,
        evictableSlots: Int,
        request: (z: Int, x: Int, y: Int) -> Unit,
        prefetch: (z: Int, x: Int, y: Int) -> Unit = { _, _, _ -> },
    ): List<VisibleTile> {
        val frame = TraversalFrame(
            basis = basis,
            viewport = viewport,
            focal = camera.focalPixels(viewport.height),
            horizonAngle = camera.horizonAngle(),
            altitude = camera.altitude,
        )

        val leaves = ArrayList<VisibleTile>(64)
        val heap = NodeHeap(256)
        val nearSplit = LongQueue(64)
        // The root always survives its own cull: at z0 the angular radius is
        // clamped to PI, which is above VIEWPORT_CULL_MAX_ANG_RADIUS so the
        // viewport test is skipped, and the horizon test is unsatisfiable. So
        // pushing it through the same gate as every other node costs nothing and
        // keeps one path. The old code could not reach this case, which is why
        // it is worth a sentence.
        pushIfVisible(frame, heap, 0, 0, 0)
        var nonPinnedRequested = 0

        while (!heap.isEmpty) {
            val slot = heap.pop()
            val node = heap.nodeAt(slot)
            val z = zOf(node)
            val x = xOf(node)
            val y = yOf(node)
            request(z, x, y)
            if (z > pinnedMaxLevel) nonPinnedRequested++

            // Emitting a leaf keeps `leaves + queue` constant and culling shrinks
            // it, so gating subdivision — a net +3 nodes — on the prospective
            // total keeps the final leaf count at or under the cap. The same
            // argument holds for `requested + queue` against the atlas: a
            // dequeued node moves from one term to the other or drops out, and
            // only a split grows the sum, by exactly the four checked here.
            val withinLeafCap = leaves.size + heap.size + 4 <= MAX_VISIBLE_TILES
            val withinAtlas = nonPinnedRequested + heap.size + 4 <= evictableSlots
            val screenPx = heap.screenPxAt(slot)

            if (z < maxLod && withinLeafCap && withinAtlas && screenPx > SPLIT_SCREEN_PX) {
                val cx = x * 2
                val cy = y * 2
                pushIfVisible(frame, heap, z + 1, cx, cy)
                pushIfVisible(frame, heap, z + 1, cx + 1, cy)
                pushIfVisible(frame, heap, z + 1, cx, cy + 1)
                pushIfVisible(frame, heap, z + 1, cx + 1, cy + 1)
            } else {
                leaves += VisibleTile(
                    z = z,
                    x = x,
                    y = y,
                    lonMin = heap.lonMinAt(slot),
                    lonMax = heap.lonMaxAt(slot),
                    depth = heap.depthAt(slot),
                )
                // "Nearly ready to split" means a tile that stopped for its
                // *size*, not one the budget refused: prefetching the children of
                // a budget-truncated leaf asks for exactly what was just declined.
                if (z < maxLod &&
                    screenPx > PREFETCH_FRACTION * SPLIT_SCREEN_PX &&
                    screenPx <= SPLIT_SCREEN_PX
                ) {
                    nearSplit.addLast(node)
                }
            }
        }

        var prefetchBudget = min(PREFETCH_CAP, evictableSlots - nonPinnedRequested)
        while (!nearSplit.isEmpty && prefetchBudget >= 4) {
            val node = nearSplit.removeFirst()
            val cz = zOf(node) + 1
            val cx = xOf(node) * 2
            val cy = yOf(node) * 2
            prefetch(cz, cx, cy)
            prefetch(cz, cx + 1, cy)
            prefetch(cz, cx, cy + 1)
            prefetch(cz, cx + 1, cy + 1)
            prefetchBudget -= 4
        }

        // **Sorted by key, not left in traversal order.** GlobeScene builds a
        // mesh signature from this list and rebuilds the geometry when it
        // changes, on the argument that the order is a function of the visible
        // set. Breadth-first made that true by accident; a priority order does
        // not - it is a function of continuous float priorities, so a sub-pixel
        // camera move could reorder two near-ties and force a needless rebuild.
        // Sorting by (z, x, y) makes the order a pure function of the set, which
        // is strictly stronger than what the FIFO gave, and keeps the
        // coarse-before-fine property the emitted order used to have.
        leaves.sortWith(BY_TILE_KEY)
        return leaves
    }

    /** Ascending pack(z, x, y) - z first, so coarse tiles still come first. */
    private val BY_TILE_KEY = Comparator<VisibleTile> { a, b ->
        pack(a.z, a.x, a.y).compareTo(pack(b.z, b.x, b.y))
    }

    /**
     * Evaluates (z, x, y) and queues it unless it is culled.
     *
     * Culling at **push** time rather than at pop is load-bearing twice over. A
     * node carries its own priority into the heap rather than inheriting its
     * parent's, which would tie all four siblings and degrade the order back
     * toward breadth-first. And the budget gate stops over-charging for nodes
     * that will never be spent, so more of the atlas reaches real splits. Every
     * node is still evaluated exactly once; only the moment moves.
     */
    private fun pushIfVisible(frame: TraversalFrame, heap: NodeHeap, z: Int, x: Int, y: Int) {
        if (!tileMetrics(frame, z, x, y)) return
        heap.push(
            node = pack(z, x, y),
            priority = frame.priority,
            screenPx = frame.screenPx,
            depth = frame.depth,
            lonMin = frame.lonMin,
            lonMax = frame.lonMax,
        )
    }

    // (z, x, y) packed into one Long for the work queue: z at bit 48, x at bit
    // 24, y at bit 0. The traversal used to allocate a Triple and three boxed
    // Ints per node.
    private const val Z_SHIFT = 48
    private const val X_SHIFT = 24
    private const val AXIS_MASK = 0xFF_FFFFL

    private fun pack(z: Int, x: Int, y: Int): Long =
        (z.toLong() shl Z_SHIFT) or (x.toLong() shl X_SHIFT) or y.toLong()

    private fun zOf(node: Long): Int = (node ushr Z_SHIFT).toInt()
    private fun xOf(node: Long): Int = ((node ushr X_SHIFT) and AXIS_MASK).toInt()
    private fun yOf(node: Long): Int = (node and AXIS_MASK).toInt()

    /**
     * Per-frame values shared by every node visit, plus the scratch the metrics
     * are written into — one object per traversal rather than one per node.
     *
     * The basis is unpacked into floats so the inner loops touch no `Vec3`:
     * the nine viewport samples per node used to allocate two vectors each.
     */
    private class TraversalFrame(
        basis: CameraBasis,
        viewport: GlobeViewport,
        val focal: Float,
        val horizonAngle: Float,
        val altitude: Float,
    ) {
        val rx = basis.right.x
        val ry = basis.right.y
        val rz = basis.right.z
        val ux = basis.up.x
        val uy = basis.up.y
        val uz = basis.up.z
        val lx = basis.look.x
        val ly = basis.look.y
        val lz = basis.look.z
        val px = basis.position.x
        val py = basis.position.y
        val pz = basis.position.z
        val fx = basis.facingUnit.x
        val fy = basis.facingUnit.y
        val fz = basis.facingUnit.z
        val width = viewport.width
        val height = viewport.height
        val centerX = viewport.centerX
        val centerY = viewport.centerY

        /** The chord-based screen estimate, for the viewport margin. */
        var screenPx = 0f

        /**
         * The same, scaled by incidence — the order the atlas budget is spent
         * in, **not** a split test. See the class note on incidence.
         */
        var priority = 0f
        var depth = 0f
        var lonMin = 0f
        var lonMax = 0f
    }

    /**
     * Culls `(z, x, y)` against the horizon cone and the viewport, and writes
     * its projected screen size and extents into [frame]. False when the tile
     * cannot be visible.
     */
    private fun tileMetrics(frame: TraversalFrame, z: Int, x: Int, y: Int): Boolean {
        val numTiles = (1 shl z).toFloat()
        val lonMin = (x / numTiles) * 360f - 180f
        val lonMax = ((x + 1) / numTiles) * 360f - 180f
        val latN = tileYToLat(y.toFloat(), numTiles)
        val latS = tileYToLat((y + 1).toFloat(), numTiles)
        val latC = (latN + latS) * 0.5f
        val lonC = (lonMin + lonMax) * 0.5f

        // World-space angular extents of the tile, radians.
        val ewArc = (lonMax - lonMin) * DEG_TO_RAD * cos(latC * DEG_TO_RAD)
        val nsArc = (latN - latS) * DEG_TO_RAD

        // Conservative angular radius: the flat half-diagonal underestimates
        // spherical distance for near-hemisphere tiles, so inflate and cap.
        val angRadius = min(PI_F, sqrt(ewArc * ewArc + nsArc * nsArc) * 0.625f)

        // The tile centre on the unit sphere.
        val latR = latC * DEG_TO_RAD
        val lonR = lonC * DEG_TO_RAD
        val cosLat = cos(latR)
        val wx = cosLat * sin(lonR)
        val wy = sin(latR)
        val wz = cosLat * cos(lonR)

        val cosCenter = (wx * frame.fx + wy * frame.fy + wz * frame.fz).coerceIn(-1f, 1f)
        if (acos(cosCenter) - angRadius > frame.horizonAngle + HORIZON_CULL_MARGIN) return false

        // Camera to tile centre. Its length is the depth the reference computes
        // in camera space — a rotation preserves it — and its direction against
        // the tile's normal is the incidence.
        val tx = frame.px - wx
        val ty = frame.py - wy
        val tz = frame.pz - wz
        val dist = sqrt(tx * tx + ty * ty + tz * tz)
        // World-space extent across the tile's larger axis.
        val chord = 2f * sin(min(PI_F, max(ewArc, nsArc)) * 0.5f)
        // Nearest possible camera-to-tile distance; the nadir at `altitude` is a
        // global lower bound for the whole sphere.
        val near = max(MIN_DEPTH, max(frame.altitude, dist - chord * 0.5f))
        val screenPx = chord * frame.focal / near

        if (angRadius < VIEWPORT_CULL_MAX_ANG_RADIUS &&
            !projectsIntoViewport(frame, screenPx, latN, latC, latS, lonMin, lonC, lonMax)
        ) {
            return false
        }

        val incidence = if (dist < MIN_DEPTH) 1f else (wx * tx + wy * ty + wz * tz) / dist
        frame.screenPx = screenPx
        frame.priority = screenPx * max(INCIDENCE_FLOOR, incidence)
        frame.depth = dist
        frame.lonMin = lonMin
        frame.lonMax = lonMax
        return true
    }

    /**
     * Conservative viewport test: project a 3×3 sample of the tile and check
     * whether its screen box, padded for the bulge between samples, touches the
     * viewport. A sample behind the camera keeps the tile — it cannot be
     * bounded, and dropping it would punch a hole in the near field, which is
     * the one place a hole is unmissable.
     *
     * The projection is inlined against the frame's focal length. Going through
     * `GlobeCamera.project` recomputed `tan(fovY / 2)` for each of the nine
     * samples — about two thousand `tan` calls a frame for a value that cannot
     * change mid-traversal.
     */
    private fun projectsIntoViewport(
        frame: TraversalFrame,
        screenPx: Float,
        latN: Float,
        latC: Float,
        latS: Float,
        lonMin: Float,
        lonC: Float,
        lonMax: Float,
    ): Boolean {
        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        for (i in 0 until 3) {
            val lat = when (i) {
                0 -> latN
                1 -> latC
                else -> latS
            }
            val latR = lat * DEG_TO_RAD
            val cosLat = cos(latR)
            val wy = sin(latR)
            for (j in 0 until 3) {
                val lon = when (j) {
                    0 -> lonMin
                    1 -> lonC
                    else -> lonMax
                }
                val lonR = lon * DEG_TO_RAD
                val dx = cosLat * sin(lonR) - frame.px
                val dy = wy - frame.py
                val dz = cosLat * cos(lonR) - frame.pz
                val depth = dx * frame.lx + dy * frame.ly + dz * frame.lz
                if (depth <= BEHIND_CAMERA_DEPTH) return true
                val sx = frame.centerX + frame.focal * (dx * frame.rx + dy * frame.ry + dz * frame.rz) / depth
                val sy = frame.centerY - frame.focal * (dx * frame.ux + dy * frame.uy + dz * frame.uz) / depth
                minX = min(minX, sx)
                minY = min(minY, sy)
                maxX = max(maxX, sx)
                maxY = max(maxY, sy)
            }
        }
        val margin = screenPx * 0.35f + 24f
        return (minX - margin) <= frame.width &&
            (maxX + margin) >= 0f &&
            (minY - margin) <= frame.height &&
            (maxY + margin) >= 0f
    }

    /**
     * The traversal's work queue, ordered by how much each node deserves the
     * atlas rather than by how deep it is.
     *
     * ### Why an order at all
     *
     * The split test asks whether a tile is too big for its texture; the budget
     * asks who gets one of the atlas's finite slots when not everyone can. Those
     * are different questions, and only the first is the reference's — it has no
     * atlas and gates on leaf count alone. At `MAX_TILT` a correct traversal wants
     * roughly 1,960 non-pinned tiles against 235 slots, an eight-fold shortfall,
     * so *which* tiles get them is the whole design. Breadth-first spends them
     * uniformly: when the budget runs out at level k every remaining branch stops
     * at level k, which is the "everything turns to mush" the incidence term used
     * to hide by suppressing demand before the budget ever bound.
     *
     * Popping the largest `priority` first spends the budget on the near field
     * and lets the horizon stay coarse, which is what a tilted view should look
     * like. Incidence belongs *here* — it is a bad predictor of a tile's screen
     * extent, which is what an SSE test measures, and a good predictor of its
     * screen area, which is what should decide who gets a scarce slot.
     *
     * ### Shape
     *
     * An index-ordered binary max-heap over a parallel-array arena, the same
     * idiom as [LongQueue] and the atlas's intrusive LRU: the heap holds `Int`
     * record indices, so a sift swaps one `Int` rather than six fields. Records
     * are append-only — a popped record is never reclaimed, because the caller
     * reads a node's metrics *after* popping it and the arena is bounded by the
     * number of nodes ever pushed.
     *
     * Allocated per traversal rather than hoisted to object scratch: two test
     * classes call [collectVisibleTiles], and shared mutable scratch would turn a
     * parallel test runner into intermittent failures. Seven arrays of 256 is
     * 8 KB against the ~150 `VisibleTile` the traversal already allocates.
     */
    private class NodeHeap(initialCapacity: Int) {
        private var node = LongArray(initialCapacity)
        private var priority = FloatArray(initialCapacity)
        private var screenPx = FloatArray(initialCapacity)
        private var depth = FloatArray(initialCapacity)
        private var lonMin = FloatArray(initialCapacity)
        private var lonMax = FloatArray(initialCapacity)

        /** Record indices, ordered as a binary max-heap over [priority]. */
        private var order = IntArray(initialCapacity)

        private var records = 0

        var size = 0
            private set

        val isEmpty: Boolean get() = size == 0

        fun nodeAt(slot: Int): Long = node[slot]
        fun screenPxAt(slot: Int): Float = screenPx[slot]
        fun depthAt(slot: Int): Float = depth[slot]
        fun lonMinAt(slot: Int): Float = lonMin[slot]
        fun lonMaxAt(slot: Int): Float = lonMax[slot]

        fun push(
            node: Long,
            priority: Float,
            screenPx: Float,
            depth: Float,
            lonMin: Float,
            lonMax: Float,
        ) {
            if (records == this.node.size) growRecords()
            val slot = records++
            this.node[slot] = node
            this.priority[slot] = priority
            this.screenPx[slot] = screenPx
            this.depth[slot] = depth
            this.lonMin[slot] = lonMin
            this.lonMax[slot] = lonMax

            if (size == order.size) order = order.copyOf(order.size * 2)
            order[size] = slot
            siftUp(size)
            size++
        }

        /** The record index of the highest-priority node, removed from the heap. */
        fun pop(): Int {
            val top = order[0]
            size--
            if (size > 0) {
                order[0] = order[size]
                siftDown(0)
            }
            return top
        }

        private fun siftUp(from: Int) {
            var i = from
            val slot = order[i]
            val key = priority[slot]
            while (i > 0) {
                val parent = (i - 1) / 2
                if (priority[order[parent]] >= key) break
                order[i] = order[parent]
                i = parent
            }
            order[i] = slot
        }

        private fun siftDown(from: Int) {
            var i = from
            val slot = order[i]
            val key = priority[slot]
            while (true) {
                val left = i * 2 + 1
                if (left >= size) break
                val right = left + 1
                var child = left
                if (right < size && priority[order[right]] > priority[order[left]]) child = right
                if (priority[order[child]] <= key) break
                order[i] = order[child]
                i = child
            }
            order[i] = slot
        }

        private fun growRecords() {
            val n = node.size * 2
            node = node.copyOf(n)
            priority = priority.copyOf(n)
            screenPx = screenPx.copyOf(n)
            depth = depth.copyOf(n)
            lonMin = lonMin.copyOf(n)
            lonMax = lonMax.copyOf(n)
        }
    }

    /** A FIFO of packed nodes over a ring of longs: no boxing, no per-node allocation. */
    private class LongQueue(initialCapacity: Int) {
        private var items = LongArray(initialCapacity)
        private var head = 0

        var size = 0
            private set

        val isEmpty: Boolean get() = size == 0

        fun addLast(value: Long) {
            if (size == items.size) grow()
            items[(head + size) % items.size] = value
            size++
        }

        fun removeFirst(): Long {
            val value = items[head]
            head = (head + 1) % items.size
            size--
            return value
        }

        private fun grow() {
            val next = LongArray(items.size * 2)
            for (i in 0 until size) next[i] = items[(head + i) % items.size]
            items = next
            head = 0
        }
    }
}

/**
 * A tile selected for rendering this frame.
 *
 * [lonMin] and [lonMax] come back with the tile because the traversal has
 * already computed them and the mesh builder needs them; recomputing them per
 * vertex would be the same two divides a few thousand times over.
 *
 * @property depth distance from the camera to the tile centre. Carried for the
 *   diagnostics and for parity with the reference's `VisibleTile`; nothing is
 *   sorted by it any more — see the note on order in [Quadtree]
 */
internal data class VisibleTile(
    val z: Int,
    val x: Int,
    val y: Int,
    val lonMin: Float,
    val lonMax: Float,
    val depth: Float,
)
