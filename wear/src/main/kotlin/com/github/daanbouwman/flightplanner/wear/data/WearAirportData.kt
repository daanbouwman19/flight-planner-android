package com.github.daanbouwman.flightplanner.wear.data

import android.content.Context
import com.github.daanbouwman.flightplanner.model.AircraftSpec
import com.github.daanbouwman.flightplanner.model.FleetCsv
import com.github.daanbouwman.flightplanner.routing.AirportIndex
import com.github.daanbouwman.flightplanner.routing.AirportIndexCodec
import com.github.daanbouwman.flightplanner.routing.WorldOutline
import com.github.daanbouwman.flightplanner.routing.WorldOutlineCodec
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Everything the watch needs to generate and draw a route, read from assets.
 *
 * The phone splits this across `AirportIndexLoader`, `AirportAssetInstaller`,
 * `FleetRepository` and a Room database. None of that comes to the watch,
 * because none of it is needed for the one thing this app does:
 *
 * - **The index, not the database.** `:app/src/main/assets` is 7.7 MB, of which
 *   6.5 MB is SQLite carrying names, municipalities, elevations and runway
 *   detail. Route generation reads none of them — it needs coordinates, runway
 *   lengths and codes, all of which are in the 1.5 MB prebuilt index — and this
 *   app shows codes rather than names. So the watch ships the index alone and
 *   the database's 30 MB first-run extraction never happens here at all.
 * - **The CSV, not Room.** The fleet on the watch is the bundled seed fleet and
 *   nothing else: there is no editing, no flown flag to write back, and so no
 *   row to own. `FleetCsv` already parses the exact file `:app` seeds from, in
 *   `:core:model`, with no Android on its imports. Reading it directly costs one
 *   call; Room would cost a schema, a migration policy and a DAO.
 *
 * The consequence to keep in mind is that the watch's airframe ids are **not**
 * the phone's. `FleetCsv.parse` assigns none, and the phone's are Room row ids
 * that depend on what the user has added and removed. That is why the handoff
 * names an airframe by what it is rather than by id — see
 * [com.github.daanbouwman.flightplanner.handoff.WatchRouteLink].
 *
 * The primary constructor takes the asset read as a function and is `internal`,
 * so tests can hand it bytes without an instrumented device; the `@Inject`
 * secondary maps the graph's `Context` onto it. That is the same shape
 * `:app`'s `DailyChallengeSource` has, and for the same reason.
 */
@Singleton
class WearAirportData internal constructor(
    private val readAsset: (String) -> ByteArray,
) {

    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : this(
        readAsset = { path -> context.assets.open(path).use { it.readBytes() } },
    )

    /** The airport index, decoded from the prebuilt asset. */
    suspend fun index(): AirportIndex = withContext(Dispatchers.IO) {
        AirportIndexCodec.decode(readAsset(INDEX_ASSET))
    }

    /**
     * The seed fleet.
     *
     * Parse warnings are dropped rather than surfaced: this file ships inside
     * the APK, so a warning here is a build-time defect rather than something
     * the wearer can act on, and the phone's import screen is where a
     * user-supplied CSV gets its warnings shown.
     */
    suspend fun fleet(): List<AircraftSpec> = withContext(Dispatchers.IO) {
        FleetCsv.parse(readAsset(FLEET_ASSET).decodeToString())
            .aircraft
            // `FleetCsv` gives every row id 0, which `RouteMode.Specific` could
            // not tell apart. Nothing on the watch picks one airframe yet, but
            // leaving fifty-odd rows sharing an id is a trap to walk into later,
            // so they are numbered on the way out. These numbers are local to
            // this process and mean nothing to the phone.
            .mapIndexed { position, spec -> spec.copy(id = position + 1) }
    }

    /** The coastline the route is drawn over. */
    suspend fun outline(): WorldOutline = withContext(Dispatchers.IO) {
        WorldOutlineCodec.decode(readAsset(OUTLINE_ASSET))
    }

    private companion object {
        // What `:wear/build.gradle.kts`'s `copyWearAssets` brings across from
        // `:app`, at the paths it has there.
        const val INDEX_ASSET = "databases/airports.index"
        const val FLEET_ASSET = "seed/aircrafts.csv"
        const val OUTLINE_ASSET = "maps/land.outline"
    }
}
