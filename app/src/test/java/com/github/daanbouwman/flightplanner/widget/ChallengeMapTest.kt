package com.github.daanbouwman.flightplanner.widget

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.github.daanbouwman.flightplanner.core.designsystem.components.WorldMapCoastAlpha
import com.github.daanbouwman.flightplanner.core.designsystem.components.WorldMapLandAlpha
import com.github.daanbouwman.flightplanner.routing.WorldOutline
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.roundToInt

/**
 * The map behind the widget is three alpha masks, and their whole contract is
 * "white pixels whose alpha is the ink's weight" — Glance supplies the colour.
 * Robolectric's native graphics draw them for real, so this pins that the
 * route layer actually contains a route, that land never exceeds the coast's
 * 16 %, and that every mask is white where it is anything at all.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChallengeMapTest {

    private val size = DpSize(250.dp, 100.dp)
    private val density = Density(2f)

    /** One invented island under the Atlantic, so the land layer has something to fill. */
    private val outline = WorldOutline(
        lon = floatArrayOf(-40f, -10f, -10f, -40f, -40f),
        lat = floatArrayOf(40f, 40f, 60f, 60f, 40f),
        ringStart = intArrayOf(0, 5),
    )

    @Test
    fun `a ready state renders three masks at the card's pixel size`() {
        val map = renderChallengeMap(ChallengeWidget.PREVIEW_STATE, outline, size, density).shouldNotBeNull()
        for (mask in listOf(map.land, map.casing, map.route)) {
            mask.width shouldBe 500
            mask.height shouldBe 200
        }
    }

    @Test
    fun `the route layer has ink and the land layer never exceeds the coast's weight`() {
        val map = renderChallengeMap(ChallengeWidget.PREVIEW_STATE, outline, size, density).shouldNotBeNull()

        map.route.inkedPixels() shouldBeGreaterThan 100
        map.casing.inkedPixels() shouldBeGreaterThan map.route.inkedPixels()
        map.land.inkedPixels() shouldBeGreaterThan 100
        // The coast is stroked over the fill, so where the two meet the alpha
        // composites: 1 − (1 − 0.08)(1 − 0.16). Nothing in the land layer may be
        // heavier than that — the map is texture, and this is the ceiling that
        // keeps text over it legible without a scrim.
        val coastOverLand = 1f - (1f - WorldMapLandAlpha) * (1f - WorldMapCoastAlpha)
        map.land.maxAlpha() shouldBeLessThanOrEqual (coastOverLand * 255).roundToInt() + 1
    }

    @Test
    fun `every mask is white wherever it has alpha`() {
        val map = renderChallengeMap(ChallengeWidget.PREVIEW_STATE, outline, size, density).shouldNotBeNull()
        for (mask in listOf(map.land, map.casing, map.route)) {
            mask.isWhiteMask() shouldBe true
        }
    }

    @Test
    fun `a state with no route has no map`() {
        renderChallengeMap(ChallengeState.FleetEmpty, outline, size, density).shouldBeNull()
        renderChallengeMap(ChallengeState.Unavailable, outline, size, density).shouldBeNull()
    }

    private fun Bitmap.inkedPixels(): Int {
        var count = 0
        forEachPixel { if (Color.alpha(it) > 0) count++ }
        return count
    }

    private fun Bitmap.maxAlpha(): Int {
        var max = 0
        forEachPixel { max = maxOf(max, Color.alpha(it)) }
        return max
    }

    /** Premultiplied storage rounds channels down with alpha, so "white" is "no channel below its alpha − 2". */
    private fun Bitmap.isWhiteMask(): Boolean {
        var white = true
        forEachPixel { pixel ->
            val a = Color.alpha(pixel)
            if (a > 0) {
                val floor = a - 2
                if (Color.red(pixel) < floor || Color.green(pixel) < floor || Color.blue(pixel) < floor) white = false
            }
        }
        return white
    }

    private inline fun Bitmap.forEachPixel(block: (Int) -> Unit) {
        val row = IntArray(width)
        for (y in 0 until height) {
            getPixels(row, 0, width, 0, y, width, 1)
            row.forEach(block)
        }
    }
}
