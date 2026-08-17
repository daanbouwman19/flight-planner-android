package com.github.daanbouwman.flightplanner.core.database.repository

import com.github.daanbouwman.flightplanner.core.database.user.AircraftDao
import com.github.daanbouwman.flightplanner.core.database.user.FleetSeeder
import com.github.daanbouwman.flightplanner.core.database.user.toEntity
import com.github.daanbouwman.flightplanner.core.database.user.toSpec
import com.github.daanbouwman.flightplanner.model.AircraftSpec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The user's fleet, in domain terms.
 *
 * Reads are `Flow` so a flown toggle repaints every screen showing the airframe
 * without anyone re-querying; writes are `suspend` and return only what the
 * caller cannot derive.
 */
interface FleetRepository {

    fun observeFleet(): Flow<List<AircraftSpec>>

    /** Backs the "not flown" badge and the generation mode of the same name. */
    fun observeNotFlownCount(): Flow<Int>

    suspend fun fleet(): List<AircraftSpec>

    suspend fun byId(id: Int): AircraftSpec?

    suspend fun count(): Int

    /**
     * Marks an airframe flown or not flown, stamping the date.
     *
     * The stamp is part of the same statement rather than a follow-up write:
     * "flown" and "when" are one fact, and the desktop app treats clearing the
     * flag as clearing the date too.
     */
    suspend fun setFlown(id: Int, flown: Boolean, on: LocalDate = LocalDate.now())

    /** The desktop app's "mark all aircraft as not flown"; clears the dates as well. */
    suspend fun markAllNotFlown()

    /** Inserts a user-added airframe, ignoring [spec]'s id. Returns the assigned id. */
    suspend fun add(spec: AircraftSpec): Int

    suspend fun update(spec: AircraftSpec)

    suspend fun delete(spec: AircraftSpec)

    /** Replaces the bundled airframes with a fresh seed; user-added ones survive. */
    suspend fun restoreDefaults(): Int

    /** Seeds the starter fleet on first run. A no-op once any airframe exists. */
    suspend fun seedIfEmpty(): Int
}

@Singleton
internal class DefaultFleetRepository @Inject constructor(
    private val aircraftDao: AircraftDao,
    private val seeder: FleetSeeder,
) : FleetRepository {

    override fun observeFleet(): Flow<List<AircraftSpec>> =
        aircraftDao.observeAll().map { rows -> rows.map { it.toSpec() } }

    override fun observeNotFlownCount(): Flow<Int> = aircraftDao.observeNotFlownCount()

    override suspend fun fleet(): List<AircraftSpec> = aircraftDao.all().map { it.toSpec() }

    override suspend fun byId(id: Int): AircraftSpec? = aircraftDao.byId(id)?.toSpec()

    override suspend fun count(): Int = aircraftDao.count()

    override suspend fun setFlown(id: Int, flown: Boolean, on: LocalDate) =
        aircraftDao.setFlown(id, flown, if (flown) on.toString() else null)

    override suspend fun markAllNotFlown() = aircraftDao.markAllNotFlown()

    override suspend fun add(spec: AircraftSpec): Int {
        // id 0 lets Room autogenerate; isCustom is forced true because this is
        // the only path a non-seed airframe can arrive by, and a custom airframe
        // that claimed otherwise would be deleted by "restore default fleet".
        val rowId = aircraftDao.upsert(spec.copy(id = 0).toEntity(isCustom = true))
        return rowId.toInt()
    }

    override suspend fun update(spec: AircraftSpec) =
        aircraftDao.update(spec.toEntity(isCustom = spec.isCustom))

    override suspend fun delete(spec: AircraftSpec) =
        aircraftDao.delete(spec.toEntity(isCustom = spec.isCustom))

    override suspend fun restoreDefaults(): Int = seeder.restoreDefaults()

    override suspend fun seedIfEmpty(): Int = seeder.seedIfEmpty()
}
