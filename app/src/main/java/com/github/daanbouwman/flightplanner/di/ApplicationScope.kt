package com.github.daanbouwman.flightplanner.di

import javax.inject.Qualifier

/**
 * Marks the process-lifetime [kotlinx.coroutines.CoroutineScope].
 *
 * Work started in this scope outlives every screen, ViewModel and Activity on
 * purpose: the airport index is built once per process and kept, so its
 * coroutine must not be tied to whatever composable happened to ask for it
 * first.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
