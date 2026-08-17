package com.github.daanbouwman.flightplanner.routing

import com.github.daanbouwman.flightplanner.model.AircraftSpec
import com.github.daanbouwman.flightplanner.model.Airport
import com.github.daanbouwman.flightplanner.model.FlightRecord
import java.util.PriorityQueue

/**
 * One search query, prepared once and matched against many candidates.
 *
 * The desktop app lowercases the query once outside the loop and then scans each
 * haystack byte-wise; this is the same idea expressed in Kotlin. The critical
 * property is that matching a candidate allocates **nothing** — no `lowercase()`
 * per field, no substring, no regex. Search runs over the whole airport table
 * (~24,000 rows) on every keystroke, so one allocation per field per candidate
 * would be roughly a hundred thousand short-lived strings per character typed.
 *
 * Matching is a case-insensitive substring test, exactly as on the desktop:
 * "ham" matches both `EHAM` and `Birmingham`.
 */
class SearchQuery(
    /** The query as the user typed it. Deliberately not trimmed — see [SearchScorer.rank]. */
    val text: String,
) {
    /** True when the query carries no search intent, so ranking is a no-op. */
    val isBlank: Boolean = text.isBlank()

    private val length: Int = text.length

    /**
     * The first character, hoisted so the scan can reject most positions with a
     * single comparison instead of a call into `regionMatches`. `' '` is an
     * arbitrary placeholder for the empty query, which never reaches the scan.
     */
    private val first: Char = if (text.isEmpty()) ' ' else text[0]

    /**
     * True when [haystack] contains this query, ignoring case.
     *
     * A null field never matches; an empty query matches anything non-null,
     * mirroring the desktop's `contains_case_insensitive_optimized`.
     */
    fun matches(haystack: String?): Boolean {
        if (haystack == null) return false
        if (length == 0) return true

        val last = haystack.length - length
        if (last < 0) return false

        for (i in 0..last) {
            // `Char.equals(ignoreCase = true)` applies the same upper-then-lower
            // folding that `String.regionMatches(ignoreCase = true)` applies, so
            // this guard can never reject a position the full test would accept.
            if (!haystack[i].equals(first, ignoreCase = true)) continue
            if (haystack.regionMatches(i, text, 0, length, ignoreCase = true)) return true
        }
        return false
    }
}

/**
 * Anything the search box can rank.
 *
 * Feature modules can implement this on their own row models rather than copying
 * data into a wrapper, which keeps the scoring loop allocation-free. The four
 * types the desktop app supports are provided as [SearchItem].
 */
fun interface SearchCandidate {
    /**
     * Returns [SearchScorer.CODE_MATCH_SCORE], [SearchScorer.FIELD_MATCH_SCORE]
     * or `0` — never anything else, or ranking stops being a two-tier sort.
     */
    fun searchScore(query: SearchQuery): Int
}

/**
 * Chooses between the two tiers.
 *
 * Both arguments are lambdas so a code match never pays for evaluating the
 * cheaper-tier fields; `inline` means neither lambda is ever allocated.
 */
private inline fun scoreOf(code: () -> Boolean, field: () -> Boolean): Int = when {
    code() -> SearchScorer.CODE_MATCH_SCORE
    field() -> SearchScorer.FIELD_MATCH_SCORE
    else -> 0
}

/**
 * The four kinds of row the desktop app searches over.
 *
 * Each is a field-for-field port of one arm of the Rust
 * `TableItem::search_score_optimized` match. The scoring *shape* — code beats
 * everything else, everything else is equal — lives once in [scoreOf]; only the
 * field lists differ per type, which is the only thing that genuinely does.
 */
sealed interface SearchItem : SearchCandidate

/**
 * An airport row. Code field: [icao]. Text fields: [name], [longestRunway].
 *
 * [longestRunway] is the *formatted* runway string the row displays, so typing
 * "10826" finds the airport whose longest runway reads 10,826 ft — the desktop
 * searches the rendered cell, not the raw integer.
 */
