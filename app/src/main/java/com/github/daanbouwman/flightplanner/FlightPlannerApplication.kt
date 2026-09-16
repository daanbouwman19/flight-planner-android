package com.github.daanbouwman.flightplanner

import android.app.Application
import androidx.work.Configuration
import com.github.daanbouwman.flightplanner.core.database.airport.AirportAssetInstaller
import com.github.daanbouwman.flightplanner.index.AirportIndexProvider
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class FlightPlannerApplication : Application(), Configuration.Provider {

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

    /**
     * WorkManager, initialised on demand rather than at process start.
     *
     * Nothing in this app schedules work. WorkManager is here because Glance
     * renders every widget session inside one of its workers, and its default
     * `androidx.startup` initializer would then build `WorkManagerImpl` — its
     * executors, schedulers, a Room database and a `ForceStopRunnable` that
     * queries it — inside the `InitializationProvider`, before `onCreate`, on
     * *every* launch of the app, widget or no widget. That is spend against the
     * cold-start budget for a feature the launch does not use. The manifest
     * removes that initializer and this getter replaces it: WorkManager builds
     * itself the first time something asks for it, which is the widget path and
     * only the widget path. A getter runs nothing until it is called, so this
     * class's start-up cost is unchanged.
     */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()
}
