package com.github.daanbouwman.flightplanner.di

import com.github.daanbouwman.flightplanner.index.AirportIndexProvider
import com.github.daanbouwman.flightplanner.index.DefaultAirportIndexProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds the one [AirportIndexProvider] implementation.
 *
 * `@Binds` rather than `@Provides`: the implementation is already
 * `@Singleton` and constructor-injected, so this only tells Dagger which type
 * satisfies the interface and generates no factory of its own.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AirportIndexModule {

    @Binds
    @Singleton
    abstract fun bindAirportIndexProvider(
        implementation: DefaultAirportIndexProvider,
    ): AirportIndexProvider
}
