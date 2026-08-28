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
enum class ThemeChoice { SYSTEM, LIGHT, DARK, COCKPIT, CHART }

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
   accents; `CHART` → `ChartColorScheme`, paper and ink. Both **ignore
   `dynamicColor` deliberately** — each theme's identity is a specific pair of
   surfaces and ink (Cockpit's so the screen stops competing with the pilot's dark
   adaptation), and a scheme derived from whatever the wallpaper happens to be
   cannot promise that.
2. `dynamicColor` on → wallpaper scheme. No version guard; `minSdk` is 35 and dynamic colour is API 31+.
3. Otherwise → `BrandLightColorScheme` / `BrandDarkColorScheme`, avgas blue with
   runway-marking amber.

**There are five values here and two of them are not a light or a dark anything.**
That is what makes the theme choice, rather than the tone mapping, the right key
for anything scenic — see `SkyColors` below.

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
    @Composable fun paneContent(): ContentTransform     // content replaced in place
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

**`paneContent` is for a panel that stays put and is now about something else** — a
detail pane after a new selection. A fade-through, never a slide, because a slide
claims a direction the selection does not have. The outgoing half runs on the *fast*
effects spring and the incoming half on the default one, so the old content is gone
before the new is fully there; faded at the same rate, two dense pages of figures
overlap into a smear. Pair it with content that does not also stage its own
entrance — same rule as the shared elements, one motion per change.

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
                           actionLabel: String? = null, onAction: (() -> Unit)? = null,
                           icon: Painter? = null)
@Composable fun ErrorState(title: String, message: String, modifier: Modifier = Modifier,
                           onRetry: (() -> Unit)? = null)
@Composable fun MorphingLoadingIndicator(modifier: Modifier = Modifier,
                                         contained: Boolean = false,
                                         contentDescription: String = "Loading")
@Composable fun ConfirmationDialog(title: String, message: String, confirmLabel: String,
                                   onConfirm: () -> Unit, onDismiss: () -> Unit,
                                   modifier: Modifier = Modifier)

@Immutable data class ModeOption(val label: String,
                                val count: Int? = null,
                                val contentDescription: String? = null,
                                val enabled: Boolean = true)

@Composable fun ModeSelector(options: List<ModeOption>, selectedIndex: Int,
                             onSelect: (Int) -> Unit, modifier: Modifier = Modifier)

@Composable fun FilterField(label: String, value: String, selected: Boolean,
                            onClick: () -> Unit, modifier: Modifier = Modifier,
                            detail: String? = null)

data class StatTile(val label: String, val value: Int,
                    val format: (Int) -> String = { it.toString() },
                    val contentDescription: String? = null)

@Composable fun StatSummaryStrip(tiles: List<StatTile>, modifier: Modifier = Modifier)

@Composable fun MonthHeader(label: String, modifier: Modifier = Modifier)

@Composable fun FlightDatePickerDialog(selectedDate: LocalDate, maxDate: LocalDate,
                                       onDateSelected: (LocalDate) -> Unit,
                                       onDismiss: () -> Unit,
                                       modifier: Modifier = Modifier)


@Composable fun RouteMap(arc: GeoArc, outline: WorldOutline, modifier: Modifier = Modifier,
                         landColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                         coastColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f),
                         routeColor: Color = MaterialTheme.colorScheme.primary,
                         casingColor: Color = MaterialTheme.colorScheme.surfaceContainer)

@Immutable data class DiagramWind(val directionFromDeg: Int?, val speedKt: Int,
                                  val gustKt: Int? = null, val variable: Boolean = false) {
    val hasDirection: Boolean
}

