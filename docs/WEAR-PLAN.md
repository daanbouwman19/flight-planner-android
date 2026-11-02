# Wear OS — the watch app

The Galaxy Watch 9 build of Flight Planner: one route filling the round face, a
swipe up for the next, a tap to open that route in the phone app.

**Status: running on the watch, and corrected by what that showed.** The first
pass was written without a device; a session over wireless `adb` on 2026-09-22
installed it, drove it, and found four layout defects and one dead input that no
build, test or preview had any way to report. All five are fixed — see *What the
device found*. What follows is what shipped, what was decided and why, and what
is deliberately not here yet.

## The target

One device: Daan's Galaxy Watch, and nothing else for now. That settles more
than it sounds like it does.

- **The device reports `SM_L350`, Android 17, API 37** — above the API 36 this
  document first assumed, and comfortably above the repo's `minSdk 35`, so
  `:wear` keeps the single floor every other module has and the no-`SDK_INT`
  invariant is untouched. If the target ever widens to Wear OS 5 hardware
  (API 34) this comes back: the floor is one version-catalog entry applied by
  the convention plugins, so lowering it is either global or a per-module
  exception.
- **One round screen, now measured: 480 × 480 at 340 dpi — 226 dp across.** The
  design was drawn against 454 × 454, and the face places its text in fractions
  of the face rather than in dp for that reason — see `RouteFace`'s KDoc. The
  22 px difference is small; what it is not is free, and every one of the layout
  defects below is a place where the design's fractions met a real circle and
  lost. A round face is narrower than its width everywhere but the middle, and
  text laid out near the top or the bottom has far less room than a fraction of
  the *diameter* suggests.

## What it does

`:wear` is a standalone app. It generates its own routes from its own copy of
the airport index and is useful with the phone out of range; the phone is needed
for exactly one thing the wearer can ask for, opening a route in the full app,
and that path says so when it fails rather than the app refusing to start
without a companion. The theme also comes from the phone, but never blocks:
whatever it last published is already stored on the watch, and before it has
published anything the face takes the default dark look.

| Piece | Where |
| --- | --- |
| The route face, pager and map | `wear/…/ui/` |
| The map's scenes, its style and their cache | `wear/…/map/`, drawn through `:core:mapscene` |
| The endless batch feed | `wear/…/route/WatchRouteFeed.kt` |
| Assets — index, seed fleet, coastline | `wear/…/data/WearAirportData.kt` |
| The tap to the phone | `wear/…/handoff/PhoneHandoff.kt` |
| The link both APKs compile against | `:core:handoff`, `WatchRouteLink` |
| The theme both APKs agree on | `:core:handoff`, `WatchThemeSync` |
| The phone publishing its theme | `:app`, `PublishThemeToWatch` |
| The watch following it | `wear/…/theme/` |
| The phone's end of it | `:app`'s manifest, `LaunchIntents`, `LaunchRequest.OpenWatchRoute` |

## The handoff

The watch builds `flightplanner://route/EHAM/EGLL?nm=200&ac=C172&name=…` and
hands it to `RemoteActivityHelper`; the Wear companion app carries it to the
phone, where an `<intent-filter>` on `MainActivity` catches it as an ordinary
`ACTION_VIEW`.

Chosen over a Data Layer message because the phone side is then one
`<intent-filter>` and no new dependency at all — `:app` has a 500 ms cold-start
budget, and this is a feature it uses once per tap on a different device. The
Data Layer is the more capable of the two and is where to go the moment the
watch needs to *read* something from the phone; the argument is written out on
`PhoneHandoff`.

**The airframe is named, not identified.** The watch's fleet is the bundled seed
CSV parsed in its own process, where ids are positions in a file; the phone's are
Room row ids that depend on what the user has added and removed. So the link
carries the display name and the type code, and `LaunchViewModel.resolveWatchRoute`
matches them against whatever fleet that phone is carrying — name first, then type
code, then nothing. An airframe the phone does not have lands on Plan rather than
on a detail screen for an id that means nothing there.

## Defaults picked, and what they cost

