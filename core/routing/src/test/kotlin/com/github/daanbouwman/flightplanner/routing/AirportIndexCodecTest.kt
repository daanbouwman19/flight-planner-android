package com.github.daanbouwman.flightplanner.routing

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlin.test.Test

/**
 * The blob is the only thing the app reads at startup — the database is never
 * consulted for routing — so a decoded index has to be indistinguishable from a
 * freshly built one, field for field. Anything less and the app would generate
 * routes from data that merely looks plausible.
 */
class AirportIndexCodecTest {

    @Test
    fun `a decoded index equals the one that was encoded`() {
        val original = randomWorld(5_000, seed = 71)
        val restored = AirportIndexCodec.decode(AirportIndexCodec.encode(original))

        restored.size shouldBe original.size
        restored.ids.toList() shouldBe original.ids.toList()
        restored.codes.toList() shouldBe original.codes.toList()
        restored.latDeg.toList() shouldBe original.latDeg.toList()
        restored.lonDeg.toList() shouldBe original.lonDeg.toList()
        restored.latRad.toList() shouldBe original.latRad.toList()
        restored.sinLat.toList() shouldBe original.sinLat.toList()
        restored.cosLat.toList() shouldBe original.cosLat.toList()
        restored.sinLon.toList() shouldBe original.sinLon.toList()
        restored.cosLon.toList() shouldBe original.cosLon.toList()
        restored.longestRunwayFt.toList() shouldBe original.longestRunwayFt.toList()
        restored.flags.toList() shouldBe original.flags.toList()

        restored.bands.bandStart.toList() shouldBe original.bands.bandStart.toList()
        restored.bands.bandSlots.toList() shouldBe original.bands.bandSlots.toList()
        restored.bands.bandLonKey.toList() shouldBe original.bands.bandLonKey.toList()
    }

    /**
     * The stored bands are what makes startup cheap, and a stale or mis-ordered
     * copy would not throw — it would quietly return the wrong destinations.
     */
    @Test
    fun `a decoded index answers window queries identically`() {
        val original = randomWorld(4_000, seed = 72)
        val restored = AirportIndexCodec.decode(AirportIndexCodec.encode(original))
        val random = kotlin.random.Random(73)

        repeat(500) {
            val lat = random.nextDouble(-88.0, 88.0)
            val lon = random.nextDouble(-180.0, 180.0)
            val latHalf = random.nextDouble(0.5, 15.0)
            val lonHalf = random.nextDouble(0.5, 60.0)

            val a = mutableListOf<Int>()
            original.bands.forEachInWindow(lat - latHalf, lat + latHalf, lon, lonHalf) { a += it }
            val b = mutableListOf<Int>()
            restored.bands.forEachInWindow(lat - latHalf, lat + latHalf, lon, lonHalf) { b += it }
            b shouldBe a
        }
    }

    @Test
    fun `route generation from a decoded index reproduces the original exactly`() {
        val original = randomWorld(4_000, seed = 74)
        val restored = AirportIndexCodec.decode(AirportIndexCodec.encode(original))
        val request = RouteRequest(
            mode = RouteMode.AllAircraft,
            fleet = listOf(aircraft(rangeNm = 120), aircraft(id = 2, rangeNm = 4_000)),
            amount = 60,
        )

        val fromOriginal = kotlinx.coroutines.runBlocking {
            RouteGenerator(original).generate(request, kotlin.random.Random(7))
        }
        val fromRestored = kotlinx.coroutines.runBlocking {
            RouteGenerator(restored).generate(request, kotlin.random.Random(7))
        }
        fromRestored shouldBe fromOriginal
    }

    @Test
    fun `an empty index round-trips`() {
        val empty = AirportIndexBuilder(0).build()
        AirportIndexCodec.decode(AirportIndexCodec.encode(empty)).size shouldBe 0
    }

    @Test
    fun `a blob that is not an index is rejected`() {
        shouldThrow<IllegalStateException> { AirportIndexCodec.decode(ByteArray(64)) }
    }

    /**
     * Truncation is the realistic corruption: a partial asset extraction or an
     * interrupted write. It must fail loudly rather than yield a short index.
     */
    @Test
    fun `a truncated blob is rejected`() {
        val bytes = AirportIndexCodec.encode(randomWorld(100, seed = 75))
        shouldThrow<IllegalStateException> { AirportIndexCodec.decode(bytes.copyOf(bytes.size - 40)) }
    }

    @Test
    fun `a blob from a future version is rejected`() {
        val bytes = AirportIndexCodec.encode(randomWorld(100, seed = 76))
        // Bytes 4..7 hold the little-endian format version.
        bytes[4] = 99
        shouldThrow<IllegalStateException> { AirportIndexCodec.decode(bytes) }
    }
}
