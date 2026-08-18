package com.github.daanbouwman.flightplanner.routing

import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max

/**
 * The patch of the world one route card shows, and the projection onto it.
 *
 * The sparkline this replaced normalised its arc into the unit box by itself,
 * which is all one line needs and is unusable the moment a *second* layer has to
 * line up with it: a coastline normalised to its own extent would sit somewhere
 * else entirely. The frame is that shared window — a centre, a span and the
 * canvas's aspect ratio — and both layers project through it.
 *
 * ### Longitudes may leave [-180, 180]
 *
 * A route from Tokyo to Los Angeles is sampled as longitudes running from 140°
 * up past 180° to 242°, because [RouteArc.unwrapLongitudes] removes the seam
 * rather than drawing a line back across the planet. The frame keeps that
 * convention: [centreLon] and the window bounds are unwrapped, and it is
 * [projectOutline] that shifts land into the window by whole turns.
 *
 * ### The projection is equirectangular about a standard parallel
 *
 * Plain plate carrée — longitude straight onto x — stretches everything by
 * `1 / cos(latitude)`: at Amsterdam that is 1.6×, which is enough to make the
 * North Sea look like an ocean and Britain look fat. Scaling longitude by the
 * cosine of the window's centre latitude fixes the shape where the route
 * actually is, which is the only place on a 180 dp card anyone is looking. Near
 * the poles that factor collapses, so it is floored: a polar window is drawn
 * slightly stretched rather than infinitely wide.
 */
