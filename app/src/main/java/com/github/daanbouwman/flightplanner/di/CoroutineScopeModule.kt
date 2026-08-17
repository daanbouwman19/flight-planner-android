package com.github.daanbouwman.flightplanner.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CoroutineScopeModule {

    /**
     * A scope that lives as long as the process.
     *
     * [SupervisorJob] rather than a plain `Job` because a failure in one piece of
     * process-scoped work — say the index decode hitting a corrupt asset — must
     * not cancel every other consumer of this scope. `Dispatchers.Default` is the
     * base: the only work here is CPU-bound, and the loader switches to
     * `Dispatchers.IO` itself for the file read.
     */
    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
