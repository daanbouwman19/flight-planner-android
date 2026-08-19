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
| `:core:designsystem` | **Complete for what exists.** Theme, motion, shapes, and eleven components. See [DESIGN-SYSTEM.md](DESIGN-SYSTEM.md) |
| `:core:network` | **Empty.** No sources at all. Phase F |
| `:feature:globe` | `FilamentProbe` only. Vulkan confirmed working, `FEATURE_LEVEL_3` |
| `:app` | Shell, navigation, the self-check, the Plan screen, the route detail and Settings. Logbook, Fleet, Airports and Stats are still placeholders |
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
- **Predictive back is not optional.** Every sheet and detail pane responds to the
  back gesture progressively.
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

### F10 — the navigation bar, still a decision and now a better one

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

**Not implemented.** It was briefly built and reverted: the bar is still five
destinations with Settings in the app bar, exactly as before. The trigger has not
changed — decide before Phase D, because the cost of moving these screens rises
the moment they exist.

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
| **C6** | Detail motion | ✅ `sharedBounds` on the map, the ICAO pair and the aircraft name; the spine staggers in beneath the hero; distance counts up once; mark-as-flown plays a haptic confirmation and then hands back to the list. Making the map's flight clean needed a fix inside `RouteMap` — see below |

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

**Blocked on one decision, not on code.** F10 above is still open, and it decides
whether this phase builds two sections or one: a Profile section holding the
logbook and its statistics, with settings an icon inside it, changes what D1 sits
in and folds E3 forward. The tasks below are the same work either way — only
where they live changes — but deciding after they exist costs more than deciding
now.

| ID | Task | Notes |
| --- | --- | --- |
| **D1** | Logbook list | Grouped by month with sticky headers; summary strip showing flights, NM and hours this year |
| **D2** | Add-flight sheet | Three searchable pickers plus a date picker; distance computed live as the pickers fill |
| **D3** | Swipe-to-delete | With undo, matching the Plan screen's swipe grammar exactly |
| **D4** | Fleet list | Filter chips (All · Flown · Not flown · Category); row toggle stamps `date_flown` |
| **D5** | Fleet detail | Per-airframe stats, "generate routes for this aircraft", inline editing of range, cruise and takeoff distance |
| **D6** | Fleet management | Mark all not flown behind a confirmation; add aircraft; restore defaults |
| **D7** | CSV import/export | SAF, reusing `FleetCsv` so existing desktop files work unchanged |
| **D8** | Motion | Month headers collapse on scroll; the summary strip re-counts when the log changes; the flown toggle animates state rather than snapping |

---

## 7. Phase E — Airports, Stats, Settings

**E5 is partly built.** The appearance half of Settings landed with the design
review; the rest of the screen is still Phase E work.

| ID | Task | Notes |
| --- | --- | --- |
| **E1** | Airports browse | Ranked type-ahead over the name index from **A5**, plus the desktop's random-50 action |
| **E2** | Airport detail | Runway diagram drawn in `Canvas` — idents, true headings, surface, length — plus "fly from here", which sets the locked departure and jumps to Plan |
| **E3** | Stats dashboard | All nine `FlightStatistics` fields. Hero total distance with count-up and an equivalence ("2.3× around the Earth"), monthly bar chart, top-aircraft list, longest/shortest cards |
| **E4** | Visited mini-globe | Dots on a small projection. Cheap 2D version now; upgraded in Phase G |
| **E5** | Settings | Partly done: theme (four choices) and dynamic colour ship now, in Preferences DataStore, because F6 of the design review found the brand palette and the Cockpit theme were unreachable without them. Still to do — units, ICAO-only toggle, weather provider, tile provider, dataset info, licences |
| **E6** | Motion | Chart bars grow on first composition only; stat values count up; filter changes animate the list rather than replacing it |

---

## 8. Phase F — Weather

| ID | Task | Notes |
| --- | --- | --- |
| **F1** | NOAA client in `:core:network` | Keyless default. `deriveFlightRules` locally for reports without `fltCat` |
| **F2** | Batched fetch | Visible ICAOs collected from `LazyListState`, debounced 300 ms, chunked by 50 — **one request per screenful, not fifty** |
| **F3** | Cache | `metar_cache` in the user DB with ~15 min TTL, so a dataset refresh never wipes it |
| **F4** | Flight-rules chips live | Replaces the stubs in B2 and C5 |
| **F5** | AVWX fallback | Masked key field in Settings; the desktop's provider kept as an option |
| **F6** | Motion | Chips fade from unknown to resolved as data arrives — never a layout jump, since the chip reserves its space from the start |

---

## 9. Phase G — The 3D globe

Strictly ordered. Each step is verifiable before the next begins, because
debugging a renderer and a camera at the same time is how weeks disappear.

| ID | Task | Notes |
| --- | --- | --- |
| **G1** | `Camera` and `Quadtree`, no rendering at all | Pure math ported from Rust and unit-tested first. Includes `CameraMatrixConsistencyTest`: CPU `project()` and the matrices handed to Filament must agree to sub-pixel accuracy |
| **G2** | Filament host | `SurfaceView` + `UiHelper` + `SwapChain` + `Choreographer`. Translucent, below the window, so Compose chrome draws on top |
| **G3** | Solid-colour sphere | Proves the pipeline before textures exist |
| **G4** | Tile atlas | One 4096² RGB565 atlas, 256 slots, 32 MB. z0–z3 pinned (85 tiles) and never evicted, so the globe is never blank offline. **The desktop's 512-texture LRU is 128 MB and would OOM immediately** |
| **G5** | Tile pipeline | LIFO queue, 4 coroutines, OkHttp disk cache, `inBitmap` pooling |
| **G6** | Arc and markers | Triangle-strip ribbon, not `GL_LINE_STRIP` — line widths above 1 are unreliable on mobile GPUs. Labels rendered in Compose from CPU-projected positions |
| **G7** | Gestures | Pan, fling, pinch, rotate, tilt — with the ~60 ms / 12 dp **mode-locking classification window**, without which the camera jitters constantly |
| **G8** | Crossfade in | The globe fades in over the static preview from **C3** once its first frame is ready |
| **G9** | Accessibility | A `SurfaceView` is invisible to TalkBack: content description, custom actions, and visible ± and compass controls |

**Open risk:** Esri tile licensing for a consumer app is unresolved. `TileProvider`
stays swappable and NASA GIBS (public domain, keyless, caps around z8–z9) is the
default until that is settled.

---

## 10. Phase H — Polish and ship

| ID | Task | Notes |
| --- | --- | --- |
| **H1** | ~~Baseline profile~~ | **Moved to P1.** It is the instrument, not the polish |
| **H2** | ~~Macrobenchmark~~ | **Done as P2.** `:macrobenchmark` exists and reports. What stays in H is extending it to the globe, once there is a globe |
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
