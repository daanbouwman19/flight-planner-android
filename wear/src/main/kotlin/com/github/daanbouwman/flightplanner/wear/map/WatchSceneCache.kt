package com.github.daanbouwman.flightplanner.wear.map

import com.github.daanbouwman.flightplanner.routing.WorldOutline
import com.github.daanbouwman.flightplanner.wear.route.WatchRouteCard

/**
 * The last few faces' map scenes, built before the swipe that shows them.
 *
 * ### Why
 *
 * Building a scene projects and clips the world outline and walks the result
 * into paths. On the 1:50m outline a long-haul face is ~40,000 points of fill
 * and coast, and that work used to happen inside the map's draw — which runs
 * on the UI thread, in the first frame a page is drawn, which is the first
 * frame of a swipe. Measured on the watch, that frame took 20 ms on the UI
 * thread alone. The pager composes and measures the next page ahead of time
 * but never draws it, so nothing built the scene early. This does: the
 * ViewModel asks for the pages around the one the wearer settled on, on the
 * generator's background dispatcher, and the draw finds them here.
 *
 * ### What a miss does
 *
 * Builds the scene there and then, on the calling thread, with the same
 * [build] and the same inputs, and keeps it. A rotary flick can outrun the
 * prefetch; when it does, the face costs what it always did, and looks exactly
 * as it would have. There is no placeholder and no late pop-in, by design.
 *
 * ### Keys, size, threads and invalidation
 *
 * - **Keyed** by the route's identity — its card key and its airframe's outline,
 *   because two airframes can share a type code and still differ in outline —
 *   the [WatchFace] and the outline instance. The great circle is a function of
 *   the two codes, so those fix the picture. The routes list grows without
 *   bound, so scenes live here rather than on the cards.
 * - **[capacity] entries, least recently used out first.** The prefetch window
 *   is the settled page, one behind and two ahead; six keeps that and the pages
 *   just left, so swiping back and forth is all hits. A scene's paths live in
 *   native memory, a few hundred kilobytes for a long haul, so six is a couple
 *   of megabytes at worst.
 * - **Thread-safe.** Look-ups and stores take a lock; building does not, so a
 *   background build never holds up the UI thread's look-up of another page.
 *   Two threads that miss on the same key both build — one function, one input,
 *   one picture — and the first to store wins, so every caller gets one instance.
 * - **A key is built in the background once.** A scene is only stored when its
 *   build finishes, so on its own a second prefetch — the ViewModel relaunches
 *   one whenever the page settles, the routes grow or the face reports in,
 *   which at start-up is twice in a row — would miss on the page the first is
 *   still building and build it again beside it. So the cache also knows which
 *   keys are being built, and [prefetch] skips them. The draw's [get] does not:
 *   it never waits on another thread, by the rule under *What a miss does*, so
 *   a draw that catches a page mid-prefetch builds its own and the first stored
 *   wins, as above.
 * - **Never invalidated.** A scene holds no colour, so a theme change redraws
 *   one and never rebuilds it; another face size or density is another key; the
 *   outline is fixed for the feed's life and is part of the key regardless.
 *   "Nothing to draw" — a null from [build] — is kept as well: it is as much a
 *   function of the key as a scene is.
 *
 * Generic over the scene type, so its tests can count builds without a renderer.
 */
internal class WatchSceneCache<S : Any>(
    private val capacity: Int = CAPACITY,
    private val build: (WatchRouteCard, WorldOutline, WatchFace) -> S?,
) {
    init {
        require(capacity > 0) { "A scene cache needs room for at least one scene, was $capacity" }
    }

    private data class Key(
        val route: String,
        val outlineId: String?,
        val outline: WorldOutline,
        val face: WatchFace,
    )

    /** A built result, null included, so "nothing to draw" is remembered too. */
    private class Built<S : Any>(val scene: S?)

    /** Access-ordered, so iteration starts at the least recently used entry. Guarded by itself. */
    private val entries = object : LinkedHashMap<Key, Built<S>>(capacity + 1, 1f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, Built<S>>?): Boolean = size > capacity
    }

    /** The keys some thread is building right now. Guarded by [entries]' lock, like the entries. */
    private val building = HashSet<Key>()

    /**
     * [route]'s scene for [face], from here if it has been built and built now if
     * not — the draw's entry point. A key another thread is still building is a
     * miss like any other: the draw builds its own rather than wait.
     */
    fun get(route: WatchRouteCard, outline: WorldOutline, face: WatchFace): S? {
        val key = keyOf(route, outline, face)
        val claimed = synchronized(entries) {
            entries[key]?.let { return it.scene }
            building.add(key)
        }
        return buildAndStore(key, claimed, route, outline, face).scene
    }

    /**
     * Builds [route]'s scene for [face] unless it is already here or already
     * being built — the prefetch's entry point, so a relaunched prefetch never
     * builds a page a cancelled one is still finishing. A hit still counts as a
     * use, so the pages the prefetch window covers are the last to be evicted.
     */
    fun prefetch(route: WatchRouteCard, outline: WorldOutline, face: WatchFace) {
        val key = keyOf(route, outline, face)
        synchronized(entries) {
            if (entries[key] != null || !building.add(key)) return
        }
        buildAndStore(key, claimed = true, route, outline, face)
    }

    private fun keyOf(route: WatchRouteCard, outline: WorldOutline, face: WatchFace) =
        Key(route.key(), route.shape?.id, outline, face)

    /**
     * Builds outside the lock and keeps the first result stored for [key]. When
     * [claimed], this call marked [key] as being built, and unmarks it whether
     * the build returned or threw.
     */
    private fun buildAndStore(
        key: Key,
        claimed: Boolean,
        route: WatchRouteCard,
        outline: WorldOutline,
        face: WatchFace,
    ): Built<S> = try {
        val built = Built(build(route, outline, face))
        synchronized(entries) { entries.getOrPut(key) { built } }
    } finally {
        if (claimed) synchronized(entries) { building.remove(key) }
    }

    companion object {
        /** See the class KDoc: the prefetch window and the pages just left. */
        const val CAPACITY: Int = 6
    }
}
