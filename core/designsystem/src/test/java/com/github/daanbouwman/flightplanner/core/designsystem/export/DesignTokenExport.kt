@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.github.daanbouwman.flightplanner.core.designsystem.export

import androidx.compose.animation.core.SpringSpec
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Typography
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.graphics.shapes.RoundedPolygon
import java.util.Locale
import com.github.daanbouwman.flightplanner.core.designsystem.motion.FlightMotion
import com.github.daanbouwman.flightplanner.core.designsystem.theme.BrandDarkColorScheme
import com.github.daanbouwman.flightplanner.core.designsystem.theme.BrandDarkSkyColors
import com.github.daanbouwman.flightplanner.core.designsystem.theme.BrandLightColorScheme
import com.github.daanbouwman.flightplanner.core.designsystem.theme.BrandLightSkyColors
import com.github.daanbouwman.flightplanner.core.designsystem.theme.ChartColorScheme
import com.github.daanbouwman.flightplanner.core.designsystem.theme.ChartSkyColors
import com.github.daanbouwman.flightplanner.core.designsystem.theme.CockpitColorScheme
import com.github.daanbouwman.flightplanner.core.designsystem.theme.CockpitSkyColors
import com.github.daanbouwman.flightplanner.core.designsystem.theme.DarkFlightRulesColors
import com.github.daanbouwman.flightplanner.core.designsystem.theme.FlightRulesColors
import com.github.daanbouwman.flightplanner.core.designsystem.theme.FlightShapeScale
import com.github.daanbouwman.flightplanner.core.designsystem.theme.FlightTypography
import com.github.daanbouwman.flightplanner.core.designsystem.theme.LightFlightRulesColors

/**
 * Serialises the design system's real values for the React mirror.
 *
 * Every number the mirror renders with comes from here, read off the same objects
 * the app composes with — never transcribed by hand. That is the whole point: a
 * mirror whose tokens are hand-copied is a second source of truth, and it starts
 * drifting the day it is written.
 *
 * See `DesignTokenExportTest`, which fails the build when the committed export no
 * longer matches what these objects say.
 */
internal object DesignTokenExport {

    /**
     * Every role in [ColorScheme], named.
     *
     * Explicit rather than reflective so that a role added by a future Material
     * release is a deliberate addition here rather than a silent one — and so the
     * export does not need `kotlin-reflect` on the unit-test classpath.
     */
    private fun ColorScheme.roles(): List<Pair<String, Color>> = listOf(
        "primary" to primary,
        "onPrimary" to onPrimary,
        "primaryContainer" to primaryContainer,
        "onPrimaryContainer" to onPrimaryContainer,
        "inversePrimary" to inversePrimary,
        "secondary" to secondary,
        "onSecondary" to onSecondary,
        "secondaryContainer" to secondaryContainer,
        "onSecondaryContainer" to onSecondaryContainer,
        "tertiary" to tertiary,
        "onTertiary" to onTertiary,
        "tertiaryContainer" to tertiaryContainer,
        "onTertiaryContainer" to onTertiaryContainer,
        "background" to background,
        "onBackground" to onBackground,
        "surface" to surface,
        "onSurface" to onSurface,
        "surfaceVariant" to surfaceVariant,
        "onSurfaceVariant" to onSurfaceVariant,
        "surfaceTint" to surfaceTint,
        "inverseSurface" to inverseSurface,
        "inverseOnSurface" to inverseOnSurface,
        "error" to error,
        "onError" to onError,
        "errorContainer" to errorContainer,
        "onErrorContainer" to onErrorContainer,
        "outline" to outline,
        "outlineVariant" to outlineVariant,
        "scrim" to scrim,
        "surfaceBright" to surfaceBright,
        "surfaceDim" to surfaceDim,
        "surfaceContainer" to surfaceContainer,
        "surfaceContainerHigh" to surfaceContainerHigh,
        "surfaceContainerHighest" to surfaceContainerHighest,
        "surfaceContainerLow" to surfaceContainerLow,
        "surfaceContainerLowest" to surfaceContainerLowest,
        "primaryFixed" to primaryFixed,
        "primaryFixedDim" to primaryFixedDim,
        "onPrimaryFixed" to onPrimaryFixed,
        "onPrimaryFixedVariant" to onPrimaryFixedVariant,
        "secondaryFixed" to secondaryFixed,
        "secondaryFixedDim" to secondaryFixedDim,
        "onSecondaryFixed" to onSecondaryFixed,
        "onSecondaryFixedVariant" to onSecondaryFixedVariant,
        "tertiaryFixed" to tertiaryFixed,
        "tertiaryFixedDim" to tertiaryFixedDim,
        "onTertiaryFixed" to onTertiaryFixed,
        "onTertiaryFixedVariant" to onTertiaryFixedVariant,
    )

