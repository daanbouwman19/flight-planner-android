package com.github.daanbouwman.flightplanner.core.network.di

import io.kotest.matchers.shouldBe
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Request
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * The weather client identifies itself on the wire with the one string the
 * whole app uses. Asserted as the literal rather than through the constant, so
 * that editing the constant fails here as well as in the globe's own loader
 * test — the two clients are separate on purpose, and this is what keeps what
 * they say about themselves the same.
 */
class NetworkModuleTest {

    private lateinit var server: MockWebServer

    @BeforeTest
    fun start() {
        server = MockWebServer()
        server.start()
    }

    @AfterTest
    fun stop() {
        server.close()
    }

    @Test
    fun `every request carries the app's User-Agent`() {
        server.enqueue(MockResponse.Builder().body("{}").build())
        val client = NetworkModule.provideOkHttpClient()

        client.newCall(Request.Builder().url(server.url("/metar")).build()).execute().use {
            it.code shouldBe 200
        }

        server.takeRequest().headers["User-Agent"] shouldBe
            "FlightPlannerAndroid/1.0 (+https://github.com/daanbouwman19/flight-planner-android)"
    }
}
