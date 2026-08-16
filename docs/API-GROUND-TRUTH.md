# API ground truth for the pinned stack

Everything here was established by **compiling a throwaway probe** against the
versions in `gradle/libs.versions.toml`, or by reading the resolved artifact,
not by reading documentation. Documentation for Compose Material 3 in this era
describes APIs across several release trains at once, and a large part of the
"Material 3 Expressive" surface it describes is **not public in the version this
project resolves**. That discrepancy invalidated part of `UI-PLAN.md` §2 and is
recorded here so it is not rediscovered.

Resolved by Compose BOM `2026.08.00`:

| Artifact | Version |
| --- | --- |
| `androidx.compose.ui:ui` | 1.12.0 |
| `androidx.compose.material3:material3` | **1.4.0** (newest *stable*; 1.5.0 is alpha26) |
| `androidx.compose.material3:material3-adaptive-*` | 1.4.0 |

## What does NOT exist in material3 1.4.0

Confirmed twice: the compiler rejects each of these, and none appears in the
class listing of `material3-android-1.4.0.aar`.

| Symbol | Status |
| --- | --- |
| `MaterialShapes` (`Circle`, `Cookie9Sided`, `Pill`, …) | **absent** |
| `LoadingIndicator`, `ContainedLoadingIndicator` | **absent** |
| `ButtonGroup` | **absent** |
| `HorizontalFloatingToolbar` / `VerticalFloatingToolbar` | **absent** |
| `FloatingActionButtonMenu` | **absent** |
| `SplitButtonLayout` | **absent** |
| `androidx.compose.material3.carousel.*` | **absent** |
| `MotionScheme`, `MaterialTheme.motionScheme`, `MotionScheme.expressive()` | present but **`internal`** |
| `MaterialExpressiveTheme` | present but **`internal`** |
| `Typography.*Emphasized` (`bodyLargeEmphasized`, …) | present but **`internal`** |
| `Shapes.largeIncreased` / `.extraLargeIncreased` / `.extraExtraLarge` | present but **`internal`** |
| `ShapeDefaults.CornerExtraSmall` | **`internal`** (`ShapeDefaults.Medium` etc. are public) |
| `ExperimentalMaterial3ExpressiveApi` | **`internal`** — cannot even be opted into |

`internal` here is not a formality that an opt-in annotation unlocks. Kotlin
`internal` is module-scoped, so from this project these members are as
unreachable as private ones.

## What DOES exist and is public + stable

No opt-in required for any of these — verified by compiling without any `@OptIn`:

- `NavigationSuiteScaffold(navigationItems = { … }, navigationSuiteType = …)`
  with `NavigationSuiteItem(selected, onClick, icon, label)`. Note the parameter
  is `navigationItems`, not the older `navigationSuiteItems`.
- `NavigationSuiteType.ShortNavigationBarCompact` / `ShortNavigationBarMedium` /
  `WideNavigationRailCollapsed` / `WideNavigationRailExpanded` / `None`, and
  `NavigationSuiteScaffoldDefaults.navigationSuiteType(currentWindowAdaptiveInfo())`.
- `ShortNavigationBar`, `WideNavigationRail` — the expressive navigation
  components did land publicly, even though the rest of Expressive did not.
- `AppBarRow(overflowIndicator = …)` and `AppBarColumn` — overflow-aware app bar
  content.
- `VerticalDragHandle` — for `ListDetailPaneScaffold` pane resizing.
- `SingleChoiceSegmentedButtonRow` / `SegmentedButton`.
- `CircularProgressIndicator`, `LinearProgressIndicator`.
- `PullToRefreshBox`.

Experimental (compiles with `@OptIn(ExperimentalMaterial3Api::class)`):

- `rememberSearchBarState`, `TopSearchBar`, `ExpandedFullScreenSearchBar`.

Also verified compiling: `SharedTransitionLayout` with `Modifier.sharedBounds` /
`Modifier.sharedElement` and `rememberSharedContentState`; `LazyItemScope
.animateItem()`; `preferencesDataStore` delegate; a Hilt `@Qualifier`-annotated
application `CoroutineScope` module.

## Consequence: this project owns its expressive layer

`UI-PLAN.md` §2 named `ButtonGroup`, `FloatingToolbar` and the expressive
`LoadingIndicator` directly. None can be used. Bumping material3 to `1.5.0-alpha26`
would supply them, but pinning an app that is meant to ship to an alpha of the
single largest dependency trades a bounded amount of our own code for an unbounded
amount of someone else's churn. The decision is to **stay on 1.4.0 stable and own
the thin expressive layer in `:core:designsystem`**, which is what `UI-PLAN.md`
already recommended for exactly this risk ("keep the Expressive surface behind one
wrapper file that absorbs the churn").

That layer is small because the underlying primitives are all public elsewhere:

- **Motion** — `FlightMotion` supplies the five tokens as `spring()` specs. The
  Expressive spring constants are not guesses; they were read out of material3
  1.4.0's own internal `ExpressiveMotionTokens` table:

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
  alpha that overshoots reads as a flicker, not as motion.

- **Shape morphing** — `androidx.graphics:graphics-shapes:1.1.0` is **stable** and
  public, and it is the library `MaterialShapes` is a catalogue on top of. We get
  `RoundedPolygon` and `Morph` directly and define the handful of shapes actually
  used, rather than the ~40 in the catalogue.

- **Loading indicator** — a morphing indeterminate indicator built on the above.

- **Mode selector** — `SingleChoiceSegmentedButtonRow` is public and stable, and
  is a better fit for a three-way exclusive choice than `ButtonGroup` anyway.

## Re-verifying

The probe is deliberately not checked in; it exists to be thrown away. To redo
this after a dependency bump, write a file that references each symbol in
question, run `./gradlew :app:compileDebugKotlin`, read the errors, and delete it.
An unresolved reference means absent; "cannot access … it is internal" means
present but unusable. Both are compile errors, and only the message distinguishes
them — which is why guessing from documentation does not work here.
