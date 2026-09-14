# Building the app: design and task breakdown

The data and domain layers work. This document covers everything from there to a
finished, animated application. It supersedes milestones M3–M7 of [PLAN.md](PLAN.md),
which sequenced motion and polish into a final milestone; motion is now designed
once up front and applied as each screen is built, because retrofitting animation
onto finished screens produces decoration rather than behaviour.

Task IDs are stable. Refer to them directly ("do B4").

---

## 1. Where the code actually is

Verified against the source tree, not against the plan.

| Module | State |
| --- | --- |
| `:core:model` | **Complete.** `Airport`, `AircraftSpec`, `FlightRecord`, `FlightStatistics`, `FlightRules`, `Metar`, `Units`, `SurfaceKinds`, `FleetCsv` |
| `:core:database` | **Complete for what exists.** Both Room DBs, all DAOs, asset installer, fleet seeder |
| `:core:routing` | **Complete.** Index, codec, band index, great-circle, generator, `SearchScorer`, `FlightStatisticsCalculator`, `RouteArc` and `AirportSlotSearch` from Phase B, and `MapFrame`, `WorldOutline` and the two clippers from Phase B++ — all tested |
| `:core:designsystem` | **Complete for what exists.** Theme, motion, shapes, and thirteen components (D1 added `MonthHeader` and `StatSummaryStrip`). See [DESIGN-SYSTEM.md](DESIGN-SYSTEM.md) |
| `:core:network` | **Empty.** No sources at all. Phase F |
| `:feature:globe` | `FilamentProbe` only. Vulkan confirmed working, `FEATURE_LEVEL_3` |
| `:app` | Shell, navigation, the self-check, the Plan screen, the route detail, Settings, Logbook (with swipe-to-delete and a two-pane flight-detail layout), Fleet (list, detail, management), Stats (dashboard with 9 metrics, monthly chart, 2D visited network map, and timeframe filters), and Airports (browse plus detail, E1/E2) — reached from an icon on Plan's header, not the bar, see F10 |
| `:macrobenchmark` | **The instrument, from P2.** `FrameTimingMetric` over a scripted fling and `StartupTimingMetric` over a cold start, both on the `benchmarkRelease` variant, plus `BaselineProfileGenerator` from P1. See [the module README](../macrobenchmark/README.md) |

The three gaps the original plan did not cover — the index carrying no display
data (**G-a**), the missing `SearchScorer` and `FlightStatisticsCalculator`
(**G-b**), and the absent repository layer (**G-c**) — were closed by tasks A5,
A7/A8 and A6 respectively.

Two things Phase B added to `:core:routing`, both pure JVM and both unit-tested
because they are pure functions of a handful of numbers: `RouteArc`, which is
spherical interpolation plus an equirectangular projection, and
`AirportSlotSearch`, which ranks over the index's primitive arrays rather than
materialising 24,000 wrapper objects per keystroke.

Cold start is **169 ms median `timeToInitialDisplayMs`** against a 500 ms budget —
twelve controlled cold launches from `:macrobenchmark` on the SM-S942B. That
supersedes the ~370 ms this section carried from before Phase B, which was a
hand-picked median of `am start -W` on an emulator: different hardware, not a
regression that was fixed. It also retires the emulator-drift caveat that used to
be attached to it. What the figure still does not cover is the splash the app holds
*after* the first frame, until the index and the stored theme settle — see Phase P.

---

## 2. Design direction

The user's brief was "native modern android, modern 2026 design, completely free
with the design", plus "pretty animations". Two principles resolve most
individual decisions:

**The app is an instrument, not a toy.** Aviation data is dense and precise.
Tabular figures for numbers, real chart colours for flight rules, no decorative
imagery competing with data. Expressiveness comes from motion, shape and
hierarchy — not from ornament.

**Motion explains, it does not perform.** Every animation answers "where did this
come from" or "what just changed". A route card expanding into its detail view is
worth animating because it establishes continuity. A number counting up is worth
animating because it draws the eye to a value that changed. A logo spinning is
not. Anything the user will see more than ten times a session gets shorter and
quieter, not longer.

**Weather's exception got narrower, because the design got more rigorous.** This
paragraph used to license the weather glyph to be "playful and interactive — sun,
drifting cloud, falling rain/snow, drifting fog" as a bounded carve-out from the
two principles above. That glyph is gone, and the carve-out went with most of it.

`SkyProfile` replaced it with a measured atmospheric cross-section: altitude on the
Y axis, breakpoints at the flight-rules thresholds, every deck at its true base.
**That is not an exception to "the app is an instrument" — it is an expression of
it**, and it needs no special licence. It also earns the motion principle rather
than being excused from it: the decks drift at a rate taken from the reported wind
speed and faster at altitude, so the movement answers "how hard is it blowing", and
a calm field is visibly still. Motion explaining, not performing.

Two things remain genuinely playful, and they are the whole of what is left:

- **The windsock's drag.** Grabbing and swinging it is interaction for its own sake.
  It is defensible because a sock is a physical object a pilot already knows the feel
  of, and because it springs back to the truth — the toy cannot leave a false reading
  on screen.
- **The ambient drift of the decks.** Wind-driven, but the *choice* to animate air at
  all rather than draw it still is a pleasure decision.

Everything else about weather now lives under the main principles:
- The scene is driven entirely by the decoded METAR, and its unknown states are
  drawn as hatch precisely so that ornament can never stand in for data. The defect
  that prompted the redesign was ornament outvoting content.
- The Plan card's flight-rules badge stays the plain chart-figure chip it always was.
  A fifty-row list is exactly the density this principle exists to protect. The card
  glyph was **removed**, not restyled.
- Every other visual decision already made stands unchanged: chart colours for
  flight rules, tabular figures, the rejected map colouring and route-label icons
  from Phase B++. Nothing here reopens those.

See **[docs/WEATHER-PLAN.md](WEATHER-PLAN.md)** for the scene itself, and
`SkyColors`' KDoc for why weather scenery is per-theme while the flight-rules
colours are not.

### Visual language

- **Material 3 Expressive** as the base — `MaterialExpressiveTheme`, the expressive
  shape scale, `MaterialShapes` morphing on the generate FAB, `ButtonGroup` for the
  mode selector, `FloatingToolbar` for primary actions, `LoadingIndicator` and the
  wavy progress indicators rather than a plain spinner, emphasized type for hero
  numerics, and `ShortNavigationBar`/`WideNavigationRail` for navigation.
  This requires material3 **1.5.0-alpha26**, pinned above the Compose BOM: the
  BOM's 1.4.0 has none of it (absent or `internal`). See
  [API-GROUND-TRUTH.md](API-GROUND-TRUTH.md) for exactly what compiles and why the
  alpha is worth it. Screens must reach Expressive **through `:core:designsystem`**,
  never by importing a material3 Expressive symbol directly — that is what keeps an
  alpha bump to a one-module change.
- **Dynamic colour** from wallpaper, with a brand fallback seeded from avgas
  blue and runway-marking amber.
- **A "Cockpit" theme** — near-black with amber accents, for night flying. Not a
  third dark mode; a deliberate instrument-panel look.
- **Flight-rules colours are semantic and never dynamic.** VFR green, MVFR blue,
  IFR red, LIFR magenta are standard chart colours; recolouring them by wallpaper
  would be actively wrong. They live in their own `CompositionLocal` with
  tone-mapped container pairs that hold 4.5:1 in every theme.
- **Edge-to-edge everywhere**, with the globe running under the status bar behind
  a scrim.

### Motion language

| Token | Use | Spec |
| --- | --- | --- |
| `spatial` | Anything that moves or resizes | `MotionScheme.expressive()` spatial spring — damping 0.8 / stiffness 380, interruptible, slight overshoot |
| `effects` | Fades, colour, alpha | Expressive effects spring — damping 1.0 / stiffness 1600, critically damped, no overshoot |
| `enter` | List items appearing | 30 ms stagger, capped at 8 items, then instant |
| `emphasis` | Value changed, action landed | Count-up or pulse, ≤ 500 ms, once |
| `nav` | Screen to screen | Shared bounds where a real element persists, else fade-through |

Rules that apply everywhere:

- **Springs, not durations,** for anything a user can interrupt. A card mid-flight
  into detail must be able to reverse.
- **Predictive back is not optional — and it belongs to the `NavHost`.** A back
  gesture seeks the pop transition to the gesture's own progress, so the screen
  being returned *to* is composed and animates in underneath while the outgoing
  one animates out over it. Do **not** register a `PredictiveBackHandler` inside a
  destination to draw a local peek: it consumes the gesture before the host sees
  it, and the result is an outgoing screen sliding over a black background, which
  is a screen leaving rather than a preview of where you are going. Phase C shipped
  exactly that mistake and removed it. Spend the effort on transitions worth
  seeking instead. (A handler is still right for something the host does not own —
  a sheet, or an in-place state a back gesture should undo.)
- **Honour reduce-motion.** When `ANIMATOR_DURATION_SCALE == 0`, inertia, stagger
  and count-ups are disabled — not merely shortened.
- **Haptics on commitment, never on browse.** Mark-as-flown, swipe threshold
  crossed, generate complete. Not on scroll, not on selection.

---

## 3. Phase A — Foundations ✅ COMPLETE (`5a6274d`)

Nothing below can be built well until these exist. **A1–A3 are the reason the
animations will look coherent instead of assembled.**

All ten tasks are done and the acceptance criterion below is met. The resulting
API is documented in [DESIGN-SYSTEM.md](DESIGN-SYSTEM.md) — read that, not this
table, when building a screen.

Three things learned while building it, worth carrying forward:

- **material3 1.4.0 has no usable Expressive surface**, so it is pinned to
  `1.5.0-alpha26`. Screens must reach Expressive only through
  `:core:designsystem`; that containment is the entire mitigation for the alpha.
  See [API-GROUND-TRUTH.md](API-GROUND-TRUTH.md).
- **`minSdk` is 35** (it was 36 until Phase C closed), so no `SDK_INT` guards anywhere.
- **The flight-rules palette needed retuning.** Checking 4.5:1 text contrast was
  not sufficient: at the conventional tone-90 the five pastels compress toward
  white, and IFR and LIFR sat 0.05 apart in normalised RGB — two near-identical
  pinks for "below minimums" and "well below minimums". Contrast against the
  background was fine; contrast against *each other* was the defect.
  `FlightRulesContrastTest` now enforces both.

**Reviewed** in `1260284`: eight findings, all fixed — inset ownership under a
navigation rail, a permanently-terminal index failure with no retry path, a
`ContentObserver` registered per skeleton box, entity mapping on the main thread,
two hardcoded English strings, and two stale comments.

**Open:** startup has not been re-verified since those fixes. The emulator drifted
badly during that session — the same unchanged APK read a 424 ms median and then
~712 ms — so no comparison taken then means anything. Re-measure on an idle host
with commits interleaved, or via `:macrobenchmark`. One change is worth checking
specifically: reduce-motion is now resolved in `FlightPlannerTheme`, which puts a
`Settings.Global` read and a `ContentObserver` registration on the startup path.
Small, but it was not there before. The app now also carries a baseline profile of
its own, on top of the AndroidX libraries' merged one, which P1 measured at 25 ms
of cold start — so a startup figure taken before P1 is not comparable to one taken
after. See Phase P.

| ID | Task | Notes |
| --- | --- | --- |
| **A1** | Colour system in `:core:designsystem` | Dynamic colour, brand fallback, Cockpit theme, light/dark. `FlightRulesColors` as a `CompositionLocal` with contrast-checked container/on-container pairs |
| **A2** | Typography and shape | Tabular figures for all numerics (ICAO codes, distances, runway lengths). Expressive shape scale |
| **A3** | Motion tokens | One file exposing the five tokens above. Screens never call `spring()` directly — they name a token, which is how the motion stays consistent |
| **A4** | Shared atoms | Skeleton loader, empty state, error state, section header, value chip, flight-rules badge. Each with a Compose preview |
| **A5** | Airport display-data bridge | New DAO query fetching display rows by id set; a repository that batches the handful of visible rows. Plus a **lazily built name index** for search, loaded on a background scope *after* first frame so it never touches the startup path. Resolves **G-a** |
| **A6** | Repository layer | `FleetRepository`, `LogbookRepository`, `AirportRepository`. Entity↔domain mapping, `Flow` throughout. Resolves **G-c** |
| **A7** | `SearchScorer` in `:core:routing` | Field-for-field port of `TableItem::search_score_optimized`: code match 2, name/manufacturer/variant/category/date/runway 1, score-descending. Bounded min-heap for top-K. Resolves half of **G-b** |
| **A8** | `FlightStatisticsCalculator` | Pure-Kotlin reference mirroring `StatsAccumulator`, including every tie-break rule. SQL aggregates in the DAO are the production path; this cross-checks them in tests. Resolves the rest of **G-b** |
| **A9** | Navigation scaffold | Type-safe `@Serializable` routes in a sealed hierarchy, `NavigationSuiteScaffold`, edge-to-edge, `PredictiveBackHandler` |
| **A10** | `AirportIndexProvider` | Process-scoped singleton, lazily `async`-built, warmed from `Application.onCreate` so it overlaps first-frame inflation. `SplashScreen.setKeepOnScreenCondition` capped at ~800 ms |

**Done when:** the app launches into an empty themed shell with working
navigation, both themes render correctly, and `:core:routing` tests cover the
scorer and the statistics calculator. ✅ — cold start ~370 ms against the 500 ms
budget.

---

## 4. Phase B — Plan screen ✅ COMPLETE

The heart of the app. The desktop's entire 250 px sidebar collapses into two
chips and one segmented control.

| ID | Task | Notes |
| --- | --- | --- |
| **B1** | `PlanViewModel` | One `Selection` value under `collectLatest`, so a changed selection cancels the in-flight batch — the clean replacement for the desktop's `AtomicU64` generation counter. Appends collect *inside* that coroutine, so a stale "load more" cannot survive a mode change |
| **B2** | Route card | Aircraft + category, `EHAM → RJTT` in tabular figures, distance, estimated time, per-end runway, dep/dest flight-rules slots (reserved, empty until Phase F) |
| **B3** | Great-circle sparkline | Real spherical interpolation in `RouteArc`, projected equirectangularly and drawn in a Compose `Canvas`. No GPU, no globe |
| **B4** | Departure and aircraft pickers | One `ModalBottomSheet` serving both, with ranked type-ahead |
| **B5** | Mode selector | Any · Not flown · This aircraft |
| **B6** | Infinite scroll | Appends 50 on approach to the end; pull-to-refresh regenerates |
| **B7** | ~~Generate FAB~~ | **Cut.** See below |
| **B8** | Swipe actions | Right = mark flown, writing both the logbook row and the airframe's flag, with undo; left = replace, which keeps the airframe and the departure and generates a new destination into the gap |
| **B9** | Empty, loading, error states | Delayed skeletons, distinct empty vs. no-match vs. failure states |
| **B10** | Plan screen motion | Staggered entrance for the first screenful, spring placement, swipe reveal proportional to commitment |

**Done:** generates in all three modes, with and without a locked departure,
scrolls and pages, swipes to mark flown with a working undo, and the flight lands
in the logbook. Verified on a Galaxy S26 and on an emulator at the same geometry.

### What changed from the plan, and why

**B7 was deleted rather than built.** The FAB generated a batch on tap and
appended one on long press. Once the screen generated on open, pull-to-refresh
regenerated and the list appended by itself, both of those had no work left — so
it was a permanent 56 dp obstruction over the content, and it flickered in on
launch a moment before the routes it offered to generate had already arrived.
`FlightShapes.GenerateFabMorph` stayed in the design system for a phase with
nothing using it, and the design review deleted it: an unused promise about what
the app looks like drifts. `MorphShape` itself remains — it is a general
primitive, not a named morph for a cut component.

**The screen generates on open.** It was built to start empty and wait, on the
reasoning that generating unprompted spends startup budget on work nobody asked
for. That reasoning was wrong in use: this is the launch destination and its
whole purpose is a list of routes, so an empty one asks the user to press a
button to get the thing they opened the app for.

**`ButtonGroup` could not be used (B5).** It crashes in `1.5.0-alpha26` —
deterministically, at the default font scale, on an ordinary phone. `ModeSelector`
wraps the stable `SingleChoiceSegmentedButtonRow` instead. That substitution was
one file because no screen ever named `ButtonGroup`, which is the containment
rule paying for itself. See [API-GROUND-TRUTH.md](API-GROUND-TRUTH.md).

**Settings left the navigation bar.** Six destinations on a 360 dp window is
60 dp each, below what Material specifies. It is also not the same *kind* of
thing as the other five, so it moved to the app bar.

### Four defects worth remembering

Each of these was found on a device and none would have been caught by a unit
test or a preview.

- **A swipe must act on `settledValue`, not `currentValue`.** The latter moves
  during the drag, so the action fired mid-gesture and dragging back could not
  cancel it. The stock `onDismiss` callback is better still — it is an event
  rather than a state.
- **`rememberSwipeToDismissBoxState` is saveable and keyed by list item.** A row
  restored by an undo comes back *still dismissed*, so an observer over its state
  reads that as a fresh gesture and re-runs the action the undo just reversed —
  an undo that undoes itself, once per tap, forever.
- **An undo must restore the list before it touches the database.** Behind two
  writes it reads as a second failure.
- **Edge-triggered prefetch cannot recover from a dropped request.** "Fire once
  when the end comes into view" asks once, is refused because a batch is running,
  and then waits at the bottom of the list for a scroll that never comes.

### Previews

Every screen component carries `@LightDarkPreview`, and anything with a
horizontal layout also carries `@CompactWidthPreview` — 360 dp, plus the same at
font scale 2.0. Both annotations are in `:core:designsystem`.

360 dp is not a nicety: it is what a 1080 × 2340 phone at 480 dpi reports, which
is the most common phone width there is, and it is the width at which this app's
dense rows first overflow. The tooling's default preview width is wider than any
phone this app will run on, so previewing only at the default hides exactly the
problems worth catching — the route card shipped with a clipped runway figure
because of it.

`PlanScreen` takes a Hilt ViewModel and cannot be rendered by the tooling, so the
previews target `PlanControls` and `PlanContent`. That is not a workaround; it is
why those two are stateless. Between them they cover the four states that are
awkward to reach by hand: a failed index, an empty fleet, a filter combination
that matches nothing, and "this aircraft" with no aircraft chosen. Sample data is
in `PlanPreviewData`, built from real coordinates through the real `RouteArc`, so
a preview shows what the screen will actually draw.

---

## 4a. Phase B+ — an immersive Plan screen ✅ COMPLETE

The screen used to sit inside its chrome: a top app bar, then controls, then a
list that stopped politely above the navigation bar.

Every part of that was already Material 3 Expressive — the app bar, the segmented
control, the cards, the shape scale, the springs. **The dated thing was the frame,
not the components.** Boxing content between two opaque bars and padding the
*container* by the system insets is the structure Android used before edge-to-edge
and predictive back became platform defaults, and a current component library
assembled that way still reads as an old screen. On a 360 × 780 dp phone it also
spends about a fifth of the height on bars while the content the screen exists to
show is squeezed between them.

**Built.** Verified on the emulator at 1080 × 2424: at rest the title, mode
selector and filter chips float over the list on a transparent container; a short
scroll takes the title; sustained scrolling takes the controls and the navigation
bar together; a small upward scroll brings both back. Cards pass behind an empty
status bar and an empty gesture area. What follows is the design as built, then the
things that turned out to matter.

### The design

Content runs edge to edge under empty system bars, and there is **almost nothing
left to get out of the way**: the screen's name and its controls are the list's own
first item, so they leave by being scrolled, and the navigation bar is the only
thing that hides on a signal.