    /** Every slot in [Typography], regular cut and emphasized cut alike. */
    private fun Typography.slots(): List<Pair<String, TextStyle>> = listOf(
        "displayLarge" to displayLarge,
        "displayLargeEmphasized" to displayLargeEmphasized,
        "displayMedium" to displayMedium,
        "displayMediumEmphasized" to displayMediumEmphasized,
        "displaySmall" to displaySmall,
        "displaySmallEmphasized" to displaySmallEmphasized,
        "headlineLarge" to headlineLarge,
        "headlineLargeEmphasized" to headlineLargeEmphasized,
        "headlineMedium" to headlineMedium,
        "headlineMediumEmphasized" to headlineMediumEmphasized,
        "headlineSmall" to headlineSmall,
        "headlineSmallEmphasized" to headlineSmallEmphasized,
        "titleLarge" to titleLarge,
        "titleLargeEmphasized" to titleLargeEmphasized,
        "titleMedium" to titleMedium,
        "titleMediumEmphasized" to titleMediumEmphasized,
        "titleSmall" to titleSmall,
        "titleSmallEmphasized" to titleSmallEmphasized,
        "bodyLarge" to bodyLarge,
        "bodyLargeEmphasized" to bodyLargeEmphasized,
        "bodyMedium" to bodyMedium,
        "bodyMediumEmphasized" to bodyMediumEmphasized,
        "bodySmall" to bodySmall,
        "bodySmallEmphasized" to bodySmallEmphasized,
        "labelLarge" to labelLarge,
        "labelLargeEmphasized" to labelLargeEmphasized,
        "labelMedium" to labelMedium,
        "labelMediumEmphasized" to labelMediumEmphasized,
        "labelSmall" to labelSmall,
        "labelSmallEmphasized" to labelSmallEmphasized,
    )

    private fun FlightRulesColors.pairs(): List<Triple<String, Color, Color>> = listOf(
        Triple("vfr", vfr.container, vfr.onContainer),
        Triple("mvfr", mvfr.container, mvfr.onContainer),
        Triple("ifr", ifr.container, ifr.onContainer),
        Triple("lifr", lifr.container, lifr.onContainer),
        Triple("unknown", unknown.container, unknown.onContainer),
    )

    /**
     * Every number here is formatted in [Locale.ROOT], never the default locale.
     *
     * This is the same rule the app applies to its own figures, for a related
     * reason. Here it is not about how a value reads but about whether it parses
     * at all: under a locale that uses a decimal comma — Dutch, German, French —
     * `"%.4f".format(0.5f)` yields `0,5000`, and an SVG path of `M1,0000,0,5000`
     * is not a path. It also makes the committed export byte-identical on every
     * machine, which is what lets `DesignTokenExportTest` compare it at all.
     */
    private val Fixed: Locale = Locale.ROOT

    /** `#rrggbb`, or `#rrggbbaa` when the colour is not fully opaque. */
    private fun Color.hex(): String {
        val a = (alpha * 255f + 0.5f).toInt()
        val r = (red * 255f + 0.5f).toInt()
        val g = (green * 255f + 0.5f).toInt()
        val b = (blue * 255f + 0.5f).toInt()
        val rgb = String.format(Fixed, "#%02x%02x%02x", r, g, b)
        return if (a == 255) rgb else rgb + String.format(Fixed, "%02x", a)
    }

