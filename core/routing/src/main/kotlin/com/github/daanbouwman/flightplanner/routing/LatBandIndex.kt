package com.github.daanbouwman.flightplanner.routing

import kotlin.math.ceil
import kotlin.math.floor

/**
 * A spatial index over airports, bucketed by whole degrees of latitude and
 * sorted by longitude inside each band.
 *
 * The only spatial question route generation ever asks is "which airports lie
 * within this latitude/longitude window of a departure point". At roughly
 * 24,000 static points, an R-tree — which the desktop application uses via
 * `rstar` — is the wrong shape for that: it costs several hundred lines, chases
 * pointers, and only starts to win above about 10⁵ points. Counting-sorting the
 * airports into 181 latitude bands answers the same query from flat arrays.
 *
 * Latitude alone is not enough of a filter, though. A short-range airframe asks
 * for a window a degree or two tall but only a few degrees wide, and a
 * latitude-only scan visits every airport at that latitude *anywhere on Earth* —
 * for a band crossing the United States that is several hundred airports, of
 * which fewer than one percent are in the longitude window. Keeping each band
 * sorted by longitude turns the query into a binary search plus a contiguous
 * slice, which is what makes short-range generation cheap rather than merely
 * cheaper than a full scan.
 */
class LatBandIndex internal constructor(
    /** Where each band begins in [bandSlots]; [BAND_COUNT] + 1 entries. */
    val bandStart: IntArray,
    /** Slots grouped by band, ascending by [bandLonKey] within a band. */
    val bandSlots: IntArray,
    /**
     * Quantised longitude of `bandSlots[i]`, ascending within each band.
     *
     * Searching the same array that was sorted is what makes the binary search
     * exact. Quantising to about a metre and padding the query bounds by a
     * quantum keeps the window an over-approximation, which is all it has to be:
     * membership is decided by the caller's distance test, never here.
     */
    val bandLonKey: IntArray,
) {

    /**
     * Visits every slot inside a latitude/longitude window, wrapping across the
     * antimeridian when the window does.
     *
     * Bands have whole-degree granularity and the window is an
     * over-approximation of a circle, so the callback is invoked for slots
     * outside the true range; callers must still apply an exact distance test.
     *
     * @param lonHalfWidth half-width in degrees; at or above 180 the longitude
     *   test is skipped entirely, which is what keeps near-polar windows correct.
     */
    inline fun forEachInWindow(
        minLat: Double,
        maxLat: Double,
        centreLon: Double,
        lonHalfWidth: Double,
        action: (slot: Int) -> Unit,
    ) {
        if (lonHalfWidth >= 180.0) {
            forEachInLatRange(minLat, maxLat, action)
            return
        }

        val from = bandOf(minLat)
        val to = bandOf(maxLat)
        val low = centreLon - lonHalfWidth
        val high = centreLon + lonHalfWidth

        for (band in from..to) {
            val begin = startOfBand(band)
            val end = startOfBand(band + 1)
            if (begin == end) continue
            when {
                low < -180.0 -> {
                    forEachInLonSpan(begin, end, low + 360.0, 180.0, action)
                    forEachInLonSpan(begin, end, -180.0, high, action)
                }
                high > 180.0 -> {
                    forEachInLonSpan(begin, end, low, 180.0, action)
                    forEachInLonSpan(begin, end, -180.0, high - 360.0, action)
                }
                else -> forEachInLonSpan(begin, end, low, high, action)
            }
        }
    }

    /**
     * Visits every slot whose latitude falls in `[minLat, maxLat]`, clamped to
     * the poles, ignoring longitude.
     */
    inline fun forEachInLatRange(minLat: Double, maxLat: Double, action: (slot: Int) -> Unit) {
        val start = startOfBand(bandOf(minLat))
        val end = startOfBand(bandOf(maxLat) + 1)
        for (i in start until end) action(slotAt(i))
    }

    /** Number of slots that would be visited for a latitude range. */
    fun countInLatRange(minLat: Double, maxLat: Double): Int =
        startOfBand(bandOf(maxLat) + 1) - startOfBand(bandOf(minLat))

    /** Visits one band's slots whose longitude lies in `[low, high]`. */
    @PublishedApi
    internal inline fun forEachInLonSpan(
        begin: Int,
        end: Int,
        low: Double,
        high: Double,
        action: (slot: Int) -> Unit,
    ) {
        val highKey = keyAtLeast(high)
        var i = lowerBound(begin, end, keyAtMost(low))
        while (i < end && keyAt(i) <= highKey) {
            action(slotAt(i))
            i++
        }
    }

    /** First position in `[begin, end)` whose key is at least [low]. */
    @PublishedApi
    internal fun lowerBound(begin: Int, end: Int, low: Int): Int {
        var lo = begin
        var hi = end
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (bandLonKey[mid] < low) lo = mid + 1 else hi = mid
        }
        return lo
    }

    @PublishedApi
    internal fun startOfBand(band: Int): Int = bandStart[band.coerceIn(0, BAND_COUNT)]

    @PublishedApi
    internal fun slotAt(position: Int): Int = bandSlots[position]

    @PublishedApi
    internal fun keyAt(position: Int): Int = bandLonKey[position]

    companion object {
        /** One band per degree of latitude, from -90 to +90 inclusive. */
        const val BAND_COUNT = 181

        /** Quantisation steps per degree of longitude — about a metre. */
        private const val KEY_STEPS_PER_DEGREE = 100_000.0

        @PublishedApi
        internal fun bandOf(latitude: Double): Int =
            floor(latitude + 90.0).toInt().coerceIn(0, BAND_COUNT - 1)

        /** Quantised longitude, rounding to nearest. */
        internal fun keyOf(lon: Double): Int = Math.round((lon + 180.0) * KEY_STEPS_PER_DEGREE).toInt()

        /** A key no greater than [lon]'s, so a lower bound never excludes it. */
        @PublishedApi
        internal fun keyAtMost(lon: Double): Int = floor((lon + 180.0) * KEY_STEPS_PER_DEGREE).toInt() - 1

        /** A key no less than [lon]'s, so an upper bound never excludes it. */
        @PublishedApi
        internal fun keyAtLeast(lon: Double): Int = ceil((lon + 180.0) * KEY_STEPS_PER_DEGREE).toInt() + 1

        /**
         * Builds the index: a counting sort into bands, then one longitude sort
         * per band.
         *
         * @param latDeg latitudes indexed by slot.
         * @param lonDeg longitudes indexed by slot.
         * @param size number of slots.
         */
        fun build(latDeg: DoubleArray, lonDeg: DoubleArray, size: Int): LatBandIndex {
            val counts = IntArray(BAND_COUNT + 1)
            for (slot in 0 until size) counts[bandOf(latDeg[slot])]++

            // Prefix sum: bandStart[b] is where band b begins in bandSlots.
            val bandStart = IntArray(BAND_COUNT + 1)
            var running = 0
            for (band in 0 until BAND_COUNT) {
                bandStart[band] = running
                running += counts[band]
            }
            bandStart[BAND_COUNT] = running

            val cursor = bandStart.copyOf()
            val bandSlots = IntArray(size)
            for (slot in 0 until size) {
                val band = bandOf(latDeg[slot])
                bandSlots[cursor[band]++] = slot
            }

            // Packing the key into the high half and the slot into the low half
            // keeps the per-band sort a primitive `LongArray` sort: no boxing,
            // no comparator, and the slot rides along for free.
            val packed = LongArray(size)
            for (i in 0 until size) {
                val slot = bandSlots[i]
                packed[i] = (keyOf(lonDeg[slot]).toLong() shl 32) or slot.toLong()
            }
            for (band in 0 until BAND_COUNT) {
                val begin = bandStart[band]
                val end = bandStart[band + 1]
                if (end - begin > 1) java.util.Arrays.sort(packed, begin, end)
            }

            val bandLonKey = IntArray(size)
            for (i in 0 until size) {
                bandSlots[i] = (packed[i] and 0xFFFFFFFFL).toInt()
                bandLonKey[i] = (packed[i] ushr 32).toInt()
            }

            return LatBandIndex(bandStart, bandSlots, bandLonKey)
        }
    }
}