```
 scrolled to top                    scrolling down
┌─────────────────────┐            ┌─────────────────────┐
│ ▓ 8:04    ▓▓▓ ░░░░  │ status     │ ▓ 8:04 ─○ KPDC ░░░  │  card under the clock
│ Plan            ⚙   │ ┐          │ ╰─────────────────╯ │
│  All  Not flown·116 │ │ header   │ ╭─────────────────╮ │
│ ╭─────────╮╭──────╮ │ │  = the   │ │ SSGE ──○ SJGY   │ │
│ │DEPARTURE││AIRCRA│ │ │  list's  │ ╰─────────────────╯ │
│ │EHAM     ││737-6…│ │ │  item 0  │ ╭─────────────────╮ │
│ ╰─────────╯╰──────╯ │ ┘          │ │ 0NM7 ──○ KRCA   │ │
│ ╭─────────────────╮ │            │ ╰─────────────────╯ │
│ │ KMTJ ──○ KPDC   │ │ cards      │ ╭─────────────────╮ │
│ ╰─────────────────╯ │            │ │ KDEN ──○ KSLC   │ │  card in the gesture area
│ ▓ Plan Log Fleet ▓  │ nav        │      (nav gone)     │
└─────────────────────┘            └─────────────────────┘
```

**Two things move, and only one of them is animated.**

- **The header scrolls, because it is content.** Title, settings and controls in
  one item at the top of the list. It moves at exactly the speed of the finger,
  with no threshold to wait through, no container to fade in and no bottom edge to
  slice a card on. It does not come back on an upward flick — **B20** is the way
  back, and the reason that task exists.
- **The navigation bar hides on a sustained scroll and returns on a small upward
  one.** It lives in `FlightPlannerApp`'s `NavigationSuiteScaffold`, one level above
  the screen, so a screen cannot hide it by itself: the signal travels *up* through
  a small piece of shared state, which is what makes it work the same way for the
  Logbook and Fleet lists in Phase D.

**Content goes under the bars, not merely to the edges.** The list's first item
scrolls up behind the status bar, and its last scrolls down through the gesture
area once the navigation bar is away. That means `contentPadding` carrying the
inset heights rather than the *container* being padded by them — the distinction
the whole thing turns on, and the opposite of what `PlaceholderScaffold` does. The
bars themselves stay **empty**: no scrim, no translucency, nothing painted behind
the clock (see "The parts that bit").

### Tasks

| ID | Task | Notes |
| --- | --- | --- |
| **B15** | Collapsing title | ✅ **as content, not a bar.** The title is the list's first item and scrolls away with it. Built as a collapsing `TopAppBar` first — see below |
| **B16** | Retracting controls | ✅ **as content, not chrome.** Same item. Built as a threshold-driven overlay first, and that is the part that had to be thrown away |
| **B17** | Hoisted scroll state | ✅ `AppChromeState` + `rememberChromeScrollConnection` in `ui/chrome`. One signal, one place that decides when the navigation may leave. Built for Phase D's lists as much as for this one |
| **B18** | Hiding navigation suite | ✅ `NavigationSuiteScaffold`'s own `state` — it animates the suite out and stops consuming the bottom inset while away. Bottom bars only; a rail never hides |
| **B19** | Insets as content padding | ✅ `ContentInsets` reports what ancestors consumed and the list pads the remainder as `contentPadding` |
| **B20** | Reselect scrolls to top | ✅ Tapping the active section's navigation item returns the list to the top. `NavigationReselect`, an event with no replay |

### How it is put together

`PlanScreen` is a `Box`, not a `Scaffold`. The list fills the window and the
snackbar is one aligned modifier. A scaffold's content padding would have to be
taken and then deliberately ignored, and content padding that must be ignored is a
sign the layout is not a scaffold's shape. (Lint says so too:
`UnusedMaterial3ScaffoldPaddingParameter`.)

**The title and the controls are the list's first item.** Not a bar over it. That
one decision is what makes the screen feel right, and it was arrived at the hard way
— the first build was a floating chrome that faded its container in on the first
scroll, dropped the title at one threshold and the controls at another. It was
rejected on the device, in three parts that were all the same mistake:

- **It could not feel attached to the finger.** A threshold means nothing happens
  for 48 dp and then a spring runs on its own clock. Chrome that retracts *after*
  the scroll reads as lag no matter how quick the spring is.
- **Its container had to appear from nowhere.** An overlay needs a background the
  moment a card is behind it, so the top of the screen tinted itself as soon as the
  list moved — a highlight nobody asked for.
- **Its bottom edge cut the cards in half.** An opaque block over a scrolling list
  ends in a straight horizontal line, and a card sliced by one looks broken rather
  than layered.

As an item, all three stop existing rather than getting fixed: it moves at exactly
the speed of the finger because it *is* the content, it needs no background because
nothing passes behind it, and it has no edge because there is nothing to have an
edge against. A measured chrome height, a reserved band of top padding, two
thresholds and two transitions all went with it.

**What it costs** is that the controls no longer return on an upward flick from deep
in the list. **B20** is the answer and the reason it exists: tapping Plan in the
navigation bar while already on Plan returns the list to the top, which is what that
tap means everywhere else on Android and which the old design had no use for.

The navigation bar is the one piece of chrome left. It is at the other end of the
screen, it overlays nothing, and it must stay reachable — so it keeps the threshold
(48 dp down to hide, 6 dp up to return) and the trap guard that shows it whenever
the list is at its top or cannot scroll at all.

### The parts that bit

- **A padded container outlives its children.** The chrome block padded itself by
  the status-bar inset with its background applied outside that padding, so when both
  halves retracted it kept a bar's worth of height and painted it — a solid strip
  across the top of the screen, which is precisely the opaque status bar the phase
  exists to remove. The padding has to be *inside* whatever hides. **This shipped and
  was caught on the device, not by lint, a test or a preview.**
- **Scrims lose to transparency.** A short gradient of the page colour behind each
  bar was built to keep an ICAO code from colliding with the clock. It is invisible
  over the page's own background by construction — and on the device, with a card
  behind it, it reads as an opaque bar. Removed. The bars are now genuinely empty and
  `FlightPlannerTheme` sets `isAppearanceLightStatusBars` /
  `isAppearanceLightNavigationBars` **from the scheme it resolved**, not from the
  system night setting, which is what keeps the clock legible in Cockpit and under a
  forced Light or Dark once Phase E offers them.
- **The pull-to-refresh indicator needed moving.** Its default `TopCenter` was fine
  under a scaffold and emerges from behind the clock in a full-bleed box, so it is
  offset by the chrome's height.
- **Insets, as predicted.** Which edge belongs to whom now varies with the chrome
  state as well as the width, so nothing could be hard-coded:
  `Modifier.onConsumedWindowInsetsChanged` plus `WindowInsets.exclude` turn "what my
  ancestors did not take" into the `PaddingValues` a list needs. See `ContentInsets`.
- **Reduce motion needed nothing.** Every part of this is a spring — the two
  `AnimatedVisibility` blocks and the suite's own animation — and Compose collapses
  springs at `ANIMATOR_DURATION_SCALE == 0`. Nothing here is staged or infinite, so
  `LocalReduceMotion` is not consulted.

### The controls themselves — "the flight strip"

Redesigned with the immersive layout, because the segmented row and the two icon
chips were the generic half of an otherwise specific screen. They now use the
grammar the route cards already use: a letter-spaced caps field label over a short
identifier in tabular figures, over one line of plain text.

```
  All    Not flown · 116    This aircraft          ← scope: which pool
 ┌──────────────────────┐ ┌──────────────────────┐
 │ DEPARTURE            │ │ AIRCRAFT             │  ← constraints: the envelope
 │ EHAM                 │ │ 737-600              │
 │ Amsterdam Schiphol   │ │ 3,010 NM · 6,900 ft  │
 └──────────────────────┘ └──────────────────────┘
```

**A set field reports the constraint it imposes.** "3,010 NM · 6,900 ft" is the box
every route in the list was generated inside — the fact that explains why the list
looks the way it does, and one nothing else in the app shows. That line is the
reason the redesign is worth more than a reskin.

**One selection language across both rows:** hairline outline is "no constraint",
filled is "constrained". Both read on any surface, which matters because this is
drawn on the page background rather than on a chrome of its own.

**Two accessories removed.** The leading icons — a `DEPARTURE` label and a departure
icon say the same thing twice, and the icon costs the width the value needs — and
the segmented row's checkmark, since the fill already says which option is on.

**Two copy fixes fall out.** "Any" was doing two jobs one row apart: a scope in the
first row and an unset value in the second, so the scope became **All**. And the
not-flown count is now *drawn*: it was hidden in a `contentDescription` only because
equal thirds ellipsised "Not flown 116" into a wrong number, and chips are sized to
their own label. The mode row also wraps at font scale 2.0 where a segmented row
truncated.

The airframe is named by its **variant** — "737-600", not "Boeing 737-600", which
truncates to "Boeing 737-6…" and spends the field on the half every 737 shares.

### Divergences from the plan above

- **The settings action leaves with the title**, because both are content now. The
  navigation bar already names the section, so a permanent 64 dp bar to hold one
  icon and a word the user can already see was the wrong trade.
- **Cards stop above the navigation bar rather than passing behind it.**
  `NavigationSuiteScaffoldLayout` measures its content to the window minus the bar,
  so the "behind" half of B19 applies to the status bar and to the gesture area once
  the suite is away. Taking it further would mean drawing our own bottom bar over
  full-height content — a second layout to keep in step with the rail, and a
  per-frame relayout traded for a translation — for a card edge that is only visible
  while the bar is up. Not worth it.

**Done when:** scrolling down leaves nothing but cards on screen, scrolling up
brings the navigation back immediately, no card is ever clipped by a system bar, and
the whole thing behaves the same at 360 dp and on a tablet rail. ✅ — verified on the
emulator at 1080 × 2424, at font scale 1.0 and 2.0, in both the unset and the
constrained state. The tablet rail is reasoned rather than measured: the suite is
only hidden for the two bar types, so a rail cannot vanish. Predictive back was not
re-verified on the truly edge-to-edge window and remains open.

---

## 4b. Phase B++ — the world under the route ✅ COMPLETE

**B11–B14 are built and measured on device. ✅ COMPLETE.** The immersive layout
changes the card's frame — height, and how much of it is ever covered by chrome —
so the map is designed against the final shape rather than redesigned twice.

The sparkline proves a route has a *shape*. It does not say where on Earth that
shape is: a bowed arc over the Pacific and a bowed arc over the Atlantic draw
identically. Putting the world under it turns an abstract curve into a place,
which is most of what a route card is for.

This is a redesign of the card, not an addition to it. The map becomes the card's
**background**, full bleed; everything the card already says is drawn over it.

### The design

```
┌────────────────────────────────────────────────┐
│                            ╭─╮                 │  ← land, filled, whisper contrast
│  Boeing 777-300ER      Wide-body ╰──╮          │
│                       ╭────────╯    ╰╮         │
│  EHAM  ●───────────────╯              ╲        │  ← route, the only strong graphic
│  RWY 12,467 ft                         ○  RJTT │
│                                     RWY 11,811 │
│  ╭──────────────╮ ╭──────────────╮             │
│  │ DIST 5,180 NM│ │ ETE  10h 34m │             │  ← translucent, map shows through
│  ╰──────────────╯ ╰──────────────╯             │
└────────────────────────────────────────────────┘
```

**Two numbers are settled, not open:** land at **8 %** of `onSurface` with its
coast at **16 %**, and a card height of **180 dp**. Both were chosen against the
alternatives — a mid-contrast map that reads unmistakably as a map but needs a
scrim, and card heights of 150 dp and 220 dp — and both alternatives were
rejected. Anything else is tuning on a device, not a redesign.

**The map is texture, not imagery.** This is the decision the whole design rests
on. A photograph or a full-contrast map behind dense figures destroys them, and
§2's principle is explicit — *no decorative imagery competing with data*. So land
is a fill at 8 % of `onSurface` with its coast stroked at 16 %: enough for a
silhouette to be recognisable, far too little to fight text. Body copy keeps
essentially its full contrast against the card, which means **no scrim is
needed** — and a design that needs no scrim is simpler than one that hides its
problems behind a gradient.

**180 dp, so about two cards fill a 360 × 780 dp phone.** A 150 dp card keeps
today's density but leaves a map band so short that a long route shows almost
nothing but ocean; 220 dp makes the map the point of the card and drops a list of
fifty to a card and a half per screen, which is slow to scan.

**The route is the only saturated thing on the card.** Because everything else is
either text or whisper-grey, a 2.5 dp `primary` stroke reads as the subject
immediately. It is drawn as a *casing*: a wider stroke in the card colour
underneath, then the primary stroke on top — the technique aeronautical and road
charts use to keep a line readable wherever it crosses. Departure stays a hollow
ring and destination a filled dot, both with the same casing, both enlarged now
that there is room.

**The chips are translucent, the text is not.** `ValueChip` gets its container at
around 70 % alpha so the coastline passes faintly behind the figures — that is
what makes the content read as a layer over the map rather than a panel bolted to
it. Text itself never goes translucent; a figure at 70 % is just a figure that is
harder to read.

**Frame the window to the card's aspect, not to a square.** `RouteArc` currently
normalises into a unit box and lets the caller stretch it, which is fine for an
abstract arc and wrong for a map — stretching an equirectangular projection to a
2:1 card squashes every coastline. The frame has to be computed *from* the
canvas's aspect ratio, so the projection is uniform and land keeps its shape.

**Minimum span, not the whole world.** A world map makes every European route a
two-pixel squiggle. The window is bounded by the route with padding and a floor
of roughly 25°, so a short hop still shows recognisable coast around it.

### Anti-aliasing

Compose already draws paths anti-aliased; the current line looks faceted for a
different reason — it is a 24-segment polyline, and 24 segments that were
invisible across 120 dp are plainly visible across a full card. The fix is
sampling and joins, not a flag:

- sample the arc against the canvas width rather than at a fixed 24
- `StrokeJoin.Round` and `StrokeCap.Round`, which is also what makes the casing
  read as one ribbon rather than as stacked segments
- verify on a 3× zoomed screenshot, not by eye at 1×

### Tasks

| ID | Task | Notes |
| --- | --- | --- |
| **B11** | World outline asset | ✅ `:tools:worldmap` builds `app/src/main/assets/maps/land.outline` — 122 rings, 4,601 points, 18.9 KB. Natural Earth 1:110m land polygons, simplified and quantised to a prebuilt binary asset, exactly as the airport index is. Built by a pure-JVM tool; never parsed from GeoJSON on device. Source and format settled below |
| **B12** | `MapFrame` in `:core:routing` | ✅ Plus the two clips it projects through — Sutherland–Hodgman for the fill, Liang–Barsky for the coast — and `RouteArc.sampleGeographic`, which hands back degrees instead of a self-normalised box, and `ProjectedLand`, the coastline clipped and projected for one card. A window — centre, span, aspect — that both the coastline and the arc project through. Replaces `RouteArc`'s self-normalising output, which cannot be shared by a second layer |
| **B13** | `RouteMap` in `:core:designsystem` | ✅ `RouteSparkline` is deleted, and so is `RouteArc.normalisedPath` with it. Land fill, coast stroke, cased arc, cased endpoints, in that order |
| **B14** | Card recomposition | ✅ Looked at in both themes on an emulator and on a phone, at font scale 1.0 and 2.0. Map to the card's background layer, content over it, chips translucent, height raised to fit a map |

### Findings for B11, so the next session does not rediscover them

**Source.** `https://raw.githubusercontent.com/nvkelso/natural-earth-vector/master/geojson/ne_110m_land.geojson`
— reachable, returns 200. Natural Earth is **public domain**: no attribution
required and nothing to add to a licences screen, which is the reason to prefer it
over OpenStreetMap coastline extracts for this job. 1:110m is the coarsest of the
three Natural Earth scales and still finer than a 180 dp card can show, so
simplification is about *file size and segment count*, not fidelity. Take `land`,
not `coastline`: land is closed polygons, which can be filled; coastline is open
lines, which cannot.

**Where the tool goes.** A new pure-JVM `:tools:worldmap`, not `:tools:airportdb`.
The existing tool is named for what it produces and already does two jobs —
`Extract.kt` builds the database and `IndexFromDb.kt` builds the airport index —
so adding coastlines to it would make its name a lie. Follow its shape though: a
`Main.kt` with a documented `SOURCE_URL`, input read from a data directory rather
than fetched at build time, and a `Verify.kt` that asserts the output before it is
committed. The asset lands next to the airport index under `app/src/main/assets/`.

**Format.** Same reasoning as `AirportIndexCodec`: a prebuilt binary blob read
with one file read and no parsing. Quantising a longitude to 16 bits gives ~600 m
precision, far finer than a card can show. At 4 bytes per point, a few thousand
points is under 20 KB — a rounding error against the 30 MB airport database, and
it must stay one. Rings need explicit lengths so the reader can `moveTo`/`lineTo`
without a sentinel value that a coordinate could collide with.

**What must not happen.** No GeoJSON on device, and nothing added to
`Application.onCreate` or a `@Singleton` constructor. The airport index already
demonstrates how that goes: rebuilding it from SQLite rows measured 646 ms against
a 500 ms cold-start budget and was deleted. Load the outline lazily, on first use
by the Plan screen, on a background dispatcher.

**Per-frame cost is the real risk, and it is larger than it was.** A full-bleed
map shows far more coastline than a 120 dp sparkline did: eight visible cards
stroking a thousand segments each is 8,000 segments a frame. Three mitigations, in
order — clip and project each route.s coastline **once** (done, though in
`drawWithCache` on the UI thread rather than beside `RouteArc` on a dispatcher,
because the window needs a measured aspect ratio); build the `Path` once per row
in `remember`, never per frame; and if that is still not enough, snap frames to a handful of zoom
levels and cache them as `ImageBitmap`s. Measure with `dumpsys gfxinfo` while
flinging, before and after.

> **How this turned out, from P3.** The first two mitigations were built and are
> what ships. The third was built and rejected: an `ImageBitmap` per card at full
> resolution is *slower* than stroking the paths, because allocating 2.3 MB every
> time a card enters composition costs more than the per-frame stroking it saves,
> and the reduced resolutions that are faster are visibly soft. The estimate above
> was also wrong about where the cost sits — it is per-frame rasterisation, not the
> clip and projection, which cache and cost ~0.1 ms. See Phase P.

### What B11 turned out to be

The findings above held. Three things they did not predict, and the numbers as
built:

**The source is already coarse, so simplification is not where the size went.**
`ne_110m_land` is 128 rings and 5,143 points — Douglas–Peucker at 0.05° (about
5.5 km, a third of a pixel at the closest a card zooms) removes only 10 % of them.
The file is small because a point is 4 bytes, not because the geometry was thinned:
4,601 points is 18.9 KB, inside the 20 KB the format was designed around. Six rings
were dropped for being under 0.75° across, which is a couple of pixels.

**Antarctica legitimately steps 360° in longitude.** Natural Earth clips its
polygons at the antimeridian, so no ring crosses the seam — except that Antarctica
runs along ±180 down to the pole and back, giving one segment from (180, -90) to
(-180, -90). That is the bottom edge of the map, not a coastline, and a
seam-crossing check that does not exempt it fails on correct data. Anything
projecting these rings has to expect it.

**The verifier asks where places are, not whether bytes decode.** Eleven
land-or-water probes — the Sahara, central Siberia, the Amazon, the Australian
interior, Kansas, East Antarctica, and five open oceans — answered through the same
even-odd rule the renderer fills with. A transposed coordinate pair, a flipped sign
or a ring table off by one all decode cleanly and all still look like *a* planet;
only asking whether the Pacific is wet distinguishes them. The probes are far from
any coast on purpose: this is a check on the world's orientation, not an audit of a
shoreline.

### What B12 settled

**The projection has a standard parallel.** Longitude is scaled by the cosine of
the window's centre latitude before anything else happens, because plate carrée
stretches by `1 / cos(latitude)` — 1.6× at Amsterdam, which makes Britain fat and
the North Sea look like an ocean. The factor is floored at 75° so a polar window
is drawn slightly stretched rather than hundreds of degrees wide. This is the
difference between the sparkline, which was allowed to distort because its whole
job was the *shape* of a curve, and a map, whose job is a recognisable place.

**Land is not clipped to the window, only culled by it.** Clipping a filled
polygon runs its boundary along the window's edge, and the coast stroke would then
draw a hairline box around every card. Rings are rejected by their bounding box —
122 rings, three of which a card typically shows — and the canvas clips the
overspill. If it ever costs too much, the next mitigation is caching whole frames,
not clipping.

