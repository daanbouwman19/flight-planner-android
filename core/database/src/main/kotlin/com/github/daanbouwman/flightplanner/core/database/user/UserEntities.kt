package com.github.daanbouwman.flightplanner.core.database.user

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * An airframe in the user's fleet.
 *
 * Column-compatible with the desktop app's `aircraft` table so an existing
 * `aircrafts.csv` imports unchanged. `takeoff_distance_m` stays in **metres**,
 * as it is upstream; the conversion to feet happens once, at the point where a
 * runway requirement is derived.
 */
@Entity(
    tableName = "aircraft",
    indices = [Index(value = ["flown"]), Index(value = ["category"])],
)
data class AircraftEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Int = 0,

    @ColumnInfo(name = "manufacturer")
    val manufacturer: String,

    @ColumnInfo(name = "variant")
    val variant: String,

    @ColumnInfo(name = "icao_code")
    val icaoCode: String,

    @ColumnInfo(name = "flown")
    val flown: Boolean,

    @ColumnInfo(name = "range_nm")
    val rangeNm: Int,

    @ColumnInfo(name = "category")
    val category: String,

    @ColumnInfo(name = "cruise_speed_kt")
    val cruiseSpeedKt: Int,

    /** ISO-8601 date, null when never flown. */
    @ColumnInfo(name = "date_flown")
    val dateFlown: String?,

    @ColumnInfo(name = "takeoff_distance_m")
    val takeoffDistanceM: Int?,

    /** True for user-added airframes; the seed importer only replaces non-custom rows. */
    @ColumnInfo(name = "is_custom")
    val isCustom: Boolean = false,
)

/**
 * A logged flight.
 *
 * Airports are stored as ICAO **text**, not as a foreign key. There is no
 * choice about this — the airports live in a different database file — but it
 * is also the right design: ICAO codes survive a dataset refresh, whereas
 * OurAirports row ids need not.
 */
@Entity(
    tableName = "flight_log",
    indices = [
        Index(value = ["aircraft_id"]),
        Index(value = ["date"]),
        Index(value = ["departure_icao"]),
        Index(value = ["arrival_icao"]),
    ],
)
data class FlightLogEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "departure_icao")
    val departureIcao: String,

    @ColumnInfo(name = "arrival_icao")
    val arrivalIcao: String,

    @ColumnInfo(name = "aircraft_id")
    val aircraftId: Int,

    /** ISO-8601 date. */
    @ColumnInfo(name = "date")
    val date: String,

    @ColumnInfo(name = "distance_nm")
    val distanceNm: Int?,
)

/**
 * Cached METAR, keyed by station.
 *
 * Deliberately in the *user* database rather than alongside the airports: the
 * desktop app keeps its `metar_cache` in the airport database, where refreshing
 * the dataset would wipe it.
 */
@Entity(tableName = "metar_cache")
data class MetarCacheEntity(
    @PrimaryKey
    @ColumnInfo(name = "station")
    val station: String,

    @ColumnInfo(name = "raw")
    val raw: String,

    @ColumnInfo(name = "flight_rules")
    val flightRules: String?,

    @ColumnInfo(name = "observation_time")
    val observationTime: String?,

    @ColumnInfo(name = "observation_instant")
    val observationInstant: String?,

    /** Epoch millis; drives the freshness TTL. */
    @ColumnInfo(name = "fetched_at")
    val fetchedAt: Long,
)
