package com.github.daanbouwman.flightplanner.routing

/**
 * The world's land polygons, flattened into primitive arrays.
 *
 * This is the coastline the route cards draw themselves on. It is deliberately
 * *not* a list of ring objects: the drawing path clips every ring against a
 * per-card window, so the hot loop is a scan over a few thousand coordinates,
 * and a `List<List<LatLon>>` would turn that into a few thousand pointer chases
 * and as many short-lived objects per card.
 *
 * Coordinates are degrees, in the source's own frame: longitude in `[-180, 180]`
 * and latitude in `[-90, 90]`. Natural Earth splits its polygons at the
 * antimeridian, so **no ring here spans the ±180° seam** — a ring's longitudes
 * are always monotone within that range. Windows that do cross the seam are the
 * projection's problem, not the data's.
 *
 * Rings are closed: the last point of each repeats the first.
 *
 * ### Holes
 *
 * A polygon's interior rings (the Caspian, inland seas) follow its exterior ring
 * and are not marked as such, because the renderer does not need to know: filling
 * the whole outline with an even-odd rule makes an enclosed ring a hole for free.
 * Any renderer using a non-zero rule would fill lakes as land.
 */
class WorldOutline(
    /** Longitudes, degrees east, one entry per point across all rings. */
    val lon: FloatArray,
    /** Latitudes, degrees north. */
    val lat: FloatArray,
    /** Start index of each ring, plus a final entry holding the total point count. */
    val ringStart: IntArray,
) {
    init {
        require(lon.size == lat.size) { "lon has ${lon.size} points, lat has ${lat.size}" }
        require(ringStart.isNotEmpty()) { "ringStart must hold at least the end offset" }
        require(ringStart.first() == 0) { "ringStart must begin at 0, was ${ringStart.first()}" }
        require(ringStart.last() == lon.size) {
            "ringStart ends at ${ringStart.last()} but there are ${lon.size} points"
        }
    }

    val ringCount: Int get() = ringStart.size - 1

    val pointCount: Int get() = lon.size

    /**
     * Per-ring bounding boxes, so a window can reject a ring in four comparisons
     * rather than by walking its points.
     *
     * Computed on load rather than stored, unlike everything derived in
     * [AirportIndexCodec]. That format stores its trigonometry because 120,000
     * `sin`/`cos` calls on a cold ART measured 73 ms; this is a few thousand
     * comparisons with no transcendental in sight, and it keeps the format one
     * table smaller.
     */
    val ringMinLon: FloatArray = FloatArray(ringCount)
    val ringMaxLon: FloatArray = FloatArray(ringCount)
    val ringMinLat: FloatArray = FloatArray(ringCount)
    val ringMaxLat: FloatArray = FloatArray(ringCount)

    init {
        for (ring in 0 until ringCount) {
            val from = ringStart[ring]
            val to = ringStart[ring + 1]
            require(to > from) { "Ring $ring is empty" }
            var minLon = lon[from]
            var maxLon = lon[from]
            var minLat = lat[from]
            var maxLat = lat[from]
            for (i in from + 1 until to) {
                if (lon[i] < minLon) minLon = lon[i]
                if (lon[i] > maxLon) maxLon = lon[i]
                if (lat[i] < minLat) minLat = lat[i]
                if (lat[i] > maxLat) maxLat = lat[i]
            }
            ringMinLon[ring] = minLon
            ringMaxLon[ring] = maxLon
            ringMinLat[ring] = minLat
            ringMaxLat[ring] = maxLat
        }
    }

    companion object {
        /** An outline with no land, for previews and for a failed load. */
        val Empty: WorldOutline = WorldOutline(FloatArray(0), FloatArray(0), intArrayOf(0))
    }
}