Three design questions were open when this was built. Each got a default rather
than a wait; each is cheap to revisit.

**METAR and the flight-rules badges: not in this pass.** The design canvas shows
a VFR badge under each code. Fetching two METARs per route for a list the wearer
flicks through is a lot of radio on a wrist, and a badge that shows a stale or
assumed category would be the app restating the weather — which the flight-rules
palette's own KDoc says it may not do. So the watch carries no `INTERNET`
permission at all and the badges are absent rather than wrong. The obvious next
increment is to fetch for the *settled* route only, both ends, and show the badge
when it lands.

**Theming: the phone's, read across.** ~~The brand dark scheme, always.~~ This
one did not stay a default for long — Daan asked on 2026-09-22 for the watch to
take its colours from the phone so the two stay in sync, and it now does. The
phone writes its `ThemeChoice` to a Wearable `DataItem` whenever the setting
changes (`PublishThemeToWatch`); the watch reads that item and follows it
(`WatchThemeSource`). All four looks cross over — brand light, brand dark,
Cockpit, Chart — and the map's inks are roles rather than values so it follows
too.

A `DataItem` rather than a message, because it is **replicated and then kept on
the watch**: the last theme the phone published is there at launch with the
phone off, out of range or unpaired. That is also why nothing is cached a second
time on the watch. The grammar is `WatchThemeSync` in `:core:handoff`, which
both APKs compile against, and it is the reason `:app` now carries
`play-services-wearable` — the dependency the deep-link handoff was chosen to
avoid, taken on here because this direction cannot be done with an
`<intent-filter>`.

Two things are deliberately not identical to the phone. **The dark backgrounds
go to true black** (`#000000`) rather than the phone's `#111319` and `#110A02`,
because the display is OLED and the design's full-bleed map wants the bezel to
disappear; Chart is exempt, since a paper chart that went black would be a
different theme. And **dynamic colour is not reproduced** — a watch has no
wallpaper to derive the phone's scheme from, so a phone on dynamic colour lands
on the brand scheme of the matching tone.

**Units: nautical miles, fixed.** The phone reads a unit system from DataStore
and can show kilometres. The watch has no settings screen to offer the choice
and no way to read the phone's yet, and inventing a second place to set it would
mean two answers to one question.

**Mark-as-flown: not here.** It is the action most worth having on a wrist and it
writes to the logbook, which means the Data Layer and a round trip the watch has
to survive being out of range. That is its own piece of work, not a corner of
this one.

## What the watch ships

1.5 MB of assets, not 7.7 MB. `CopyWearAssets` in `wear/build.gradle.kts` takes
three files from `:app`'s assets at build time — the prebuilt airport index, the
seed fleet CSV and the world outline — so one copy stays under version control
and the watch skips the 6.5 MB SQLite database entirely. It is registered once
per variant and wired to that variant's asset sources through AGP's Variant API,
which is what carries the task dependency into `mergeAssets`; adding the
directory to `sourceSets` instead is refused outright by AGP 9. The database carries
names, municipalities, elevations and runway detail; route generation reads none
of them and this app shows codes rather than names.

**The airframe flies the route (Phase I).** The face draws the route's aircraft
from above at the leg's midpoint, nose along the track, 24 dp long and cased in
the face's ground: the phone's route-card rule, and since *Swipe performance*
below literally the phone's code, from `:core:mapscene`. (It was first written
again in `WatchRouteMap` as `planeOnLeg`, because `:wear` cannot see
`:core:designsystem`.) It draws only where the leg is at least 1.6 plane lengths
end to end, so a short hop keeps its two dots. The outlines come from `:core:aircraftart`, which is pure JVM and so a
plain dependency here. The world outline the watch copies is the 1:50m one, at
about 240 KB.

## What the device found

Five defects, none of which a green build could have reported. Four are the same
mistake — laying text out against the face's *width* when the constraint is the
*chord* at that text's own height — and the fifth is an input that was simply
never wired.

