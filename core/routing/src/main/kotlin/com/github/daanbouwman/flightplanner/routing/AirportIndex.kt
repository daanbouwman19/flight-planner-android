package com.github.daanbouwman.flightplanner.routing

import com.github.daanbouwman.flightplanner.model.AirportSizeClass
import kotlin.math.cos
import kotlin.math.sin

/**
 * Every planning-usable airport, held in memory as a struct of arrays.
 *
 * This holds only what route generation needs, and every field is a primitive.
 * Names, municipalities, countries, elevations and runway counts are *not* here:
 * they are display data, they are never read in the sampling loop, and fetching
 * them at startup dominated the load. Reading a column out of SQLite costs about
 * 11 ms per 24,000 rows for a number and about 30 ms for text, so the difference
 * between "what the generator needs" and "everything about an airport" is the
 * difference between a fast start and a visible stall. Display fields are loaded
 * on demand for the handful of airports actually shown.
 *
 * **Rows are sorted ascending by [longestRunwayFt], and that ordering is
 * load-bearing** — [firstSlotWithRunway] binary-searches it, which turns
 * "airports this aircraft can use" into an O(log n) slice rather than a scan.
 */
class AirportIndex internal constructor(
    val size: Int,
    /** Upstream airport id, for joining to display data. */
    val ids: IntArray,
    /** Four-character codes packed via [IcaoCode]. */
    val codes: IntArray,
    val latDeg: DoubleArray,
    val lonDeg: DoubleArray,
    val latRad: FloatArray,
    val sinLat: FloatArray,
    val cosLat: FloatArray,
    val sinLon: FloatArray,
    val cosLon: FloatArray,
    val longestRunwayFt: IntArray,
    val flags: IntArray,
    /** Codes sorted ascending, for lookup. */
    private val sortedCodes: IntArray,
    /** Slot for the code at the same position in [sortedCodes]. */
    private val sortedCodeSlots: IntArray,
    internal val bands: LatBandIndex,
) {

    /** Slot for a packed code, or -1. */
    fun slotOfCode(packedCode: Int): Int {
        if (packedCode < 0) return -1
        val position = sortedCodes.binarySearch(packedCode)
        return if (position >= 0) sortedCodeSlots[position] else -1
    }

    /** Slot for an ICAO code, or -1. Case-insensitive. */
    fun slotOf(icao: String): Int = slotOfCode(IcaoCode.encode(icao.trim()))

    /** The code at a slot, as text. Allocates; for display only. */
    fun icaoOf(slot: Int): String = IcaoCode.decode(codes[slot])

    /**
     * Index of the first slot whose longest runway is at least [requiredFt].
     * Every slot from here to [size] is usable; returns [size] when none is.
     */
    fun firstSlotWithRunway(requiredFt: Int): Int {
        var low = 0
        var high = size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (longestRunwayFt[mid] < requiredFt) low = mid + 1 else high = mid
        }
        return low
    }

    fun hasIcaoCode(slot: Int): Boolean = flags[slot] and FLAG_HAS_ICAO != 0

    fun hasHardSurface(slot: Int): Boolean = flags[slot] and FLAG_HARD_SURFACE != 0

    fun hasLighting(slot: Int): Boolean = flags[slot] and FLAG_LIGHTING != 0

    fun sizeClass(slot: Int): AirportSizeClass = SIZE_CLASSES[(flags[slot] ushr SIZE_SHIFT) and 0b11]

    companion object {
        const val FLAG_HAS_ICAO = 1 shl 0
        const val FLAG_HARD_SURFACE = 1 shl 1
        const val FLAG_LIGHTING = 1 shl 2
        const val SIZE_SHIFT = 3

        private val SIZE_CLASSES = AirportSizeClass.entries.toTypedArray()

        fun packFlags(
            hasIcao: Boolean,
            hardSurface: Boolean,
            lighting: Boolean,
            sizeClass: AirportSizeClass,
        ): Int {
            var f = 0
            if (hasIcao) f = f or FLAG_HAS_ICAO
            if (hardSurface) f = f or FLAG_HARD_SURFACE
            if (lighting) f = f or FLAG_LIGHTING
            return f or (sizeClass.ordinal shl SIZE_SHIFT)
        }
    }
}

/**
 * Accumulates airports and produces a sorted [AirportIndex].
 *
 * Rows are pushed straight from a database cursor, so building the index never
 * materialises a list of entity objects.
 */
class AirportIndexBuilder(expectedSize: Int) {

