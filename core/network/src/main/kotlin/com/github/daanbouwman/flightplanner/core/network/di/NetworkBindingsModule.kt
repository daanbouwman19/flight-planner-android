package com.github.daanbouwman.flightplanner.core.network.di

import com.github.daanbouwman.flightplanner.core.network.avwx.AvwxMetarClient
import com.github.daanbouwman.flightplanner.core.network.avwx.DefaultAvwxMetarClient
import com.github.daanbouwman.flightplanner.core.network.noaa.DefaultNoaaMetarClient
import com.github.daanbouwman.flightplanner.core.network.noaa.NoaaMetarClient
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Binds the weather client interfaces to their implementations. */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class NetworkBindingsModule {

    @Binds
    @Singleton
    abstract fun bindNoaaMetarClient(impl: DefaultNoaaMetarClient): NoaaMetarClient

    @Binds
    @Singleton
    abstract fun bindAvwxMetarClient(impl: DefaultAvwxMetarClient): AvwxMetarClient
}