| What | Why it happened | Fix |
| --- | --- | --- |
| **The destination code wrapped, on essentially every route.** `ZYBA` drew as `ZYB` over `A`, `VYAN` as `VY`/`AN`, `KRKS` as `KR`/`KS` | A plain `Row` gives its first child the whole remaining width and the second whatever survives, so it was always the destination that ran out. The pair needs ~187 dp at 30 sp; `SIDE_INSET_TOP = 0.13` was allowing 167 dp | Both codes take `weight(1f, fill = false)`, so they share what the arrow leaves. `IcaoCode` is `BasicText` with `TextAutoSize` (30 sp down to 22 sp) and `maxLines = 1`, which makes wrapping structurally impossible rather than merely unlikely — a fixed smaller size only moves the cliff, since `SAVC` measures 74 dp where a letter-heavy `EDMM` is wider |
| **The outer letters touched the bezel** once the codes fitted | The binding constraint is not the middle of the row but the *top corners* of the outer two letters, where the chord across the glyphs is narrowest. Measured at 1 px of clearance | `TOP_GROUP_FRACTION` 0.145 → 0.17 and `SIDE_INSET_TOP` 0.10 → 0.135. Near the top of a circle a little height buys a lot of chord, so the codes give up about 3 sp and gain 10 dp of glass |
| **The airframe name ran under the bezel.** `McDonnell Douglas MD-11 GE` set one line 357 px wide across a 348 px chord | It shared `SIDE_INSET_BOTTOM` with the two plates, but it sits *below* them, where the circle is markedly narrower — 147 dp against the plates' 181 dp | Its own `AIRFRAME_SIDE_INSET = 0.20`. A longer name wraps instead, which it already did and which the bottom-anchored column has room for |
| **The handoff flash had no width bound at all**, 20 dp from the top where the chord is only ~129 dp | Same error again, in the one piece that is not part of the face's own layout | Capped by `FLASH_SIDE_INSET` and moved to the middle. There is no third position along the top — a round face has no band above a full-width header, and lowering it into one put the opaque pill straight through the codes |
| **The bezel did nothing.** Six synthetic rotary events left the screen identical pixel for pixel | `:wear` used `androidx.compose.foundation.pager.VerticalPager`, which has no rotary parameter. `grep` found no rotary support anywhere in the module | Wear's own `VerticalPager` with `rotaryScrollableBehavior = RotaryScrollableDefaults.snapBehavior(pager)`. See *Rotary* below |

**And one that was not a layout defect at all: the destination dot was invisible.**
`MapFrame` fits a route to a *rectangle*, and `MAP_ZOOM = 1.3` then scaled that
rectangle past the face — the route's own extremes mapped to −2.4 % and 102.4 %
of the face, so one or both endpoint dots were drawn off the glass. The zoom
existed only to push the world outline's clip seam off-screen, which
`OUTLINE_MARGIN`'s 8 % already does, so it is now 1.0. (The margin has since
come down to 1 % as well — see *Swipe performance*.) The padding that makes a
route fit the *inscribed circle* rather than the square solves to 0.284 at the
worst corner, against the 0.12 default — verified across six route geometries
including a 2,944 NM diagonal, which is the case that clipped.

**The lesson is the one CLAUDE.md already states**, and it cost a session to
relearn: a preview cannot show a round display, window insets, or a system
gesture. Every defect above survived a green `build`, a passing test suite and a
correct `@Preview`.

## Rotary

The Galaxy Watch has no rotating bezel — the rim is capacitive, and swiping
around it is what generates rotary events. This matters less than it sounds,
because a crown, a rotating bezel and a touch rim all arrive as the same
`RotaryScrollEvent`: wiring the one parameter covers all three, and there is no
device-specific branch to write.

Two things came with that switch to Wear's `VerticalPager`, and the second was
unplanned:

- **`CustomTouchSlopMultiplier` and a page-tuned fling.** A swipe no longer has
  to beat the system's own edge gestures to register. This matters on a watch
  where swipe-up from the bottom is the launcher and swipe-right is back — the
  old pager lost that fight repeatedly while the feed was being driven from
  `adb`.
