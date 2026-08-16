package com.github.daanbouwman.flightplanner.routing

import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Serialises the airport index as a flat binary blob.
 *
 * Rebuilding the index from database rows costs one `step()` and six column
 * fetches per airport — around 150,000 JNI crossings for 24,000 airports — and
 * that per-row overhead, not the data volume, is what makes startup slow. No
 * amount of trimming columns changes the shape of that cost.
 *
 * But the index is *derived* data: it depends only on the shipped database, so
 * it can be built once by the ETL and read back as a handful of bulk memory
 * copies. `ByteBuffer.asIntBuffer().get(array)` moves an entire column in one
 * call, which turns startup from ~150,000 boundary crossings into about a dozen.
 *
 * **Everything derived is stored, including the trigonometry and the latitude
 * bands.** The first version of this format recomputed them on the grounds that
 * they are cheap arithmetic — and on a warm JVM they are. On a cold ART they
 * measured 73 ms of the 81 ms load, because 120,000 `sin`/`cos` calls run
 * interpreted before anything JITs. Storing them costs about 580 KB and makes
 * the load a sequence of copies whose cost does not depend on the runtime having
 * warmed up.
 */
object AirportIndexCodec {

    private const val MAGIC = 0x46504958 // "FPIX"
    private const val VERSION = 2

    /**
     * Magic, version, airport count, and one word of padding.
     *
     * The padding is load-bearing: it puts the `double` columns on an 8-byte
     * boundary, which is what lets a `DoubleBuffer` view copy them as memory
     * rather than element by element.
     */
    private const val HEADER_BYTES = 16

    /** Per airport: ids, codes, longestRunwayFt, flags, bandSlots, bandLonKey. */
    private const val INTS_PER_AIRPORT = 6

    /** Per airport: latDeg, lonDeg. */
    private const val DOUBLES_PER_AIRPORT = 2

    /** Per airport: latRad, sinLat, cosLat, sinLon, cosLon. */
    private const val FLOATS_PER_AIRPORT = 5

    fun encodedSize(count: Int): Int =
        HEADER_BYTES +
            LatBandIndex.BAND_COUNT * 4 + 4 + // bandStart
            count * (INTS_PER_AIRPORT * 4 + DOUBLES_PER_AIRPORT * 8 + FLOATS_PER_AIRPORT * 4)

    fun encode(index: AirportIndex): ByteArray {
        val n = index.size
        val bands = index.bands
        check(bands.bandSlots.size == n && bands.bandLonKey.size == n) {
            "Band index holds ${bands.bandSlots.size} slots for $n airports"
        }

        val buffer = ByteBuffer.allocate(encodedSize(n)).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(MAGIC)
        buffer.putInt(VERSION)
        buffer.putInt(n)
        buffer.putInt(0)

        // Widest columns first, so each group starts aligned to its own width.
        buffer.bulkDoubles { doubles ->
            doubles.put(index.latDeg, 0, n)
            doubles.put(index.lonDeg, 0, n)
        }
        buffer.bulkInts { ints ->
            ints.put(bands.bandStart, 0, LatBandIndex.BAND_COUNT + 1)
            ints.put(index.ids, 0, n)
            ints.put(index.codes, 0, n)
            ints.put(index.longestRunwayFt, 0, n)
            ints.put(index.flags, 0, n)
            ints.put(bands.bandSlots, 0, n)
            ints.put(bands.bandLonKey, 0, n)
        }
        buffer.bulkFloats { floats ->
            floats.put(index.latRad, 0, n)
            floats.put(index.sinLat, 0, n)
            floats.put(index.cosLat, 0, n)
            floats.put(index.sinLon, 0, n)
            floats.put(index.cosLon, 0, n)
        }

        check(buffer.position() == buffer.capacity()) {
            "Wrote ${buffer.position()} of ${buffer.capacity()} bytes"
        }
        return buffer.array()
    }

    fun encodeTo(index: AirportIndex, out: OutputStream) = out.write(encode(index))

    /**
     * Reads a blob written by [encode].
     *
     * @throws IllegalStateException when the blob is not a recognised index, so
     *   a corrupt or stale asset fails loudly instead of producing an index with
     *   plausible-looking nonsense in it.
     */
    fun decode(bytes: ByteArray): AirportIndex {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        check(buffer.int == MAGIC) { "Not an airport index blob" }
        val version = buffer.int
        check(version == VERSION) { "Airport index blob is version $version, expected $VERSION" }
        val n = buffer.int
        buffer.int // alignment padding
        check(n >= 0) { "Airport index blob declares $n airports" }
        check(bytes.size == encodedSize(n)) {
            "Airport index blob is ${bytes.size} bytes, expected ${encodedSize(n)} for $n airports"
        }

        val bandStart = IntArray(LatBandIndex.BAND_COUNT + 1)
        val ids = IntArray(n)
        val codes = IntArray(n)
        val longestRunwayFt = IntArray(n)
        val flags = IntArray(n)
        val bandSlots = IntArray(n)
        val bandLonKey = IntArray(n)
        val latDeg = DoubleArray(n)
        val lonDeg = DoubleArray(n)
        val latRad = FloatArray(n)
        val sinLat = FloatArray(n)
        val cosLat = FloatArray(n)
        val sinLon = FloatArray(n)
        val cosLon = FloatArray(n)

        // Bulk copies: one call per column rather than one per value.
        buffer.bulkDoubles { doubles ->
            doubles.get(latDeg)
            doubles.get(lonDeg)
        }
        buffer.bulkInts { ints ->
            ints.get(bandStart)
            ints.get(ids)
            ints.get(codes)
            ints.get(longestRunwayFt)
            ints.get(flags)
            ints.get(bandSlots)
            ints.get(bandLonKey)
        }
        buffer.bulkFloats { floats ->
            floats.get(latRad)
            floats.get(sinLat)
            floats.get(cosLat)
            floats.get(sinLon)
            floats.get(cosLon)
        }

        return AirportIndex(
            size = n,
            ids = ids,
            codes = codes,
            latDeg = latDeg,
            lonDeg = lonDeg,
            latRad = latRad,
            sinLat = sinLat,
            cosLat = cosLat,
            sinLon = sinLon,
            cosLon = cosLon,
            longestRunwayFt = longestRunwayFt,
            flags = flags,
            bands = LatBandIndex(bandStart, bandSlots, bandLonKey),
        )
    }

    fun decodeFrom(input: InputStream): AirportIndex = decode(input.readBytes())

    /**
     * Runs [block] against a typed view of the buffer, then advances the
     * buffer's own position past what the view consumed.
     *
     * `asIntBuffer()` and friends share the bytes but keep an independent
     * position, so without this every call site has to restate how many bytes it
     * just moved — which is exactly the arithmetic that silently corrupts a
     * format when a column is added.
     */
    private inline fun ByteBuffer.bulkInts(block: (java.nio.IntBuffer) -> Unit) {
        val view = asIntBuffer()
        block(view)
        position(position() + view.position() * 4)
    }

    private inline fun ByteBuffer.bulkDoubles(block: (java.nio.DoubleBuffer) -> Unit) {
        val view = asDoubleBuffer()
        block(view)
        position(position() + view.position() * 8)
    }

    private inline fun ByteBuffer.bulkFloats(block: (java.nio.FloatBuffer) -> Unit) {
        val view = asFloatBuffer()
        block(view)
        position(position() + view.position() * 4)
    }
}
