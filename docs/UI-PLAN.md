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
| `:core:routing` | **Complete.** Index, codec, band index, great-circle, generator, `SearchScorer`, `FlightStatisticsCalculator`, plus `RouteArc` and `AirportSlotSearch` from Phase B — all tested |
| `:core:designsystem` | **Complete for what exists.** Theme, motion, shapes, and eleven components. See [DESIGN-SYSTEM.md](DESIGN-SYSTEM.md) |
| `:core:network` | **Empty.** No sources at all. Phase F |
| `:feature:globe` | `FilamentProbe` only. Vulkan confirmed working, `FEATURE_LEVEL_3` |
| `:app` | Shell, navigation, the self-check, and the Plan screen. Logbook, Fleet, Airports, Stats and Settings are still placeholders |

The three gaps the original plan did not cover — the index carrying no display
data (**G-a**), the missing `SearchScorer` and `FlightStatisticsCalculator`
(**G-b**), and the absent repository layer (**G-c**) — were closed by tasks A5,
A7/A8 and A6 respectively.

Two things Phase B added to `:core:routing`, both pure JVM and both unit-tested
because they are pure functions of a handful of numbers: `RouteArc`, which is
spherical interpolation plus an equirectangular projection, and
`AirportSlotSearch`, which ranks over the index's primitive arrays rather than
materialising 24,000 wrapper objects per keystroke.

Cold start was last measured at ~370 ms against a 500 ms budget, **before Phase
B**. Nothing in Phase B runs before the first frame, but that is reasoning rather
than a measurement, and the caveat in CLAUDE.md about emulator drift applies to
whoever measures it next.

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
- **`minSdk` is now 36**, so no `SDK_INT` guards anywhere.
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
Small, but it was not there before. There is also still no baseline profile, which
remains the largest single startup win available to a Compose app.

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
| **B8** | Swipe actions | Right = mark flown, writing both the logbook row and the airframe's flag, with undo; left = discard and generate a replacement into the gap |
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
`FlightShapes.GenerateFabMorph` stays in the design system; nothing uses it yet.

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

## 4a. Phase B+ — an immersive Plan screen

**This is the next work item, before the map and before Phase C.**

The screen currently sits inside its chrome: a top app bar, then controls, then a
list that stops politely above the navigation bar. That is a correct 2019 Android
screen. What it is not is *modern* — and on a 360 × 780 dp phone it also spends
about a fifth of the height on bars while the content it exists to show is
squeezed between them.

### The design

Content runs edge to edge, under the status bar and under the navigation bar, and
the chrome gets out of the way as you scroll.

```
 scrolled to top                    scrolling down
┌─────────────────────┐            ┌─────────────────────┐
│ ▓ 8:04    ▓▓▓ ░░░░  │ status     │ ▓ 8:04    ▓▓▓ ░░░░  │  card behind status bar
│ Plan            ⚙   │ title      │ ╭─────────────────╮ │  title gone
│ ╭───╮╭───╮╭───────╮ │ modes      │ │ KMTJ ──○ KPDC   │ │
│ ╰───╯╰───╯╰───────╯ │            │ ╰─────────────────╯ │
│ ╭─────────╮╭──────╮ │ filters    │ ╭─────────────────╮ │
│ ╰─────────╯╰──────╯ │            │ │ SSGE ──○ SJGY   │ │
│ ╭─────────────────╮ │            │ ╰─────────────────╯ │
│ │ KMTJ ──○ KPDC   │ │            │ ╭─────────────────╮ │
│ ╰─────────────────╯ │            │ │ 0NM7 ──○ KRCA   │ │  card behind nav bar
│ ╭──── card ───────╮ │            │ ╰─────────────────╯ │
│ ▓ Plan Log Fleet ▓  │ nav        │      (nav hidden)   │
└─────────────────────┘            └─────────────────────┘
```

**Three things move, and they move for different reasons.**

- **The title collapses first.** A large-title bar that shrinks to nothing is the
  cheapest height on the screen to reclaim, because the screen's name is only
  useful before you have started reading it. `TopAppBar` with a scroll behaviour
  does this; the settings action stays pinned.
- **The controls stay longer, then go.** The mode selector and the two filter
  chips are not decoration — they are *what is being listed*, and hiding them the
  instant a finger moves means the user cannot see which mode they are in. They
  should survive the title and leave only on a sustained downward scroll, and come
  back immediately on any upward one, which is the behaviour people already know
  from a mail app's compose bar.
