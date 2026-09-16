package com.github.daanbouwman.flightplanner.di

import com.github.daanbouwman.flightplanner.launch.DefaultLaunchStore
import com.github.daanbouwman.flightplanner.launch.LastRouteStore
import com.github.daanbouwman.flightplanner.launch.WidgetPreviewStamp
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds the one `launch` DataStore behind its two faces.
 *
 * Same shape as [SettingsModule]: the implementation is `@Singleton` and
 * constructor-injected, so these only say which type satisfies each interface.
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class LaunchModule {

    @Binds
    @Singleton
    abstract fun bindLastRouteStore(impl: DefaultLaunchStore): LastRouteStore

    @Binds
    @Singleton
    abstract fun bindWidgetPreviewStamp(impl: DefaultLaunchStore): WidgetPreviewStamp
}
