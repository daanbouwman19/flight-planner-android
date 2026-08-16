package com.github.daanbouwman.flightplanner.core.database.airport

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One runway **end**.
 *
 * There is deliberately no foreign key to `airports`: the ETL already
 * guarantees referential integrity (and an asset-integrity test re-checks it),
 * and skipping the constraint keeps the read-only database smaller and its
 * queries free of trigger overhead.
 */
@Entity(
    tableName = "runways",
    indices = [Index(value = ["airport_id"])],
)
data class RunwayEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: Int,

    @ColumnInfo(name = "airport_id")
    val airportId: Int,

    @ColumnInfo(name = "ident")
    val ident: String,

    /** Null when not published upstream and not derivable from [ident]. */
    @ColumnInfo(name = "true_heading")
    val trueHeading: Double?,

    @ColumnInfo(name = "length_ft")
    val lengthFt: Int,

    @ColumnInfo(name = "width_ft")
    val widthFt: Int,

    /** Raw upstream string, preserved for display. */
    @ColumnInfo(name = "surface")
    val surface: String,

    /** Ordinal of [com.github.daanbouwman.flightplanner.model.SurfaceKind]. */
    @ColumnInfo(name = "surface_kind")
    val surfaceKind: Int,

    @ColumnInfo(name = "lat")
    val lat: Double?,

    @ColumnInfo(name = "lon")
    val lon: Double?,

    @ColumnInfo(name = "elevation_ft")
    val elevationFt: Int,

    @ColumnInfo(name = "lighted")
    val lighted: Boolean,
)