- **The navigation bar hides on scroll down and returns on scroll up.** This is
  the one that has to be done carefully: it lives in `FlightPlannerApp`'s
  `NavigationSuiteScaffold`, one level above the screen, so a screen cannot hide it
  by itself. The scroll signal has to travel *up*, which means a small piece of
  shared state rather than a per-screen hack — and it must behave the same way
  when the Logbook and Fleet lists arrive in Phase D.

**Content goes under the bars, not merely to the edges.** The list's first item
scrolls up behind a translucent status bar and its last scrolls down behind the
navigation bar, so the app reads as one surface with chrome floating on it. That
means `contentPadding` carrying the inset heights rather than the *container*
being padded by them — the distinction the whole thing turns on, and the opposite
of what `PlaceholderScaffold` does today.

### Tasks

| ID | Task | Notes |
| --- | --- | --- |
| **B15** | Collapsing title | `TopAppBar` on a scroll behaviour, settings action pinned, title gone by the time the first card reaches the top |
| **B16** | Retracting controls | Mode selector and filter chips leave on sustained downward scroll and return on any upward one. Not on the first pixel — see above |
| **B17** | Hoisted scroll state | A shared "chrome visible" signal the navigation suite reads and any scrolling screen can drive. Designed for Phase D's lists, not just this one |
| **B18** | Hiding navigation suite | The suite animates out and back on that signal. Compact only — a `WideNavigationRail` on a tablet must not vanish, because there is no height to reclaim on a rail |
| **B19** | Insets as content padding | The list draws under both bars; the top and bottom insets move into `contentPadding` so nothing is clipped and nothing is double-padded |

### The parts that will bite

- **Insets are the whole difficulty, and this app has already documented why.**
  `PlaceholderScaffold`'s KDoc explains that which edge belongs to whom changes
  with width: on compact the suite owns the bottom, on medium and expanded it is a
  rail owning the *start*. Moving insets into `contentPadding` must keep that true,
  and must not double-pad when the suite is a rail.
- **A hiding navigation bar can trap the user.** If the suite is hidden and the
  list is short — one card, or an empty state — there must be no way to end up with
  no navigation and nothing to scroll. Reveal on any upward scroll, and always
  reveal when the list cannot scroll.
- **Reduce motion.** Bars that slide are exactly the "anything that moves" the
  motion rules cover; at `ANIMATOR_DURATION_SCALE == 0` they should snap, and
  `LocalReduceMotion` already carries the signal.
- **Predictive back.** The manifest opt-in landed in Phase B. Verify the
  back-to-home preview still animates correctly once the window is truly
  edge-to-edge, because that gesture and a hidden navigation bar share an edge.
- **The status bar needs contrast.** A card scrolling under a transparent status
  bar can put a dark ICAO code behind dark system icons. Either a short scrim or
  `isAppearanceLightStatusBars` driven from the theme — decide on device, in both
  themes and in Cockpit.

**Done when:** scrolling down leaves nothing but cards on screen, scrolling up
brings the chrome back immediately, no card is ever clipped by a system bar, and
the whole thing behaves the same at 360 dp and on a tablet rail.

---

## 4b. Phase B++ — the world under the route

**After 4a.** The immersive layout changes the card's frame — height, and how much
of it is ever covered by chrome — so the map is designed against the final shape
rather than redesigned twice.

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
| **B11** | World outline asset | Natural Earth 1:110m land polygons, simplified and quantised to a prebuilt binary asset, exactly as the airport index is. Built by a pure-JVM tool; never parsed from GeoJSON on device. Source and format settled below |
| **B12** | `MapFrame` in `:core:routing` | A window — centre, span, aspect — that both the coastline and the arc project through. Replaces `RouteArc`'s self-normalising output, which cannot be shared by a second layer |
| **B13** | `RouteMap` in `:core:designsystem` | Replaces `RouteSparkline`. Land fill, coast stroke, cased arc, cased endpoints, in that order |
| **B14** | Card recomposition | Map to the card's background layer, content over it, chips translucent, height raised to fit a map |

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
order — clip and project each route's coastline **once**, off the main thread,
where `RouteArc` already runs; build the `Path` once per row in `remember`, never
per frame; and if that is still not enough, snap frames to a handful of zoom
levels and cache them as `ImageBitmap`s. Measure with `dumpsys gfxinfo` while
flinging, before and after.

