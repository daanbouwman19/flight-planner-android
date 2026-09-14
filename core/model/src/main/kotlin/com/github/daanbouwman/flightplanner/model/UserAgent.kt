package com.github.daanbouwman.flightplanner.model

/**
 * How this app identifies itself to every service it talks to.
 *
 * One string for the weather clients in `:core:network` and the tile client in
 * `:feature:globe`, which are deliberately **separate `OkHttpClient`s** — the
 * tile client owns a 96 MB disk cache that METAR traffic must not share, and the
 * two have different timeouts for good reasons recorded on each. They used to
 * carry two copies of this literal, kept in step by a comment; a provider
 * reading its logs would have seen them drift the first time one was edited.
 *
 * Here rather than in either of those modules because this is the one module
 * both already depend on. A repo URL, not the user's own contact details, which
 * must never reach a third-party service in a header sent on their behalf —
 * NOAA has no SLA and AVWX a per-key one (docs/PLAN.md risk #10), and both ask
 * to be told who is calling.
 */
object UserAgent {
    /** The HTTP header this goes in. */
    const val HEADER: String = "User-Agent"

    /** The value, as it goes on the wire. */
    const val VALUE: String =
        "FlightPlannerAndroid/1.0 (+https://github.com/daanbouwman19/flight-planner-android)"
}
