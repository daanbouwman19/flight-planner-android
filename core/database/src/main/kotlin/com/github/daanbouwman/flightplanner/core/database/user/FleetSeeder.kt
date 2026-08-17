package com.github.daanbouwman.flightplanner.core.database.user

import android.content.Context
import android.util.Log
import com.github.daanbouwman.flightplanner.model.AircraftSpec
import com.github.daanbouwman.flightplanner.model.FleetCsv
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "FleetSeeder"
private const val SEED_ASSET = "seed/aircrafts.csv"

/**
 * Imports the bundled starter fleet on first run.
 *
 * The seed is the same `aircrafts.csv` the desktop application ships, so the
 * two start from an identical fleet, and a user's own exported file can be
 * imported through the same parser.
 */
@Singleton
class FleetSeeder @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val aircraftDao: AircraftDao,
) {

    /**
     * Seeds the fleet if it is empty.
     *
     * Deliberately a no-op once any airframe exists: re-seeding would resurrect
     * airframes the user deleted and reset the flown flags they have been
     * accumulating.
     *
     * @return the number of airframes inserted.
     */
    suspend fun seedIfEmpty(): Int = withContext(Dispatchers.IO) {
        if (aircraftDao.count() > 0) return@withContext 0
        val aircraft = readSeedFleet() ?: return@withContext 0
        aircraftDao.insertAll(aircraft.map { it.toEntity(isCustom = false) })
        Log.i(TAG, "Seeded ${aircraft.size} aircraft")
        aircraft.size
    }

    /**
     * Replaces the bundled airframes with a fresh copy of the seed, leaving
     * user-added ones untouched. Backs "Restore default fleet" in Settings.
     */
    suspend fun restoreDefaults(): Int = withContext(Dispatchers.IO) {
        val aircraft = readSeedFleet() ?: return@withContext 0
        aircraftDao.deleteSeeded()
        aircraftDao.insertAll(aircraft.map { it.toEntity(isCustom = false) })
        aircraft.size
    }

    private fun readSeedFleet(): List<AircraftSpec>? {
        val text = runCatching { context.assets.open(SEED_ASSET).bufferedReader().use { it.readText() } }
            .getOrElse {
                Log.e(TAG, "Could not read $SEED_ASSET", it)
                return null
            }

        val result = FleetCsv.parse(text)
        result.warnings.forEach { Log.w(TAG, "Seed fleet: $it") }
        if (result.aircraft.isEmpty()) {
            Log.e(TAG, "Seed fleet parsed to zero aircraft")
            return null
        }
        return result.aircraft
    }
}

internal fun AircraftSpec.toEntity(isCustom: Boolean): AircraftEntity = AircraftEntity(
    id = if (id == 0) 0 else id,
    manufacturer = manufacturer,
    variant = variant,
    icaoCode = icaoCode,
    flown = flown,
    rangeNm = rangeNm,
    category = category,
    cruiseSpeedKt = cruiseSpeedKt,
    dateFlown = dateFlown,
    takeoffDistanceM = takeoffDistanceMeters,
    isCustom = isCustom,
)

fun AircraftEntity.toSpec(): AircraftSpec = AircraftSpec(
    id = id,
    manufacturer = manufacturer,
    variant = variant,
    icaoCode = icaoCode,
    flown = flown,
    rangeNm = rangeNm,
    category = category,
    cruiseSpeedKt = cruiseSpeedKt,
    dateFlown = dateFlown,
    takeoffDistanceMeters = takeoffDistanceM,
    isCustom = isCustom,
)
