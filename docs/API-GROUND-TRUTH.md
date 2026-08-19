# API ground truth for the pinned stack

Everything here was established by **compiling a throwaway probe** against the
versions in `gradle/libs.versions.toml`, or by reading the resolved artifact — not
by reading documentation. Documentation for Compose Material 3 in this era
describes several release trains at once, so it cannot tell you whether a symbol
exists *in the version you resolve*. Only the compiler can.

**Platform floor: `minSdk 35` (Android 15).** Everything below assumes it. Because
the floor is the platform the design language targets, there are **no
`Build.VERSION.SDK_INT` guards anywhere in this app** — dynamic colour, predictive
back, edge-to-edge enforcement and per-app language are all unconditionally
available. A guard for an API level below `minSdk` is a branch no supported device
can take, so it is untestable rather than merely redundant; lint reports it as
`ObsoleteSdkInt`. If you are about to write one, the answer is that you do not need
it.

| Artifact | Version |
| --- | --- |
| `androidx.compose.ui:ui` | 1.12.0 (from BOM `2026.08.00`) |
| `androidx.compose.material3:material3` | **1.5.0-alpha26**, pinned above the BOM |
| `androidx.compose.material3:material3-adaptive-*` | 1.4.0 |
| `androidx.graphics:graphics-shapes` | 1.1.0 |

## Why material3 is pinned above the BOM

**Material 3 Expressive is the design language for this app**, so the decision is
which version supplies it, not whether to use it.

The BOM resolves material3 **1.4.0**, and Expressive is essentially unavailable
there. Probed and confirmed twice — the compiler rejects each, and none appears in
the class listing of `material3-android-1.4.0.aar`:

| Symbol | in 1.4.0 |
| --- | --- |
| `MaterialShapes`, `LoadingIndicator`, `ButtonGroup` | **absent** |
| `FloatingToolbar`, `SplitButtonLayout`, `FloatingActionButtonMenu` | **absent** |
| `LinearWavyProgressIndicator` / `CircularWavyProgressIndicator` | **absent** |
| `androidx.compose.material3.carousel.*` | **absent** |
| `MotionScheme`, `MaterialTheme.motionScheme`, `MotionScheme.expressive()` | **`internal`** |
| `MaterialExpressiveTheme` | **`internal`** |
| `Typography.*Emphasized` | **`internal`** |
| `Shapes.largeIncreased` / `.extraLargeIncreased` / `.extraExtraLarge` | **`internal`** |
| `ExperimentalMaterial3ExpressiveApi` | **`internal`** — no opt-in is even possible |

`internal` is not a formality an annotation unlocks: Kotlin `internal` is
module-scoped, so from this project those members are as unreachable as private
ones. Reimplementing all of it by hand was considered and rejected — it would mean
hand-writing a loading indicator, a button group, a floating toolbar, a FAB menu,
a split button, a shape catalogue and a motion scheme, and the result would be an
imitation of the design language rather than the design language.

So material3 is pinned to **1.5.0-alpha26**, where the whole surface is public
behind `@OptIn(ExperimentalMaterial3ExpressiveApi::class)`. The cost is smaller
than the version string suggests: alpha26 requires compose `1.12.0-beta01` and the
BOM already supplies `1.12.0` stable, and it requires `graphics-shapes 1.0.1`
against our 1.1.0 — both newer, so **nothing else in the graph moves**. The
adaptive artifacts stay on 1.4.0 and continue to work.

The risk is real and is accepted: an alpha can change API between releases. It is
contained by keeping the Expressive surface behind `:core:designsystem` — screens
name a motion token or a design-system component, never a material3 Expressive
symbol directly — so a breaking alpha bump is a change in one module.

## Verified working on 1.5.0-alpha26

Compiled with zero errors and zero warnings under
`@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)`:

- `MaterialExpressiveTheme(colorScheme, motionScheme, shapes, typography, content)`
- `MotionScheme.expressive()` / `.standard()`, `MaterialTheme.motionScheme`, and all
  six specs: `defaultSpatialSpec`, `fastSpatialSpec`, `slowSpatialSpec`,
  `defaultEffectsSpec`, `fastEffectsSpec`, `slowEffectsSpec`
- Expressive shape tiers `Shapes.largeIncreased`, `.extraLargeIncreased`, `.extraExtraLarge`
- `MaterialShapes.Circle` / `Square` / `Pill` / `Cookie9Sided` / `Clover4Leaf` /
  `VerySunny` / `Diamond` / `Arrow` (and the rest of the catalogue)
- Emphasized type: `displayLargeEmphasized`, `headlineMediumEmphasized`,
  `titleMediumEmphasized`, `bodyLargeEmphasized`, `labelLargeEmphasized`