- **`AnimatedPage`** from `androidx.wear.compose.material3`, with
  `PagerScaffoldDefaults.snapWithSpringFlingBehavior`, replacing a hard slide
  with Wear's own cross-fade — roughly 180 ms, measured off a 60 fps capture.
  Taking Wear's curve rather than writing one also keeps `:wear` clear of the
  raw `spring()` the invariants forbid outside `:core:designsystem`, **a facade
  this module deliberately cannot reach**. That is worth stating plainly: `:wear`
  has no motion-token layer of its own, so every animation it wants must either
  come from Wear's defaults or motivate one.

## Swipe performance

**The symptom.** Swiping lagged, worst on routes between continents and across
oceans. Measured on the watch from a **release** build: a median frame of 34 ms,
about 30 fps against the display's 60 Hz. The UI thread recorded a frame in
~0.5 ms, with ~20 ms spikes on the frame a page was first drawn; RenderThread's
issue-draw ran at a 13.5 ms median and 60–120 ms in the first frames of every
swipe; the GPU at a 19 ms median (gfxinfo's *50th gpu percentile*, which HWUI
takes per frame as GpuCompleted − SwapBuffers). That GPU figure is of the tree
before any of this work. On the tree with the first four causes below in,
framestats put the same interval at ~6–8 ms on zoomed-out faces while
RenderThread's issue-draw ran at a 36 ms median: by then a page turn waited on
RenderThread, not on the GPU — see the fifth cause below.

**The cause** was the world outline, plus what the face did with it. The outline
went from Natural Earth 1:110m to 1:50m — 4,601 points to 59,690, 18.9 KB to
246 KB — so a long-haul face projects and draws some 40,000 fill and coast
vertices where it drew 3,000. Five things then made each of those vertices dear:

1. **The map was built in its draw.** `WatchRouteMap` framed, projected, clipped
   and walked the outline into paths inside its `Canvas` lambda, which runs the
   first time a page is drawn — the first frame of a swipe. Wear's pager
   composes and measures the next page ahead but never draws it, so nothing
   built the map early. One `projectOutline` allocated 1–5 MB.
2. **The cards were worded on the main thread.** The generator returns on the
   caller's context, and `toCard` — sampling each great circle, resolving each
   outline, initialising the generated outline table — ran after it, on the
   ViewModel's main thread.
3. **An 8 % margin nobody can see.** It hid a clip seam when the map was zoomed
   1.3×; at 1.0 on a round face, everything past the square is off the glass.
4. **Round coast joins.** The phone moved its coast to bevel joins and butt caps
   in Phase P3, measured as visually free at 1 dp; the watch's copy of the
   drawing never followed.
5. **Both maps are re-converted every frame of a turn.** `AnimatedPage` scales
   the outgoing and incoming pages through a `graphicsLayer`, and RenderThread
   converted both maps' dense paths again at every new scale.

**What shipped** addresses all five — the fifth in a second pass, once the first
four had been measured — and changes the face's look in exactly three declared
ways:

- **`:core:mapscene`.** The phone's scene builder and draw — `routeMapScene`,
  `drawRouteMap`, the ink, the plane rule, the outline cache — moved out of
  `:core:designsystem` into a module that is Compose graphics and no Material, so
  the watch draws through the phone's code instead of a copy of it. What the face
  does differently is `WatchMapStyle`: a frame padded 0.284 for a circle, a 1 %
  margin, two filled dots of 3 dp radius, no arrowhead, no short-hop ring, no
  graticule.
  The phone is byte-identical: its 161 goldens verify without re-recording.
- **`MapFrame.projectOutline` stopped allocating per ring and per segment**, with
  bit-identical output, pinned by `ProjectOutlineRegressionTest` across eight
  routes and three frames: 5,066 KB to 1,870 KB per call on EGLL–KJFK at the old
  watch margin, measured on a desktop JVM.
