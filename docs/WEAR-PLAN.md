# Wear OS — the watch app

The Galaxy Watch 9 build of Flight Planner: one route filling the round face, a
swipe up for the next, a tap to open that route in the phone app.

**Status: first pass built, nothing verified on a device.** What follows is what
shipped, what was decided and why, and what is deliberately not here yet.

## The target

One device: Daan's Galaxy Watch 9, and nothing else for now. That settles more
than it sounds like it does.

- **Wear OS 6 is API 36**, comfortably above the repo's `minSdk 35`, so `:wear`
  keeps the single floor every other module has and the no-`SDK_INT` invariant
  is untouched. If the target ever widens to Wear OS 5 hardware (API 34) this
  comes back: the floor is one version-catalog entry applied by the convention
  plugins, so lowering it is either global or a per-module exception.
- **One round screen.** The design was drawn against 454 × 454, and the face
  places its text in fractions of the face rather than in dp for that reason —
  see `RouteFace`'s KDoc. The exact display size has not been read off the
  device (`adb shell wm size`); doing that is the first thing worth checking
  when the APK is installed.

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

## Not yet done

- **Nothing has run on a device.** The APK has not been installed, and per
  CLAUDE.md a UI change is not verified until a screenshot of it has been looked
  at. Everything below follows from that.
- **Rotary input.** The bezel and crown do not drive the pager yet; only touch
  does. Wear Compose's rotary modifiers are the answer.
- **A tile and a complication.** "Today's challenge" already exists as
  `DailyChallenge` in `:core:routing` and feeds the phone's two widgets; a tile
  is the single best-fit next feature and needs no new domain code.
- **Screenshot goldens.** `:app` has 161 under Roborazzi; `:wear` has none.
- **A `checkInvariants` rule for the watch's Material surface.** The existing
  rules are written against `androidx.compose.material3` *Expressive* imports,
  which is the right test for `:app` and the wrong one for a module that must
  import none of that library at all. Adding the rule needs a planted violation
  to verify it fires, which needs a machine that can build.
- **The five Wear versions in `gradle/libs.versions.toml` are unverified.** They
  are the only entries in that file not resolved from live Maven metadata — the
  session that added them had no route to Google Maven. CI resolved all five, so
  they exist; re-resolve them against Google Maven from a machine that can reach
  it to find out whether they are *current*.
- **The theme sync has never been watched crossing.** CI cannot pair two
  devices. What a build proves is that it compiles; what it does not prove is
  that flipping Cockpit on the phone recolours the face, or how long it takes.