    private val ids = IntArray(expectedSize)
    private val codes = IntArray(expectedSize)
    private val latDeg = DoubleArray(expectedSize)
    private val lonDeg = DoubleArray(expectedSize)
    private val longestRunwayFt = IntArray(expectedSize)
    private val flags = IntArray(expectedSize)

    private var count = 0

    /** Number of rows rejected because their code could not be packed. */
    var rejectedCodes = 0
        private set

    fun add(id: Int, packedCode: Int, latitude: Double, longitude: Double, longestRunway: Int, packedFlags: Int) {
        if (packedCode < 0) {
            rejectedCodes++
            return
        }
        val i = count
        check(i < ids.size) { "AirportIndexBuilder overflow: expected at most ${ids.size} airports" }
        ids[i] = id
        codes[i] = packedCode
        latDeg[i] = latitude
        lonDeg[i] = longitude
        longestRunwayFt[i] = longestRunway
        flags[i] = packedFlags
        count++
    }

    /** Convenience for tests and tools that hold codes as text. */
    fun add(id: Int, icao: String, latitude: Double, longitude: Double, longestRunway: Int, packedFlags: Int) =
        add(id, IcaoCode.encode(icao), latitude, longitude, longestRunway, packedFlags)

    fun build(): AirportIndex {
        val n = count
        val order = sortPermutationByRunwayLength(n)

        val sortedIds = IntArray(n)
        val sortedCodesBySlot = IntArray(n)
        val sortedLat = DoubleArray(n)
        val sortedLon = DoubleArray(n)
        val sortedRunway = IntArray(n)
        val sortedFlags = IntArray(n)

        val latRad = FloatArray(n)
        val sinLat = FloatArray(n)
        val cosLat = FloatArray(n)
        val sinLon = FloatArray(n)
        val cosLon = FloatArray(n)

        for (slot in 0 until n) {
            val src = order[slot]
            sortedIds[slot] = ids[src]
            sortedCodesBySlot[slot] = codes[src]
            sortedLat[slot] = latDeg[src]
            sortedLon[slot] = lonDeg[src]
            sortedRunway[slot] = longestRunwayFt[src]
            sortedFlags[slot] = flags[src]

            val rLat = Math.toRadians(latDeg[src]).toFloat()
            val rLon = Math.toRadians(lonDeg[src]).toFloat()
            latRad[slot] = rLat
            sinLat[slot] = sin(rLat)
            cosLat[slot] = cos(rLat)
            sinLon[slot] = sin(rLon)
            cosLon[slot] = cos(rLon)
        }

        // Lookup table: codes ascending with their slots alongside. A sorted
        // IntArray plus a binary search costs two arrays and no boxing, where a
        // HashMap<String, Int> would allocate 24,000 entries and box every value.
        val lookupOrder = (0 until n).sortedBy { sortedCodesBySlot[it] }
        val sortedCodes = IntArray(n)
        val sortedCodeSlots = IntArray(n)
        for (i in 0 until n) {
            val slot = lookupOrder[i]
            sortedCodes[i] = sortedCodesBySlot[slot]
            sortedCodeSlots[i] = slot
        }

        return AirportIndex(
            size = n,
            ids = sortedIds,
            codes = sortedCodesBySlot,
            latDeg = sortedLat,
            lonDeg = sortedLon,
            latRad = latRad,
            sinLat = sinLat,
            cosLat = cosLat,
            sinLon = sinLon,
            cosLon = cosLon,
            longestRunwayFt = sortedRunway,
            flags = sortedFlags,
            sortedCodes = sortedCodes,
            sortedCodeSlots = sortedCodeSlots,
            bands = LatBandIndex.build(sortedLat, n),
        )
    }

    /**
     * Slot order sorted ascending by runway length, ties by input order.
     *
     * A counting sort rather than a comparator: runway lengths are small
     * non-negative integers, so this is O(n) with no boxing and no comparator
     * calls, where `sortedWith(compareBy { ... })` would allocate 24,000 boxed
     * Integers. Callers feed rows in primary-key order and counting sort is
     * stable, so ties come out ordered by id for free.
     */
    private fun sortPermutationByRunwayLength(n: Int): IntArray {
        if (n == 0) return IntArray(0)

        var maxLength = 0
        for (i in 0 until n) {
            if (longestRunwayFt[i] > maxLength) maxLength = longestRunwayFt[i]
        }

        val counts = IntArray(maxLength + 2)
        for (i in 0 until n) counts[longestRunwayFt[i] + 1]++
        for (value in 1 until counts.size) counts[value] += counts[value - 1]

        val order = IntArray(n)
        for (i in 0 until n) order[counts[longestRunwayFt[i]]++] = i
        return order
    }
}
