package com.github.daanbouwman.flightplanner.worldmap

import kotlin.math.max
import kotlin.math.sqrt

/**
 * Thins a ring until it is no finer than a route card can draw.
 *
 * Natural Earth's 1:110m land is already the coarsest of the three scales it
 * publishes, and it is still far finer than needed here: its coordinates carry
 * six decimal places — about 11 cm — and a card 180 dp tall showing a 25° window
 * resolves roughly 0.06° per pixel at 3×. Simplification is therefore about
 * segment count, which is what costs per frame, not about fidelity.
 *
 * Distances are in **degrees, treated as a plane**. That is wrong as geography —
 * a degree of longitude at 70°N is a third of one at the equator — and right as a
 * criterion here, because the map is drawn equirectangularly: a degree of
 * longitude is the same number of pixels everywhere on the card, so tolerance
 * measured in degrees is tolerance measured in pixels. Using true metres would
 * over-simplify exactly the high-latitude coasts that stay visually large.
 */
object Simplify {

    /**
     * Douglas–Peucker: keep the point furthest from the chord while it is
     * further than [epsilon], and recurse into both halves.
     *
     * Implemented with an explicit stack rather than recursion — Antarctica is a
     * single ring of over a thousand points, and a degenerate split pattern on a
     * ring that size is exactly where a recursive version runs out of stack in
     * the one environment nobody tests, the build machine.
     */
    fun douglasPeucker(ring: Ring, epsilon: Double): Ring {
        val n = ring.size
        if (n <= 2 || epsilon <= 0.0) return ring

        val keep = BooleanArray(n)
        keep[0] = true
        keep[n - 1] = true

        val stack = ArrayDeque<IntArray>()
        stack.addLast(intArrayOf(0, n - 1))
        while (stack.isNotEmpty()) {
            val (from, to) = stack.removeLast()
            if (to <= from + 1) continue

            var furthest = -1
            var furthestDistance = 0.0
            for (i in from + 1 until to) {
                val distance = perpendicularDistance(
                    lon = ring.lon[i], lat = ring.lat[i],
                    lon1 = ring.lon[from], lat1 = ring.lat[from],
                    lon2 = ring.lon[to], lat2 = ring.lat[to],
                )
                if (distance > furthestDistance) {
                    furthestDistance = distance
                    furthest = i
                }
            }

            if (furthest >= 0 && furthestDistance > epsilon) {
                keep[furthest] = true
                stack.addLast(intArrayOf(from, furthest))
                stack.addLast(intArrayOf(furthest, to))
            }
        }

        return ring.filtered(keep)
    }

    /**
     * Snaps a ring onto the storage grid and drops the points that snapping made
     * redundant.
     *
     * Quantisation happens in the builder rather than being left to the encoder
     * alone, because it *creates* duplicates: two source points 200 m apart land
     * on the same 610 m cell, and a duplicated point is a zero-length segment
     * that the device would still stroke and join. Dropping them here is free;
     * dropping them at load time is per-launch work.
     */
    fun quantise(ring: Ring, snapLon: (Double) -> Double, snapLat: (Double) -> Double): Ring {
        val lon = ArrayList<Double>(ring.size)
        val lat = ArrayList<Double>(ring.size)
        for (i in 0 until ring.size) {
            val qLon = snapLon(ring.lon[i])
            val qLat = snapLat(ring.lat[i])
            if (lon.isNotEmpty() && lon.last() == qLon && lat.last() == qLat) continue
            lon += qLon
            lat += qLat
        }
        return Ring(lon.toDoubleArray(), lat.toDoubleArray())
    }

    /** Restores the closing point if simplification or snapping removed it. */
    fun closed(ring: Ring): Ring {
        val n = ring.size
        if (n == 0) return ring
        if (ring.lon[0] == ring.lon[n - 1] && ring.lat[0] == ring.lat[n - 1]) return ring
        return Ring(ring.lon + ring.lon[0], ring.lat + ring.lat[0])
    }

    /**
     * Whether a ring is large enough to be worth shipping.
     *
     * A ring whose whole extent is below [minSpanDegrees] is at most a couple of
     * pixels across at the closest a card ever zooms, so it costs a `moveTo`, a
     * `lineTo` and a join to draw a dot. There are hundreds of them in the source
     * and dropping them is most of the file-size win. The threshold is a *span*,
     * not an area: a long thin island is still recognisable coast.
     */
    fun isVisible(ring: Ring, minSpanDegrees: Double): Boolean =
        ring.size >= 3 && max(ring.lonSpan, ring.latSpan) >= minSpanDegrees

    private fun Ring.filtered(keep: BooleanArray): Ring {
        var kept = 0
        for (flag in keep) if (flag) kept++
        val lon = DoubleArray(kept)
        val lat = DoubleArray(kept)
        var out = 0
        for (i in 0 until size) {
            if (!keep[i]) continue
            lon[out] = this.lon[i]
            lat[out] = this.lat[i]
            out++
        }
        return Ring(lon, lat)
    }

    /**
     * Distance from a point to the segment between two others, in degrees.
     *
     * Segment, not infinite line: a ring doubles back on itself constantly, and
     * measuring against the infinite line lets a point far beyond the chord's end
     * score as close to it, which deletes headlands.
     */
    private fun perpendicularDistance(
        lon: Double, lat: Double,
        lon1: Double, lat1: Double,
        lon2: Double, lat2: Double,
    ): Double {
        val dx = lon2 - lon1
        val dy = lat2 - lat1
        if (dx == 0.0 && dy == 0.0) return hypot(lon - lon1, lat - lat1)

        val t = ((lon - lon1) * dx + (lat - lat1) * dy) / (dx * dx + dy * dy)
        val clamped = t.coerceIn(0.0, 1.0)
        return hypot(lon - (lon1 + clamped * dx), lat - (lat1 + clamped * dy))
    }

    private fun hypot(dx: Double, dy: Double): Double = sqrt(dx * dx + dy * dy)
}
