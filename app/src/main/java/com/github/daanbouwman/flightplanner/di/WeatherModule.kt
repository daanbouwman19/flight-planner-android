package com.github.daanbouwman.flightplanner.di

import com.github.daanbouwman.flightplanner.weather.DefaultWeatherRepository
import com.github.daanbouwman.flightplanner.weather.WeatherRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class WeatherModule {

    @Binds
    @Singleton
    abstract fun bindWeatherRepository(impl: DefaultWeatherRepository): WeatherRepository
}