    /**
     * A [RoundedPolygon] as SVG path data, in the polygon's own normalised space.
     *
     * Exported rather than approximated: these are the actual Expressive shapes,
     * and a hand-drawn nine-lobed cookie is a different cookie.
     */
    private fun RoundedPolygon.svgPath(): String {
        val list = cubics
        if (list.isEmpty()) return ""
        fun f(v: Float): String {
            val s = String.format(Fixed, "%.4f", v)
            return if (s.contains('.')) s.trimEnd('0').trimEnd('.') else s
        }
        val sb = StringBuilder()
        sb.append("M").append(f(list.first().anchor0X)).append(",").append(f(list.first().anchor0Y))
        for (c in list) {
            sb.append("C").append(f(c.control0X)).append(",").append(f(c.control0Y))
                .append(" ").append(f(c.control1X)).append(",").append(f(c.control1Y))
                .append(" ").append(f(c.anchor1X)).append(",").append(f(c.anchor1Y))
        }
        sb.append("Z")
        return sb.toString()
    }

    /**
     * The radius of a single-radius [RoundedCornerShape], in dp.
     *
     * Every shape in `FlightShapeScale` is built from one `Dp`, so the top-start
     * corner describes the whole shape. `CornerSize` exposes no accessor for the
     * `Dp` it was built from, but it will resolve itself against a [Density], and
     * at density 1 a pixel is a dp. The shape size only matters for a percentage
     * corner, which this scale does not use.
     */
    private fun cornerDp(shape: RoundedCornerShape): Float =
        shape.topStart.toPx(Size(1000f, 1000f), Density(1f))

    private fun spring(name: String, spec: Any): String {
        val s = spec as? SpringSpec<*>
            ?: error("Motion token '$name' is no longer a SpringSpec: ${spec::class}")
        return "{\"dampingRatio\": ${s.dampingRatio}, \"stiffness\": ${s.stiffness}}"
    }

    /** Strips the `.sp` suffix Compose's unit types print, or `null` if unspecified. */
    private fun unit(value: Any?): String {
        val s = value.toString()
        if (s == "Unspecified" || s == "null") return "null"
        return s.removeSuffix(".sp").toFloatOrNull()?.toString() ?: "null"
    }

