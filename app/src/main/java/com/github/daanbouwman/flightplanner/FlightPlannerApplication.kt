package com.github.daanbouwman.flightplanner

import android.app.Application
import com.github.daanbouwman.flightplanner.core.database.airport.AirportAssetInstaller
import com.github.daanbouwman.flightplanner.index.AirportIndexProvider
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class FlightPlannerApplication : Application() {

    @Inject
    lateinit var airportIndexProvider: AirportIndexProvider

    @Inject
    lateinit var airportAssetInstaller: AirportAssetInstaller

    override fun onCreate() {
        super.onCreate()

        // Kicked off here rather than from the first screen that needs it, so the
        // read and decode overlap Activity creation and first-frame inflation
        // instead of queueing behind them. It is a start, not a wait: nothing on
        // this thread blocks on the result.
        airportIndexProvider.warm()

        // Same shape for the database copy. On every launch but the first this
        // is a sidecar read and a file-exists check on an IO thread; on the first
        // it is the ~30 MB copy that used to run synchronously inside Hilt's
        // provision of the database, on whichever thread first asked for a DAO —
        // the main thread, during the Plan screen's first composition.
        airportAssetInstaller.warm()
    }
}
