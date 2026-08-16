package com.github.daanbouwman.flightplanner.routing

import com.github.daanbouwman.flightplanner.model.Airport
import com.github.daanbouwman.flightplanner.model.AirportSizeClass
import kotlin.math.cos
import kotlin.math.sin

/**
 * Every planning-usable airport, held in memory as a struct of arrays.
 *
 * Route generation probes latitude and longitude sines and cosines for up to
 * 128 candidate slots per route, fifty routes at a time. As objects that is
 * thousands of scattered pointer dereferences per batch; as parallel primitive
 * arrays it is a handful of sequential reads with no object headers and no
 * garbage. At roughly 24,000 airports the whole structure costs about 4.5 MB.
 *
 * **The rows are sorted ascending by [longestRunwayFt], and that ordering is
 * load-bearing.** [firstSlotWithRunway] binary-searches it, which is what makes
 * "airports this aircraft can use" an O(log n) slice rather than a scan.
 */
class AirportIndex internal constructor(
    val size: Int,
    val ids: IntArray,
    val icao: Array<String>,
    val names: Array<String>,
    val latDeg: DoubleArray,
    val lonDeg: DoubleArray,
    val latRad: FloatArray,
    val sinLat: FloatArray,
    val cosLat: FloatArray,
    val sinLon: FloatArray,
    val cosLon: FloatArray,
    val elevationFt: IntArray,
    val longestRunwayFt: IntArray,
    val runwayCount: IntArray,
    val flags: IntArray,
    val countries: Array<String>,
    val municipalities: Array<String?>,
    private val icaoToSlot: Map<String, Int>,
    internal val bands: LatBandIndex,
) {

    /** Slot for an ICAO code, or -1. Case-insensitive. */
    fun slotOf(icao: String): Int = icaoToSlot[icao.trim().uppercase()] ?: -1

    /**
     * Index of the first slot whose longest runway is at least [requiredFt].
     *
     * Every slot from here to [size] is usable by an aircraft with that runway
     * requirement. Returns [size] when nothing qualifies.
     */
    fun firstSlotWithRunway(requiredFt: Int): Int {
        // Lower-bound binary search: the equivalent of Rust's `partition_point`.
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

    /** "Amsterdam Airport Schiphol (EHAM)". Built on demand; not worth caching. */
    fun displayName(slot: Int): String = "${names[slot]} (${icao[slot]})"

    fun toAirport(slot: Int): Airport = Airport(
        id = ids[slot],
        icao = icao[slot],
        name = names[slot],
        latitude = latDeg[slot],
        longitude = lonDeg[slot],
        elevationFt = elevationFt[slot],
        country = countries[slot],
        municipality = municipalities[slot],
        sizeClass = sizeClass(slot),
        longestRunwayFt = longestRunwayFt[slot],
        runwayCount = runwayCount[slot],
        hasHardSurface = hasHardSurface(slot),
        hasIcaoCode = hasIcaoCode(slot),
    )

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
 * Callers push rows straight out of a database cursor; nothing intermediate is
 * materialised, so building the index for 24,000 airports never allocates a
 * list of 24,000 objects.
 */
class AirportIndexBuilder(expectedSize: Int) {

    private val ids = IntArray(expectedSize)
    private val icao = arrayOfNulls<String>(expectedSize)
    private val names = arrayOfNulls<String>(expectedSize)
    private val latDeg = DoubleArray(expectedSize)
    private val lonDeg = DoubleArray(expectedSize)
    private val elevationFt = IntArray(expectedSize)
    private val longestRunwayFt = IntArray(expectedSize)
    private val runwayCount = IntArray(expectedSize)
    private val flags = IntArray(expectedSize)
    private val countries = arrayOfNulls<String>(expectedSize)
    private val municipalities = arrayOfNulls<String>(expectedSize)

    private var count = 0

    fun add(
        id: Int,
        icaoCode: String,
        name: String,
        latitude: Double,
        longitude: Double,
        elevation: Int,
        longestRunway: Int,
        runways: Int,
        country: String,
        municipality: String?,
        packedFlags: Int,
    ) {
        val i = count
        check(i < ids.size) { "AirportIndexBuilder overflow: expected at most ${ids.size} airports" }
        ids[i] = id
        icao[i] = icaoCode
        names[i] = name
        latDeg[i] = latitude
        lonDeg[i] = longitude
        elevationFt[i] = elevation
        longestRunwayFt[i] = longestRunway
        runwayCount[i] = runways
        countries[i] = country
        municipalities[i] = municipality
        flags[i] = packedFlags
        count++
    }

    /**
     * Sorts by longest runway and precomputes the trigonometry.
     *
     * The sort happens here rather than being relied upon from the database:
     * `id` is an `INTEGER PRIMARY KEY` and therefore SQLite's `rowid`, so
     * physical row order follows upstream ids no matter how the ETL inserts.
     * Sorting 24,000 integers costs a few milliseconds, and the alternative is
     * an invariant that silently stops holding.
     */
    fun build(): AirportIndex {
        val n = count

        // Sort an index permutation, then gather. Sorting the arrays in lockstep
        // any other way would mean either boxing or eleven parallel swaps.
        val order = (0 until n).sortedWith(
            compareBy({ longestRunwayFt[it] }, { ids[it] }),
        )

        val sortedIds = IntArray(n)
        val sortedIcao = arrayOfNulls<String>(n)
        val sortedNames = arrayOfNulls<String>(n)
        val sortedLat = DoubleArray(n)
        val sortedLon = DoubleArray(n)
        val sortedElevation = IntArray(n)
        val sortedRunway = IntArray(n)
        val sortedRunwayCount = IntArray(n)
        val sortedFlags = IntArray(n)
        val sortedCountries = arrayOfNulls<String>(n)
        val sortedMunicipalities = arrayOfNulls<String>(n)

        val latRad = FloatArray(n)
        val sinLat = FloatArray(n)
        val cosLat = FloatArray(n)
        val sinLon = FloatArray(n)
        val cosLon = FloatArray(n)

        val icaoToSlot = HashMap<String, Int>(n * 2)

        for (slot in 0 until n) {
            val src = order[slot]
            sortedIds[slot] = ids[src]
            sortedIcao[slot] = icao[src]
            sortedNames[slot] = names[src]
            sortedLat[slot] = latDeg[src]
            sortedLon[slot] = lonDeg[src]
            sortedElevation[slot] = elevationFt[src]
            sortedRunway[slot] = longestRunwayFt[src]
            sortedRunwayCount[slot] = runwayCount[src]
            sortedFlags[slot] = flags[src]
            sortedCountries[slot] = countries[src]
            sortedMunicipalities[slot] = municipalities[src]

            val rLat = Math.toRadians(latDeg[src]).toFloat()
            val rLon = Math.toRadians(lonDeg[src]).toFloat()
            latRad[slot] = rLat
            sinLat[slot] = sin(rLat)
            cosLat[slot] = cos(rLat)
            sinLon[slot] = sin(rLon)
            cosLon[slot] = cos(rLon)

            // First writer wins, matching the desktop app's `or_insert`.
            icaoToSlot.putIfAbsent(sortedIcao[slot]!!, slot)
        }

        @Suppress("UNCHECKED_CAST")
        return AirportIndex(
            size = n,
            ids = sortedIds,
            icao = sortedIcao as Array<String>,
            names = sortedNames as Array<String>,
            latDeg = sortedLat,
            lonDeg = sortedLon,
            latRad = latRad,
            sinLat = sinLat,
            cosLat = cosLat,
            sinLon = sinLon,
            cosLon = cosLon,
            elevationFt = sortedElevation,
            longestRunwayFt = sortedRunway,
            runwayCount = sortedRunwayCount,
            flags = sortedFlags,
            countries = sortedCountries as Array<String>,
            municipalities = sortedMunicipalities,
            icaoToSlot = icaoToSlot,
            bands = LatBandIndex.build(sortedLat, n),
        )
    }
}
