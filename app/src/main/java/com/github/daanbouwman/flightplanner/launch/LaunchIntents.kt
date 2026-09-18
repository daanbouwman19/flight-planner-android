package com.github.daanbouwman.flightplanner.launch

import android.content.Intent
import com.github.daanbouwman.flightplanner.navigation.Destination

/**
 * The one Intent encoding of a [LaunchRequest], read and written here and
 * nowhere else.
 *
 * The widgets write it with [putLaunchRequest]; `res/xml/shortcuts.xml` writes
 * it by hand with the same action strings; `MainActivity` reads it with [parse].
 * `LaunchIntentsTest` round-trips every request and parses the shortcut XML, so
 * a typo in either place fails a unit test rather than a tap on a device.
 *
 * Actions are namespaced under the application id so that no other app's
 * broadcast or shortcut can collide with them, and the extras are plain
 * primitives because a static shortcut's `<extra>` cannot carry anything else.
 */
object LaunchIntents {

    private const val PREFIX = "com.github.daanbouwman.flightplanner.action."
    const val ACTION_OPEN_ROUTE: String = PREFIX + "OPEN_ROUTE"
    const val ACTION_GENERATE_ROUTES: String = PREFIX + "GENERATE_ROUTES"
    const val ACTION_LOG_FLIGHT: String = PREFIX + "LOG_FLIGHT"
    const val ACTION_LAST_ROUTE: String = PREFIX + "LAST_ROUTE"
    const val ACTION_OPEN_AIRCRAFT: String = PREFIX + "OPEN_AIRCRAFT"

    const val EXTRA_DEPARTURE_ICAO: String = "departure_icao"
    const val EXTRA_DESTINATION_ICAO: String = "destination_icao"
    const val EXTRA_AIRCRAFT_ID: String = "aircraft_id"
    const val EXTRA_DISTANCE_NM: String = "distance_nm"
    const val EXTRA_ALREADY_FLOWN: String = "already_flown"

    /**
     * The request an Intent carries, or null when it carries none.
     *
     * Null for the launcher's own MAIN intent, for an action this object does
     * not know, and for an `OPEN_ROUTE` missing any of its fields — an Intent
     * from outside the process is input, and malformed input is "do nothing",
     * never a crash on the way into the app. `OPEN_AIRCRAFT` is the one action
     * with an optional field rather than required ones; see
     * [LaunchRequest.OpenAircraft].
     */
    fun parse(intent: Intent?): LaunchRequest? {
        when (intent?.action) {
            ACTION_GENERATE_ROUTES -> return LaunchRequest.GenerateRoutes
            ACTION_LOG_FLIGHT -> return LaunchRequest.LogFlight
            ACTION_LAST_ROUTE -> return LaunchRequest.LastRoute
            ACTION_OPEN_AIRCRAFT -> return LaunchRequest.OpenAircraft(
                // Absent is "the fleet list", not "malformed" — see
                // `LaunchRequest.OpenAircraft`. An id that no longer names an
                // airframe is the Fleet detail screen's problem, as it already
                // is for one reached from a restored back stack.
                airframeId = if (intent.hasExtra(EXTRA_AIRCRAFT_ID)) intent.getIntExtra(EXTRA_AIRCRAFT_ID, 0) else null,
            )

            ACTION_OPEN_ROUTE -> Unit
            else -> return null
        }
        val departure = intent.getStringExtra(EXTRA_DEPARTURE_ICAO)?.takeIf { it.isNotBlank() } ?: return null
        val destination = intent.getStringExtra(EXTRA_DESTINATION_ICAO)?.takeIf { it.isNotBlank() } ?: return null
        if (!intent.hasExtra(EXTRA_AIRCRAFT_ID)) return null
        return LaunchRequest.OpenRoute(
            Destination.RouteDetail(
                departureIcao = departure,
                destinationIcao = destination,
                aircraftId = intent.getIntExtra(EXTRA_AIRCRAFT_ID, 0),
                distanceNm = intent.getIntExtra(EXTRA_DISTANCE_NM, 0),
                alreadyFlown = intent.getBooleanExtra(EXTRA_ALREADY_FLOWN, false),
            ),
        )
    }

    /**
     * Writes [request] onto this Intent and returns it.
     *
     * Only the action and extras are set; which component the Intent targets is
     * the caller's decision, so the same encoding serves an explicit Intent
     * from the widget and a `<shortcut>` in XML.
     */
    fun Intent.putLaunchRequest(request: LaunchRequest): Intent = when (request) {
        LaunchRequest.GenerateRoutes -> setAction(ACTION_GENERATE_ROUTES)
        LaunchRequest.LogFlight -> setAction(ACTION_LOG_FLIGHT)
        LaunchRequest.LastRoute -> setAction(ACTION_LAST_ROUTE)
        // The extra is written only when there is an airframe to name, so
        // that "no id" and "id 0" stay distinguishable on the way back out.
        is LaunchRequest.OpenAircraft -> setAction(ACTION_OPEN_AIRCRAFT).also { intent ->
            request.airframeId?.let { id -> intent.putExtra(EXTRA_AIRCRAFT_ID, id) }
        }
        is LaunchRequest.OpenRoute -> setAction(ACTION_OPEN_ROUTE)
            .putExtra(EXTRA_DEPARTURE_ICAO, request.route.departureIcao)
            .putExtra(EXTRA_DESTINATION_ICAO, request.route.destinationIcao)
            .putExtra(EXTRA_AIRCRAFT_ID, request.route.aircraftId)
            .putExtra(EXTRA_DISTANCE_NM, request.route.distanceNm)
            .putExtra(EXTRA_ALREADY_FLOWN, request.route.alreadyFlown)
    }
}
