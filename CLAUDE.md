# Working on flight-planner-android

A native Android flight planner: generates flyable routes between real airports
constrained by aircraft range and runway length, logs flights, shows statistics,
and draws the route on a 3D globe. 100% Kotlin, fully offline, no server.

It is a ground-up fork of a Rust desktop app that lives at
`J:\projects\flight-planner`. **That repo is the behavioural reference.** When
porting anything — scoring, statistics, route generation — read the Rust original
first and port it field-for-field rather than reinventing it. Where the port
deliberately diverges, say so in a KDoc.

## Read these first

| Document | What it settles |
| --- | --- |
| [docs/PLAN.md](docs/PLAN.md) | Architecture, module layout, the data pipeline, the parity matrix |
| [docs/UI-PLAN.md](docs/UI-PLAN.md) | The task breakdown (stable IDs — "do B4"), design and motion direction, phase status |
| [docs/DESIGN-SYSTEM.md](docs/DESIGN-SYSTEM.md) | The `:core:designsystem` API every screen builds on |
| [docs/API-GROUND-TRUTH.md](docs/API-GROUND-TRUTH.md) | What actually compiles in the pinned dependency versions |

## Invariants

These are not preferences. Breaking one is a defect even when it compiles.

### Material 3 Expressive is reached only through `:core:designsystem`

`material3` is pinned to **`1.5.0-alpha26`**, above the Compose BOM, because the
BOM's 1.4.0 has no usable Expressive surface at all. Pinning an alpha of the
largest dependency is a real risk, and the *only* thing containing it is that
screens never import an Expressive symbol directly.

So in `:app` and any `:feature:*` module, do not import `MaterialShapes`,
`MotionScheme`, `LoadingIndicator`, `ButtonGroup`, `FloatingToolbar`,
`SplitButtonLayout`, `FloatingActionButtonMenu` or the wavy progress indicators.
If you need one, surface it from `:core:designsystem` first. A breaking alpha bump
must stay a one-module change.

### Never call `spring()` or `tween()` outside `:core:designsystem`

Name a motion token — `FlightMotion.spatial()`, `.effects()`, and so on. A screen
knows it is moving a card; it does not know it wants `dampingRatio = 0.8f`. This
is what keeps motion coherent and retunable in one place.

### No `Build.VERSION.SDK_INT` guards

`minSdk` is **36** (Android 16). Dynamic colour, predictive back, edge-to-edge
enforcement, per-app language and all of `java.time` are unconditionally present.
A guard for an API level below `minSdk` is a branch no supported device can take,
so it cannot be tested and rots silently. Lint does **not** reliably flag these —
`ObsoleteSdkInt` missed one — so this is on you, not on tooling.

### Cold start stays under 500 ms

**Measure it with `:macrobenchmark`, not by hand.**

```bash
./gradlew :macrobenchmark:connectedBenchmarkAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.github.daanbouwman.flightplanner.macrobenchmark.StartupBenchmark
```

Twelve cold launches, ~35 seconds, on the `benchmark` variant. Latest figure:
**169 ms median `timeToInitialDisplayMs`** on the SM-S942B. The older ~370 ms
came off an emulator and is different hardware, not a regression that was fixed.

Anything added to `Application.onCreate`, a Hilt `@Singleton` constructor, or an
`androidx.startup` Initializer runs before first frame and spends that budget. In
particular:

- The airport index loads from a **prebuilt binary asset** via `AirportIndexLoader`.
  Rebuilding it from SQLite rows was measured at 646 ms and was deleted. It must
  not come back.
- `timeToInitialDisplay` stops at the first frame, and this app holds a splash
  screen *after* that until the index and the stored theme settle. A regression in
  the index load therefore shows up as a longer splash a user can see and **not**
  in this number. Covering it would need `reportFullyDrawn`, which the app does
  not call.
- **A debug APK is `debuggable` and runs largely interpreted, so timings taken
  from it are meaningless** — 872 ms debug versus 157 ms non-debuggable for
  identical code. `:macrobenchmark` has no debug variant for this reason, and
  `androidx.benchmark.suppressErrors` must stay unset so the library keeps
  refusing a debuggable target.

Every caveat below applies to hand-measurement, which is now the fallback rather
than the method:

