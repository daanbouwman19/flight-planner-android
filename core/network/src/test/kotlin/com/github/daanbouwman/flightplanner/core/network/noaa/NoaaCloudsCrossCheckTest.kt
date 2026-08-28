package com.github.daanbouwman.flightplanner.core.network.noaa

import com.github.daanbouwman.flightplanner.model.Ceiling
import com.github.daanbouwman.flightplanner.model.CloudCover
import com.github.daanbouwman.flightplanner.model.ConvectiveCloud
import com.github.daanbouwman.flightplanner.model.MetarParser
import com.github.daanbouwman.flightplanner.model.SkyCover
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test

/**
 * Cross-checks this app's raw-METAR parser against NOAA's own decode of the
 * same string.
 *
 * ### Why this test exists rather than a production fallback
 *
 * Production reads `rawOb` and ignores NOAA's `cover`/`clouds[]`, because two
 * decoders for one string is what produced the defect this redesign fixes. The
 * robustness a fallback would have bought — a parser gap degrading to AWC's
 * answer instead of a wrong render — is bought here instead, at test time, where
 * a divergence is *information* about which strings the parser gets wrong rather
 * than a silent difference in behaviour between two code paths.
 *
 * Growing the fixture corpus below is how confidence in the parser grows. Every
 * entry is a real response captured from NOAA on 2026-08-27.
 */
class NoaaCloudsCrossCheckTest {

    private val json = Json { ignoreUnknownKeys = true }

    /** Real station objects, trimmed to the fields this comparison needs. */
    private val corpus = listOf(
        // Four layers, one convective.
        """{"icaoId":"KJFK","cover":"OVC","clouds":[{"cover":"FEW","base":1500},
           {"cover":"BKN","base":4300},{"cover":"BKN","base":11000},{"cover":"OVC","base":13000}],
           "rawOb":"METAR KJFK 271851Z 17020G27KT 4SM -TSRA BR FEW015 BKN043CB BKN110 OVC130 23/22 A3003"}""",
        // Obscuration — the OVX case.
        """{"icaoId":"PACD","cover":"OVX","clouds":[{"cover":"OVX","base":200}],"vertVis":2,
           "rawOb":"SPECI PACD 271823Z 14015G22KT 1/2SM FG VV002 12/12 A3011"}""",
        // The three flavours of affirmatively clear.
        """{"icaoId":"EHAM","cover":"CAVOK","clouds":[],
           "rawOb":"METAR EHAM 271855Z 31003KT CAVOK 22/17 Q1008 NOSIG"}""",
        """{"icaoId":"MMCL","cover":"SKC","clouds":[],
           "rawOb":"METAR MMCL 271851Z 22005KT 10SM SKC 38/26 A2980 RMK HZY"}""",
        """{"icaoId":"CYYL","cover":"CLR","clouds":[],
           "rawOb":"METAR CYYL 271800Z AUTO 15004KT 110V200 9SM CLR 19/08 A3013"}""",
        // Scattered only — a layer list with no ceiling in it.
        """{"icaoId":"KLCH","cover":"SCT","clouds":[{"cover":"SCT","base":2700}],
           "rawOb":"SPECI KLCH 271830Z VRB05KT 10SM SCT027 30/23 A2999"}""",
        // Four broken decks.
        """{"icaoId":"KSYR","cover":"BKN","clouds":[{"cover":"BKN","base":3200},
           {"cover":"BKN","base":6000},{"cover":"BKN","base":12000},{"cover":"BKN","base":20000}],
           "rawOb":"METAR KSYR 271754Z 30015G25KT 10SM TS BKN032 BKN060 BKN120 BKN200 24/17 A2996"}""",
    ).map { json.parseToJsonElement(it.trimIndent().replace("\n", " ")) as JsonObject }

    private val affirmativelyClear = setOf("CLR", "SKC", "CAVOK", "NSC", "NCD")

