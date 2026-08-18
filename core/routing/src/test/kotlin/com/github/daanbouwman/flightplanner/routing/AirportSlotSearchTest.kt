package com.github.daanbouwman.flightplanner.routing

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlin.test.Test

/**
 * Fixture in the slot order the real index uses — **ascending runway length** —
 * so that "the more significant airport wins" is asserted against a meaningful
 * order rather than against alphabetical accident. Slot 5 is the largest field
 * here and slot 0 the smallest.
 *
 * ### These expectations changed deliberately
 *
 * The previous version of this file asserted the desktop app's two-tier scoring:
 * code beats field, ties break by ascending slot. Every one of those assertions
 * passed, and the behaviour they pinned down was the defect — on the shipped
 * dataset it put Ecuadorian airstrips above Schiphol for the query `EH`, because
 * ascending slot *is* ascending runway length. The assertions were not weakened
 * to make anything pass; they were rewritten because they encoded the wrong
 * ranking. See [AirportSlotSearch] for the reasoning.
 */
private val codes = intArrayOf(
    IcaoCode.encode("EEHA"), // 0 — smallest: contains "EHA" but does not begin with it
    IcaoCode.encode("EHAL"), // 1
    IcaoCode.encode("KJFK"), // 2
    IcaoCode.encode("EGLL"), // 3
    IcaoCode.encode("EHAM"), // 4
    IcaoCode.encode("EHRD"), // 5 — largest
)

private val names = arrayOf(
    "Humala Airfield",
    "Ameland Airport Ballum",
    "John F Kennedy International Airport",
    "London Heathrow Airport",
    "Amsterdam Airport Schiphol",
    "Rotterdam The Hague Airport",
)

private val municipalities = arrayOf<String?>(
    "Humala",
    "Ballum",
    "New York",
    "London",
    "Amsterdam",
    null,
)

private fun rank(query: String, limit: Int = AirportSlotSearch.DEFAULT_LIMIT): List<Int> =
    AirportSlotSearch.rank(query, codes, codes.size, names, municipalities, limit).toList()

class AirportSlotSearchTest {

    /**
     * The regression this ranking exists for: on the real dataset, `EHA` used to
     * return EEHA, then EHAL, then an Elkhart county field, and only then
     * Schiphol.
     */
    @Test
    fun `a prefix outranks a code that merely contains the query`() {
        // EHAM (4) and EHAL (1) begin with EHA; EEHA (0) only contains it.
        rank("EHA") shouldContainExactly listOf(4, 1, 0)
    }

    @Test
    fun `an exact code wins outright`() {
        // EHAM is exact; every other match is a prefix, a substring or a name.
        rank("EHAM").first() shouldBe 4
    }

    @Test
    fun `within a tier the larger airport comes first`() {
        // E begins EEHA, EHAL, EGLL, EHAM and EHRD. Ordered by significance, the
        // largest field leads — the opposite of what slot order alone would give.
        rank("E").take(5) shouldContainExactly listOf(5, 4, 3, 1, 0)
    }

    @Test
    fun `a code hit precedes a field hit whatever their sizes`() {
        // Code hits: EGLL (3) and EHAL (1), both by substring. Field hits: Schiphol
        // (4), International (2) and Airfield (0). Every code hit first, and each
        // tier ordered by size.
        rank("L") shouldContainExactly listOf(3, 1, 4, 2, 0)
    }

    @Test
    fun `a query matching only text stays in the field tier`() {
        rank("Heathrow") shouldContainExactly listOf(3)
    }

    @Test
    fun `matching is case insensitive in both directions`() {
        rank("eham").first() shouldBe 4
        rank("AMSTERDAM") shouldContainExactly listOf(4)
    }

    @Test
    fun `municipality is searchable`() {
        rank("new york") shouldContainExactly listOf(2)
    }

    @Test
    fun `a row with no municipality is still searchable by name`() {
        // Slot 5 publishes no municipality; reading a null must fall through to
        // the name rather than short-circuit the row.
        rank("Hague") shouldContainExactly listOf(5)
    }

    @Test
    fun `surrounding whitespace does not change the ranking`() {
        rank("  EHAM  ").first() shouldBe 4
    }

    @Test
    fun `a blank query returns nothing rather than everything`() {
        rank("") shouldBe emptyList()
        rank("   ") shouldBe emptyList()
    }

    @Test
    fun `a query longer than a code cannot match a code`() {
        rank("EHAMX") shouldBe emptyList()
    }

    @Test
    fun `without a name index it still matches codes`() {
        val slots = AirportSlotSearch.rank("EG", codes, codes.size, names = null, municipalities = null)
        slots.toList() shouldContainExactly listOf(3)
    }

    @Test
    fun `without a name index a name-only query finds nothing`() {
        val slots = AirportSlotSearch.rank("Heathrow", codes, codes.size, names = null, municipalities = null)
        slots.toList() shouldBe emptyList()
    }

    /**
     * The other half of the original defect: the scan used to stop as soon as the
     * cap was full of *any* matches, so a large airport could be excluded rather
     * than merely ranked low.
     */
    @Test
    fun `a capped result keeps the best matches, not the first ones found`() {
        rank("EHA", limit = 2) shouldContainExactly listOf(4, 1)
        rank("E", limit = 1) shouldContainExactly listOf(5)
    }

    @Test
    fun `a limit of zero or a negative limit yields nothing`() {
        rank("E", limit = 0) shouldBe emptyList()
        rank("E", limit = -1) shouldBe emptyList()
    }

    @Test
    fun `size bounds the scan`() {
        // Only the first two slots are visible, so EHAM is out of range even
        // though its code matches best.
        AirportSlotSearch.rank("EHA", codes, size = 2, names = names, municipalities = municipalities)
            .toList() shouldContainExactly listOf(1, 0)
    }
}

class IcaoCodeMatchTest {

    private val eham = IcaoCode.encode("EHAM")

    @Test
    fun `contains matches every substring position`() {
        IcaoCode.contains(eham, "E") shouldBe true
        IcaoCode.contains(eham, "H") shouldBe true
        IcaoCode.contains(eham, "AM") shouldBe true
        IcaoCode.contains(eham, "EHAM") shouldBe true
        IcaoCode.contains(eham, "HA") shouldBe true
    }

    @Test
    fun `startsWith matches only from the first character`() {
        IcaoCode.startsWith(eham, "E") shouldBe true
        IcaoCode.startsWith(eham, "EH") shouldBe true
        IcaoCode.startsWith(eham, "EHAM") shouldBe true
        IcaoCode.startsWith(eham, "H") shouldBe false
        IcaoCode.startsWith(eham, "HAM") shouldBe false
        IcaoCode.startsWith(eham, "EHAMX") shouldBe false
    }

    @Test
    fun `matches is exact and length-sensitive`() {
        IcaoCode.matches(eham, "EHAM") shouldBe true
        IcaoCode.matches(eham, "eham") shouldBe true
        IcaoCode.matches(eham, "EHA") shouldBe false
        IcaoCode.matches(eham, "EHAMM") shouldBe false
    }

    @Test
    fun `an invalid code matches nothing`() {
        IcaoCode.startsWith(IcaoCode.INVALID, "E") shouldBe false
        IcaoCode.matches(IcaoCode.INVALID, "EHAM") shouldBe false
        IcaoCode.contains(IcaoCode.INVALID, "E") shouldBe false
    }
}
