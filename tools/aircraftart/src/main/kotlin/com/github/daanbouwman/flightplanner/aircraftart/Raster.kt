package com.github.daanbouwman.flightplanner.aircraftart

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * A binary top-down image of an airframe: one bit per pixel, set where any
 * triangle covers the pixel's centre.
 *
 * Pixel space is screen space — x to the right, y down — with the nose at the
 * top. The caller maps model coordinates into it (see [Silhouette.project]); the
 * raster knows nothing about aircraft.
 */
class Raster(val width: Int, val height: Int) {
    private companion object {
        /** "No seed in reach": large, finite, so the parabola arithmetic stays exact. */
        const val FAR = 1e20
    }

    val bits = BooleanArray(width * height)

    operator fun get(x: Int, y: Int): Boolean = x in 0 until width && y in 0 until height && bits[y * width + x]

    /**
     * Fills a triangle by sampling pixel centres, scanline by scanline.
     *
     * Centre sampling rather than conservative coverage, so a wing skin that is
     * one triangle thin edge-on contributes nothing — which is right: seen from
     * directly above, a vertical surface has no area.
     */
    fun fillTriangle(x0: Double, y0: Double, x1: Double, y1: Double, x2: Double, y2: Double) {
        val minY = max(0, ceil(min(y0, min(y1, y2)) - 0.5).toInt())
        val maxY = min(height - 1, floor(max(y0, max(y1, y2)) - 0.5).toInt())
        for (py in minY..maxY) {
            val cy = py + 0.5
            var left = Double.POSITIVE_INFINITY
            var right = Double.NEGATIVE_INFINITY
            fun edge(ax: Double, ay: Double, bx: Double, by: Double) {
                if ((ay <= cy && by > cy) || (by <= cy && ay > cy)) {
                    val x = ax + (cy - ay) / (by - ay) * (bx - ax)
                    if (x < left) left = x
                    if (x > right) right = x
                }
            }
            edge(x0, y0, x1, y1)
            edge(x1, y1, x2, y2)
            edge(x2, y2, x0, y0)
            if (left > right) continue
            val from = max(0, ceil(left - 0.5).toInt())
            val to = min(width - 1, floor(right - 0.5).toInt())
            val row = py * width
            for (px in from..to) bits[row + px] = true
        }
    }

    /**
     * Fills a simple polygon (convex or not) by its even-odd rule, sampling
     * pixel centres. Used for drawn outlines, which are authored as polygons.
     */
    fun fillPolygon(xs: DoubleArray, ys: DoubleArray) {
        val n = xs.size
        val minY = max(0, ceil(ys.min() - 0.5).toInt())
        val maxY = min(height - 1, floor(ys.max() - 0.5).toInt())
        val crossings = ArrayList<Double>()
        for (py in minY..maxY) {
            val cy = py + 0.5
            crossings.clear()
            for (i in 0 until n) {
                val j = (i + 1) % n
                val ay = ys[i]; val by = ys[j]
                if ((ay <= cy && by > cy) || (by <= cy && ay > cy)) {
                    crossings += xs[i] + (cy - ay) / (by - ay) * (xs[j] - xs[i])
                }
            }
            crossings.sort()
            var k = 0
            while (k + 1 < crossings.size) {
                val from = max(0, ceil(crossings[k] - 0.5).toInt())
                val to = min(width - 1, floor(crossings[k + 1] - 0.5).toInt())
                for (px in from..to) bits[py * width + px] = true
                k += 2
            }
        }
    }

