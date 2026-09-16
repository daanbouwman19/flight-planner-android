package com.github.daanbouwman.flightplanner.launch

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.github.daanbouwman.flightplanner.R
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
 * The Intent contract between the widget, the shortcuts and `MainActivity`.
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

    @Test
    fun `every request survives the round trip through an Intent`() {
        val requests = listOf(
            LaunchRequest.OpenRoute(route),
            LaunchRequest.GenerateRoutes,
            LaunchRequest.LogFlight,
            LaunchRequest.LastRoute,
        )
        requests.map { LaunchIntents.parse(Intent().putLaunchRequest(it)) } shouldBe requests
    }

    @Test
    fun `an open-route request keeps every field, including the flown flag`() {
        val parsed = LaunchIntents.parse(Intent().putLaunchRequest(LaunchRequest.OpenRoute(route)))
        parsed shouldBe LaunchRequest.OpenRoute(route)
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
