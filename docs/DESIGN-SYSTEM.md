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

It also sets the **system-bar appearance** from the scheme it just resolved —
`isAppearanceLightStatusBars` and `isAppearanceLightNavigationBars` — because the
bars are transparent and nothing else is keeping the clock legible. It must come
from the resolved scheme rather than from the system night setting: those agree only
until a settings screen offers Light, Dark or Cockpit, at which point a near-black
app under a light system would lose its status bar entirely. This is the one place
the design system touches the window, and it is a `SideEffect` of two field writes.

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

    @Composable fun sharedEnter(): EnterTransition      // fade only, no scale
    @Composable fun sharedExit(): ExitTransition
    @Composable fun boundsTransform(): BoundsTransform  // how a shared element travels
    @Composable fun rememberCountUp(target: Int): Int   // one-shot emphasis on a figure
}

@Composable fun rememberReduceMotion(): Boolean
```

**`sharedEnter` / `sharedExit` exist because `navEnter` scales.** A shared element
is drawn in an overlay, and an overlay does not inherit the transform on the screen
underneath it — so a container scaling from 0.94 puts the travelling element and the
layout it is arriving into at two different sizes for the whole transition. A pair of
screens that share an element uses these instead, and lets the element carry the
movement on its own.

**`rememberCountUp` is the one place in this file that uses a duration rather than a
spring,** and deliberately: a spring's overshoot on a counter runs the figure past its
value and back, which reads as a mistake being corrected. It counts from zero on first
composition and from wherever it is on a later change — *this value appeared* versus
*this value changed* — and returns the target immediately under reduce motion.

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
fun TextStyle.asChartFigure(): TextStyle         // tnum + TextDirection.Ltr

val FlightShapeScale: Shapes                     // includes the expressive tiers
```

Numerics use tabular figures so ICAO codes, distances, runway lengths and times do
not jitter in width as they change — essential in a list of routes where the eye
scans a column.

**Use `asChartFigure` for a string that is *entirely* a figure**, and
`withTabularFigures` for prose that happens to contain one. The difference is
direction, and it only shows up under an RTL locale: the bidi algorithm reorders a
run of neutral characters in an RTL paragraph, so `1,308 NM` arrives as `NM 1,308`,
a coordinate pair swaps its halves, and a runway line comes out backwards. A runway
ident, a heading, a distance and a coordinate read left to right on every
aeronautical publication in the world, including in countries that read right to
left — this is the same rule `asFigure` applies to the digits, carried through to
the order the fields come out in. Do **not** put it on a sentence: a sentence
follows the language it is written in.

`ValueChip` arranges its label and value with `SpaceBetween` for the same reason.
When the chip is sized to its content the two arrangements are identical; it only
matters once a caller stretches it into a grid of equal-width chips, and there
centring would move the value left and right as its width changed, so "13 NM" and
"2,990 NM" would sit at different offsets and the eye would have to re-find the
number on every row. Pinned to both edges, the labels align down one edge and the
digits down the other — which is how a table of numbers has always been set.

```kotlin
object FlightShapes {
    val Circle: RoundedPolygon
    val Cookie: RoundedPolygon
    val Clover: RoundedPolygon
    val VerySunny: RoundedPolygon
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
Box(Modifier.clip(rememberMorphShape(Morph(FlightShapes.Circle, FlightShapes.Cookie), progress)))
```

## Components