- `LoadingIndicator`, `ContainedLoadingIndicator`
- `LinearWavyProgressIndicator`, `CircularWavyProgressIndicator`
- `ButtonGroup(overflowIndicator = …)` — **compiles, but see the defect below**
- `HorizontalFloatingToolbar(expanded = …)`, `VerticalFloatingToolbar`
- `FloatingActionButtonMenu(expanded, button)`
- `SplitButtonLayout(leadingButton, trailingButton)`
- `carousel.HorizontalMultiBrowseCarousel` + `carousel.rememberCarouselState`
- `ShortNavigationBar`, `WideNavigationRail`, `AppBarRow`, `AppBarColumn`,
  `VerticalDragHandle`, `SingleChoiceSegmentedButtonRow`, `PullToRefreshBox`
- `NavigationSuiteScaffold(navigationItems = { … }, navigationSuiteType = …)` with
  `NavigationSuiteItem(selected, onClick, icon, label)`. The parameter is
  `navigationItems`, **not** the older `navigationSuiteItems` that many examples show.
- `NavigationSuiteType.ShortNavigationBarCompact` / `ShortNavigationBarMedium` /
  `WideNavigationRailCollapsed` / `WideNavigationRailExpanded` / `None`, and
  `NavigationSuiteScaffoldDefaults.navigationSuiteType(currentWindowAdaptiveInfo())`
- `rememberSearchBarState`, `TopSearchBar`, `ExpandedFullScreenSearchBar`
  (these need only `ExperimentalMaterial3Api`)
- `SharedTransitionLayout` with `Modifier.sharedBounds` / `Modifier.sharedElement`
  and `rememberSharedContentState`; `LazyItemScope.animateItem()`
- `preferencesDataStore` delegate; a Hilt `@Qualifier` application `CoroutineScope`

## Known defects in 1.5.0-alpha26

Compiling is not the same as working. These were found on a device, not by the
compiler, and they are the concrete cost of the alpha.

### `ButtonGroup` crashes when its items fill the width

```
java.lang.IllegalArgumentException: maxWidth must be >= than minWidth,
maxHeight must be >= than minHeight, minWidth and minHeight must be >= 0
  at androidx.compose.ui.unit.Constraints.copy-Zbe2FdA(Constraints.kt:670)
  at androidx.compose.material3.ButtonGroupMeasurePolicy.measure-3p2s80s(ButtonGroup.kt:712)
```

Reproduced on a Galaxy S26, Android 16, **at the default font scale** with three
`toggleableItem`s ("Any" / "Not flown 12" / "This aircraft") and an
`overflowIndicator`, under `Modifier.fillMaxWidth()`. The measure policy computes
a negative width and constructs an invalid `Constraints`. It is deterministic:
the screen never draws, the app dies on launch.

This is the failure mode the containment rule was written for. `ModeSelector` in
`:core:designsystem` now wraps the stable `SingleChoiceSegmentedButtonRow`, and
that substitution is one file. Nothing in `:app` changed, because nothing in
`:app` ever named `ButtonGroup`.

**Before switching back**, run the Plan screen on a device at font scale 2.0 with
the longest mode label — the crash is a measurement bug, so it will not show up
in a preview or a unit test.

### Shape conversion — the two signatures worth writing down

Both are **extension functions** in `androidx.compose.material3`, so they need an
import and receiver-call syntax; a qualified call like
`androidx.compose.material3.toShape(polygon)` does not compile.

```kotlin
import androidx.compose.material3.toShape
import androidx.compose.material3.toPath

@Composable fun RoundedPolygon.toShape(startAngle: Int = 270): Shape
fun Morph.toPath(progress: Float, path: Path = Path(), startAngle: Int = 270): Path
```

`Morph.toPath(progress)` is the animation bridge — it is **not** `@Composable`, so
it can be called from a draw scope each frame. This is why the design system does
not need to hand-write a morph→`Shape` adapter.

## The motion tokens are exact, not approximate

Read out of material3's own internal `ExpressiveMotionTokens` table rather than
recalled, so `MotionScheme.expressive()` and any token we mirror agree exactly:

| Token | damping ratio | stiffness |
| --- | --- | --- |
| default spatial | 0.8 | 380 |
| fast spatial | 0.6 | 800 |
| slow spatial | 0.8 | 200 |
| default effects | 1.0 | 1600 |
| fast effects | 1.0 | 3800 |
| slow effects | 1.0 | 800 |

Spatial springs are underdamped on purpose — that overshoot *is* the expressive
character. Effects springs are critically damped (ratio 1.0) because a colour or
alpha that overshoots reads as a flicker rather than as motion.

## Re-verifying after a dependency bump

The probe is deliberately not checked in; it exists to be thrown away. Write a file
referencing each symbol in question, run `./gradlew :app:compileDebugKotlin`, read
the errors, delete it. An **unresolved reference** means absent; **"cannot access …
it is internal"** means present but unusable. Both are compile errors and only the
message distinguishes them, which is exactly why guessing from documentation fails
here.
