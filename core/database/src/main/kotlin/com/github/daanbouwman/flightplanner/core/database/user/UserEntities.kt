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
 *
 * ### Version 3 stores `raw` plus the provider supplement, and nothing else
 *
 * Cloud layers, present weather, wind, visibility and the altimeter are **not
 * columns**. They are re-derived from [raw] by
 * [com.github.daanbouwman.flightplanner.model.MetarParser] on read, through the
 * same `buildMetar` a fresh fetch uses, so a cache hit and a live response are
 * the same object.
 *
 * That is why version 2's flat decoded columns are gone. They were a
 * hand-maintained second copy of information already in this table, and v2's own
 * KDoc warned that a forgotten field would read as data "flickering between two
 * visits of the same station within fifteen minutes". **There is now nothing to
 * forget** — the columns that remain are exactly the facts a METAR text does not
 * carry.
 *
 * No column has a Kotlin default value, deliberately: a field added here is a
 * compile error at both mapping directions until it is wired, which is the guard
 * v2 lacked.
 */
@Entity(tableName = "metar_cache")
data class MetarCacheEntity(
    @PrimaryKey
    @ColumnInfo(name = "station")
    val station: String,

    /** The only lossless field, and the source every decoded value is re-derived from. */
    @ColumnInfo(name = "raw")
    val raw: String,

    /** Epoch millis; drives the freshness TTL. */
    @ColumnInfo(name = "fetched_at")
    val fetchedAt: Long,

    @ColumnInfo(name = "flight_rules")
    val flightRules: String?,

    @ColumnInfo(name = "report_kind")
    val reportKind: String?,

    @ColumnInfo(name = "observation_epoch_seconds")
    val observationEpochSeconds: Long?,

    @ColumnInfo(name = "temperature_c")
    val temperatureC: Double?,

    @ColumnInfo(name = "dewpoint_c")
    val dewpointC: Double?,

    @ColumnInfo(name = "sea_level_pressure_hpa")
    val seaLevelPressureHpa: Double?,

    @ColumnInfo(name = "precip_in")
    val hourlyPrecipInches: Double?,

    @ColumnInfo(name = "precip_3h_in")
    val precip3hInches: Double?,

    @ColumnInfo(name = "precip_6h_in")
    val precip6hInches: Double?,

    @ColumnInfo(name = "precip_24h_in")
    val precip24hInches: Double?,

    @ColumnInfo(name = "snow_depth_in")
    val snowDepthInches: Double?,

    @ColumnInfo(name = "latitude")
    val latitude: Double?,

    @ColumnInfo(name = "longitude")
    val longitude: Double?,

    /** **Feet.** NOAA sends `elev` in metres; converted in `:core:network`. */
    @ColumnInfo(name = "elevation_ft")
    val elevationFt: Int?,

    @ColumnInfo(name = "station_name")
    val stationName: String?,
)
