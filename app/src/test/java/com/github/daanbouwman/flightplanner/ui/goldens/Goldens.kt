package com.github.daanbouwman.flightplanner.ui.goldens

import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.core.app.ApplicationProvider
import com.github.daanbouwman.flightplanner.core.designsystem.theme.FlightPlannerTheme
import com.github.daanbouwman.flightplanner.core.designsystem.theme.ThemeChoice
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.roborazziSystemPropertyCompareOutputDirectory
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Screenshot goldens (UI-PLAN §10 H5).
 *
 * One golden is one screen, in one state, under one [GoldenVariant]; the PNGs live
 * under `app/src/test/goldens/<subject>/<state>/<variant>.png` and are compared
 * pixel for pixel by `:app:verifyRoborazziDebug`, which `check` — and so `build`
 * and CI — reaches. `:app:recordRoborazziDebug` rewrites them; look at the diff
 * before committing it. A plain `testDebugUnitTest` runs these tests too but
 * `captureRoboImage` is then a no-op, so the composition is still exercised.
 *
 * ### The matrix
 *
 * One axis at a time, not the cross-product: [GoldenVariant.Base] plus one variant
 * per axis. A layout bug on an axis still shows, review of a golden diff stays
 * possible, and the tree does not carry a thousand PNGs. `dynamic` colour is not
 * an axis — under Robolectric it resolves to a fixed default palette and would
 * pin nothing about the app. Cockpit and Chart are, because each resolves its own
 * scheme rather than a tint of the brand one.
 *
 * ### What is held still
 *
 * - **Motion.** `Settings.Global.ANIMATOR_DURATION_SCALE` is set to 0 before
 *   composition, which is the seam `rememberReduceMotion()` in the design system
 *   reads: shimmer freezes at mid alpha, staggers and count-ups skip to the end,
 *   the windsock stops. The clock is then paused and advanced by a fixed amount,
 *   so a `LaunchedEffect` that waits (the 150 ms skeleton delay) has fired and an
 *   infinite transition that ignores reduce motion sits on the same virtual frame
 *   every run.
 * - **Time.** Fixtures carry literal dates and no observation timestamps; nothing
 *   composed here reads the wall clock.
 * - **The globe.** `GlobeSession.support()` asks the device for GLES 3 and
 *   Robolectric reports none, so every `GlobeSurface` draws its still-map fallback
 *   and Settings' globe line says there is no renderer. That is the "globe is
 *   stubbed" of the plan, by the app's own fallback rather than a test double —
 *   an implicit seam, and this is where it is written down. The renderer itself is
 *   covered by `:feature:globe`'s tests and by looking at a device.
 *
 * ### Font scale and RTL
 *
 * Both come from Robolectric's configuration — `@Config(fontScale = 2f)` and the
 * `ldrtl` qualifier — because that is how a device delivers them, and the harness
 * asserts inside composition that they arrived. A variant that silently rendered
 * at 1.0 or LTR would be a green test of nothing.
 */
enum class GoldenVariant(
    val fileName: String,
    val themeChoice: ThemeChoice,
    val layoutDirection: LayoutDirection = LayoutDirection.Ltr,
    val fontScale: Float = 1f,
) {
    /** Light, LTR, font 1.0, a 360 × 800 dp phone. Every other variant changes one thing. */
    Base("base", ThemeChoice.LIGHT),
    Dark("dark", ThemeChoice.DARK),
    Cockpit("cockpit", ThemeChoice.COCKPIT),
    Chart("chart", ThemeChoice.CHART),
    Rtl("rtl", ThemeChoice.LIGHT, layoutDirection = LayoutDirection.Rtl),
    FontScale2("font-2_0", ThemeChoice.LIGHT, fontScale = 2f),
    /** 700 dp wide: past `MaxContentWidth`, so the content columns stop growing. */
    Medium("medium", ThemeChoice.LIGHT),
    /** Roborazzi's `MediumTablet`, 1280 × 800 dp — the widest frame the app lays out for. */
    Expanded("expanded", ThemeChoice.LIGHT),
}

/**
 * Robolectric qualifier strings, in AAPT's canonical order, at xhdpi so the PNGs
 * are 2 px per dp. There is no font-scale qualifier; that is `@Config.fontScale`.
 */
object GoldenQualifiers {
    const val COMPACT = "w360dp-h800dp-normal-long-notround-any-xhdpi-keyshidden-nonav"
    const val COMPACT_RTL = "ar-rXB-ldrtl-$COMPACT"
    const val MEDIUM = "w700dp-h900dp-large-notlong-notround-any-xhdpi-keyshidden-nonav"
    const val EXPANDED = RobolectricDeviceQualifiers.MediumTablet
}