**A ring is offered at ±360°.** A Pacific crossing is framed around 190°E, and
nothing in the outline is stored there; a window just west of the seam has to be
met by land at +179°. Both shifts are tried and only one can match, since no ring
is wider than a turn.

**A route to itself framed a NaN.** Coincident endpoints have no extent, so
scaling them up to the 25° floor multiplies zero by infinity. It failed at the far
end of the frame, where nothing said an airport had been routed to itself — the
kind of defect the pure-JVM layer exists to catch in milliseconds.

### What B13 and B14 turned out to be

**It works, it cost frames, and clipping bought most of them back.** Every number
below is the `benchmark` variant, eight identical flings per run, `dumpsys gfxinfo`
reset before each.

The first build culled rings by bounding box and left the canvas to discard the
rest. On the emulator that measured 30 % janky frames and a 27 ms p50 against 12 %
and 18 ms for the same build with the map switched off — about 9 ms a frame, and
the loss showed up as *frames not produced at all*, 90 against 181 for the same
input.

Then the rings were clipped to the window, and the comparison moved to a real
device (SM-S942B, 1080 × 2340 at 480 dpi, 120 Hz), interleaved three pairs deep
because the emulator's numbers drift further between runs than the change being
measured:

| | p50 | p90 | janky |
| --- | --- | --- | --- |
| Unclipped map | 8 / 8 / 9 ms | 11 / 11 / 12 ms | 10 % / 16 % / 10 % |
| Clipped map | 6 / 6 / 6 ms | 8 / 8 / 9 ms | 6 % / 8 % / 6 % |
| No map at all | 5 / 5 / 5 ms | 5 / 5 / 5 ms | 1 % / 1 % / 1 % |

So the map costs about **1 ms a frame** on hardware, half what it cost unclipped,
and jank is within a few points of a card with nothing drawn behind it. Two things
this exercise settled beyond the numbers:

- **The emulator was measuring itself.** 9 ms there is ~3 ms here, and its jank
  figures for an *unchanged* APK ranged from 12 % to 47 % across one working
  session, which is wider than every effect measured. Fill-rate on a software
  raster is not a phone's GPU. Frame numbers for this app come from the device.
- **Clipping has to be two operations.** A polygon clipped to a rectangle has the
  rectangle's edges in its boundary — right for a fill, and stroking it would draw
  a hairline box around every card. So the fill is Sutherland–Hodgman and the coast
  is the original segments trimmed by Liang–Barsky into *open* polylines. Verified
  by looking at a 2× crop of a card whose land runs off three sides.

**A background with no content measures zero.** The map was written with
`fillMaxSize()` inside the card's `Box`, which is unbounded vertically in a lazy
list, so it resolved to nothing and the first build shipped blank cards that
compiled, laid out and drew perfectly — the map simply was not there. A background
that takes no part in sizing wants `matchParentSize()`. This is the fourth time in
this project that "a green build says nothing about a UI change" has been the
lesson.

**A third of the chip row was spent on an effect the alpha already provided.** The
chips were given two thirds of the width so the coast could run out from under
them rather than ending on a straight edge. On a 360 dp phone at the *default*
font scale that leaves 122 dp a chip, and "DIST 2,847 NM" wrapped to two lines
inside it — which only showed up on the device, because the emulator's routes
happened to be shorter. The chips are translucent, so the coast passes behind them
regardless; the gap was removed. Past font scale 1.3 they stop sharing a line at
all and stack, because two chips cannot hold a figure between them at that size.

**The empty ocean is honest and looks like a bug.** A route in the American
Midwest frames 25° of land-free interior, so the card is genuinely blank above the
codes while a Mediterranean or Baltic route reads immediately. That is the design
working as specified — the map says where you are, and some places have no coast —
but it is worth knowing before someone reports it.

**The flight-rules slot moved above the code.** It was a reserved row *under* each
runway figure, which in a 180 dp card spends vertical space on absent weather.
Above the code it costs nothing: the codes sit on a line that is already tall
enough, weather is a property of the airport it now sits with, and Phase F's badge
will fade in without moving anything.

### What a code review found afterwards

A high-effort review over the whole day's diff, run once the fifteen fixes were in.
Six findings, all fixed; three are worth carrying forward.

**The card's weighted spacer was dead code, and the layout only looked right by
luck.** `Box` relaxes its children's minimum constraints by default, and a lazy
list hands its items an unbounded maximum height — so the content `Column`
measured against `0..Infinity`, where `fillMaxSize` does nothing and a weight
resolves to zero. The card looked correct anyway, because its content is 197 dp
against a 180 dp floor: there was never any slack to distribute, so nothing
visibly collapsed. `propagateMinConstraints = true` makes the floor real.
Confirmed by measurement rather than by eye — `uiautomator dump` reports the card
bounds, and raising the floor to 320 dp moved nothing until the flag was set, then
put the whole 123 dp exactly where the KDoc says it goes.

**A ring can reach the window at two whole turns, not one.** `lonShiftFor`
returned the first shift that overlapped and the comment said only one could
match "because no ring is wider than a full turn" — which is false for the one
ring the format explicitly allows to span −180…180. A far-southern window
straddling the seam matched at both −360 and 0, took −360, and drew a sliver of
Antarctica instead of the coast. It now emits every matching shift, with a test
that fails against the old behaviour (`expected:<2> but was:<1>`).

**A KDoc asserted a guarantee the code does not provide.** `projectOutline` said
it "runs once per route, off the main thread"; its only caller runs it inside
`drawWithCache`, on the UI thread. That was the design intent, and it cannot be
met until the projection has an aspect ratio before the card is measured. The
KDoc now says where it actually runs — a comment that describes the intended
architecture rather than the built one is worse than no comment, because it is
trusted.

The rest were small: translator comments still naming `%1$d` after the specifiers
became `%1$s` (a translator following them would have crashed the app at runtime),
an unused import, a dead string, and a formatting trap in the fade modifier that
parsed correctly and read wrongly.

### Rejected

- **Labels anchored to the endpoints on the map.** It looks wonderful in a single
  mockup and collides unpredictably in a list, because the endpoints land
  somewhere different on every card. The codes stay at the card's edges, which is
  also what keeps a column of cards scannable.
- **Country borders as well as coastline.** Noise at this size. Land silhouette is
  what makes a place recognisable; borders are a later flag, not a redesign.
- **Political or terrain colouring.** It cannot survive the Cockpit theme or
  dynamic colour, and it is exactly the decorative imagery §2 rules out.

**Done when — and it is:** a European hop shows a recognisable coast, a Pacific
crossing shows a recognisably empty one, the codes and figures are no harder to
read than they were at 360 dp and font scale 2.0, and flinging the list costs
about 1 ms a frame on device against a card with no map behind it.

---

## 4c. The design review, and what it changed

A critical review of the whole application — visual system, interaction,
information architecture, and the decisions encoded in code — was run against the
built app on 18 August 2026. Sixteen findings; fifteen are fixed, and the
sixteenth is a product decision recorded below rather than taken unilaterally.

The three that mattered most were not aesthetic.

**The departure picker could not find major airports.** Typing `EH` returned
Ecuadorian and Estonian airstrips; typing `EHA` put Schiphol fourth, behind two
airports whose codes merely *contain* those letters. Three decisions compounded:
the ranking had no notion of a prefix, ties broke by slot order, and the shipped
index is sorted **ascending by runway length** — so within a tier the least
significant airport on Earth sorted first, and a 50-result cap could exclude the
answer entirely rather than merely bury it.

`AirportSlotSearch` now ranks in four tiers — exact code, code prefix, code
substring, then name or municipality — and scans the index **backwards**, which
is what makes "the larger airport wins the tie" free: descending runway length is
just reverse slot order. The early exit is safe for the same reason. `EHA` now
returns EHAM first.

The port that produced the defect is worth naming, because the trap generalises:
the desktop app's `SearchService` was ported field-for-field, and its ranking is
fine *there* because it feeds a sortable table where the user can re-sort. A
type-ahead has no re-sort. **Rank is the interface, and the first row is the
answer.**

**Mark flown and Replace were swipe-only, and invisible to a screen reader.** No
`CustomAccessibilityAction` existed anywhere in the app, so a TalkBack user could
read every route and log none of them. Both are now declared on the card's
existing semantics node — the gesture is unchanged, and the same two actions
appear in TalkBack's actions menu.

**A card parked under the status bar stayed there.** Content passing under an
empty bar is the point of the immersive layout; content *stopped* under it is a
card whose ETE figure the battery icon is sitting on. The list's top edge now
fades over the inset — a mask on the content with `BlendMode.DstIn`, not a scrim
on the bar, so nothing is painted behind the clock and the invariant holds.

### The rest, briefly

| ID | Finding | Resolution |
| --- | --- | --- |
| **F3** | Landscape showed one clipped card | Controls share one row, title drops a step, card floor 132 dp, and the width cap lifts when the window is short — the axis with room is the one that gets used |
| **F5** | Refresh spinner landed on the mode chips | The header measures itself and the indicator clears it |
| **F6** | Brand palette and Cockpit unreachable | A real Settings screen: four themes, a dynamic-colour switch, persisted in DataStore. `MainActivity` holds the splash for the stored value, so nobody sees a light flash before Cockpit loads |
| **F7** | Direction under-encoded | An arrowhead on the arc, oriented along the curve. Drawn as a triangle rather than `MaterialShapes.Arrow`, which rounds away the tip that carries the signal |
| **F8** | Tapping a card led to a placeholder | The detail screen is real: the map at 220 dp, both airports with names, municipality and runway, distance and ETE. Phase C still owns bearings, per-runway detail and weather |
| **F9** | Settings was a dead end | A back arrow, as on route detail |
| **F11** | `3863 NM` on one screen, `3,863 NM` on another | One formatter, and the string that skipped the separator is gone |
| **F12** | "Fleet unavailable" read as an error | Placeholders say what is not built yet and what already works instead. "Start your journey" — the desktop's wording, kept deliberately once — is gone: it was the one sentence in the app that sounded like a different product |
| **F13** | Dead design-system surface | `SectionHeader`, `FlightShapes.Arrow` and `GenerateFabMorph` deleted. (The review said four of five `MaterialShapes` were unused; that was wrong — four are used through `LoadingPolygons`) |
| **F14** | Landless cards read as broken | A graticule at the land fill's own contrast, drawn only when the window frames no coast at all |
| **F15** | `06h 18m` | `6:18`, which is how a flight plan writes it |
| **F16** | RTL unverified | Verified under an Arabic app locale. It mirrors correctly — and exposed that distances and runways were being localised into Arabic-Indic digits while ETE stayed Latin. Aviation figures are chart figures: they now format in a fixed locale everywhere, and only the spoken description stays localised, because speech follows the language it is spoken in |

### F10 — the navigation bar, decided

**Five bottom-bar destinations serve one action loop.** Plan generates, Logbook
records it, Stats is a projection *of* Logbook, Airports duplicates a search the
departure picker already performs, and Fleet duplicates the aircraft picker. The
bar's cost is already visible: Settings was evicted from it for space, which is
the layout saying there is one destination too many.

The review's proposal was Plan · Logbook · Fleet, with Stats folded into the
Logbook, Airports folded into Plan, and **Settings taking the freed slot**. That
last part is wrong, and it is worth writing down why: a bottom-bar slot is for a
place you move between constantly, and Settings is somewhere you go once and come
back from. Promoting it would repeat the mistake the bar is already making — a
destination in the bar that does not earn its permanence — just with a different
occupant.

**The shape being considered instead is a Profile section.** One destination for
the things that are about *you* rather than about the next flight: the logbook,
the statistics drawn from it, and a settings entry point as an icon within it.
That gives a bar of Plan · Fleet · Profile — or Plan · Fleet · Airports · Profile
if browsing airports earns a slot of its own — and it puts Settings exactly one
tap deeper than a section, which is the depth it deserves.

Three things to weigh before this is settled:

- **Logbook and Stats are the same subject at two levels of zoom.** Records and
  the summary of those records belong on one screen, which is an argument for the
  Profile shape independent of what it does to the bar.
- **"Profile" has to mean something in an app with no account.** There is no sign
  in, no server and no user record — so the name has to read as "your flying"
  rather than as an account page, or it promises something the app does not have.
- **It changes what Phase D builds.** Phase D is currently two screens, Logbook
  and Fleet. Under this shape it is one section containing two views, and the
  shared list-screen work it needs is the same either way.

**Built, as separate Logbook and Stats sections.** On phones, the bar is Plan · Fleet · Logbook · Stats, keeping each section one tap away. On tablets, the navigation rail expands to include Settings, filling the tall vertical space and providing direct access to all main sections without grouping.

**Logbook and Stats are now top-level destinations.** This removes the "Profile"
nesting that was found to be unnecessary on larger screens. Logbook remains the
record-keeping heart, and Stats remains the analytical view, but they no longer
share a segment-switch container.

**Adaptive width caps.** The 640 dp `MaxContentWidth` was found to be too
restrictive on tablets, leaving large margins that made the app feel sparse.
A new `WideMaxContentWidth` of 840 dp is used for the Logbook and Route Detail
screens when on a wide window, using the available space more effectively without
stretching lines to an unreadable length.

D1 (Logbook list) landed as Profile's Logbook segment in the same change — see
Phase D below.

### The measurement that was owed — and a retraction

The top fade wraps the list in an offscreen compositing layer, which is the shape
of change that usually costs frames, so Phase B++ recorded its cost as unverified
rather than claiming it was free.

An A/B then appeared to clear it — five interleaved pairs, "never worse with the
fade". **That result was void.** The installs between pairs failed silently: a
Windows `adb` was handed a POSIX `/tmp/...` path from a shell with path
conversion disabled, and the failures were suppressed with `>/dev/null 2>&1`. Both
halves of every pair measured whatever was already on the phone. So the fade's
cost is still unmeasured. P2 built the instrument that can settle it and did not
settle it: `:macrobenchmark` measures one APK per run, and this needs an A/B of
two, so what is outstanding is a run to schedule rather than something P2's
existence delivered.

Two things worth keeping from the wreckage.

**The build type matters more than anything measured so far.** Verified installs,
same phone, same harness, minutes apart:

| Build | p50 | p90 | p95 | janky |
| --- | --- | --- | --- | --- |
| `benchmark` (release code) | 8 ms | 11–12 ms | 12 ms | 5.8 % / 6.2 % / 7.3 % |
| `debug` | 8–9 ms | 16–18 ms | 21 ms | 9.9 % / 11.6 % |

The median hardly moves — a cheap frame is cheap either way — and the tail halves.
The expensive frames are the ones where a card composes and draws for the first
time, which is precisely where a debuggable build loses.

**The "same APK, wildly different numbers" anecdote was wrong.** Phase P blamed
the fling pattern for a 5–7 ms session and a 9–12 ms session. It was not the
pattern: the first session's installs worked and the second's did not, so it was
`benchmark` against `debug`. The harness is cruder than a macrobenchmark, but it
is not what produced that gap.

The lesson is narrower than "the harness lies" and sharper: **never suppress the
output of a command whose success you are about to depend on.** Every number in
this document that came from an install is only as good as the install, and the
one check that would have caught it — reading the `DEBUGGABLE` flag out of
`dumpsys package` — takes one line.

## 4d. Phase P — Performance, before the next feature ✅ CLOSED

**Closed after P1, P2 and P3. P4 is deferred deliberately — see the tasks table.**
The list is smooth enough on the target device, and the remaining tail costs more
in image quality than it returns in frames. Feature work resumes at Phase C.

It was inserted ahead of Phase C not because the app was slow — it was not — but
because the two things that would tell us were missing, and every screen built
from here adds surface to whatever they would have found. That held: the
instrument found three separate things the reasoning had wrong.

### What is actually observed

Flinging the route list **stutters for the first second or two, then smooths out,
and generation itself is very smooth once it has run a few times.** That shape —
bad at first, fine later, on the same code and the same data — is warm-up, not
throughput: ART interpreting and then compiling. It is what a baseline profile
exists for, and this app has no profile of its own. It is not, however, running
with none at all: the `benchmark` APK ships a 7,472-byte
`assets/dexopt/baseline.prof`, merged by AGP from the profiles the AndroidX
libraries bundle in their own artifacts. Compose is therefore already covered;
this app's own code is what is not.

### What the budget actually is

The panel on the test device (SM-S942B) supports 120 Hz and Adaptive motion
smoothness is on. Sampled *during* a fling, `mActiveRenderFrameRate` is
**120.0** — so the frame budget while scrolling is **8.33 ms**, not the 16.7 ms
a 60 Hz reading suggests. Every earlier figure in this document was recorded
without knowing which of the two applied.

`FrameTimingMetric` settles this properly: `frameOverrunMs` is measured against
each frame's *own* deadline, so it stays correct whatever the panel is doing and
no longer depends on anybody having sampled the refresh rate by hand.

### Why the existing numbers cannot answer this

Two sessions measured "the same APK" at **5–7 ms p50** and **9–12 ms p50**, and the
first explanation offered here was the fling pattern. It was not: the second
session's installs had failed silently, so it was the `benchmark` build against the
`debug` one. Verified installs put release code at **8 ms p50, 11–12 ms p90, ~6 %
janky** and debug at **8–9 ms p50, 16–18 ms p90, ~11 % janky** — the tail is what
the build type moves.

What survives of the original point: that harness drove synthetic flings through
`input motionevent` and read `dumpsys gfxinfo`, which is enough to rank two builds
in one sitting and not enough to defend an absolute. And the reason the wrong
explanation lasted an afternoon is that a failed install was silent — so P2's first
job was to make the thing being measured impossible to mistake. It is: the
`:macrobenchmark` convention plugin disables the debug variant outright, so
`connectedDebugAndroidTest` does not exist to be run by accident, and
`androidx.benchmark.suppressErrors` is unset, so the library's own refusal to
measure a debuggable target stays armed.

### What the instrument says

First full run, `:macrobenchmark` on SM-S942B, 7 m 39 s for all four benchmarks.
Ten iterations per fling, twelve per startup, process killed before each one.

| Benchmark | | | | |
| --- | --- | --- | --- | --- |
| | **P50** | **P90** | **P95** | **P99** |
| `flingNoCompilation` — `frameDurationCpuMs` | 4.8 | 7.2 | 8.4 | 12.5 |
| `flingNoCompilation` — `frameOverrunMs` | 0.8 | 4.7 | 5.3 | 7.4 |
| `flingPartialCompilation` — `frameDurationCpuMs` | 4.7 | 6.7 | 7.7 | 11.4 |
| `flingPartialCompilation` — `frameOverrunMs` | 0.7 | 4.6 | 5.1 | 7.5 |

| Benchmark | min | median | max |
| --- | --- | --- | --- |
| `startupNoCompilation` — `timeToInitialDisplayMs` | 160.7 | **168.7** | 208.2 |
| `startupPartialCompilation` — `timeToInitialDisplayMs` | 162.0 | 198.6 | 226.6 |

Three things follow, and one non-thing.

**Cold start is comfortable.** 169 ms median against a 500 ms budget, from a
controlled instrument rather than a hand-picked median of `am start -W`. This is
the first cold-start figure in this document that does not need the paragraph of
caveats in `CLAUDE.md` attached to it.

**The median fling is inside budget and the tail is not.** 4.8 ms at P50 against
8.33 ms, but P99 frame duration is 12.5 ms and P99 overrun is +7.4 ms. That is the
shape the complaint describes, and it is now a number rather than an impression.

**Compilation barely moves it.** None → Partial is 7.2 → 6.7 ms at P90 and 12.5 →
11.4 ms at P99: real, but around 7 %. Both modes were measured against a killed
process, so this is precisely the "first fling" comparison P1 is meant to win, and
it is a much smaller prize than "the single largest win available to a Compose
app" implies. The likely reason is in the paragraph above — the library profile is
already in the APK, so the code Partial adds on top is only this app's own.

**The non-thing:** `startupPartialCompilation`'s median came in 30 ms *above*
`startupNoCompilation`'s, which is not a result anybody should believe. It ran
last, on a device that had just been flinging for five minutes. Re-running one
startup benchmark now costs 35 seconds, which is the point.

### What P1 found, including that the question above was mis-posed

