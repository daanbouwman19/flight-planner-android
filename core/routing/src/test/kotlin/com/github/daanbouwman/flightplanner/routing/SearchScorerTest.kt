package com.github.daanbouwman.flightplanner.routing

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.of
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * Fixtures shared by the scoring tests.
 *
 * Every field value is chosen so that a query aimed at one field cannot
 * accidentally be a substring of another — otherwise a test asserting "1"
 * would pass even if the code tier were broken.
 */
private val schiphol = AirportSearchItem(
    icao = "EHAM",
    name = "Amsterdam Airport Schiphol",
    longestRunway = "12467 ft",
)

private val boeing = AircraftSearchItem(
    icaoCode = "B738",
    manufacturer = "Boeing",
    variant = "737-800",
    category = "Jet",
    dateFlown = "2024-05-11",
)

private val route = RouteSearchItem(
    departureIcao = "EHAM",
    destinationIcao = "KJFK",
    departureInfo = "Amsterdam Airport Schiphol (EHAM)",
    destinationInfo = "John F Kennedy Intl (KJFK)",
    aircraftInfo = "Boeing 737-800",
)

private val logged = HistorySearchItem(
    departureIcao = "EHAM",
    arrivalIcao = "EGLL",
    aircraftName = "Boeing 737-800",
    date = "2024-05-11",
)

class SearchScorerScoringTest {

    @Test
    fun `an airport scores 2 on its code and 1 on its other fields`() {
        SearchScorer.score(schiphol, "EHAM") shouldBe 2
        SearchScorer.score(schiphol, "Amsterdam") shouldBe 1
        SearchScorer.score(schiphol, "Schiphol") shouldBe 1
        SearchScorer.score(schiphol, "12467") shouldBe 1
        SearchScorer.score(schiphol, "Brisbane") shouldBe 0
    }

    @Test
    fun `an aircraft scores 2 on its type code and 1 on the rest`() {
        SearchScorer.score(boeing, "B738") shouldBe 2
        SearchScorer.score(boeing, "Boeing") shouldBe 1
        SearchScorer.score(boeing, "737-800") shouldBe 1
        SearchScorer.score(boeing, "Jet") shouldBe 1
        SearchScorer.score(boeing, "2024-05") shouldBe 1
        SearchScorer.score(boeing, "Cessna") shouldBe 0
    }

    @Test
    fun `an aircraft that has never been flown has no date to match`() {
        val neverFlown = boeing.copy(dateFlown = null)
        SearchScorer.score(neverFlown, "2024-05") shouldBe 0
        // The other fields still work, so a null date cannot short-circuit them.
        SearchScorer.score(neverFlown, "Jet") shouldBe 1
    }

    @Test
    fun `a route scores 2 on either end's code and 1 on either end's name`() {
        SearchScorer.score(route, "EHAM") shouldBe 2
        SearchScorer.score(route, "KJFK") shouldBe 2
        SearchScorer.score(route, "Schiphol") shouldBe 1
        SearchScorer.score(route, "Kennedy") shouldBe 1
        SearchScorer.score(route, "Boeing") shouldBe 1
        SearchScorer.score(route, "Bergerac") shouldBe 0
    }

    @Test
    fun `a route matches an aircraft query spanning manufacturer and variant`() {
        // `aircraft_info` is searched as one string on the desktop, so a query
        // crossing the space between the two fields matches. Pinning this stops
        // a "tidy-up" into two separate fields from silently changing results.
        SearchScorer.score(route, "Boeing 737") shouldBe 1
    }

    @Test
    fun `a logbook entry scores 2 on either code and 1 on aircraft or date`() {
        SearchScorer.score(logged, "EHAM") shouldBe 2
        SearchScorer.score(logged, "EGLL") shouldBe 2
        SearchScorer.score(logged, "Boeing") shouldBe 1
        SearchScorer.score(logged, "2024") shouldBe 1
        SearchScorer.score(logged, "Airbus") shouldBe 0
    }

    @Test
    fun `matching ignores case in both directions`() {
        SearchScorer.score(schiphol, "eham") shouldBe 2
        SearchScorer.score(schiphol, "EhAm") shouldBe 2
        SearchScorer.score(schiphol, "SCHIPHOL") shouldBe 1
        SearchScorer.score(boeing, "b738") shouldBe 2
        SearchScorer.score(boeing, "jET") shouldBe 1
    }

    @Test
    fun `a code match wins even when a lower-tier field also matches`() {
        // "EHAM" appears in both the code and the display string; the code tier
        // has to win, not merely be checked first by accident.
        SearchScorer.score(route, "EHAM") shouldBe 2
    }

    @Test
    fun `a query longer than the field cannot match`() {
        SearchScorer.score(schiphol, "EHAMEHAM") shouldBe 0
    }

    @Test
    fun `a match at the very end of a field is found`() {
        // An off-by-one in the scan bound would drop exactly this case.
        SearchScorer.score(schiphol, "M") shouldBe 2
        SearchScorer.score(schiphol, "ft") shouldBe 1
    }
}

class SearchScorerRankingTest {

    /** Scores 1: "Quebec" is in the name only. */
    private fun nameMatch(index: Int) = AirportSearchItem(
        icao = "AA${index.toString().padStart(2, '0')}",
        name = "Quebec Field $index",
        longestRunway = "1000 ft",
    )

    /** Scores 2: "Q" is in the code. */
    private fun codeMatch(index: Int) = AirportSearchItem(
        icao = "Q${index.toString().padStart(3, '0')}",
        name = "Nowhere $index",
        longestRunway = "1000 ft",
    )

