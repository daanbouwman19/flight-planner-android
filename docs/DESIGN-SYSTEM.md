# The design system

`:core:designsystem` is the whole surface a screen is allowed to build on. It
exists for two reasons: to keep the app visually coherent, and to contain the
`material3:1.5.0-alpha26` dependency to one module so an alpha bump is a one-module
change rather than an app-wide one.

Package root: `com.github.daanbouwman.flightplanner.core.designsystem`

Everything below is the **current, compiled** API — not a proposal. If it disagrees
with the source, the source wins and this file is stale.

---

## Theme

```kotlin
enum class ThemeChoice { SYSTEM, LIGHT, DARK, COCKPIT }

@Composable
fun FlightPlannerTheme(
    themeChoice: ThemeChoice = ThemeChoice.SYSTEM,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
)
```

Wraps `MaterialExpressiveTheme` with `MotionScheme.expressive()`, `FlightShapeScale`
and `FlightTypography`. Scheme selection, in order:

1. `COCKPIT` → `CockpitColorScheme`, a near-black instrument panel with amber
   accents. It **ignores `dynamicColor` deliberately** — the theme exists so the
   screen stops competing with the pilot's dark adaptation, and a scheme derived
   from whatever the wallpaper happens to be cannot promise that.
2. `dynamicColor` on → wallpaper scheme. No version guard; `minSdk` is 36.
3. Otherwise → `BrandLightColorScheme` / `BrandDarkColorScheme`, avgas blue with
   runway-marking amber.

## Flight-rules colours

```kotlin
@Immutable data class FlightRulesColorPair(val container: Color, val onContainer: Color)

@Immutable data class FlightRulesColors(
    val vfr: FlightRulesColorPair, val mvfr: FlightRulesColorPair,
    val ifr: FlightRulesColorPair, val lifr: FlightRulesColorPair,
    val unknown: FlightRulesColorPair,
) {
    operator fun get(rules: FlightRules): FlightRulesColorPair
    val all: List<Pair<FlightRules, FlightRulesColorPair>>
}

val LocalFlightRulesColors: ProvidableCompositionLocal<FlightRulesColors>
val LightFlightRulesColors: FlightRulesColors
val DarkFlightRulesColors: FlightRulesColors   // dark and cockpit share this
```

Read them through the composition local, never by importing the palettes directly.
The pair travels together so a call site cannot take a container from one category
and a foreground from another.

**These colours are outside the Material scheme and are never dynamic.** See
CLAUDE.md; `FlightRulesContrastTest` is what enforces it.

## Motion

```kotlin
object FlightMotion {
    @Composable fun <T> spatial(): FiniteAnimationSpec<T>       // damping 0.8, stiffness 380
    @Composable fun <T> spatialFast(): FiniteAnimationSpec<T>   // 0.6, 800
    @Composable fun <T> spatialSlow(): FiniteAnimationSpec<T>   // 0.8, 200
    @Composable fun <T> effects(): FiniteAnimationSpec<T>       // 1.0, 1600
    @Composable fun <T> effectsFast(): FiniteAnimationSpec<T>   // 1.0, 3800
    @Composable fun <T> effectsSlow(): FiniteAnimationSpec<T>   // 1.0, 800

    const val EnterStaggerMillis: Int = 30
    const val EnterStaggerCap: Int = 8
    const val EmphasisMillis: Int = 500

    fun enterDelayMillis(index: Int): Int
    @Composable fun navEnter(): EnterTransition
    @Composable fun navExit(): ExitTransition
}

@Composable fun rememberReduceMotion(): Boolean
```

Each is a thin alias over `MaterialTheme.motionScheme`; none writes a spring
constant. **Screens name an intent, never physics.**

**Spatial vs effects is not a style choice.** Spatial springs are underdamped —
they overshoot and settle, and that overshoot *is* the expressive character, so a
card that moves reads as having mass. Effects springs are critically damped
(ratio 1.0) because a colour or alpha that overshoots does not read as momentum,
it reads as a flicker. Use spatial for anything that moves or resizes; effects for
fades, colour and elevation. A spatial spring on a colour fade looks broken.

