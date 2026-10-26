package com.github.daanbouwman.flightplanner.feature.globe.tile

import android.util.Log
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

private const val TAG = "TileDiskCache"

/**
 * A tile as it sits on disk: the encoded image and what decides whether it may
 * be used without asking the origin again.
 */
internal class CachedTile(
    val body: ByteArray,
    /**
     * Wall-clock epoch millis from which the origin has to be asked again;
     * [Long.MAX_VALUE] for a tile that never goes stale. A stale tile is still
     * kept — it is what a revalidation reuses on a 304 and what the loader falls
     * back on when the network fails.
     */
    val expiresAt: Long,
    /** The origin's `ETag`, sent back as `If-None-Match` once the tile is stale. */
    val etag: String?,
    /** The origin's `Last-Modified`, sent back as `If-Modified-Since`. */
    val lastModified: String?,
) {
    fun isFresh(now: Long): Boolean = now < expiresAt
}

/**
 * The globe's imagery on disk: one file per tile, holding the image and the few
 * fields that say whether it is still fresh, bounded by what the files actually
 * spend on the disk.
 *
 * ### Why not OkHttp's `Cache`
 *
 * It used to be OkHttp's own `Cache`, 96 MB, and on a phone that 96 MB occupied
 * 123 MB. Measured on the SM-S942B, 5,942 Esri tiles: bodies averaged 11 KB, and
 * every tile also had a metadata file averaging **5.4 KB**, of which about
 * 4.6 KB was the server's TLS certificate chain — OkHttp stores the handshake of
 * every HTTPS response, so the same three certificates were on disk six thousand
 * times. Two files per tile, each rounded up to a 4 KB block, then added a
 * quarter again on top of what the cap counted.
 *
 * Here a tile is one file: a header of about seventy bytes, then the image. The
 * same tiles take roughly 40% less disk, and [maxBytesOnDisk] is measured in
 * whole blocks, so the cap is the number Settings shows rather than a figure
 * the filesystem quietly exceeds.
 *
 * ### What it owns, and what it does not
 *
 * Storage and eviction only. *Whether* a response may be stored and for how
 * long is HTTP's question and is answered in [TileHttp.expiresAt]; revalidating
 * a stale tile and falling back to one offline are the loader's, because both
 * are decisions about a request rather than about a file.
 *
 * ### Least recently used, across restarts
 *
 * Recency lives in an access-ordered map while the process runs and in each
 * file's modification time between runs: a hit touches the file, and the scan
 * that rebuilds the map orders by it. The map is the index — there is no
 * journal to get out of step with the directory, because the directory is the
 * only record.
 *
 * ### Crash safety
 *
 * A tile is written to a temporary file and renamed over its name, so a crash
 * mid-write leaves a stray temporary, which the next scan deletes, never a
 * truncated tile under a real name. A file that does not read back — a foreign
 * format, a short body — is a miss, and is deleted where it is found.
 *
 * ### Opened lazily
 *
 * The constructor touches nothing. The first [get] or [put], which happens on a
 * tile worker, scans the directory, and that is also when [obsolete] — the old
 * OkHttp cache's directory — is deleted. Other workers wait on that one scan;
 * it is a few thousand `stat`s, once per process.
 */
