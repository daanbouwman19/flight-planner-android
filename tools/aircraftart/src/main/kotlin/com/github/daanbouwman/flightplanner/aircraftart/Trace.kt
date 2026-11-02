package com.github.daanbouwman.flightplanner.aircraftart

import kotlin.math.abs
import kotlin.math.hypot

/** A closed polygon in raster pixels, the last point not repeating the first. */
class Ring(val x: DoubleArray, val y: DoubleArray) {
    val size: Int get() = x.size

    /** Shoelace area, positive for a clockwise ring in y-down screen space — an outline, not a hole. */
    fun signedArea(): Double {
        var sum = 0.0
        for (i in 0 until size) {
            val j = (i + 1) % size
            sum += x[i] * y[j] - x[j] * y[i]
        }
        return sum / 2
    }
}

/**
 * Follows the boundary between set and clear pixels into closed rings.
 *
 * Each boundary edge between a set pixel and a clear neighbour is walked with
 * the set pixel on its right, so an outline comes out clockwise and a hole
 * anticlockwise — which is what lets an even-odd fill draw both without being
 * told which is which. Where two set pixels touch only at a corner the walk
 * turns left and keeps them in one ring: a wing tip a pixel wide stays attached
 * to its wing rather than becoming a separate speck.
 *
 * The ring's vertices are the **midpoints** of the boundary edges rather than
 * the pixel corners. That is marching squares at an iso-level of one half, and
 * it replaces a staircase with a polygon whose diagonal edges are already
 * diagonal, so Douglas–Peucker has a line to find instead of a sawtooth.
 */
object Trace {

    fun rings(raster: Raster): List<Ring> {
        val w = raster.width
        val h = raster.height
        // Directed boundary edges keyed by their start corner. A corner on the
        // (w + 1) × (h + 1) lattice has at most two outgoing edges.
        val stride = w + 1
        val outA = IntArray((w + 1) * (h + 1)) { -1 }
        val outB = IntArray((w + 1) * (h + 1)) { -1 }
        fun corner(x: Int, y: Int) = y * stride + x
        fun addEdge(from: Int, to: Int) {
            if (outA[from] < 0) outA[from] = to else outB[from] = to
        }
        for (py in 0 until h) for (px in 0 until w) {
            if (!raster[px, py]) continue
            if (!raster[px, py - 1]) addEdge(corner(px, py), corner(px + 1, py))
            if (!raster[px + 1, py]) addEdge(corner(px + 1, py), corner(px + 1, py + 1))
            if (!raster[px, py + 1]) addEdge(corner(px + 1, py + 1), corner(px, py + 1))
            if (!raster[px - 1, py]) addEdge(corner(px, py + 1), corner(px, py))
        }

        val rings = ArrayList<Ring>()
        for (start in outA.indices) {
            while (outA[start] >= 0 || outB[start] >= 0) {
                val xs = ArrayList<Double>()
                val ys = ArrayList<Double>()
                var from = start
                var to = take(outA, outB, from, previous = -1, stride = stride)
                while (true) {
                    xs += ((from % stride) + (to % stride)) / 2.0
                    ys += ((from / stride) + (to / stride)) / 2.0
                    val next = take(outA, outB, to, previous = from, stride = stride)
                    from = to
                    to = next
                    if (to < 0) break
                }
                rings += Ring(xs.toDoubleArray(), ys.toDoubleArray())
            }
        }
        return rings
    }

    /**
     * Removes and returns the edge leaving [at]. With two candidates — a saddle
     * corner — it takes the one turning left from the incoming direction.
     */
    private fun take(outA: IntArray, outB: IntArray, at: Int, previous: Int, stride: Int): Int {
        val a = outA[at]
        val b = outB[at]
        if (a < 0 && b < 0) return -1
        val pick = when {
            b < 0 -> a
            a < 0 -> b
            previous < 0 -> a
            else -> {
                val inX = at % stride - previous % stride
                val inY = at / stride - previous / stride
                val aX = a % stride - at % stride
                val aY = a / stride - at / stride
                // In y-down space a left turn has a negative cross product.
                if (inX * aY - inY * aX < 0) a else b
            }
        }
        if (pick == a) {
            outA[at] = outB[at]
            outB[at] = -1
        } else {
            outB[at] = -1
        }
        return pick
    }
}

/** Douglas–Peucker for closed rings, iterative, as `:tools:worldmap`'s is and for the same reason. */
object Simplify {

    fun closed(ring: Ring, epsilon: Double): Ring {
        val n = ring.size
        if (n <= 4) return ring
        // Split the ring at the vertex furthest from its first, and simplify
        // both open halves: a closed ring has no chord to start from.
        var far = 0
        var farDistance = -1.0
        for (i in 1 until n) {
            val d = hypot(ring.x[i] - ring.x[0], ring.y[i] - ring.y[0])
            if (d > farDistance) { farDistance = d; far = i }
        }
        val keep = BooleanArray(n)
        keep[0] = true
        keep[far] = true
        mark(ring, 0, far, epsilon, keep)
        mark(ring, far, n, epsilon, keep)
        val xs = ArrayList<Double>()
        val ys = ArrayList<Double>()
        for (i in 0 until n) if (keep[i]) { xs += ring.x[i]; ys += ring.y[i] }
        return Ring(xs.toDoubleArray(), ys.toDoubleArray())
    }

    /** Marks the points to keep strictly between [from] and [to]; index [n] wraps to 0. */
    private fun mark(ring: Ring, from: Int, to: Int, epsilon: Double, keep: BooleanArray) {
        val n = ring.size
        val stack = ArrayDeque<IntArray>()
        stack.addLast(intArrayOf(from, to))
        while (stack.isNotEmpty()) {
            val (a, b) = stack.removeLast()
            if (b <= a + 1) continue
            val ax = ring.x[a % n]; val ay = ring.y[a % n]
            val bx = ring.x[b % n]; val by = ring.y[b % n]
            var furthest = -1
            var furthestDistance = 0.0
            for (i in a + 1 until b) {
                val d = distance(ring.x[i], ring.y[i], ax, ay, bx, by)
                if (d > furthestDistance) { furthestDistance = d; furthest = i }
            }
            if (furthest >= 0 && furthestDistance > epsilon) {
                keep[furthest] = true
                stack.addLast(intArrayOf(a, furthest))
                stack.addLast(intArrayOf(furthest, b))
            }
        }
    }

    private fun distance(px: Double, py: Double, ax: Double, ay: Double, bx: Double, by: Double): Double {
        val dx = bx - ax
        val dy = by - ay
        val length = hypot(dx, dy)
        if (length < 1e-12) return hypot(px - ax, py - ay)
        return abs((px - ax) * dy - (py - ay) * dx) / length
    }
}
