package com.github.daanbouwman.flightplanner.core.database.di

import com.github.daanbouwman.flightplanner.core.database.repository.AirportRepository
import com.github.daanbouwman.flightplanner.core.database.repository.DefaultAirportRepository
import com.github.daanbouwman.flightplanner.core.database.repository.DefaultFleetRepository
import com.github.daanbouwman.flightplanner.core.database.repository.DefaultLogbookRepository
import com.github.daanbouwman.flightplanner.core.database.repository.DefaultMetarCacheRepository
import com.github.daanbouwman.flightplanner.core.database.repository.FleetRepository
import com.github.daanbouwman.flightplanner.core.database.repository.LogbookRepository
import com.github.daanbouwman.flightplanner.core.database.repository.MetarCacheRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds the repository interfaces to their implementations.
 *
 * Separate from `DatabaseModule` on purpose: that module provides the databases
 * and DAOs — the things that own a file handle — while this one only chooses an
 * implementation. Keeping them apart means a test can replace a repository
 * without also having to reason about Room.
 *
 * The bindings are `@Singleton` because each implementation holds state a second
 * instance would silently duplicate: the airport repository's name index most of
 * all, which is a full-table read that must happen once.
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAirportRepository(impl: DefaultAirportRepository): AirportRepository

    @Binds
    @Singleton
    abstract fun bindFleetRepository(impl: DefaultFleetRepository): FleetRepository

    @Binds
    @Singleton
    abstract fun bindLogbookRepository(impl: DefaultLogbookRepository): LogbookRepository

    @Binds
    @Singleton
    abstract fun bindMetarCacheRepository(impl: DefaultMetarCacheRepository): MetarCacheRepository
}
