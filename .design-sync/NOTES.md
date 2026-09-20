# design-sync notes — flight-planner-android

## Where the mirror lives now

The mirror is published as a **Design System artifact**:
<https://claude.ai/artifact/Dmjit3682NXRcbiuwninnt>. Until 2026-09-16 it was a
`claude.ai/design` project (`projectId` in `.design-sync/config.json`, kept only
because `.ds-sync`'s config validator requires the key); that project was migrated
to the artifact type mechanically, nothing redesigned. The `DesignSync` tool the
old upload used cannot write to an artifact, so the upload is now the Artifact
tool, fed by a converter in this directory.

### Re-sync recipe

```bash
./gradlew :core:designsystem:testDebugUnitTest --tests "*DesignTokenExportTest*"   # tokens.json from Kotlin
(cd design-mirror && npm run build)                                                  # dist/, tokens.gen.ts
node .ds-sync/resync.mjs --config .design-sync/config.json \
  --node-modules design-mirror/node_modules --entry design-mirror/dist/index.js \
  --out ./ds-bundle                                                                  # ds-bundle/, full render check, grades
# Artifact read  url=<the artifact>  path=project/design-system.json   → the live index, saved locally
# Artifact read  url=<the artifact>  (no path)  and  Artifact list scope=files url=<the artifact>
#   — both required in the same session before a publish: the tool refuses to replace a path it has
#   not seen listed, and refuses outright until the artifact itself has been read (2026-09-19)
node .design-sync/artifact/build.mjs --live-paths                                   # every path the last publish wrote
# Artifact read  url=<the artifact>  paths=<that JSON array>  — no out_dir: a multi-path read only saves
#   into the scratchpad's artifact-files/<id>/ folder (≤256 paths per call; split when over, same folder)
node .design-sync/artifact/build.mjs --index <the index> --live <that folder> --note "<what changed>"
#   → .design-sync/.cache/artifact/, and refuses to build over anything edited on the page (below)
# Artifact publish, twice, straight from .design-sync/.cache/artifact/publish.json:
#   call 1 = every content file; call 2 = the index (+ deletions). The index goes last, always.
git add .design-sync/artifact/published.json                                        # commit with the re-sync
```

**No `--remote` flag.** `resync.mjs` still accepts one (it's `.ds-sync`'s own
incremental-verification anchor, meant to skip re-checking a component whose
source hasn't changed since the last upload), but the Design System Artifact
type this now publishes to has no `_ds_sync.json`-shaped sidecar anywhere in its
`project/` tree to anchor against — nothing has been able to refresh
`.design-sync/.cache/remote-sync.json` since the 2026-09-16 migration off
`claude.ai/design`, so it can only ever be a frozen pre-migration snapshot.
Passing it doesn't break anything (a shape/scheme mismatch is handled), but it
also can't skip anything real, so every re-sync above is a full one: full
render check, full re-grade, full convert, across every component. That's the
actual current cost of a re-sync, not an optimization opportunity being missed
by forgetting a flag.

`--entry` is needed because the mirror is its own source repo: `.ds-sync` otherwise
looks for the package under `node_modules/@flightplanner/design-mirror`. `.ds-sync/`
and `ds-bundle/` are the former `/design-sync` skill's toolchain and output, gitignored;
the converter reads `ds-bundle/` and everything the converter *owns* is tracked here:

| `.design-sync/artifact/` | What it is |
| --- | --- |
| `build.mjs` | the converter: `ds-bundle/` → `project/**` in the artifact's own layout, plus `publish.json` |
| `README.md` | the brand book, the artifact's `project/README.md` verbatim (also `config.readmeHeader`) |
| `token-usage.json` | the `usage` note on every colour, radius and motion token, and `sample`/`usage` per type style. **The build fails on a token without a note, and on a note without a token** |
| `cover.html` | `components/Cover/preview.html`, written to the artifact type's `cover.md` brief. **Hand-authored, and the only place the cover survives a re-sync** — a cover designed on the page has to be copied here verbatim |
| `published.json` | sha256 of every file the last build staged, keyed by published path. The page-side guard reads it; commit it with every re-sync |

### Which way the pipeline runs, and the page-side guard

Repo → artifact, one way, every file — a publish replaces each path it names
unconditionally, and the converter names all of them. Nothing flows back on its
own. So an edit made on the page lasts exactly until the next re-sync unless it
is brought into the repo first: verbatim for the two hand-authored files
(`cover.html`, `README.md`), re-expressed in `.design-sync/previews/*.tsx` or
`design-mirror/src` for anything generated. This bit on 2026-09-19: the cover was
redesigned on the page (the staircase), the AircraftWidgetCard re-sync rebuilt
from the repo's `cover.html` (still the arcs) and put the old one back. The
earlier re-sync had "carried it through" only because the staircase did not
exist yet.

Since then `build.mjs` refuses to overwrite a page-side edit. For every path it
would publish it compares the live copy (`--live`, the folder an `Artifact read
paths=` saved to) against two hashes: what this build wrote, and what the last
build staged (`published.json`). Equal to either is fine — a no-op, or the repo
moving while the page stood still. Equal to neither means the page changed it,
and the build stops, naming each path, its saved live copy and the repo source it
belongs in. `--discard-live <path>` publishes over one on purpose; a discard for a
path not in conflict is itself an error, so a stale flag cannot linger in a
script. A path the manifest knows but the read left out is also an error — the
guard cannot pass on a file it has not seen. Verified by planting an edit in a
live copy and watching the build fail, then pass with the discard, then fail on
a stale discard (2026-09-19).

Two edges, both conservative: the manifest is written at build time, so a build
whose publish never happened records hashes that were never live — the next run
then reports a conflict that a look at the live copy resolves; and a page-authored
file at a path the repo has *never* published is not seen at all (no manifest
entry, and the read does not ask for it) — the `Artifact list scope=files` the
recipe already requires is where such a file would show up.

What the converter changes on the way through, so a diff against `ds-bundle/`
does not read as corruption:

- `components/<group>/<Name>/<Name>.html` → `components/<Name>/preview.html`; the
  group moves into the `@dsCard` marker, `viewport="WxH"` becomes `width=W`, a card
  with no `_preview/<Name>.js` is marked `floor`. The stylesheet links and the
  react / bundle / preview `<script src>` tags go — the artifact's frame preloads all
  of them — and the preview module is inlined in their place. `body{background:#fff}`
  becomes `var(--fp-background)` so a non-default page theme is not framed in white.
- `<Name>.prompt.md` → `<Name>/README.md`, with each story's JSDoc placed **above its
  own fence** as prose. `.ds-sync`'s slicer attaches every story's doc to the previous
  story's code and drops the first one entirely; the converter re-reads
  `.design-sync/previews/<Name>.tsx` for the right pairing.
- `_ds_bundle.css` → `components/bundle.css` **minus the four named-scheme blocks**
  (between the `@schemes` sentinels `design-mirror/build.mjs` writes). The page
  compiles those from `tokens.json` into its own `tokens.css`; shipping them twice
  would let a page-side edit silently lose to the bundle. What stays is what
  `tokens.json` cannot express: `fp-system` under `prefers-color-scheme`, the sampled
  spring easings, every component style. The local `dist/styles.css` keeps the
  blocks — `.gallery` and the Playwright capture have no `tokens.css` of their own.
- `tokens.json` is regenerated in the page's list shape from the mirror's map-shaped
  one: four themes (`fp-brand-light` first — the page needs a default — then
  `fp-brand-dark`, `fp-cockpit`, `fp-chart`), no `light` duplicate, no `fp-system`
  (a page theme cannot follow the device; `bundle.css` does that), no `spacing`
  family (Kotlin has no spacing scale to export — add one there first).
- The `@ds-bundle` header keeps the namespace and component order and drops the
  `sourcePath`s, which pointed at `.jsx` re-export stubs the artifact does not carry.
- `tokens.css` is written too, in the page's own compile format. The type says the
  page generates it, and it does — but only on the page's own save, so a publish alone
  left previews on the previous compile, pointing at fonts that no longer existed. The
  page overwrites it identically on its next save.
- One extra colour token, `background`, an alias of `fp-background`. The page paints the
  ground behind every preview from a token named exactly `background` / `bg` / `page` /
  `canvas` / `paper`; with none it guesses light or dark from the theme's *name*
  (`/dark|night/`), which put Cockpit on a cream well. Found by reading the type's
  `app.js` (`Ho0`, `HE`); the alias gives the page the name it looks for.
- TSDoc `{@link X}` in a guideline becomes `` `X` ``; the slicer passed it through raw.

### The theme attribute and the page picker

`FlightPlannerTheme` scopes its colours under `data-theme="fp-<id>"` — the same
attribute the artifact's page sets on `<html>` from its theme picker — rather than a
mirror-only `data-fp-theme`. With the picker at its default (`fp-brand-light`, the
first theme) every wrapper wears its own theme; on any other choice the wrapper sets
no attribute and reports the page's theme through `useFlightTheme()`, so one picker
restyles every wrapper, nested ones included (`pageTheme()` in
`FlightPlannerTheme.tsx`). No local consumer sets `data-theme` on `<html>`, so
locally nothing changed. `system` still resolves in CSS, never in JS: the artifact's
own hand-edited bundle read `matchMedia` for it and would have flashed light for a
frame; that branch was deliberately not ported.

### Roboto is one variable file

Google serves the same woff2 for every Roboto weight, so requesting `400;500;700`
produced three `@font-face` rules pointing at one file misleadingly named
`roboto-400.woff2`. `fetch-fonts.mjs` now asks for `400..700` and writes one face,
`font-weight: 400 700`, in `roboto-400-700.woff2`. Nothing rendered differently
before; the name was the only lie.

## What is being synced

`design-mirror/` is a **React mirror of `:core:designsystem`**, written for this
purpose. This repo has no JavaScript design system of its own: the app is Kotlin
and Jetpack Compose, and the artifact's preview frame executes React out of a JS bundle,
so a Compose `@Composable` cannot be uploaded. The mirror is the bridge.

**As of the 2026-09-06 re-sync it also carries Phase G — the 3D globe** (commit
`1f2287d`), as an *outline globe*: the app draws NASA satellite imagery on a
Filament sphere, the runtime here has no GPU, so the mirror projects the same
`land.outline` the phone reads through the same camera and clips it to the limb.
`GlobeHero`, `GlobeNetwork`, `ImmersiveGlobeScreen`, `GlobeRouteScene`,
`GlobeView`, `GlobeCameraControls`, `GlobeAttribution` — plus `heroMode="globe"`
on `RouteDetailScreen` and `globeAvailable` on `VisitedNetworkCard`. The camera
and fit maths are `design-mirror/src/geo/globeFrame.ts`, ported field-for-field
from `:feature:globe`'s `math/GlobeCamera.kt` and `math/GlobeFit.kt`.

**The direction of truth is one-way and permanent.** Kotlin defines; the mirror
follows. A concept designed in Claude Design comes back as *intent* — a hierarchy,
a colour role, a spacing rhythm — never as pixel values transplanted into Compose.
That trap is already recorded in the user's memory as
`design-numbers-are-intent-not-values`; this is the same hazard running the other
way.

## What is generated, and from where

Nothing in the mirror's design values is hand-written:

| Output | Generated by | From |
| --- | --- | --- |
| `design-mirror/src/tokens/tokens.json` | `DesignTokenExport` (a JVM unit test in `:core:designsystem`) | the real `ColorScheme`, `Typography`, `Shapes`, `MotionScheme` and `MaterialShapes` objects the app composes with |
| `design-mirror/src/tokens/tokens.gen.ts`, `design-mirror/dist/styles.css` | `design-mirror/build.mjs` | `tokens.json` |
| `design-mirror/src/geo/worldOutline.gen.ts` | `design-mirror/build.mjs` | `app/src/main/assets/maps/land.outline` — the same bytes the phone reads |
| `design-mirror/fonts/` | `design-mirror/fetch-fonts.mjs` | Google Fonts (Roboto, Roboto Mono, Apache-2.0) |

`DesignTokenExportTest` **fails the build** when the committed `tokens.json` no
longer matches the Kotlin. That is the drift guard, and it is the whole reason the
mirror is defensible: drift is a test failure rather than a discipline.

To regenerate after changing a colour, type slot, shape or motion token:

```bash
./gradlew :core:designsystem:testDebugUnitTest --tests "*DesignTokenExportTest*"
cd design-mirror && npm run build
```

## Gotchas found while building this

- **Format numbers in `Locale.ROOT` in the exporter.** The first version used
  `"%.4f".format(v)`, which takes the default locale — Dutch here — and emitted
  every Expressive shape path with comma decimal separators (`M1,0000,0,5000`).
  That is not a parseable SVG path, and the loading indicator rendered nothing. It
  would also have made the committed export differ per machine. Fixed; the KDoc on
  `DesignTokenExport.Fixed` says why.
- **Sky palette keys are dotted paths** (`day.cloudEdge`, `sock.alternateBand`), and
  a `.` is not legal in a CSS custom property name. `build.mjs`'s `kebab()` maps
  dots to dashes; without it the variables existed but nothing could resolve them.
- **Playwright freezes CSS animations at their end state** when screenshotting. Any
  component whose resting appearance depends on an animation being mid-cycle
  captures blank. `MorphingLoadingIndicator` needed an explicit resting shape
  (`.fp-loading__path:first-child { opacity: 1 }`).
- **Never invent airport or runway coordinates.** A first draft used recalled
  Schiphol thresholds and drew a confidently wrong field. Real data is one query
  away — node 22+ has `node:sqlite` built in:
  ```js
  import { DatabaseSync } from 'node:sqlite'
  const db = new DatabaseSync('app/src/main/assets/databases/airports.db', { readOnly: true })
  db.prepare('SELECT ident,true_heading,length_ft,width_ft,lat,lon FROM runways WHERE airport_id=?').all(id)
  ```
- **`node -e "…"` with backticks inside is unsafe here** — the shell expands them and
  silently eats words from generated files. Two KDoc lines and one JSX attribute
  were corrupted that way. Use a heredoc with a quoted delimiter, or write the file
  with an editor tool.
- The converter classified `MapFrame` — a geometry class — as a component. Excluded
  via `componentSrcMap.MapFrame: null`. Phase G added `GlobeCamera` to that list
  for the same reason.
- **A mirror component never `import`s its own `.css`.** `build.mjs` walks
  `src/**/*.css` and concatenates every one into `dist/styles.css` independently;
  `tsconfig` has no ambient `*.css` module declaration, so an `import './Foo.css'`
  fails `tsc`. Name the file `Foo.css` next to `Foo.tsx` and it is picked up. (Cost
  an iteration on the Phase G components.)

- **A scrim inside a phone frame has no height of its own, and a sheet asking for
  90% of it gets 90% of *itself*.** `.fp-phone-frame__content` is a flex child with
  `min-height: 0`, so the scrim sized to its content and every sheet floated
  mid-screen showing four edges — a card with two square corners rather than a
  bottom sheet. Fixed in `BottomSheet.css` with two rules: the scrim fills the
  frame's content box, and `.fp-scrim:has(> .fp-sheet)` anchors to `flex-end` with
  no padding. The `:has()` guard is what keeps `ConfirmationDialog` centred.
- **`preview-rebuild.mjs` does not re-copy the stylesheet.** It recompiles a
  preview's `.tsx` into its `.html` and nothing else, so a CSS fix in
  `design-mirror/src/**/*.css` is invisible to a scoped rebuild — the card renders
  against the bundle's old `_ds_bundle.css` and looks unchanged, which reads as "the
  fix didn't work". Any CSS change needs a full `package-build.mjs` run before
  recapturing. Verify with `grep -c '<the new selector>' ds-bundle/_ds_bundle.css`.
- **A scoped `package-capture.mjs --components …` prunes every other review sheet.**
  Fine mid-iteration; before grading a full set, re-run it unscoped.
- **Playwright must be importable from the repo root**, not just from
  `design-mirror/node_modules` — `package-validate.mjs` resolves it from `.ds-sync/`.
  Install the version pinned to the cached chromium build (`ls ~/AppData/Local/ms-playwright`
  → `chromium-1234` → playwright 1.62.1 here): `(cd .ds-sync && npm i playwright@1.62.1)`.
  Without it validate exits 1 on `[RENDER_SKIPPED]` and the driver skips capture
  with `prior_failure`, which looks like a build problem and is not one.

## Deliberate divergences from the Android original

Recorded so a future sync does not read them as defects:

- **The globe is an outline globe, not a photograph.** Claude Design's runtime has
  no GPU and no imagery asset, so `GlobeView` draws `land.outline` through the
  ported `GlobeCamera` and clips it to the limb, over `sky.day.high` as the
  backdrop. The framing (`GlobeFit`, aspect-aware), the great-circle arc, the
  projected DEP/DEST plates (merge-when-close, limb fade, chrome fade), the limb
  rim and atmosphere ring, and the on-glass chrome are all faithful. The still
  globe has **no gestures** — `bearing` and `tilt` are always zero — so the
  compass cell in `GlobeCameraControls` only shows when a caller passes
  `bearingDegrees`, and the buttons are drawn inert like `BottomSheet`'s handle.
- **`GlobeFit`'s distance is clamped to `[2.2, 4.5]` in the mirror** (`MIN_FIT_DISTANCE`
  / `MAX_FIT_DISTANCE` in `globeFrame.ts`), not the app's `[1.0001, 10]`. The
  app's globe is interactive — a leg framed near the surface can be pinched out
  of, a whole-planet logbook spun — so either extreme is fine there. A **still**
  globe at `distance ≈ 1.03` is a near-flat close-up, and at `10` a marble in a
  field of backdrop; the clamp keeps it reading as a sphere at both ends. The
  floor also stands in for the app's `sharpestAltitude` (one screen pixel per
  deepest texel), which the mirror has no tiles to compute.
