package com.github.daanbouwman.flightplanner

import android.app.Application
import com.github.daanbouwman.flightplanner.index.AirportIndexProvider
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class FlightPlannerApplication : Application() {

    @Inject
    lateinit var airportIndexProvider: AirportIndexProvider

    override fun onCreate() {
        super.onCreate()

        // Kicked off here rather than from the first screen that needs it, so the
        // read and decode overlap Activity creation and first-frame inflation
        // instead of queueing behind them. It is a start, not a wait: nothing on
        // this thread blocks on the result.
        airportIndexProvider.warm()
    }
}
