package com.github.daanbouwman.flightplanner.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Qualifier

/**
 * Marks the CPU-bound dispatcher.
 *
 * Injected rather than referenced as `Dispatchers.Default` at each call site so
 * that a test can substitute a scheduler it controls. Without it, work handed to
 * the real `Default` is invisible to `advanceUntilIdle`, and a ViewModel test can
 * only ever observe the state *before* that work finishes — which is how a suite
 * ends up asserting `Generating` and calling it a pass.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

@Module
@InstallIn(SingletonComponent::class)
object DispatcherModule {

    @Provides
    @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default
}
