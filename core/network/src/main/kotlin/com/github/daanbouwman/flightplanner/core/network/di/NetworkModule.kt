package com.github.daanbouwman.flightplanner.core.network.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    /**
     * Identifies this app to weather providers with no SLA (NOAA) or a
     * per-key one (AVWX) — docs/PLAN.md risk #10. A repo URL, not the user's
     * own contact details, which must never reach a third-party service in a
     * header sent on their behalf.
     */
    private const val USER_AGENT =
        "FlightPlannerAndroid/1.0 (+https://github.com/daanbouwman19/flight-planner-android)"

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("User-Agent", USER_AGENT).build())
        }
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    /**
     * Shared by both weather clients. `ignoreUnknownKeys` because NOAA's
     * payload in particular carries many fields the domain model has no use
     * for (`receiptTime`, `qcField`, `lat`, `lon`, `elev`, `name`, …).
     */
    @Provides
    @Singleton
    fun provideJson(): Json = Json { ignoreUnknownKeys = true }
}
