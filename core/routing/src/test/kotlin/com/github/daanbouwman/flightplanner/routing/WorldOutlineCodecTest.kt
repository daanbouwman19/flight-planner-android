package com.github.daanbouwman.flightplanner.routing

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test

/**
 * The outline is lossy on purpose — coordinates are quantised to 16 bits — so
 * the round trip cannot be asserted for equality. What has to hold is that the
 * loss is bounded by the grid step the format promises, and that the ring
 * structure survives exactly: a ring table off by one splices two continents
 * into one polygon, which fills an ocean.
 */
class WorldOutlineCodecTest {

    /** Half a grid step, the most rounding can move a coordinate. */
    private val lonTolerance = 180.0 / 32767.0 / 2.0 + 1e-6
    private val latTolerance = 90.0 / 32767.0 / 2.0 + 1e-6

    @Test
    fun `a decoded outline keeps every coordinate within the quantisation step`() {
        val original = randomOutline(rings = 40, seed = 11)
        val restored = WorldOutlineCodec.decode(WorldOutlineCodec.encode(original))

        restored.ringStart.toList() shouldBe original.ringStart.toList()
        restored.pointCount shouldBe original.pointCount
        for (i in 0 until original.pointCount) {
            abs(restored.lon[i] - original.lon[i]).toDouble() shouldBeLessThan lonTolerance
            abs(restored.lat[i] - original.lat[i]).toDouble() shouldBeLessThan latTolerance
        }
    }

    /** What the builder quantises with must be what the reader decodes to. */
    @Test
    fun `the builder's round-trip helpers agree with the codec`() {
        val original = randomOutline(rings = 8, seed = 12)
        val restored = WorldOutlineCodec.decode(WorldOutlineCodec.encode(original))

        for (i in 0 until original.pointCount) {
            restored.lon[i] shouldBe WorldOutlineCodec.roundTripLon(original.lon[i])
            restored.lat[i] shouldBe WorldOutlineCodec.roundTripLat(original.lat[i])
        }
    }

    @Test
    fun `the extremes of both ranges survive`() {
        val outline = WorldOutline(
            lon = floatArrayOf(-180f, 180f, 0f),
            lat = floatArrayOf(-90f, 90f, 0f),
            ringStart = intArrayOf(0, 3),
        )
        val restored = WorldOutlineCodec.decode(WorldOutlineCodec.encode(outline))

        restored.lon.toList() shouldBe listOf(-180f, 180f, 0f)
        restored.lat.toList() shouldBe listOf(-90f, 90f, 0f)
    }

    @Test
    fun `bounding boxes are derived from the decoded points`() {
        val outline = WorldOutline(
            lon = floatArrayOf(4f, 9f, -2f, 4f, 100f, 120f, 100f),
            lat = floatArrayOf(52f, 48f, 60f, 52f, 1f, -3f, 1f),
            ringStart = intArrayOf(0, 4, 7),
        )

        outline.ringCount shouldBe 2
        outline.ringMinLon.toList() shouldBe listOf(-2f, 100f)
        outline.ringMaxLon.toList() shouldBe listOf(9f, 120f)
        outline.ringMinLat.toList() shouldBe listOf(48f, -3f)
        outline.ringMaxLat.toList() shouldBe listOf(60f, 1f)
    }

    @Test
    fun `a blob that is not an outline is refused`() {
        shouldThrow<IllegalStateException> { WorldOutlineCodec.decode(ByteArray(64)) }
    }

    @Test
    fun `a truncated blob is refused rather than decoded short`() {
        val bytes = WorldOutlineCodec.encode(randomOutline(rings = 4, seed = 13))
        shouldThrow<IllegalStateException> { WorldOutlineCodec.decode(bytes.copyOf(bytes.size - 8)) }
    }

    @Test
    fun `an empty outline round-trips`() {
        val restored = WorldOutlineCodec.decode(WorldOutlineCodec.encode(WorldOutline.Empty))

        restored.ringCount shouldBe 0
        restored.pointCount shouldBe 0
    }

    private fun randomOutline(rings: Int, seed: Int): WorldOutline {
        val random = Random(seed)
        val lon = ArrayList<Float>()
        val lat = ArrayList<Float>()
        val starts = ArrayList<Int>()
        repeat(rings) {
            starts += lon.size
            val centreLon = random.nextDouble(-170.0, 170.0)
            val centreLat = random.nextDouble(-80.0, 80.0)
            val points = random.nextInt(4, 40)
            repeat(points) {
                lon += (centreLon + random.nextDouble(-8.0, 8.0)).coerceIn(-180.0, 180.0).toFloat()
                lat += (centreLat + random.nextDouble(-8.0, 8.0)).coerceIn(-90.0, 90.0).toFloat()
            }
            // Closed, as the shipped rings are.
            lon += lon[starts.last()]
            lat += lat[starts.last()]
        }
        starts += lon.size
        return WorldOutline(lon.toFloatArray(), lat.toFloatArray(), starts.toIntArray())
    }
}