- **The map is built before the swipe.** `WatchSceneCache`, owned by the
  ViewModel, holds six scenes keyed by route, airframe outline and face, and is
  filled on the generator's dispatcher: the first page before the feed is shown
  (the face is measured on the loading screen), then the settled page, the next,
  the previous and the one after next on every settle. The map draws with
  `drawWithCache` from it; a flick that outruns it builds the scene there and
  then with the same function, so the face never pops in late and never looks
  different. A theme change redraws a scene and never rebuilds it. A prefetch
  skips a page another thread is still building: a newer settle cancels the
  older prefetch only between builds, and start-up and every top-up relaunch it
  on the same page, so without that skip the long-haul build already running
  was started a second time beside it.
- **`nextBatch` words its cards on the generator's dispatcher.**
- **The map draws into an offscreen layer of its own**, so a page turn scales a
  finished image instead of converting the coast again every frame — the fifth
  cause, taken on its own below.
- **Declared visual change 1: the coast strokes bevel/butt**, the phone's
  Phase P3 stroke, now shared rather than copied.
- **Declared visual change 2: the outline margin is 1 %, down from 8 %.** It
  removes 22–36 % of a long-haul face's fill and coast points — EGLL–KJFK,
  RJAA–KLAX and PHNL–NZAA, from the counts `ProjectOutlineRegressionTest` pins
  at both margins — and nothing visible: see `OUTLINE_MARGIN`'s KDoc.
- **Declared visual change 3: mid-turn, the map is a resample.** While a page is
  scaled, everything the map draws — land, coast, the route line, its dots and
  the airframe — is resampled from the layer's 1:1 image; on the watch that
  shows as a faint softening of the route line's edge and the coast, gone when
  the page settles. The text is drawn as vectors on the page and is unaffected.
  At rest the map is within 5/255 of drawing straight to the page. Figures
  below.

Nothing else on the face changes. The one numerical difference: the top inset now
reaches the frame through the scene's pixel parameter, so it is `0.22f` rather
than `0.22` — a shift under a millionth of a pixel.

**Measured after.** The first four alone left the zoomed-out faces slow. On the
seeded pass described under *How it was measured*,
a turn across faces under 15,000 vertices ran at 56–60 fps; 15,000–30,000 at
35–36 dragged and 41–45 flicked; past 30,000 at 20–26, with 28–36 % of frames on
time. Unseeded, over 40 flicks and 20 drags, janky frames went from 40 % of a
drag and 25 % of a flick before any of this work to 10–12 % and 17–21 %. With the
fifth cause addressed — variant E below — every zoom turns at 60 fps, dragged or
flicked, with 98–100 % of frames on time.

**The fifth cause, and the layer that took it.** What the first four left was
RenderThread's. Framestats over four zoomed-out drags (40,000–64,000 vertices),
on the tree with the first four in: issue-draw at a 36 ms median and 58 ms at
p90, the UI thread then waiting ~33 ms for RenderThread, GPU time
(GpuCompleted − SwapBuffers, the interval gfxinfo's gpu percentiles are built
from) ~6 ms — 23–30 fps, 75 % of frames late.
`AnimatedPage` gives each page a new scale every frame of a turn, and
RenderThread converted the fill and coast paths again at each one. At rest
nothing changes, so only a swipe paid, and the bill tracked how far out the face
is zoomed: ~200–2,000 vertices on a short hop, 15,000–64,000 on a long haul —
64,122 on OPMI–KELP, 40,260 on UBBQ–YMML, 32,682 on LFLV–CYRQ.

`WatchRouteMap` now puts `graphicsLayer(compositingStrategy =
CompositingStrategy.Offscreen)` ahead of its `drawWithCache`. The map is drawn
into that layer at 1:1 when its page is first drawn, and again only when the
map's own drawing changes — a new scene, a new palette; a turn scales the
finished image.
It covers the map and not the page, so the text stays vectors, and it is always
on rather than only while the pager scrolls. It is bounded by the canvas, which
cuts the scene's 1 % overshoot off at the face's square; that is invisible,
because `AnimatedPage` clips each page to the circle inscribed in it, inside its
scale.