@Composable fun RunwayDiagram(runways: List<Runway>, modifier: Modifier = Modifier,
                              wind: DiagramWind? = null,
                              hardColor: Color = MaterialTheme.colorScheme.onSurface,
                              softColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
                              litColor: Color = MaterialTheme.colorScheme.primary)

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
- **`RunwayDiagram` draws a true plan when the data has one, and a compass when
  it does not.** OurAirports publishes real threshold coordinates for every end
  of most well-documented fields and for very few small ones, so there are two
  layouts. When every diagrammed end has both a latitude and a longitude,
  `positionedRays` projects them onto a small local plane (equirectangular — an
  airport spans a few kilometres, far too little for curvature to matter) and
  draws each physical runway as the real segment between its two thresholds: a
  genuine miniature of Denver or Schiphol. Otherwise `layoutRunways` falls back
  to the compass schematic — one ray per end at its true heading, with
  *roughly parallel* families fanned into evenly spaced lanes. The fan is not
  decoration: a first version drew every ray through one shared centre and turned
  Edwards Air Force Base's three near-parallel runways into a starburst through a
  point that does not exist. Pairing is by opposite heading and matching length,
  never by parsing `09`/`27` or `18R`/`36L`. Ends with no published heading (real
  on grass strips) are skipped and reported as a count underneath rather than
  silently dropped.
- **A runway's designator goes at its threshold, and getting that wrong is
  actively misleading.** Both layouts tip a ray at the end the runway *points
  toward*, and the diagram used to anchor the ident there — so every designator
  sat at the wrong end. Nobody noticed until `wind` arrived: a reader then saw
  `30` at the north-west end, applied the convention every FAA and Jeppesen plate
  uses, and concluded that departing 30 meant rolling south-east — downwind, and
  the opposite of what `SurfaceWind` had actually recommended. `RunwayRay` now
  carries an explicit `threshold`, because neither layout can derive it from the
  ray alone: positioned rays start at the end's own published coordinate, while
  the two lane-schematic rays of one strip *share* an origin and must mirror the
  tip about it instead.
- **`RunwayDiagram`'s optional `wind` is where a crosswind stops being
  arithmetic.** A wind direction in degrees has to be compared against a runway
  heading, and drawn in the same frame as the runways the comparison becomes a
  picture: the sock points down the strip or across it. It brings a windsock, a
  halo under the favoured strip and the preferred end's ident in bold sock
  orange — the halo says *which strip*, the numeral says *which end of it*, and
  the second is the question being asked. `SurfaceWind` in `:core:routing` does
  the trigonometry, returning `null` below 3 kt rather than inventing a
  recommendation. **Lift is length, not droop**: this is a plan view, and from
  directly above you cannot see a sock hang, you see it foreshortened. A version
  that tilted it made the sock rise *above* its own mast in a northerly, because
  the droop was being added to the bearing's screen-space Y component — a
  quantity a top-down view does not have. The mast is a dot for the same reason.
  `DiagramWind` is its own type rather than two nullables because *calm*,
  *variable* and *steady* are three different facts — a `VRB` group is a real
  speed with no usable direction, and a 0 kt report is the station saying the air
  is still — and no combination of a nullable direction and a nullable speed
  expresses that without the call site remembering which pairing means what.
- **`StatSummaryStrip` is not `ValueChip` stretched.** A chip is a small
  label-left/value-right unit sized to sit over a route card's map; a summary
  strip wants the opposite grammar — a few large centred figures under their
  captions, the way a statistics screen states a headline. Each tile counts up
  through `FlightMotion.rememberCountUp` once when it appears or changes, which
  is the motion principle's own example of an animation worth having: it draws
  the eye to a value that changed. `StatTile.format` is a plain `(Int) -> String`
  and **not** `@Composable`, because it is called on every frame of that count-up
  — so a unit-aware tile captures its suffix once outside the lambda rather than
  reading `LocalUnitSystem` inside it.
- **`MonthHeader` is opaque, and that is not a violation of the transparent-bars
  invariant.** That invariant is about the *window's* chrome — nothing may be
  painted behind the status and navigation bars. A sticky section header inside a
  scrolling list is a different object: it has to stay legible over whichever row
  happens to be passing behind it, which a translucent surface cannot promise for
  an arbitrary row's colours.
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

## Scenery colours

```kotlin
@Immutable data class SkyBand(val low: Color, val high: Color,
                              val cloudBody: Color, val cloudEdge: Color,
                              val convective: Color) {
    val precipitation: Color            // cloudEdge, deliberately
}

@Immutable data class CelestialInk(val sun: Color, val sunGlow: Color,
                                   val moonLit: Color, val moonDark: Color)
@Immutable data class GroundInk(val subsurface: Color, val dry: Color, val wet: Color,
                                val snow: Color, val frost: Color, val icy: Color,
                                val fog: Color) {
    operator fun get(condition: GroundCondition): Color?   // null for Unknown
}
@Immutable data class SockInk(val mast: Color, val band: Color, val alternateBand: Color)

@Immutable data class SkyColors(val day: SkyBand, val twilight: SkyBand, val night: SkyBand,
                                val celestial: CelestialInk, val ground: GroundInk,
                                val sock: SockInk)

val LocalSkyColors: ProvidableCompositionLocal<SkyColors>
val BrandLightSkyColors / BrandDarkSkyColors / CockpitSkyColors / ChartSkyColors
```

