package com.github.daanbouwman.flightplanner.widget

import android.content.Context
import com.github.daanbouwman.flightplanner.launch.WidgetPreviewStamp
import com.github.daanbouwman.flightplanner.settings.SettingsRepository
import com.github.daanbouwman.flightplanner.world.WorldOutlineLoader
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * The Hilt graph, as seen from code that Hilt does not construct.
 *
 * Every other injection site in the app is an Activity or a ViewModel, which
 * Hilt builds and fills itself. A Glance widget is neither: the launcher asks
 * the system, the system starts a `GlanceAppWidgetReceiver` (a `BroadcastReceiver`
 * Glance instantiates), and `provideGlance` runs with nothing but a `Context`.
 * An entry point is Hilt's door for exactly that case — a typed view of the
 * singleton component, fetched from the `Application` — and this is the
 * app's first one. Keep it to what the widget needs; it is not a service
 * locator.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun challengeSource(): DailyChallengeSource

    /** "Aircraft of the day". It needs the fleet and nothing else — see [DailyAircraftSource]. */
    fun aircraftSource(): DailyAircraftSource

    fun settingsRepository(): SettingsRepository
    fun widgetPreviewStamp(): WidgetPreviewStamp

    /** The coastline under the challenge's route — the same 19 kB asset the route cards draw. */
    fun worldOutlineLoader(): WorldOutlineLoader

    companion object {
        fun from(context: Context): WidgetEntryPoint =
            EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
    }
}
