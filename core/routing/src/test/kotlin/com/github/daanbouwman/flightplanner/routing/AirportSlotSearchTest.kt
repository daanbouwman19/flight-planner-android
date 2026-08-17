package com.github.daanbouwman.flightplanner.routing

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlin.test.Test

/**
 * Fixture in the slot order the real index uses — ascending runway length — so
 * that "ties resolve by slot" is being asserted against a meaningful order
 * rather than against alphabetical accident.
 */
private val codes = intArrayOf(
    IcaoCode.encode("EHAM"), // 0
    IcaoCode.encode("KJFK"), // 1
    IcaoCode.encode("EGLL"), // 2
    IcaoCode.encode("LFPG"), // 3
    IcaoCode.encode("EHRD"), // 4
)

private val names = arrayOf(
    "Amsterdam Airport Schiphol",
    "John F Kennedy International Airport",
    "London Heathrow Airport",
    "Charles de Gaulle International Airport",
    "Rotterdam The Hague Airport",
)

private val municipalities = arrayOf<String?>(
    "Amsterdam",
    "New York",
    "London",
    "Paris",
    null,
)

private fun rank(query: String, limit: Int = AirportSlotSearch.DEFAULT_LIMIT): List<Int> =
    AirportSlotSearch.rank(query, codes, codes.size, names, municipalities, limit).toList()

class AirportSlotSearchTest {

    @Test
    fun `a code match outranks a name match`() {
        // "EH" is a code substring of EHAM and EHRD, and appears in no name;
        // "am" is in Amsterdam. Code hits must come first regardless of slot.
        rank("EHR") shouldContainExactly listOf(4)
    }

    @Test
    fun `code hits precede field hits even when the field hit has a lower slot`() {
        // "L" matches EGLL and LFPG by code (slots 2 and 3) and Schiphol and
        // "International" by name (slots 0 and 1). The two code hits sit at
        // higher slots than both field hits, so a result that merely preserved
        // slot order would put 0 and 1 first — the tier has to win.
        rank("L") shouldContainExactly listOf(2, 3, 0, 1)
    }

    @Test
    fun `a query matching only text stays in the field tier`() {
        // "LO" is in no code (EGLL's pairs are EG, GL, LL) but is in "London".
        rank("LO") shouldContainExactly listOf(2)
    }

    @Test
    fun `matching is case insensitive in both directions`() {
        rank("eham") shouldContainExactly listOf(0)
        rank("AMSTERDAM") shouldContainExactly listOf(0)
    }

    @Test
    fun `municipality is searchable`() {
        // Nothing in Rotterdam's row matches "hague" except its name; nothing in
        // JFK's row matches "new york" except its municipality.
        rank("new york") shouldContainExactly listOf(1)
    }

    @Test
    fun `a row with no municipality is still searchable by name`() {
        // Slot 4 publishes no municipality. Reading a null out of the array must
        // fall through to the name rather than short-circuit the whole row.
        rank("Hague") shouldContainExactly listOf(4)
    }

    @Test
    fun `a blank query returns nothing rather than everything`() {
        rank("") shouldBe emptyList()
        rank("   ") shouldBe emptyList()
    }

    @Test
    fun `a query longer than a code cannot match a code`() {
        // "EHAMX" is five characters; no four-character code can contain it.
        // It must not match by code, and it matches no name either.
        rank("EHAMX") shouldBe emptyList()
    }

    @Test
    fun `without a name index it still matches codes`() {
        val slots = AirportSlotSearch.rank("EG", codes, codes.size, names = null, municipalities = null)
        slots.toList() shouldContainExactly listOf(2)
    }

    @Test
    fun `without a name index a name-only query finds nothing`() {
        val slots = AirportSlotSearch.rank("Heathrow", codes, codes.size, names = null, municipalities = null)
        slots.toList() shouldBe emptyList()
    }

    @Test
    fun `the limit caps the result and keeps the best entries`() {
        // Three code hits are available for "E" (EHAM, EGLL, EHRD); a limit of two
        // must keep the two lowest slots and drop every field hit.
        rank("E", limit = 2) shouldContainExactly listOf(0, 2)
    }

    @Test
    fun `a limit of zero or a negative limit yields nothing`() {
        rank("E", limit = 0) shouldBe emptyList()
        rank("E", limit = -1) shouldBe emptyList()
    }

    @Test
    fun `size bounds the scan`() {
        // Only the first two slots are visible, so EHRD is out of range even
        // though its code matches.
        AirportSlotSearch.rank("EH", codes, size = 2, names = names, municipalities = municipalities)
            .toList() shouldContainExactly listOf(0)
    }
}

class IcaoCodeContainsTest {

    @Test
    fun `matches every substring position`() {
        val eham = IcaoCode.encode("EHAM")
        IcaoCode.contains(eham, "E") shouldBe true
        IcaoCode.contains(eham, "H") shouldBe true
        IcaoCode.contains(eham, "AM") shouldBe true
        IcaoCode.contains(eham, "EHAM") shouldBe true
        IcaoCode.contains(eham, "HA") shouldBe true
    }

    @Test
    fun `rejects a substring that is not present`() {
        val eham = IcaoCode.encode("EHAM")
        IcaoCode.contains(eham, "EA") shouldBe false
        IcaoCode.contains(eham, "MA") shouldBe false
        IcaoCode.contains(eham, "Z") shouldBe false
    }

    @Test
    fun `agrees with decoding for every code in the alphabet`() {
        // The packed form is read arithmetically rather than decoded, so this
        // pins the two representations together: every position of every code
        // must yield the character the decoder produces.
        for (code in listOf("EHAM", "KJFK", "0A9Z", "ZZZZ", "0000")) {
            val packed = IcaoCode.encode(code)
            for (position in code.indices) {
                IcaoCode.charAt(packed, position) shouldBe code[position]
            }
        }
    }

    @Test
    fun `an invalid code never matches`() {
        IcaoCode.contains(IcaoCode.INVALID, "E") shouldBe false
    }
}
