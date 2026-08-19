package com.github.daanbouwman.flightplanner.ui.detail

import com.github.daanbouwman.flightplanner.model.AircraftSpec
import com.github.daanbouwman.flightplanner.model.Airport
import com.github.daanbouwman.flightplanner.model.AirportSizeClass
import com.github.daanbouwman.flightplanner.routing.FlightTime
import io.kotest.matchers.shouldBe
import kotlin.test.Test

/**
 * The formats this app hands to other applications.
 *
 * Asserted against the literal strings `route_popup.rs` builds, rather than
 * against a regular expression, because the point of the port is that the two
 * applications produce the *same* text — and a pattern that admits both would
 * not notice the day they stopped agreeing.
 */
class RouteLinksTest {

    private val boeing = AircraftSpec(
        id = 1,
        manufacturer = "Boeing",
        variant = "737-800",
        icaoCode = "B738",
        flown = false,
        rangeNm = 2935,
        category = "Jet",
        cruiseSpeedKt = 460,
        dateFlown = null,
        takeoffDistanceMeters = 2000,
    )

    private fun airport(icao: String, hasIcaoCode: Boolean = true) = Airport(
        id = 1,
        icao = icao,
        name = icao,
        latitude = 52.3086,
        longitude = 4.7639,
        elevationFt = -11,
        country = "NL",
        municipality = "Haarlemmermeer",
        sizeClass = AirportSizeClass.LARGE,
        longestRunwayFt = 12467,
        runwayCount = 6,
        hasHardSurface = true,
        hasIcaoCode = hasIcaoCode,
    )

    @Test
    fun `the summary is the desktop's, ungrouped`() {
        RouteLinks.summary(boeing, "EHAM", "KJFK", 2847) shouldBe
            "Boeing 737-800: EHAM -> KJFK (2847 nm)"
    }

    @Test
    fun `SkyVector encodes the space the desktop leaves raw`() {
        RouteLinks.skyVector("EHAM", "KJFK") shouldBe "https://skyvector.com/?fpl=EHAM%20KJFK"
    }

    @Test
    fun `SimBrief carries the type, both ends and the estimate, in that order`() {
        RouteLinks.simBrief(boeing, "EHAM", "KJFK", FlightTime(6, 18, 460.0)) shouldBe
            "https://dispatch.simbrief.com/options/custom" +
            "?type=B738&orig=EHAM&dest=KJFK&steh=6&stem=18"
    }

    @Test
    fun `Google Maps takes the reference point at fixed precision`() {
        // Six decimals, not `Double.toString`. The trailing zeros are the point:
        // a fixed format is the only one that cannot produce the case below.
        RouteLinks.googleMaps(52.3086, 4.7639) shouldBe
            "https://www.google.com/maps/search/?api=1&query=52.308600,4.763900"
    }

    @Test
    fun `a coordinate near zero does not become scientific notation`() {
        // `Double.toString` switches to exponent form below 1e-3, so an airport
        // within about 100 m of the equator or the prime meridian used to produce
        // `query=4.0E-4,32.1`, which Google Maps does not resolve at all.
        RouteLinks.googleMaps(0.0004, 32.1) shouldBe
            "https://www.google.com/maps/search/?api=1&query=0.000400,32.100000"
    }

    @Test
    fun `SimBrief is offered only when both ends have a real ICAO code`() {
        RouteLinks.simBriefAccepts(airport("EHAM"), airport("KJFK")) shouldBe true
        // An OurAirports ident is not an ICAO code, and SimBrief resolves airports
        // by the latter — so the link would open a form that rejects the route.
        RouteLinks.simBriefAccepts(airport("EHAM"), airport("XY01", hasIcaoCode = false)) shouldBe false
        RouteLinks.simBriefAccepts(airport("XY01", hasIcaoCode = false), airport("KJFK")) shouldBe false
    }
}
