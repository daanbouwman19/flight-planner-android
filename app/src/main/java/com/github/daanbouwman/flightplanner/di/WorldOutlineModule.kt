package com.github.daanbouwman.flightplanner.di

import com.github.daanbouwman.flightplanner.world.AssetWorldOutlineLoader
import com.github.daanbouwman.flightplanner.world.WorldOutlineLoader
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds the one [WorldOutlineLoader] implementation.
 *
 * `@Binds` rather than `@Provides`: the implementation is already `@Singleton`
 * and constructor-injected, so this only tells Dagger which type satisfies the
 * interface and generates no factory of its own.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class WorldOutlineModule {

    @Binds
    @Singleton
    abstract fun bindWorldOutlineLoader(implementation: AssetWorldOutlineLoader): WorldOutlineLoader
}
