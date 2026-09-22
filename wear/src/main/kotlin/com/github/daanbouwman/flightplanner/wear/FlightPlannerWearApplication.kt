package com.github.daanbouwman.flightplanner.wear

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * The watch app's `Application`.
 *
 * Deliberately empty. `:app`'s equivalent warms the airport index and starts
 * the database install here, because it has a 500 ms cold-start budget and a
 * splash screen to release; this one has neither, and the same reasoning
 * applies more strictly rather than less — everything put in
 * `Application.onCreate` or a `@Singleton` constructor runs before the first
 * frame, and a watch has less to spend on it than a phone. The index load is
 * where it belongs instead: inside `WatchRouteFeedViewModel`, on a coroutine,
 * with a state the face can show while it runs.
 */
@HiltAndroidApp
class FlightPlannerWearApplication : Application()