- **`EDGE_MARGIN` is `0.24`, not the app's `0.18`.** "About a plate" of clear
  margin is a bigger fraction of a 360 dp mirror box than of a 411 dp phone
  window. Same intent, scaled.
- **The route detail's globe-mode app bar stays on glass for the length of the
  hero.** The app returns it to `surface` once the page has scrolled up; the
  mirror's concept surface is a still frame, so the bar just scrolls away with
  the hero (absolute, not pinned).
- **Phase G's `:core:designsystem` changes are noted, not reproduced.**
  `sharedExit()` now runs on `effectsFast()` (an asymmetry the shared-element
  transition uses — and the mirror already does not carry that transition);
  `LocalThemeChoice` / `SystemBarsOverMedia` / `FlightMotion.flingDecay()` are
  new but they drive window-inset glyph colour, a compositional theme read, and
  fling momentum — none of which a static React mirror has a surface for. The
  cockpit imagery-dim (`CockpitImageryDim = 0.66`) has no effect here because
  there is no imagery to dim; the mirror's Cockpit globe is just the dark scheme.

- **Motion is approximated as sampled CSS `linear()` easings.** `build.mjs`
  integrates each spring's real step response, so the overshoot of a spatial spring
  and the critical damping of an effects spring are both preserved — but CSS has no
  spring, so an interruption mid-flight does not retarget the way Compose's does.
