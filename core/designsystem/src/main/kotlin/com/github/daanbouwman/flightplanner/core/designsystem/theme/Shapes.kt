@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.github.daanbouwman.flightplanner.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.Shapes
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.compose.material3.toPath as morphToPath

/**
 * The Expressive shape scale.
 *
 * Material 3 Expressive widened the old five-step scale to eight by inserting
 * `largeIncreased` and `extraLargeIncreased` and topping it off with
 * `extraExtraLarge`. That matters here because the app's two most prominent
 * surfaces — the route card and the detail sheet — used to have to share `large`
 * with everything else; now the card can sit at `largeIncreased` and the sheet at
 * `extraLargeIncreased` and the difference is legible rather than theoretical.
 *
 * The radii are the Expressive defaults, written out explicitly so the scale is
 * readable at a glance instead of hiding inside a token table.
 */
val FlightShapeScale: Shapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    largeIncreased = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
    extraLargeIncreased = RoundedCornerShape(32.dp),
    extraExtraLarge = RoundedCornerShape(48.dp),
)

/**
 * The handful of `MaterialShapes` this app actually uses.
 *
 * The catalogue has forty shapes. Naming the four we use — and naming them for
 * what they are *for* rather than for what they look like — is what stops a
 * component from quietly becoming a clover one day because a clover was
 * available.
 *
 * An `Arrow` polygon was removed rather than kept "for later": nothing ever drew
 * it, because the route card draws its direction marker as a triangle instead — a
 * rounded polygon at eight pixels a side rounds away the tip that carries the
 * whole signal. A design system is a set of promises about what the app looks
 * like, and an unused promise drifts. `Circle`/`Cookie` came close to the same
 * fate: the named morph built for Plan's generate FAB was deleted when that FAB
 * was cut, but the pair itself stayed — and Fleet's own add-aircraft FAB now
 * morphs between them on press, which is the promise being kept rather than one
 * that drifted.
 */
object FlightShapes {

    /** A FAB's shape at rest, and the "nothing is happening" state generally. */
    val Circle: RoundedPolygon get() = MaterialShapes.Circle

    /** A FAB's shape while pressed: a nine-lobed cookie, unmistakably *active*. */
    val Cookie: RoundedPolygon get() = MaterialShapes.Cookie9Sided

    /** Used for the loading indicator's rotation and for aircraft-category glyphs. */
    val Clover: RoundedPolygon get() = MaterialShapes.Clover4Leaf

    /** The spiky one; the last frame of the loading sequence before it settles. */
    val VerySunny: RoundedPolygon get() = MaterialShapes.VerySunny

    /**
     * The polygon sequence [androidx.compose.material3.LoadingIndicator] cycles
     * through. Four shapes rather than the default seven — the sequence is short
     * enough to read as one gesture, which suits a loading state that usually
     * lasts a few hundred milliseconds.
     */
    val LoadingPolygons: List<RoundedPolygon> by lazy {
        listOf(Circle, Cookie, Clover, VerySunny)
    }
}

/**
 * Converts a [RoundedPolygon] to a Compose [Shape].
 *
 * A thin re-export of material3's own `RoundedPolygon.toShape()`, so that a
 * screen can clip something to a `MaterialShapes` polygon without importing an
 * Expressive symbol directly — which is the rule that keeps the alpha
 * dependency contained to this module.
 */
@Composable
fun RoundedPolygon.asShape(startAngle: Int = 270): Shape = toShape(startAngle)

/**
 * A [Shape] that reads a [Morph] at a given progress.
 *
 * This is the one piece material3 does not hand you ready-made. It gives you
 * `RoundedPolygon.toShape()` (a static polygon) and `Morph.toPath(progress)` (a
 * path, not a shape, but usefully **not** `@Composable`, so it is safe to call
 * every frame). The gap between them is this class: anything that travels from
 * one shape to another under a spring needs a new outline on every frame of the
 * animation.
 *
 * Construct a new instance per frame — that is intentional, and cheap. The shape
 * is a value: two instances with the same morph and progress are equal, so
 * Compose skips the re-clip when the animation settles.
 *
 * ```
 * val progress by animateFloatAsState(if (pressed) 1f else 0f, FlightMotion.spatialFast())
 * Box(Modifier.clip(MorphShape(Morph(FlightShapes.Circle, FlightShapes.Cookie), progress)))
 * ```
 *
 * The geometry mirrors what material3 does inside `toShape()`: the morph's path
 * is normalised, so it is scaled to the requested [Size] and then recentred,
 * because a scaled polygon's bounds do not necessarily centre themselves.
 */
@Immutable
class MorphShape(
    private val morph: Morph,
    private val progress: Float,
    private val startAngle: Int = 270,
) : Shape {

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = morph.morphToPath(progress = progress.coerceIn(0f, 1f), startAngle = startAngle)
        val matrix = Matrix()
        matrix.scale(x = size.width, y = size.height)
        path.transform(matrix)
        path.translate(size.center - path.getBounds().center)
        return Outline.Generic(path)
    }

    override fun equals(other: Any?): Boolean =
        other is MorphShape &&
            morph === other.morph &&
            progress == other.progress &&
            startAngle == other.startAngle

    override fun hashCode(): Int {
        var result = morph.hashCode()
        result = 31 * result + progress.hashCode()
        result = 31 * result + startAngle
        return result
    }

    override fun toString(): String = "MorphShape(progress=$progress, startAngle=$startAngle)"
}

/**
 * Remembers a [MorphShape] keyed on its progress.
 *
 * Convenience for the common case where the progress comes from an animation
 * `State` — it keeps the allocation to one per distinct frame value instead of
 * one per recomposition.
 */
@Stable
@Composable
fun rememberMorphShape(morph: Morph, progress: Float, startAngle: Int = 270): Shape =
    remember(morph, progress, startAngle) { MorphShape(morph, progress, startAngle) }