/**
 * One subject — a screen or a family of components — in its primary state under
 * all eight variants, through the eight inherited tests. Secondary states are
 * captured at [GoldenVariant.Base] only, from a subclass test that calls
 * [captureState].
 *
 * Qualifiers and font scale are method-level `@Config`, and Robolectric merges
 * them with this class's `sdk` and graphics mode. They cannot be parameters: the
 * configuration is fixed before the Activity behind [createComposeRule] exists,
 * which is also why there is no `ParameterizedRobolectricTestRunner` here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class GoldenSuite(private val subject: String, private val primaryState: String) {

    @get:Rule
    val compose = createComposeRule()

    /** The subject at its primary state, minus theme — [captureGolden] supplies that. */
    @Composable
    protected abstract fun Primary()

    @Test
    @Config(qualifiers = GoldenQualifiers.COMPACT)
    fun base() = capture(GoldenVariant.Base)

    @Test
    @Config(qualifiers = GoldenQualifiers.COMPACT)
    fun dark() = capture(GoldenVariant.Dark)

    @Test
    @Config(qualifiers = GoldenQualifiers.COMPACT)
    fun cockpit() = capture(GoldenVariant.Cockpit)

    @Test
    @Config(qualifiers = GoldenQualifiers.COMPACT)
    fun chart() = capture(GoldenVariant.Chart)

    @Test
    @Config(qualifiers = GoldenQualifiers.COMPACT_RTL)
    fun rtl() = capture(GoldenVariant.Rtl)

    @Test
    @Config(qualifiers = GoldenQualifiers.COMPACT, fontScale = 2f)
    fun fontScale2() = capture(GoldenVariant.FontScale2)

    @Test
    @Config(qualifiers = GoldenQualifiers.MEDIUM)
    fun medium() = capture(GoldenVariant.Medium)

    @Test
    @Config(qualifiers = GoldenQualifiers.EXPANDED)
    fun expanded() = capture(GoldenVariant.Expanded)

    private fun capture(variant: GoldenVariant) =
        captureGolden(compose, "$subject/$primaryState/${variant.fileName}.png", variant) { Primary() }

    /**
     * A secondary state at [GoldenVariant.Base]. The calling test must carry
     * `@Config(qualifiers = GoldenQualifiers.COMPACT)` itself; the harness asserts
     * the frame, so forgetting it fails rather than recording a 320 dp golden.
     */
    protected fun captureState(state: String, content: @Composable () -> Unit) =
        captureGolden(compose, "$subject/$state/base.png", GoldenVariant.Base, content)
}

/** How long the paused clock is advanced before the capture; see [GoldenSuite]. */
private const val SettleMillis = 1_000L

/**
 * Composes [content] under [variant]'s theme, holds motion and time still, and
 * captures the root node to [relativePath] under `roborazzi.outputDir`.
 */
fun captureGolden(
    compose: ComposeContentTestRule,
    relativePath: String,
    variant: GoldenVariant,
    content: @Composable () -> Unit,
) {
    val context = ApplicationProvider.getApplicationContext<Context>()
    Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)

    compose.mainClock.autoAdvance = false
    compose.setContent {
        val density = LocalDensity.current
        check(density.fontScale == variant.fontScale) {
            "font scale ${density.fontScale} reached composition; @Config asked for ${variant.fontScale}"
        }
        val direction = LocalLayoutDirection.current
        check(direction == variant.layoutDirection) {
            "layout direction $direction reached composition; the qualifiers asked for ${variant.layoutDirection}"
        }
        check(density.density == 2f) { "density ${density.density}; the qualifiers ask for xhdpi" }
        FlightPlannerTheme(themeChoice = variant.themeChoice, dynamicColor = false) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                content()
            }
        }
    }
    compose.mainClock.advanceTimeBy(SettleMillis)
    // Compare and actual images are named after the golden's basename, and a
    // hundred goldens here are called base.png; mirroring the golden's directory
    // under the compare directory is what keeps two failures from overwriting
    // each other's diff.
    val compareDirectory = File(roborazziSystemPropertyCompareOutputDirectory(), relativePath).parentFile.path
    compose.onRoot().captureRoboImage(
        filePath = relativePath,
        roborazziOptions = RoborazziOptions(
            compareOptions = RoborazziOptions.CompareOptions(outputDirectoryPath = compareDirectory),
        ),
    )
}
