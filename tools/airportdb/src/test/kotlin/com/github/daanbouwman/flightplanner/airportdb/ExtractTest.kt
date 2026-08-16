package com.github.daanbouwman.flightplanner.airportdb

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class HeadingFromIdentTest {

    @Test
    fun `numeric idents map to their heading`() {
        headingFromIdent("09") shouldBe 90.0
        headingFromIdent("27") shouldBe 270.0
        headingFromIdent("36") shouldBe 360.0
        headingFromIdent("01") shouldBe 10.0
    }

    @Test
    fun `parallel runway suffixes are ignored`() {
        headingFromIdent("27L") shouldBe 270.0
        headingFromIdent("18R") shouldBe 180.0
        headingFromIdent("36C") shouldBe 360.0
    }

    @Test
    fun `compass idents are supported because grass strips use them`() {
        headingFromIdent("N") shouldBe 0.0
        headingFromIdent("NE") shouldBe 45.0
        headingFromIdent("SW") shouldBe 225.0
        headingFromIdent("WNW") shouldBe 292.5
    }

    @Test
    fun `out-of-range and unrecognised idents yield no heading rather than a wrong one`() {
        headingFromIdent("00").shouldBeNull()
        headingFromIdent("99").shouldBeNull()
        headingFromIdent("ALL").shouldBeNull()
        headingFromIdent("").shouldBeNull()
    }
}

class ResolveCodeTest {

    @Test
    fun `a real icao code always wins and is marked as such`() {
        for (tier in FilterTier.entries) {
            resolveCode(icaoCode = "eham", ident = "EHAM", tier = tier) shouldBe ("EHAM" to true)
        }
    }

    @Test
    fun `strict tier rejects anything without a real icao code`() {
        resolveCode(icaoCode = "", ident = "KXYZ", tier = FilterTier.STRICT).shouldBeNull()
        resolveCode(icaoCode = "", ident = "AK01", tier = FilterTier.STRICT).shouldBeNull()
    }

    @Test
    fun `alpha tier accepts four letters but not alphanumeric idents`() {
        resolveCode(icaoCode = "", ident = "KXYZ", tier = FilterTier.ALPHA) shouldBe ("KXYZ" to false)
        resolveCode(icaoCode = "", ident = "AK01", tier = FilterTier.ALPHA).shouldBeNull()
    }

    @Test
    fun `alnum tier accepts faa style local codes`() {
        resolveCode(icaoCode = "", ident = "AK01", tier = FilterTier.ALNUM) shouldBe ("AK01" to false)
        // Still bounded to exactly four characters.
        resolveCode(icaoCode = "", ident = "A1", tier = FilterTier.ALNUM).shouldBeNull()
        resolveCode(icaoCode = "", ident = "TOOLONG", tier = FilterTier.ALNUM).shouldBeNull()
    }
}
