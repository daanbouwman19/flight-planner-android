package com.github.daanbouwman.flightplanner.routing

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

/**
 * Serialises [WorldOutline] as a flat binary blob.
 *
 * Same reasoning as [AirportIndexCodec], for a much smaller file: the device
 * reads bytes and copies arrays, and no GeoJSON parser ever runs on a phone.
 * The source outline is 138 KB of JSON whose numbers carry six decimal places —
 * about 11 cm — which is 20,000 times finer than a 180 dp card can show.
 *
 * ### Coordinates are 16-bit
 *
 * A longitude quantised to a signed 16-bit fraction of 180° lands on a
 * 0.0055° grid, or ~610 m at the equator; latitude, over 90°, lands on ~305 m.
 * At the closest a route card ever zooms — a window floor of about 25° across a
 * 360 dp card — that grid step is under a twentieth of a pixel, so the
 * quantisation is invisible and the file halves.
 *
 * ### Rings carry explicit lengths
 *
 * The ring table stores offsets rather than separating rings with a sentinel
 * coordinate. There is no coordinate value a sentinel could safely use — every
 * bit pattern in the 16-bit range is a real place — and an offset table also
 * lets the reader allocate exactly once.
 */
object WorldOutlineCodec {

    private const val MAGIC = 0x4650574D // "FPWM"
    private const val VERSION = 1

    /** Magic, version, ring count, point count. */
    private const val HEADER_BYTES = 16

    /** Scale for a longitude in [-180, 180] onto the signed 16-bit range. */
    private const val LON_SCALE = 32767.0 / 180.0

    /** Scale for a latitude in [-90, 90]. */
    private const val LAT_SCALE = 32767.0 / 90.0

    fun encodedSize(ringCount: Int, pointCount: Int): Int =
        HEADER_BYTES + (ringCount + 1) * 4 + pointCount * 2 * 2

    fun encode(outline: WorldOutline): ByteArray {
        val rings = outline.ringCount
        val points = outline.pointCount

        val buffer = ByteBuffer.allocate(encodedSize(rings, points)).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(MAGIC)
        buffer.putInt(VERSION)
        buffer.putInt(rings)
        buffer.putInt(points)

        // Ints first, so the ring table is 4-byte aligned and the coordinate
        // columns that follow start on an even offset whatever its length.
        buffer.bulkInts { ints -> ints.put(outline.ringStart, 0, rings + 1) }
        buffer.bulkShorts { shorts ->
            for (i in 0 until points) shorts.put(quantiseLon(outline.lon[i]))
            for (i in 0 until points) shorts.put(quantiseLat(outline.lat[i]))
        }

        check(buffer.position() == buffer.capacity()) {
            "Wrote ${buffer.position()} of ${buffer.capacity()} bytes"
        }
        return buffer.array()
    }

    /**
     * Reads a blob written by [encode].
     *
     * @throws IllegalStateException when the blob is not a recognised outline, so
     *   a stale or truncated asset fails loudly rather than drawing a coastline
     *   made of noise.
     */
    fun decode(bytes: ByteArray): WorldOutline {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        check(buffer.int == MAGIC) { "Not a world outline blob" }
        val version = buffer.int
        check(version == VERSION) { "World outline blob is version $version, expected $VERSION" }
        val rings = buffer.int
        val points = buffer.int
        check(rings >= 0 && points >= 0) { "World outline blob declares $rings rings, $points points" }
        check(bytes.size == encodedSize(rings, points)) {
            "World outline blob is ${bytes.size} bytes, expected ${encodedSize(rings, points)} " +
                "for $rings rings and $points points"
        }

        val ringStart = IntArray(rings + 1)
        val lon = FloatArray(points)
        val lat = FloatArray(points)

        buffer.bulkInts { ints -> ints.get(ringStart) }
        buffer.bulkShorts { shorts ->
            for (i in 0 until points) lon[i] = (shorts.get() / LON_SCALE).toFloat()
            for (i in 0 until points) lat[i] = (shorts.get() / LAT_SCALE).toFloat()
        }

        return WorldOutline(lon = lon, lat = lat, ringStart = ringStart)
    }

    fun decodeFrom(input: InputStream): WorldOutline = decode(input.readBytes())

    /** The longitude [encode] would store for [lon], in degrees. Used by the builder. */
    fun roundTripLon(lon: Float): Float = (quantiseLon(lon) / LON_SCALE).toFloat()

    /** The latitude [encode] would store for [lat], in degrees. */
    fun roundTripLat(lat: Float): Float = (quantiseLat(lat) / LAT_SCALE).toFloat()

    private fun quantiseLon(lon: Float): Short =
        (lon.toDouble().coerceIn(-180.0, 180.0) * LON_SCALE).roundToInt().toShort()

    private fun quantiseLat(lat: Float): Short =
        (lat.toDouble().coerceIn(-90.0, 90.0) * LAT_SCALE).roundToInt().toShort()

    /** See [AirportIndexCodec]'s equivalents: a typed view keeps its own position. */
    private inline fun ByteBuffer.bulkInts(block: (java.nio.IntBuffer) -> Unit) {
        val view = asIntBuffer()
        block(view)
        position(position() + view.position() * 4)
    }

    private inline fun ByteBuffer.bulkShorts(block: (java.nio.ShortBuffer) -> Unit) {
        val view = asShortBuffer()
        block(view)
        position(position() + view.position() * 2)
    }
}