**Four authored palettes, keyed on `ThemeChoice` rather than on light/dark** — which
is the difference from `FlightRulesColors` and the reason it exists. Flight-rules
colours are *safety data* and must never be re-themed, so Cockpit shares the dark
set unchanged. **The sky is scenery**, and Cockpit exists to protect a pilot's dark
adaptation, so a bright day gradient there would be an actual defect rather than a
mismatch. Chart is paper and ink and is a light theme of nothing.

Flight-rules colours appear in the scene **only in the measuring apparatus** — the
threshold hairlines and the ceiling wedge — never in a sky, cloud or ground fill.
Nothing in `SkyColors` is a flight-rules colour.

- **Anything drawn as a mark against the air belongs to `SkyBand`.** This has now
  been forced three times. A cloud's underside needs 3:1 against the air, and no
  single ink clears that against both a bright day sky and a near-black night one —
  a dark line that reads at noon disappears at midnight. `cloudEdge` moved in first;
  `convective` followed after measurement showed Brand light's single value clearing
  only **2.66:1** against its own night band, with the two constraints provably
  contradictory (≤ 0.077 luminance for the day air, ≥ 0.124 for the night). The
  escape is never a cleverer colour, it is pairing.
- **`precipitation` is `cloudEdge` itself.** A falling streak is a hairline against
  the same three airs, so it has the identical constraint including the polarity
  reversal. Reusing the ink inherits a proved guarantee rather than making a fourth
  attempt at satisfying it — and rain is the same water as the cloud it fell from.
- **`GroundInk[Unknown]` returns null on purpose.** A grey is a colour the scene
  *could* paint, and painting anything asserts the surface state is known. The
  caller must reach for the hatch instead. That is the shape of the bug this whole
  redesign fixes, expressed in the palette.
- **Blending two bands is not always safe.** `blendBands` interpolates air and both
  inks, and `bandsAgreeOnPolarity` is what a caller must check first: where the ink
  reverses (Brand light and Chart, twilight→night) the intermediate value theorem
  guarantees a blend point at which ink and air have *equal* luminance and the
  deck's underside is a 1.0:1 line. No third ink exists — the endpoint constraints
  contradict. `SkyProfile` therefore drives that one crossing from a
  `FlightMotion.effects()` traversal instead of from the sun's elevation, turning
  twenty minutes of an invisible edge into a few hundred milliseconds of one.
- `SkyColorsContrastTest` enforces every claim above. Two of its rules were verified
  by **planting violations** — a bright sky pasted into Cockpit, an amber sun into
  Chart — because a rule that matches nothing passes silently and looks identical to
  a clean tree.

## The sky profile

```kotlin
enum class SkyPhase { DAY, TWILIGHT, NIGHT }

object SkyProfileHeight { val AirportDetail: Dp = 220.dp; val RouteDetail: Dp = 168.dp }

@Composable fun SkyProfile(metar: Metar?, modifier: Modifier = Modifier,
                           celestial: CelestialState? = null,
                           phase: SkyPhase = SkyPhase.DAY,
                           height: Dp = SkyProfileHeight.AirportDetail,
                           showAltitudeLabels: Boolean = true,
                           colors: SkyColors = LocalSkyColors.current,
                           rulesColors: FlightRulesColors = LocalFlightRulesColors.current,
                           axisColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
                           unknownAirColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest)
```

**It is not a picture of the sky.** It is a vertical cross-section read the way an
approach plate's profile view is: altitude on the Y axis, every deck at its true
base, the flight-rules thresholds as hairlines, the ground at the bottom. Read
[docs/WEATHER-PLAN.md](WEATHER-PLAN.md) before changing it.