class MapFrame(
    /** Centre longitude in degrees east, possibly outside [-180, 180]. */
    val centreLon: Double,
    /** Centre latitude in degrees north. */
    val centreLat: Double,
    /** Degrees of longitude the card's full width covers. */
    val spanLon: Double,
    /** Degrees of latitude the card's full height covers. */
    val spanLat: Double,
) {
    init {
        require(spanLon > 0.0 && spanLat > 0.0) {
            "Frame spans must be positive, were $spanLon by $spanLat"
        }
    }

    val minLon: Double get() = centreLon - spanLon / 2.0
    val maxLon: Double get() = centreLon + spanLon / 2.0
    val minLat: Double get() = centreLat - spanLat / 2.0
    val maxLat: Double get() = centreLat + spanLat / 2.0

    /** Fraction of the card's width, 0 at the left edge and 1 at the right. */
    fun x(lon: Double): Float = ((lon - minLon) / spanLon).toFloat()

    /** Fraction of the card's height, 0 at the **top**, i.e. north. */
    fun y(lat: Double): Float = ((maxLat - lat) / spanLat).toFloat()

    /**
     * Projects a sampled arc into interleaved `x, y` fractions.
     *
     * Longitudes must already be unwrapped — see [RouteArc.sampleGeographic],
     * which is where they come from.
     */
    fun project(lats: DoubleArray, lons: DoubleArray): FloatArray {
        require(lats.size == lons.size) { "${lats.size} latitudes against ${lons.size} longitudes" }
        val out = FloatArray(lats.size * 2)
        for (i in lats.indices) {
            out[i * 2] = x(lons[i])
            out[i * 2 + 1] = y(lats[i])
        }
        return out
    }

    /**
     * Projects the land this window shows, clipped to it.
     *
     * **This is the expensive call.** It is called from `RouteMap`'s
     * `drawWithCache`, which is the UI thread — once per card per size, not per
     * frame, but on the thread that has to produce the next frame. It was
     * designed to run beside `RouteArc.sampleGeographic` on a background
     * dispatcher, and it cannot yet, because the window it projects through
     * depends on a canvas aspect ratio nobody knows until the card is measured.
     * Moving it off the main thread needs that plan, not a comment; until then
     * this KDoc says where it actually runs.
     *
     * Three things it does that a naive loop would not:
     *
     *  - **A ring is rejected by its bounding box first.** The outline holds 122
     *    rings and a card typically shows three, so 119 rejections cost four
     *    comparisons each rather than a thousand projections.
     *  - **A ring is drawn at every whole turn that reaches the window.** A
     *    window centred at 242°E has to be met by rings stored around −118°, and
     *    one just west of the seam by rings stored at +179°. Usually exactly one
     *    shift matches — but Antarctica spans the full −180…180 range by itself,
     *    so a window straddling the seam needs it drawn twice, and taking only
     *    the first match silently drops half the Antarctic coast.
     *  - **What survives is clipped to the window**, which is what the first
     *    version of this did not do. Culling by bounding box alone leaves a card
     *    showing Poland filling and stroking the whole of Eurasia — 1,183 points —
     *    and trusting the canvas to throw the rest away. Measured on the benchmark
     *    build, that cost about 9 ms a frame while flinging.
     *
     * ### Why the fill and the coast are clipped differently
     *
     * A polygon clipped to a rectangle has the rectangle's edges in its boundary.
     * That is exactly right for a fill and exactly wrong for a stroke: stroking a
     * clipped polygon draws a hairline box around every card, along the three
     * sides where land runs off it. So [ProjectedLand.fill] is the polygon —
     * Sutherland–Hodgman, closed, ready for an even-odd fill — and
     * [ProjectedLand.coast] is the *original* coastline segments trimmed to the
     * window and left open, so the stroke stops at the edge instead of turning
     * along it.
     *
     * @param margin extra window, as a fraction of each span, so a coast just off
     *   the card still contributes the segment that enters it, and a stroked end
     *   is trimmed outside the visible area rather than inside it.
     */
    fun projectOutline(outline: WorldOutline, margin: Double = 0.0): ProjectedLand {
        val windowMinLon = minLon - margin * spanLon
        val windowMaxLon = maxLon + margin * spanLon
        val windowMinLat = minLat - margin * spanLat
        val windowMaxLat = maxLat + margin * spanLat

        // The clip rectangle in the card's own coordinates: the window is 0..1 by
        // construction, so a margin expressed as a fraction of the span is simply
        // a wider box. This is the whole reason clipping happens after projection
        // rather than in degrees.
        val low = (-margin).toFloat()
        val high = (1.0 + margin).toFloat()

        val fill = RingBuilder()
        val coast = RingBuilder()
        val scratch = FloatArrayList()

        for (ring in 0 until outline.ringCount) {
            if (outline.ringMaxLat[ring] < windowMinLat) continue
            if (outline.ringMinLat[ring] > windowMaxLat) continue

            val shifts = lonShiftsFor(
                ringMinLon = outline.ringMinLon[ring].toDouble(),
                ringMaxLon = outline.ringMaxLon[ring].toDouble(),
                windowMinLon = windowMinLon,
                windowMaxLon = windowMaxLon,
            )
            if (shifts.isEmpty()) continue

            val from = outline.ringStart[ring]
            val to = outline.ringStart[ring + 1]

            // Every matching turn, not just the first. A ring and a window that
            // between them span 360° or more overlap at *two* shifts — Antarctica
            // spans the whole range by itself, and a wide long-haul window can do
            // it with Eurasia — and taking only the first drops everything the
            // other one would have drawn.
            for (shift in shifts) {
                scratch.clear()
                for (i in from until to) {
                    scratch.add(x(outline.lon[i].toDouble() + shift))
                    scratch.add(y(outline.lat[i].toDouble()))
                }
                clipPolygonInto(scratch, low, high, fill)
                clipPolylineInto(scratch, low, high, coast)
            }
        }

        return ProjectedLand(fill = fill.build(), coast = coast.build())
    }

    /**
     * Whole-degree parallels and meridians crossing this window, as projected
     * polylines.
     *
     * For the cards that frame no coastline at all — the interior of a continent,
     * the middle of an ocean — where the alternative is a flat wash that reads as
     * a failed load rather than as a place. Two lines each way is enough to say
     * "this is a piece of the world"; a full grid would be a texture competing
     * with the route.
     *
     * The step is chosen so the lines land on numbers a chart would use rather
     * than on whatever divides the span evenly.
     */
    fun graticule(): ProjectedRings {
        val step = graticuleStep(maxOf(spanLon, spanLat))
        val points = ArrayList<Float>()
        val starts = ArrayList<Int>()
        starts += 0

        var meridian = ceil(minLon / step) * step
        while (meridian <= maxLon) {
            points += x(meridian)
            points += 0f
            points += x(meridian)
            points += 1f
            starts += points.size / 2
            meridian += step
        }

        var parallel = ceil(minLat / step) * step
        while (parallel <= maxLat) {
            // Latitude is clamped because a window can extend past the pole; a
            // parallel there is not a place, and drawing it would put a line
            // across the card at a latitude that does not exist.
            if (parallel in -90.0..90.0) {
                points += 0f
                points += y(parallel)
                points += 1f
                points += y(parallel)
                starts += points.size / 2
            }
            parallel += step
        }

        return ProjectedRings(
            points = FloatArray(points.size) { points[it] },
            ringStart = IntArray(starts.size) { starts[it] },
        )
    }

    /** A round number of degrees that leaves two to four lines across [span]. */
    private fun graticuleStep(span: Double): Double = when {
        span > 120.0 -> 45.0
        span > 60.0 -> 20.0
        span > 30.0 -> 10.0
        else -> 5.0
    }

    /**
     * Which whole turns of longitude bring a ring into the window.
     *
     * Usually one, and the reason it is not always one is Antarctica: it spans
     * −180° to 180° by itself, so a window straddling the seam overlaps it at two
     * different shifts and needs both drawn. The same holds for any ring wide
     * enough that `ringWidth + windowWidth >= 360`.
     *
     * @return the shifts in degrees, empty when the ring misses the window at
     *   every offset.
     */
    private fun lonShiftsFor(
        ringMinLon: Double,
        ringMaxLon: Double,
        windowMinLon: Double,
        windowMaxLon: Double,
    ): DoubleArray {
        var count = 0
        val found = DoubleArray(3)
        for (turns in -1..1) {
            val shift = turns * 360.0
            if (ringMaxLon + shift >= windowMinLon && ringMinLon + shift <= windowMaxLon) {
                found[count++] = shift
            }
        }
        return if (count == found.size) found else found.copyOf(count)
    }

    companion object {
        /**
         * The narrowest window a card will show, in degrees of longitude at the
         * window's own latitude.
         *
         * Without a floor, a Rotterdam–Amsterdam hop frames 0.4° of the world and
         * the card fills with a piece of the Dutch coast magnified past
         * recognition — technically correct and useless. At 25° a short European
         * hop still shows the North Sea, the Channel and enough mainland to say
         * where it is.
         */
        const val MIN_SPAN_DEGREES: Double = 25.0

        /**
         * Fraction of the window kept clear around the route, per side.
         *
         * An arc that runs exactly to the card's edges reads as clipped rather
         * than framed, and the endpoint markers — drawn in pixels, not degrees —
         * would hang half off the card.
         */
        const val PADDING_FRACTION: Double = 0.12

        /**
         * Floor on the cosine that scales longitude, at 75° of latitude.
         *
         * Above it the factor falls away fast enough that a window would have to be
         * hundreds of degrees wide to fill a card, so a flight over the pole would
         * frame most of the hemisphere to show two airports an hour apart.
         */
        private const val MIN_LON_SCALE: Double = 0.2588 // cos(75°)

        /**
         * A window around a sampled route, sized to the canvas it will be drawn on.
         *
         * @param aspect the canvas's width divided by its height. The spans are
         *   fitted to it, so a degree covers the same number of pixels on both axes
         *   and land is not squashed to the card's shape.
         */
        fun forRoute(
            lats: DoubleArray,
            lons: DoubleArray,
            aspect: Double,
            minSpanDegrees: Double = MIN_SPAN_DEGREES,
            paddingFraction: Double = PADDING_FRACTION,
        ): MapFrame {
            require(lats.isNotEmpty() && lats.size == lons.size) {
                "Need at least one point and matching arrays, got ${lats.size} and ${lons.size}"
            }
            require(aspect > 0.0) { "Canvas aspect must be positive, was $aspect" }

            var minLat = lats[0]
            var maxLat = lats[0]
            var minLon = lons[0]
            var maxLon = lons[0]
            for (i in 1 until lats.size) {
                if (lats[i] < minLat) minLat = lats[i]
                if (lats[i] > maxLat) maxLat = lats[i]
                if (lons[i] < minLon) minLon = lons[i]
                if (lons[i] > maxLon) maxLon = lons[i]
            }

            val centreLat = (minLat + maxLat) / 2.0
            val centreLon = (minLon + maxLon) / 2.0
            val lonScale = max(cos(Math.toRadians(centreLat)), MIN_LON_SCALE)

            // Work in projected units, where fitting the aspect and applying the
            // floor are both plain arithmetic, then convert the width back to
            // degrees of longitude at the end.
            val padding = 1.0 + 2.0 * paddingFraction
            var width = (maxLon - minLon) * lonScale * padding
            var height = (maxLat - minLat) * padding

            // Fit the canvas by *growing* the deficient axis. Shrinking the other
            // one instead would crop the route the frame exists to show.
            if (width < height * aspect) width = height * aspect else height = width / aspect

            val floorWidth = minSpanDegrees * lonScale
            if (width <= 0.0) {
                // Departure and destination at the same point: there is no extent
                // to scale up, so the floor is set outright. Scaling zero by
                // anything is still zero, and the ratio that would do it is
                // infinite — a NaN window that fails at the far end of the frame,
                // where nothing says an airport was routed to itself.
                width = floorWidth
                height = width / aspect
            } else if (width < floorWidth) {
                val scale = floorWidth / width
                width *= scale
                height *= scale
            }

            return MapFrame(
                centreLon = centreLon,
                centreLat = centreLat,
                spanLon = width / lonScale,
                spanLat = height,
            )
        }
    }
}