Six builds were measured — the tree before the layer (B) and five alternatives
to it, D and D2 sharing a row — each gesture grouped by the largest vertex count
among the faces it crossed:

| Variant | Under 15k | 15k–30k | 30k and over | RenderThread a frame, 30k+ |
| --- | --- | --- | --- | --- |
| **B** — before the layer | 56–60 fps | drag 35–36, flick 41–45 | drag 20–21, flick 24–26; 28–36 % on time | 44–48 ms |
| **C** — coast thinned at 0.5 px, no layer | ~57–60 | drag 43, flick 44–47 | drag 28–29, flick 31–39 | 21–32 ms |
| **D, D2** — whole page in a layer, while scrolling / always | 60 | 60 | 60, 98–100 % on time | ~2 ms |
| **E** — map only in a layer, always (**shipped**) | 60 | 60 | 60 dragged and flicked, 98–100 % on time | ~4.7 ms |
| **Es** — map only, only while the pager scrolls | 60 | 60 | 60, 100 % on time; a flick's slowest frame 61 ms at p90, to E's 37 | ~5–5.5 ms |

C on top of D added nothing measurable.

**What it costs to look at**, from on-device screenshots of the same seeded
route and page, diffed against B by largest channel difference. At rest, E is
within 5/255 everywhere and more than 4 levels off on at most one pixel a face —
invisible; D and Es, which have no layer at rest, are identical to B. Mid-drag,
with a finger held 200 px in, D and D2 differ on 8,000–12,000 pixels by more than
4 levels and on 900–1,300 by more than 48: they resample the text, and its glyph
edges go visibly softer. E and Es differ on 3,100–5,800 by more than 4, 390–740
by more than 16 and only 14–80 by more than 48 — the text is drawn as vectors and
untouched, and what remains is a faint softening of the route line's edge and
the coast while the page is scaled. C differs on 650–2,100 by more than 4 and on
0–1 by more than 48.

**Why E.** Over D and D2: the same 60 fps, but they resample the text mid-turn,
and text is cheap to redraw — only the map needed caching. Over Es: the same
60 fps, but Es makes its layer at each swipe's start and drops it at the end.
Both draw the incoming page's map into a new layer when that page first
appears, since the pager never draws a page ahead; Es must also draw the map
already on screen into one again at every swipe's start — the slower p90 above
— and it needs the pager's state threaded down into the map. E keeps the
settled page's layer from rest, and pays at most 5/255 on one pixel a face at
rest for that. Over C: 28–39 fps on the zoomed-out faces on its own, and nothing
on top of D's layer. The standing rule is that image quality beats a marginal
frame, and that a change which resamples the map is offered with screenshots
rather than shipped silently. This one was offered: Daan swiped E, in the
measurement build, on the watch on 2026-11-02, found it smooth, and chose it.

**How it was measured.** Release builds (R8), AOT-compiled with `cmd package
compile -f -m speed`, on the SM-L350 — 60 Hz only, a 16.7 ms budget — on its
charger, at thermal status 0 throughout. Full `speed` compilation is not the
state an install from a store is in, and `:wear` has no baseline profile; what
the layer took away is RenderThread's, which is native code ART compilation does
not touch, but the UI thread's share was not measured uncompiled. A measurement
build of the tree seeded the route generator so that every variant swiped
through the same 31 pages, picked the variant at launch, and logged every page
settle with that face's unthinned vertex count. Each run was a cold start and then 30 slow drags
(`input swipe` over 700 ms) or 30 flicks (over 120 ms), with framestats dumped
after every gesture and joined to the settle log; fps is from display-present
intervals. Two rounds per variant in alternating order, and a third for the
map-only variants (a fourth was stopped after its first run, which agreed).
That measurement build is not in the repo — it was a throwaway — and only its
numbers are kept here. The tree's own release build, with the layer, was then
measured the same way on the watch, on random routes since it has no seed and
no logs: 30 drags and 30 flicks, all at 60 fps, 99–100 % of frames on time,
~4–5 ms of RenderThread a frame.

