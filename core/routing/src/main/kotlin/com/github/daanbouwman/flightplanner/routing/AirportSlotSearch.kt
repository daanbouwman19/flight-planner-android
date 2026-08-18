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
 * ### Why this is not the desktop's two-tier scoring
 *
 * The desktop app scores a code match 2, any other field 1, and sorts. Ported
 * literally, that made this picker unusable, and the reason is worth stating
 * because it applies to every ranked list in this app: **a table's rank and a
 * type-ahead's rank are different problems.** The desktop showed a sortable
 * table, so its ranking only had to be defensible — the user could re-sort. A
 * type-ahead has no re-sort. Rank *is* the interface, and the first row is the
 * answer.
 *
 * Two consequences, both measured against the shipped dataset:
 *
 *  - **A prefix is not a substring.** Typing `EHA` means "codes beginning EHA"
 *    to anyone who has used an airport code. Ranked by substring alone, EEHA and
 *    an Elkhart county field outrank Schiphol, because their codes happen to
 *    contain the letters too.
 *  - **Ties have to break on significance.** Scores took two values, so ties were
 *    the common case, and they broke by slot order — which is ascending runway
 *    length, so the least significant airport on Earth sorted first. Worse, the
 *    scan stopped once the result cap was full of matches, so for a two-letter
 *    query a major airport could be excluded rather than merely buried.
 *
 * ### The four tiers
 *
 * Exact code, code prefix, code substring, then name or municipality. Within a
 * tier, results are ordered by descending runway length — which costs nothing,
 * because the index is sorted *ascending* by runway length and this scans it
 * backwards.
 *
 * That backwards scan is also what makes the early exit safe: every slot still
 * unvisited is less significant than everything already found, so once the two
 * top tiers hold [limit] results between them, nothing later can displace them.
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
     * that is scrolled with a thumb, not a table that is scanned.
     */
    const val DEFAULT_LIMIT: Int = 50

    private val EMPTY = IntArray(0)

    /**
     * The best [limit] slots for [query], best first.
     *
     * A blank query returns **nothing**, not everything. The caller is a picker
     * with an empty search box, and "every airport in the world" is not a useful
     * thing to show there — see `PlanViewModel`, which offers the largest
     * airports as suggestions instead.
     *
     * @param codes packed codes, slot-aligned — `AirportIndex.codes`.
     * @param size number of slots to scan; the array parameters must be at least
     *   this long.
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
        val trimmed = query.trim()
        if (trimmed.isEmpty() || limit <= 0 || size <= 0) return EMPTY

        val prepared = SearchQuery(trimmed)

        // One buffer per tier rather than a heap: within a tier the scan already
        // visits slots in the order the results want, so there is nothing to
        // sort — no comparator, no boxing, no allocation per candidate.
        val exact = IntArray(limit)
        var exactCount = 0
        val prefix = IntArray(limit)
        var prefixCount = 0
        val contains = IntArray(limit)
        var containsCount = 0
        val field = IntArray(limit)
        var fieldCount = 0

        // Backwards: the index is sorted ascending by runway length, so this
        // visits the most significant airports first and every tier comes out
        // ordered without a sort.
        for (slot in size - 1 downTo 0) {
            val code = codes[slot]
            when {
                IcaoCode.matches(code, trimmed) ->
                    if (exactCount < limit) exact[exactCount++] = slot

                IcaoCode.startsWith(code, trimmed) ->
                    if (prefixCount < limit) prefix[prefixCount++] = slot

                IcaoCode.contains(code, trimmed) ->
                    if (containsCount < limit) contains[containsCount++] = slot

                fieldCount < limit &&
                    (prepared.matches(names?.get(slot)) || prepared.matches(municipalities?.get(slot))) ->
                    field[fieldCount++] = slot
            }

            // Everything left to visit is less significant than what the top two
            // tiers already hold, so nothing later could displace it.
            if (exactCount + prefixCount >= limit) break
        }

        val total = minOf(limit, exactCount + prefixCount + containsCount + fieldCount)
        if (total == 0) return EMPTY

        val ranked = IntArray(total)
        var out = 0
        out += copyTier(exact, exactCount, ranked, out)
        out += copyTier(prefix, prefixCount, ranked, out)
        out += copyTier(contains, containsCount, ranked, out)
        copyTier(field, fieldCount, ranked, out)
        return ranked
    }

    /** Copies as much of a tier as still fits, and reports how much that was. */
    private fun copyTier(tier: IntArray, count: Int, into: IntArray, at: Int): Int {
        val room = into.size - at
        if (room <= 0 || count <= 0) return 0
        val taken = minOf(count, room)
        tier.copyInto(into, at, 0, taken)
        return taken
    }
}