    @Test
    fun `the parser and NOAA agree on every layer base`() {
        corpus.forEach { station ->
            val icao = station["icaoId"]!!.jsonPrimitive.content
            val raw = station["rawOb"]!!.jsonPrimitive.content
            val noaaLayers = (station["clouds"] as JsonArray)
                .map { it as JsonObject }
                .mapNotNull { layer ->
                    val cover = layer["cover"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    val base = layer["base"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
                    cover to base
                }

            val parsed = MetarParser.parse(raw).skyCover

            withClue("$icao — $raw") {
                when (parsed) {
                    is SkyCover.Layers -> {
                        // Same count, same bases, same order.
                        parsed.layers.map { it.baseFt } shouldBe noaaLayers.map { it.second }
                        parsed.layers.map { it.cover.code } shouldBe noaaLayers.map { it.first }
                    }

                    is SkyCover.Obscured -> {
                        // NOAA reports the obscuration as one OVX "layer" whose
                        // base is the vertical visibility, in feet.
                        noaaLayers.size shouldBe 1
                        noaaLayers.single().first shouldBe "OVX"
                        parsed.verticalVisibilityFt shouldBe noaaLayers.single().second
                    }

                    SkyCover.Clear -> noaaLayers shouldBe emptyList()
                    SkyCover.Unknown -> error("$icao parsed as Unknown; the corpus should not contain one")
                }
            }
        }
    }

    @Test
    fun `an affirmatively clear cover corresponds exactly to a Clear parse`() {
        corpus.forEach { station ->
            val icao = station["icaoId"]!!.jsonPrimitive.content
            val cover = station["cover"]!!.jsonPrimitive.content
            val parsed = MetarParser.parse(station["rawOb"]!!.jsonPrimitive.content).skyCover

            withClue("$icao — cover=$cover") {
                (parsed is SkyCover.Clear) shouldBe (cover in affirmativelyClear)
            }
        }
    }

    @Test
    fun `an OVX cover corresponds exactly to an Obscured parse`() {
        corpus.forEach { station ->
            val icao = station["icaoId"]!!.jsonPrimitive.content
            val cover = station["cover"]!!.jsonPrimitive.content
            val parsed = MetarParser.parse(station["rawOb"]!!.jsonPrimitive.content).skyCover

            withClue("$icao — cover=$cover") {
                (parsed is SkyCover.Obscured) shouldBe (cover == "OVX")
            }
        }
    }

    @Test
    fun `the documented divergence is that NOAA drops the convective modifier`() {
        // Asserted *as a divergence*, so the reason the parser is authoritative
        // stays visible. NOAA sends KJFK's second deck as
        // {"cover":"BKN","base":4300}; the raw text says BKN043CB, and that CB is
        // the difference between a broken deck and thunderstorms inside it.
        val kjfk = corpus.single { it["icaoId"]!!.jsonPrimitive.content == "KJFK" }

        val noaaHasNoTypeField = (kjfk["clouds"] as JsonArray)
            .map { it as JsonObject }
            .none { it.containsKey("type") || it.containsKey("modifier") }
        noaaHasNoTypeField shouldBe true

        val parsedLayers = (MetarParser.parse(kjfk["rawOb"]!!.jsonPrimitive.content).skyCover as SkyCover.Layers).layers
        parsedLayers[1].convective shouldBe ConvectiveCloud.CUMULONIMBUS
        parsedLayers[1].cover shouldBe CloudCover.BROKEN
    }

    @Test
    fun `the ceiling the parser derives matches the lowest NOAA ceiling layer`() {
        val ceilingCovers = setOf("BKN", "OVC", "OVX")

        corpus.forEach { station ->
            val icao = station["icaoId"]!!.jsonPrimitive.content
            val lowestNoaaCeiling = (station["clouds"] as JsonArray)
                .map { it as JsonObject }
                .filter { it["cover"]?.jsonPrimitive?.contentOrNull in ceilingCovers }
                .minOfOrNull { it["base"]!!.jsonPrimitive.int }

            val parsedCeiling = MetarParser.parse(station["rawOb"]!!.jsonPrimitive.content).skyCover.ceiling

            withClue("$icao — NOAA lowest ceiling layer $lowestNoaaCeiling") {
                when (lowestNoaaCeiling) {
                    null -> (parsedCeiling is Ceiling.Unlimited) shouldBe true
                    else -> (parsedCeiling as Ceiling.At).ft shouldBe lowestNoaaCeiling
                }
            }
        }
    }
}

private val kotlinx.serialization.json.JsonPrimitive.int: Int get() = content.toInt()
