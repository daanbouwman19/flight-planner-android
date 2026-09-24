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
| The endless batch feed | `wear/…/route/WatchRouteFeed.kt` |
| Assets — index, seed fleet, coastline | `wear/…/data/WearAirportData.kt` |
| The tap to the phone | `wear/…/handoff/PhoneHandoff.kt` |
| The link both APKs compile against | `:core:handoff`, `WatchRouteLink` |
| The theme both APKs agree on | `:core:handoff`, `WatchThemeSync` |
| The phone publishing its theme | `:app`, `PublishThemeToWatch` |
| The watch following it | `wear/…/theme/` |
| The phone's end of it | `:app`'s manifest, `LaunchIntents`, `LaunchRequest.OpenWatchRoute` |
| The "Today's challenge" tile | `wear/…/tile/` — see *The tile* |

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
`OUTLINE_MARGIN`'s 8 % already does, so it is now 1.0. The padding that makes a
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

## The tile

"Today's challenge" in the tile carousel: the day's route in words — codes,
distance and time, airframe — and a tap that opens the app with that route as
its first page. Added 2026-09-24 as the next feature this document named.

**It is the phone widget's route, computed on the watch.** `ChallengeTileService`
asks `WatchChallengeSource`, which calls `WatchRouteFeed.challengeFor` — the same
`dailyChallenge` in `:core:routing` that `:app`'s `ChallengeWidget` draws from,
seeded by the date alone. The face opened from the tap calls the same function,
so the tile and the page it opens on cannot disagree. What they need *not* agree
with is the phone: the watch's fleet is the seed CSV and its `icaoOnly` is fixed
on, so the two show the same route only while the phone's fleet is still the seed
and its ICAO-only setting is on. Making them always agree means reading the
phone's fleet over the Data Layer — the same work mark-as-flown needs.

Decisions, each cheap to revisit:

- **Words, not the map.** A tile cannot draw a path, so the face's map would be a
  bitmap resource regenerated daily, for a picture one tap away. The layout is a
  centred stack inset 14 % each side, which keeps every line near the middle of
  the circle — the lesson of *What the device found* above.
- **The phone's theme, read per request.** The tile takes its colours from the
  face's own `schemeFor`, so Cockpit and Chart cross over too. It reads the
  replicated `DataItem` when the system asks for the tile rather than listening
  for changes, because nothing on the watch is alive to listen; a tile already
  on screen keeps its colours until it is next requested.
- **Fresh until local midnight**, and memoised per date in a `@Singleton`, since
  the system asks for a tile every time it is scrolled to and each answer would
  otherwise decode the index again. A failed read is not memoised.
- **No preview image yet.** `androidx.wear.tiles.PREVIEW` wants a picture of the
  real tile, which wants taking off a watch; the picker shows the app icon until
  then.
- **No complication.** A complication is a different service with a different
  budget — a few characters of text — and nothing about the tile needed it.

**Built without a compiler.** The session that wrote it could reach neither
Google Maven nor an Android SDK, so the three new versions in
`libs.versions.toml` are conservative known releases, and the Tiles and
ProtoLayout calls are written from their documented API rather than checked
against a compile, as CLAUDE.md asks. CI's `check` is the first thing to have
compiled them. Nothing about it has been seen on a watch.

## Not yet done

- **The tile has never been on a watch.** Everything *The tile* describes is
  unverified on a device: that it appears in the carousel, how its text sits in
  the circle (at font scale 1.0 and at the largest), and whether the tap lands on
  the challenge when the app is already open in the background — the feed reads
  the tap's extra only when its ViewModel is first created, so a tap that brings
  an existing activity forward rather than starting one would land on whatever
  page was showing. Its preview image is also still owed.
- **A duplicate route in the feed crashes the pager.** Pre-existing, found while
  adding the tile: the pager is keyed by `WatchRouteCard.key()`, and
  `RouteGenerator` does not de-duplicate within or across batches, so the same
  departure, destination and airframe type drawn twice gives two pages the same
  key — which Compose's lazy layouts reject with an exception. Against 24,000
  airports it is rare rather than impossible; the unit tests' five-airport world
  draws duplicates in nearly every batch. The tile's first page drops a batch
  route that repeats the challenge, and nothing else is filtered. The fix is to
  filter each appended batch against the keys already loaded, and to change the
  size assertions in `WatchRouteFeedViewModelTest` to match, since they currently
  expect every batch to arrive whole.

- **The release build has not been looked at.** `:wear:assembleRelease` passes —
  R8 and resource shrinking both run — but the app has only ever been *watched*
  as a debug build, which is largely interpreted. Whether the page turn is
  smoother minified is an open question, and per CLAUDE.md a debug timing is not
  evidence either way.
- **The handoff was verified one way only.** A tap on the watch opened the right
  route on the phone, codes, distance, ETE and airframe all matching. What has
  still never been watched is the *theme* crossing — though the phone's light
  theme did reach the face during the session, which is the same channel, so
  this is now closer to unverified-in-detail than untested.
- **A complication.** The tile shipped (see *The tile*); a complication showing
  the day's pair of codes is the obvious companion and would reuse
  `WatchChallengeSource` unchanged.
- **Screenshot goldens.** `:app` has 161 under Roborazzi; `:wear` has none.
- **A `checkInvariants` rule for the watch's Material surface.** The existing
  rules are written against `androidx.compose.material3` *Expressive* imports,
  which is the right test for `:app` and the wrong one for a module that must
  import none of that library at all. Adding the rule needs a planted violation
  to verify it fires — the machine that ran this session can build, so the
  blocker named here is gone and only the work is left.
- **Flipping a theme on the phone has not been watched reaching the face.** The
  watch did follow the phone into light, so the channel works; what has not been
  observed is a *change* propagating, or how long it takes.
- **The feed's behaviour at the end of a batch is unexamined.** The pager tops up
  on `settledPage`, but nothing has driven it far enough to see a top-up happen,
  and a rotary flick covers pages much faster than a swipe did.
