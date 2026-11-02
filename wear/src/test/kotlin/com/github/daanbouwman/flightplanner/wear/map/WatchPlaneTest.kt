package com.github.daanbouwman.flightplanner.wear.map

import com.github.daanbouwman.flightplanner.aircraftart.AircraftShape
import com.github.daanbouwman.flightplanner.routing.RouteArc
import com.github.daanbouwman.flightplanner.routing.WorldOutline
import com.github.daanbouwman.flightplanner.wear.route.WatchRouteCard
import io.kotest.matchers.floats.shouldBeGreaterThan
import io.kotest.matchers.floats.shouldBeLessThan
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlin.math.abs
import kotlin.test.Test

/**
 * Where the watch face puts the airframe on a leg, through the face's own
 * scene: the reference watch's 480 px face at its density, [WatchMapStyle]'s
 * frame, and the real [watchRouteScene] the face draws.
 *
 * This used to pin `planeOnLeg`, the watch's hand-written copy of the phone's
 * rule. The copy is gone — the face places the plane with `:core:mapscene`'s
 * rule now — so the rule's geometry (nose clockwise from up, box centred on the
 * middle sample, longer side the plane length) is pinned once, in
 * `:core:mapscene`'s `RouteMapGeometryTest`, and what is left to pin here is the
 * rule *at the watch's parameters*: on this face, at this density, through this
 * frame, which legs carry the plane and how large it is.
 */
class WatchPlaneTest {

    /** The reference watch: 480 × 480 at 340 dpi, so 2.125 px per dp. */
    private val face = WatchFace(extentPx = 480, density = 340f / 160f)

    /** The phone's 24 dp plane, which the watch shares, on this face. */
    private val planePx = 24f * face.density

    // An A320-sized outline in centimetres: a little longer than it is wide.
    private val a320 = AircraftShape(
        id = "test-a320",
        label = "Test",
        spanCm = 3579,
        lengthCm = 3773,
        provenance = AircraftShape.Provenance.Drawn,
        pathData = "M0 0 L3579 0 L3579 3773 L0 3773 Z",
    )

    private fun card(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double, shape: AircraftShape? = a320) =
        WatchRouteCard(
            departureIcao = "AAAA",
            destinationIcao = "BBBB",
            distanceNm = 0,
            distanceText = "",
            eteText = "",
            aircraftName = "Test",
            aircraftTypeCode = "A320",
            arc = RouteArc.sampleGeographic(fromLat, fromLon, toLat, toLon, samples = RouteArc.CARD_SAMPLES),
            shape = shape,
        )

    private fun planeOn(card: WatchRouteCard) = watchRouteScene(card, WorldOutline.Empty, face).shouldNotBeNull().plane

    @Test
    fun `a long leg on the face carries the plane, nose along the track, the plane length long`() {
        // London to Warsaw, about 790 NM, flown roughly east.
        val placed = planeOn(card(51.47, -0.45, 52.17, 20.97)).shouldNotBeNull()

        placed.degrees shouldBeGreaterThan 70f
        placed.degrees shouldBeLessThan 110f
        abs(a320.lengthCm * placed.scale - planePx) shouldBeLessThan 1e-3f
        // Centred on the face's middle sample, which is on the face.
        placed.x shouldBeGreaterThan 0f
        placed.x shouldBeLessThan face.extentPx.toFloat()
    }

    @Test
    fun `a short hop on the face carries none`() {
        // Schiphol to Rotterdam, 30 NM: the ends would sit under the wings.
        planeOn(card(52.31, 4.76, 51.96, 4.44)).shouldBeNull()
    }

    @Test
    fun `the plane appears at one distance and stays for every longer leg`() {
        // Due south from Schiphol in quarter-degree steps, 15 NM to 600 NM. Below
        // 1.6 plane lengths of chord on the face there is no plane; past it,
        // there always is — the face's frame only ever gives a longer leg more
        // room, never less.
        val carries = (1..40).map { step -> planeOn(card(52.31, 4.76, 52.31 - step * 0.25, 4.76)) != null }

        carries.first() shouldBe false
        carries.last() shouldBe true
        val firstCarried = carries.indexOf(true)
        carries.drop(firstCarried).all { it } shouldBe true
    }

    @Test
    fun `a leg with no heading or no airframe carries none`() {
        planeOn(card(52.31, 4.76, 52.31, 4.76)).shouldBeNull()
        planeOn(card(51.47, -0.45, 52.17, 20.97, shape = null)).shouldBeNull()
    }
}
