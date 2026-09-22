package com.github.daanbouwman.flightplanner.launch

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import androidx.test.core.app.ApplicationProvider
import com.github.daanbouwman.flightplanner.R
import com.github.daanbouwman.flightplanner.handoff.WatchRoute
import com.github.daanbouwman.flightplanner.launch.LaunchIntents.putLaunchRequest
import com.github.daanbouwman.flightplanner.navigation.Destination
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser

/**
 * The Intent contract between the two widgets, the shortcuts and `MainActivity`.
 *
 * Robolectric rather than a pure function, because the property that matters is
 * the round trip through a real `Intent` and its extras — a hand-rolled bundle
 * reader would need its own adapter, itself untested. Robolectric is a JUnit 4
 * runner reaching the JUnit 5 platform through the vintage engine, as
 * `LogbookOverlayTest` explains.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LaunchIntentsTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private val route = Destination.RouteDetail(
        departureIcao = "EHAM",
        destinationIcao = "KJFK",
        aircraftId = 7,
        distanceNm = 3_162,
        alreadyFlown = true,
    )

    private val watchRoute = WatchRoute(
        departureIcao = "EHAM",
        destinationIcao = "KJFK",
        distanceNm = 3_162,
        aircraftTypeCode = "B738",
        aircraftName = "Boeing 737-800",
    )

    @Test
    fun `every request survives the round trip through an Intent`() {
        val requests = listOf(
            LaunchRequest.OpenRoute(route),
            LaunchRequest.GenerateRoutes,
            LaunchRequest.LogFlight,
            LaunchRequest.LastRoute,
            LaunchRequest.OpenAircraft(7),
            LaunchRequest.OpenAircraft(null),
            LaunchRequest.OpenWatchRoute(watchRoute),
        )
        requests.map { LaunchIntents.parse(Intent().putLaunchRequest(it)) } shouldBe requests
    }

    /**
     * The watch's front door. Unlike every other request this one arrives as a
     * URI rather than as extras, because it crosses from another device through
     * the Wear companion app — so the Intent is built by hand here, the way the
     * companion app builds it, rather than only through `putLaunchRequest`.
     */
    @Test
    fun `a watch link arrives as a VIEW intent`() {
        val fromTheWatch = Intent(Intent.ACTION_VIEW)
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .setData("flightplanner://route/EHAM/KJFK?nm=3162&ac=B738&name=Boeing+737-800".toUri())

        LaunchIntents.parse(fromTheWatch) shouldBe LaunchRequest.OpenWatchRoute(watchRoute)
    }

    /**
     * The intent filter is narrow, but a filter is not a promise about what an
     * arriving Intent actually holds: `VIEW` with no data, or with someone
     * else's link, has to be "do nothing" rather than a crash on the way in.
     */
    @Test
    fun `a VIEW intent that is not ours is nothing`() {
        LaunchIntents.parse(Intent(Intent.ACTION_VIEW)).shouldBeNull()
        LaunchIntents.parse(Intent(Intent.ACTION_VIEW).setData("https://example.com/route".toUri())).shouldBeNull()
        LaunchIntents.parse(Intent(Intent.ACTION_VIEW).setData("flightplanner://aircraft/7".toUri())).shouldBeNull()
    }

    @Test
    fun `an open-route request keeps every field, including the flown flag`() {
        val parsed = LaunchIntents.parse(Intent().putLaunchRequest(LaunchRequest.OpenRoute(route)))
        parsed shouldBe LaunchRequest.OpenRoute(route)
    }

    /**
     * The aircraft widget's tap, and the one request whose field is optional:
     * absent means the fleet list, so it must not come back as `0` — the id of
     * a real airframe — and `0` must not come back as absent.
     */
    @Test
    fun `an open-aircraft request tells a named airframe from none`() {
        val named = LaunchIntents.parse(Intent().putLaunchRequest(LaunchRequest.OpenAircraft(7)))
        named shouldBe LaunchRequest.OpenAircraft(7)

        val bare = LaunchIntents.parse(Intent().putLaunchRequest(LaunchRequest.OpenAircraft(null)))
        bare shouldBe LaunchRequest.OpenAircraft(null)

        val zero = LaunchIntents.parse(Intent().putLaunchRequest(LaunchRequest.OpenAircraft(0)))
        zero shouldBe LaunchRequest.OpenAircraft(0)
    }

    @Test
    fun `the launcher's own intent, an unknown action and a null intent are nothing`() {
        LaunchIntents.parse(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)).shouldBeNull()
        LaunchIntents.parse(Intent("com.example.SOMETHING_ELSE")).shouldBeNull()
        LaunchIntents.parse(null).shouldBeNull()
    }

    @Test
    fun `an open-route intent missing a field is nothing rather than a crash`() {
        val noAircraft = Intent(LaunchIntents.ACTION_OPEN_ROUTE)
            .putExtra(LaunchIntents.EXTRA_DEPARTURE_ICAO, "EHAM")
            .putExtra(LaunchIntents.EXTRA_DESTINATION_ICAO, "KJFK")
        LaunchIntents.parse(noAircraft).shouldBeNull()

        val blankDestination = Intent(LaunchIntents.ACTION_OPEN_ROUTE)
            .putExtra(LaunchIntents.EXTRA_DEPARTURE_ICAO, "EHAM")
            .putExtra(LaunchIntents.EXTRA_DESTINATION_ICAO, "")
            .putExtra(LaunchIntents.EXTRA_AIRCRAFT_ID, 7)
        LaunchIntents.parse(blankDestination).shouldBeNull()
    }

    /**
     * The shortcut XML is hand-written against the same action strings. This is
     * what ties the two together: every `<intent>` in it must parse to a real
     * request, and the three shortcuts must be the three the plan names.
     */
    @Test
    fun `every shortcut in shortcuts xml parses to a request`() {
        val actions = mutableListOf<String>()
        context.resources.getXml(R.xml.shortcuts).use { parser ->
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && parser.name == "intent") {
                    actions += parser.getAttributeValue(ANDROID_NS, "action")
                }
                event = parser.next()
            }
        }
        actions.map { LaunchIntents.parse(Intent(it)) } shouldContainExactly listOf(
            LaunchRequest.GenerateRoutes,
            LaunchRequest.LogFlight,
            LaunchRequest.LastRoute,
        )
    }

    private companion object {
        const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    }
}