- **The axis is the signature element and is deliberately not linear.** Piecewise,
  with breakpoints *at* the flight-rules thresholds, spending 56 % of its height on
  the first 3,000 ft. Linear, a 900 ft ceiling and a 2,900 ft one — the difference
  between IFR and MVFR — would be two hairlines 4 dp apart. The cost is a compressed
  upper air, which is the right trade: nobody reads a cirrus base to two significant
  figures and everybody reads a ceiling.
- **The air is four flat steps, and they break at the flight-rules thresholds.** It
  was a gradient, and that was the wrong mark twice over: a soft atmospheric wash
  beside the route card's flat map silhouette reads as a different application, and
  a wash puts its tone changes at heights that mean nothing. Each step is now the air
  inside one band, so the tone change and the hairline are the same edge stated
  twice, and the scene's coarsest channel — how dark the air is where a deck sits —
  answers the question the diagram is for. The warm lift near the ground survives as
  a *tint on the lowest steps* rather than as a gradient over them; it is small, and
  it is the whole of what stops four flat greys reading as a bar chart.
- **The graticule rides over the scene, and the numerals sit on chips.** Under the
  decks it failed in exactly the case it is for: an overcast lid at 900 ft covers the
  1,000 ft hairline, so the band structure disappears when the deck's position
  relative to it is the entire content of the frame. On a printed chart the
  graticule's ink is a different system from the data's, and rides above it. That
  costs a legibility guarantee, which the chip buys back: a numeral straight onto the
  scene would have to clear its bound against whichever step, deck or fog slab it
  landed on, and `onSurfaceVariant` — proved against a card surface these pixels are
  nowhere near — lands around 2.3:1 on the night band. The chip is one known mix
  between the band's two ends, inked in `cloudEdge`, which is proved against both.
  Reserving a left gutter for them is gone with it; the decks run full width.
- **Fog is an opaque slab with a hard top edge, not a fade.** Drawn as a gradient
  into the air it met the sky at about 1.1:1, so the headline case — a half-mile
  `FG VV002` field — looked like a clear day with a wash at the bottom. Fog is not a
  tint on the air; it is a surface, with a top you can see from above and cannot see
  through. Its edge takes `cloudEdge` for the same reason every other edge does: it
  is a deck's underside, upside down.
- **A convective deck gets two lightning strikes, not a tick.** The tick was the
  quietest possible way to mark the one thing in the frame that is dangerous, and at
  168 dp a 2 dp line in the band's convective ink is a smudge competing with the
  silhouette it stands on. Struck down toward the field, because the reach across the
  gap is what reads as a strike. The envelope is mostly dark with a leader and a
  return stroke a few frames apart — the eye reads that pair as lightning where it
  reads a single fade as a pulse — and the two bolts run at coprime whole-number
  rates inside one master cycle, so they neither drift out of the loop nor flash in
  lockstep. The bolt's **width is fixed in dp** while its height is the gap: scaled
  uniformly, a 4,300 ft CB produced a maroon slab a fifth of the frame across.
- **Precipitation varies per drop, and length is tied to speed.** The field read as
  pen hatching because every stroke was the same length, weight, opacity and speed.
  A streak is what the eye keeps of a drop that has already moved on, so it is long
  *because* it is fast — length and speed varying independently is the tell. Speeds
  are whole crossings of the frame per cycle (2, 3 or 4) and that is not a rounding
  convenience: one shared phase puts a drop at `(start + progress × speed) mod 1`, so
  a fractional speed leaves it mid-frame at the restart and the whole field jumps at
  once, once a cycle, forever.
- **The structure is what makes the original defect unrepresentable.** A sun over an
  IFR field was possible because the category was a label pinned beside a cartoon.
  Here the category is a *consequence of the geometry*, and there is no arrangement
  of this drawing that shows a 700 ft overcast as a nice day.
- **Three things can be unknown independently and each hatches**: no report at all,
  an unreported sky, an unreported surface. They compose — a report with a
  temperature but no cloud group draws real ground under hatched air.
- **`celestial` is resolved by the caller, at the observation instant, never at
  now.** The scene takes no clock: a composable that read `System.currentTimeMillis()`
  could not be previewed at dusk and would recompose on a schedule nobody asked for.
  Using the *observation* instant also keeps the frame one moment — a current sun
  over a three-day-old report is two times in one picture. `phase` remains for
  previews and for reports with no position.
