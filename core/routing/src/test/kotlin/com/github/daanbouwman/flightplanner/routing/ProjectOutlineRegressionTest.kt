package com.github.daanbouwman.flightplanner.routing

import io.kotest.matchers.shouldBe
import java.io.File
import kotlin.test.Test

/**
 * Pins, bit for bit, what [MapFrame.projectOutline] makes of the outline the app
 * ships, across the windows both devices actually draw.
 *
 * The clipper is the hot path behind every route map, and it is the code most
 * worth rewriting for speed — which is exactly when a reordered subtraction or a
 * `>=` turned `>` would move a coastline by a rounding step on some card and on
 * no test that checks shapes. A checksum over the raw float bits catches that;
 * a tolerance would not.
 *
 * The routes cover what stresses it differently: two short hops at the window
 * floor, a mid-range leg, a fjord coast and an Arctic archipelago (thousands of
 * small rings), two ocean crossings (whole continents clipped), one of them past
 * the antimeridian, and a Pacific leg that straddles it. Each is projected
 * through a phone-shaped card and through the watch face at both of its
 * margins.
 *
 * ### When this fails
 *
 * If the projection code changed, the change altered the output: find out why
 * before touching the pins. If `app/src/main/assets/maps/land.outline` was
 * regenerated, the pins are stale by construction — recapture them from a
 * [MapFrame] that has *not* changed in the same commit.
 */
class ProjectOutlineRegressionTest {

    private val outline: WorldOutline by lazy {
        val path = System.getProperty("flightplanner.worldOutline") ?: error("flightplanner.worldOutline is not set")
        WorldOutlineCodec.decode(File(path).readBytes())
    }

    @Test
    fun `the shipped outline projects to the pinned bits on every route and frame`() {
        val actual = LinkedHashMap<String, Pin>()
        for (route in ROUTES) {
            val arc = RouteArc.sampleGeographic(
                route.depLat,
                route.depLon,
                route.destLat,
                route.destLon,
                samples = RouteArc.CARD_SAMPLES,
            )
            for (card in CARDS) {
                val frame = MapFrame.forRoute(
                    lats = arc.lats,
                    lons = arc.lons,
                    aspect = card.aspect,
                    paddingFraction = card.paddingFraction,
                    topInsetFraction = card.topInsetFraction,
                )
                val land = frame.projectOutline(outline, margin = card.margin)
                actual["${route.name} ${card.name}"] = Pin(
                    fillRings = land.fill.ringCount,
                    fillPoints = land.fill.pointCount,
                    fillBits = fingerprint(land.fill),
                    coastRings = land.coast.ringCount,
                    coastPoints = land.coast.pointCount,
                    coastBits = fingerprint(land.coast),
                )
            }
        }

        actual shouldBe PINNED
    }

    /** 64-bit FNV-1a over every float's raw bits, then every ring offset. */
    private fun fingerprint(rings: ProjectedRings): Long {
        var hash = FNV_OFFSET
        fun mix(word: Int) {
            for (shift in 0 until 32 step 8) {
                hash = (hash xor ((word ushr shift) and 0xFF).toLong()) * FNV_PRIME
            }
        }
        for (value in rings.points) mix(value.toRawBits())
        for (start in rings.ringStart) mix(start)
        return hash
    }

    private data class Route(
        val name: String,
        val depLat: Double,
        val depLon: Double,
        val destLat: Double,
        val destLon: Double,
    )

    private data class Card(
        val name: String,
        val aspect: Double,
        val paddingFraction: Double,
        val topInsetFraction: Double,
        val margin: Double,
    )

    private data class Pin(
        val fillRings: Int,
        val fillPoints: Int,
        val fillBits: Long,
        val coastRings: Int,
        val coastPoints: Int,
        val coastBits: Long,
    )