```kotlin
@Composable fun FlightRulesBadge(rules: FlightRules, modifier: Modifier = Modifier)
@Composable fun ValueChip(label: String, value: String, modifier: Modifier = Modifier,
                          containerAlpha: Float = 1f)
@Composable fun SkeletonBox(modifier: Modifier = Modifier, shape: Shape = MaterialTheme.shapes.small)
@Composable fun SkeletonCard(modifier: Modifier = Modifier)
@Composable fun EmptyState(title: String, message: String, modifier: Modifier = Modifier,
                           actionLabel: String? = null, onAction: (() -> Unit)? = null)
@Composable fun ErrorState(title: String, message: String, modifier: Modifier = Modifier,
                           onRetry: (() -> Unit)? = null)
@Composable fun MorphingLoadingIndicator(modifier: Modifier = Modifier,
                                         contained: Boolean = false,
                                         contentDescription: String = "Loading")

@Immutable data class ModeOption(val label: String,
                                val count: Int? = null,
                                val contentDescription: String? = null,
                                val enabled: Boolean = true)

@Composable fun ModeSelector(options: List<ModeOption>, selectedIndex: Int,
                             onSelect: (Int) -> Unit, modifier: Modifier = Modifier)

@Composable fun FilterField(label: String, value: String, selected: Boolean,
                            onClick: () -> Unit, modifier: Modifier = Modifier,
                            detail: String? = null)


@Composable fun RouteMap(arc: GeoArc, outline: WorldOutline, modifier: Modifier = Modifier,
                         landColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                         coastColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f),
                         routeColor: Color = MaterialTheme.colorScheme.primary,
                         casingColor: Color = MaterialTheme.colorScheme.surfaceContainer)

enum class SwipeActionSide { Start, End }

@Composable fun SwipeActionBackground(side: SwipeActionSide, icon: Painter, label: String,
                                     containerColor: Color, contentColor: Color,
                                     modifier: Modifier = Modifier,
                                     progress: Float = 1f, committed: Boolean = false)
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
- **`ModeSelector` is single-select chips in a `FlowRow`**, and it is neither
  `ButtonGroup` (which *crashes* in the pinned alpha — see
  [API-GROUND-TRUTH.md](API-GROUND-TRUTH.md)) nor the segmented row it wrapped
  first. A segmented row divides the width **equally**, so every option is as wide
  as the longest one needs and none is as wide as it wants: "Not flown · 116" did
  not fit a third of a 360 dp phone and ellipsised to a *wrong number*, which is
  why the count used to be spoken to TalkBack and never drawn. Chips are sized to
  their own label, so the count is visible, and the row **wraps** at font scale 2.0
  where a segmented row truncates. The row is a `selectableGroup` of
  `Role.RadioButton` chips, so TalkBack announces "2 of 3".
- **`FilterField` is a control set like the data it filters** — a letter-spaced caps
  label over a short identifier in tabular figures, over one line of detail. Its
  detail line is the point: a chip can say *what* is set, a field can say what that
  **means** ("3,010 NM · 6,900 ft" is the envelope every route was generated
  inside). Outline is unset and a filled container is set, a language it shares with
  `ModeSelector`, and both survive being drawn on any surface. It holds no space for
  a detail it does not have — unlike the route card's flight-rules slot, because the
  user's own tap is what changes it. Put a pair inside a
  `Modifier.height(IntrinsicSize.Min)` row with `fillMaxHeight` to keep them level.
- **`RouteMap` is the card's background, and it takes geography.** Land at 8 % of
  `onSurface` with its coast at 16 %, then the route as the only saturated thing on
  the card — that ratio is what makes a scrim unnecessary, and a design that needs
  no scrim is simpler than one hiding behind a gradient. Every line is drawn twice,
  a casing in the card's colour under the line itself, which is the technique a
  chart uses to keep a route readable wherever it crosses something. It takes a
  `GeoArc` rather than projected points — the opposite of the sparkline it replaced
  — because the window it projects through depends on the *canvas aspect ratio*, so
  the projection cannot be done before the card is measured. The expensive half,
  spherical interpolation, still happens once per route off the main thread; what
  happens here is a multiply per point plus a clip, inside `drawWithCache`, so it
  runs when the size changes and never while scrolling. The clip is two operations
  on purpose: the fill is a polygon clipped to the card, the coast is the original
  segments trimmed and left open, because stroking a clipped polygon would draw a
  hairline box around every card. On device the whole map costs about 1 ms a frame.
  It also draws the direction arrowhead — a triangle, not `MaterialShapes.Arrow`,
  whose corner radii round away the tip at eight pixels a side — and, when the
  window frames no coastline at all, a graticule at the land fill's own contrast,
  so an inland card reads as a place rather than as a failed load.
  **It crops its own drawing.** The outline is projected with a margin, so the map
  paints past its own bounds by design, and for a long time it let the `Card` around
  it do the cropping. That broke the first time it was used as a shared element: a
  shared element is rendered in an overlay where no ancestor clip applies, and every
  off-window coastline had the whole screen to draw on. The crop is a `clipRect`
  inside the draw scope rather than `Modifier.clipToBounds()`, which is a
  `graphicsLayer` and would put an offscreen layer on every card in the list.
- **`SwipeActionBackground` takes colours rather than choosing them.** "Confirm"
  and "destroy" are the caller's semantics. Pass the *solid* roles, not the
  containers: a pale container behind a `surfaceContainer` card is two
  neighbouring greys and the reveal stays invisible until it is nearly complete.
  There is deliberately no hardcoded green anywhere in it — a fixed hue would be
  the one element on screen that ignores the Cockpit theme.
- **`progress` and `committed` are separate on purpose.** `progress` tracks the
  finger with no spring, because a spring lags and the surface must feel attached
  to the drag. `committed` is a discrete event and gets a sprung response.
- Every atom carries a `@LightDarkPreview`. A component only ever viewed in one
  theme is a component whose other theme is broken and nobody has noticed.

## Preview annotations

```kotlin
@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = UI_MODE_NIGHT_YES)
annotation class LightDarkPreview

@Preview(name = "Compact 360dp", showBackground = true, widthDp = 360)
@Preview(name = "Compact 360dp · font 2.0", showBackground = true, widthDp = 360, fontScale = 2.0f)
annotation class CompactWidthPreview
```

Public rather than internal, because screens in `:app` need the same pairing and
one shared annotation is what keeps every preview in the project framed the same
way.

**Anything with a horizontal layout gets `@CompactWidthPreview` as well.** 360 dp
is what a 1080 × 2340 phone at 480 dpi reports — the most common phone width there
is, and the width at which this app's dense rows first overflow. The tooling's
default preview is wider than any phone this app runs on, so previewing only at
the default hides exactly the problems worth catching: the route card shipped with
a clipped runway figure for precisely that reason.

## Adding to this module

1. Check the symbol exists in the pinned versions by compiling a probe
   (see [API-GROUND-TRUTH.md](API-GROUND-TRUTH.md)) — not by reading docs.
2. Add a `@LightDarkPreview`.
3. If it animates, drive it from `FlightMotion`, and handle `rememberReduceMotion()`
   if the animation is infinite or staged.
4. Update this file.