    /** Replaces one half of every row with the reflection of the other, about the image's vertical centre line. */
    fun reflect(keepLeft: Boolean) {
        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width / 2) {
                val left = row + x
                val right = row + width - 1 - x
                if (keepLeft) bits[right] = bits[left] else bits[left] = bits[right]
            }
        }
    }

    /** Sets every pixel whose centre lies in the axis-aligned rectangle. */
    fun fillRect(left: Double, top: Double, right: Double, bottom: Double) {
        val minY = max(0, ceil(top - 0.5).toInt())
        val maxY = min(height - 1, floor(bottom - 0.5).toInt())
        val minX = max(0, ceil(left - 0.5).toInt())
        val maxX = min(width - 1, floor(right - 0.5).toInt())
        for (py in minY..maxY) for (px in minX..maxX) bits[py * width + px] = true
    }

    /**
     * Morphological opening by a disc of [radius] pixels: erode, then dilate
     * back.
     *
     * What survives is every part of the silhouette a disc that size can sit
     * inside — the fuselage, the wings, the tail — and what goes is everything
     * thinner: antennas, pitot tubes, static wicks, bracing wires, the odd
     * stray vertex. Convex corners come back rounded to the same radius, which
     * at a tenth of a percent of the airframe's length is a fraction of a pixel
     * at any size the app draws.
     *
     * Exact Euclidean distances (Felzenszwalb–Huttenlocher), so the disc is a
     * disc and the result does not depend on the image's orientation.
     */
    fun open(radius: Double) {
        if (radius <= 0.0) return
        val r2 = radius * radius
        // Erode: keep pixels further than `radius` from the background.
        val toBackground = squaredDistance { !bits[it] }
        val eroded = BooleanArray(bits.size) { toBackground[it] > r2 }
        // Dilate: keep pixels within `radius` of what survived.
        val toEroded = squaredDistance { eroded[it] }
        for (i in bits.indices) bits[i] = toEroded[i] <= r2
    }

    /**
     * Morphological closing by a disc of [radius] pixels: dilate, then erode
     * back.
     *
     * The opposite job to [open]. A model is built from separate parts, and the
     * hinge gaps between a wing and its flaps and ailerons, or between the two
     * halves of an elevator, are real gaps in the mesh that read from above as
     * slits cut into the trailing edge. Closing fills every gap narrower than
     * the disc and leaves the outline otherwise where it was.
     */
    fun close(radius: Double) {
        if (radius <= 0.0) return
        val r2 = radius * radius
        val toShape = squaredDistance { bits[it] }
        val dilated = BooleanArray(bits.size) { toShape[it] <= r2 }
        val toOutside = squaredDistance { !dilated[it] }
        for (i in bits.indices) bits[i] = toOutside[i] > r2
    }

    /** Squared distance from every pixel to the nearest pixel where [seed] holds. */
    private fun squaredDistance(seed: (Int) -> Boolean): DoubleArray {
        val n = max(width, height)
        val f = DoubleArray(n)
        val d = DoubleArray(n)
        val v = IntArray(n)
        val z = DoubleArray(n + 1)
        val grid = DoubleArray(width * height) { if (seed(it)) 0.0 else FAR }

        // The lower envelope of parabolas rooted at each sample, one line at a time.
        fun transform(length: Int) {
            var k = 0
            v[0] = 0
            z[0] = Double.NEGATIVE_INFINITY
            z[1] = Double.POSITIVE_INFINITY
            for (q in 1 until length) {
                var s = intersection(f, q, v[k])
                while (s <= z[k]) {
                    k--
                    s = intersection(f, q, v[k])
                }
                k++
                v[k] = q
                z[k] = s
                z[k + 1] = Double.POSITIVE_INFINITY
            }
            k = 0
            for (q in 0 until length) {
                while (z[k + 1] < q) k++
                val p = v[k]
                d[q] = (q - p).toDouble() * (q - p) + f[p]
            }
        }

        for (x in 0 until width) {
            for (y in 0 until height) f[y] = grid[y * width + x]
            transform(height)
            for (y in 0 until height) grid[y * width + x] = d[y]
        }
        for (y in 0 until height) {
            for (x in 0 until width) f[x] = grid[y * width + x]
            transform(width)
            for (x in 0 until width) grid[y * width + x] = d[x]
        }
        return grid
    }

    private fun intersection(f: DoubleArray, q: Int, p: Int): Double =
        ((f[q] + q.toDouble() * q) - (f[p] + p.toDouble() * p)) / (2.0 * q - 2.0 * p)

    fun count(): Int = bits.count { it }
}
