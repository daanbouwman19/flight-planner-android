package com.github.daanbouwman.flightplanner.routing

import kotlin.random.Random

/**
 * The desktop's "Random airports" action, ported field-for-field from
 * `generate_random_airports` in `modules/data_operations.rs`.
 *
 * The reference samples `amount` distinct airports out of the whole fleet
 * uniformly, without replacement, via `rand`'s slice sampling — which also
 * **randomises the result order**, not merely deduplicates it. This mirrors
 * both properties: [sampleSlots] returns [amount] distinct slots drawn
 * uniformly from `[0, size)`, in an order that is not the index's own.
 *
 * Working over slot indices rather than [com.github.daanbouwman.flightplanner.model.Airport]
 * values keeps this in `:core:routing`, alongside [AirportSlotSearch]: the
 * caller resolves slots to airports afterwards via
 * `AirportRepository.airportsForSlots`, exactly as a search result already
 * does.
 */
object RandomAirportSample {

    /** The desktop's own constant — `RANDOM_AIRPORTS_COUNT` in `gui/ui.rs`. */
    const val BATCH_SIZE: Int = 50

    /**
     * [amount] distinct slots in `[0, size)`, uniformly sampled, in randomised
     * order.
     *
     * Uses Floyd's algorithm for the distinct sample — O([amount]) time and
     * space, no O([size]) scratch array — then shuffles the result, because
     * Floyd's own insertion order is not a uniform permutation and the
     * reference's order is explicitly randomised rather than merely
     * deduplicated.
     *
     * When `amount > size` this falls back to sampling with replacement,
     * matching the reference's `else` arm. On the shipped dataset (thousands
     * of airports) that branch never runs — [BATCH_SIZE] is 50 — but it is
     * kept for parity rather than assumed away.
     */
    fun sampleSlots(size: Int, amount: Int, random: Random = Random.Default): IntArray {
        if (size <= 0 || amount <= 0) return IntArray(0)

        if (amount > size) {
            return IntArray(amount) { random.nextInt(size) }
        }

        val selected = LinkedHashSet<Int>(amount * 2)
        for (i in size - amount until size) {
            val t = random.nextInt(i + 1)
            if (!selected.add(t)) selected.add(i)
        }

        val slots = selected.toIntArray()
        for (i in slots.lastIndex downTo 1) {
            val j = random.nextInt(i + 1)
            val tmp = slots[i]
            slots[i] = slots[j]
            slots[j] = tmp
        }
        return slots
    }
}
