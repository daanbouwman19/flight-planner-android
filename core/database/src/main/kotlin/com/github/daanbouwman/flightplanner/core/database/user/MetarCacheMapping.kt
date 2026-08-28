package com.github.daanbouwman.flightplanner.core.database.user

import com.github.daanbouwman.flightplanner.model.Metar
import com.github.daanbouwman.flightplanner.model.MetarSupplement
import com.github.daanbouwman.flightplanner.model.buildMetar
import com.github.daanbouwman.flightplanner.model.toSupplement

/**
 * The cache's mapping, both directions.
 *
 * The guard against a forgotten field is **structural, not a convention**. The
 * cache stores `raw` plus a [MetarSupplement], and everything else about an
 * observation is re-derived from `raw` — so most of a [Metar] cannot be dropped
 * here, because it is never stored. What remains is a flat 1:1 between two types
 * that both have *no default values*, so adding a field to either is
 * "No value passed for parameter" until it is wired.
 *
 * `MetarCacheMappingTest` then closes the last gap — a field wired to the
 * *wrong* column — with a whole-object round trip.
 */
internal fun MetarCacheEntity.toSupplement(): MetarSupplement = MetarSupplement(
    flightRulesCode = flightRules,
    reportKindCode = reportKind,
    observationEpochSeconds = observationEpochSeconds,
    temperatureC = temperatureC,
    dewpointC = dewpointC,
    seaLevelPressureHpa = seaLevelPressureHpa,
    hourlyPrecipInches = hourlyPrecipInches,
    precip3hInches = precip3hInches,
    precip6hInches = precip6hInches,
    precip24hInches = precip24hInches,
    snowDepthInches = snowDepthInches,
    latitude = latitude,
    longitude = longitude,
    elevationFt = elevationFt,
    stationName = stationName,
)

/**
 * Rebuilds the observation.
 *
 * Runs the same `buildMetar` a fresh network response runs, over the same raw
 * text, so a cached station and a just-fetched one are indistinguishable.
 */
fun MetarCacheEntity.toMetar(): Metar = buildMetar(station, raw, toSupplement())

fun Metar.toEntity(fetchedAt: Long): MetarCacheEntity {
    val supplement = toSupplement()
    return MetarCacheEntity(
        station = station,
        raw = raw,
        fetchedAt = fetchedAt,
        flightRules = supplement.flightRulesCode,
        reportKind = supplement.reportKindCode,
        observationEpochSeconds = supplement.observationEpochSeconds,
        temperatureC = supplement.temperatureC,
        dewpointC = supplement.dewpointC,
        seaLevelPressureHpa = supplement.seaLevelPressureHpa,
        hourlyPrecipInches = supplement.hourlyPrecipInches,
        precip3hInches = supplement.precip3hInches,
        precip6hInches = supplement.precip6hInches,
        precip24hInches = supplement.precip24hInches,
        snowDepthInches = supplement.snowDepthInches,
        latitude = supplement.latitude,
        longitude = supplement.longitude,
        elevationFt = supplement.elevationFt,
        stationName = supplement.stationName,
    )
}
