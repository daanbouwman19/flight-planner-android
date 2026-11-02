package com.github.daanbouwman.flightplanner.mapscene

import androidx.compose.ui.unit.Density
import com.github.daanbouwman.flightplanner.aircraftart.AircraftShape
import com.github.daanbouwman.flightplanner.routing.GeoArc
import com.github.daanbouwman.flightplanner.routing.MapFrame
import com.github.daanbouwman.flightplanner.routing.RouteArc
import com.github.daanbouwman.flightplanner.routing.WorldOutline
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlin.test.Test

/**
 * What a [RouteMapStyle] changes about a scene, and that the default changes
 * nothing: the phone's card is the default, so a caller that names no style
 * must get exactly the map it got before the style existed.
 *
 * These build real scenes. On the JVM the `Path`s inside them are the Android
 * stub's no-op paths, so this pins the decisions — which marks exist — and
 * leaves the pixels to the phone's Roborazzi goldens.
 */
class RouteMapStyleTest {

    /** A route card's canvas at 3× density: 328 × 180 dp. */
    private val width = 984f
    private val height = 540f
    private val density = Density(3f)

    private val dots = RouteMapStyle(endpoints = RouteMapEndpoints.Dots(radiusDp = 3f), oceanGraticule = false)

    // Schiphol to Rotterdam, 30 NM: under MinArrowChordDp on a card.
    private val hop = RouteArc.sampleGeographic(52.31, 4.76, 51.96, 4.44, samples = RouteArc.CARD_SAMPLES)

    // Schiphol to London, about 200 NM: room for a head or the plane.
    private val leg = RouteArc.sampleGeographic(52.31, 4.76, 51.48, -0.46, samples = RouteArc.CARD_SAMPLES)

    // An A320-sized box. The path is never drawn here, only placed.
    private val aircraft = AircraftShape(
        id = "test",
        label = "Test",
        spanCm = 3579,
        lengthCm = 3773,
        provenance = AircraftShape.Provenance.Drawn,
        pathData = "M0 0 L3579 0 L3579 3773 Z",
    )

    private fun scene(arc: GeoArc, style: RouteMapStyle = RouteMapStyle.Card, plane: AircraftShape? = null) =
        routeMapScene(arc, WorldOutline.Empty, width, height, density, aircraft = plane, style = style).shouldNotBeNull()

    @Test
    fun `the default style is the phone's card`() {
        val card = RouteMapStyle.Card
        card.paddingFraction shouldBe MapFrame.PADDING_FRACTION
        card.outlineMargin shouldBe OutlineMargin
        card.endpoints shouldBe RouteMapEndpoints.Chart
        card.oceanGraticule shouldBe true
    }

    @Test
    fun `the chart style collapses a short hop to one ring and heads a longer leg`() {
        val short = scene(hop)
        short.shortHop shouldBe true
        short.arrow.shouldBeNull()

        val long = scene(leg)
        long.shortHop shouldBe false
        long.arrow.shouldNotBeNull()
        long.endpointRadius shouldBe EndpointRadiusDp * 3f
    }

    @Test
    fun `dots never draw the short-hop ring or an arrowhead`() {
        scene(hop, dots).shortHop shouldBe false
        scene(hop, dots).arrow.shouldBeNull()
        scene(leg, dots).arrow.shouldBeNull()
        scene(leg, dots).endpointRadius shouldBe 3f * 3f
    }

    @Test
    fun `the aircraft is placed by the same rule whatever marks the ends`() {
        val chart = scene(leg, plane = aircraft).plane.shouldNotBeNull()
        scene(leg, dots, plane = aircraft).plane shouldBe chart

        scene(hop, plane = aircraft).plane.shouldBeNull()
        scene(hop, dots, plane = aircraft).plane.shouldBeNull()
    }

    @Test
    fun `only a style that asks for it draws the ocean graticule`() {
        // The empty outline has no coast anywhere, so every window is ocean.
        scene(leg).graticulePath.shouldNotBeNull()
        scene(leg, dots).graticulePath.shouldBeNull()
    }
}