internal class TileDiskCache(
    private val directory: File,
    private val maxBytesOnDisk: Long = DEFAULT_MAX_BYTES_ON_DISK,
    /** A directory a previous version cached tiles in, deleted on first open. */
    private val obsolete: File? = null,
) {

    companion object {
        /**
         * 48 MB of disk, which holds about 3,800 Esri tiles — fifteen or so
         * atlases' worth of distinct imagery, and every pinned base level many
         * times over, so airplane mode after real use still draws a planet.
         */
        const val DEFAULT_MAX_BYTES_ON_DISK: Long = 48L * 1024 * 1024

        /**
         * The allocation unit every file is rounded up to. Both ext4 and f2fs
         * use 4 KB on Android, and it is what made the old cache overrun its cap.
         */
        private const val BLOCK_BYTES = 4096L

        /** "TIL1": a file without it is not one of these and is treated as a miss. */
        private const val MAGIC = 0x54494C31

        private const val TEMP_SUFFIX = ".tmp"

        private fun blocksOf(length: Long): Long = (length + BLOCK_BYTES - 1) / BLOCK_BYTES * BLOCK_BYTES
    }

    private val lock = Any()

    /** File name to bytes on disk, least recently used first. Guarded by [lock]. */
    private val entries = LinkedHashMap<String, Long>(0, 0.75f, true)
    private var bytesOnDisk = 0L
    private var loaded = false

    /** Bytes the cached tiles occupy on disk, block-rounded. Opens the cache. */
    val size: Long get() = synchronized(lock) { ensureLoaded(); bytesOnDisk }

    fun get(provider: TileProvider, key: TileKey): CachedTile? {
        val name = nameOf(provider, key)
        synchronized(lock) {
            ensureLoaded()
            // `get` on an access-ordered map is also what marks it recent.
            entries[name] ?: return null
        }
        val file = File(directory, name)
        val tile = try {
            read(file)
        } catch (e: IOException) {
            Log.d(TAG, "tile $key unreadable: ${e.message}")
            null
        }
        if (tile == null) {
            remove(provider, key)
            return null
        }
        // Recency for the next process's scan. Best effort: a refusal only
        // makes this tile look older than it is.
        file.setLastModified(System.currentTimeMillis())
        return tile
    }

    fun put(provider: TileProvider, key: TileKey, tile: CachedTile) {
        val name = nameOf(provider, key)
        synchronized(lock) { ensureLoaded() }
        val temp = try {
            File.createTempFile(name, TEMP_SUFFIX, directory).also { write(it, tile) }
        } catch (e: IOException) {
            Log.d(TAG, "tile $key not cached: ${e.message}")
            return
        }
        synchronized(lock) {
            val target = File(directory, name)
            if (!temp.renameTo(target)) {
                temp.delete()
                return
            }
            val size = blocksOf(target.length())
            entries.put(name, size)?.let { bytesOnDisk -= it }
            bytesOnDisk += size
            trimLocked()
        }
    }

    /** Forgets [key]'s tile, for a body that arrived intact and would not decode. */
    fun remove(provider: TileProvider, key: TileKey) {
        val name = nameOf(provider, key)
        synchronized(lock) {
            entries.remove(name)?.let { bytesOnDisk -= it }
            File(directory, name).delete()
        }
    }

    /**
     * Per provider, so a build that gains an API key and switches source does
     * not draw the old source's tiles at the new one's addresses. The token in
     * Esri's URL is deliberately not part of it: rotating a key does not change
     * the imagery.
     */
    private fun nameOf(provider: TileProvider, key: TileKey): String =
        "${provider.cacheName}-${key.z}-${key.x}-${key.y}"

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        obsolete?.let { if (it.exists() && !it.deleteRecursively()) Log.d(TAG, "could not delete $it") }
        directory.mkdirs()
        val files = directory.listFiles().orEmpty()
        val (temps, tiles) = files.partition { it.name.endsWith(TEMP_SUFFIX) }
        temps.forEach { it.delete() }
        // Oldest first, so insertion order is the access order the map keeps.
        for (file in tiles.sortedBy { it.lastModified() }) {
            val size = blocksOf(file.length())
            entries[file.name] = size
            bytesOnDisk += size
        }
        trimLocked()
    }

    private fun trimLocked() {
        val eldest = entries.entries.iterator()
        while (bytesOnDisk > maxBytesOnDisk && eldest.hasNext()) {
            val (name, size) = eldest.next()
            eldest.remove()
            bytesOnDisk -= size
            File(directory, name).delete()
        }
    }

    private fun write(file: File, tile: CachedTile) {
        DataOutputStream(BufferedOutputStream(FileOutputStream(file))).use { out ->
            out.writeInt(MAGIC)
            out.writeLong(tile.expiresAt)
            out.writeOptionalUtf(tile.etag)
            out.writeOptionalUtf(tile.lastModified)
            out.writeInt(tile.body.size)
            out.write(tile.body)
        }
    }

    /** Null for a file that is not ours; an [IOException] for one cut short. */
    private fun read(file: File): CachedTile? =
        DataInputStream(BufferedInputStream(FileInputStream(file))).use { input ->
            if (input.readInt() != MAGIC) return null
            val expiresAt = input.readLong()
            val etag = input.readOptionalUtf()
            val lastModified = input.readOptionalUtf()
            val length = input.readInt()
            // Bounded by the file itself, so a damaged length is a miss rather
            // than an allocation of whatever four bytes happened to say.
            if (length < 0 || length > file.length()) return null
            val body = ByteArray(length)
            input.readFully(body)
            CachedTile(body, expiresAt, etag, lastModified)
        }

    private fun DataOutputStream.writeOptionalUtf(value: String?) {
        writeBoolean(value != null)
        if (value != null) writeUTF(value)
    }

    private fun DataInputStream.readOptionalUtf(): String? = if (readBoolean()) readUTF() else null
}