/**
 * One card's land, in two layers that are drawn differently.
 *
 * @property fill closed polygons, clipped to the window, to be filled with an
 *   even-odd rule so an enclosed ring is a hole.
 * @property coast open polylines — the real coastline, trimmed where it leaves
 *   the window. Never closed and never filled: these exist so the stroke follows
 *   the coast and stops at the card's edge instead of running along it.
 */
class ProjectedLand(val fill: ProjectedRings, val coast: ProjectedRings) {

    val isEmpty: Boolean get() = fill.isEmpty && coast.isEmpty

    companion object {
        val Empty: ProjectedLand = ProjectedLand(ProjectedRings.Empty, ProjectedRings.Empty)
    }
}

/**
 * Land rings projected into one card's coordinate space, as interleaved `x, y`
 * fractions with an offset per ring.
 *
 * The same column layout as [WorldOutline], for the same reason: the renderer
 * walks it once to build a `Path` and allocates nothing per point.
 */
class ProjectedRings(val points: FloatArray, val ringStart: IntArray) {
    init {
        require(ringStart.isNotEmpty() && ringStart.first() == 0) { "Ring table must begin at 0" }
        require(ringStart.last() * 2 == points.size) {
            "Ring table ends at ${ringStart.last()} points but there are ${points.size / 2}"
        }
    }