    @Test
    fun `code matches sort ahead of field matches`() {
        val items = listOf(nameMatch(0), codeMatch(1), nameMatch(2), codeMatch(3))
        SearchScorer.rank(items, "q") shouldBe listOf(codeMatch(1), codeMatch(3), nameMatch(0), nameMatch(2))
    }

    @Test
    fun `items that do not match are dropped`() {
        val items = listOf(nameMatch(0), schiphol, codeMatch(1))
        SearchScorer.rank(items, "q") shouldBe listOf(codeMatch(1), nameMatch(0))
    }

    @Test
    fun `no matches yields an empty list rather than the input`() {
        SearchScorer.rank(listOf(nameMatch(0), codeMatch(1)), "zzz").shouldBeEmpty()
    }

    @Test
    fun `a blank query returns the input untouched and in order`() {
        val items = listOf(nameMatch(2), nameMatch(0), codeMatch(1))
        SearchScorer.rank(items, "") shouldBe items
        SearchScorer.rank(items, "   ") shouldBe items
        // Untruncated too: a non-search is not a search result.
        SearchScorer.rank(items, "", limit = 1) shouldBe items
    }

    @Test
    fun `equal scores keep input order`() {
        // All five score 1. Anything other than input order here shows up in the
        // UI as the list reshuffling under the user's finger between keystrokes.
        val items = (0 until 5).map(::nameMatch)
        SearchScorer.rank(items, "quebec") shouldBe items
    }

    @Test
    fun `equal scores keep input order when the input order is not sorted`() {
        // The reversed construction rules out a tie-break that happens to agree
        // with some natural ordering of the items themselves.
        val items = listOf(nameMatch(4), nameMatch(1), nameMatch(3), nameMatch(0), nameMatch(2))
        SearchScorer.rank(items, "quebec") shouldBe items
    }

    @Test
    fun `the limit keeps the best matches, earliest first`() {
        // Deliberately puts the score-1 items first, so a limit that simply took
        // the first three matches would return the wrong three.
        val items = listOf(
            nameMatch(0), nameMatch(1), nameMatch(2),
            codeMatch(3), codeMatch(4), codeMatch(5), codeMatch(6),
        )
        SearchScorer.rank(items, "q", limit = 3) shouldBe listOf(codeMatch(3), codeMatch(4), codeMatch(5))
    }

    @Test
    fun `a full heap is not disturbed by an equally-scoring later item`() {
        // Once `limit` items share the top score, later ties must lose, because
        // they sit later in the input. This is the eviction rule's tie case.
        val items = (0 until 6).map(::codeMatch)
        SearchScorer.rank(items, "q", limit = 2) shouldBe listOf(codeMatch(0), codeMatch(1))
    }

    @Test
    fun `a non-positive limit yields nothing`() {
        SearchScorer.rank((0 until 3).map(::codeMatch), "q", limit = 0).shouldBeEmpty()
        SearchScorer.rank((0 until 3).map(::codeMatch), "q", limit = -1).shouldBeEmpty()
    }

    @Test
    fun `an empty input yields an empty result`() {
        SearchScorer.rank(emptyList<AirportSearchItem>(), "q").shouldBeEmpty()
    }

    @Test
    fun `a limit larger than the input is harmless`() {
        val items = listOf(codeMatch(0), nameMatch(1))
        SearchScorer.rank(items, "q", limit = 1_000) shouldBe listOf(codeMatch(0), nameMatch(1))
    }

    @Test
    fun `a custom candidate type ranks alongside the built-in ones`() {
        // The scorer is open to feature-module row models, which is the whole
        // point of `SearchCandidate` being an interface rather than a sealed set.
        class Row(val label: String) : SearchCandidate {
            override fun searchScore(query: SearchQuery): Int =
                if (query.matches(label)) SearchScorer.CODE_MATCH_SCORE else 0
        }

        val rows = listOf(Row("alpha"), Row("bravo"), Row("charlie"))
        SearchScorer.rank(rows, "a").map { it.label } shouldBe listOf("alpha", "bravo", "charlie")
        SearchScorer.rank(rows, "r").map { it.label } shouldBe listOf("bravo", "charlie")
    }
}

class SearchScorerTopKPropertyTest {

    private val itemArb = Arb.bind(
        Arb.of("AAAA", "QAAA", "BBBB", "QQQQ", "ABQD", "ZZZZ"),
        Arb.of("Alpha", "Quebec", "Bravo", "aq", "Zulu Field", ""),
        Arb.of("1000 ft", "2000 ft", "999 ft"),
    ) { code, name, runway -> AirportSearchItem(code, name, runway) }

    /** The obvious, slow implementation: score everything, sort everything. */
    private fun reference(items: List<AirportSearchItem>, query: String): List<AirportSearchItem> =
        items.withIndex()
            .map { (index, item) -> Triple(SearchScorer.score(item, query), index, item) }
            .filter { it.first > 0 }
            .sortedWith(compareByDescending<Triple<Int, Int, AirportSearchItem>> { it.first }.thenBy { it.second })
            .map { it.third }

    @Test
    fun `top-K equals the first K of a full sort`() = runTest {
        checkAll(
            Arb.list(itemArb, 0..40),
            Arb.of("q", "a", "Q", "bb", "zz", "1000", "ft", "x", "quebec"),
            Arb.int(1..12),
        ) { items, query, limit ->
            SearchScorer.rank(items, query, limit) shouldBe reference(items, query).take(limit)
        }
    }

    @Test
    fun `an unbounded rank equals a full sort`() = runTest {
        checkAll(
            Arb.list(itemArb, 0..40),
            Arb.of("q", "a", "bb", "zz", "1000"),
        ) { items, query ->
            SearchScorer.rank(items, query, limit = items.size + 1) shouldBe reference(items, query)
        }
    }
}
