package com.github.daanbouwman.flightplanner.core.database.airport

import androidx.room.useReaderConnection
import com.github.daanbouwman.flightplanner.routing.AirportIndex
import com.github.daanbouwman.flightplanner.routing.AirportIndexBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Timing breakdown of an index load, so regressions are visible rather than felt. */
data class IndexLoadTiming(
    val readMillis: Long,
    val buildMillis: Long,
    val airports: Int,
    val rejectedCodes: Int,
) {
    val totalMillis: Long get() = readMillis + buildMillis
}

data class LoadedIndex(val index: AirportIndex, val timing: IndexLoadTiming)

/**
 * Builds the in-memory airport index straight from a SQLite cursor.
 *
 * Two measurements shaped this, taken on device over the real 24,321-row table:
 *
 *  - Fetching a column costs roughly 11 ms per 24,000 rows for a number and
 *    30 ms for text, against 27 ms for the bare scan. The cost is per *column*,
 *    not per row — it is JNI crossings, plus a UTF-8 decode and an allocation
 *    for text. So the query fetches six columns and no text at all: codes are
 *    packed integers, and the four boolean/enum attributes are combined into a
 *    single value by SQLite rather than fetched separately.
 *  - A DAO returning `List<AirportEntity>` additionally allocates 24,000 entity
 *    objects and ~100,000 strings only for the index to copy their fields out
 *    and discard them.
 *
 * There is deliberately no `ORDER BY`: the builder sorts by runway length with a
 * counting sort, so asking SQLite to sort as well would be the same work twice,
 * and an indexed sort would turn a sequential scan into random row lookups.
 */
@Singleton
class AirportIndexLoader @Inject constructor(
    private val database: AirportDatabase,
) {

    suspend fun load(): LoadedIndex {
        var builder: AirportIndexBuilder? = null

        val readMillis = measureMillis {
            database.useReaderConnection { connection ->
                val count = connection.usePrepared("SELECT COUNT(*) FROM airports") { statement ->
                    if (statement.step()) statement.getInt(0) else 0
                }
                val local = AirportIndexBuilder(count)
                builder = local

                connection.usePrepared(SELECT_FOR_INDEX) { statement ->
                    while (statement.step()) {
                        local.add(
                            id = statement.getInt(COL_ID),
                            packedCode = statement.getInt(COL_CODE),
                            latitude = statement.getDouble(COL_LAT),
                            longitude = statement.getDouble(COL_LON),
                            longestRunway = statement.getInt(COL_LONGEST_RUNWAY),
                            packedFlags = statement.getInt(COL_FLAGS),
                        )
                    }
                }
            }
        }

        val loaded = checkNotNull(builder) { "Airport index was never populated" }

        // Trigonometry and the sort are pure CPU, so they belong off the IO path.
        val buildStart = System.nanoTime()
        val index = withContext(Dispatchers.Default) { loaded.build() }
        val buildMillis = (System.nanoTime() - buildStart) / 1_000_000

        return LoadedIndex(
            index,
            IndexLoadTiming(readMillis, buildMillis, index.size, loaded.rejectedCodes),
        )
    }

    private inline fun measureMillis(block: () -> Unit): Long {
        val start = System.nanoTime()
        block()
        return (System.nanoTime() - start) / 1_000_000
    }

    private companion object {
        /**
         * Six values, no text.
         *
         * `code_packed` is precomputed by the ETL. The flags are combined here
         * instead, because that is four bit operations SQLite does essentially
         * for free — unlike the string work packing a code would need.
         * The layout mirrors [AirportIndex.packFlags]: bit 0 has_icao, bit 1
         * hard surface, bit 2 lighting, bits 3-4 size class.
         */
        const val SELECT_FOR_INDEX = """
            SELECT id,
                   code_packed,
                   lat,
                   lon,
                   longest_runway_ft,
                   has_icao | (has_hard_surface << 1) | (has_lighting << 2) | (size_class << 3)
            FROM airports
        """

        const val COL_ID = 0
        const val COL_CODE = 1
        const val COL_LAT = 2
        const val COL_LON = 3
        const val COL_LONGEST_RUNWAY = 4
        const val COL_FLAGS = 5
    }
}
