package com.github.daanbouwman.flightplanner.routing

/**
 * Ranked airport search that walks the index arrays directly.
 *
 * [SearchScorer] ranks a `List<SearchCandidate>`, which is the right shape for
 * the fleet — a hundred rows, already in memory as objects. It is the wrong
 * shape for airports: there are ~24,000 of them, they are held as parallel
 * primitive arrays precisely so that they are *not* objects, and search runs on
 * every keystroke. Materialising a list of 24,000 wrappers per character typed
 * would allocate more in a second of typing than the whole index costs to hold.
 *
 * So this is the same two-tier ranking expressed over the arrays:
 *
 *  - a match on the **code** scores [SearchScorer.CODE_MATCH_SCORE]
 *  - a match on the name or municipality scores [SearchScorer.FIELD_MATCH_SCORE]
 *  - non-matches are dropped; results come back score-descending, ties in slot
 *    order
 *
 * Slots come back rather than airports, because the caller already has the
 * index and knows how to turn slots into rows — see
 * `AirportRepository.airportsForSlots`.
 *
 * **One deliberate divergence from the desktop app.** It searches an airport's
 * code, name and *rendered runway length*; this searches code, name and
 * **municipality**. Municipality is the field a user actually reaches for when
 * they know the city but not the code ("Schiphol" is not a word most people
 * think of before "Amsterdam"), and it is a column the desktop's airport table
 * never had. Runway length is dropped from the field tier because typing a
 * five-digit number to find an airport is not a real query — the runway figure
 * is a filter, and Phase E gives it one.
 */
object AirportSlotSearch {

    /**
     * Results per query.
     *
     * Far smaller than [SearchScorer.DEFAULT_LIMIT] because this backs a picker
     * that is scrolled with a thumb, not a table that is scanned. The cap is
     * also what makes the scan cheap: once this many code matches exist there is
     * nothing a later slot could displace, so the loop stops early.
     */
    const val DEFAULT_LIMIT: Int = 50

    private val EMPTY = IntArray(0)

    /**
     * The best [limit] slots for [query].
     *
     * A blank query returns **nothing**, not everything. The caller is a picker
     * with an empty search box, and "every airport in the world, in runway-length
     * order" is not a useful thing to show there — see `PlanViewModel`, which
     * offers the largest airports as suggestions instead. Returning the whole
     * index would also be the one case that allocates.
     *
     * @param codes packed codes, slot-aligned — `AirportIndex.codes`.
     * @param size number of slots to scan; the two array parameters must be at
     *   least this long.
     * @param names slot-aligned airport names, or null when the name index has
     *   not been built yet, in which case matching falls back to codes alone.
     * @param municipalities slot-aligned municipalities, null entries allowed.
     */
    fun rank(
        query: String,
        codes: IntArray,
        size: Int,
        names: Array<String>? = null,
        municipalities: Array<String?>? = null,
        limit: Int = DEFAULT_LIMIT,
    ): IntArray {
        if (query.isBlank() || limit <= 0 || size <= 0) return EMPTY

        val prepared = SearchQuery(query)
        // Two fixed buffers rather than a heap: scores here take exactly two
        // values, and slots are visited in ascending order, so "score-descending,
        // ties by slot" is simply every code match in order followed by every
        // field match in order. No comparator, no boxing, no sort.
        val codeHits = IntArray(limit)
        var codeCount = 0
        val fieldHits = IntArray(limit)
        var fieldCount = 0

        for (slot in 0 until size) {
            if (IcaoCode.contains(codes[slot], query)) {
                codeHits[codeCount++] = slot
                // A full buffer of code matches cannot be improved on: every
                // remaining slot has a higher index, so it would lose every tie.
                if (codeCount == limit) break
                continue
            }
            if (fieldCount < limit &&
                (prepared.matches(names?.get(slot)) || prepared.matches(municipalities?.get(slot)))
            ) {
                fieldHits[fieldCount++] = slot
            }
        }

        val total = minOf(limit, codeCount + fieldCount)
        if (total == 0) return EMPTY

        val ranked = IntArray(total)
        val fromCodes = minOf(codeCount, total)
        codeHits.copyInto(ranked, 0, 0, fromCodes)
        if (fromCodes < total) fieldHits.copyInto(ranked, fromCodes, 0, total - fromCodes)
        return ranked
    }
}