**The two modes above cannot measure a baseline profile.** The paragraph headed
"compilation barely moves it" calls None → Partial "precisely the first fling
comparison P1 is meant to win". It is not, and no arrangement of those two modes
is. `CompilationMode.None` issues `cmd package compile --reset`, which discards
the installed profile along with everything else; `CompilationMode.Partial()`
defaults to **three warm-up iterations**, so it reaches a hot process whether or
not a profile helped it get there. The distance between them is floor to ceiling,
and a profile was only ever going to buy part of it.

So a third mode was added — `Partial(BaselineProfileMode.Require, warmupIterations
= 0)`, an installed profile and an unwarmed JIT, which is the state a user is in
exactly once per install. `Require` rather than `UseIfAvailable` so that a missing
profile fails the run instead of quietly re-measuring `None` and reporting that P1
achieved nothing.

**The fling, three ways.** Ten iterations each, process killed before every one,
`PlanScrollBenchmark` run on its own:

| `frameDurationCpuMs` | P50 | P90 | P95 | P99 |
| --- | --- | --- | --- | --- |
| None | 5.0 | 7.3 | 8.6 | 12.1 |
| **Baseline profile** | 4.7 | 6.9 | 7.8 | 11.7 |
| Partial (3 warm-ups) | 4.6 | 6.7 | 7.7 | 11.1 |

| `frameOverrunMs` | P50 | P90 | P95 | P99 |
| --- | --- | --- | --- | --- |
| None | 1.4 | 4.9 | 5.5 | 7.5 |
| **Baseline profile** | 1.1 | 4.8 | 5.3 | 7.4 |
| Partial (3 warm-ups) | 0.6 | 4.6 | 5.0 | 6.7 |

**The profile buys CPU time and does not buy the tail.** Of the floor-to-ceiling
gap in frame *duration* it recovers about two thirds at P90 and nearly all of it
at P95. Of the gap in *overrun* — the number this module says to read, because it
is measured against each frame's own deadline — it recovers roughly a sixth at P90
and an eighth at P99. Frames are getting cheaper to compute and are still missing
their deadline by the same margin, which is what it looks like when the tail is
not about how fast the code runs. **P3 is now the indicated cause**, and it is no
longer an inference from AOT having failed to claim the tail — it is that making
the code demonstrably faster did not move it.

**Cold start is where the profile pays.** `StartupBenchmark` run on its own, on a
device reporting `Thermal Status: 0`, twelve iterations each:

| `timeToInitialDisplayMs` | min | median | max |
| --- | --- | --- | --- |
| None | 190.3 | 206.7 | 240.5 |
| **Baseline profile** | 156.9 | **181.8** | 214.7 |
| Partial (3 warm-ups) | 147.8 | 174.9 | 210.0 |

**25 ms, about 12 %, and 78 % of everything compilation had to give.** That is a
first launch after install, and it is the one launch a user cannot avoid. It is
also the only place P1 delivered.

**`None` is not reproducible across sessions and should not be quoted.** Its
median has now been measured three times on the same device — 168.7 ms in P2,
226.4 ms in a full suite this session, 206.7 ms alone — while `Partial` over the
same three runs sat at 198.6, 172.9 and 174.9. Interpreted code is the mode most
exposed to whatever else the scheduler is doing, so the two-way comparisons in the
tables above are trustworthy *within* a run and the absolutes are not. No
explanation is offered here for the 168.7 that P2 recorded and this session could
not reproduce; the honest reading is that the table above supersedes it, not that
something regressed.

### What P3 found, including that it was aimed at the wrong half

P3 was written as "move `MapFrame.projectOutline` off the UI thread". Before doing
that, the map's cost was split in two and each half measured with
`flingBaselineProfile` — compute (project and build the `Path`s) against draw
(rasterise them). Three builds, same device, same session:

| | CPU P50 / P90 / P95 / P99 | overrun P50 / P90 / P95 / P99 |
| --- | --- | --- |
| Compute **and** draw — as shipped | 4.7 / 6.9 / 7.8 / 11.7 | 1.1 / 4.8 / 5.3 / 7.4 |
| Compute, do not draw | 3.8 / 5.4 / 6.1 / 8.5 | −1.4 / 2.0 / 3.6 / 5.1 |
| Neither | 3.8 / 5.3 / 5.9 / 8.3 | −1.4 / 3.1 / 3.7 / 4.9 |

Read the CPU column; overrun at P90 carries about ±1 ms of noise here, which is
why the middle row appears to beat the bottom one.

**The compute is free and the drawing is everything.** Rows two and three are the
same to within noise, so projecting and path-building cost about 0.1 ms at P90 —
because `drawWithCache` already caches them, so they run once per card entering
composition. Rasterising costs 1.5 ms at P90 and 3.2 ms at P99, because
`onDrawBehind` runs *per frame per visible card*. P3 as written would have
addressed roughly three per cent of the map's cost.

**The mitigation this component was designed with was tried and rejected.** B11's
notes list "snap frames to a handful of zoom levels and cache them as
`ImageBitmap`s" as the third mitigation if the first two were not enough. Caching
each card's land and coast into a tile and blitting it was built and measured at
three resolutions:

| Tile | Sharp? | overrun P50 / P90 / P99 |
| --- | --- | --- |
| Full resolution | yes | 3.2 / 5.3 / 9.2 — **worse than not caching** |
| Half | visibly soft | −0.4 / 4.0 / 6.9 |
| Quarter | blurry, rejected on sight | −0.8 / 1.6 / 5.6 |

**A full-resolution tile is slower than the thing it replaces.** It is 2.3 MB
allocated every time a card enters composition, tens of times a second during a
fling, and that burst costs more than the per-frame stroking it removes. The
tile's speed and its blurriness are therefore the same knob: it only pays by
resampling. On a card whose subject is a coastline, that is the wrong currency,
and the quarter-scale build was rejected by looking at it.

**What was kept is the part that was free.** The coast is stroked with bevel
joins and butt caps instead of round ones. A round join constructs an arc at every
vertex, and the coast has a few thousand per card; at 1 dp wide and 16 % opacity
that arc is sub-pixel. The route keeps round joins — it is a few dozen segments
and it is the thing being looked at. Verified on device, not from a preview.

| | overrun P50 / P90 / P99 |
| --- | --- |
| Before | 1.1 / 4.8 / 7.4 |
| **Bevel and butt** | 0.6 / 4.8 / **6.9** |
| *Floor, no map at all* | −1.4 / 3.1 / 4.9 |

0.5 ms at P99 and a median frame that finishes early rather than late, for no
visual cost. P90 did not move. That is a small result honestly obtained, and it is
where Phase P stops: the remaining tail is worth less than the sharpness it would
cost.

### Tasks

| ID | Task | Notes |
| --- | --- | --- |
| **P1** | Baseline profile | ✅ `BaselineProfileGenerator` in `:macrobenchmark` writes `app/src/main/generated/baselineProfiles/`, committed: 22,655 rules, 1,633 of them this app's own code, 9,250 bytes once compiled into the APK against 7,472 for the libraries alone. Worth **25 ms of cold start** and, on the fling, frame duration but not overrun — see above. `androidx.baselineprofile` also replaced the hand-written `benchmark` build type with its own `benchmarkRelease`, so the measurement task is now `connectedBenchmarkReleaseAndroidTest` |
| **P2** | Macrobenchmark module | ✅ `:macrobenchmark`. `PlanScrollBenchmark` (`FrameTimingMetric` over a down-and-back-up fling) and `StartupBenchmark` (`StartupTimingMetric`, cold), each in **three** compilation modes since P1 — none, baseline-profile-only, and partial with warm-ups. Only the fling is inside `measureBlock`; launching, generating fifty routes and the staggered entrance happen in `setupBlock`. See [the module README](../macrobenchmark/README.md) |
| **P3** | ~~Projection off the UI thread~~ → cheaper coast stroke | ✅ **as a different change, because the premise was wrong.** The projection costs ~0.1 ms at P90; rasterising costs 1.5 ms at P90 and 3.2 ms at P99, per frame per visible card. Moving the projection would have addressed ~3 % of the map. Raster-caching the map was built and rejected — full resolution is *slower* than not caching, and the resolutions that are faster are visibly soft. What shipped is bevel joins and butt caps on the coast: 0.5 ms at P99, no visual cost. See above |
| **P4** | A budget, written down | **Deferred by decision, not by oversight.** The numbers exist and could be asserted today — `frameOverrunMs` P90 and `timeToInitialDisplayMs` median, both from the baseline-profile mode. They are not, because a threshold on a number that moves ±1 ms between runs on an idle device buys flaky builds rather than protection, and the list is smooth enough that a regression worth catching would be seen before it would be measured. Revisit when a regression is noticed by eye, or when a device farm makes the numbers steady enough to assert |

**Order as run: P2 ✅, P1 ✅, re-measure ✅, P3 ✅ — and P4 deliberately not.**
The IDs are stable, so they are not renumbered. The instrument came before the
change and paid for itself three times, each time by contradicting something this
document had already written down:

1. The prize from AOT compilation was smaller than "the single largest win
   available to a Compose app" implied — known *before* the profile was written.
2. The pair of numbers P2 quoted for that prize could not have measured a baseline
   profile at all: `None` resets the profile away and `Partial` warms the JIT past
   it. That needed a third compilation mode, and the mistake would have survived
   indefinitely if P1 had been argued about instead of run.
3. P3 was aimed at the compute half of a cost that is almost entirely in the draw
   half — and the mitigation B11 had pre-registered for that half turns out, at the
   only resolution worth shipping, to be slower than the problem.

**Done when:** ✅ — the list is smooth enough on the target device, `:macrobenchmark`
says so rather than a person judging it by eye, and the remaining tail has been
priced and found not worth what it costs in image quality.

---

## 5. Phase C — Route detail

**Complete.** C1–C6 are built and verified on a device, in one window and in two.
The `ModalBottomSheet` half of C1 is deliberately rejected rather than deferred —
see below.

**Part of this landed early.** Resolving F8 of the design review — tapping a card
reached a placeholder — turned the detail destination into a real screen: the map
at 220 dp, both airports with their names, municipality and runway, and the two
figures. The rest is what needed data or a calculation the screen did not have.

| ID | Task | Notes |
| --- | --- | --- |
| **C1** | Detail container | ✅ and partly **rejected**. Compact keeps the full screen with predictive back; the `ModalBottomSheet` is not built and will not be — see below. `ListDetailPaneScaffold` carries the list and the detail side by side wherever the window has room for two panes, with `PlanRoute` making that one decision. `RouteDetailContent` and `RouteDetailLoader` are extracted so one layout and one load serve both hosts |
| **C2** | Route facts | ✅ Initial and final bearing (`GreatCircle.finalBearingDeg`, new), field elevation, and **every runway end** at both fields — ident, true heading, length × width, surface, lighting — collapsed to the longest with a disclosure |
| **C3** | Hero map area | ✅ `RouteMap` at 220 dp, the same component the card draws. **Deliberately a still image until Phase G** — it is the fallback the globe crossfades in over, so it is not throwaway work |
| **C4** | Actions | ✅ Mark as flown, copy plan, SkyVector, SimBrief, Google Maps (per airport, as on the desktop), share. URLs ported field-for-field from `route_popup.rs` and unit-tested against those literals |
| **C5** | Weather block | ✅ as a placeholder: the block states what is missing and holds its height, so Phase F fills it without a reflow |
| **C6** | Detail motion | ✅ `sharedBounds` on the map, the ICAO pair and the aircraft name; the spine staggers in beneath the hero; distance counts up once; mark-as-flown plays a haptic confirmation and then hands back to the list. On a two-pane window a new selection cross-fades the pane instead (`FlightMotion.paneContent`), and the content skips its own stagger there so one motion explains one change. Making the map's flight clean needed a fix inside `RouteMap`, and making back *predictive* meant deleting a handler rather than adding one — both below |

**Done when:** ✅ — every element of the desktop's `route_popup.rs` has an
equivalent, and the card→detail→back journey is continuous with no visual jump. The
two that are *deliberately* still placeholders are the ones another phase owns: the
3D globe is G, and live METAR is F, for which C5 reserves the space.

### The layout: the leg is the page's structure

Two equal panels with an arrow between them is the obvious answer and it throws
away something true — **the initial bearing belongs to the departure and the final
bearing belongs to the destination** (they differ by 62° on EHAM–KJFK), and the
distance and the estimate belong to the span between them. So the page is a
**spine**: a hairline rail with a hollow ring at the departure end and a filled dot
at the destination end, which are the two markers `RouteMap` already draws on the
arc. Each figure sits where it is true. The pair of `ValueChip`s under the hero was
removed to pay for it: the screen says more than it did with one row fewer.

The runway list is collapsed to the longest plus a count. Not to save ink — a hub
with ten ends and a strip with two would otherwise move the leg's figures by 400 dp,
so whether the distance was on the first screenful would depend on which airport
the generator happened to pick.

### Three decisions worth keeping

**The `ModalBottomSheet` was rejected, not forgotten.** At the height this content
needs it is a full screen with a drag handle, and it forfeits the shared-element
continuity C6 exists to create. The full-screen destination already had predictive
back, which is what the design review actually asked for.

**The scaffold is used only where it has something to offer.** `ListDetailPaneScaffold`
would happily collapse to one pane on a phone and swap the detail in over the list,
and that is a *worse* phone experience than the destination that exists: no app bar,
a binary back gesture instead of a progressive one, and no shared element, so a route
would appear rather than arrive from its card. `PlanRoute` therefore branches on
`maxHorizontalPartitions` — the adaptive library's own answer to "is there room for
two panes", the same family of decision the navigation suite already makes, so the
two cannot disagree about what kind of window this is.

**The list pane is the whole Plan screen**, not a reduced one. The header, filters,
swipe grammar and undo snackbar are what that screen *is*, and a tablet has no less
use for them. The only thing a wider window changes is what a tap on a card means,
which is a lambda.

**The map flies from the card to the hero, and the bug was `RouteMap`'s, not the
transition's.** During the flight the map painted far outside the bounds it had
animated to, spilling across the whole screen. Two wrong diagnoses came first — the
hero being double-animated, and the container's scale fighting the overlay — and
both produced real improvements that are still in (see below) without touching the
artifact.

The actual cause was one line that was never written: **`RouteMap` had no clip of
its own.** Its own KDoc says it projects the outline *with a margin*, "so a coast
just off the card still contributes the segment that enters it, and a stroke's
trimmed end falls outside the visible area rather than inside it" — that is a
component deliberately painting past its bounds and relying on the `Card` around it
to crop the overspill. A shared element is rendered in an **overlay, where no
ancestor clip applies at all**, so every off-window coastline suddenly had the whole
screen to draw on. `RouteMap` now crops itself with a `clipRect` inside its draw
scope — not `Modifier.clipToBounds()`, which is a `graphicsLayer` and would add an
offscreen layer per card to the list Phase P spent its time keeping smooth.

Two things that made this hard to see. The element's *own* clip is useless here:
placed inside `sharedBounds` it clips to the size being measured, not the size being
drawn. And `RemeasureToBounds` is the only resize mode material3 1.5.0-alpha26
offers — `ScaleToBounds` is absent — so the map genuinely re-projects at every size
it passes through on the way, which is why nothing about the flight could be
"scaled" out of trouble.

