package com.github.daanbouwman.flightplanner.core.network.noaa

import com.github.daanbouwman.flightplanner.model.Ceiling
import com.github.daanbouwman.flightplanner.model.FlightRules
import com.github.daanbouwman.flightplanner.model.SkyCover
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

private const val TWO_STATION_RESPONSE = """
[
  {
    "icaoId": "KJFK", "receiptTime": "2026-08-27 17:03", "obsTime": 1787850180,
    "reportTime": "2026-08-27T17:03:00.000Z", "temp": 23.9, "dewp": 21.1,
    "wdir": 150, "wspd": 9, "visib": "10+", "altim": 1016.7,
    "rawOb": "SPECI KJFK 271703Z 15009KT 10SM TS FEW020 BKN047CB BKN095 OVC150 24/21 A3002 RMK",
    "clouds": [
      {"cover":"FEW","base":2000},{"cover":"BKN","base":4700},
      {"cover":"BKN","base":9500},{"cover":"OVC","base":15000}
    ],
    "fltCat": "VFR"
  },
  {
    "icaoId": "EHAM", "receiptTime": "2026-08-27 17:00", "obsTime": 1787850000,
    "reportTime": "2026-08-27T17:00:00.000Z", "temp": 18.0, "dewp": 12.0,
    "wdir": "VRB", "wspd": 3, "visib": "6", "altim": 1013.2,
    "rawOb": "EHAM 271700Z VRB03KT 6SM CLR 18/12 Q1013",
    "clouds": [],
    "fltCat": null
  }
]
"""

class NoaaMetarClientTest {

    private val server = MockWebServer()
    private lateinit var client: DefaultNoaaMetarClient

    @BeforeTest
    fun setUp() {
        server.start()
        client = DefaultNoaaMetarClient(
            http = OkHttpClient.Builder().build(),
            json = Json { ignoreUnknownKeys = true },
        )
        client.baseUrl = server.url("/api/data/metar").toString()
    }

    @AfterTest
    fun tearDown() {
        server.close()
    }

    @Test
    fun `an empty station list makes no request`() = runTest {
        client.fetchMetars(emptyList()) shouldBe emptyList()
    }

    @Test
    fun `the request batches every station into one comma-joined ids param`() = runTest {
        server.enqueue(MockResponse(body = TWO_STATION_RESPONSE))

        client.fetchMetars(listOf("KJFK", "EHAM"))

        val recorded = server.takeRequest()
        recorded.url.queryParameter("ids") shouldBe "KJFK,EHAM"
        recorded.url.queryParameter("format") shouldBe "json"
    }

    @Test
    fun `visibility, wind and ceiling decode field for field`() = runTest {
        server.enqueue(MockResponse(body = TWO_STATION_RESPONSE))

        val result = client.fetchMetars(listOf("KJFK", "EHAM"))

        val jfk = result.first { it.station == "KJFK" }
        jfk.flightRules shouldBe FlightRules.VFR // fltCat present, used directly
        // The first BROKEN-or-worse layer, not the FEW below it.
        jfk.ceiling shouldBe Ceiling.At(ft = 4_700, indefinite = false)
        jfk.visibilityStatuteMiles shouldBe 10.0
        jfk.windDirectionDeg shouldBe 150
        (kotlin.math.abs(jfk.altimeterInHg!! - 30.02) < 0.01) shouldBe true

        // EHAM is the two-fix demonstration. Both of these were losses under the
        // previous design: `VRB` became "no wind direction reported", and an
        // empty `clouds[]` became "no ceiling" — indistinguishable from a
        // genuinely clear sky.
        val eham = result.first { it.station == "EHAM" }
        eham.windVariable shouldBe true
        eham.windDirectionDeg.shouldBeNull() // varying, so there is no mean direction
        eham.windSpeedKt shouldBe 3
        eham.skyCover shouldBe SkyCover.Clear // affirmatively clear, from the raw `CLR`
        eham.ceiling shouldBe Ceiling.Unlimited
        eham.flightRules shouldBe FlightRules.VFR // derived: clear sky, 6 SM
        eham.visibilityStatuteMiles shouldBe 6.0
    }

    @Test
    fun `an unreported sky stays unknown even with good visibility`() = runTest {
        // The bug, at the client boundary: 10 miles of visibility and no cloud
        // group is not VFR, because the sky could be a 200 ft overcast.
        server.enqueue(
            MockResponse(
                body = """
                [{"icaoId":"XXXX","clouds":[],
                  "rawOb":"METAR XXXX 271800Z 31006KT 10SM 22/17 Q1008"}]
                """.trimIndent(),
            ),
        )

        val metar = client.fetchMetars(listOf("XXXX")).single()
        metar.skyUnknown shouldBe true
        metar.ceiling shouldBe Ceiling.Unknown
        metar.flightRules shouldBe FlightRules.UNKNOWN
    }

    @Test
    fun `two observations for one station collapse to the newest`() = runTest {
        // NOAA's `hours` parameter returns every observation in the window. It is
        // not sent, but a plain mapNotNull would silently yield two rows for one
        // station if it ever were, and whichever landed last would win at random.
        server.enqueue(
            MockResponse(
                body = """
                [{"icaoId":"KDEN","obsTime":1787850000,
                  "rawOb":"METAR KDEN 271700Z 31006KT 10SM CLR 20/05 A3000"},
                 {"icaoId":"KDEN","obsTime":1787853600,
                  "rawOb":"METAR KDEN 271800Z 31008KT 10SM OVC020 21/06 A2999"}]
                """.trimIndent(),
            ),
        )

        val result = client.fetchMetars(listOf("KDEN"))
        result.size shouldBe 1
        result.single().observationEpochSeconds shouldBe 1_787_853_600L
        result.single().windSpeedKt shouldBe 8
    }

    @Test
    fun `a non-2xx response yields an empty list rather than throwing`() = runTest {
        server.enqueue(MockResponse(code = 503))

        client.fetchMetars(listOf("KJFK")) shouldBe emptyList()
    }

    @Test
    fun `an unparsable body yields an empty list rather than throwing`() = runTest {
        server.enqueue(MockResponse(body = "not json"))

        client.fetchMetars(listOf("KJFK")) shouldBe emptyList()
    }

    @Test
    fun `one malformed element does not discard the rest of the batch`() = runTest {
        val body = "[{\"noIcaoId\": true}, " + TWO_STATION_RESPONSE.trim().removePrefix("[")
        server.enqueue(MockResponse(body = body))

        val result = client.fetchMetars(listOf("KJFK", "EHAM"))

        result.map { it.station }.toSet() shouldBe setOf("KJFK", "EHAM")
    }

    @Test
    fun `cancelling the collecting coroutine cancels the underlying call`() = runTest {
        // No response ever enqueued, so the request would otherwise hang.
        val job = async { client.fetchMetars(listOf("KJFK")) }
        job.cancel()
        // Reaching this line without the test hanging is the assertion: a
        // blocking `execute()` call would keep the socket read running past
        // cancellation instead of the `Call` itself being cancelled.
    }
}