- **`MorphingLoadingIndicator` crossfades between the four polygons rather than
  morphing vertex to vertex.** The shapes themselves are the real exported
  `MaterialShapes` paths; CSS cannot tween two `d` values of differing structure.
- **The sky band is snapped between phases rather than blended across them.**
  Kotlin mixes two bands continuously where their cloud ink runs the same way round
  and drives a short `FlightMotion.effects()` traversal across the reversal where
  it does not — because interpolating across that reversal provably destroys the
  3:1 guarantee on a deck's underside: both the ink's luminance and the air's are
  continuous in `t`, so there is a `t` at which they are equal and the edge is a
  1.0:1 line. A still mirror has no traversal to be continuous with, and its bands
  are CSS variables whose luminances it cannot compare, so `resolvePhase` takes the
  band that spring settles on and never enters the middle. The *time of day* is the
  app's — read from the reported sun's elevation through the ported `skyBlendFor`,
  with `phase` used only when a report carries no position.
- **`SkyProfile` draws the scene faithfully but simplifies the motion.** Deck drift,
  per-drop precipitation speeds and the two-bolt coprime lightning envelope are not
  reproduced; a convective deck gets a static bolt. The geometry that carries the
  meaning — the piecewise axis, the four air steps breaking at the flight-rules
  thresholds, decks at true bases, the opaque fog slab, the hatch for each kind of
  unknown, the celestial rail — is ported.
