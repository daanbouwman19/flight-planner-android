package com.github.daanbouwman.flightplanner.core.network.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import com.github.daanbouwman.flightplanner.model.UserAgent
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    /**
     * Identifies this app to weather providers with no SLA (NOAA) or a
     * per-key one (AVWX) — docs/PLAN.md risk #10. The string itself lives in
     * `:core:model` as [UserAgent], shared with the globe's tile client.
     */
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder().header(UserAgent.HEADER, UserAgent.VALUE).build(),
            )
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
