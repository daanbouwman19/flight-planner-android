package com.github.daanbouwman.flightplanner.ui.detail

import com.github.daanbouwman.flightplanner.model.AircraftSpec
import com.github.daanbouwman.flightplanner.model.Airport
import com.github.daanbouwman.flightplanner.routing.FlightTime
import java.net.URLEncoder
import java.util.Locale

/**
 * The strings the route detail hands to other applications.
 *
 * Ported field-for-field from the desktop app's `route_popup.rs` — the summary
 * format, both URL templates and SimBrief's parameter order — so a route copied
 * out of this app and one copied out of that one are the same text, and a
 * SimBrief link built here opens the same dispatch form.
 *
 * Kept free of Android imports so the formats are unit-testable on the JVM. An
 * intent is a delivery mechanism; the string is the part that can be wrong.
 */
object RouteLinks {

    /**
     * `Boeing 737-800: EHAM -> KJFK (2,847 nm)`.
     *
     * The distance is **not** grouped here, matching `{:.0}` in the original.
     * `asFigure` groups a figure for a person reading a card; this string is
     * mostly pasted into somewhere else, where a thousands separator is a
     * character another parser has to survive.
     */
    fun summary(aircraft: AircraftSpec, departure: String, destination: String, distanceNm: Int): String =
        "${aircraft.displayName}: $departure -> $destination ($distanceNm nm)"

    /**
     * SkyVector's flight-plan query: `?fpl=EHAM KJFK`.
     *
     * **A deliberate divergence.** The desktop interpolates the two codes into
     * the template with the space left raw, which a desktop browser repairs on
     * the way out. An `ACTION_VIEW` `Uri` is handed to another application
     * verbatim, so the space is encoded here.
     */
    fun skyVector(departure: String, destination: String): String =
        "https://skyvector.com/?fpl=" + encode("$departure $destination")

    /**
     * SimBrief Dispatch, pre-filled — `construct_simbrief_url`.
     *
     * The estimated time is passed as the hours and minutes SimBrief asks for
     * rather than as a duration, which is why [FlightTime] is taken whole rather
     * than as a single figure.
     */
    fun simBrief(aircraft: AircraftSpec, departure: String, destination: String, time: FlightTime): String =
        "https://dispatch.simbrief.com/options/custom" +
            "?type=${encode(aircraft.icaoCode)}" +
            "&orig=${encode(departure)}" +
            "&dest=${encode(destination)}" +
            "&steh=${time.hours}" +
            "&stem=${time.minutes}"

    /**
     * Google Maps at an airport's reference point.
     *
     * Fixed decimals rather than `%s`. `Double.toString` switches to scientific
     * notation below 1e-3, so an airport within about 100 m of the equator or of
     * the prime meridian would produce `query=4.0E-4,32.1`, which Maps does not
     * resolve. Six decimals is roughly 0.1 m, well past what a reference point is
     * published to, and it makes the linked coordinate and the copyable one agree
     * instead of one being rounded and the other full `Double` precision.
     */
    fun googleMaps(latitude: Double, longitude: Double): String =
        "https://www.google.com/maps/search/?api=1&query=" +
            String.format(Locale.ROOT, "%.6f,%.6f", latitude, longitude)

    /**
     * Whether SimBrief can take this route at all.
     *
     * SimBrief resolves airports by real ICAO code, and roughly a third of the
     * dataset is identified by an OurAirports ident instead — `hasIcaoCode`
     * records which. Offering the link for those would open a dispatch form that
     * rejects the route, so the action is disabled with the reason stated. The
     * desktop app cannot make this distinction and always offers it.
     */
    fun simBriefAccepts(departure: Airport, destination: Airport): Boolean =
        departure.hasIcaoCode && destination.hasIcaoCode

    /**
     * Percent-encoding for a query value.
     *
     * [URLEncoder] is form encoding, which writes a space as `+`. That is
     * correct in a form body and wrong in the `fpl` value SkyVector parses, so
     * the one substitution is made explicitly.
     */
    private fun encode(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8).replace("+", "%20")
}
