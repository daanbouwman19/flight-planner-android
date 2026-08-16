package com.github.daanbouwman.flightplanner.core.database.airport

import android.content.Context
import com.github.daanbouwman.flightplanner.routing.AirportIndex
import com.github.daanbouwman.flightplanner.routing.AirportIndexCodec
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Timing breakdown of an index load, so regressions are visible rather than felt. */
data class IndexLoadTiming(
    val readMillis: Long,
    val decodeMillis: Long,
    val airports: Int,
) {
    val totalMillis: Long get() = readMillis + decodeMillis
}

data class LoadedIndex(val index: AirportIndex, val timing: IndexLoadTiming)

/**
 * Restores the in-memory airport index from a prebuilt asset.
 *
 * Building the index from database rows was the slowest thing the app did.
 * Measured on device, fetching a column costs ~11 ms per 24,000 rows for a
 * number and ~30 ms for text, against 27 ms for a bare scan — the cost is per
 * *column fetched*, i.e. JNI crossings, not data volume. Even trimmed to six
 * numeric columns that is still ~150,000 boundary crossings on every launch.
 *
 * The index depends only on the shipped database, so the ETL builds it once —
 * trigonometry and spatial bands included — and stores it as a flat blob;
 * loading is then a file read plus a dozen bulk array copies, with no arithmetic
 * left to do. The database is still opened for everything else — runway detail,
 * airport names, search — just not for this.
 */
@Singleton
class AirportIndexLoader @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    suspend fun load(): LoadedIndex = withContext(Dispatchers.IO) {
        val readStart = System.nanoTime()
        val bytes = context.assets.open(INDEX_ASSET_PATH).use { it.readBytes() }
        val readMillis = (System.nanoTime() - readStart) / 1_000_000

        val decodeStart = System.nanoTime()
        val index = AirportIndexCodec.decode(bytes)
        val decodeMillis = (System.nanoTime() - decodeStart) / 1_000_000

        LoadedIndex(index, IndexLoadTiming(readMillis, decodeMillis, index.size))
    }

    private companion object {
        const val INDEX_ASSET_PATH = "databases/airports.index"
    }
}
