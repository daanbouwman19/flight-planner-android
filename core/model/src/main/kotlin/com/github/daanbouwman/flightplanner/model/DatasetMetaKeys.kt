package com.github.daanbouwman.flightplanner.model

/**
 * Keys of the `dataset_meta` table in the shipped airport database.
 *
 * They live in `:core:model` because both sides need them and neither can see
 * the other: the ETL is a pure-JVM tool, the Room entity is in an Android
 * library. Sharing the constants is what keeps the writer and the reader from
 * drifting apart.
 */
object DatasetMetaKeys {
    const val SOURCE = "source"
    const val UPSTREAM_MODIFIED = "upstream_last_modified"
    const val AIRPORT_COUNT = "airport_count"
    const val RUNWAY_COUNT = "runway_count"
    const val FILTER_TIER = "filter_tier"
}