- **Emphasized type slots are identical to their regular cuts** — see the finding
  below. The mirror reproduces what the app actually renders, not what the type
  scale intends.
- Interaction-only states are not previewed: the windsock drag, swipe gesture
  tracking, and the FAB's shape-morph on press.
- **The sheets, the picker and the date dialog carry no behaviour.** `BottomSheet`
  renders inline rather than in a portal, so a sheet can be one element of a screen
  concept; its drag handle is drawn and inert. `PickerSheet` reproduces the sheet's
  shape and its result rows but not the focus-one-frame-late keyboard rise, the
  query debounce, or the scroll-to-top on a new query. `FlightDatePickerDialog`
  makes future days `disabled` — the app's constraint stated where the choice is,
  rather than a validation after it — but nothing is clickable.
- **Four app behaviours are deliberately absent, and the conventions header says so
  in words** rather than half-implementing them: compact-height and landscape
  reflow; the bottom bar and app bar retracting on scroll (`PhoneFrame` always
  draws them); swipe-to-delete and the undo that follows (the
  `SwipeActionBackground` component is here, the gesture and the snackbar are not);
  and the picker's search affordances. A static library that faked one would let a
  concept assume a gesture it cannot show.
- **`VisitedNetworkCard` has no Compose counterpart yet.** It is the one component
  in the mirror that is ahead of the app rather than behind it: the statistics
  screen currently has no map. It reuses the app's own `MapFrame` and
  `land.outline`, so the geometry is the app's, but if the concept is adopted the
  Kotlin has to be written. Treat it as a proposal, not as a mirror.
