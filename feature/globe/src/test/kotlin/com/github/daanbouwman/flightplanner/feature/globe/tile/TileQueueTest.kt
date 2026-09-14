package com.github.daanbouwman.flightplanner.feature.globe.tile

import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.test.assertNull

/**
 * The ordering the loader relies on: coarse before fine across levels, the
 * traversal's own order within one, prefetch last of all. The single stack this
 * replaced had the deepest leaves come off first and the root come off last.
 *
 * **The within-level expectation was reversed on purpose.** These cases used to
 * assert that the *newest* request of a level popped first. That held while the
 * traversal was breadth-first and its order carried no meaning; the traversal
 * is a priority heap now and requests a level's nodes most important first, so
 * newest-first fetched the least important one — see [TileQueue]'s class note.
 */
class TileQueueTest {

    private val queue = TileQueue(maxLevel = 8)

    @Test
    fun `a frame pops its shallowest level first, in the order it was requested`() {
        // The order a traversal pushes: every visited node, level by level,
        // most important first within each.
        for (z in 4..7) for (i in 0 until 3) queue.push(TileKey.of(z, i, 0))
        queue.pop() shouldBe TileKey.of(4, 0, 0)
        queue.pop() shouldBe TileKey.of(4, 1, 0)
        queue.pop() shouldBe TileKey.of(4, 2, 0)
        queue.pop() shouldBe TileKey.of(5, 0, 0)
    }

    @Test
    fun `within a level the first request pops first`() {
        queue.push(TileKey.of(5, 0, 0))
        queue.push(TileKey.of(5, 1, 0))
        queue.push(TileKey.of(5, 2, 0))
        queue.pop() shouldBe TileKey.of(5, 0, 0)
        queue.pop() shouldBe TileKey.of(5, 1, 0)
        queue.pop() shouldBe TileKey.of(5, 2, 0)
    }

    @Test
    fun `a coarse request pushed late still pops before a fine one pushed early`() {
        queue.push(TileKey.of(7, 0, 0))
        queue.push(TileKey.of(0, 0, 0))
        queue.pop() shouldBe TileKey.of(0, 0, 0)
        queue.pop() shouldBe TileKey.of(7, 0, 0)
    }

    @Test
    fun `the prefetch bucket pops only when every level is empty`() {
        queue.push(TileKey.of(6, 0, 0), prefetch = true)
        queue.push(TileKey.of(8, 0, 0))
        queue.pop() shouldBe TileKey.of(8, 0, 0)
        queue.push(TileKey.of(3, 0, 0))
        queue.pop() shouldBe TileKey.of(3, 0, 0)
        queue.pop() shouldBe TileKey.of(6, 0, 0)
        assertNull(queue.pop())
        queue.isEmpty shouldBe true
    }

    @Test
    fun `overflow drops prefetch and the deepest levels first, whole buckets at a time`() {
        queue.push(TileKey.of(4, 0, 0))
        queue.push(TileKey.of(5, 0, 0))
        queue.push(TileKey.of(5, 1, 0))
        repeat(3) { queue.push(TileKey.of(7, it, 0)) }
        repeat(2) { queue.push(TileKey.of(6, it, 0), prefetch = true) }
        queue.size shouldBe 8

        val dropped = mutableListOf<TileKey>()
        queue.dropDeepestUntil(4) { dropped += it }

        // Prefetch (2) and then z7 (3) take it to 3, under the target; z5 stays.
        dropped.size shouldBe 5
        dropped.count { it.z == 6 } shouldBe 2
        dropped.count { it.z == 7 } shouldBe 3
        queue.size shouldBe 3
        queue.pop() shouldBe TileKey.of(4, 0, 0)
        queue.pop() shouldBe TileKey.of(5, 0, 0)
    }

    @Test
    fun `size follows pushes, pops and clear`() {
        queue.isEmpty shouldBe true
        queue.push(TileKey.of(2, 0, 0))
        queue.push(TileKey.of(3, 0, 0), prefetch = true)
        queue.size shouldBe 2
        queue.pop()
        queue.size shouldBe 1
        queue.clear()
        queue.size shouldBe 0
        assertNull(queue.pop())
    }
}
