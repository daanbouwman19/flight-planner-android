package com.github.daanbouwman.flightplanner.aircraftart

import com.github.daanbouwman.flightplanner.model.AircraftSpec
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * A traced outline as the build left it, so a run limited with `--only` can
 * still emit the whole table and the whole contact sheet.
 */
class CachedShape(val id: String, val spanCm: Int, val lengthCm: Int, val points: Int, val path: String) {
    val spanM: Double get() = spanCm / 100.0
    val lengthM: Double get() = lengthCm / 100.0

    fun write(dir: File) {
        dir.mkdirs()
        val json = buildJsonObject {
            put("id", id); put("spanCm", spanCm); put("lengthCm", lengthCm); put("points", points); put("path", path)
        }
        File(dir, "$id.json").writeText(json.toString())
    }

    companion object {
        fun of(built: Built) = CachedShape(
            id = built.source.id,
            spanCm = built.outline.spanCm,
            lengthCm = built.outline.lengthCm,
            points = built.outline.pointCount,
            path = built.outline.pathData(),
        )

        fun read(file: File): CachedShape {
            val o = Json.parseToJsonElement(file.readText()).jsonObject
            return CachedShape(
                id = o.getValue("id").jsonPrimitive.content,
                spanCm = o.getValue("spanCm").jsonPrimitive.int,
                lengthCm = o.getValue("lengthCm").jsonPrimitive.int,
                points = o.getValue("points").jsonPrimitive.int,
                path = o.getValue("path").jsonPrimitive.content,
            )
        }

        fun readAll(dir: File): Map<String, CachedShape> =
            (dir.listFiles { f -> f.extension == "json" } ?: emptyArray()).map(::read).associateBy { it.id }
    }
}

/**
 * The review page: every outline, its measurements against the published
 * figures, and the seed rows still waiting for one.
 *
 * Generated from the same cached outlines the Kotlin table is, so what the page
 * shows is exactly what the app will draw.
 */
object ContactSheet {

    fun write(
        file: File,
        sources: List<ShapeSource>,
        shapes: Map<String, CachedShape>,
        seed: List<AircraftSpec>,
    ) {
        val seedByKey = seed.associateBy { RowKey(it.manufacturer, it.variant) }
        val mapped = sources.filter { it.id in shapes }.flatMap { it.rows }.toSet()

        val shapeArray = buildJsonArray {
            for (source in sources) {
                val shape = shapes[source.id] ?: continue
                val shapeRows = source.rows
                val specs = shapeRows.mapNotNull { seedByKey[it] }
                val spanDev = source.publishedSpanM?.let { (shape.spanM - it) / it * 100 }
                val lengthDev = source.publishedLengthM?.let { (shape.lengthM - it) / it * 100 }
                val ok = listOfNotNull(spanDev, lengthDev).all { kotlin.math.abs(it) <= source.tolerancePct }
                add(
                    buildJsonObject {
                        put("id", source.id)
                        put("label", source.label)
                        put("provenance", source.provenance.name)
                        put("spanCm", shape.spanCm)
                        put("lengthCm", shape.lengthCm)
                        put("spanM", shape.spanM)
                        put("lengthM", shape.lengthM)
                        source.publishedSpanM?.let { put("pubSpanM", it) }
                        source.publishedLengthM?.let { put("pubLengthM", it) }
                        spanDev?.let { put("spanDev", it) }
                        lengthDev?.let { put("lengthDev", it) }
                        put("tolerance", source.tolerancePct)
                        put("ok", ok)
                        put("verified", source.verified)
                        put("points", shape.points)
                        put("path", shape.path)
                        put("notes", source.notes)
                        put("rows", JsonArray(shapeRows.map { JsonPrimitive("${it.manufacturer} ${it.variant}") }))
                        put("codes", JsonArray(specs.map { it.icaoCode }.distinct().map(::JsonPrimitive)))
                        put("categories", JsonArray(specs.map { it.category }.distinct().map(::JsonPrimitive)))
                    },
                )
            }
        }
        val missing = seed.filter { RowKey(it.manufacturer, it.variant) !in mapped }.map { "${it.displayName} (${it.icaoCode})" }
        val data = buildJsonObject {
            put("generated", ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z")))
            put("rowCount", seed.size)
            put("missing", JsonArray(missing.map(::JsonPrimitive)))
            put("shapes", shapeArray)
        }
        val template = ContactSheet::class.java.getResource("/contact-sheet.html")!!.readText()
        // `</` inside the JSON would end the script element early.
        val html = template.replace("/*DATA*/", data.toString().replace("</", "<\\/"))
        file.parentFile.mkdirs()
        file.writeText(html)
    }
}