- **`StartupCheckScreen` and `LicencesScreen` are simplified.** Both are real
  screens reachable from Settings; the mirror carries their shape and their status
  vocabulary, not the live check results or the generated licence manifest.
- **The shared-element transition is not reproduced.** In the app the route card's
  face travels to the detail screen's hero — one element holding the map *and* the
  figures printed over it — on a spatial `boundsTransform`, with `sharedEnter`/
  `sharedExit` deliberately fade-only because an overlay does not inherit the
  transform of the screen beneath it. A component library has no navigation for
  that flight to happen across, so the mirror ships the two ends (`RouteCard` and
  `RouteDetailPane`, one component in both places) and the motion tokens, but not
  the transition itself. The entrance stagger and the press state **are** ported.

## A finding about the app itself

In `material3:1.5.0-alpha26`, **all fifteen `*Emphasized` typography slots are
byte-identical to their regular counterparts** — same size, line height, letter
spacing, weight and family. `FlightTypography` deliberately fills the ordinary
slots with the emphasized cut (`titleMedium = titleMediumEmphasized`, and so on)
and documents that decision at length, but at the value level it is currently a
no-op: nothing in the app renders differently because of it.

This is a finding about the Android app, not about the mirror. It was surfaced by
the export, which reads the real `TextStyle` objects. Worth deciding deliberately —
either the alpha has not wired emphasis up yet and the scale is correct in
anticipation, or the slots need explicit weights.

**A second finding, from the Phase G port:** `:feature:globe`'s `GlobeControls.kt`
defines `PlateAlpha = 0.82` and its KDoc calls it the single value "shared by the
camera stack, the airport labels and the credit". But `ImmersiveGlobeScreen.kt`
declares its *own* `private const val PlateAlpha = 0.78f` for its `RoutePlate`
and collapse button, and `RouteGlobeHero`/`RouteDetailScreen` import the 0.82 one.
So the immersive screen's plates are 4% more transparent than the hero's for no
stated reason. The mirror uses 0.82 everywhere (`GLOBE_PLATE_ALPHA`). Likely a
stray literal in the app.

## Fixed after review

Recorded because each was invisible in a green build, and the next port of a
Compose component can make the same one:

- **The sky band followed a `phase` default instead of the reported sun**, so a
  report with the sun 18° below the horizon drew a bright daytime sky with a moon
  in it — the "drawing lying" defect the whole weather phase exists to remove,
  reintroduced by a prop default. `skyBlendFor` was correctly ported and then never
  called. **Porting a function is not porting the decision that calls it.**
- **Celestial bodies were drawn with no rail inset**, so a body near due east or
  west lost half its disc to the frame edge, and a sun and moon at similar azimuth
  overlapped. `railXInset` and `separatedMoonX` exist in the Kotlin precisely
  because both were seen in its own gallery; neither had been ported.
- **`RouteMap`'s clip path used a constant DOM id.** Two maps on one page share the
  first one, and the rect is in `userSpaceOnUse` units sized from *that* instance's
  aspect — so a route detail above a list of cards cropped every card's right tenth.
  `useId()`. Any `<defs>` id in a component that can appear twice needs this.
- **Runway pairing failed for a field with no published thresholds.** `let best =
  Number.MAX_VALUE` with a strict `<` never fires when every candidate returns
  `MAX_VALUE`, which is exactly the dataset that falls back to the lane schematic.
  Kotlin's `minByOrNull` picks the first on a tie and its KDoc says so on purpose;
  **a `minBy` ported to a loop must seed from the first element, not from infinity.**
