    # Phase F′ — Weather, redesigned around a Sky Profile

**Resumable plan.** Phase F shipped a working weather pipeline and a small animated
glyph; the data layer was rebuilt and the visual layer replaced wholesale. **All
three stages are done**, and a fourth pass has since taken the scene and the readout
through a Claude Design redesign — see [Stage 4](#stage-4--the-claude-design-pass).

| | |
| --- | --- |
| **Status** | **Stages 1–4 shipped.** What is left is the on-device sweep in [Still owed on device](#still-owed-on-device) |
| **Build** | `./gradlew build checkInvariants` green |
| **Committed?** | **No.** Everything is uncommitted on `main` (HEAD `bfa1902 feat: E5`) |
| **Entry points** | [`SkyProfile.kt`](../core/designsystem/src/main/kotlin/com/github/daanbouwman/flightplanner/core/designsystem/components/SkyProfile.kt), [`SkyProfileGeometry.kt`](../core/designsystem/src/main/kotlin/com/github/daanbouwman/flightplanner/core/designsystem/components/SkyProfileGeometry.kt), [`SkyColors.kt`](../core/designsystem/src/main/kotlin/com/github/daanbouwman/flightplanner/core/designsystem/theme/SkyColors.kt), [`Celestial.kt`](../core/routing/src/main/kotlin/com/github/daanbouwman/flightplanner/routing/Celestial.kt), [`Windsock.kt`](../core/designsystem/src/main/kotlin/com/github/daanbouwman/flightplanner/core/designsystem/components/Windsock.kt), [`SurfaceWind.kt`](../core/routing/src/main/kotlin/com/github/daanbouwman/flightplanner/routing/SurfaceWind.kt) |

**If you are picking this up cold, read [Known defects](#known-defects) first.** One
of them contains a diagnosis that a fresh reader would very likely get backwards.

## Stage 3, as it actually landed

The plan above describes what was intended. Six things came out differently, and
each is recorded where it belongs — this is the index.

1. **Defect 2 was resolved as a veto, not a blanket `UNKNOWN`.** An unreported sky
   takes the category away from the provider and hands it to `FlightRules.derive`,
   which against `Ceiling.Unknown` can only answer LIFR or UNKNOWN. So the chip can
   say *nothing* or say *the weather is bad*, and never that it is fine over a sky
   nobody measured — while a fogged field reporting `1/4SM` keeps its LIFR. Forcing
   `UNKNOWN` would have traded a false reassurance for a suppressed warning.
2. **The lunar series carries all 120 coefficients, not the abridged 45.** The
   argument is checkability rather than accuracy: with the full tables Meeus's own
   Example 47.a is asserted *exactly*, so one mis-keyed digit fails; abridged, the
   same example can only be checked to a tolerance wide enough to hide one.
3. **`convective` moved into `SkyBand`** — the polarity trap for the third time,
   this time measured at 2.66:1 and provably unfixable with a single value.
4. **The celestial bodies are drawn *over* the decks, with alpha carrying the
   occlusion.** Painting them underneath was tried first and buried KDEN's
   afternoon sun completely; it also let a cirrus hide the sun while an overcast
   let it through, which is the drawing lying.
5. **The deck drift became directional** and the moon's lit limb flips south of the
   equator. Both were put to the repository owner and both were taken.
6. **The band blend refuses to interpolate across a polarity reversal**, and drives
   that one crossing from a spring instead. See `blendBands` for the proof that no
   colour can serve the middle of it.

## Why the visual layer was replaced

**1. It was wrong, not just plain.** An IFR field rendered with a sun icon.
`WeatherCondition.derive()` ended `else -> when (metar.ceilingFt) { null -> CLEAR … }`,
so **a null ceiling rendered as good weather** — and `ceilingFt` was null far more
often than "the sky is clear":

- **`CEILING_LAYERS = setOf("BKN","OVC")` omitted `OVX`**, NOAA's normalisation of
  vertical-visibility obscuration (`VV002`). A fog-obscured field at 200 ft came back
  as `clouds:[{"cover":"OVX","base":200}]` and yielded *no ceiling at all*.
- **AVWX decoded no cloud data whatsoever** (raw, flight_rules, station, time), so
  *every* AVWX report reached the null branch.
- **A cache row written before the v2 columns existed** had no decoded ceiling.
- The present-weather code lists missed `SHSN`, `GR`, `FU`, `DU`, `BLSA`, `SQ`, `VA`,
  `PO`, so those fell past the phenomena checks into the same branch.

**And the app could not state the opposite either.** NOAA's top-level `cover` field
is the only affirmative "clear" signal in the payload, and the DTO discarded it. So
the app never knew a sky *was* clear — only that it failed to find a BKN or OVC
layer. Absence of evidence was rendered as evidence of sunshine.

*Verified against live NOAA on 2026-08-27: `cover` is always present, and for an
empty `clouds` array it was always `CLR`, `SKC` or `CAVOK`. So the fix reads `cover`;
it must **not** add a defensive branch for "cover missing", which would be exactly
the untestable dead code `CLAUDE.md` forbids.*

**2. The design was redone, then twice more on look-and-feel.** The confirmed
decisions are below; the second and third rounds are the reason the palette is
low-chroma and the clouds are shaped.

## Confirmed decisions

| Question | Decision |
| --- | --- |
| Scene structure | **Sky Profile** — a vertical atmospheric cross-section, altitude as the Y axis, not a naturalistic sky |
| Ground | A derived ground surface *in the scene*: wet, snow, frost, dry, with fog lying on it |
| Windsock | **Draggable** — grab and swing it, resistance from wind strength, springs back to the true direction |
| Scale | **Hero** — ~168 dp on route detail (two, one per end), ~220 dp on airport detail |
| Route-card glyph | **Removed entirely** — the VFR/MVFR/IFR/LIFR chip alone carries category at list scale |
| Sun & moon | **Real geometry** — true solar position, real moon phase, both drawn when both are up |
| Delivery | **Data first, then a look** — an explicit stop-and-look gate after the static scene |
| Palette | **Low chroma, hues from each scheme's own surface family.** The scene sits inside the app's palette, not beside it |
| Cloud form | **Flat base, lobed top**, the lumpiness *being* the density — puffs for FEW, a textured slab for OVC |
| Motion | **Wind-driven only.** Decks drift at a rate from the reported speed, faster higher up; the sock flutters. Off entirely under reduce-motion |
| Crosswind | **On the runway map**, not the sky scene: a plan-view sock, the favoured end highlighted, `SurfaceWind` doing the trigonometry |
| Wind arrow | **Removed.** The sock carries direction; an arrow across the diagram was clutter over the runways it was to be compared against |
| Runway idents | Pushed clear of the ray tips, and the **preferred end's ident bold in the sock's orange** |

## The concept, and the one real risk

The scene is **not a picture of the sky**. It is a vertical cut through the
atmosphere at this field, read like an approach plate's profile view: altitude on the
Y axis, every deck at its true base, the flight-rules thresholds as hairlines, the
ground at the bottom.

**The risk:** this abandons the literal reading of "sun, moon, clouds, ground" as a
scenic picture. Justified because:

- Layer *altitude* is the whole point of "different layers", and a naturalistic sky
  cannot express it — decks only stack by painting order, so 700 ft and 25,000 ft
  look identical. On an altitude axis they cannot.
- It is native to the subject's own artifacts (profile view, station model, skew-T),
  not borrowed consumer-weather iconography.
- **It structurally prevents the bug that prompted the redesign.** You cannot draw a
  700 ft overcast deck below the IFR hairline and have it look like a nice day.

**Verified on device: the concept holds.** KHPN's `BKN013 OVC019` renders as a solid
overcast slab sitting on the 1,000 ft hairline with the red IFR wedge pointing at it.
There is no arrangement of that drawing that reads as fine weather.

### The axis is the signature element

[`AxisBreakpoints`] is piecewise-linear with its breakpoints **at the flight-rules
thresholds** — 0 → 0.00, 500 → 0.20, 1,000 → 0.36, 3,000 → 0.56, 12,000 → 0.78,
45,000 → 1.00. A linear 0–45,000 ft axis would put every altitude that decides a
category inside the bottom 7 % of the frame, so a 900 ft ceiling and a 2,900 ft
ceiling would be two hairlines 4 dp apart. The ruler is weighted by what matters to a
pilot rather than by physical distance. The cost is a compressed upper air, which is
the right trade: nobody reads a cirrus base to two significant figures, everybody
reads a ceiling.

Two constraints follow:

- **The axis numerals go before the hairlines do.** Above a threshold width fraction
  the numerals drop and the coloured hairlines stay, because the hairlines are what
  encode the bands. Now *measured* rather than guessed from the font scale — a
  translated label or a wide face costs width the scale factor alone does not predict.
- **The diagram does not mirror in RTL.** A cross-section's left–right axis is
  physical, not reading-order, the same as `RunwayDiagram`'s compass. Only the
  numerals get `asChartFigure()`.

## Architecture

```
:core:model        DONE  Sky.kt (CloudCover/CloudLayer/SkyCover/Ceiling),
                         PresentWeather.kt (full WMO 4678), GroundCondition.kt,
                         MetarParser.kt, MetarSupplement.kt
                   DONE  WeatherCondition deleted — the 7-value enum and its null→CLEAR
:core:routing      DONE  SurfaceWind.kt — headwind/crosswind, favoured end, sock lift
                   TODO  SolarPosition + LunarPhase → CelestialState
:core:network      DONE  NoaaMetarDto reads supplement only; AVWX san→station; dupe guard
:core:database     DONE  v3 schema: raw + MetarSupplement, everything re-derived on read
:core:designsystem DONE  SkyColors (4 authored palettes), SkyProfileGeometry, SkyProfile,
                         Windsock.kt + DiagramWind, RunwayDiagram wind rendering
                   DONE  AnimatedWeatherGlyph deleted
:app               DONE  MetarPanel Column with the scene on top; WeatherReservedHeight
                         deleted; visibilityFigure; wind passed to RunwayDiagram
```

### Why `SkyColors` and not Material roles

The old glyph used `warmColor = MaterialTheme.colorScheme.tertiary` for the sun. In
**Cockpit `tertiary` is blue** (`#82CFFF`) and `primary` is amber — inverted from
Brand — so that one line drew a **blue sun** on the night-flying theme. Chart's own
KDoc forbids amber outright: *"No amber anywhere: that accent belongs to the
runway."* No role mapping survives all four schemes.

So `SkyColors` mirrors the proven `FlightRulesColors` pattern — `@Immutable data
class` + `staticCompositionLocalOf` + provided inside `FlightPlannerTheme` — with
**four explicitly authored palettes**, switched on `ThemeChoice` rather than on
`dark`.

This diverges from `FlightRulesColors` in one deliberate way: **flight-rules colours
are safety data and must never be re-themed** (which is why Cockpit shares the dark
set). **The sky is scenery**, and Cockpit exists to protect a pilot's dark
adaptation, so a bright day gradient there would be an actual defect. Scenery gets a
per-theme palette; safety signals do not.

Flight-rules colours therefore appear in the scene **only in the measuring
apparatus** — the threshold hairlines and the ceiling wedge — never in a sky, cloud
or ground fill.

### Why the palette was authored twice

The first version was naturalistic: `#D6E9F9 → #8FBAE0` day sky over `#6E6350`
earth. On device it read as a children's weather illustration against an app of cool
near-white surfaces and chart figures. The rework:

- **Hues from each scheme's own surface family**, at much lower chroma. Brand light's
  horizon starts a shade off `surfaceContainer` and deepens into the `outline` family.
- **Ground is slate, not soil** — a section fill through the earth, the way a profile
  view draws it.
- **Cloud bodies are grey**, because from a field you look at cloud *undersides*.
  White-cloud-on-blue is the view from outside the deck.
- **A 2 % warm lift at the horizon.** The single change that took the scene off flat;
  the gradient alone read as a grey wash. Physically motivated — the longer path
  through thick air is why a low sun reddens. Night gets none.
- **Solid hairlines with one opaque tick at the head**, not dashes across the frame.
  The dashes competed with the decks and read like a spreadsheet border.
- **A 3 dp shade under each deck base.** Clouds shade the air beneath them, and this
  is what stops the decks reading as stickers on a gradient.

### Constraints discovered while authoring, that shaped the types

- **The cloud edge had to move inside `SkyBand`.** A deck's underside is a hairline
  needing 3:1 against the air behind it, and **no single ink clears that against both
  a bright day sky and a near-black night one**. So a day deck gets a dark underside
  and a night deck a pale one, paired with their own band so a call site cannot
  combine them. This bit twice before the structure changed.
- **Twilight is the hard band in every palette**, because its air is neither bright
  nor dark. Brand light solves it by lifting `high`; Brand dark by narrowing the
  band. That the two solve it differently is exactly why the palettes are authored
  rather than derived.
- **Cockpit separates time of day by hue inside a narrow luminance window**, because
  the luminance ceiling leaves no room for brightness. Its band tops all converge,
  which is honest — thin air is dark at any hour.

## Work breakdown

### W1 · `:core:model` — types that cannot lie · **DONE**

- `SkyCover` is a sealed hierarchy with **`Clear` and `Unknown` as different
  values** (plus `Layers`, `Obscured`); `Ceiling` likewise distinguishes `Unlimited`
  from `Unknown`. This is the fix for the headline bug.
- `FlightRules.derive` **refuses to claim VFR from one known-good side**: with a
  ceiling or a visibility missing, only an unambiguously-bad reading returns a
  category, otherwise `UNKNOWN`.
- `MetarParser` — a real tokeniser over `raw`, the only lossless field. Stops at
  `RMK` **and at trend groups** (`TEMPO`/`BECMG`/`FMnnnn`), which was a real bug
  found in review: `TEMPO 3000 SHRA` is a forecast and would have been read as
  current conditions. The `$` maintenance flag is read off the whole raw string
  *before* truncation, because it sits after `RMK`.
- `GroundCondition` derived by a pure function, with the measured/inferred boundary
  documented — "frosted" is inferred, "snow depth 3 in" is measured.
- `MetarSupplement` — `raw` + supplement reconstructs a `Metar` exactly, with an
  asserted idempotence property.
- `WeatherCondition` **deleted**.

### W2 · celestial math · **DONE**

Pure JVM, `Double` throughout (deliberately *not* `GreatCircle`'s `Float`, whose
convention is about a sampling loop that dominates route generation). Solar
declination / equation of time / hour angle → elevation + azimuth from (lat, lon,
instant); twilight bands from elevation; moon phase and position via Meeus ch. 47 —
an "elongation shortcut" was measured at **10.4° error** and rejected.

**Four files, and the abridgement was rejected.** `Celestial.kt` is the only public
one; `CelestialFrames.kt` exists so both bodies reach the horizon through *one*
route — the Sun could have used the equation of time instead, and then a sun and a
moon in the same frame would have been two conventions disagreeing about where
south is. `LunarPosition.kt` carries **all 120 coefficients of tables 47.A and
47.B** rather than the planned 45: the abridgement is accurate enough (0.027°, a
fifth of a pixel) but it is not *checkable* enough, because the realistic defect is
one mis-keyed digit and only the full tables let Meeus's Example 47.a be asserted
exactly. A tolerance wide enough to absorb the truncation is wide enough to hide a
typo.

No refraction (the twilight thresholds are *defined* on geometric elevation, so
refracting first would make the app disagree with the definition it quotes) and no
ΔT (measured at 0.0122° for the Moon). Topocentric parallax **is** applied to both
bodies through one function: worth 1.01° for the Moon and 0.0024° for the Sun, and
applying it to both means `elevationDeg` means one thing.

**Polar cases are first-class**: the airport set includes `PABR` (71.3°N), `BGSF`,
`PAEG`, where the sun genuinely does not rise or set on some dates. Elevation is
always computable; only crossing times can be absent, which is an argument for not
needing crossing times at all.

`SkyProfile` must **not** take a clock — `SkyPhase` is already a parameter for
exactly this reason, and `CelestialState` will be too. A composable that reads
`System.currentTimeMillis()` cannot be previewed at dusk and recomposes on a
schedule nobody asked for.

Delivered with it: `SolarPositionTest` (12) against the NOAA calculator, closed-form
equinox and solstice checks, the equation of time reconstructed *inside the test*
because nothing draws it, and PABR / BGSF / PAEG; `LunarPositionTest` (7) against
Meeus's Examples 47.a and 48.a and the published full moon of 3 January 2026.

### W3 · `:core:network` — stop discarding data · **DONE**

Reads **only** supplement fields and deliberately ignores `cover`, `clouds[]`,
`wxString`, `wdir`, `wspd`, `wgst`, `visib` — all lossier decodes of the same
`rawOb`. **One decoder, not two**, because two decoders for one string is what
produced the defect. The robustness a fallback would have bought is bought instead by
`NoaaCloudsCrossCheckTest`, which compares our parse against NOAA's own on 7 real
stations and asserts the CB divergence *as* a divergence — at test time, where a
divergence is information about which strings the parser gets wrong rather than a
silent difference between two code paths.

Unit traps encoded in the types: NOAA `elev` is **metres**; NOAA `vertVis` is
**hundreds of feet** while `clouds[].base` is feet; AVWX `Cloud.base` is **hundreds
of feet**. AVWX's `san` → `station` (a dead field — `san` never existed).

### W4 · `:core:database` — carry it through without loss · **DONE**

Rather than columns for cloud layers, the cache stores `raw` + `MetarSupplement` and
re-derives everything on read through the same `buildMetar` a live fetch uses.
**There is nothing to forget.** `MetarCacheEntity` has **no Kotlin default values**,
which is the compile-time completeness guard — it caught a missing field during the
rewrite. Room 2.8.4 combined delete+add auto-migration verified by reading the
generated SQL.

### W5 · `:core:designsystem` — `SkyColors` + `SkyProfile` · **DONE**

Canvas-drawn, following `RouteMap`/`RunwayDiagram`: colours as parameters with
`MaterialTheme` defaults, all `dp.toPx()` and path construction in the
`drawWithCache` build phase, `clipRect` inside the draw scope rather than
`Modifier.clipToBounds()`, text via `rememberTextMeasurer`, and **all testable
geometry as `internal` top-level pure functions** because the module has JVM tests
only.

Shipped: the altitude axis and its threshold ticks, the ceiling wedge, cloud decks
with real silhouettes, deck merging and separation, the ground surface, the fog band,
the hatched unknown states, and **wind-driven deck drift**.

Also shipped in Stage 3: the **horizon rail** (why it is not the altitude axis, and
why `HorizonRailTest` asserts that rather than assuming it), the **band crossfade**
and its polarity refusal, **precipitation**, the **unlimited-ceiling wedge**, and
the **sock's drag** — the app's first `pointerInput`, on
`detectHorizontalDragGestures` so the three `verticalScroll` hosts keep their axis,
verified on a device by scrolling out of the diagram after swinging the sock.

#### Deck merging, and the one correctness rule

A METAR can carry more layers than a 200 dp frame has room for. `mergeDecks`
collapses layers that would overlap on the axis, and **two layers merge only when
they agree about whether they are a ceiling**. That restriction is the whole
correctness argument: merging a FEW into a nearby BKN would move the ceiling —
downward, in the safe direction, but still to an altitude the station never reported.
`SkyProfileGeometryTest` asserts the merged deck list has exactly the same ceiling as
the layer list it came from, over every fixture plus two synthetic worst cases built
to tempt a cross-boundary merge.

### W6 · `:app` — restructure and remove · **DONE**

Done: `MetarPanel` is a `Column` with the scene edge-to-edge on top;
`WeatherReservedHeight` deleted; `skyLine` says the sky in words including a literal
"Sky not reported"; `visibilityFigure` fixes a raw `6.213090551181102 SM` on screen;
the wind is passed to `RunwayDiagram`.

**Correction — nothing on the Plan screen was deleted.** An earlier draft said to
remove `FlightRulesSlot` and the `weatherByStation` chain from `RouteCard.kt`. That
was wrong: **there is no weather glyph on the route card.** `FlightRulesSlot` renders
the chip the confirmed decision keeps, and `weatherByStation` is the only thing
feeding it. `RouteCard.kt`, `PlanScreen.kt`, `PlanViewModel.kt` and `PlanUiState.kt`
are all **unchanged**.

**Done in Stage 3 — the two figures now have converters**, plus the observation age:

- **Visibility.** `visibilityText` honours `LocalUnitSystem`: statute miles as the
  *fractions* a US station actually sends (`1/2`, `2 1/2`), metres below 5 km and
  kilometres above. Above three miles a whole number prints only when the value
  genuinely is one — caught on a device against a live `VVPQ … 9000`, where rounding
  to the nearest mile printed 5.59 as `6 SM` and would have printed a 5.4 SM report
  as `5 SM`, the MVFR/VFR boundary. A VFR field reading as marginal is the same
  class of defect as an IFR field drawing a sun, only smaller.
- **Altimeter.** `altimeterText` shows the station's own convention — inHg where it
  said `A`, hPa where it said `Q` — and is the one figure in the app that
  deliberately ignores `LocalUnitSystem`. An altimeter setting is not a quantity to
  compare, it is a number dialled into a subscale, and converting it is a conversion
  the pilot has to undo plus an opportunity to mis-set it that the report never
  offered.
- **The observation age.** `ObservationAges` bands a report current / ageing /
  stale, and `MetarPanel` prints the `ddhhmmZ` group beside the elapsed time. See
  defect 3.

Temperature stays °C — `UnitSystem` has no Fahrenheit option and adding one is out of
scope.

### W7 · docs · **DONE**

**`docs/UI-PLAN.md` §2 — the exception gets *narrower*, not wider.** *(Already done
before Stage 3 began — §2 and the Phase F table both read correctly now.)* It used
to license "playful and interactive — sun, drifting cloud, falling rain/snow,
drifting fog" as a bounded exception to "the app is an instrument, not a toy". That
wording was written for the naturalistic glyph and is now wrong in an interesting
direction: **a measured cross-section with a real altitude axis is not an exception
to the instrument principle, it is an expression of it.**

The rewrite shrinks the carve-out to the two things that genuinely remain playful —
the sock's drag and the ambient drift of the decks — and moves everything else back
under the main principle. **The design got more rigorous, so the doc should claim
less licence.** Also retitle Phase F's table to note the redesign and record why (the
`null -> CLEAR` bug), since "F is complete" is written there and is no longer true.

**`docs/DESIGN-SYSTEM.md`** gained two new sections — *Scenery colours* and *The sky
profile* — carrying `SkyBand`, `SkyColors`, `SkyPhase`, `SkyProfile` and the rules
that are easy to break silently: that anything drawn as a mark against the air
belongs to the band, that blending two bands is not always safe, and that the rail
is clear of the ruler by construction. `RunwayDiagram`'s entry was rewritten (it
still described the compass-only version, with no `positionedRays`, no `wind` and no
`DiagramWind`), the ident-at-the-threshold rule added with the report that found it,
and the pre-existing staleness fixed: `ThemeChoice` now lists five values, and
`StatSummaryStrip`, `MonthHeader` and `FlightDatePickerDialog` are in the catalogue.

### W8 · crosswind and the sock on the runway map · **DONE** *(added mid-flight)*

**Why here rather than in the sky scene.** A wind direction in degrees has to be
compared against a runway heading, and the comparison is the whole question. Drawn in
the same frame as the runways it stops being arithmetic: the sock points down the
strip or across it.

`SurfaceWind.kt` in `:core:routing` — pure trigonometry, 14 tests against the values
printed on every crosswind chart (0.50 / 0.71 / 0.87 at 30° / 45° / 60°):

- `components(runwayHeadingDeg, windFromDeg, windSpeedKt)` → headwind (signed, the
  chart convention), crosswind (magnitude) and which side it comes from. Split that
  way because the number goes against a demonstrated limit and the side tells the
  pilot which way to hold aileron; one signed value makes the limit comparison an
  `abs()` the caller has to remember.
- `favouredEnd(...)` — **most headwind wins, crosswind breaks a tie only.** A
  tailwind lengthens every roll; a crosswind within limits is a technique problem.
  Returns `null` below 3 kt rather than inventing a recommendation.
- `sockLift(windSpeedKt)` — 0 below 3 kt, 1 at 15 kt, which is what a standard sock
  is built to indicate.

**Directions are the aviation ones and they are not the same as each other.** A wind
direction is where the wind blows *from*; a runway heading is where an aircraft on it
points *toward*. Getting these backwards produces an answer that is exactly wrong
rather than obviously wrong, which is why every function names its argument.

`Windsock.kt` — `DiagramWind` (its own type, because *calm* / *variable* / *steady*
are not expressible as two nullables without the caller remembering which combination
means what), plus the cone and stripe paths. **Lift is length, not droop**, because
the diagram is a plan view — see [Known defects](#known-defects) for the bug that
taught this.

`RunwayDiagram` gains an optional `wind`: the sock, a halo under the favoured strip,
idents pushed clear of the ray tips, and the **preferred end's ident bold in the
sock's orange**.

## Build order

**Stage 1 — the truth (W1, W3, W4).** **DONE.** All pure-JVM or data-layer.
**The reported bug was fixed at the end of this stage**, with the old glyph still on
screen and now saying "unknown" instead of drawing a sun.

**Stage 2 — the scene (W5, part of W6), plus W8.** **DONE.** `SkyColors` across all
four schemes, `SkyProfile`, `MetarPanel` restructured, then three rounds of styling
on device, then the runway map's sock and crosswind.

> **The stop-and-look gate did its job.** Every defect in the list below was found by
> installing it and looking, and none was visible in a green build, a passing test or
> a preview. It is the reason this stage cost what it did and the reason Stage 3 is
> now cheap.

**Stage 3 — celestial and instrument (W2, W5 remainder, W6 remainder, W7).**
**DONE.** The celestial math with 19 tests against Meeus and the NOAA calculator,
the horizon rail, the band crossfade, precipitation, the unlimited-ceiling wedge,
the draggable sock, unit-aware visibility and altimeter, the observation age, and
all three known defects. The stop-and-look gate earned its keep again: four
defects in this stage were found only by installing it and looking, and none was
visible in a green build.

---

## Known defects

**All three of the outstanding ones are now fixed**, and each is left in place
below with its diagnosis rather than deleted: the reasoning is the reason the fix
is the shape it is, and defect 1 in particular is a diagnosis a fresh reader would
get backwards if only the conclusion survived.

### 1 · The runway idents are drawn at the wrong end of their strip · **FIXED**

> `RunwayRay` now carries an explicit `threshold`. `RunwayIdentAnchorTest` asserts
> over both layouts that an ident anchors on the opposite side of the strip midpoint
> from its own ray tip, and the test was verified by planting the old behaviour and
> watching two of its four cases fail. Confirmed on a device against KDEN (16R north,
> 34L south, 07 west, 25 east) and against LGAV in a quartering 290 deg wind, where the
> favoured 21L now reads correctly at the north-east threshold.

**This is the one a fresh reader will get backwards, so read the whole item before
changing anything.**

Reported as: *"the preferred runway is showing the one going along with the wind
instead of against it — either the sock is wrong or the highlighted runway is."*

**Neither the sock nor the highlight is wrong.** On the reported case the sock points
about 115° (ESE), so the wind is *from* about 295°; runway 30 (heading 300°) is
nearly straight into it, and 30 is what `favouredEnd` highlighted. `SurfaceWind` is
correct and so is the sock.

**What is wrong is where the ident sits.** `RunwayDiagram` draws each ident at
`ray.tip`, and in both layouts the tip is the end the runway *points toward*:

- `positionedRays` — for a paired physical runway, `rays[i] = RunwayRay(a, b)` where
  `a` is runway *i*'s own threshold and `b` is the opposite threshold. So the ident
  lands on the **opposite** threshold.
- `laneRay` — the ray runs from a centre/lane point outward along the heading, so the
  ident lands at the far end.

Every real airport diagram (FAA, Jeppesen) paints the designator **at the threshold**
— the end you cross on landing and start from on takeoff. So a pilot reading this
diagram applies that convention, sees `30` at the north-west end, concludes the 30
threshold is at the north-west and that departing 30 means rolling south-east — i.e.
downwind. Hence the report. The diagram is contradicting the convention it looks like
it is using, and the new highlight made a pre-existing inconsistency consequential.

**No information is lost by moving it**, which settles the obvious counter-argument
("the label at the far end tells you which way the runway points"): the designator is
self-describing. `30` *means* heading 300°. Put it at the threshold and the direction
is still readable, from the digits.

**Fix:** anchor each ident at the end of the strip **opposite** the direction that
runway points.

- Positioned layout: `ray.origin`.
- Schematic layout: the two paired rays share an origin, so `origin` is unusable —
  mirror the tip about the origin instead (`origin - (tip - origin)`), which puts
  `30` where `12`'s ray tip is and vice versa. The outward label push must then be
  computed along the mirrored direction, not the ray's own.

Add a test over both layouts asserting each ident's anchor is on the opposite side of
the strip's midpoint from its own ray tip.

### 2 · Decision needed — a green chip beside a hatched sky · **FIXED**

> Resolved by the repository owner as a **veto rather than a blanket UNKNOWN** — see
> "Stage 3, as it actually landed" for why the obvious version was the worse one.
> `buildMetar` drops the provider category when `MetarParser` found no sky, and
> `FlightRules.derive` decides instead. `WeatherTest`s assertion here is the exact
> reverse of the one that stood before, and says so.

`ZUZH` reports `9999 // //////` — an automated station whose cloud sensor returned
nothing. Our `FlightRules.derive` correctly returns `UNKNOWN`; `buildMetar` prefers
the provider's `fltCat`, and NOAA computed `VFR` from the same missing sky. So the
chip says VFR while the scene says "we do not know what the sky is" — **the original
bug's shape, surviving in the chip.**

Making `SkyCover.Unknown` force the category to `UNKNOWN` is a one-line precedence
change in `MetarSupplement.buildMetar`, but it overrides a provider field that was
deliberately given precedence in Stage 1. **Needs an explicit call before changing.**

### 3 · A stale report is rendered as if it were current · **FIXED**

> `ObservationAges` in `:core:model` bands an observation as current, ageing or
> stale, and `MetarPanel` prints the `ddhhmmZ` group beside the elapsed time,
> recomputed once a minute so a backgrounded-and-resumed app cannot keep claiming
> "4 min ago". The colour flags it but the words carry it alone, because colour as
> the sole channel fails exactly the reader who most needs the warning. Caught in
> the wild on the emulator: a ZGMX report rendered `170000Z - 1775 days ago` in red
> where it had previously drawn as confident VFR weather.

`ZUZH 241300Z` — three days old — drew identically to a report from four minutes ago.
`observationEpochSeconds` is fetched, cached and shown nowhere. This is the one
outstanding item with a safety flavour.

### 4 · Fixed already, recorded so they are not reintroduced

Nine defects found by looking at a device, none visible in a green build, a passing
test or a preview:

1. Threshold hairlines painted with `container` (a pastel *background* tone) where
   the ink of the pair is `onContainer` — the ruler was nearly invisible.
2. The whole palette reading as a weather app rather than as this app.
3. Cloud decks reading as **fluorescent tubes** — a rectangle with a gradient in it.
4. Cloud bodies with almost no contrast against the sky.
5. `deckShoulder` expressed against thickness, exceeding `deckLobeAmplitude`, so
   OVC's **valleys sat above its peaks** and the silhouette inverted into a faint
   ripple. Shoulder is now a fraction *of the peaks*, where the relation cannot come
   apart.
6. The ceiling marker as a 10 dp stub in the gutter, reading as an artefact rather
   than a marker. Now a filled wedge on the right edge, clear of the numerals.
7. `6.213090551181102 SM` — an ICAO `9999` group converted and interpolated raw.
8. The wind-axis arrow **running clean off the frame** — nothing there clips.
9. The windsock given a **droop, in a plan view.** It rose *above* its own mast for
   northerly winds, because the droop was being added to the bearing's screen-space Y
   component, which is a quantity a top-down view does not have. **From above you
   cannot see a sock hang — you see it foreshortened.** Lift is now length, and the
   mast is a dot, because a vertical line on a top-down chart is a projection error.

Two near-misses worth keeping in mind:

- `KHPN`'s `BKN013 OVC019` lands *exactly* on the merge boundary (0.06 apart) and
  collapses to one deck. This was first mistaken for a drawing bug — two decks
  appearing as one line looked like a body painting over its neighbour. It is pinned
  by a test now. The clamp added while chasing it (`deckThicknesses`) is still a
  valid guard for the cross-boundary case, which `mergeDecks` refuses to collapse.
- A latent bug in the deleted `AnimatedWeatherGlyph.drawFog` made fog bands snap
  every 6 s because a non-integer cycle count met `RepeatMode.Restart`. **Every
  phase-driven term must complete an integer number of cycles.** The deck drift obeys
  this (its period is exactly one frame width); anything new must too.

---

---

## Stage 4 — the Claude Design pass

The 29 gallery screenshots went into a Claude Design project as a card fixture with
its own harness, and it came back with a redesign of both halves of the panel. This
is what was taken, what was refused, and why.

**Taken.** Each of these fixed something the gallery had already made visible:

| Change | The defect it closes |
| --- | --- |
| Air as four flat steps, breaking at the flight-rules thresholds | The wash read as a different app beside the route card's flat map, and its tone changes sat at heights that mean nothing. The steps are now one per band |
| The graticule drawn **over** the scene, numerals on chips | An overcast lid at 900 ft covered the 1,000 ft hairline — the band structure vanished in exactly the case it exists for. `onSurfaceVariant` on the night band is about 2.3:1; `cloudEdge` on a known chip is proved |
| Fog as an opaque slab with a hard top edge | Faded into the air it met the sky at ~1.1:1, and the headline `FG VV002` case looked like a clear day |
| Two lightning strikes instead of a convective tick | A 2 dp line was the quietest possible mark for the only hazard in the frame |
| Per-drop length, weight, opacity and speed | The field read as pen hatching, because every stroke was identical |
| A `ValueChip` grid, the age on the identifier's row, the raw text behind a tap | Forty characters of monospace under every panel — twice over on the route screen — pushed everything read at a glance off the top |

**Refused, with the reason:**

- **Deck sway in place of directional drift.** The design's own fixture note asks
  for drift (*"a westerly, so the decks drift east"*) while its CSS only sways; the
  drift is also the app's third use of the same east–west commitment, so replacing
  it would have left the rain slanting one way and the cloud going nowhere.
- **The celestial body drawn under the decks.** The design's mock pins the body at a
  fixed corner, so it never met a high deck. Painting under is the KDEN bug — and it
  contradicts the design's own stated principle that alpha is the occlusion channel.
- **The body's fixed position and cover-keyed alpha ladder.** The app has real
  astronomy; the design has a harness that cannot. Its alpha ladder is
  `celestialAlpha` with different numbers.
- **Tangent circles for the cloud lobes.** The existing cubic silhouette is the same
  idea with smooth valleys; a row of tangent circles reads as a chain of bubbles.
- **The design's fixed run positions.** `deckSpans` already counts eighths and
  jitters them deterministically from the deck's own base, which is the same idea
  plus a determinism argument the design does not have.

**Three defects the design's own numbers introduced**, all caught on the device or
by a test, all fixed:

1. **A fractional per-drop speed breaks the loop.** The design gives every drop its
   own CSS duration, which has independent timelines; one shared Compose phase does
   not, so a non-integer speed leaves each drop mid-frame at the restart and the
   whole field jumps once a cycle. Speeds are now whole crossings — 2, 3 or 4.
2. **A uniformly scaled bolt is a slab.** The design's SVG is a fixed 15 px wide with
   only its height stretched. Scaled uniformly, KJFK's 4,300 ft CB drew a maroon
   wedge a fifth of the frame across. The width is now fixed in dp.
3. **The design's step boundaries are its own axis, not this one.** Transplanted as
   raw fractions they landed a few per cent off every hairline, which read as an
   accident. They are derived from `CeilingThresholds` now, which is both exact and
   what makes the steps mean something.

Two further collisions the pass exposed, neither of them the design's doing:

- A body at a westerly azimuth had the ceiling wedge drawn through it — the full
  moon over EHAM. The rail's inset now clears the wedge's depth as well as the
  frame's edge.
- An unlimited ceiling's wedge sits at the top of the axis, where the card's
  `shapes.large` corner ate half of it. It is clamped clear of the radius.
## Outstanding

### Closed by Stage 3

- ~~**A clear sky is an empty frame.**~~ Two things fixed it: the celestial layer,
  which is at full strength precisely when the frame is emptiest, and giving
  `Ceiling.Unlimited` the same wedge `Ceiling.At` already had. A clear sky now
  differs from an unknown one by an *affirmative mark* rather than by the absence
  of hatch.
- ~~**Chart has never been seen on device.**~~ Seen. The air reads as a wash over
  paper and the vermilion sock sits on it without reading as amber. Its navy sun is
  still the palette's aesthetic risk and has not been caught with the sun up.
- ~~**Twilight and night have never been seen.**~~ Night seen at KSFO (08:56Z, 01:56
  local): the authored night band with pale cloud undersides, the moon on the rail.
  **Twilight itself is still unseen** — it needs a station caught within about ten
  degrees of the horizon, which is a narrow window to hit by hand.
- ~~**Defects 2 and 3**~~ and ~~**unit-aware visibility and altimeter**~~ — all done,
  see the defects section and W6.

### Still open

- **Twilight has not been looked at**, and it is the band every palette found
  hardest to author — neither bright nor dark. Worth catching deliberately rather
  than waiting to stumble on it.
- **Four of the five ground states have never been seen.** Only `Dry` has appeared
  on a real station. `Wet`/`Snow`/`Frost`/`Icy` are enforced mutually
  distinguishable by test, but not looked at.
- **Precipitation has not been seen falling.** The geometry is tested and the field
  is seamless by construction, but no station under real rain has been opened, and
  two things about it can only be settled by looking: whether the 62° fall-angle
  clamp reads as blowing snow or as a hatch pattern over the frame, and whether the
  static field under reduce motion reads as rain or as noise. The failure mode of
  the first is that a snowy field resembles the *unknown* state, which is the one
  confusion this scene must never make.
- **Font scale 2.0 and RTL are unverified.** The label-drop path is measured rather
  than guessed, which is the right mechanism, but nobody has watched it fire.
- **The favoured-end halo may now be redundant.** The bold orange ident says which
  *end*; the halo says which *strip*. Worth deciding whether both survive.
- **The ground band is the weakest element that remains.** It works, but it is still
  a 16 dp strip at the bottom doing very little.
- **A body can be occluded by a top deck.** The rail sits at 0.86 and
  `MaxDeckFraction` is 0.94, so a `FEW250`-only report can put a deck across it.
  Alpha handles the general case honestly, but a sun half behind a cirrus wisp at
  168 dp has not been looked at and may read as a drawing fault rather than as cloud.

### Animation

Shipped: **deck drift** (rate from the reported wind, direction from its component
in the section plane, faster higher up because wind speed genuinely rises with
altitude, seamless by drawing each deck twice a frame-width apart), **sock flutter**
(period shortens with speed), **precipitation** (form, rate and slant from the
report; seamless by deriving `x` from `y`), **the sock's drag**, and the **band
crossfade** from the sun's real elevation.

Deck drift and flutter go off *entirely* under reduce motion. Precipitation is drawn
**still** instead — the asymmetry is deliberate and W5 explains it.

Still to build:

- **Entrance staging for the scene.** Currently it appears fully drawn. The screen's
  own stagger may be enough; worth looking before adding a second motion.
- **A gust indication.** `windGustKt` is parsed, cached and rendered nowhere. A
  periodic surge in the drift rate is the obvious reading and would make gusty
  fields legible at a glance.
- **The sun and moon do not move**, and that is a consequence of drawing the
  *observation* instant rather than the current one: the celestial layer is static
  by construction. A ticker that moved it would be animating a different moment
  from the rest of the frame.

### Data and behaviour

- The AVWX key is entered by the user, never by the assistant.

## Verification

- `./gradlew build checkInvariants` — all variants, all tests, lint, R8. **Green.**
- Suites added across all three stages: `MetarParserTest`, `GroundConditionTest`,
  `WeatherTest`, `MetarSupplementTest`, `NoaaMetarDtoTest`, `NoaaCloudsCrossCheckTest`,
  `AvwxMetarClientTest`, `MetarCacheRepositoryTest`, **`SkyColorsContrastTest` (7)**,
  **`SkyProfileGeometryTest` (35)**, **`SurfaceWindTest` (14)**, and from Stage 3:
  **`ObservationAgeTest` (7)**, **`VisibilityFigureTest` / `AltimeterTextTest` (8)**,
  **`RunwayIdentAnchorTest` (4)**, **`WindsockDragTest` (4)**, **`SolarPositionTest` (12)**,
  **`LunarPositionTest` (7)**, **`HorizonRailTest` (6)**, **`SkyBlendTest` (5)**,
  **`PrecipitationGeometryTest` (10)**, **`MoonTerminatorTest` (3)**.

### What the celestial tests are actually checking

Every numeric assertion is either a value from the reference algorithm or a figure
derivable from spherical geometry in one line — never a value read off this
implementation and pinned. Three are worth naming:

- **Meeus Example 47.a is asserted exactly.** All three printed sums come out to the
  unit, which is the whole reason the lunar tables are carried in full.
- **The published full moon of 3 January 2026 (10:02Z)** is found by scanning the
  elongation, and depends on the solar position, the lunar position and the phase
  arithmetic all at once.
- **The synodic month needs a fifty-year window**, and the comment says why: the
  estimator is `(last - first) / intervals`, so it is fixed entirely by the two end
  crossings and is *not* an average. Ten years is out by 0.0039 d and fifty by
  0.00013. Only the last is a statement about the series rather than about the window.

### What `SkyColorsContrastTest` enforces, and why each rule exists

Six claims that until now lived only in prose, two of them theme *identity* rules a
plausible-looking edit could have broken silently:

1. **Cloud edges clear 3:1 against both ends of their own band.** The check that
   forced the palettes apart in the first place.
2. **The five ground states are mutually distinguishable.** Frost and ice collapsed
   into one pale blue-grey twice while the values were being chosen; dry and wet
   collapsed in Cockpit.
3. **The moon's two halves are distinguishable** — otherwise every night is full.
4. **The three horizons are distinguishable.** Horizon only: the band tops
   legitimately converge, and asserting otherwise would assert something false about
   the air.
5. **Cockpit stays dim** — separate ceilings for fills (0.42) and marks (0.58), split
   by *area*, because a glyph costs a dark-adapted eye almost nothing while a
   frame-spanning fill costs it real recovery time. Cockpit's own `onSurface` sits
   near 0.78, far above either bound, which is the point.
6. **Chart carries no amber**, as a hue-and-saturation window rather than a list of
   forbidden values, so it catches a colour nobody thought to name.

**Rules 5 and 6 were verified by planting violations** and watching them fail (a
bright sky pasted into Cockpit, an amber sun into Chart) — per `CLAUDE.md`, a rule
that matches nothing passes silently and looks identical to a clean tree.

### Regression fixtures, all captured from live NOAA on 2026-08-27

| Fixture | Must produce |
| --- | --- |
| `PACD … 1/2SM FG VV002`, `vertVis:2`, `clouds:[{"cover":"OVX","base":200}]` | A **200 ft obscuration**, not an unlimited ceiling. The headline regression. Note `vertVis` is *hundreds* of feet while `clouds[].base` is feet — take the base |
| `EHAM … CAVOK`, `clouds:[]` | **Clear.** Likewise `SKC` (MMCL) and `CLR` (CYYL, `AUTO` with a `110V200` range) |
| An AVWX report, or a v1-cache row | **Unknown**, rendered as hatch. The case that must never look sunny |
| `KJFK … FEW015 BKN043CB BKN110 OVC130` | Four decks, `CB` on the second, `-TSRA BR`, a gust, `fltCat:"MVFR"`. Its upper two land 0.031 apart and **merge** — compression working, not a limit hit |
| `KSYR … BKN032 BKN060 BKN120 BKN200` | Four BKN layers, the merge-within-band case |
| `KHPN … 2 1/2SM BKN013 OVC019` | Sits **exactly** on the merge boundary and collapses. See defect 4 |
| `KLCH … VRB05KT 10SM SCT027` | `VRB` wind must be **"variable"**, not "no wind data" |

### Still owed on device

**Done in Stage 3**, each against a live station rather than a fixture:

| Looked at | Where | What it showed |
| --- | --- | --- |
| Ident at the threshold | KDEN, LGAV | 16R north / 34L south / 07 west / 25 east; LGAV's favoured 21L at the north-east threshold in a quartering 290 deg wind |
| Observation age | VVPQ, ZGMX | `21 min ago` in grey; a `1775 days ago` report in red that had previously drawn as confident VFR |
| Unit-aware figures | LGAV, KDEN, OEAO | `1015 hPa` from `Q`, `30.19 inHg` from `A`, `>=6.2 SM` from `9999` |
| No data at all | LGTG offline | The whole frame hatched, ground included, and said so in words |
| Chart palette | KDEN | Air as a wash over paper; the vermilion sock does not read as amber |
| Sun on the rail | LGAV | Azimuth 148 deg at 12:20 local, clear of the ruler |
| Night band | KSFO 08:56Z | The authored night band, pale cloud undersides, the moon on the rail |
| The sock's drag | LGAV | Swings, clamps at 120 deg, springs back; the favoured end never moves; vertical scroll still works |

### The gallery, and why it is gone

Stages 3 and 4 were both driven from a **`WeatherGalleryScreen`** behind Settings:
29 states, one per tap, each real raw METAR text run through the real parser, with
lighting instants solved for the elevation they were named after. Twilight, a
crescent moon, hail, freezing rain and a fogged-in field occur at a real station
rarely and never on demand, so browsing airports and hoping was never going to reach
them — which is why several had gone unlooked-at through two shipped stages.

It earned its keep. On its first run it found a body at a due-east or due-west
azimuth losing half its disc to the frame — a waxing crescent at 272°, where nothing
about the astronomy was wrong and so no test could have caught it. Stage 4 was
checked entirely through it, in Brand light on the SM-S942B: CAVOK (step edges on
the hairlines, the unlimited wedge clear of the card's corner), four decks + CB (the
strike at its proper width, mid-envelope), obscured `VV002` (the opaque slab and its
hard edge under the LIFR wedge), snow (the OVC lid on its own hairline, dots of
mixed size and weight), and night + full moon (pale numerals on dark chips, the disc
whole and clear of the wedge).

**It was removed at the end of Stage 4, at the repository owner's request**, along
with its destination, its Settings entry and the `celestial` parameter that existed
only so it could hold a report's freshness and its lighting apart. Anyone resuming
this work should expect to rebuild it rather than to find it: about 280 lines, a
`List<WeatherCase>` of raw METAR strings plus a station position and an epoch, a
`Box` that cycles the index on tap, and a `MetarPanel` with an overridable
`CelestialState`. It is worth the half hour; the scene has states no live station
will hand you.

**Still owed**: all four themes at font scale 2.0 and in RTL. The gallery reaches
every weather state; what it does not vary is the theme or the type scale, and
both are a settings change away. Twilight is now reachable — gallery card 19 — and
still has not been looked at.

Navigation notes for whoever does this: `adb` is at
`C:\Users\daanb\AppData\Local\Android\Sdk\platform-tools\adb.exe`; drive taps from
`uiautomator dump` bounds rather than screenshot maths (see
`adb-tap-coordinates-need-uiautomator-not-screenshot-math` in memory); set
`MSYS_NO_PATHCONV=1` so Git Bash does not mangle `/sdcard/…`; and **one tap per
call** — chained taps outrun the screen transitions. Theme is switched in
Settings (gear, Plan header); the Airports browser is the adjacent icon.
