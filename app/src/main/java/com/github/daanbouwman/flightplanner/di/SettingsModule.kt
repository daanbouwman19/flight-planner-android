package com.github.daanbouwman.flightplanner.di

import com.github.daanbouwman.flightplanner.settings.DefaultSettingsRepository
import com.github.daanbouwman.flightplanner.settings.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds the one [SettingsRepository] implementation.
 *
 * `@Binds` rather than `@Provides`: the implementation is already `@Singleton`
 * and constructor-injected, so this only tells Dagger which type satisfies the
 * interface and generates no factory of its own.
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class SettingsModule {

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: DefaultSettingsRepository): SettingsRepository
}
