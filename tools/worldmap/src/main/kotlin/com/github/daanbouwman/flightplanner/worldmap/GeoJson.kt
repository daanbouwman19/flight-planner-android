package com.github.daanbouwman.flightplanner.worldmap

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * One closed ring of land, in degrees.
 *
 * Longitude and latitude are kept in parallel arrays rather than as a list of
 * points because every step after this — simplification, quantisation, encoding
 * — walks them as columns.
 */
class Ring(val lon: DoubleArray, val lat: DoubleArray) {
    init {
        require(lon.size == lat.size) { "Ring has ${lon.size} longitudes and ${lat.size} latitudes" }
    }

    val size: Int get() = lon.size

    val lonSpan: Double get() = lon.max() - lon.min()
    val latSpan: Double get() = lat.max() - lat.min()
}

/**
 * Reads land rings out of a Natural Earth GeoJSON snapshot.
 *
 * Only the geometry is read. `ne_110m_land` carries three properties —
 * `featurecla`, `scalerank`, `min_zoom` — and none of them survives to the
 * device: a route card draws one silhouette in one colour, so a per-feature
 * attribute has nothing to select.
 *
 * **`land`, not `coastline`.** Land is closed polygons, which can be filled;
 * coastline is open lines, which cannot. A ring's first point repeats as its
 * last in the source, and that is kept — the renderer closes the path anyway,
 * but a ring that arrives already closed cannot be closed wrongly.
 *
 * Interior rings (holes) are read and kept in source order, immediately after
 * the exterior ring they belong to. They are not marked: the renderer fills the
 * whole outline with an even-odd rule, which turns an enclosed ring into a hole
 * without the format having to say so.
 */
object GeoJson {

    private val json = Json { ignoreUnknownKeys = true }

    fun readLandRings(file: File): List<Ring> {
        val root = json.parseToJsonElement(file.readText()).jsonObject
        val type = root["type"]?.jsonPrimitive?.content
        check(type == "FeatureCollection") { "Expected a FeatureCollection, got $type" }

        val rings = ArrayList<Ring>()
        val features = root["features"]?.jsonArray ?: error("GeoJSON has no features")
        for (feature in features) {
            val geometry = feature.jsonObject["geometry"]?.jsonObject
                ?: error("Feature without geometry")
            rings += ringsOf(geometry)
        }
        check(rings.isNotEmpty()) { "No land rings in ${file.path}" }
        return rings
    }

    private fun ringsOf(geometry: JsonObject): List<Ring> {
        val coordinates = geometry["coordinates"]?.jsonArray ?: error("Geometry without coordinates")
        return when (val type = geometry["type"]?.jsonPrimitive?.content) {
            "Polygon" -> coordinates.map { ring(it.jsonArray) }
            "MultiPolygon" -> coordinates.flatMap { polygon -> polygon.jsonArray.map { ring(it.jsonArray) } }
            else -> error("Unsupported geometry type '$type'; expected Polygon or MultiPolygon")
        }
    }

    private fun ring(points: JsonArray): Ring {
        val lon = DoubleArray(points.size)
        val lat = DoubleArray(points.size)
        points.forEachIndexed { i, point ->
            val pair = point.jsonArray
            // GeoJSON is longitude-first. Reading it latitude-first produces a
            // map that looks like a plausible but wrong planet, so it is worth
            // stating rather than inferring from the variable names.
            lon[i] = pair[0].jsonPrimitive.content.toDouble()
            lat[i] = pair[1].jsonPrimitive.content.toDouble()
        }
        return Ring(lon, lat)
    }
}