- Measure a median of ~12 launches and discard scheduler outliers. Do not measure
  immediately after install: the first runs include dex verification and JIT
  warm-up and read ~130 ms high.
- **An emulator number is only comparable to another taken minutes earlier on the
  same idle host.** Measured here: one unchanged APK gave a 424 ms median and then
  ~712 ms later in the same session, after a series of Gradle builds had loaded the
  host — a drift far larger than most regressions worth hunting. A before/after
  comparison spanning a work session therefore proves nothing.
- Order matters even inside one `:macrobenchmark` run: the startup benchmark that
  ran last, after five minutes of flinging, reported a 30 ms higher median than
  the one that ran first on the same code. Re-run the one you care about on its
  own rather than reading a suite in sequence.

### The system bars stay empty

The window is edge to edge and the status and navigation bars are **transparent —
nothing is painted behind them**. Content scrolls up under the clock and down under
the gesture handle. A scrim, however subtle, reads on a device as an opaque bar the
moment a card is behind it; so does an inset padding left on a container that
outlives its children, because its background then paints a bar-height strip. Both
were built and removed in Phase B+. Legibility where the app's glyphs meet the
system's is handled by `FlightPlannerTheme` setting `isAppearanceLightStatusBars`
and `isAppearanceLightNavigationBars` from the scheme it resolved — never from the
system night setting, which is a different question once Cockpit exists.

### Flight-rules colours are semantic and never dynamic

VFR green, MVFR blue, IFR red, LIFR magenta are standard aviation chart colours; a
pilot reads the colour before the letters. They live outside the Material scheme
in `LocalFlightRulesColors` and are never derived from wallpaper. `FlightRulesContrastTest`
enforces both legibility (4.5:1) and mutual distinguishability — the latter caught
IFR and LIFR sitting 0.05 apart in normalised RGB. If you retune a colour, that
test decides whether you may.

### Do not suppress warnings

No `@Suppress` and no `let _ = x` equivalents; fix the root cause. `@OptIn` for a
genuinely experimental API is not suppression and is expected throughout — the
Expressive surface requires `ExperimentalMaterial3ExpressiveApi`.

Do not delete, `@Ignore`, or weaken a test to make a build pass. If a test encodes
a wrong expectation, say so explicitly rather than quietly changing it.

## Verifying a change

```bash
./gradlew build                      # compiles everything, runs all tests + lint
./gradlew :core:routing:test         # fast: the pure-JVM algorithms
./gradlew :core:designsystem:testDebugUnitTest
```

`adb` is not on `PATH`:
`C:\Users\daanb\AppData\Local\Android\Sdk\platform-tools\adb.exe`

**A UI change is not verified until a screenshot of it has been looked at.** A green
build, a passing test and a correct preview together said nothing about the Phase B+
Plan screen shipping a solid bar across the status bar: a preview cannot show window
insets, chrome that retracts, or anything else driven by scrolling. Install it and
look — at rest *and* in the state the change is actually about.

```bash
./gradlew :app:installDebug
adb shell am force-stop com.github.daanbouwman.flightplanner
adb shell am start -n com.github.daanbouwman.flightplanner/.MainActivity
adb exec-out screencap -p > shot.png
# `input swipe` is often read as a tap and opens a card. Drive a scroll explicitly:
adb shell "input motionevent DOWN 540 1800; input motionevent MOVE 540 1300; \
           input motionevent MOVE 540 800; input motionevent UP 540 800"
```

Determining whether an API exists in the pinned versions is done by **compiling a
throwaway probe**, never by reading documentation — the docs describe several
release trains at once. Write a file referencing the symbols, run
`./gradlew :app:compileDebugKotlin`, read the errors, delete it. "Unresolved
reference" means absent; "cannot access … it is internal" means present but
unusable. Both are compile errors and only the message distinguishes them.

## Module boundaries

| Module | Rule |
| --- | --- |
| `:core:model` | Pure JVM. No Android imports, ever |
| `:core:routing` | Pure JVM. No Android, no Compose. The algorithms live here so they stay unit-testable in milliseconds |
| `:core:database` | Room + repositories. Knows nothing about UI |
| `:core:designsystem` | Knows `:core:model` (for `FlightRules`) and `:core:routing` (for the geometry `RouteMap` projects). Must never know the database or the network |
| `:app` | Screens. Reaches Expressive and motion only through `:core:designsystem` |
