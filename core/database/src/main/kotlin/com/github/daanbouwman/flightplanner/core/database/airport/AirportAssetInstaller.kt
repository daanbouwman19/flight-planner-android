package com.github.daanbouwman.flightplanner.core.database.airport

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AirportAssetInstaller"

/**
 * Copies the shipped airport database out of the APK.
 *
 * Room has a `createFromAsset` helper that would do this, but it throws
 * `Pre-Package Database is not supported when an SQLiteDriver is configured`
 * when combined with `BundledSQLiteDriver`. Since the bundled driver is the
 * whole reason this app behaves identically across OEM SQLite builds, the copy
 * is done here instead and Room simply opens the resulting file.
 *
 * Freshness is decided by a version sidecar written by the ETL, which holds
 * Room's schema identity hash. That single value changes whenever the entities
 * change *or* the dataset is regenerated, so a stale copy can never survive
 * either — which matters, because Room's own reaction to a schema mismatch is
 * to drop every table and hand the app an empty database.
 */
@Singleton
class AirportAssetInstaller @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    /** Copies the asset if needed and returns the on-disk database file. */
    fun ensureInstalled(): File {
        val target = context.getDatabasePath(AirportDatabase.NAME)
        val marker = File(target.parentFile, "${AirportDatabase.NAME}.version")
        val expected = readAssetVersion()

        if (target.exists() && expected != null && marker.readTextOrNull() == expected) {
            return target
        }

        Log.i(TAG, "Installing airport database (version $expected)")
        target.parentFile?.mkdirs()

        // Remove the old database and any journal sidecars; leaving a -wal file
        // beside a replaced database resurrects pages from the previous one.
        for (path in listOf(target.path, "${target.path}-wal", "${target.path}-shm", "${target.path}-journal")) {
            File(path).delete()
        }

        // Copy via a temporary file so an interrupted install cannot leave a
        // truncated database that looks complete on the next launch.
        val staging = File(target.parentFile, "${AirportDatabase.NAME}.tmp")
        staging.delete()
        context.assets.open(AirportDatabase.ASSET_PATH).use { input ->
            staging.outputStream().use { output -> input.copyTo(output, DEFAULT_BUFFER_SIZE * 8) }
        }
        check(staging.renameTo(target)) { "Could not move the airport database into place" }

        if (expected != null) marker.writeText(expected) else marker.delete()
        Log.i(TAG, "Installed ${target.length()} bytes")
        return target
    }

    private fun readAssetVersion(): String? = runCatching {
        context.assets.open(AirportDatabase.ASSET_VERSION_PATH).bufferedReader().use { it.readText().trim() }
    }.getOrElse {
        Log.w(TAG, "No version sidecar beside the airport database; it will be reinstalled on every launch", it)
        null
    }

    private fun File.readTextOrNull(): String? = runCatching { readText().trim() }.getOrNull()
}