**Reduce motion.** Compose already collapses spring-driven animation when the
system animator scale is zero, so ordinary transitions need no special handling.
What *does* need it is anything infinite or staged — shimmer, stagger, count-ups.
Those must consult `rememberReduceMotion()` and **switch off**, not merely shorten.

## Typography and shape

```kotlin
val FlightTypography: Typography
fun TextStyle.withTabularFigures(): TextStyle    // fontFeatureSettings = "tnum"

val FlightShapeScale: Shapes                     // includes the expressive tiers
```

Numerics use tabular figures so ICAO codes, distances, runway lengths and times do
not jitter in width as they change — essential in a list of routes where the eye
scans a column.

```kotlin
object FlightShapes {
    val Circle: RoundedPolygon
    val Cookie: RoundedPolygon
    val Clover: RoundedPolygon
    val VerySunny: RoundedPolygon
    val Arrow: RoundedPolygon
    val GenerateFabMorph: Morph              // circle -> cookie, the FAB press
    val LoadingPolygons: List<RoundedPolygon>
}

@Composable fun RoundedPolygon.asShape(startAngle: Int = 270): Shape
@Immutable class MorphShape(morph: Morph, progress: Float, startAngle: Int = 270) : Shape
@Composable fun rememberMorphShape(morph: Morph, progress: Float, startAngle: Int = 270): Shape
```

`MorphShape` is the one piece material3 does not hand you: it gives you a static
polygon `Shape` and a `Morph.toPath(progress)` (usefully **not** `@Composable`, so
it is safe every frame), but nothing that animates between them.

```kotlin
val progress by animateFloatAsState(if (pressed) 1f else 0f, FlightMotion.spatialFast())
Box(Modifier.clip(rememberMorphShape(FlightShapes.GenerateFabMorph, progress)))
```

## Components

```kotlin
@Composable fun FlightRulesBadge(rules: FlightRules, modifier: Modifier = Modifier)
@Composable fun ValueChip(label: String, value: String, modifier: Modifier = Modifier)
@Composable fun SectionHeader(text: String, modifier: Modifier = Modifier)
@Composable fun SkeletonBox(modifier: Modifier = Modifier, shape: Shape = MaterialTheme.shapes.small)
@Composable fun SkeletonCard(modifier: Modifier = Modifier)
@Composable fun EmptyState(title: String, message: String, modifier: Modifier = Modifier,
                           actionLabel: String? = null, onAction: (() -> Unit)? = null)
@Composable fun ErrorState(title: String, message: String, modifier: Modifier = Modifier,
                           onRetry: (() -> Unit)? = null)
@Composable fun MorphingLoadingIndicator(modifier: Modifier = Modifier,
                                         contained: Boolean = false,
                                         contentDescription: String = "Loading")
```

Notes that are easy to get wrong:

- **`EmptyState` and `ErrorState` are not interchangeable.** An empty list and a
  failed load render identically in a naive implementation, but they are opposite
  situations — one is the app working and waiting for the user, the other is the
  app having failed. Using one for both teaches the user to ignore the message.
- `EmptyState` needs **both** `actionLabel` and `onAction` to draw its button; a
  label with no handler is a dead button, a handler with no label is invisible.
  Prefer supplying them: "no routes yet" is a description, "no routes yet —
  Generate" is a next step.
- `ErrorState` is a polite live region, so a screen-reader user learns the load
  failed even when focus is elsewhere.
- Skeletons beat a spinner when the content's shape is known, because they stop the
  layout jumping. They are *worse* than a spinner when you are guessing at the size.
- Every atom carries a `@LightDarkPreview`. A component only ever viewed in one
  theme is a component whose other theme is broken and nobody has noticed.

## Adding to this module

1. Check the symbol exists in the pinned versions by compiling a probe
   (see [API-GROUND-TRUTH.md](API-GROUND-TRUTH.md)) — not by reading docs.
2. Add a `@LightDarkPreview`.
3. If it animates, drive it from `FlightMotion`, and handle `rememberReduceMotion()`
   if the animation is infinite or staged.
4. Update this file.