**The phone's baseline profile, regenerated.** The phone draws no differently,
but its hottest map methods moved or changed signature — `toPath` and
`arrowPath` from `RouteMapKt` in `:core:designsystem` to `RouteMapGeometryKt` in
`:core:mapscene`, the clipper's `clipPolygonInto`, `clipPolylineInto` and
`clipSegment` into `OutlineClipper`, `lonShiftsFor` into `lonShiftsInto` — and
the profile still named the old ones, so the loops behind every route card's
first draw were no longer compiled ahead of time on install. It was regenerated
on the SM-S942B on 2026-11-02 with `./gradlew :app:generateBaselineProfile`:
every one of those methods is now named under its new home (77 `:core:mapscene`
rules, none before), and the startup profile grew from 22,467 to 31,149 rules,
since it also caught up with everything added since it was last generated.
`StartupBenchmark` (`startupBaselineProfile`) then read a 152 ms median
`timeToInitialDisplayMs` — see CLAUDE.md for that figure and its caveats.

**Not taken.** Two other levers were on the list. Neither is needed now that a
turn holds 60 fps, and each would change how the face looks or moves:

- **Coastline thinning — measured, as C above.** Thinning the coast at 0.5 px
  took a turn across a 30,000-plus face from 20–26 fps to 28–39, and RenderThread
  from 44–48 ms a frame to 21–32: real, and well short of 60 on its own. On top of
  D's whole-page layer it added nothing measurable; it was not tried on top of
  E's, which holds the same coast. A coarser outline, or a level of detail
  per frame span, has no frame left to win.
- **A stiffer snap spring.** A shorter turn is fewer doubly-drawn frames, and
  with the layer those frames are already on time. The curve stays Wear's own,
  and `:wear` still has no motion tokens to put a new one in.

## Not yet done

- **The release build has been measured for swiping, and for little else.**
  `:wear:assembleRelease` passes — R8 and resource shrinking both run — and the
  swipe figures in *Swipe performance* come from release builds, measured per
  zoom across six builds of the face; Daan swiped variant E of the measurement
  build — the layer this tree ships — on the watch on 2026-11-02 and found it
  smooth, and the tree's own release build then measured 60 fps on every drag
  and flick. Everything else about the app has
  still been watched mostly as a debug build, which is largely interpreted, and
  per CLAUDE.md a debug timing is not evidence either way.
- **The handoff was verified one way only.** A tap on the watch opened the right
  route on the phone, codes, distance, ETE and airframe all matching. What has
  still never been watched is the *theme* crossing — though the phone's light
  theme did reach the face during the session, which is the same channel, so
  this is now closer to unverified-in-detail than untested.
- **A tile and a complication.** "Today's challenge" already exists as
  `DailyChallenge` in `:core:routing` and feeds the phone's two widgets; a tile
  is the single best-fit next feature and needs no new domain code.
- **Screenshot goldens.** `:app` has 161 under Roborazzi; `:wear` has none.
- **A `checkInvariants` rule for the watch's Material surface.** The existing
  rules are written against `androidx.compose.material3` *Expressive* imports,
  which is the right test for `:app` and the wrong one for a module that must
  import none of that library at all. Adding the rule needs a planted violation
  to verify it fires — the machine that ran this session can build, so the
  blocker named here is gone and only the work is left.
- **The five Wear versions in `gradle/libs.versions.toml` are unverified.** They
  are the only entries in that file not resolved from live Maven metadata — the
  session that added them had no route to Google Maven. CI resolved all five, so
  they exist, and this session resolved `compose-foundation` and
  `compose-material3` at 1.5.0 from the local Gradle cache while reading their
  APIs — but *current* is still unchecked, and the blocker named here is gone.
- **Flipping a theme on the phone has not been watched reaching the face.** The
  watch did follow the phone into light, so the channel works; what has not been
  observed is a *change* propagating, or how long it takes.
- **The feed's behaviour at the end of a batch is unexamined.** The pager tops up
  on `settledPage`, but nothing has driven it far enough to see a top-up happen,
  and a rotary flick covers pages much faster than a swipe did.