- **The visited-network frame was fitted to wrapped longitudes**, so a trans-Pacific
  leg was projected outside its own window and silently vanished while its markers
  landed. `MapFrame.forRoute` documents unwrapped input; `unwrapLongitudes` now
  supplies it.
- **`useIsDark()` reported light for a `system` theme on a dark viewer.** The CSS
  resolves `system` through `prefers-color-scheme` and is right from the first
  paint; the React context was hard-set to `brandLight`, so a component branching
  on the hook rendered its light branch against dark variables. The media query is
  now read for the hooks alone, never for a colour.
- **The drift guard could be walked past by running it twice.** It wrote the
  regenerated JSON *and* failed, so the second `./gradlew build` passed against
  what the first had written. It now only reports, names the first differing line,
  and leaves the tree alone; regenerating is `-Dtokens.write=true` and nothing else.
  Verified by planting a wrong colour and watching two consecutive runs fail.
- **`build.mjs` interpolated type metrics raw**, so a null from `unit()` would emit
  `font-size: nullpx` — which a browser drops silently, leaving the slot inheriting
  and looking almost right. It throws now.
- **`RouteCard` restated the enter stagger as literals** in a package whose whole
  premise is that no design value is hand-written. Read from `tokens.constants`.
- `StartupCheckScreen` summarised a list containing warnings as "All checks passed".

## 2026-09-16 re-sync: the globe, and three weeks of Kotlin drift

Phase G (`feat: the 3D globe`) and several designsystem changes landed between
the 2026-08-29 sync and this one. What each needed:

- **The globe went through two attempts in one session; the second replaced the
  first.** First pass: reproducing `:feature:globe` faithfully looked out of
  scope (it is a Filament/Vulkan native pipeline — a tessellated mesh,
  quadtree-streamed satellite tiles, a hand-written camera and gesture system,
  ~10,000 lines native plus ~3,000 more in its Compose chrome —
  `GlobeSurface.kt`, `GlobeControls.kt`, `GlobeLabels.kt`), so a themed CSS
  stand-in shipped instead (a radial-gradient sphere, a decorative route arc that
  did not vary with distance or bearing). While closing that work out, a git
  branch was found — `phase-g-globe`, committed **2026-09-06**, never merged, its
  remote deleted — carrying a real field-for-field port: camera and opening-fit
  maths from `GlobeCamera.kt`/`GlobeFit.kt`, the actual `land.outline` projected
  through it as an outline globe with a limb silhouette and back-face cull, a
  great-circle arc that actually bends over the curve. This is what the orphaned
  upload in the project's `_ds_sync.json` anchor (dated ~2026-09-06, matching
  `.cache/review/*` grade files from that date) had actually come from — the
  source was never lost, only unmerged. **Integrated by rebasing/merging
  `phase-g-globe` onto this sync's other changes** (it predated the RouteMap
  `topInset`/short-hop port and the `VisitedNetworkCard` rewrite, both reconciled
  cleanly since they touch disjoint parts of `StatsCards.tsx`/`RouteMap.tsx`).
  The CSS stand-in (`GlobeSphere`, `GlobeRouteArc`, the old flat-API `GlobeHero`)
  is gone, replaced by the real port: `GlobeView`/`GlobeHero`/`GlobeRouteScene`
  (`components/GlobeHero.tsx`), `GlobeNetwork`, `GlobeChrome.tsx`'s
  `GlobeCameraControls`/`GlobeAttribution`, `screens/ImmersiveGlobeScreen.tsx`,
  and the geometry in `geo/globeFrame.ts`. `RouteDetailScreen` gained a real
  Flat/Globe toggle in the app bar (`heroMode`, `globeAvailable`,
  `onOpenImmersiveGlobe`) and `VisitedNetworkCard` gained the matching
  `globeAvailable`/`initialView` toggle, both defaulting to the flat view so
  existing concepts are unaffected. **Lesson for next time: an unmerged branch
  is not a save point either — a design-mirror change is only safe once it is
  on a branch that gets merged**, and `git branch -a`/checking for
  stray local branches belongs in the "explore the repo" step of a future
  re-sync, not just `git log` on the current branch.
- **`RouteMap.kt` gained `topInset` and a short-hop ring, ported.** The route card's
  title now keeps a band clear at the top of the map (`MapFrame.forRoute`'s new
  `topInsetFraction`, ported field-for-field into `mapFrame.ts`), and a leg whose
  projected chord is under 24 dp draws as a single ring instead of an
  overlapping arrowhead + two endpoint markers (`MIN_ARROW_CHORD`,
  `projectedChord`, both now exported from `RouteMap.tsx`). `RouteCard.tsx` passes
  `topInset={36}` (16 dp padding + titleSmall's 20 dp line height, matching
  `RouteCard.kt`'s own computation).
- **`VisitedNetworkCard` was rewritten to match `NetworkMap.kt` — it was the
  "first, wrong version" the Kotlin's own KDoc describes.** It now draws from
  `RouteMap`'s exported ink (`ROUTE_STROKE`, `CASING`, `COAST_STROKE`,
  `ENDPOINT_RADIUS`, `OUTLINE_MARGIN`, `arrowPath`, `projectedChord`) instead of
  ad hoc strokes; casing goes under every leg before any leg is drawn, dots size by
  `nodeSizeFraction` (ported to `mapFrame.ts`) from `NODE_MIN_RADIUS` (=
  `ENDPOINT_RADIUS`) to `NODE_MAX_RADIUS` (9), largest-first; the longitude unwrap
  changed from a chain walk to the widest-gap-seam algorithm
  (`unwrapLongitudeSet`, matching `NetworkFraming.kt` exactly) with `arcShifts`
  handling a leg whose ends fall in different unwrap turns. Node fill is
  `var(--fp-primary)`, not the old `tertiary`.
