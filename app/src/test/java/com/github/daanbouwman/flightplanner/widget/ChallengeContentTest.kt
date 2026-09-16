package com.github.daanbouwman.flightplanner.widget

import androidx.glance.GlanceTheme
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasText
import androidx.test.core.app.ApplicationProvider
import com.github.daanbouwman.flightplanner.settings.AppSettings
import io.kotest.matchers.nulls.shouldNotBeNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The widget's face, composed through Glance's own unit-test host. Robolectric
 * supplies the `Context` the strings are read from; the palette is the brand
 * one (dynamic colour off) so the test does not depend on a wallpaper the JVM
 * has not got. The map is passed as null here — its rendering is
 * [ChallengeMapTest]'s job — so this pins the words alone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ChallengeContentTest {

    private val ready = ChallengeWidget.PREVIEW_STATE
    private val palette = widgetPalette(AppSettings(dynamicColour = false), ApplicationProvider.getApplicationContext())
    private val colors = palette.colors.shouldNotBeNull()

    @Test
    fun `wide, the card shows the airframe, the date, both codes and both figures`() = runGlanceAppWidgetUnitTest {
        setContext(ApplicationProvider.getApplicationContext())
        setAppWidgetSize(ChallengeWidget.WIDE)
        provideComposable { GlanceTheme(colors = colors) { ChallengeContent(ready, map = null, palette = palette) } }

        onNode(hasText("Boeing 787-9")).assertExists()
        onNode(hasText("Wed 16 Sep")).assertExists()
        onNode(hasText("EHAM")).assertExists()
        onNode(hasText("KJFK")).assertExists()
        onNode(hasText("3,162 NM")).assertExists()
        onNode(hasText("6:33")).assertExists()
    }

    @Test
    fun `compact, the airframe line goes and the codes and date stay`() = runGlanceAppWidgetUnitTest {
        setContext(ApplicationProvider.getApplicationContext())
        setAppWidgetSize(ChallengeWidget.COMPACT)
        provideComposable { GlanceTheme(colors = colors) { ChallengeContent(ready, map = null, palette = palette) } }

        onNode(hasText("EHAM")).assertExists()
        onNode(hasText("KJFK")).assertExists()
        onNode(hasText("Wed 16 Sep")).assertExists()
        onNode(hasText("Boeing 787-9")).assertDoesNotExist()
    }

    @Test
    fun `an empty fleet says so in words`() = runGlanceAppWidgetUnitTest {
        setContext(ApplicationProvider.getApplicationContext())
        setAppWidgetSize(ChallengeWidget.WIDE)
        provideComposable {
            GlanceTheme(colors = colors) { ChallengeContent(ChallengeState.FleetEmpty, map = null, palette = palette) }
        }
        onNode(hasText("Add an aircraft to your fleet and a challenge appears here.")).assertExists()
    }
}
