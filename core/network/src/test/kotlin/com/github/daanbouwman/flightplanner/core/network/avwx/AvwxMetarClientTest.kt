package com.github.daanbouwman.flightplanner.core.network.avwx

import com.github.daanbouwman.flightplanner.model.Ceiling
import com.github.daanbouwman.flightplanner.model.CloudCover
import com.github.daanbouwman.flightplanner.model.CloudLayer
import com.github.daanbouwman.flightplanner.model.FlightRules
import com.github.daanbouwman.flightplanner.model.SkyCover
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class AvwxMetarClientTest {

    private val server = MockWebServer()
    private lateinit var client: DefaultAvwxMetarClient

    @BeforeTest
    fun setUp() {
        server.start()
        client = DefaultAvwxMetarClient(http = OkHttpClient.Builder().build(), json = Json { ignoreUnknownKeys = true })
        client.baseUrl = server.url("/api/metar").toString()
    }

    @AfterTest
    fun tearDown() {
        server.close()
    }

    @Test
    fun `an AVWX report is now as rich as a NOAA one, decoded from its raw text`() = runTest {
        // This test used to assert the opposite — that wind and ceiling were
        // null, because the client decoded four fields and AVWX reports were
        // therefore *cloudless by construction*. That was one of the four ways
        // an IFR field came to be drawn with a sun on it. The DTO is still four
        // fields; the richness now comes from parsing `raw`, which AVWX supplies.
        server.enqueue(
            MockResponse(
                body = """
                {
                  "raw": "EHAM 271700Z VRB03KT 6SM BKN008 18/12 Q1013",
                  "flight_rules": "IFR",
                  "station": "EHAM",
                  "time": {"repr": "271700Z", "dt": "2026-08-27T17:00:00Z"},
                  "clouds": [{"repr": "BKN008", "type": "BKN", "base": 8}]
                }
                """.trimIndent(),
            ),
        )

        val metar = client.fetchMetar("EHAM", "test-key")!!

        metar.station shouldBe "EHAM"
        metar.flightRules shouldBe FlightRules.IFR
        metar.observationTime shouldBe "271700Z"
        metar.observationEpochSeconds shouldBe 1_787_850_000L

        // From the raw text, none of which the DTO reads:
        metar.windVariable shouldBe true
        metar.windSpeedKt shouldBe 3
        metar.visibilityStatuteMiles shouldBe 6.0
        metar.skyCover shouldBe SkyCover.Layers(listOf(CloudLayer(CloudCover.BROKEN, baseFt = 800)))
        metar.ceiling shouldBe Ceiling.At(ft = 800, indefinite = false)
        metar.temperatureC!! shouldBe (18.0 plusOrMinus 0.001)
    }

    @Test
    fun `AVWX's hundreds-of-feet cloud base is ignored in favour of the raw text`() = runTest {
        // AVWX's `clouds[].base` is in HUNDREDS of feet where NOAA's is in feet.
        // Decoding it would put an 800 ft deck at 8 ft. Not decoding it at all
        // removes the trap rather than remembering to dodge it.
        server.enqueue(
            MockResponse(
                body = """
                {"raw": "EHAM 271700Z 31006KT 6SM OVC012 18/12 Q1013",
                 "station": "EHAM",
                 "clouds": [{"repr": "OVC012", "type": "OVC", "base": 12}]}
                """.trimIndent(),
            ),
        )

        client.fetchMetar("EHAM", "test-key")!!.ceiling shouldBe Ceiling.At(ft = 1_200, indefinite = false)
    }

    @Test
    fun `the station key is read, and falls back when absent`() = runTest {
        // AVWX's field is `station`. The old DTO read `san`, which AVWX does not
        // have — so it always fell back. Harmless, but dead.
        server.enqueue(MockResponse(body = """{"raw":"x 271700Z","station":"EHAM"}"""))
        client.fetchMetar("ZZZZ", "k")!!.station shouldBe "EHAM"

        server.enqueue(MockResponse(body = """{"raw":"271700Z 31006KT"}"""))
        client.fetchMetar("LFPG", "k")!!.station shouldBe "LFPG"
    }

    @Test
    fun `an offset timestamp parses, and an unparsable one is simply absent`() = runTest {
        // ISO_INSTANT rejects the "+00:00" offset form, which AVWX does emit, so
        // OffsetDateTime is the right parser here.
        server.enqueue(
            MockResponse(
                body = """{"raw":"x 271700Z","station":"EHAM","time":{"dt":"2026-08-27T17:00:00+00:00"}}""",
            ),
        )
        client.fetchMetar("EHAM", "k")!!.observationEpochSeconds shouldBe 1_787_850_000L

        server.enqueue(
            MockResponse(body = """{"raw":"x 271700Z","station":"EHAM","time":{"dt":"not a date"}}"""),
        )
        client.fetchMetar("EHAM", "k")!!.observationEpochSeconds.shouldBeNull()
    }

    @Test
    fun `a report with no raw text is not worth a row`() = runTest {
        // `raw` is what every decoded field derives from, and the cache refuses
        // to store a row it could not faithfully return.
        server.enqueue(MockResponse(body = """{"flight_rules":"VFR","station":"EHAM"}"""))
        client.fetchMetar("EHAM", "k").shouldBeNull()

        server.enqueue(MockResponse(body = """{"raw":"   ","station":"EHAM"}"""))
        client.fetchMetar("EHAM", "k").shouldBeNull()
    }

    @Test
    fun `the key is sent raw, with no Bearer prefix`() = runTest {
        server.enqueue(MockResponse(body = """{"raw":"x 271700Z","station":"EHAM"}"""))

        client.fetchMetar("EHAM", "abc123")

        server.takeRequest().headers["Authorization"] shouldBe "abc123"
    }

    @Test
    fun `204 means no data`() = runTest {
        server.enqueue(MockResponse(code = 204))

        client.fetchMetar("ZZZZ", "test-key").shouldBeNull()
    }

    @Test
    fun `400 means the station was not found`() = runTest {
        server.enqueue(MockResponse(code = 400))

        client.fetchMetar("ZZZZ", "test-key").shouldBeNull()
    }

    @Test
    fun `an unparsable body is null rather than throwing`() = runTest {
        server.enqueue(MockResponse(body = "not json"))

        client.fetchMetar("EHAM", "test-key").shouldBeNull()
    }
}
