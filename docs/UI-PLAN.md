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
| `:core:routing` | **Mostly complete.** Index, codec, band index, great-circle, generator — all tested. Missing: `SearchScorer`, `FlightStatisticsCalculator` |
| `:core:designsystem` | **Empty.** No sources at all |
| `:core:network` | **Empty.** No sources at all |
| `:feature:globe` | `FilamentProbe` only. Vulkan confirmed working, `FEATURE_LEVEL_3` |
| `:app` | `MainActivity`, Hilt application, and the temporary self-check screen |

Cold start is ~311 ms and route generation is ~1 ms per batch of 50. Both have
headroom; the budget for everything below is that cold start stays under 500 ms.

### Three gaps the original plan does not cover

**G-a. The index has no display data.** To hit the startup budget, `AirportIndex`
was reduced to primitives: id, packed code, coordinates, trig, runway length,
flags. Names, municipalities, countries and elevations are *not* in memory. But
the Airports screen needs them, and PLAN.md §3.4 assumes a ranked in-memory
search over names. Resolved by task **A5**.

**G-b. `SearchScorer` and `FlightStatisticsCalculator` were never written.**
PLAN.md §8 specifies tests for both. They are prerequisites for the Airports,
Fleet and Stats screens. Tasks **A7**, **A8**.

**G-c. There is no repository layer.** DAOs return Room entities; screens need
domain types. Nothing currently maps between them except `FleetSeeder.toSpec()`.
Task **A6**.

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

**Not done:** the adversarial review pass never ran (the orchestrating workflow
died on a usage limit), so this code is verified at the build/test/startup level
but has not been read adversarially. Worth a `/code-review` over `5a6274d`. Two
known gaps: `AirportIndexProvider` caches a failed load permanently with no retry
path, and the repositories' `Flow` re-emission semantics are unaudited.

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

## 4. Phase B — Plan screen

The heart of the app. The desktop's entire 250 px sidebar collapses into two
chips and one segmented control.

| ID | Task | Notes |
| --- | --- | --- |
| **B1** | `PlanViewModel` | `flatMapLatest` over a request flow so a changed selection cancels the in-flight batch — the clean replacement for the desktop's `AtomicU64` generation counter |
| **B2** | Route card | Aircraft + category badge, `EHAM → KJFK` in tabular figures, distance, estimated time, runway badges, dep/dest flight-rules chips (stubbed until Phase F) |
| **B3** | Great-circle sparkline | Equirectangular mini-arc drawn in a Compose `Canvas`. No GPU, no globe — this is what makes a card feel like a *route* |
| **B4** | Departure and aircraft chips | Each opens a full-screen `SearchBar` with ranked type-ahead from **A7** |
| **B5** | Mode selector | `ButtonGroup`: Any · Not flown · This aircraft. Not-flown badge from `observeNotFlownCount()` |
| **B6** | Infinite scroll | Appends 50 on approach to the end; pull-to-refresh regenerates |
| **B7** | Generate FAB | `MaterialShapes` morph on press, long-press for "generate 50 more", haptic on completion |
| **B8** | Swipe actions | Right = mark flown, with undo snackbar; left = discard and regenerate that one card |
| **B9** | Empty, loading, error states | Skeleton cards while generating. Empty state reuses the desktop's copy: "Start your journey by generating a flight plan." |
| **B10** | Plan screen motion | Staggered list entrance; card content crossfades rather than jumping when a batch replaces another; FAB shape morph; swipe threshold haptic; discarded card collapses and its replacement expands into the gap |

**Done when:** generate in all three modes, with and without a locked departure,
scroll to page, swipe to mark flown, and see it land in the logbook.

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
A ──► B ──► C ──► D ──► E ──► H
      │     │            │
      └─────┴──► F ──────┘
                  │
      C3 ────────►└──► G ──► H
```

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