    val ringCount: Int get() = ringStart.size - 1

    val pointCount: Int get() = points.size / 2

    val isEmpty: Boolean get() = ringCount == 0

    companion object {
        val Empty: ProjectedRings = ProjectedRings(FloatArray(0), intArrayOf(0))
    }
}

/**
 * A growable `FloatArray`.
 *
 * `ArrayList<Float>` boxes every coordinate, and clipping a continent touches a
 * few thousand of them per card; this is the same code without the garbage.
 */
internal class FloatArrayList(initialCapacity: Int = 64) {
    private var data = FloatArray(initialCapacity)

    var size: Int = 0
        private set

    operator fun get(index: Int): Float = data[index]

    fun add(value: Float) {
        if (size == data.size) data = data.copyOf(data.size * 2)
        data[size++] = value
    }

    fun clear() {
        size = 0
    }

    fun toFloatArray(): FloatArray = data.copyOf(size)
}

/**
 * Collects clipped rings into the flat columns [ProjectedRings] holds.
 *
 * A ring is only committed once it is known to have survived with enough points
 * to draw — an empty or one-point result is dropped rather than recorded as a
 * ring the renderer would have to skip.
 */
internal class RingBuilder {
    private val points = FloatArrayList(256)
    private val starts = ArrayList<Int>().apply { add(0) }

    /** Appends one ring from [source], which holds interleaved `x, y`. */
    fun addRing(source: FloatArrayList, minimumPoints: Int) {
        if (source.size < minimumPoints * 2) return
        for (i in 0 until source.size) points.add(source[i])
        starts += points.size / 2
    }

    fun build(): ProjectedRings =
        ProjectedRings(points = points.toFloatArray(), ringStart = starts.toIntArray())
}

/**
 * Sutherland–Hodgman: clips a closed ring to the box `[low, high]` on both axes.
 *
 * Four passes, one per edge, each keeping the points inside that half-plane and
 * inserting a point where the boundary is crossed. The result is a closed
 * polygon whose boundary partly runs along the box — which is correct for a fill
 * and is why the coastline is clipped separately, by [clipPolylineInto].
 *
 * The classic caveat applies and does not matter here: a ring that leaves and
 * re-enters the box comes back joined by a zero-width sliver along the edge
 * rather than as two polygons. Filled, that is invisible; it is only stroking
 * such a boundary that would show it.
 */
