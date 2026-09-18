package com.github.daanbouwman.flightplanner.widget

import androidx.glance.GlanceTheme
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
 * What is pinned here is what each width *says*: the wide card states the flown
 * status in words and carries both figures, the compact one drops the second
 * figure and states the same status as a described mark rather than as colour
 * alone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AircraftContentTest {

    private val ready = AircraftWidget.PREVIEW_STATE
    private val palette = widgetPalette(AppSettings(dynamicColour = false), ApplicationProvider.getApplicationContext())
    private val colors = palette.colors.shouldNotBeNull()

    @Test
    fun `wide, the card shows the airframe, the date, the type code and both figures`() =
        runGlanceAppWidgetUnitTest {
            setContext(ApplicationProvider.getApplicationContext())
            setAppWidgetSize(AircraftWidget.WIDE)
            provideComposable { GlanceTheme(colors = colors) { AircraftContent(ready, palette = palette) } }

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
        setContext(ApplicationProvider.getApplicationContext())
        setAppWidgetSize(AircraftWidget.WIDE)
        provideComposable {
            GlanceTheme(colors = colors) { AircraftContent(ready.copy(flown = false), palette = palette) }
        }

        onNode(hasText("NOT FLOWN")).assertExists()
    }

    @Test
    fun `compact, the runway figure goes and the range, code and date stay`() = runGlanceAppWidgetUnitTest {
        setContext(ApplicationProvider.getApplicationContext())
        setAppWidgetSize(AircraftWidget.COMPACT)
        provideComposable { GlanceTheme(colors = colors) { AircraftContent(ready, palette = palette) } }

        onNode(hasText("B789")).assertExists()
        onNode(hasText("16 Sep")).assertExists()
        onNode(hasText("7,355 NM")).assertExists()
        onNode(hasText("RWY")).assertDoesNotExist()
        onNode(hasText("9,800 ft")).assertDoesNotExist()
    }

    /**
     * The compact card states the flown status as a dot, and a dot that carried
     * the status in colour alone would not state it at all to a screen reader.
     */
    @Test
    fun `compact, the status dot is described rather than left to colour`() = runGlanceAppWidgetUnitTest {
        setContext(ApplicationProvider.getApplicationContext())
        setAppWidgetSize(AircraftWidget.COMPACT)
        provideComposable { GlanceTheme(colors = colors) { AircraftContent(ready, palette = palette) } }

        onNode(hasContentDescription("Flown")).assertExists()
    }

    @Test
    fun `an airframe with no takeoff distance shows no runway figure even when wide`() =
        runGlanceAppWidgetUnitTest {
            setContext(ApplicationProvider.getApplicationContext())
            setAppWidgetSize(AircraftWidget.WIDE)
            provideComposable {
                GlanceTheme(colors = colors) { AircraftContent(ready.copy(runwayText = null), palette = palette) }
            }

            onNode(hasText("RNG")).assertExists()
            onNode(hasText("RWY")).assertDoesNotExist()
        }

    @Test
    fun `an empty fleet says so in words`() = runGlanceAppWidgetUnitTest {
        setContext(ApplicationProvider.getApplicationContext())
        setAppWidgetSize(AircraftWidget.WIDE)
        provideComposable {
            GlanceTheme(colors = colors) { AircraftContent(AircraftState.FleetEmpty, palette = palette) }
        }
        onNode(hasText("Add an aircraft to your fleet and one appears here each day.")).assertExists()
    }
}
