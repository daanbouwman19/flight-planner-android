package com.github.daanbouwman.flightplanner.widget

import androidx.compose.ui.unit.DpSize
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.testing.unit.GlanceAppWidgetUnitTest
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasContentDescription
import androidx.glance.testing.unit.hasText
import androidx.test.core.app.ApplicationProvider
import com.github.daanbouwman.flightplanner.settings.AppSettings
import io.kotest.matchers.nulls.shouldNotBeNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The aircraft card's face, composed through Glance's own unit-test host —
 * [ChallengeContentTest]'s arrangement, for the same reasons. Robolectric
 * supplies the `Context` the strings are read from and the palette is the brand
 * one (dynamic colour off), so the test does not depend on a wallpaper the JVM
 * has not got.
 *
 * What is pinned here is what each of the four layouts *says*: which figures
 * it carries, whether the range is labelled, and that the flown status is
 * stated on every one of them — in words on the wide cards, and on the compact
 * ones as a described mark rather than as colour alone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AircraftContentTest {

    private val ready = AircraftWidget.PREVIEW_STATE
    private val palette = widgetPalette(AppSettings(dynamicColour = false), ApplicationProvider.getApplicationContext())
    private val colors = palette.colors.shouldNotBeNull()

    private fun GlanceAppWidgetUnitTest.compose(size: DpSize, state: AircraftState = ready) {
        setContext(ApplicationProvider.getApplicationContext())
        setAppWidgetSize(size)
        provideComposable { GlanceTheme(colors = colors) { AircraftContent(state, palette = palette) } }
    }

    @Test
    fun `wide and tall, the card shows the airframe, the date, the type code, both labelled figures and the status in words`() =
        runGlanceAppWidgetUnitTest {
            compose(AircraftWidget.WIDE_TALL)

            onNode(hasText("Boeing 787-9")).assertExists()
            onNode(hasText("16 Sep")).assertExists()
            onNode(hasText("B789")).assertExists()
            onNode(hasText("RNG")).assertExists()
            onNode(hasText("7,355 NM")).assertExists()
            onNode(hasText("RWY")).assertExists()
            onNode(hasText("9,800 ft")).assertExists()
            onNode(hasText("FLOWN")).assertExists()
        }

    @Test
    fun `wide and short, the same facts on two lines`() = runGlanceAppWidgetUnitTest {
        compose(AircraftWidget.WIDE)

        onNode(hasText("Boeing 787-9")).assertExists()
        onNode(hasText("16 Sep")).assertExists()
        onNode(hasText("B789")).assertExists()
        onNode(hasText("RNG")).assertExists()
        onNode(hasText("7,355 NM")).assertExists()
        onNode(hasText("RWY")).assertExists()
        onNode(hasText("9,800 ft")).assertExists()
        onNode(hasText("FLOWN")).assertExists()
    }

    @Test
    fun `wide, an airframe never flown says so instead`() = runGlanceAppWidgetUnitTest {
        compose(AircraftWidget.WIDE, ready.copy(flown = false))

        onNode(hasText("NOT FLOWN")).assertExists()
    }

    @Test
    fun `compact and tall, the runway figure goes and the labelled range, code and date stay`() =
        runGlanceAppWidgetUnitTest {
            compose(AircraftWidget.COMPACT_TALL)

            onNode(hasText("B789")).assertExists()
            onNode(hasText("16 Sep")).assertExists()
            onNode(hasText("RNG")).assertExists()
            onNode(hasText("7,355 NM")).assertExists()
            onNode(hasText("RWY")).assertDoesNotExist()
            onNode(hasText("9,800 ft")).assertDoesNotExist()
        }

    /**
     * Beside the type code there is room for the range or its label, not both;
     * the unit says which figure it is.
     */
    @Test
    fun `compact and short, the range keeps its unit and loses its label`() = runGlanceAppWidgetUnitTest {
        compose(AircraftWidget.COMPACT)

        onNode(hasText("B789")).assertExists()
        onNode(hasText("7,355 NM")).assertExists()
        onNode(hasText("RNG")).assertDoesNotExist()
        onNode(hasText("RWY")).assertDoesNotExist()
    }

    /**
     * The compact cards state the flown status as a dot, and a dot that carried
     * the status in colour alone would not state it at all to a screen reader.
     */
    @Test
    fun `compact and tall, the status dot is described rather than left to colour`() = runGlanceAppWidgetUnitTest {
        compose(AircraftWidget.COMPACT_TALL)

        onNode(hasContentDescription("Flown")).assertExists()
        onNode(hasText("FLOWN")).assertDoesNotExist()
    }

    @Test
    fun `compact and short, the dot moves to the top line and is still described`() = runGlanceAppWidgetUnitTest {
        compose(AircraftWidget.COMPACT, ready.copy(flown = false))

        onNode(hasContentDescription("Not flown")).assertExists()
        onNode(hasText("NOT FLOWN")).assertDoesNotExist()
    }

    @Test
    fun `an airframe with no takeoff distance shows no runway figure even when wide`() =
        runGlanceAppWidgetUnitTest {
            compose(AircraftWidget.WIDE, ready.copy(runwayText = null))

            onNode(hasText("RNG")).assertExists()
            onNode(hasText("RWY")).assertDoesNotExist()
        }

    @Test
    fun `an empty fleet says so in words`() = runGlanceAppWidgetUnitTest {
        compose(AircraftWidget.WIDE, AircraftState.FleetEmpty)
        onNode(hasText("Add an aircraft to your fleet and one appears here each day.")).assertExists()
    }
}