internal fun clipPolygonInto(ring: FloatArrayList, low: Float, high: Float, into: RingBuilder) {
    var source = ring
    var target = FloatArrayList(ring.size)
    // axis 0 is x, axis 1 is y; the two `false` passes keep what is below the
    // upper bound, the two `true` passes what is above the lower one.
    for (edge in 0 until 4) {
        val axis = edge % 2
        val keepAbove = edge < 2
        val bound = if (keepAbove) low else high
        target.clear()
        clipHalfPlane(source, axis, bound, keepAbove, target)
        if (target.size == 0) return
        val swap = if (source === ring) FloatArrayList(target.size) else source
        source = target
        target = swap
    }
    into.addRing(source, minimumPoints = 3)
}

private fun clipHalfPlane(
    source: FloatArrayList,
    axis: Int,
    bound: Float,
    keepAbove: Boolean,
    target: FloatArrayList,
) {
    val count = source.size / 2
    if (count == 0) return

    var previousX = source[(count - 1) * 2]
    var previousY = source[(count - 1) * 2 + 1]
    var previousInside = inside(previousX, previousY, axis, bound, keepAbove)

    for (i in 0 until count) {
        val currentX = source[i * 2]
        val currentY = source[i * 2 + 1]
        val currentInside = inside(currentX, currentY, axis, bound, keepAbove)

        if (currentInside != previousInside) {
            val from = if (axis == 0) previousX else previousY
            val to = if (axis == 0) currentX else currentY
            val t = if (to == from) 0f else (bound - from) / (to - from)
            target.add(previousX + (currentX - previousX) * t)
            target.add(previousY + (currentY - previousY) * t)
        }
        if (currentInside) {
            target.add(currentX)
            target.add(currentY)
        }

        previousX = currentX
        previousY = currentY
        previousInside = currentInside
    }
}

private fun inside(x: Float, y: Float, axis: Int, bound: Float, keepAbove: Boolean): Boolean {
    val value = if (axis == 0) x else y
    return if (keepAbove) value >= bound else value <= bound
}

/**
 * Trims a ring's segments to the box and emits what is left as **open**
 * polylines.
 *
 * Liang–Barsky per segment. A segment wholly outside ends the run in progress; a
 * segment that enters part way starts a new one; a segment that leaves part way
 * closes the current one at the boundary. The result follows the real coast and
 * never contains an edge of the box, so stroking it cannot draw a frame around
 * the card.
 */
internal fun clipPolylineInto(ring: FloatArrayList, low: Float, high: Float, into: RingBuilder) {
    val count = ring.size / 2
    if (count < 2) return

    val run = FloatArrayList(64)
    fun flush() {
        into.addRing(run, minimumPoints = 2)
        run.clear()
    }

    for (i in 0 until count - 1) {
        val x1 = ring[i * 2]
        val y1 = ring[i * 2 + 1]
        val x2 = ring[(i + 1) * 2]
        val y2 = ring[(i + 1) * 2 + 1]

        val span = clipSegment(x1, y1, x2, y2, low, high)
        if (span == null) {
            flush()
            continue
        }

        val (enter, exit) = span
        if (run.size == 0 || enter > 0f) {
            flush()
            run.add(x1 + (x2 - x1) * enter)
            run.add(y1 + (y2 - y1) * enter)
        }
        run.add(x1 + (x2 - x1) * exit)
        run.add(y1 + (y2 - y1) * exit)
        if (exit < 1f) flush()
    }
    flush()
}

/**
 * The portion of a segment inside the box, as the pair of parameters `t` where it
 * enters and leaves, or null when none of it is inside.
 */
private fun clipSegment(
    x1: Float,
    y1: Float,
    x2: Float,
    y2: Float,
    low: Float,
    high: Float,
): Pair<Float, Float>? {
    val dx = x2 - x1
    val dy = y2 - y1
    var enter = 0f
    var exit = 1f

    // One (direction, distance-to-edge) pair per side of the box.
    val directions = floatArrayOf(-dx, dx, -dy, dy)
    val distances = floatArrayOf(x1 - low, high - x1, y1 - low, high - y1)

    for (side in 0 until 4) {
        val direction = directions[side]
        val distance = distances[side]
        if (direction == 0f) {
            // Parallel to this edge: inside or outside for its whole length.
            if (distance < 0f) return null
            continue
        }
        val t = distance / direction
        if (direction < 0f) {
            if (t > exit) return null
            if (t > enter) enter = t
        } else {
            if (t < enter) return null
            if (t < exit) exit = t
        }
    }
    return enter to exit
}