    private companion object {
        const val FNV_OFFSET: Long = -0x340d631b7bdddcdbL // 0xcbf29ce484222325
        const val FNV_PRIME: Long = 0x100000001b3L

        val ROUTES = listOf(
            Route("EHAM-EGSH", 52.3086, 4.7639, 52.6758, 1.2828),
            Route("LGAV-LGSR", 37.9364, 23.9445, 36.3992, 25.4793),
            Route("EDDF-LPPT", 50.0333, 8.5706, 38.7813, -9.1359),
            Route("ENBR-ENTC", 60.2934, 5.2181, 69.6833, 18.9189),
            Route("CYFB-CYRB", 63.7564, -68.5558, 74.7169, -94.9694),
            Route("EGLL-KJFK", 51.4700, -0.4543, 40.6398, -73.7789),
            Route("RJAA-KLAX", 35.7647, 140.3864, 33.9425, -118.4081),
            Route("PHNL-NZAA", 21.3187, -157.9225, -37.0081, 174.7917),
        )

        val CARDS = listOf(
            // The phone's route card: wide, default padding, its OutlineMargin.
            Card("phone", aspect = 1.8, paddingFraction = MapFrame.PADDING_FRACTION, topInsetFraction = 0.0, margin = 0.05),
            // The watch face, at the margin it shipped with and the one it is moving to.
            Card("watch@0.08", aspect = 1.0, paddingFraction = 0.284, topInsetFraction = 0.22, margin = 0.08),
            Card("watch@0.01", aspect = 1.0, paddingFraction = 0.284, topInsetFraction = 0.22, margin = 0.01),
        )

        // Captured from MapFrame as of the 1:50m outline, before the allocation-free
        // rewrite of its clipper. Pin(fillRings, fillPoints, fillBits, coastRings, coastPoints, coastBits).
        val PINNED: Map<String, Pin> = mapOf(
            "EHAM-EGSH phone" to Pin(23, 545, 1774984679740613159L, 27, 543, 1557346970847464538L),
            "EHAM-EGSH watch@0.08" to Pin(32, 942, -8325685283563848602L, 40, 940, -9036401923309428340L),
            "EHAM-EGSH watch@0.01" to Pin(23, 674, -2581035452484177193L, 33, 670, -2095765884824192793L),
            "LGAV-LGSR phone" to Pin(42, 903, 4388884253902680883L, 45, 899, -2114814943955332331L),
            "LGAV-LGSR watch@0.08" to Pin(48, 1138, -5053885083695180919L, 57, 1134, 8826103559357095952L),
            "LGAV-LGSR watch@0.01" to Pin(46, 1005, 3645882963356477182L, 52, 1000, -1309254808686369831L),
            "EDDF-LPPT phone" to Pin(35, 1272, -8258297073342958093L, 42, 1269, 7665571428779761219L),
            "EDDF-LPPT watch@0.08" to Pin(88, 2885, -3353266629287242926L, 95, 2882, -4539866923887918311L),
            "EDDF-LPPT watch@0.01" to Pin(72, 2396, -2331079771309755799L, 81, 2393, 2348541189893581617L),
            "ENBR-ENTC phone" to Pin(109, 3458, -3246899884184405875L, 121, 3457, 4031731747222265651L),
            "ENBR-ENTC watch@0.08" to Pin(126, 3989, -7342870226304653948L, 139, 3988, 515364297545927503L),
            "ENBR-ENTC watch@0.01" to Pin(109, 3414, -2599966295492775384L, 121, 3413, -3808986533527568243L),
            "CYFB-CYRB phone" to Pin(101, 5309, -5047311510625123737L, 120, 5305, 7219841680965812742L),
            "CYFB-CYRB watch@0.08" to Pin(128, 7051, -7121571799625390929L, 142, 7050, -4509448912936325982L),
            "CYFB-CYRB watch@0.01" to Pin(112, 6219, -6879765931620868333L, 126, 6218, -6429498989147710650L),
            "EGLL-KJFK phone" to Pin(213, 8495, 5356269532301935901L, 242, 8490, -117751385842797452L),
            "EGLL-KJFK watch@0.08" to Pin(386, 20268, 4252972586781553581L, 409, 20267, -2172155032861543181L),
            "EGLL-KJFK watch@0.01" to Pin(285, 15774, -2217230310014252868L, 320, 15773, -5491925029076480263L),
            "RJAA-KLAX phone" to Pin(238, 8979, -5039787853190514984L, 263, 8977, 4169107819278269164L),
            "RJAA-KLAX watch@0.08" to Pin(391, 22272, 8365898192714036216L, 419, 22272, 8668551520455088360L),
            "RJAA-KLAX watch@0.01" to Pin(287, 16005, -206238218949062224L, 310, 16004, -6639576507448839036L),
            "PHNL-NZAA phone" to Pin(203, 6740, 9099186122553984739L, 212, 6738, -1817669726359866577L),
            "PHNL-NZAA watch@0.08" to Pin(289, 13601, 7399394815666237770L, 306, 13600, 49800496090780223L),
            "PHNL-NZAA watch@0.01" to Pin(203, 8737, -3485223281758198479L, 218, 8735, 7454758084695836298L),
        )
    }
}