data class AirportSearchItem(
    val icao: String,
    val name: String,
    val longestRunway: String,
) : SearchItem {
    override fun searchScore(query: SearchQuery): Int = scoreOf(
        code = { query.matches(icao) },
        field = { query.matches(name) || query.matches(longestRunway) },
    )

    companion object {
        /** Builds a row from a domain airport, formatting the runway the way the list does. */
        fun from(airport: Airport, longestRunway: String = "${airport.longestRunwayFt} ft"): AirportSearchItem =
            AirportSearchItem(icao = airport.icao, name = airport.name, longestRunway = longestRunway)
    }
}

/**
 * An aircraft row. Code field: [icaoCode] (the *type* code, e.g. `B738`).
 * Text fields: [manufacturer], [variant], [category], [dateFlown].
 */
data class AircraftSearchItem(
    val icaoCode: String,
    val manufacturer: String,
    val variant: String,
    val category: String,
    val dateFlown: String?,
) : SearchItem {
    override fun searchScore(query: SearchQuery): Int = scoreOf(
        code = { query.matches(icaoCode) },
        field = {
            query.matches(manufacturer) ||
                query.matches(variant) ||
                query.matches(category) ||
                query.matches(dateFlown)
        },
    )

    companion object {
        fun from(spec: AircraftSpec): AircraftSearchItem = AircraftSearchItem(
            icaoCode = spec.icaoCode,
            manufacturer = spec.manufacturer,
            variant = spec.variant,
            category = spec.category,
            dateFlown = spec.dateFlown,
        )
    }
}

/**
 * A generated-route row. Code fields: [departureIcao], [destinationIcao].
 * Text fields: [departureInfo], [destinationInfo], [aircraftInfo].
 *
 * The `*Info` fields are the composite display strings — `"Name (ICAO)"` and
 * `"Manufacturer Variant"` — searched whole rather than as separate columns.
 * That is the desktop's optimisation, kept because it is also its behaviour:
 * a query spanning the boundary, such as "boeing 737", matches.
 */
data class RouteSearchItem(
    val departureIcao: String,
    val destinationIcao: String,
    val departureInfo: String,
    val destinationInfo: String,
    val aircraftInfo: String,
) : SearchItem {
    override fun searchScore(query: SearchQuery): Int = scoreOf(
        code = { query.matches(departureIcao) || query.matches(destinationIcao) },
        field = {
            query.matches(departureInfo) ||
                query.matches(destinationInfo) ||
                query.matches(aircraftInfo)
        },
    )

    companion object {
        fun from(departure: Airport, destination: Airport, aircraft: AircraftSpec): RouteSearchItem =
            RouteSearchItem(
                departureIcao = departure.icao,
                destinationIcao = destination.icao,
                departureInfo = departure.displayName,
                destinationInfo = destination.displayName,
                aircraftInfo = aircraft.displayName,
            )
    }
}

/**
 * A logbook row. Code fields: [departureIcao], [arrivalIcao].
 * Text fields: [aircraftName], [date].
 */
data class HistorySearchItem(
    val departureIcao: String,
    val arrivalIcao: String,
    val aircraftName: String,
    val date: String,
) : SearchItem {
    override fun searchScore(query: SearchQuery): Int = scoreOf(
        code = { query.matches(departureIcao) || query.matches(arrivalIcao) },
        field = { query.matches(aircraftName) || query.matches(date) },
    )

    companion object {
        fun from(record: FlightRecord, aircraftName: String): HistorySearchItem = HistorySearchItem(
            departureIcao = record.departureIcao,
            arrivalIcao = record.arrivalIcao,
            aircraftName = aircraftName,
            date = record.date,
        )
    }
}

