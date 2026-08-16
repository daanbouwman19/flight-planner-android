package com.github.daanbouwman.flightplanner.routing

import kotlin.math.floor

/**
 * A spatial index over airports, bucketed by whole degrees of latitude.
 *
 * The only spatial question route generation ever asks is "which airports lie
 * within this latitude/longitude window of a departure point". At roughly
 * 24,000 static points, an R-tree — which the desktop application uses via
 * `rstar` — is the wrong shape for that: it costs several hundred lines, chases
 * pointers, and only starts to win above about 10⁵ points. Counting-sorting the
 * airports into 181 latitude bands costs about 200 KB and answers the same
 * query with two array reads and a contiguous scan.
 *
 * A 200 NM query touches roughly seven bands, i.e. about a thousand slots.
 */
class LatBandIndex private constructor(
    private val bandStart: IntArray,
    private val bandSlots: IntArray,
) {

    /**
     * Visits every slot whose latitude falls in `[minLat, maxLat]`, clamped to
     * the poles. The callback may be invoked for slots slightly outside the
     * range — bands have whole-degree granularity — so callers must still apply
     * an exact distance test.
     */
    inline fun forEachInLatRange(minLat: Double, maxLat: Double, action: (slot: Int) -> Unit) {
        val from = bandOf(minLat)
        val to = bandOf(maxLat)
        val start = startOfBand(from)
        val end = startOfBand(to + 1)
        for (i in start until end) action(slotAt(i))
    }

    /** Number of slots that would be visited for a latitude range. */
    fun countInLatRange(minLat: Double, maxLat: Double): Int =
        startOfBand(bandOf(maxLat) + 1) - startOfBand(bandOf(minLat))

    @PublishedApi
    internal fun startOfBand(band: Int): Int = bandStart[band.coerceIn(0, BAND_COUNT)]

    @PublishedApi
    internal fun slotAt(position: Int): Int = bandSlots[position]

    companion object {
        /** One band per degree of latitude, from -90 to +90 inclusive. */
        const val BAND_COUNT = 181

        @PublishedApi
        internal fun bandOf(latitude: Double): Int =
            floor(latitude + 90.0).toInt().coerceIn(0, BAND_COUNT - 1)

        /**
         * Builds the index in O(n) by counting sort.
         *
         * @param latDeg latitudes indexed by slot.
         * @param size number of slots.
         */
        fun build(latDeg: DoubleArray, size: Int): LatBandIndex {
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

            return LatBandIndex(bandStart, bandSlots)
        }
    }
}
