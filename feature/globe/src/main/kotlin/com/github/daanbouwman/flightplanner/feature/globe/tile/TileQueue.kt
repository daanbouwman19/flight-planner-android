package com.github.daanbouwman.flightplanner.feature.globe.tile

import java.util.ArrayDeque

/**
 * The fetch queue: coarse before fine across levels, newest first within one.
 *
 * ### Why not one stack
 *
 * The queue used to be a single LIFO stack, and the traversal that feeds it
 * walks the quadtree breadth-first and requests every node it visits — so the
 * **deepest** leaves went onto the stack last and came off first, and their
 * ancestors came off last. The mesh draws a leaf whose own tile is missing
 * from its nearest resident ancestor; with the ancestors at the bottom of the
 * stack, there was nothing to fall back to until the whole set had landed. The
 * session's warm-up made it worse by pushing z0 first, so the root tile — the
 * one every other tile falls back to — was fetched last of the lot.
 *
 * One deque per level fixes the ordering across levels while keeping the
 * property the old stack was right about: within a level, the newest request
 * is the one for where the camera is *now*, and a stale one from a frame ago
 * is simply never reached.
 *
 * ### The prefetch bucket
 *
 * One extra bucket below every level holds tiles the traversal expects to want
 * soon — the children of leaves that are nearly ready to split. It is popped
 * only when every other bucket is empty, so a prefetch never delays anything
 * that is on screen.
 *
 * Pure JVM and not thread-safe: the loader wraps every call in its own lock,
 * and this class exists apart from it so the ordering can be unit-tested.
 */
internal class TileQueue(maxLevel: Int) {

    private val prefetchBucket = maxLevel + 1
    private val buckets = Array(maxLevel + 2) { ArrayDeque<TileKey>() }

    /** Entries across every bucket, including prefetch. */
    var size: Int = 0
        private set

    val isEmpty: Boolean get() = size == 0

    /** Queues [key] in its level's bucket, or in the prefetch bucket. */
    fun push(key: TileKey, prefetch: Boolean = false) {
        val bucket = if (prefetch) prefetchBucket else key.z
        buckets[bucket].addLast(key)
        size++
    }

    /**
     * The shallowest level's newest request, or null when empty.
     *
     * Scans levels ascending and takes the last-pushed entry of the first
     * non-empty one; the prefetch bucket comes after the deepest level.
     */
    fun pop(): TileKey? {
        for (bucket in buckets) {
            val key = bucket.pollLast() ?: continue
            size--
            return key
        }
        return null
    }

    /**
     * Drops whole buckets, deepest first — prefetch, then the deepest level,
     * and so on — until [size] is at or under [target]. Every dropped key is
     * reported so the caller can forget it was ever queued.
     *
     * Deepest first because a deep tile is the one most likely to belong to a
     * camera position that has already moved on, and because a coarse tile is
     * what every deeper one falls back to while it loads.
     */
    fun dropDeepestUntil(target: Int, onDropped: (TileKey) -> Unit) {
        var bucket = buckets.lastIndex
        while (size > target && bucket >= 0) {
            val deque = buckets[bucket--]
            while (true) {
                onDropped(deque.pollLast() ?: break)
                size--
            }
        }
    }

    fun clear() {
        for (bucket in buckets) bucket.clear()
        size = 0
    }
}