/**
 * Ranks search candidates, porting the desktop app's `SearchService`.
 *
 * The rules, unchanged from Rust:
 *  - a match on a **code** field scores [CODE_MATCH_SCORE]
 *  - a match on any other field scores [FIELD_MATCH_SCORE]
 *  - non-matching candidates are dropped, and the rest are sorted score-descending
 *
 * Two things the port pins down that the original left to chance:
 *
 * **Ties are broken by input position, ascending.** Scores only take two values,
 * so ties are the common case, not the exception: a one-letter query can leave
 * thousands of candidates on score 1. Any tie-break that is not a stable
 * function of the input makes the result list visibly reshuffle between
 * keystrokes even where the matched set barely changed.
 *
 * **Top-K uses a bounded min-heap, not a full sort.** With a cap of [DEFAULT_LIMIT]
 * out of ~24,000 airports, sorting everything costs O(n log n) and — worse —
 * allocates a scored wrapper per candidate. The heap holds at most `limit`
 * entries and allocates only for candidates that are actually in contention.
 */
object SearchScorer {

    /** A match on an item's code field. */
    const val CODE_MATCH_SCORE: Int = 2

    /** A match on a name, manufacturer, variant, category, date or runway field. */
    const val FIELD_MATCH_SCORE: Int = 1

    /** Mirrors the desktop's `MAX_SEARCH_RESULTS`: enough to scroll, few enough to render. */
    const val DEFAULT_LIMIT: Int = 1_000

    /** Scores a single candidate. Convenience wrapper for one-off use and tests. */
    fun score(item: SearchCandidate, query: String): Int = item.searchScore(SearchQuery(query))

    /**
     * Returns the best [limit] matches for [query], score-descending, ties in
     * input order.
     *
     * A blank query returns [items] itself, untouched and untruncated — the list
     * is not a search result, so imposing a search result's ordering or cap on
     * it would be wrong.
     *
     * The query is **not** trimmed, matching the desktop: a trailing space is a
     * character the user typed, so `"EHAM "` is matched literally and finds
     * nothing.
     *
     * **One deliberate divergence from the desktop.** The short-circuit here is
     * `isBlank()`; the desktop's `filter_items_static` uses `is_empty()`. So a
     * whitespace-only query returns the whole list here, where the desktop would
     * filter to everything *containing* a space — which, since nearly every
     * airport and aircraft name has one, produces a list that looks unfiltered
     * but is quietly missing the entries that do not. Treating "nothing but
     * whitespace" as "no query" is the better reading of the user's intent, and
     * `SearchScorerTest` pins it. Recorded here because it is a real behavioural
     * difference from the reference implementation rather than an oversight.
     */
    fun <T : SearchCandidate> rank(
        items: List<T>,
        query: String,
        limit: Int = DEFAULT_LIMIT,
    ): List<T> {
        if (query.isBlank()) return items
        if (limit <= 0 || items.isEmpty()) return emptyList()

        val prepared = SearchQuery(query)
        // A min-heap ordered worst-first, so `peek` is always the entry to evict.
        val heap = PriorityQueue<Scored<T>>(minOf(limit, items.size))

        items.forEachIndexed { index, item ->
            val score = item.searchScore(prepared)
            if (score <= 0) return@forEachIndexed

            if (heap.size < limit) {
                heap.add(Scored(score, index, item))
                return@forEachIndexed
            }
            val worst = heap.peek()
            // Compared before allocating: most candidates in a large list lose.
            // Because indices only increase, an equal score can never displace an
            // incumbent — which is exactly the stable tie-break, enforced here.
            val better = score > worst.score || (score == worst.score && index < worst.index)
            if (better) {
                heap.poll()
                heap.add(Scored(score, index, item))
            }
        }

        val ranked = ArrayList<Scored<T>>(heap)
        ranked.sortWith(compareByDescending<Scored<T>> { it.score }.thenBy { it.index })
        return ranked.map { it.item }
    }

    /**
     * A candidate that made the cut.
     *
     * Natural order is deliberately *worst first* — lowest score, and among equal
     * scores the highest input index — so that a [PriorityQueue] over it evicts
     * the entry the final ordering would have placed last.
     */
    private class Scored<T>(
        val score: Int,
        val index: Int,
        val item: T,
    ) : Comparable<Scored<*>> {
        override fun compareTo(other: Scored<*>): Int {
            val byScore = score.compareTo(other.score)
            return if (byScore != 0) byScore else other.index.compareTo(index)
        }
    }
}