Worth keeping from the two wrong turns, because both are independently right:
**nothing that is a shared element is also staggered in** (fading a thing in while
it travels animates it twice, and the two do not agree), and **screens that share an
element use `FlightMotion.sharedEnter` rather than `navEnter`** (an overlay does not
inherit the container's scale).

**The overlay was wrong in two further ways, and both showed on the way back.**
Cropping `RouteMap` stopped the map painting across the screen; it did not make the
overlay behave like the card. Coming back from the detail screen, for the length of
the transition, the card's corners were square and its `DIST`/`ETE` chips were
visibly darker — and both snapped right at one instant rather than resolving, which
is what made it read as a fault rather than as motion. The snap is the tell: it is
the frame the overlay is torn down in, so whatever is wrong is wrong *because the
element is in the overlay* and is wrong right up to the end, when the element has
already arrived at the card's bounds and everything else about it is correct.

- **No ancestor clip reaches into that overlay — including the one that rounds the
  corners.** The card's radius is the `Card`'s clip and the hero's is the
  `Surface`'s, and neither is the element. `sharedBounds` takes
  `clipInOverlayDuringTransition` for exactly this, and `sharedRouteElement` now
  carries an optional `clipShape` that both ends name. Its default is **no clip**,
  which replaces the API's `ParentClip` default: that one confines an element to
  the nearest enclosing shared element, and the two ICAO codes are supposed to
  leave the card entirely.
- **The overlay is above *everything*, so sharing only the background inverted the
  card's own stacking.** The map is what the card's figures are printed over; lifted
  into the overlay it was painted on top of them instead. The chips are the one
  translucent thing on the card, so they showed it first — at rest 30 % of the map
  reaches through a chip and in the overlay 100 % of it lands on top, which is a
  darker chip with the route arc drawn across it. The fix is that the shared element
  is now the card's whole **face**, map and figures together in their resting order,
  so what the overlay draws in the final frame is what the card draws in the first
  frame after it. The airframe and the two codes stay shared individually inside it,
  nested, which is supported and is what keeps them travelling to the spine.

**Two panes means no shared elements at all.** Clearing both scopes under
`ListDetailPaneScaffold` is not tidiness: a card and a detail pane showing that same
route hold the same keys *at the same time*, which is the one arrangement a shared
element cannot be in — the two halves of a pair are meant to be two screens apart in
time, not side by side on one. Nothing travels in that layout anyway, so there was
never anything for them to do there.

**The shared ICAO pair lands in the app bar, not on the spine.** The spine's two
codes are the obvious target and the destination's block can sit below the fold; a
shared element flying to something off screen reads as a glitch. The title is on
screen at both ends of the journey, every time — which is why it is three nodes
rather than one formatted string.

### What the device found that the build did not

Every one of these compiled, passed tests and looked right in a preview.

- **Repeated spaces in a string resource are folded to one**, so the runway line's
  columns arrived as `197 ft ASP`. It is middot-separated now.
- **The raw `surface` column is unusable for display.** The dataset spells one
  surface at least five ways — `ASP` (19,738 rows), `ASPH`, `ASPH-G`, `Asphalt`,
  `PEM` — so a route really did show `ASP` at one end and `ASPHALT` at the other,
  and 3,000-odd rows are `X`, `N`, `G`, `C` or empty. The screen shows the ETL's
  normalised `SurfaceKind` instead, and says nothing where that is `UNKNOWN`. **A
  divergence from the desktop**, which prints the column.
- **7.6 % of runway ends publish no width** and the ETL writes zero, so the line
  read `3,444 × 0 ft`. The width is dropped when it is absent.
- **823 airports record an elevation of exactly zero**, which OurAirports uses for
  "not published" as well as for sea level. An inland strip stated as sea level is a
  wrong figure on an instrument, so the elevation is omitted at zero.
- **RTL reordered every figure on the screen.** F16 fixed the *digits* — a distance
  must not become Arabic-Indic — and missed the *order*: under an Arabic locale the
  bidi algorithm delivered `NM 1,308`, `54.5394 ,25.9089` and
  `ft · Hard 175 × 8,345 · 127° · 12`. `TextStyle.asChartFigure()` now pairs tabular
  figures with `TextDirection.Ltr`, and `ValueChip` uses it, so the route cards were
  fixed by the same change. The title's arrow is mirrored by hand, since the `Row`
  reverses but the glyph does not.
- **The title's arrow was a type step smaller than the codes**, so centring two
  different line boxes put it visibly low. It is the same style now, de-emphasised
  by colour.

### What tapping it on a device found

All three owed checks are done. Two passed as built; one did not.

- **Mark as flown, end to end** ✅ — the screen hands back, the snackbar reads
  "Logged OIKK to UWWG", and the not-flown count moves 103 → 102, which is the
  proof that *both* writes landed rather than just the logbook row. Undo returns
  it to 103.
- **The five external actions** ✅ — SkyVector loads with its Flight Plan bar,
  SimBrief Dispatch boots, the Maps intent resolves to `com.google.android.apps.maps`
  rather than a browser, the share sheet carries `Boeing 777-200ER: YMAV -> VDTI
  (3705 nm)` under a "Flight plan" title, and Android's own clipboard confirmation
  shows the same string — which is why there is no snackbar for a copy.
- **Landscape** ❌ **— and it was as bad as F3 predicted.** The app bar plus a
  220 dp hero *is* the window: every fact on the screen sat below the fold, behind
  a map that at that aspect is mostly empty. Two fixes, both reusing what the Plan
  screen already had: the hero drops to the card's own `CompactCardHeight` of
  132 dp when `isCompactHeight()`, and the column is capped at `MaxContentWidth`
  and centred, because an 800 dp-wide window otherwise sets an airport's full name
  on one line running the whole way across.

### The second pane found a third instance of the same bug

**A screen that reads the window is a screen that assumes it is the window.** The
Plan screen centred its column by computing `(windowWidthDp() - MaxContentWidth) / 2`,
which was correct for exactly as long as the screen *was* the window. Placed in a
pane on a tablet it asked for the window's 1,706 dp, computed over a thousand dp of
centring margin for a pane a few hundred dp wide, and rendered **nothing at all** —
a blank column beside a working detail pane, with no crash and no warning. It
measures itself with `BoxWithConstraints` now.

That is the same mistake as F3's landscape and as this phase's own landscape
finding, in a third disguise: a layout deciding from something other than the space
it was actually given. Worth looking for by name whenever a screen gains a new host.

The line-length cap moved into `RouteDetailContent` for the same reason. It started
as the landscape fix on the full screen, and the pane needed it too — so it now
lives with the content, where a third host gets it without having to learn the
lesson again. Hosts decide margins and alignment; the content decides how wide a
line of it may get.

### Two motion faults found by using it, not by reading it

**Selecting a route in the two-pane layout changed nothing visibly.** The panel
simply held different text a frame later. There is no navigation on a tablet, so
none of the phase's transitions applied, and the staged entrance does not replay
on a recomposition. It cross-fades now — `AnimatedContent` keyed on the *route's
identity* rather than on the state, because one selection publishes twice (airports
first, runways a query later) and keying on the state would have faded the panel a
second time for a change the user did not make. The content skips its own stagger
in that host, since the fade already explains the change.

**Predictive back was not predictive, and the fix was to delete code.** Dragging
back from the detail screen shrank and dimmed it over a black background: you could
see the screen leaving and not the screen you were going to. The cause was this
phase's own `PredictiveBackHandler`, which drew a local peek and — by registering
inside the destination — consumed the gesture before `NavHost` could see it. The
host already seeks the pop transition to the gesture's progress, composing Plan
underneath and flying the shared elements back to the card as the finger moves.
Removing the handler restored all of that. See the rule in §2.

A third thing worth knowing before diagnosing either: **an emulator defaults to
three-button navigation**, where there is no back gesture at all and no amount of
correct code will show a preview.

### What the code review found

Six findings, all fixed. Two are worth carrying forward as general lessons.

- **A delayed `onBack()` can pop twice, and the second pop empties the app.** The
  mark-as-flown confirmation waits half a second before handing back, and a
  destination stays composed for the length of its exit transition — so tapping
  the app bar's back arrow during that half-second popped `RouteDetail` and then,
  from the still-live effect, popped `Plan` as well. `Plan` is the start
  destination of both its graph and the root, so the back stack emptied and the
  app showed nothing at all. Any navigation on a delay needs the lifecycle check
  this one now has.
- **`RemeasureToBounds` is wrong for a canvas.** It re-lays-out the child at the
  animated bounds every frame, and `RouteMap` builds its geometry in a
  `drawWithCache` keyed on size — so a remeasured map re-projected 122 coastline
  rings sixty times a second for the length of the transition, which is exactly
  the work that cache exists to prevent. Text still wants remeasuring; the map now
  scales, which is also the more honest motion for it.

Also fixed: the confirmation state is `rememberSaveable`, so a rotation inside the
window cannot strand the screen; a failed write now emits `PlanEvent.FlightLogFailed`
and says so, because from the detail screen a failure was otherwise silent after a
confirmation haptic; the Maps URL uses fixed decimals, since `Double.toString`
turns a coordinate near the equator into `4.0E-4` and Maps does not resolve it; and
`rememberRouteActionLauncher` no longer keys on an unstable lambda that defeated
the `remember` it looked like it had.

---

## 6. Phase D — Logbook and Fleet

**F10 landed as Profile**, so D1 built inside it rather than as its own bar
destination — see F10 above for the shape and why. D3 (swipe-to-delete) and a
two-pane Logbook/flight-detail layout — reusing `RouteDetailPane`, not in the
original D-phase scope — landed in the same change (`77073b2`) but the table
below was never checked off for it until now.

**D4–D6 are built.** Fleet is a real screen: filter chips, category grouping, a
per-row flown toggle, a detail screen with an inline-edit sheet for the envelope,
"generate routes for this aircraft", and fleet management (add aircraft, mark all
not flown, restore defaults) behind an overflow menu. **D7 is deliberately not
built** — the user decided the shipped app should not expose CSV import/export in
the UI at all, so it is dropped rather than deferred. `FleetCsv.parse`/`write` in
`:core:model` stay: they back the bundled first-run seed and `restoreDefaults()`,
and are already tested independently of any UI.

Two things worth carrying forward from building Fleet:

- **A stock `Switch` for the flown toggle was wrong**, even though nothing about
  it was incorrect — the mode row above it already establishes the screen's own
  selection grammar (`FilterChip`, filled means "on"), and a system switch next to
  it reads as settings chrome dropped into an otherwise chart-styled screen. The
  row toggle, the detail screen's flown state, and the mode filter now all use the
  same chip.
- **The add-aircraft FAB went through two wrong iterations before landing.** First
  a `LargeFloatingActionButton` — which is not what "Expressive" prescribes here;
  the M3 spec's *default* FAB size is what Compose's plain `FloatingActionButton`
  already is, so sizing up had nothing behind it. What Expressive actually asks
  for is motion: a FAB's shape morphing on press. `FlightShapes.Circle` →
  `FlightShapes.Cookie` was already named in `:core:designsystem` for exactly this
  — built for Plan's now-deleted generate FAB and orphaned when that FAB was cut.
  Fleet's FAB revives it: `MorphShape` driven by `FlightMotion.spatialFast()` on
  the button's own pressed state, no new design-system surface needed.

| ID | Task | Notes |
| --- | --- | --- |
| **D1** ✅ | Logbook list | Grouped by month with sticky headers (`MonthHeader`, `:core:designsystem`, the codebase's first use of `LazyColumn.stickyHeader`); a `StatSummaryStrip` (also new, `:core:designsystem`) shows flights, NM and hours flown this year, each figure driven by the existing `FlightMotion.rememberCountUp`. Month-grouping and the year summary are pure functions in `ui/logbook/LogbookGrouping.kt`, unit-tested without Robolectric — no precedent existed for either in this codebase or in the Rust reference, so both are new rather than ported. `LogbookViewModel` follows `PlanViewModel`'s shape (`combine` + `stateIn`, grouping/summing kept off the main thread via `flowOn`) |
| **D2** ✅ | Add-flight sheet | Aircraft/departure/destination pickers reuse `PlanPickerSheet` (a third `PickerTarget.Destination` added) plus a new `FlightDatePickerDialog` in `:core:designsystem`; distance computed live via `GreatCircle.distanceNm` and validated field-for-field against the Rust reference's `AddHistoryPopup` (required fields, departure ≠ destination, distance within the aircraft's range). Writes only `LogbookRepository.add` — never `FleetRepository.setFlown`, matching the reference's own `add_history_entry`. The date picker itself is a deliberate divergence from the reference, which has none |
| **D3** ✅ | Swipe-to-delete | With undo, matching the Plan screen's swipe grammar exactly. Shipped in `77073b2`, alongside a two-pane Logbook/flight-detail layout (reusing `RouteDetailPane`) that was not in the original D-phase scope |
| **D4** ✅ | Fleet list | Filter chips (All · Flown · Not flown), category grouping via `MonthHeader` reused generically. Row toggle is a `FilterChip` (not a switch — see above), stamps `date_flown` through `FleetRepository.setFlown` |
| **D5** ✅ | Fleet detail | A hero surface (identity + range/cruise/takeoff, matching the weight `RouteDetailContent`'s hero map carries), "generate routes for this aircraft" (reuses `PlanViewModel.setAircraft` through the graph-scoped instance, same mechanism `RouteDetail`'s mark-flown uses), inline editing of range, cruise and takeoff distance via a dedicated `EditEnvelopeSheet` rather than an inline form swap |
| **D6** ✅ | Fleet management | Mark all not flown and restore defaults, both behind `ConfirmationDialog` (new, `:core:designsystem` — the first confirmation dialog anywhere in the app); add aircraft via a full-form sheet with an `ExposedDropdownMenuBox` category picker (type to filter existing categories, or type a new one) |
| **D7** | ~~CSV import/export~~ | **Dropped by user decision**, not deferred. The desktop's CSV format stays supported for the bundled seed and `restoreDefaults()`, but no SAF import/export UI will be built |
| **D8** ✅ | Motion | Two of three parts turned out to already exist; see below for what closed this and why nothing new was built for the header |

**D8, and why it needed no new code.** Two of its three clauses were already
true by construction: `StatSummaryStrip` drives every tile through
`FlightMotion.rememberCountUp`, which re-animates from wherever it is
whenever the underlying value changes — not only on first appearance — so an
add or delete already makes the strip re-count with nothing extra wired up.
And the flown toggle's `FilterChip` never overrides `colors`, so Material3's
own `animateColorAsState` inside the stock component already animates the
fill on selection — there was no custom transition to replace.

The third clause — headers collapsing on scroll — is met by `stickyHeader`
itself and was deliberately left there rather than built further. A header
is already pinned and swapped for the next one at the exact instant its real
position reaches the top, which is scroll-attached with no spring and no
threshold: precisely the property Phase B+ had to fight to get for the Plan
screen's own chrome. A hand-built shrink or fade on `MonthHeader`, reading
`LazyListState.layoutInfo` every frame to ease the hand-off, would be a
second animation layered on top of a mechanic that already tracks the scroll
position one-to-one — the same shape of chrome-on-its-own-clock that B+'s
"Retracting controls" built, rejected on a device, and removed. It would also
add a per-frame read to two lists a full performance phase (Phase P) was
spent keeping cheap, to smooth a hand-off on a two-line section label. Against
§2's bar — motion explains, it does not perform — the hard cut already
explains the one fact there is to explain ("a new section starts here");
softening it would be decoration with nothing left to say.

---

## 7. Phase E — Airports, Stats, Settings

**E5 is done except for the two items blocked on later phases.** The
appearance half landed with the design review; units, the ICAO-only toggle,
dataset info and licences landed together in one pass. Weather provider and
tile provider are deliberately not built — they would configure Phase F and
Phase G, neither of which exists yet, so a setting for either now would be
inert. They land alongside their phases instead.

**F10 changes where two of these land, not what they build.** E3 (Stats dashboard)
and E5 (Settings) now build inside the Profile section D1 already opened, in the
Stats segment and behind Profile's own Settings icon respectively, rather than as
destinations of their own — the work described below is unchanged. E1 (Airports
browse) has no bar slot to land in any more, since F10 dropped Airports rather than
moving it; it now hangs off an overflow item on Plan's own header instead — see E1.

| ID | Task | Notes |
| --- | --- | --- |
| **E1** | ~~Airports browse~~ | **Complete.** Ranked type-ahead over the name index from **A5** — a blank query already returns the largest airports — plus a random-50 action, ported from the desktop's `generate_random_airports` (Floyd's algorithm plus a shuffle, `RandomAirportSample` in `:core:routing`). Reached from its own icon on Plan's header — not a bar slot, and no longer behind the overflow menu it first shipped in: a hidden 3-dot menu holding exactly one item was found to be less discoverable than the near-zero-cost commitment it was chosen for was worth |
| **E2** | ~~Airport detail~~ | **Complete.** `RunwayDiagram` (new, `:core:designsystem`) — a compass-style chart, one ray per runway end at its true heading, no desktop precedent — idents, true headings, surface, length, a copyable coordinate label and Google Maps link (shared with Route detail's `AirportLinks`), plus "fly from here", which locks the departure via `PlanViewModel.setDeparture` and pops back to Plan, mirroring `generateRoutesFor`'s own pattern. A reserved-height METAR placeholder awaits Phase F. Route detail's own departure/destination blocks (and the Logbook detail pane, which reuses them) now tap through to this screen, keyed on the airport's own `id` rather than its code |
| **E3** | ~~Stats dashboard~~ | **Complete.** All nine statistics metrics, hero distance card with count-up animation and Earth equator multiplier badge, top-aircraft rankings, and longest/shortest flight cards |
| **E4** | ~~Visited mini-globe~~ | **Complete.** 2D visited network map projection rendering land polygons, coastlines, visited airport nodes, and great-circle connecting flight arcs |
| **E5** | Settings | Theme (four choices) and dynamic colour shipped with the design review (F6). This pass added: a unit system (Aviation NM/ft/kt, Metric km/m/km-h) threaded through `Figures.kt`'s formatters and a `LocalUnitSystem` provided from `MainActivity`; an ICAO-only toggle wired to `RouteRequest.icaoOnly` (the pipeline already existed end-to-end — `AirportIndex.hasIcaoCode`, `RouteGenerator.kt` — only the Settings UI and the wiring were missing), regenerating the Plan list live via the same `combine`+`collectLatest` path a mode change already uses; dataset info (`DatasetMetaDao`, already collected by the self-check screen, now surfaced to the user); and a hand-rolled Licences screen (Apache-2.0 dependencies, OurAirports and Natural Earth attribution). `SettingsRepository` became an interface + `DefaultSettingsRepository`, matching the `AirportRepository`/`FleetRepository` shape, so `PlanViewModel` could take a fake in tests. **Still to do** — weather provider and tile provider, deferred to Phases F and G |
| **E6** | ~~Motion~~ | **Complete.** Distance count-up with `FlightMotion.rememberCountUp()`, monthly activity bar height animation with `FlightMotion.effects()`, and interactive metric switcher |

---

## 8. Phase F — Weather

> **F1–F6 shipped, then the visual layer was replaced wholesale (Phase F′).** The
> reason is a defect, not a preference: `WeatherCondition.derive()` rendered a null
> ceiling as good weather, so an IFR field drew a sun icon. F4's chips survive
> unchanged; F6's glyph is deleted.
>
> **[docs/WEATHER-PLAN.md](WEATHER-PLAN.md) is the live plan** — what shipped, the
> known defects, and everything still outstanding. Read it rather than this table
> before touching weather, and read its *Known defects* section first: one of them
> is a diagnosis a fresh reader would very likely get backwards.

| ID | Task | Notes |
| --- | --- | --- |
| **F1** | NOAA client in `:core:network` | Keyless default. `deriveFlightRules` locally for reports without `fltCat` |
| **F2** | Batched fetch | Visible ICAOs collected from `LazyListState`, debounced 300 ms, chunked by 50 — **one request per screenful, not fifty** |
| **F3** | Cache | `metar_cache` in the user DB with ~15 min TTL, so a dataset refresh never wipes it |
| **F4** | Flight-rules chips live | Replaces the stubs in B2 and C5 |
| **F5** | AVWX fallback | Masked key field in Settings; the desktop's provider kept as an option |
| **F6** | Motion | Chips fade from unknown to resolved as data arrives — never a layout jump, since the chip reserves its space from the start |

---

## 9. Phase G — The 3D globe ✅ OVERHAULED 2026-09-07 (branch `globe-overhaul`)

> **G1–G9 shipped, were reviewed twice, and were then overhauled** after the
> first use on a device came back as "no pinch to zoom, no zoom layers, slow
> tiles, tiles not loading in with zooming". A 14-agent diagnosis with one
> adversarial verifier per dimension confirmed 60-odd defects behind those four
> sentences; *The overhaul* below records what was rebuilt, what was measured
> and what is still owed. The tables that follow it are the history that led
> there and are kept as such — where a row is now fixed it says so.
>
> **Verified on the emulator, not yet on the SM-S942B, and only on the keyless
> imagery path**: this checkout has no ArcGIS key, so every screenshot so far is
> NASA GIBS at z8. Put `arcgis.apiKey=…` in `local.properties` and the same build
> sharpens to z18.

Strictly ordered. Each step is verifiable before the next begins, because
debugging a renderer and a camera at the same time is how weeks disappear.

| ID | Task | Notes |
| --- | --- | --- |
| **G1** | `Camera` and `Quadtree`, no rendering at all | Pure math ported from Rust and unit-tested first. Includes `CameraMatrixConsistencyTest`: CPU `project()` and the matrices handed to Filament must agree to sub-pixel accuracy |
| **G2** | Filament host | `SurfaceView` + `UiHelper` + `SwapChain` + `Choreographer`. Translucent, below the window, so Compose chrome draws on top |
| **G3** | Solid-colour sphere | Proves the pipeline before textures exist |
| **G4** | Tile atlas | One 4096² RGB565 atlas, 256 slots, 32 MB. **Now z0–z2 pinned (21 tiles, the reference's `BASE_LOD`), 235 evictable.** z0–z3 (85) was a third of the atlas spent on a floor the traversal rarely draws, and it left 171 slots for a frame that requests up to 238 — see *The overhaul*. **The desktop's 512-texture LRU is 128 MB and would OOM immediately** |
| **G5** | Tile pipeline | **Now a z-bucketed queue — coarsest level first, newest first within a level — 8 workers, one process-scoped OkHttp client with a 96 MB cache and two interceptors.** Was one LIFO stack and 4 workers, which fetched the deepest leaves first and their ancestors last |
| **G6** | Arc and markers | Triangle-strip ribbon, not `GL_LINE_STRIP` — line widths above 1 are unreliable on mobile GPUs. Labels rendered in Compose from CPU-projected positions |
| **G7** | Gestures | **Now a pure-JVM recogniser with a distance latch per axis and no clock**: pan and pinch concurrent, rotate and tilt latched. The 60 ms / 12 dp mode lock this row used to describe is what made pinch unreachable — it was measured from the *first* finger and never re-opened |
| **G8** | Crossfade in | **The still map from C3 fades *out* over the surface.** Fading the surface in never worked: a Compose alpha on the node hosting a below-window `SurfaceView` does not reach the SurfaceControl |
| **G9** | Accessibility | A `SurfaceView` is invisible to TalkBack: content description, custom actions, and visible ± and compass controls |

**Imagery, decided 2026-09-07:** the keyless Esri endpoint the desktop uses is not
licensed for this app (its item licence forbids offline tile export outright), so
the provider is **ArcGIS Location Platform World Imagery with an API key, to z18**,
and **NASA GIBS Blue Marble (public domain, keyless, z8)** when the build has no
key. PLAN.md §11 has the terms and the one clause still to confirm with Esri.

### The overhaul

Four sentences from the first real use — "No pinch to zoom. Also no zoom layers
when zooming in. slow loading of tiles. Or tiles not loading in with zooming" —
and what each turned out to be. Every cause below was confirmed by an adversarial
verifier re-deriving the finder's arithmetic against the code, and every fix has
a test that would have caught it.

| Sentence | What it actually was | What changed |
| --- | --- | --- |
| No pinch to zoom | The classifier decided pan-or-zoom once, on a 60 ms clock measured from the **first** finger, and never re-opened when the second landed. Real hands land 30–120 ms apart, so a pinch could only ever pan — at any speed. Even a simultaneous landing needed 24 dp of separation change inside 60 ms, because the score multiplied a *half*-separation by a stale frame. | `math/GestureRecognizer.kt`, pure JVM: pan is live, zoom latches on separation alone, rotate and tilt latch on their own axes and exclude only each other. No clock. Every latch absorbs its slop instead of repaying it as a 1.2–1.4× pop. `GlobeCameraState` owns *all* motion, so a touch stops a fling and ± during a fling does not fight it. 27 recogniser tests and 21 camera tests on the JVM; 5 instrumented tests drive a real surface with a second finger 80 ms late. |
| No zoom layers when zooming in | GIBS stops at z8 (611 m per texel) and the camera went to 637 m above the surface — ~11 doublings of pure magnification. The interactive zoom clamped to `MIN_ALTITUDE`; only the *fit* knew the imagery's floor. | Provider decision above. The zoom floor is now the provider's finest texel at one pixel, one octave lower, threaded from `GlobeSession` into the camera. `TileKey` re-packed over a `Long` (the `Int` packing threw past z12 on the render thread). `Quadtree.MAX_LOD` and `FINEST_TEXEL_RADIANS` became functions of the provider's level. |
| Slow loading of tiles | One LIFO stack under a breadth-first traversal: the deepest leaves were fetched first and the ancestors they fall back on last, and the pinned warm-up pushed z0 *first*, so the one tile the whole pyramid falls back to arrived 85th. Four blocking workers against an HTTP/1.1-only origin (the KDoc's "HTTP/2 multiplexes anyway" was false). GIBS's three-day `max-age` on an immutable layer. A new 96 MB `okhttp3.Cache` opened over the same directory per session and never closed. | `TileQueue`: coarsest level first, newest first within a level, a prefetch bucket below all. 8 workers (the reference's figure). `TileHttp`: one process-scoped client, connect 4 s / read 6 s / call 8 s, a network interceptor writing a year of `max-age` for immutable providers, an application interceptor serving a real cache hit on `IOException`. Pinned floor 85 → 21 tiles. Measured against GIBS: 24 tiles 2.08 s → 1.07 s at 12 workers. |
| Tiles not loading in with zooming | 238 distinct non-pinned requests per frame into 171 evictable slots: the atlas evicted ~5.7 tiles a frame *forever* with a parked camera, `hasWork` never went false, the loop never settled, and the victims were the z4–z7 ancestors — the fallback pyramid itself. Plus a decoded tile that had left `pending` and not yet reached `resident` was re-requested and re-decoded, and a flat 30 s memo on any failure with nothing to wake the loop when it expired. | The structural budget above, incidence folded into the split test (limb tiles cover fewer pixels than their chord says; worst case 238 → 208), no depth sort (it changed the mesh signature on 49 of 60 pan frames when the tile set changed on 7). Hold-before-ready, a bounded newest-first ready deque, exponential backoff 500 ms → 30 s with a permanent memo for 400/404, `retryDue` and `meshDirty` folded into `wantsFrame`, uploads on a 3 ms time budget. |

Also fixed along the way, each recorded in its KDoc: the still-map crossfade
(fades the map out over the surface — see G8); a camera carried from the hero
into the immersive screen is rescaled by the height ratio, because the focal
length scales with height and the same camera was the same view magnified 2.3×
with both airports off the sides; `renderedCamera` is committed after
`beginFrame` succeeds; a theme change on a settled globe redraws; two attached
surfaces share one scene through an update owner; `GlobeMesh` hoists its
trigonometry (four transcendentals per vertex → three multiplies); the ribbon is
rebuilt only when the camera moves; `TileAtlas.touch` is O(1); the credit is a
structured `ImageryAttribution` that follows the provider onto the glass, into
Settings and onto the Licences screen.

**Verified:** 103 JVM tests and 5 instrumented tests green, `./gradlew build`
green, `checkInvariants` green; on the emulator, the crossfade, the fit, the
hero → immersive → hero carry, and the pinch/pan/tap/nested-scroll gestures.
**Not verified:** the SM-S942B (GPU timing, the 3 ms upload budget); Esri
imagery (no key in this checkout); airplane mode after a session.

### Where the build diverged from the table above

Each of these is a decision taken against a measured or compile-checked fact,
not a shortcut. They are recorded here because the table reads as though they
did not happen.

| Planned | Built | Why |
| --- | --- | --- |
| G2: a **translucent** swap chain | An **opaque** one | `SwapChain.CONFIG_TRANSPARENT` does not exist in Filament 1.75.1 — established by compiling a probe, as this project requires. It is also unnecessary: the surface is composited *below* the window, so it has nothing behind it to blend with, and an opaque swap chain is the cheaper path through the compositor. |
| Materials compiled at runtime | `matc` at **build time**, `.filamat` blobs committed as assets | `filamat-android` is 33 MB of JNI for a compiler that would run four times ever. Committing generated binaries matches what the airport index and the baseline profile already do. |
| The desktop’s `MAX_LOD` | **The provider's `maxLevel`: 18 with a key, 8 without** | Was a hardcoded 8 because GIBS has no imagery above z8. That ceiling — with a camera still allowed down to 637 m — was eleven doublings of zoom in which nothing could sharpen, i.e. the whole of "no zoom layers". The quadtree now takes the level as a parameter and the fit and the zoom floor derive from it. |
| The desktop’s 256 visible tiles | **256 leaves, bounded by the atlas structurally** | Was 160, justified against 171 evictable slots while counting only leaves; the traversal also requests every non-pinned ancestor, and a frame asked for up to 238 into 171 — a thrash that never converged with a parked camera. The budget now counts requested non-pinned nodes against the atlas's actual evictable slots, and a test sweeps real viewports and tilts to prove it fits. |
| Vulkan | Vulkan, then **OpenGL** | Engine creation is attempted on Vulkan and falls back. A device that fails both reports `GlobeSupport.NoRenderer` and never sees a globe. |

### What the globe is reachable from

| Where | What |
| --- | --- |
| Route detail | A **Flat/Globe switch in the app bar, flat first.** The flat outline hero is what the screen has always drawn and it is what opening a route still gets you; the globe is one tap and takes the **deep hero** — 44% of the window, running under the status bar. |
| Route detail app bar | A fullscreen action opening `Destination.ImmersiveGlobe`, the globe with the window to itself and the figures on a plate. It appears **with the globe**, since it opens something the screen is otherwise not showing. Both it and the switch are **absent, not disabled, where there is no renderer** (3B). |
| Stats · visited network | A Flat/Globe toggle in the card header, **Flat first**. Flat is honest about density and is right for a regional logbook; the sphere is honest about distance and is what a logbook spanning hemispheres needs. The toggle is absent where there is no renderer. |
| Settings · About | The one line in the app that says a device *cannot* draw a globe, plus the GIBS credit in reading order. |

### What the device found

**Every one of these was invisible to the build.** It compiled, the 18 math tests
passed, lint was clean and `checkInvariants` passed through all seven of them.
All seven are fixed.

| # | Symptom | Cause | State |
| --- | --- | --- | --- |
| 1 | `BufferOverflowException` on the first tile, every launch | `Texture.PixelBufferDescriptor` was built with the default stride of 0. Filament reads 0 as "tightly packed" and resolves it against the **texture** width, not the region width — so a 256-wide tile into a 4096-wide atlas asked for sixteen times the buffer. | Fixed: the stride is named. |
| 2 | The whole planet invisible, backdrop only | The tile shader took its surface normal from `material.worldPosition`. Filament renders **camera-relative**, so in the vertex stage that is the vertex *minus the camera* — the view ray. Every limb dot came out near -1 and clamped the imagery to zero alpha. | Fixed: `getPosition()`, which is object space, and the mesh is on the unit sphere. |
| 3 | Imagery froze a second or two after appearing, then never updated again | `rebuildTileMesh` acquired two ring buffers and returned early when there was nothing to draw — without giving them back. Three such frames at startup (before any tile has imagery) emptied a three-deep ring permanently, after which the geometry silently held whatever it last uploaded. | Fixed: both early-outs release. |
| 4 | The backdrop sphere rendered at first and then disappeared | Its vertex and index `ByteBuffer`s were locals in `init`. Filament keeps the address rather than copying, so the first garbage collection freed geometry the driver still owned. | Fixed: they are fields. |
| 5 | Most of the sphere drew black, with a scatter of tiles from the wrong place | **Filament flips `v` on UV0 by default.** The atlas rectangles are computed against the texture this code uploads into, so `getUV0()` returning `1 - v` turned a slot in row 3 into a lookup in row 12 — a slot holding another tile, or far more often one nothing had been written to. | Fixed: `flipUV : false`. |
| 6 | A scatter of grey teeth over the sphere, worst zoomed out | The tile grid *inscribed* the unit sphere, so each quad’s flat face sagged inside it — 0.033 at z0, thirty times the 0.001 the backdrop sits below the surface, so the backdrop poked through. | Fixed: the grid circumscribes (`1 / cos(half the quad diagonal)`), and the coarse levels are tessellated finer. |
| 7 | A grey ellipse capping each pole | Web Mercator stops at 85.05°, so the top and bottom tile rows leave bare sphere. | Fixed: the outermost row of vertices is pushed to the pole and keeps its own `v`, stretching that tile’s last texel row over the cap. |

Two lessons worth keeping, because both cost hours:

- **Filament does not copy anything.** Not pixel buffers, not vertex buffers, not
  index buffers. Every direct `ByteBuffer` handed to it must stay reachable from
  the Java heap until its callback fires, and one that is dropped instead fails
  *later*, under GC, looking exactly like a driver bug.
- **A rendering fault has no stack trace.** The only instrument that worked was
  writing a value into the fragment shader and looking at the colour: solid green
  to ask whether the geometry rasterises, `fract(uv * 16)` to ask what is in the
  atlas, `vec4(uv.x, uv.y, 0, 1)` to ask what is being sampled. That last one is
  what caught defect 5 in a single screenshot after an hour of theories, because
  it showed `v ≈ 0.81` where the buffer held 0.19.
- **Read the defaults of anything that transforms your data on the way in.**
  `flipUV` is a one-line default in a file format nobody re-reads, and it made a
  correct atlas, correct rectangles and a correct mesh add up to a black planet.

### What a code review then found, and what verifying it found

`/code-review` returned eleven findings against the finished phase. Two of them
were wrong constants that would have crashed or degraded in the field, and both
came from changes made *during* Phase G rather than from the original design —
which is the argument for running it at the end of a phase rather than the
middle.

| Symptom | Cause |
| --- | --- |
| `BufferOverflowException` on the eighth visited leg | `MAX_RIBBON_VERTICES` was `SAMPLES * 2 + 8`, sized for one 256-point arc. When `setArcs` took a list for 1G the visited network began feeding every leg at 32 samples — 66 vertices each — and the ring behind the growing `RibbonBuffer` did not grow with it. The budget is now 16,384 and the buffer **refuses** vertices past its capacity rather than growing, so an unusually large logbook loses its last arcs instead of throwing. |
| The tile budget silently half of what the mesh could ask for | `MAX_TILE_VERTICES` was derived from substeps of 16/12/8/6 and was not revisited when they were raised to 32/20/14/10/7/5. Recomputed: 18,997 worst case, so 40,000, and 192,000 indices. |
| A 32 MB atlas outliving the screen | `GlobeSession.acquire` ran in `remember` while its release sat in a `DisposableEffect`, so a composition that was started and thrown away took a reference nothing balanced. Both halves are in the effect now. |
| Status-bar glyphs flipping back over imagery | Two writers. `FlightPlannerTheme` re-applies the insets controller on every one of its own recompositions, so it won whenever `SystemBarsOverMedia`’s keys had not changed — LIGHT to CHART, for instance. The override is now a flag the theme reads, and the theme is the only writer. |
| The globe rendering at 60 Hz behind a page | The frame loop re-posted unconditionally. It now stops when hidden and skips the work when settled. **`onVisibilityAggregated` is not enough**: a Compose scroll leaves the `View` VISIBLE in a visible window, so the host measures its own box and tells the surface. |
| A Filament engine built on the main thread, inside composition | `resolveSupport` created and destroyed a probe engine to answer "is there a renderer" — from `remember`, in the nav host, in Stats and in Settings — and then `acquire` built another. It now reads `reqGlEsVersion`, and `acquire` still caches `NoRenderer` if a device that claims GLES 3.0 fails anyway. |
| One AVWX request per fullscreen toggle | The immersive screen resolved its own `RouteDetailViewModel` against its own back-stack entry. It borrows the route detail’s entry instead, via `routeDetailEntry`. |
| The same tile fetched twice after a fast pan | `request`’s overflow path cleared `pending`, which is also the in-flight set. It now drops only the keys still sitting in the stack. |
| A fling recomposing the whole glass — **this fix did not hold; see the second review below** | Reading `cameraState.camera` at the canvas’s own scope made the limb, the input box and the semantics subscribers. The read moved into the layers that are actually a function of the camera; the limb reads in the draw phase, and the semantics build their sentence inside the `semantics` lambda. |
| No card morph in globe mode | The key sits on the still `RouteMap` **under** the sphere. The `SurfaceView` is a sibling and never enters the shared-element overlay, so the card’s map grows into the hero and the globe crossfades in over it — which is the crossfade C3 already asked for, now with somewhere to start. |

Two more surfaced only because the frame loop was measured rather than assumed,
and both had been quietly true all along:

- **`popNewest` dropped stale keys from the stack without removing them from
  `pending`.** A tile that became resident between being requested and being
  popped stayed in `pending` for the life of the loader, because `request`
  returns before the add for a resident key. The loader therefore always claimed
  work it was never going to do — invisible until something asked it whether it
  was idle.
- **`nextFadeExpiry` is `Float.MAX_VALUE` when nothing is fading**, so comparing
  the clock against it directly is always true. The first version of the settled
  test did exactly that and looked like the gate being ignored.

Measured on the emulator, route detail in globe mode, process CPU over five
seconds: **184 jiffies at rest before, 0 after**, and 28 with the hero scrolled
off. The globe still wakes on a drag, on the zoom controls and on a tile
arriving.

### The material toolchain is not in the build — but a stale `.filamat` now fails one

`src/main/materials/*.mat` are still compiled to the committed
`src/main/assets/materials/*.filamat` by hand, with:

```
matc --platform=mobile --api=opengl --api=vulkan -o <out>.filamat <in>.mat
```

`matc` ships in the Filament release archive (`filament-<version>-windows.tgz`),
not in the AAR, so it is not on any developer machine by default and there is no
Gradle task that recompiles them. **What is closed** is the forgotten-recompile
path — and it took two halves, because a checksum manifest on its own attests
file identity rather than the compile relationship:
`:feature:globe:verifyFilamatFreshness` (wired into `check`, so `./gradlew build`
runs it) hashes each `.mat` and each `.filamat` against a committed manifest,
`src/main/materials/checksums.txt`, and fails the build naming any pair that has
drifted — a `.mat` edited without its `.filamat` regenerated, or either file
changed out from under the manifest. After a legitimate `matc` recompile,
`./gradlew :feature:globe:updateFilamatChecksums` rewrites the manifest and it is
committed with the new blob — the same regenerate-and-commit shape the baseline
profile already has.

**And `updateFilamatChecksums` refuses to bless a lie.** Regenerating the manifest
after editing a `.mat` *without* recompiling would otherwise record the new source
hash beside the unchanged blob and leave the guard permanently blind to that
material — the exact hole a review found. A real `matc` run on a changed source
produces a different blob, so the task fails when a `.mat` moved and its
`.filamat` did not, printing the `matc` line; `-PallowUnchangedFilamat=true` is
the escape hatch for an edit that genuinely compiles to identical bytes. All three
behaviours are verified by planting the violation, per CLAUDE.md.

A checksum manifest rather than a `matc` integration because `matc` cannot be
assumed present; wiring `matc` itself into the build is still outstanding but no
longer load-bearing.

### Chrome over a photograph

Three separate things stopped being readable the moment imagery ran under them,
and all three now take the same answer the globe already used for its own labels
and its credit: **a plate**, `surfaceContainer` at 0.82 with an `extraSmall`
corner. The route title, the back button, the switch and the fullscreen action
all sit on one while the sphere is behind them, and go bare again the moment the
page scrolls up and the bar is `surface`.

The system’s own glyphs cannot be plated, so they get the only lever the platform
offers: `SystemBarsOverMedia` in `:core:designsystem` flips
`isAppearanceLightStatusBars` to light while imagery is under the clock, and hands
it back on dispose. **That is not a retreat from the empty-bars invariant** —
nothing is painted, the imagery still runs unbroken past the clock, and only the
colour the system draws its own glyphs in changes. The navigation bar is left
alone: it sits over ordinary page content here, so flipping it would make the
gesture handle harder to see rather than easier.

**A fourth thing was a real bug rather than a contrast problem.** The label plates
are placed by a `layout` block at a projected pixel, and Compose lets a child be
placed outside its parent *and drawn there*: a dot near the bottom of the sphere
put its plate on the page below the hero, next to text it had nothing to do with,
and the limb overlay drew its rim across the page for the same reason. The globe
canvas is now `clipToBounds`.

### Three more the device found, after the imagery was right

| Symptom | Cause |
| --- | --- |
| A bar’s height of dead white between the sphere and the first line of the page | The Scaffold’s `contentPadding` includes the app bar’s height, and the full-bleed hero already occupies that space. In globe mode the body now takes the start, end and bottom of that padding and drops the top. |
| An airport label sitting behind the app-bar chrome | The label may not move off its point, so it fades instead — the same treatment case 2 gives a label at the limb. The host says how tall its chrome is (`topChromeInset`); the globe fades a label that rises into it, and a second fade covers the bottom edge, where a plate has no room to hang below its dot. |
| Going back from the route detail read as two pages stacked, not as one card | `sharedExit` was the mirror of `sharedEnter`, so both screens sat near half opacity through the middle and the travelling element was lost in a dense page of figures over a list of cards. The exit now runs on the **fast** effects spring while the entrance runs on the default one — the same asymmetry `fadeThrough` already made, for the same reason. |

### Why the flat hero is not the globe drawing an outline

The obvious economy — one renderer, two skins — does not pay. The coastlines could
reuse `RouteGeometry.ribbonInto` as another set of arcs, which is genuinely close
to a switch, but **the whole world’s coastline would be re-projected on the CPU
every frame**. The flat `RouteMap` projects once into a cached `Path` because its
frame never moves; a globe’s does, every frame, and there is no measurement saying
that is affordable. Land *fill* is worse: it needs polygon triangulation on a
sphere, which the flat map gets for free from an even-odd `Path`.

So the two heroes stay two drawings. The flat one is a diagram and the globe is a
photograph, and each is drawn by the thing that is good at it.

### What a second, much larger review found

The ✅ above was written after seven device defects and eleven code-review
findings had been fixed. A second pass — fourteen review dimensions with
adversarial verification, a separate pass on Android's own gesture navigation, a
completeness critic, and another session on a device — returned **about 140
findings, 121 of them verified.** The phase is not complete; the tables below are
what is left.

**Read the calibration note at the end of this section before treating a row as
settled.** Two verdicts flipped between identical runs, and one whole batch of
forty confirmed forty out of forty, which is not a number an honest adversarial
pass usually produces.

#### The opening frame is wrong, in four independent ways

This is the feature's own promise — *"the first frame answers the question the
screen is about, without the user touching anything"* — and it is the thing every
user sees first. All four live in `GlobeFit`.

| Symptom | Cause |
| --- | --- |
| Both airports off the left and right edges, in the hero and worse in fullscreen | `frameRoute` takes no viewport. The projection's horizontal half-angle is `atan(tan(fovY/2) · w/h)`, so the near-square hero has about 0.7° of margin for a leg spanning ±14.9°, and the portrait immersive window has 6.4°. Moving between them multiplies every on-screen offset by 2.27 and carries the camera over unchanged, because `isNewSubject` is false. **Re-fit does not recover them**: it animates to the same aspect-blind camera. |
| A long leg opens on a bare planet | `frameRoute` centres on the *degree* midpoint, not the arc midpoint — up to 68° off the great circle it is framing, and the comment excusing that is arithmetically false. Above roughly 12,000 km both airport plates are at zero alpha in the opening frame; at ~138° (KJFK→WSSS) the arc is behind the limb too. The A350-1000 and 777-200LR in the seeded fleet reach that. |
| A wide visited network framed with its outliers behind the limb | `inverseZoom` is clamped to `[1, 8]`, so the fitted `distance` can never exceed **2.5** — a 66.4° horizon — while `MAX_DISTANCE` is 10, an 84.3° one. Every subject whose furthest point falls in that gap is hidden when a legal camera existed that would have shown it. |
| Every leg under ~776 NM opens at the same altitude, and cannot be zoomed out of it | The same clamp binds from the other end: `0.9 / sin(θ/2) ≥ 8` whenever θ ≤ 0.2255 rad. A 71 NM leg is ~9% of the hero's height, drawn at the same camera as a 700 NM one. The Rust original has the same constants but `MAX_LOD` **18** against Android's **8**, so on the desktop you zoom in and recover the leg; here the imagery only stretches. The seeded ATR 42-600 has a 703 NM range and `minDistanceNm` defaults to null, so **every route it can fly** sits at or under the clamp. |

A fifth thing follows from the first: `topChromeInset` is the Scaffold's whole top
padding, ~88–100 dp, and `edgeAlpha` fades anything above it plus 28 dp — which is
exactly where the fit puts the endpoints. On an ordinary leg the departure plate is
never drawn, and a plate whose anchor is just off-screen is *sliced* by
`clipToBounds` rather than dropped, so it renders as a two-letter fragment.

#### One boolean is asked three questions and gets each one wrong somewhere

`overImagery` decides whether the chrome is on glass, whether the app bar is
transparent, and what colour the system draws its glyphs. Those are three
different questions.

| Symptom | Cause |
| --- | --- |
| The glass chrome drops after a nudge, with 390 dp of photograph still under it | `overlappedFraction < 0.01f`. For a pinned single-row bar `heightOffsetLimit` is minus the bar's own 64 dp content height (~192 px at 3×), so the predicate fails after **~1.9 px, about 0.6 dp** — the first frame past touch slop. The KDoc immediately above claims the treatment ends where the imagery does. |
| An opaque slab across the status bar and the live imagery | `scrolledContainerColor` wins at the same threshold, and `TopAppBar` paints it with a `drawBehind` on the outer Box while `windowInsetsPadding` is on the inner layout, so the rect covers the status inset as well as the bar. This is how every bar in the app behaves; it is wrong *here*, over a photograph, not everywhere. |
| White status glyphs on a near-white page | The flag is gated on hero *mode*, not on imagery existing. Before the first tile mesh — every entry into globe mode, again after each 2 s session teardown, and permanently offline — the pixels under the clock are the still map over `background`. Beyond ~8,200 km the top strip is `GlobeInk.space`, which *is* `colorScheme.surface` by design. |
| Dark glyphs over full-bleed satellite imagery | `SystemBarsOverMedia` has exactly one caller. The immersive globe — the only surface in the app that is edge-to-edge photograph — never raises it, and entering it *lowers* the flag the detail screen had raised. |
| The plates snap off a frame before the bar arrives | `overImagery` is a step; the bar's container colour crosses the same threshold through `animateColorAsState`. Title and icons sit bare over imagery for the length of a spring. |

#### What only shows up above the globe module

Fourteen dimensions looking inside `:feature:globe` found none of these.

| Symptom | Cause |
| --- | --- |
| Every globe-mode scroll recomposes the whole app | `FlightPlannerTheme` now reads `overMedia.value` at its own scope, and its body calls `dynamicLightColorScheme(context)` unremembered. `ColorScheme` has no `equals`, `MaterialTheme.Values` therefore compares it by identity, and `_localMaterialTheme` is a `staticCompositionLocalOf` — so a new identity invalidates every consumer in the subtree, bypassing skipping. Each step of `overImagery` does that, on the densest screen in the app, with a live Filament surface attached. |
| Five unrelated transitions changed appearance | `sharedExit()` was retuned in `:core:designsystem` for a reason stated entirely in terms of a shared element. It is wired to `Plan`, `RouteDetail` and `ImmersiveGlobe`, and `Plan`'s exit fires on **every** navigation away from Plan — Settings, Fleet, Airports, Logbook, Stats — none of which share an element with it. |
| Two divergent answers to "is there a renderer" | `FilamentProbe` is still in `src/main` and still builds a real engine for the self-check, while `resolveSupport` was deliberately changed to read `reqGlEsVersion`. A device that claims GLES 3.0 and fails engine creation reports PASS while the globe hides its controls. |
| The Licences screen credits nobody for the imagery | Its KDoc still explains the omission in the past tense — "Phase G's globe, which does not exist yet". The branch adds the credit to About and to the glass and skips the one screen whose whole job is attribution. Settings' About also still says a tile provider "is still to come", ten lines under the line saying the globe is available. |
| A toolchain upgrade riding inside a feature branch | The entire diff to `gradle/libs.versions.toml` is `agp` 9.3.2 → 9.4.0. Nothing in the globe requires it; every dependency it uses was already in the catalogue. Reverting the globe now means also deciding on an AGP bump nobody wrote down. |

#### States a user can reach and cannot get out of

| Symptom | Cause |
| --- | --- |
| The globe becomes permanently un-pannable | The pan *target* is never clamped to the reachable disc — only the anchor is. One frame of a drag started off the sphere runs Newton at the singular limb ring and sets `centerLon` to about −290,000°. Past ~65,536° the float32 ulp exceeds `2 · PAN_EPS_DEGREES`, the Jacobian determinant goes to zero and `panTo` returns unchanged forever. |
| Momentum that cannot be stopped, and compounds | `fling` runs its own local `Animatable` in the composable's scope. `GlobeCameraState.stop()` only stops the one `animateTo` uses, so a touch-down does not stop it, a second flick runs a second concurrent decay, and re-fit, zoom and double-tap all write the camera on the same frames. |
| An aborted back swipe flings the planet | The gesture loop exits on `pressed.isEmpty()` and then unconditionally emits `End(Pan, velocity)`. Compose delivers a system pointer-cancel as a synthetic event with `pressed = false`, indistinguishable from a release unless consumption is checked — so every gesture the system steals ends in momentum the user never released. |
| A blank full-screen rectangle with no way out | `ImmersiveGlobeScreen`'s `if (globeRoute == null) return@Surface` sits *above* the overlay that carries the collapse button, the controls and the plate, and no navigation suite is shown over this destination. Reachable by process-death restore with the immersive globe on top. |
| Offline, globe mode is the flat map wearing the globe's chrome | `hasDrawnImagery` needs one resident tile, and no tiles ship in assets. With no network `globeAlpha` stays `0f` forever — but the alpha-0 layer covers the surface, the limb and the markers and **not** the input box or `overlay()`. So the still map is drawn under a full-opacity camera stack and imagery credit, driving a camera nothing renders, with fullscreen still on offer. `TileAtlas.basePinned` — KDoc: *"the globe is then legible offline"* — has zero readers in the repo. |

#### The globe fights the platform, and the page it sits in

| Symptom | Cause |
| --- | --- |
| The top 44% of the route detail cannot be scrolled | `globeGestures` consumes every position change unconditionally, from the first MOVE, before the classifier decides anything. Correct for the immersive screen, which owns the window; wrong for the deep hero inside `Column(verticalScroll)` and the 260 dp band inside the Stats `LazyColumn`. |
| A spin started near either window edge navigates away | ~30 dp of both edges is the system back-gesture strip, full window height — **14.4% of the width**, measured — and nothing in the app calls `systemGestureExclusion`. Taps still reach the window; drags are pilfered. The platform caps any fix at 200 dp of vertical extent. |
| The hero's controls under the navigation bar and the cutout | `GlobeCameraControls` and `GlobeAttribution` in `DeepGlobeHero` take no window insets at all — only `.align().padding(GlassGutter)` — unlike the immersive screen, which does it correctly. In landscape the credit lands under the back button and the camera stack under the app-bar actions, and the stack is itself taller than the 132 dp hero and is clipped. |
| TalkBack is told about the globe and can read nothing on it | The full-size input/semantics Box is composed *after* the marker layer and is `fillMaxSize()` with a `contentDescription`, so Compose prunes every label plate and node dot as covered — the things `GlobeSurface`'s own KDoc calls "the only part of the globe TalkBack can read". |

#### Resources, and the "fully offline" claim

| Symptom | Cause |
| --- | --- |
| Backgrounding holds 32 MB of atlas, the engine and the tile workers forever | The session is released on the last *detach*. Backgrounding never detaches — it only flips `onVisibilityAggregated` — so the teardown timer never posts and nothing implements `onTrimMemory`. |
| Scrolling Stats past the card and back rebuilds the engine on the main thread | The card is a `LazyColumn` item, so it is disposed when it leaves the viewport; two seconds later the session is destroyed, and scrolling back re-runs `Filament.init()`, `Engine.Builder().build()` and a 4096² RGB565 allocation from a `DisposableEffect`. |
| Panning offline fails, and every session re-validates the 85 pinned tiles | Neither interceptor PLAN.md §5.5 specifies exists — no minimum `max-age` rewrite, no `ForceCacheOnFailureInterceptor`. Weigh this against the app's own "fully offline, no server" description. |
| A second HTTP stack, and a cache that is never closed | `GlobeSession.sharedHttpClient` is a bare `OkHttpClient()` with its own pool, dispatcher and DNS — the KDoc says the opposite, and tile requests lose the User-Agent `:core:network` exists to set. `TileLoader`'s 96 MB `okhttp3.Cache` is `Closeable` and `shutdown()` never closes it, so a second one is opened over the same directory on every session rebuild. |
| ~4.3 MB of dead native code per ABI | `filament-utils-android` is declared and never referenced; the module uses only `filament-android`. |

#### Rendering and geometry

| Symptom | Cause |
| --- | --- |
| The globe arrives as a hard cut, and G8's crossfade does not exist | The fade is `graphicsLayer { alpha = … }` on the `AndroidView` hosting a Z-below `SurfaceView`, and **a Compose alpha never reaches a separate compositor layer**. The same is true of a Compose *clip*, which is the bug recorded under *A SurfaceView cannot be clipped* below. (This row used to explain itself via "the surface's `PorterDuff.CLEAR` hole punch only zeroes the offscreen". On the pinned SDK the punch is `Canvas.punchHole`, which hwui propagates, so that sentence is wrong and must not be re-used — the layer, not the punch, is the reason.) |
| ~~The rim and its glow break by 160–300 px whenever the view is tilted~~ **Fixed 2026-09-08.** | `Limb.projectInto` dropped points behind the camera plane with `continue` and wrote the survivors **compacted**, so the consumer could not tell the run was broken and drew a straight segment across the gap. It now writes all `SAMPLES` slots, `(NaN, NaN)` for a culled point, and returns only the count in front of the camera; `limbPath` in `GlobeSurface` starts the walk just after the gap, lifts the pen across it, and closes only when nothing was culled. `LimbTest` (new — `Limb` had no test file) pins the contiguous-run and NaN-in-place contract. |
| Tile thrash of exactly the kind `MAX_VISIBLE_TILES = 160` was set to prevent | The budget counted leaves, but `request` fires for every visited node and every non-pinned ancestor takes a slot. Counted by porting `collectVisibleTiles`: **227 distinct requested keys** against `SLOTS − PINNED_SLOTS` = 171. |
| Two surfaces drawing each other's tiles | `GlobeScene` is a process singleton but `lastVisibleSignature`, `lastBuiltGeneration` and the tile buffers are per-scene, not per-view. Every hero↔immersive transition, and every held predictive-back drag, has two attached views selecting different LOD (`focalPixels` is height-dependent) and invalidating each other. |
| The ribbon re-derived from scratch every frame for every leg | `appendArc` allocates roughly ten short-lived `Vec3` per visible sample and `rebuildRibbon` runs unconditionally, against a KDoc claiming "one pass over 256 points… a few microseconds". |
| A declined frame recorded as rendered | `renderedCamera` and `renderedViewport` are assigned *before* the `renderer.beginFrame` success check, so a frame the driver declines still satisfies the settle test on the next vsync. |

#### What this section itself got wrong

- **"A fling recomposing the whole glass" is recorded above as fixed. It is not.**
  Three findings refute it independently: `GlobeControlsHandle`'s `bearingDegrees`
  and `isRotated` are plain getters dereferencing a `mutableStateOf`, read at the
  *host's* call site, so the subscription lands on the host's overlay lambda; and
  the `semantics` lambda's camera read runs inside `observeSemanticsReads`, which
  subscribes the layout node exactly as a composition read would.
- **`CompactHeroHeight`'s KDoc says the globe hero and the still hero "agree at
  that width". They do not.** The still hero is laid out inside the content
  padding, so all 132 dp is visible; the globe hero is full bleed under an ~88 dp
  bar, leaving about 44 dp of sphere.
- The nav host describes the immersive transition as a box growing on a spatial
  spring. The tokens it names are pure fades whose own KDoc says "no scale".
- **The three math files with confirmed defects are the three with no tests.**
  `GlobeCamera`, `CameraMatrices` and `Quadtree` have test files; `GlobeFit`,
  `RouteGeometry` and `Limb` do not, and every confirmed maths defect is in the
  untested half. G1 claims the maths was "unit-tested first".

#### What this review did not settle

Recorded because a confirmed count reads as more certain than it is:

- **Two verdicts flipped between identical verification runs** — the stock
  `public`/`map` glyphs on the hero switch, and the design tokens `:app` now
  imports from `:feature:globe`. Both are contested, not settled either way.
- **One batch of forty findings refuted none of them.** Four were downgraded, which
  is real work, but a pass that refutes nothing has not proved everything. The
  high-severity rows in it were checked further; the medium tail was not.
- **Per-finding verification is the wrong shape.** The first run fanned out to
  about a hundred verifiers and had to be killed. One verifier per dimension,
  ruling on that dimension's findings together, is cheaper *and* better informed —
  it can see duplicates between findings, which per-finding verifiers structurally
  cannot.
- **The device found things no reviewer did**, and reading found things no device
  session could. Neither substitutes for the other.

### What is still owed

The review above is the list. In the order the defects actually cost a user
something, with the state after *The overhaul*:

1. ~~**`GlobeFit`.**~~ Done before the overhaul (viewport-aware, arc midpoint); the
   overhaul added the provider's depth as a parameter and a test file.
2. ~~**The `overImagery` / `SystemBarsOverMedia` mechanism.**~~ Done, 2026-09-07.
   `RouteDetailScreen` no longer gates on hero *mode*: `GlobeSurface` now reports
   whether its own imagery is actually the whole picture (`onImageryVisible`,
   false before the first tile and offline, true once the crossfade completes),
   and the screen turns that into two separate predicates —
   `imageryCovers(heroHeightPx, scrollPx, depthPx)`, a pure function
   (`GlobeChrome.kt`, unit-tested) — one for the app's own chrome against the
   *measured* app bar height, one for the system's glyphs against the shorter
   status inset alone. `ImmersiveGlobeScreen` now raises the flag itself. The
   `FlightPlannerTheme` recomposition this caused is also fixed: the resolved
   `ColorScheme` is `remember`ed, and the flag's own read moved into a small
   `SystemBarsAppearance` composable so stepping it invalidates only that, not
   the whole theme.

   **Finished 2026-09-08, after `/verify` failed it on a device.** The first
   build was right about the hero and wrong about everything with two callers:
   `LocalBarsOverMedia` was a shared `Boolean` and every caller's `onDispose`
   wrote `false` into it unconditionally, so when the immersive globe raised the
   flag and the route detail underneath it then disposed — which is the order a
   navigation transition disposes in — the outgoing screen cancelled the
   incoming one's request. The immersive globe ran full-bleed satellite imagery
   under dark glyphs, and coming back left the hero stuck dark too, until the
   app was restarted. It is now a **count**, `BarsOverMediaRequests`: the
   question the theme asks is "is anybody still asking", not "who wrote last".
   Six pure-JVM tests, `oneCallerLoweringDoesNotClearAnother` among them.
3. **The offline path.** Partly done: the cache now stores immutable tiles for a
   year and serves a real hit on `IOException`; `basePinned` was dead and is gone.
   Not verified in airplane mode yet.
4. ~~**The pan clamp and fling cancellation** and the `ACTION_CANCEL`-as-release
   bug.~~ Done: the pan target is held to the disc and the solve verified, one
   motion owner, cancel detected on the Initial pass and released with zero
   velocity — all under test.
5. ~~**`onTrimMemory`**, then the accessibility occlusion.~~ Done, 2026-09-07.
   `GlobeSession` now watches `ProcessLifecycleOwner`'s `ON_STOP` and a live
   `ComponentCallbacks2.onTrimMemory` level (`TRIM_MEMORY_UI_HIDDEN` — the lower
   of the two levels a throwaway probe found still non-deprecated in the pinned
   SDK) and tears itself down immediately on either, rather than waiting on a
   detach that backgrounding never causes. The route and camera it was showing
   are carried through a memento and adopted by whichever session rebuilds next;
   `GlobeCanvas` re-acquires on `ON_START` for a view that never detached, via a
   new `GlobeSession.reacquireIfNeeded` that (deliberately) does not double-count
   the view.

   **That first build crashed the app on every Home press with a globe on
   screen, and `/verify` caught it. Finished 2026-09-08.** Backgrounding
   detaches nothing — which is the whole premise — so the surface's own
   `RouteRibbon` was still alive, holding two `MaterialInstance`s of the
   `globeOverlay` material, when `GlobeScene.destroy()` freed that material.
   Filament refuses (`destroying material "globeOverlay" but 2 instances still
   alive`) and the process dies. **Freeing ribbons inside `GlobeScene.destroy()`
   would not have been enough**: the view would still hold a renderer, a view, a
   camera and a swap chain pointing into an engine destroyed two lines later — a
   second crash on the next resume.
   So the teardown is now *ordered and coordinated*: `AttachedSurfaces` tracks
   the attached surfaces, `forcedTeardown` releases every one of them before
   anything shared is destroyed, `GlobeSurfaceView.releaseForBackground()` gives
   up its whole Filament footprint without detaching, and `syncLoop()` rebuilds
   it — through the same `buildAgainst` the first attach uses — the moment
   anything wants a frame again. Six pure-JVM tests hold the ordering rule; one
   instrumented test reproduces the crash directly and now passes.
   **Two things this cost, worth knowing:** the instrumented test calls
   `forceTeardown()` rather than backgrounding an activity, because neither
   `ON_STOP` nor a trim callback is reliably delivered under instrumentation —
   the version that waited for one passed against code that crashed on every
   Home press. And `AndroidView`'s callbacks moved from `factory` to `update`:
   `cameraState` and `firstImagery` are `remember(session)`-keyed, so a rebuilt
   session left the `factory` closures writing to dead state — the globe came
   back with imagery drawn but its chrome stuck in the "no imagery yet" look.
   Separately, the input/semantics layer in
   `GlobeCanvas` now paints *before* the limb and marker layers rather than
   after, so an accessibility service's own overlap pruning no longer treats the
   DEP/DEST plates as covered by it — the mechanism a Compose semantics-tree
   query cannot exercise, so this is confirmed by existence/layout tests plus
   `adb shell uiautomator dump` on a device, not proved by the JVM tests alone.
6. **The two things that do not belong to this feature at all**: the
   `sharedExit()` retune is fixed — `Destination.Plan`'s four transitions are now
   gated on whether the other side of the navigation is `Destination.RouteDetail`
   (`NavBackStackEntry.sharesRouteFace()`), so Settings, Fleet, Airports, Logbook
   and Stats get the plain fade-through again and only the Plan↔RouteDetail pair
   keeps the shared-element springs. Not covered by a unit test — constructing a
   real, correctly-patterned `NavBackStackEntry` for a kotlinx-serialization route
   outside the `composable<T>()` DSL was judged more machinery than the assertion
   was worth; verify by navigating Plan→Settings and Plan→RouteDetail back to
   back and confirming only the second one uses the fast fade. The AGP bump
   (9.3.2 → 9.4.0) is kept rather than reverted: nothing in the globe needs it,
   but it was already merged and green, and reverting a working toolchain for
   tidiness costs a decision nobody would then own.
7. **New, from the overhaul's own screenshots:** ~~a label plate can sit under
   the camera stack on the hero~~ — fixed 2026-09-08. `GlobeCameraControls` and
   `GlobeAttribution` report their own `boundsInParent()` (guarded against
   thrash) into `GlobeControlsHandle`; `GlobeLabels` fades a plate whose dot
   projects into either reserved rect, `cornerAlpha` — the same "case 2" drop
   the top inset gets, starting one plate-height above the rect so the plate
   that hangs below the dot clears it too. Measured bounds rather than dp
   constants because the credit plate grows at font scale 2.0 and the stack
   grows a cell when the view is rotated. `cornerAlpha` is pure and tested
   (`GlobeLabelsAlphaTest`). Still open: the Esri offline-cache clause to
   confirm in writing (PLAN.md §11). ~~The 3 ms upload budget and `INCIDENCE_FLOOR` — which turned out to be the wrong knob entirely, see *Tilt destroyed the LOD* — unmeasured on hardware.~~ Measured 2026-09-14, see *The frame callback, measured*: uploads are 0.1 ms at P90 against their 3 ms budget.

### A `SurfaceView` cannot be clipped, and that was the white flash

The user reported the Stats globe "sometimes turns white when scrolling while
still a part is on the screen", and narrowed it: **only while actively pulling at
the end, so the page stretches.** Reproduced on the SM-S942B, and pre-existing —
identical on a clean `HEAD`.

**One cause, two symptoms.** A `SurfaceView` is a separate compositor layer, so
nothing Compose does to its ancestors reaches it. Where an ancestor clip cannot be
reduced to a plain rectangle the platform stops cropping the layer at all and
composites the **whole buffer at its unclipped position** — in a `LazyColumn` that
put the 260 dp band over the card's own header and up across the status bar. The
overscroll stretch is a `RenderEffect`, a non-affine warp the layer cannot follow
either, so pulling at the end toggled that same layer's visibility: imagery,
page, imagery, several times a second. **No Filament call is in that loop**, which
is why logcat was silent and why the absent `FEngine` line proved nothing.

Two hypotheses were tested on the device and **both refuted**: it is not
`fadeUnderStatusBar`'s `CompositingStrategy.Offscreen` layer (the spill is
identical with it gone) and not the `Card`'s rounded clip (identical with a
`RectangleShape`). Nor is it reachable from app code:
`SurfaceView.setClipBounds` is inert without the `@hide`
`setEnableSurfaceClipping`, and `setCornerRadius` is `@hide` *and* disables the
automatic crop outright. Disabling overscroll would work and is rejected — it
fights Material 3 Expressive.

**So the surface type is the fix, not a workaround around it.** `GlobeRenderHost`
now holds everything that renders — swap chain, renderer, camera, ribbon, frame
loop, settled test, teardown ordering — and the view is a dozen lines of lifecycle
forwarding on top of it. There are two: `GlobeSurfaceView` keeps the below-window
hole punch for the full-bleed hosts (route-detail hero, immersive), where it is
free and nothing clips it; `GlobeTextureView` draws as ordinary view content for
the one *embedded* host, the Stats band, at the cost of a copy per frame. One
`embedded` flag at that call site selects the view **and** the longer session
teardown grace, because both follow from the same fact.

Verified on device: zero `SurfaceControl`s for the Stats globe, no white frames
across a sustained pull at the end of the list, and the route-detail hero still on
its three `SurfaceView` layers.

**It also closed a bug nobody had filed.** `fadeUnderStatusBar` erases content
passing under the clock with a `DstIn` blend in the window's own layer — which
could never reach a compositor layer, so the globe was the one thing in the app
exempt from it. Measured down from the top of the screen, the band now reads
233 → 179 → 138 → 115 → 108 → 88: the globe dissolves under the status bar like
every other card, which is the "system bars stay empty" invariant finally applying
to the one surface that is a photograph.

**Still owed:** the `TextureView`'s frame cost is unmeasured. It is a copy per
frame on the UI thread's hardware canvas over a 260 dp band, very likely fine and
not yet proved — `:macrobenchmark` over a Stats scroll, which H2 owes anyway.

### Tilt destroyed the LOD, and the obvious fix made it worse

Reported from the device: *"LOD also fails when using tilt and everything gets
lower res the farther I tilt until everything is just a blob of pixels."*

**The defect** was one multiplier, `Quadtree.kt:404`, scaling the screen-space
error by the incidence at the tile's centre. At the screen centre that is exactly
`cos(tilt)`, so at `MAX_TILT` it was 0.309 — two LOD levels, a four-fold blur,
sweeping in from the horizon. The Rust reference has no such term and splits on
raw `screen_px`; everything else in the arithmetic was already byte-for-byte the
same, so this was the single divergence in the whole metric. `INCIDENCE_FLOOR`
never engaged, so §9's open item about it was aimed at the wrong knob. The
paragraph arguing for the term refuted itself in its own last sentence — it kept
the *unscaled* estimate for the viewport margin "because that margin is about the
tile's extent on screen, which incidence does not shrink", and a split test is
equally an extent test.

**Deleting it alone regresses**, which is the part worth remembering. The term was
introduced as an atlas *demand reducer*, not a resolution feature. Without it a
correct traversal at `MAX_TILT` wants ~1,960 non-pinned tiles against 235
evictable slots, the breadth-first walk hits `withinAtlas`, and every remaining
branch stops at the same level: median leaf z8 → **z7**, worse than the bug.

**So the metric and the spend order changed together.** The split test is the
reference's again — raw `screenPx`, which `chord` already takes as
`max(ewArc, nsArc)` — and incidence moved from the *gate* to the *spend order*:
the traversal is now a max-heap on `screenPx × max(INCIDENCE_FLOOR, incidence)`,
so the scarce slots go to the near field and the horizon stays coarse. Incidence
is a bad predictor of a tile's screen *extent*, which is what an SSE test
measures, and a good predictor of its screen *area*, which is what a budget
should ask. Culling moved from pop time to push time — load-bearing twice: a node
carries its own priority rather than its parent's, and the budget gate stops
over-charging for nodes that will never be spent.

Leaves are now **sorted by `(z, x, y)`** before returning. Breadth-first order was
a function of the visible set by accident, which is what `GlobeScene`'s mesh
signature relies on; a priority order is a function of continuous float
priorities, so without the sort a sub-pixel camera move could rebuild geometry
that had not changed.

**Measured, in Kotlin, not from a desk estimate:**

| | before | after |
| --- | --- | --- |
| `TiltDetailTest` A — the metric, atlas out of the way | 8 of 25 cells fail | all 25 pass |
| `TiltDetailTest` B — near field under the real budget | **4 levels** of blur | **2 levels** |
| mesh worst case | — | 16,672 vertices / 73,512 indices against 40,000 / 192,000 |

**Re-tuning: nothing changed, and that is a measurement.** Sweeping
`evictableSlots` at the worst case gives z9 at 171, 235, 251, 470 *and* 4096 —
dropping the pinned floor from z2 to z1 buys 16 slots and zero levels while
costing the z2 fallback crop. `MAX_VISIBLE_TILES` stays 256 (worst leaf count 174)
but its KDoc now records that it becomes the binding constraint the moment the
atlas grows. A second 4096² atlas page is worth about one level at extreme tilt
and is named as a deliberate deferral.

**The test that was missing.** `QuadtreeTest` had eleven cases and none compared
leaf depth under tilt against nadir — and one of them was *propped up by the bug*:
the atlas-budget assertion passes more comfortably when the view coarsens, because
coarsening reduces requests. Its KDoc now says the bound is a proof rather than a
measurement, and says what used to be holding it up. `TiltDetailTest` is new and
was written **against the unchanged traversal first**, where it failed on exactly
the 8 cells and the 4-level deficit the analysis predicted. Neither existing test
was deleted, ignored or weakened; two had their *rationale* rewritten because the
reason they hold changed.

Note also that a tilt-versus-nadir comparison cannot hold *altitude* constant:
`GlobeCamera.nadirDistance()` (extracted for this) grows from 0.010 to 0.031
between tilt 0 and `MAX_TILT`, so some of the centre deficit is correct geometry.
Case A holds the camera-to-ground distance constant; case B probes the near field.

**Verified on the device by the user**: tilting no longer drops the map's
resolution. **Not verified:** frame timing on `benchmarkRelease`, and the atlas at
its new operating point — occupancy moves 228/235 → **235/235**, so the slack that
used to mask thrash is gone. It is structurally safe (the gate proves
`requested + queued ≤ evictable` at every step) but wants a parked tilted camera
and a look at `TileStats.pending` settling to zero and staying there.

**A cost worth stating rather than burying:** over an 8,064-camera sweep, 74 cells
regress by exactly one level — mid-field, at tilt ≥ 1.0, where the budget is
genuinely zero-sum. The trade is mid-field for near-field, and near-field is what
was reported.

~~**Separable follow-up:** `TileQueue.pop` takes `pollLast` within a level, which
with the new order systematically fetches the *least* important newly-appeared
tile of each level first.~~ Fixed 2026-09-14: a level is a FIFO now, so the
queue hands the workers the traversal's own order — no reversal pass needed. See
*The frame callback, measured* below for the trace that went with it.

### Five review findings against the fixes themselves

`/code-review` over the Phase G cleanup returned five, and two were real
behavioural defects of the same shape: **a predicate that uses a point but ignores
an extent.**

- **The status-glyph predicate scanned the limb for its minimum `y`.** A minimum
  over `y` says nothing about `x`, and the disc reaches the top of the window well
  before it is wide enough to span it — so an apex above the strip was read as the
  strip being covered, which is the same false positive one layer down from the
  one it replaced. It also had a silent cliff at fewer than three visible samples.
  Both dissolve into `GlobeCamera.coversStatusStrip`, which asks the question with
  the ray `screenToWorld` already casts: exact at any tilt and bearing, no
  polygon, no cliff, and conservative on purpose — every sample must hit, because
  a wrongly-dark glyph is slightly less contrasty while a wrongly-light one is
  invisible. `Limb.discReachesTop` is deleted; the NaN-gap fix it sat beside
  stays, since that one is for the rim.

  **A correction to the review, and to my own repetition of it.** It described the
  case as a tilted, rotated ellipse whose apex swings off-axis. A sweep of
  altitudes 0.6–2 × bearings 0.5–2.2 × tilts 0.4–`MAX_TILT` found no such camera;
  what makes the defect reachable is the plain narrow-disc case at altitude 1.
  Recorded because the fix is the same either way and the reason for it should be
  the true one. `StatusStripCoverageTest` measures the real case, and also pins
  something counter-intuitive found while writing it: leaning toward the horizon
  *uncovers* the strip even 0.05 above the surface, because the horizon is then
  inside the window and the top of the screen really is sky.

- **`cornerAlpha` tested the label dot against the bare rect.** `Plate` centres a
  code on its dot, so a dot just outside the camera stack still drew the inner half
  of that code over it — the exact overlap the fade exists to prevent. The span is
  now the rect widened by `PlateHalfWidth`, the horizontal twin of the
  `plateReservePx` slack `edgeAlpha` already had. **One existing case in
  `GlobeLabelsAlphaTest` asserted the defect** — "a dot beside the stack is
  untouched" — and was changed deliberately rather than quietly, with the reason
  in its KDoc.

- **The `.filamat` guard's docs overstated it**; see *The material toolchain* above
  for the two halves that closed it.
- **A silent cliff** in the same predicate, and **a duplicated bounds-reporting
  modifier** in `GlobeControls`, both folded in.

Still outstanding from the first pass, unchanged:

- ~~**The Stats card in Globe mode, on a device with a populated logbook.**~~
  Seen 2026-09-08, and the reasoning was right but stopped one step short. A
  Compose `clip` does not reach a below-window `SurfaceView` — so it does not
  round the corners, *and it does not bound the position either*. See **A
  SurfaceView cannot be clipped** below.
- ~~**H2's extension of `:macrobenchmark` to the globe**~~ — `GlobeSpinBenchmark`,
  2026-09-14, see *The frame callback, measured* below. Still owed: a check that
  nothing in the globe path has moved into `Application.onCreate`.
- **The Esri question above**, unchanged.
- ~~**Nothing makes a stale `.filamat` fail a build.**~~ Fixed 2026-09-08.
  `:feature:globe:verifyFilamatFreshness`, wired into `check`, hashes each
  `.mat`/`.filamat` pair against `src/main/materials/checksums.txt` and fails on
  any drift; `updateFilamatChecksums` rewrites the manifest after a hand recompile.
  See "The material toolchain is not in the build" above.

Found while fixing item 2 above, not fixed:

- ~~**Past roughly 8,200 km the top of the globe is `GlobeInk.space`, which *is*
  `colorScheme.surface`.**~~ Fixed 2026-09-08, and it did need `Limb`'s own
  projection rather than a height measurement. `GlobeSurface` now projects the
  limb on a *settled* camera (`collectLatest` + a 150 ms delay over
  `snapshotFlow { camera }`) and reports `Limb.discReachesTop(points, valid,
  statusStripPx)` — whether the top of the projected disc is above the status
  inset — through a new `onImageryReachesTop` callback. `RouteDetailScreen` and
  `ImmersiveGlobeScreen` AND that Boolean into what they pass to
  `SystemBarsOverMedia`, so at a long-range camera, where `imageryCovers` still
  says the box is covered, the glyphs stay the theme's own. Only the Boolean
  crosses to `:app` — no per-frame float in the host's recomposition.
  `discReachesTop` is a pure function, tested in `LimbTest`.
  The `Limb.projectInto` fix (compaction → NaN gaps) landed with this and is
  recorded under "Rendering and geometry" above.
- ~~**The Stats card's `GlobeSession` is rebuilt on an ordinary scroll.**~~ Fixed
  2026-09-08. `GlobeSession.release` now takes a grace, and `GlobeSurface` /
  `GlobeNetworkSurface` a `retainAcrossScroll` flag — set only by the Stats
  visited-network band, the one globe hosted directly in a `LazyColumn` item —
  which raises the teardown window to 20 s (`GlobeSession.graceFor`), long enough
  to cover a scroll to the end of a Stats list and back. `retainAcrossScroll` is
  deliberately a separate flag from `nestedVerticalScroll`: the route hero passes
  the latter but sits in a `Column(verticalScroll)`, which never disposes its
  off-screen children, so it keeps the prompt 2 s default. The cost is the 32 MB
  atlas held 20 s rather than 2 s when the card is closed for good — bounded, and
  paid in memory instead of a main-thread `Filament.init()` + `Engine.build()` +
  4096² allocation. JVM-tested at the `graceFor` boundary; the timer itself is a
  device check (`adb logcat -s GlobeSession Filament`).

### The frame callback, measured

The one number Phase G had never taken on hardware was its own frame loop's:
whether the quadtree traversal plus the mesh rebuild fits in a frame, or wants
the carry-over budget the uploads already have (`UPLOAD_BUDGET_NANOS`). It is
taken now. `GlobeScene.update` and `GlobeRenderHost.renderFrame` carry
`androidx.tracing` sections — `globe:upload`, `globe:update` (traversal and
rebuild together), `globe:traversal` and `globe:mesh` inside it, `globe:ribbon`,
`globe:render` — and `:macrobenchmark`'s `GlobeSpinBenchmark` opens the first
route, takes its globe full screen, and spins it through six flung drags per
iteration, five iterations, reading each section off the Perfetto trace as a
**distribution** (`TraceSectionSamplesMetric`) rather than the library's own
per-iteration sum, because the frame a user sees is the P90 and not the mean.
SM-S942B, `benchmarkRelease`, keyless GIBS imagery, 2026-09-14:

| section (ms) | baseline profile, no warm-up — P50 / P90 / P99 | `Partial()` warmed — P50 / P90 / P99 |
| --- | --- | --- |
| `globe:update` (traversal + rebuild) | **0.8 / 1.1 / 1.5** | 0.9 / 1.2 / 1.7 |
| `globe:traversal` | 0.4 / 0.6 / 0.9 | 0.4 / 0.6 / 0.8 |
| `globe:mesh` (only frames that rebuild) | 0.5 / 0.6 / 0.9 | 0.6 / 0.8 / 1.1 |
| `globe:ribbon` | 0.1 / 0.3 / 0.6 | 0.1 / 0.3 / 0.6 |
| `globe:render` (Filament submit) | 0.3 / 0.5 / 0.7 | 0.3 / 0.4 / 0.6 |
| `globe:upload` | 0.0 / 0.1 / 0.2 | 0.0 / 0.1 / 0.2 |
| `frameDurationCpuMs` | 7.6 / 9.4 / 11.5 | 7.6 / 10.1 / 13.0 |

**Decision: no traversal budget.** Traversal plus rebuild is 1.1 ms at P90 and
1.5 ms at P99 on the first launch after an install, a quarter of the ~4 ms that
would have justified carrying work over to the next frame; a budget would be
machinery for a cost that is not there. The `Partial()` column is no faster —
these sections are `FloatArray` arithmetic the JIT has little to add to — so the
first-use case is not hiding a warm-up cliff either.

**What the trace says instead, and is left open:** the whole globe callback is
under 2 ms of a `frameDurationCpuMs` that sits at 7.6 ms P50 and ~10 ms P90
against the 120 Hz panel's 8.3 ms — `frameOverrunMs` is 2.7 ms at P50. The
frame's cost is therefore in the Compose glass over the sphere during a fling —
the label plates re-laid out per projected pixel, the limb path rebuilt per
draw — not in the renderer. That is a different investigation, with the same
benchmark already in place to run it against.

Two things about the instrument itself, for whoever runs it next: the
`.perfetto-trace` files are not pulled to the host (`adb pull` refuses the
`SM-S942B - 17` output directory AGP names, spaces and all — the JSON and the
summary above still land), and the journey drives the app by content
descriptions that are the app's own TalkBack strings, so a wording change on
"Show the globe", "Open the globe full screen" or the route card sentence stops
it at a named timeout rather than measuring the wrong screen.

---

## 10. Phase H — Polish and ship

| ID | Task | Notes |
| --- | --- | --- |
| **H1** | ~~Baseline profile~~ | **Moved to P1.** It is the instrument, not the polish |
| **H2** | ~~Macrobenchmark~~ | **Done as P2**, and extended to the globe on 2026-09-14 (`GlobeSpinBenchmark`, §9 *The frame callback, measured*) |
| **H3** | Glance widget | "Today's challenge" — one route seeded by `LocalDate.toEpochDay()`, deterministic across the day. Nearly free given the seeded RNG |
| **H4** | Shortcuts | Generate route, log a flight, last route |
| **H5** | Screenshot goldens | Roborazzi across light/dark, LTR/RTL, font scale 1.0/2.0, three window sizes. The globe is stubbed — it is covered by G1's math tests plus a device smoke check |
| **H6** | R8 rules and Play listing | |

---

## 11. Sequencing

```
A ──► B ──► B+ ──► B++ ──► P ──► C ──► D ──► E ──► H
      │            │                   │
      └────────────┴──► F ──────────────┘
                        │
            C3 ────────►└──► G ──► H
```

P was inserted after B++ rather than left to H. Two of its four tasks (P1, P2)
were H1 and H2, and they were in H because polish belongs at the end — but a
baseline profile and a macrobenchmark are not polish, they are the instrument
everything after them is judged with. Building C, D and E first means adding
three more screens and then measuring all four at once, which is the position
this project has already been in twice: an unexplained number and no way to
attribute it.

B+ and B++ sit where they do by decision rather than by dependency: nothing in C
needs either. They come next because the Plan screen is the one the user actually
looks at, and neither is throwaway work. B+'s hoisted scroll state is designed for
Phase D's two lists as much as for this one, and B++'s projection is wanted by C3's
static hero map and E4's visited mini-globe, with G8 crossfading the real globe in
over C3.

B+ precedes B++ because the immersive layout changes the card's frame, and a map
designed against the current frame would be designed twice.

A blocks everything. B and C together make the app usable end-to-end, which is
the point at which it stops being a demo. F and G both replace stubs rather than
filling holes, so either can slip without blocking the rest.

## 12. Definition of done

The parity matrix in [PLAN.md](PLAN.md) §1 is the acceptance checklist, walked on
a real device — an emulator is not representative for Filament. Beyond it:

- Cold start stays under 500 ms, measured on the `benchmarkRelease` variant. Debug-build
  numbers are meaningless: the same code measured 872 ms debug against 157 ms
  non-debuggable.
- No dropped frames flinging the route list, measured by macrobenchmark.
- Airplane mode: everything except METAR and new tiles still works, and the
  pinned z0–z3 tiles keep the globe legible.
- Both extremes of the fleet — 87 NM and 8,900 NM — produce plausible routes.
- Every screen readable at font scale 2.0 and in RTL.
