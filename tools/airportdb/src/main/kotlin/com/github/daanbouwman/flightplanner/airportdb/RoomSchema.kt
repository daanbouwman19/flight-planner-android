package com.github.daanbouwman.flightplanner.airportdb

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * The parts of Room's exported schema JSON that this tool needs.
 *
 * Emitting DDL from the exported schema rather than hand-writing it is what
 * makes the prepackaged database safe: the table definitions, the indices and
 * the `room_master_table` identity hash all come from the same source Room will
 * validate against at runtime. Hand-written DDL drifts, and the failure mode is
 * a crash on first launch on every device.
 */
@Serializable
data class RoomSchemaFile(
    val formatVersion: Int,
    val database: RoomSchemaDatabase,
)

@Serializable
data class RoomSchemaDatabase(
    val version: Int,
    val identityHash: String,
    val entities: List<RoomSchemaEntity>,
    /**
     * Room emits the `room_master_table` creation and the identity-hash insert
     * here, so they never have to be reconstructed by hand.
     */
    val setupQueries: List<String> = emptyList(),
)

@Serializable
data class RoomSchemaEntity(
    val tableName: String,
    val createSql: String,
    val indices: List<RoomSchemaIndex> = emptyList(),
)

@Serializable
data class RoomSchemaIndex(
    val name: String,
    @SerialName("createSql") val createSql: String,
)

object RoomSchema {

    private val json = Json { ignoreUnknownKeys = true }

    fun read(file: File): RoomSchemaDatabase {
        require(file.isFile) { "Room schema not found: $file (run :core:database:assembleDebug first)" }
        return json.decodeFromString<RoomSchemaFile>(file.readText()).database
    }

    /**
     * Every statement needed to create an empty database Room will accept:
     * tables, indices, then Room's own bookkeeping.
     */
    fun RoomSchemaDatabase.ddl(): List<String> = buildList {
        for (entity in entities) {
            add(entity.createSql.substituteTableName(entity.tableName))
            for (index in entity.indices) {
                add(index.createSql.substituteTableName(entity.tableName))
            }
        }
        addAll(setupQueries)
    }

    private fun String.substituteTableName(tableName: String): String =
        replace("\${TABLE_NAME}", tableName)
}
