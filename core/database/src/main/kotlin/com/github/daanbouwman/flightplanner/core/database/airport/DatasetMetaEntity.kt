package com.github.daanbouwman.flightplanner.core.database.airport

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Provenance of the shipped dataset: upstream source, its `Last-Modified` date,
 * row counts, and the filter tier used. Surfaced on the Settings screen so a
 * user can tell which snapshot they are flying against, and asserted by the
 * asset-integrity test.
 */
@Entity(tableName = "dataset_meta")
data class DatasetMetaEntity(
    @PrimaryKey
    @ColumnInfo(name = "key")
    val key: String,

    @ColumnInfo(name = "value")
    val value: String,
)