    fun buildJson(): String {
        val motion = MotionScheme.expressive()
        val schemes = listOf(
            "brandLight" to BrandLightColorScheme,
            "brandDark" to BrandDarkColorScheme,
            "cockpit" to CockpitColorScheme,
            "chart" to ChartColorScheme,
        )
        val skies = listOf(
            "brandLight" to BrandLightSkyColors,
            "brandDark" to BrandDarkSkyColors,
            "cockpit" to CockpitSkyColors,
            "chart" to ChartSkyColors,
        )
        val sb = StringBuilder()
        sb.appendLine("{")
        sb.appendLine("  \"_generated\": \"core/designsystem DesignTokenExport — do not edit by hand\",")

        sb.appendLine("  \"schemes\": {")
        schemes.forEachIndexed { i, (name, scheme) ->
            sb.appendLine("    \"$name\": {")
            val roles = scheme.roles()
            roles.forEachIndexed { j, (role, color) ->
                sb.append("      \"$role\": \"${color.hex()}\"")
                sb.appendLine(if (j == roles.lastIndex) "" else ",")
            }
            sb.appendLine(if (i == schemes.lastIndex) "    }" else "    },")
        }
        sb.appendLine("  },")

        sb.appendLine("  \"flightRules\": {")
        val ruleSets = listOf("light" to LightFlightRulesColors, "dark" to DarkFlightRulesColors)
        ruleSets.forEachIndexed { i, (name, set) ->
            sb.appendLine("    \"$name\": {")
            val ps = set.pairs()
            ps.forEachIndexed { j, (cat, container, on) ->
                sb.append("      \"$cat\": {\"container\": \"${container.hex()}\", \"onContainer\": \"${on.hex()}\"}")
                sb.appendLine(if (j == ps.lastIndex) "" else ",")
            }
            sb.appendLine(if (i == ruleSets.lastIndex) "    }" else "    },")
        }
        sb.appendLine("  },")

        sb.appendLine("  \"sky\": {")
        skies.forEachIndexed { i, (name, sky) ->
            sb.appendLine("    \"$name\": {")
            val all = sky.all
            all.forEachIndexed { j, (key, color) ->
                sb.append("      \"$key\": \"${color.hex()}\"")
                sb.appendLine(if (j == all.lastIndex) "" else ",")
            }
            sb.appendLine(if (i == skies.lastIndex) "    }" else "    },")
        }
        sb.appendLine("  },")

        sb.appendLine("  \"typography\": {")
        val slots = FlightTypography.slots()
        slots.forEachIndexed { i, (name, style) ->
            val weight = style.fontWeight?.weight?.toString() ?: "null"
            val features = style.fontFeatureSettings?.let { "\"$it\"" } ?: "null"
            sb.append(
                "    \"$name\": {\"fontSize\": ${unit(style.fontSize)}, " +
                    "\"lineHeight\": ${unit(style.lineHeight)}, " +
                    "\"letterSpacing\": ${unit(style.letterSpacing)}, " +
                    "\"fontWeight\": $weight, \"fontFeatureSettings\": $features}",
            )
            sb.appendLine(if (i == slots.lastIndex) "" else ",")
        }
        sb.appendLine("  },")

        sb.appendLine("  \"shapes\": {")
        val corners = listOf(
            "extraSmall" to FlightShapeScale.extraSmall,
            "small" to FlightShapeScale.small,
            "medium" to FlightShapeScale.medium,
            "large" to FlightShapeScale.large,
            "largeIncreased" to FlightShapeScale.largeIncreased,
            "extraLarge" to FlightShapeScale.extraLarge,
            "extraLargeIncreased" to FlightShapeScale.extraLargeIncreased,
            "extraExtraLarge" to FlightShapeScale.extraExtraLarge,
        )
        corners.forEachIndexed { i, (name, shape) ->
            sb.append("    \"$name\": ${cornerDp(shape as RoundedCornerShape)}")
            sb.appendLine(if (i == corners.lastIndex) "" else ",")
        }
        sb.appendLine("  },")

        sb.appendLine("  \"materialShapes\": {")
        val polys = listOf(
            "circle" to MaterialShapes.Circle,
            "cookie" to MaterialShapes.Cookie9Sided,
            "clover" to MaterialShapes.Clover4Leaf,
            "verySunny" to MaterialShapes.VerySunny,
        )
        polys.forEachIndexed { i, (name, poly) ->
            sb.append("    \"$name\": \"${poly.svgPath()}\"")
            sb.appendLine(if (i == polys.lastIndex) "" else ",")
        }
        sb.appendLine("  },")

        sb.appendLine("  \"motion\": {")
        val specs = listOf(
            "spatial" to motion.defaultSpatialSpec<Float>(),
            "spatialFast" to motion.fastSpatialSpec<Float>(),
            "spatialSlow" to motion.slowSpatialSpec<Float>(),
            "effects" to motion.defaultEffectsSpec<Float>(),
            "effectsFast" to motion.fastEffectsSpec<Float>(),
            "effectsSlow" to motion.slowEffectsSpec<Float>(),
        )
        specs.forEachIndexed { i, (name, spec) ->
            sb.append("    \"$name\": ${spring(name, spec)}")
            sb.appendLine(if (i == specs.lastIndex) "" else ",")
        }
        sb.appendLine("  },")

        sb.appendLine("  \"constants\": {")
        sb.appendLine("    \"enterStaggerMillis\": ${FlightMotion.EnterStaggerMillis},")
        sb.appendLine("    \"enterStaggerCap\": ${FlightMotion.EnterStaggerCap},")
        sb.appendLine("    \"emphasisMillis\": ${FlightMotion.EmphasisMillis}")
        sb.appendLine("  }")
        sb.appendLine("}")
        return sb.toString()
    }
}
