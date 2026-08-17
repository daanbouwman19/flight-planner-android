package com.github.daanbouwman.flightplanner.core.database.airport

import com.github.daanbouwman.flightplanner.routing.AirportIndex

/**
 * The searchable text for every airport, held slot-aligned with [AirportIndex].
 *
 * **Position `i` in every array here is slot `i` in [AirportIndex]** — that
 * alignment *is* the slot mapping, and it is the whole point of the type. A
 * scorer walks these arrays, keeps the best positions, and hands them straight
 * back to the index for coordinates, runway length, flags and packed code
 * without a lookup table in between. [ids] is carried as well so a consumer
 * holding only this index can still resolve a result to an airport row.
 *
 * Nothing here interprets the text. Ranking lives in `:core:routing`
 * (`SearchScorer`) so that it stays a pure-JVM, unit-testable function of the
 * arrays; this module's job is to produce them, off the startup path, and to be
 * honest about when they are not ready yet.
 */
class AirportNameIndex internal constructor(
    val size: Int,
    /** Upstream airport ids, slot-aligned. */
    val ids: IntArray,
    /** Airport names, slot-aligned. Never null; empty when the row went missing. */
    val names: Array<String>,
    /** Municipalities, slot-aligned. Null where upstream publishes none. */
    val municipalities: Array<String?>,
    /** ISO 3166-1 alpha-2 country codes, slot-aligned. */
    val countries: Array<String>,
) {
    fun nameOf(slot: Int): String = names[slot]

    fun municipalityOf(slot: Int): String? = municipalities[slot]

    fun countryOf(slot: Int): String = countries[slot]

    fun idOf(slot: Int): Int = ids[slot]

    /**
     * Airport ids for a set of slots, in the order given.
     *
     * The intended path out of a ranked scan: score the arrays, keep the best
     * slots, turn them into ids here, and fetch display rows for those — and
     * only those — in one query.
     */
    fun idsOf(slots: IntArray): List<Int> = slots.map { ids[it] }
}

/**
 * Whether the name index is available.
 *
 * A caller that finds anything other than [Ready] must fall back — search by
 * ICAO code alone, which needs only the in-memory index — rather than wait.
 * Building takes a full-table text read and blocking a keystroke on it would
 * undo exactly the startup work the index split bought.
 */
sealed interface NameIndexState {

    /** Never asked for. Building has not been started. */
    data object Idle : NameIndexState

    /** A build is in flight. */
    data object Building : NameIndexState

    data class Ready(val index: AirportNameIndex) : NameIndexState

    /**
     * The build failed. Search stays ICAO-only; a later `prepare` retries.
     * Present so a failure degrades the feature instead of hanging it.
     */
    data class Failed(val cause: Throwable) : NameIndexState
}

/**
 * Joins [rows] onto [index] by airport id.
 *
 * [rows] must be ordered by id ascending — [AirportDao.nameRows] imposes that —
 * so the join is a binary search per slot with no hash map and no boxing. An id
 * present in the index but absent from the rows yields empty text rather than
 * failing: a missing display string is a cosmetic problem, and refusing to build
 * the whole index over one row would turn it into a functional one.
 */
internal fun buildAirportNameIndex(
    index: AirportIndex,
    rows: List<AirportNameRow>,
): AirportNameIndex {
    val rowIds = IntArray(rows.size) { rows[it].id }
    val size = index.size
    val ids = IntArray(size)
    val names = Array(size) { "" }
    val municipalities = arrayOfNulls<String>(size)
    val countries = Array(size) { "" }

    for (slot in 0 until size) {
        val id = index.ids[slot]
        ids[slot] = id
        val pos = rowIds.binarySearch(id)
        if (pos >= 0) {
            val row = rows[pos]
            names[slot] = row.name
            municipalities[slot] = row.municipality
            countries[slot] = row.country
        }
    }

    return AirportNameIndex(size, ids, names, municipalities, countries)
}