- **`RunwayDiagram.kt` and `SkyProfile.kt` changes needed no mirror action.**
  Both gained a required `contentDescription` param — pure accessibility, no pixel
  changed. `SkyProfile.kt` also moved a state read from build phase to draw phase
  for performance — same output, faster recomposition. Verified by diff, not
  assumed.
- **`FlightMotion.kt`'s additions (`flingDecay`, `lateralEnter`/`lateralExit`,
  `sharedExit` now using `effectsFast`) needed no mirror action.** The fling is
  globe camera inertia (interaction-only, already out of scope per NOTES' own
  "Interaction-only states are not previewed"); the lateral transition is
  Settings' screen-to-screen navigation (not modeled — this mirror has no
  navigation, see "shared-element transition is not reproduced" above); the
  `sharedExit` retune affects only the unreproduced transition itself.

## `ChallengeWidgetCard` — the home-screen widget, added same day on request

Ported from `ChallengeWidget.kt`/`ChallengeWidgetTheme.kt`/`DailyChallengeSource.kt`
(the Phase H3 daily-challenge widget), not scoped into the original run because
nobody had asked yet. It reuses `RouteMap` directly for its background — the
real widget can't (Glance only shows bitmaps, so it renders three tinted alpha
masks via `renderRouteMapLayers`), but this mirror has no such constraint, so
`<RouteMap topInset={36} .../>` draws the identical geometry live. `topInset`
is `PADDING (16dp) + TITLE_LINE (20dp)`, the same figure `renderChallengeMap`
computes. Card tones (`surfaceContainer`/`surfaceContainerHigh`) match
`widgetPalette`'s non-dynamic-colour branch — dynamic colour itself (the
`system_accent2_*` resources Android 14 publishes) has no web equivalent and
isn't attempted.

- **A `box-sizing: content-box` default nearly shipped a clipped widget.**
  `.fp-widget-card__content` had `height: 100%` and its own `padding: 16px`;
  without `box-sizing: border-box` the padding adds on top of the 100%,
  overflowing the outer card's fixed-`height` box and clipping the bottom row
  under `overflow: hidden` — invisible until the actual screenshot was looked
  at (the div measured "correct" by every static check). **Any fixed-height
  card with its own padding needs `box-sizing: border-box` stated explicitly**;
  the rest of this mirror gets away without it because most cards size to
  their content rather than to a hard pixel height.
- **The `Compact` cell is 180 px wide, not the real `COMPACT` constant's literal
  140 dp.** `ChallengeWidget.COMPACT = DpSize(140.dp, 100.dp)` is the
  `SizeMode.Responsive` bucket's breakpoint name, not a guarantee of the actual
  rendered width — Glance composes at whatever width the launcher's grid cell
  actually grants, and that bucket covers everything from there up to
  `WIDE_THRESHOLD` (220 dp). At a literal 140 px, two 26 px bold ICAO codes and
  two figure pills do not fit in the 108 px of content width left after
  padding — previewing it at exactly 140 px would show a card that reads as
  broken for what is, in practice, the common case. 180 px is inside the real
  range and renders cleanly.

## `AircraftWidgetCard` — the second widget, 2026-09-19 re-sync