- **The sun and moon ride a horizon rail at fractions 0.86–0.935, not the altitude
  axis.** A body has an elevation *angle*, not an altitude in feet, so the layer
  must never invite a reader to drop a horizontal onto the ruler. The defence is
  that `CeilingThresholds` tops out at 3,000 ft = fraction 0.56, so no hairline,
  tick or numeral exists anywhere near the rail; `HorizonRailTest` asserts that
  rather than assuming it. Each body used to carry a short datum and a stem beneath
  it as a second defence; that was removed, because the rail is only about 11 dp
  tall on the route-detail scene against a 7 dp disc radius, so what it drew was a
  line stuck under the sun that read as a shadow rather than as a baseline.
  Reinstating it needs a taller rail first. `sin(azimuth)` places a body across the
  frame — east right, west left, north and south folded to the centre, because once
  the section is cut east–west that fold *is* the projection. The inset that keeps a
  disc whole clears the ceiling wedge's depth as well as the frame's edge: a clear
  sky puts that wedge at the top of the axis, which is where a high moon at a
  westerly azimuth sits.
- **Cloud occlusion is alpha, not painting order.** The rail sits above almost every
  deck, so paint order would let a cirrus hide the sun while a 700 ft overcast let
  it shine through — the drawing lying, in the exact shape this redesign removes.
  Keyed to the lowest ceiling instead. Painting under the decks was tried and buried
  KDEN's afternoon sun outright.
- **The same east–west commitment is used three times** and that is the point: it
  places the bodies, slants the precipitation, and steers the deck drift. A
  northerly wind therefore drifts nothing and drops its rain vertically, because a
  wind along the section's line of sight has no component in it. Before the drift
  was directional, the rain and the deck it fell out of could slant opposite ways.
- **`Ceiling.Unlimited` gets the same wedge `Ceiling.At` does**, at the top of the
  axis. An affirmatively clear sky used to draw *nothing*, so it differed from an
  unknown one only by the absence of hatch — absence of evidence, one layer up.
- **Every phase-driven term must complete an integer number of cycles**, or it snaps
  at the repeat boundary; a deleted fog animation shipped that bug. The deck drift
  obeys it by construction. Precipitation meets it on each axis differently, and
  both halves are load-bearing: **horizontally** it sidesteps the rule by making `x`
  a function of `y`, so the particle *set* is invariant at any slant — the usual
  integer-tiles fix cannot work there, because the horizontal travel is not a whole
  number of frame widths for any angle the data produces — and **vertically** it
  obeys the rule strictly, which is why a drop's speed is a whole number of crossings
  per cycle rather than the continuous spread it looks like it could be.
- **Reduce motion is not uniform, and the asymmetry is deliberate.** Deck drift and
  sock flutter go off *entirely*, because the wind is told twice over. Precipitation
  is drawn **still** rather than dropped: it is the only thing in the scene that
  says it is raining, and a reduce-motion setting is a request about motion, not
  about content.
- All testable geometry is `internal` top-level pure functions in
  `SkyProfileGeometry.kt`, because this module has JVM tests only — the same
  arithmetic inlined into `drawWithCache` could only be looked at.

## Preview annotations

```kotlin
@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = UI_MODE_NIGHT_YES)
annotation class LightDarkPreview

@Preview(name = "Compact 360dp", showBackground = true, widthDp = 360)
@Preview(name = "Compact 360dp · font 2.0", showBackground = true, widthDp = 360, fontScale = 2.0f)
annotation class CompactWidthPreview

@Preview(name = "Phone", device = "spec:width=360dp,height=800dp,dpi=480", showBackground = true)
@Preview(name = "Tablet", device = "spec:width=1280dp,height=800dp,dpi=240", showBackground = true)
@Preview(name = "Desktop", device = "spec:width=1920dp,height=1080dp,dpi=160", showBackground = true)
annotation class DevicePreviews
```

Public rather than internal, because screens in `:app` need the same pairing and
one shared annotation is what keeps every preview in the project framed the same
way. `@DevicePreviews` covers phone, tablet and desktop breakpoints where navigation
suites and multi-pane scaffolds shift.

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