### Rejected

- **Labels anchored to the endpoints on the map.** It looks wonderful in a single
  mockup and collides unpredictably in a list, because the endpoints land
  somewhere different on every card. The codes stay at the card's edges, which is
  also what keeps a column of cards scannable.
- **Country borders as well as coastline.** Noise at this size. Land silhouette is
  what makes a place recognisable; borders are a later flag, not a redesign.
- **Political or terrain colouring.** It cannot survive the Cockpit theme or
  dynamic colour, and it is exactly the decorative imagery §2 rules out.

**Done when:** a European hop shows a recognisable coast, a Pacific crossing shows
a recognisably empty one, the codes and figures are no harder to read than they
are today at 360 dp and font scale 2.0, and flinging the list drops no frames.

---

## 5. Phase C — Route detail

| ID | Task | Notes |
| --- | --- | --- |
| **C1** | Detail container | `ModalBottomSheet` on compact, `ListDetailPaneScaffold` pane on expanded. Predictive back on both |
| **C2** | Route facts | Distance, estimated time, initial and final bearing, both elevations, longest runway and surface at each end |
| **C3** | Hero map area | Static equirectangular arc with DEP/DEST markers. **Deliberately a still image until Phase G** — it is the fallback the globe crossfades in over, so it is not throwaway work |
| **C4** | Actions | Mark as flown, copy plan, SkyVector, SimBrief, Google Maps, share — the last four via `ACTION_VIEW` intents |
| **C5** | Weather block | Decoded METAR layout, raw text in monospace. Placeholder until Phase F |
| **C6** | Detail motion | `sharedBounds` on the ICAO pair and aircraft name from the card; facts stagger in beneath the hero; distance counts up once; mark-as-flown plays a confirmation with haptic |

**Done when:** every element of the desktop's `route_popup.rs` has an equivalent,
and the card→detail→back journey is continuous with no visual jump.

---

## 6. Phase D — Logbook and Fleet

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

| ID | Task | Notes |
| --- | --- | --- |
| **E1** | Airports browse | Ranked type-ahead over the name index from **A5**, plus the desktop's random-50 action |
| **E2** | Airport detail | Runway diagram drawn in `Canvas` — idents, true headings, surface, length — plus "fly from here", which sets the locked departure and jumps to Plan |
| **E3** | Stats dashboard | All nine `FlightStatistics` fields. Hero total distance with count-up and an equivalence ("2.3× around the Earth"), monthly bar chart, top-aircraft list, longest/shortest cards |
| **E4** | Visited mini-globe | Dots on a small projection. Cheap 2D version now; upgraded in Phase G |
| **E5** | Settings | Theme, dynamic colour, units, ICAO-only toggle, weather provider, tile provider, dataset info, licences. Preferences DataStore, not a table |
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
| **H1** | Baseline profile | The largest single startup win available to a Compose app, and the `benchmark` variant to measure it against already exists |
| **H2** | Macrobenchmark | Cold start, plus frame timing while flinging the list and spinning the globe |
| **H3** | Glance widget | "Today's challenge" — one route seeded by `LocalDate.toEpochDay()`, deterministic across the day. Nearly free given the seeded RNG |
| **H4** | Shortcuts | Generate route, log a flight, last route |
| **H5** | Screenshot goldens | Roborazzi across light/dark, LTR/RTL, font scale 1.0/2.0, three window sizes. The globe is stubbed — it is covered by G1's math tests plus a device smoke check |
| **H6** | R8 rules and Play listing | |

---

## 11. Sequencing

```
A ──► B ──► B+ ──► B++ ──► C ──► D ──► E ──► H
      │            │            │
      └────────────┴──► F ──────┘
                        │
            C3 ────────►└──► G ──► H
```

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

- Cold start stays under 500 ms, measured on the `benchmark` variant. Debug-build
  numbers are meaningless: the same code measured 872 ms debug against 157 ms
  non-debuggable.
- No dropped frames flinging the route list, measured by macrobenchmark.
- Airplane mode: everything except METAR and new tiles still works, and the
  pinned z0–z3 tiles keep the globe legible.
- Both extremes of the fleet — 87 NM and 8,900 NM — produce plausible routes.
- Every screen readable at font scale 2.0 and in RTL.