Ported from `AircraftWidget.kt` and the grammar both widgets now share in
`WidgetCard.kt` (app PR #30, Phase H7). `ChallengeWidget.kt`'s own diff in that PR
is a pure refactor onto `WidgetCard` — same tones, same numbers — so
`ChallengeWidgetCard` needed no mirror change. The new card takes `wide` and `tall`
(both default false, as the widget is two by one by default) for the four Glance
layouts, and reuses `.fp-widget-card` / `.fp-widget-figure` from
`ChallengeWidget.css`; `AircraftWidget.css` adds only the sizes, the wrapping name,
the FLOWN badge and the status dot.

- **Sized at the reference launcher's real grants, not the bucket floors.** The
  brand book's Layout section (added 2026-09-19, from the phone) says to design a
  widget at 176 × 90, 376 × 90, 184 × 204 and 376 × 204 dp, so this card does — the
  first component to. `ChallengeWidgetCard` still draws at 250 × 100 / 180 × 100,
  which its own CSS note explains and which was not retuned here (its Kotlin did
  not change). Restating it at the grants is the obvious follow-up so the two
  cards' base lines meet on the tall layout as `AircraftWidget.kt`'s KDoc intends.
- **A figure pill wrapped.** At a true 176 px the short compact card's content
  width is 144 px and `B789` + gap + the range pill is ~148, so `7,355 NM` broke
  onto two lines inside its pill. The Kotlin `Figure` never wraps (the XML's own
  comment says the layout wants ~174 dp and the real 176 dp grant fits it), so
  `.fp-widget-figure` is now `white-space: nowrap` — in `ChallengeWidget.css`,
  because both cards share the pill. The few pixels of overflow land in the
  card's own gutter, invisible.
- The quiet NOT FLOWN state is a filled `surfaceContainerHigh` pill, not the
  mock's outlined one, because that is what the app ships: Glance has no themed
  stroke, and `AircraftWidget.kt`'s `FlownBadge` KDoc records the choice. The
  compact card's dot carries an `aria-label` for the reason the real `Image` has
  a `contentDescription`.
- Verified from the resync's own `_screenshots/general__AircraftWidgetCard.png`,
  not the live page: the built-in browser is not signed in to claude.ai.

## Re-sync risks

- **The airport database and the world outline are read at build time.** If
  `airports.db` or `land.outline` is regenerated, re-run `design-mirror`'s build so
  `worldOutline.gen.ts` follows, and re-check any preview that hard-codes
  coordinates (`RunwayDiagram`, `AirportDetailScreen`, `RouteCard`, `PlanScreen`).
- **Preview data is inlined in `.design-sync/previews/*.tsx`.** It is real data, but
  it is a copy: it will not follow a dataset update on its own.
- **Fonts are fetched from Google Fonts** by `fetch-fonts.mjs`. The committed woff2
  files are what ship; re-running the script pulls whatever version is current.
- **`MetarPanel` is the single weather surface, and the screens compose it.** An
  earlier draft had `AirportDetailScreen` and `RouteDetailScreen` each build a bare
  `SkyProfile` inside a titled card, which is not what the app does and let the two
  ends of a route drift apart. Both now render `MetarPanel`. If the app's panel
  changes, this is the one component to follow.
- **`StatsScreen` is a composition of the statistics cards**, not markup of its own.
  A card retuned in a concept lands in every arrangement of those figures at once.
- **The globe intent was first mirrored on 2026-09-06** (`phase-g-globe`, never
  merged at the time — see the 2026-09-16 entry above for why it took until then
  to land) and carries `feature/globe`'s open findings from that date:
  `feature/globe` is still ⚠️ in `docs/UI-PLAN.md`, so the *intent* of the globe
  is mirrored but the app end was not fully settled as of that port. If a later
  finding changes how the hero switch, the fit or the glass chrome behaves, the
  mirror should follow.
- **`globeFrame.ts` is a hand-port, not a generated file.** Unlike `tokens.json` /
  `worldOutline.gen.ts` there is no drift guard on it. If `:feature:globe`'s
  `GlobeCamera` / `GlobeFit` maths changes, re-port by hand and re-check the
  `GlobeHero` / `GlobeNetwork` / `GlobeView` previews.
- **`GlobeCamera` is excluded from the card set** (`componentSrcMap.GlobeCamera:
  null`) — it is a maths class, not a component, and the converter classified it
  as one (same as `MapFrame`). It is still exported from the bundle.
- `material3` is pinned to an alpha. A bump can change the type scale, the shape
  scale or the motion scheme; `DesignTokenExportTest` will catch it, and the mirror
  then needs a rebuild.
- **The real `RouteDetailScreen`/`RouteDetailContent.kt` grew a "spine" layout in
  the same commit as the globe (`1f2287d`), and the mirror was NOT updated for it.**
  Per `RouteGlobeHero.kt`'s own KDoc, the real screen no longer carries a row of
  four `ValueChip`s under the hero — every figure now sits at the point on the leg
  where it is true, along a 1,386-line `RouteDetailContent.kt`. `RouteDetailPane`
  here still renders the old `fp-detail-facts` chip row. This is a real, separately
  scoped port (verified in-session to be large, not merely deferred out of
  caution) — read `RouteDetailContent.kt` before touching `RouteDetailPane.tsx`
  next.
- **`GlobeHero`/`ImmersiveGlobeView` are a deliberate stand-in, not a port** — see
  the "Globe stand-in" entry below before assuming more fidelity than is there.

## Known render warns

- `SwipeActionBackground / AtRest` renders as an empty cell. That is correct: the
  component is fully transparent at rest, which is the property that keeps a
  resting list free of coloured bands under its rows.
- Nothing else. The 2026-09-06 re-sync ran the full render check over all 60
  previews (53 + the 7 new globe components) and reported no `bad`, no `thin` and
  no `variantsIdentical` — so any warn line a future run prints is new and should
  be looked at rather than assumed known.
- **The globe's DEP/DEST plate can sit close to the camera-control stack** on an
  E-W leg whose right endpoint lands in the bottom-right quadrant (`GlobeHero`
  `LongHaul`). It stays readable; the app has the same corner and accepts it. Not
  a warn line, but do not "fix" it by moving the stack — the app's own KDoc places
  it there deliberately.
- `FleetDetailPane` and `FlightDetailPane` ship the **floor card** by choice: they
  are the tablet halves of screens that have their own authored previews, and the
  user scoped this run to what was already authored. Authoring
  `.design-sync/previews/FleetDetailPane.tsx` and `FlightDetailPane.tsx` is the
  standing offer on any later re-sync.
