package com.github.daanbouwman.flightplanner.routing

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlin.random.Random
import kotlin.test.Test

class RandomAirportSampleTest {

    @Test
    fun `returns amount distinct slots, all in range`() {
        val slots = RandomAirportSample.sampleSlots(size = 1_000, amount = 50, random = Random(1))

        slots shouldHaveSize 50
        slots.toSet() shouldHaveSize 50
        slots.all { it in 0 until 1_000 } shouldBe true
    }

    @Test
    fun `is deterministic for a fixed seed`() {
        val first = RandomAirportSample.sampleSlots(size = 500, amount = 50, random = Random(42))
        val second = RandomAirportSample.sampleSlots(size = 500, amount = 50, random = Random(42))

        first.toList() shouldBe second.toList()
    }

    @Test
    fun `different seeds usually produce different orders`() {
        val first = RandomAirportSample.sampleSlots(size = 500, amount = 50, random = Random(1))
        val second = RandomAirportSample.sampleSlots(size = 500, amount = 50, random = Random(2))

        first.toList() shouldNotBe second.toList()
    }

    @Test
    fun `amount equal to size returns a permutation of every slot`() {
        val slots = RandomAirportSample.sampleSlots(size = 20, amount = 20, random = Random(7))

        slots.sorted() shouldBe (0 until 20).toList()
    }

    @Test
    fun `amount zero returns empty`() {
        RandomAirportSample.sampleSlots(size = 100, amount = 0) shouldHaveSize 0
    }

    @Test
    fun `size zero returns empty`() {
        RandomAirportSample.sampleSlots(size = 0, amount = 50) shouldHaveSize 0
    }

    @Test
    fun `amount greater than size falls back to sampling with replacement`() {
        val slots = RandomAirportSample.sampleSlots(size = 5, amount = 10, random = Random(3))

        slots shouldHaveSize 10
        slots.all { it in 0 until 5 } shouldBe true
    }

    @Test
    fun `no slot is structurally excluded from a single-pick draw`() {
        val size = 10
        val seen = mutableSetOf<Int>()
        repeat(200) { seed ->
            val slot = RandomAirportSample.sampleSlots(size = size, amount = 1, random = Random(seed)).single()
            seen += slot
        }

        seen shouldHaveSize size
    }
}
