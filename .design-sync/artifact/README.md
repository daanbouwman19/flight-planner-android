Flight Planner is a native Android app that generates flyable routes between real airports, logs the flights a pilot has flown, and draws weather as a chart rather than an illustration. This system mirrors it: Material 3 Expressive with an aviation vocabulary on top, written in Kotlin and Jetpack Compose and mirrored here as React components on `window.FlightPlannerDS`. Build with the real components; they carry decisions that are easy to undo by accident. A concept made here goes back to the app as intent (a hierarchy, a colour role, a spacing rhythm), never as pixel values transplanted into Compose, because every colour, type slot, corner, shape and spring here is generated from the Kotlin source and follows it.

## Start every design inside `FlightPlannerTheme`

Every colour is a CSS custom property that `FlightPlannerTheme` scopes onto its subtree through `data-theme="fp-<theme>"`. A component rendered outside it resolves `var(--fp-primary)` against nothing and comes out unstyled, the most common way to get a broken-looking design here. Most components also read theme and locale from its context.

```tsx
const { FlightPlannerTheme, RouteCard } = window.FlightPlannerDS

<FlightPlannerTheme theme="brandLight" fullBleed>
  <RouteCard
    aircraft="Cessna 172S Skyhawk"
    category="Single Engine Piston"
    departure={{ icao: 'EHAM', lat: 52.3086, lon: 4.76389, runway: '12,467 ft', rules: 'VFR' }}
    destination={{ icao: 'EBBR', lat: 50.9014, lon: 4.48444, runway: '11,936 ft', rules: 'VFR' }}
    distance="92 NM"
    flightTime="0:52"
  />
</FlightPlannerTheme>
```

- `theme` takes `system` (the default), `brandLight`, `brandDark`, `cockpit` or `chart`. Nest wrappers to show two themes side by side; the inner one wins for its subtree.
- **Cockpit and Chart are products, not tints.** Cockpit is a near-black instrument panel with amber `fp-primary`, for flying at night; Chart is printed chart paper (`fp-background` #f4efe4) with navy ink. Never treat them as a third dark mode and a second light one.
- Mount the tree into a node of its own (`<div id="ds-root">`), never the host page's React root, so the two trees do not collide. React must be on the page before the bundle.
- `tokens.json` holds the four resolved themes — Brand Light (the default), Brand Dark, Cockpit, Chart — for reading values. Components follow `FlightPlannerTheme`: the wrapper carries `data-theme="fp-<theme>"`, the same attribute the page's theme picker sets, so it wears that theme's values straight from `tokens.json`. `system` is not a page theme: it takes Brand Light's values and switches to Brand Dark's under `prefers-color-scheme: dark`, which `bundle.css` carries because `tokens.json` cannot. The page's theme picker at its default leaves each wrapper its own theme; set to any other theme, the picker wins for every wrapper, nested ones included.

## Content fundamentals

The app speaks to one pilot, plainly, in British English ("Licences", "colour"), and it reads like a chart: short, exact, figures first.

- **Sentence case** for titles and buttons: "No routes yet", "Add aircraft", "Airport data is out of date", "Mark flown".
- **Say what is absent, then the next step.** An empty state is a description plus an action: "No routes yet" / "Pick an aircraft and generate a few." / **Generate**. A description alone is a dead end.
- **Say what failed and what did not.** "Couldn't load weather" / "The station didn't answer. Your route is unaffected." Contractions are fine.
- **Confirmations ask a question and name the consequence.** "Delete this flight?" / "It will be removed from your logbook and your statistics." The affirmative button names the action (**Delete**, **Replace**), never "OK"; the other is **Cancel**.
- **Figure labels are short uppercase abbreviations** set on `ValueChip`: `DIST 3,153 NM`, `RWY 12,467 ft`, `TIME 1:52`, `QNH`, `CEIL`. Section eyebrows are uppercase too: `DEPARTURE`, `AIRCRAFT`.
- **Codes and figures are data.** ICAO idents in capitals (`EHAM`), a leg as `EHAM → EGLL`, durations as `h:mm`, thousands with commas, units after the number (`92 NM`, `640` with `NM` as the field's suffix). Join a caption and its period with a middle dot: "Flights by month · 2026".
- **Never invent coordinates or airports.** A plausible field in the wrong place is a confidently wrong drawing. Use real ICAO codes with their real latitude and longitude.
- No emoji, no exclamation marks, no marketing voice. Address the pilot as "you" and "your" ("your logbook"), never "we".

## Colour

Name colours by role: `var(--fp-<role>)`. Every value lives in `tokens.json`; never paste a hex.

| Family | Tokens | Use |
| --- | --- | --- |
| Primary | `fp-primary`, `fp-on-primary`, `fp-primary-container`, `fp-on-primary-container`, `fp-inverse-primary` | `fp-primary` for the filled button, text buttons and the selected date; `fp-primary-container` for the route hero and the FAB |
| Secondary | `fp-secondary`, `fp-on-secondary`, `fp-secondary-container`, `fp-on-secondary-container` | `fp-secondary-container` is selection: the active nav indicator, tonal buttons, selected chips and filters |
| Tertiary | `fp-tertiary`, `fp-tertiary-container` and their `on-` pairs | `fp-tertiary` is the warning accent (a 'warn' self-check) |
| Surfaces | `fp-background`, `fp-surface`, `fp-surface-container-low`, `fp-surface-container`, `fp-surface-container-high`, `fp-surface-container-highest` | window, sheets and the METAR panel (`-low`), cards and the nav bar (`container`), dialogs (`-high`) |
| Text | `fp-on-background`, `fp-on-surface`, `fp-on-surface-variant` | `fp-on-surface` for titles and figures, `fp-on-surface-variant` for captions, labels and inactive icons, on any surface |
| Error | `fp-error` and its container roles | a failing field's border, label and message; the `ErrorState` title |
| Lines | `fp-outline`, `fp-outline-variant`, `fp-scrim` | `fp-outline` for borders that mark a control; `fp-outline-variant` for hairlines that only separate |
| Flight rules | `fp-vfr-container` / `fp-vfr-on-container`, and the same for `mvfr`, `ifr`, `lifr`, `unknown` | `FlightRulesBadge` only |
| Sky scenery | `fp-sky-day-*`, `fp-sky-twilight-*`, `fp-sky-night-*`, `fp-sky-ground-*`, `fp-sky-celestial-*`, `fp-sky-sock-*` | `SkyProfile`, `RunwayDiagram` and the ocean behind `GlobeView` |

`System` follows the device: its values are Brand Light's, and the wrapper switches to Brand Dark's when the device prefers dark.

The remaining Material roles (`fp-surface-variant`, `fp-surface-tint`, `fp-surface-bright`, `fp-surface-dim`, `fp-surface-container-lowest`, `fp-inverse-surface`, the `*-fixed` families) are carried from the Kotlin scheme; no mirror component paints them yet.

- **Flight-rules colours are semantic and never re-themed.** VFR green, MVFR blue, IFR red and LIFR magenta are standard chart colours; a pilot reads the colour before the letters. They sit outside the Material scheme, Cockpit shares the dark set unchanged, and they are never used for anything that is not a flight category.
- **Sky tokens are scenery.** Never set text on them or use them as UI grounds; `fp-sky-sock-band` (windsock orange) is the one that also marks the favoured runway in `RunwayDiagram`.
- **Legibility.** Every `on-` pair holds at least 5.6:1 in every theme; `fp-on-surface` and `fp-on-surface-variant` hold at least 6:1 on every surface. Two exceptions sit in Chart only: `fp-error` on `fp-surface-container-highest` (4.41:1) and `fp-error` or `fp-tertiary` on `fp-surface-dim`, so keep error and warning text on the lower surfaces there. `fp-outline-variant` is below 3:1 on purpose; it never carries meaning alone.

## Type

One family, Roboto (it is what Compose's sans resolves to on Android, and it ships with the system so text metrics match the app), and Roboto Mono for raw reports. In `tokens.json` they are `type.families.sans` and `type.families.mono`.

Set text with one class per Material slot; each sets size, line height, tracking, weight and figure style together. Never set a raw font size.

| Class | Size / line | Weight | Figures |
| --- | --- | --- | --- |
| `fp-type-display-large`, `-medium`, `-small` | 57/64, 45/52, 36/44 | 400 | tabular |
| `fp-type-headline-large`, `-medium`, `-small` | 32/40, 28/36, 24/32 | 400 | tabular |
| `fp-type-title-large`, `-medium`, `-small` | 22/28, 16/24, 14/20 | 400, 500, 500 | tabular |
| `fp-type-body-large`, `-medium`, `-small` | 16/24, 14/20, 12/16 | 400 | proportional |
| `fp-type-label-large`, `-medium`, `-small` | 14/20, 12/16, 11/16 | 500 | tabular |

Each slot also has an `-emphasized` class, currently identical to its base. `tokens.json` mirrors the fifteen base slots as type styles named by slot (`title-medium`, `label-small`) so their values can be read; in markup always use the `fp-type-*` class, which also sets the figure style.

- **Every figure is tabular and reads left to right.** ICAO codes, distances, runway lengths, headings and times are chart figures. `display`, `headline`, `title` and `label` carry tabular figures; `body` deliberately does not, so set figures in a label or title slot. Typed values in `TextField` are tabular and isolated left to right even under an RTL locale, because a reordered range is a wrong number.
- The raw METAR in `MetarPanel` is the one place for Roboto Mono.
- In the idiom, a screen headline is `fp-type-headline-medium` in `fp-on-background`.

## Shape

Corners come from the Expressive scale, `var(--fp-shape-*)`, 4px to 48px:

| Token | px | Where |
| --- | --- | --- |
| `fp-shape-extra-small` | 4 | text-field box, glass plates on the globe |
| `fp-shape-small` | 8 | chips: `FlightRulesBadge`, `ValueChip`, `ModeSelector`, skeleton bars |
| `fp-shape-medium` | 12 | `FilterField`, `SkyProfile`, `VisitedNetworkCard` |
| `fp-shape-large` | 16 | `MetarPanel`, list rows, the FAB |
| `fp-shape-large-increased` | 20 | cards: `RouteCard`, `fp-card`, `SwipeActionBackground` |
| `fp-shape-extra-large` | 28 | `BottomSheet` (top corners only), dialogs, the state icon disc |
| `fp-shape-extra-large-increased`, `fp-shape-extra-extra-large` | 32, 48 | in the scale, unused so far |

Buttons are pills (`fp-button`, 40px tall, 20px corner). A sheet meets the window's bottom edge, so only its top corners round.

## Surfaces, borders and elevation

- Depth is tone, not shadow: step up through `fp-surface-container-low` → `fp-surface-container` → `fp-surface-container-high` rather than adding a shadow.
- Ready-made classes: `fp-card` (`fp-surface-container`, `fp-on-surface`, 20px corner), `fp-surface`, `fp-surface-container`, `fp-surface-container-high`, `fp-surface-container-low`, `fp-button`, `fp-button--tonal`, `fp-button--text`.
- **Glass** is used only over the globe: `fp-surface-container` at 0.82 alpha (`GLOBE_PLATE_ALPHA`), an `fp-shape-extra-small` corner and one `fp-outline-variant` hairline. `GlobeCameraControls`, `GlobeAttribution`, the DEP/DEST labels and the globe-mode app bar all draw this same plate.
- Do not put a scrim over `RouteMap`: land drawn at 8 % is what makes one unnecessary.

## Layout

- **The system bars stay empty.** The window is edge to edge; the status and navigation bars are transparent, content scrolls up under the clock and down under the gesture handle. Never paint a bar, scrim or gradient there. `PhoneFrame` encodes this, so build screens inside it. `TopAppBar` is transparent for the same reason.
- **Phone and tablet are one screen, not two.** Every screen takes `layout="phone"` (default, 360 × 800) or `layout="tablet"` (1280 × 800). It is a width, not a variant; never design a parallel tablet screen. Three things change, all decisions:
  1. The bottom `NavigationBar` becomes a `NavigationRail`, and the rail carries five destinations where the bar carries four. Settings lives in the app bar on a phone and gets a permanent home on the rail. The rail never auto-hides.
  2. Plan, Fleet and Logbook become list/detail: pass a `detail` (`RouteDetailPane`, `FleetDetailPane` or `FlightDetailPane`) and the screen sets it beside the list (`TwoPaneScaffold` is that split on its own). Selecting a row replaces that pane in place, so a pane change is a fade-through, never a slide.
  3. Content is width-capped, never stretched: `fp-content-cap` (640px), `fp-content-cap--wide` (840px, detail screens and the logbook). Extra width becomes margin. It is deliberately not a grid.
- Screen padding in the idiom is `36px 16px 16px` with a 12px gap between blocks.
- Design portrait phones and landscape tablets. Short windows (a phone on its side) collapse the app bar in the app but are not mirrored; say so if a concept depends on height.

```tsx
<PlanScreen layout="tablet" routes={routes} detail={<RouteDetailPane … />} />
```

## Motion

Two spring families, each with a duration token and an easing variable (`--fp-motion-<token>-easing`, a sampled spring curve that lives in `bundle.css`):

| Token | Duration | For |
| --- | --- | --- |
| `fp-motion-spatial-fast-duration` / `-spatial-` / `-spatial-slow-` | 376 / 451 / 616ms | anything that moves or resizes |
| `fp-motion-effects-fast-duration` / `-effects-` / `-effects-slow-` | 166 / 247 / 343ms | fades, colour, elevation |

- **Spatial for movement, effects for everything else.** Spatial springs overshoot, and that overshoot is the character; a spatial spring on a colour fade reads as a flicker.
- One transition often uses both: `RouteCard` fades in on the effects spring while it rises on the spatial one.
- Give a card its list position as `enterIndex` and it staggers `fp-stagger` (30ms) per row, capped at eight rows. `replacing` makes it arrive from the end edge, because the card it replaced left towards the start.
- Anything staged or infinite (stagger, shimmer, flutter) switches **off** under `prefers-reduced-motion` rather than getting shorter.

## Iconography

- The app's own icons come from `NavIcon`: exactly five, `plan`, `fleet`, `logbook`, `stats`, `settings`. They draw in `currentColor` at any `size` (default 24); tint them by setting `color` to a role such as `fp-on-surface-variant` or `fp-primary`.
- `EmptyState` and `ErrorState` take an optional glyph, drawn in a tonal `fp-surface-container-high` disc above the title.
- There is no icon font and no image assets in this system. Never use emoji as icons, and never invent a sixth navigation icon; a new destination is a design decision to state in words.
- Pictures are data: `RouteMap` projects real geography and draws the leg, `SkyProfile` puts every reported cloud deck at its true altitude, `RunwayDiagram` draws the field's runways in plan with the windsock. Never decorate them, and never draw a sun on an IFR field: the category follows from the geometry.

## Components

61 components in three groups.

- **theme**: `FlightPlannerTheme`.
- **screens** (the app as it exists; start a concept from the closest one): `PlanScreen` (where the app opens), `FleetScreen`, `FleetDetailScreen`, `LogbookScreen`, `StatsScreen`, `AirportsScreen`, `AirportDetailScreen`, `RouteDetailScreen`, `ImmersiveGlobeScreen`, `SettingsScreen` (Appearance, Units, Airports, Weather, About). `StartupCheckScreen` and `LicencesScreen`, both reached from Settings, are filed under general.
- **general**, by job:
  - Frames and navigation: `PhoneFrame`, `TabletFrame`, `TopAppBar`, `NavigationBar` (Plan, Fleet, Logbook, Stats), `NavigationRail`, `NavIcon`, `TwoPaneScaffold`, `MonthHeader`.
  - Routes and flights: `RouteCard` (the most-seen surface), `RouteMap`, `RouteDetailPane`, `FleetDetailPane`, `FlightDetailPane`, `ValueChip`, `FlightRulesBadge`, `ModeSelector`, `FilterField`, `SwipeActionBackground`.
  - Forms and modals: `BottomSheet`, `PickerSheet`, `AddAircraftSheet`, `EditEnvelopeSheet`, `AddFlightSheet`, `TextField`, `ConfirmationDialog`, `FlightDatePickerDialog`, `ScrimOverlay`.
  - Weather and airfields: `MetarPanel`, `SkyProfile`, `RunwayDiagram`.
  - Statistics: `HeroDistanceCard`, `MetricGrid`, `StatSummaryStrip`, `VisitedNetworkCard`, `MonthlyActivityCard`, `RankedListCard`.
  - Globe: `GlobeView`, `GlobeRouteScene`, `GlobeHero`, `GlobeNetwork`, `GlobeCameraControls`, `GlobeAttribution`.
  - States: `EmptyState`, `ErrorState`, `MorphingLoadingIndicator`, `SkeletonBox`, `SkeletonCard`.
  - Outside the app: `ChallengeWidgetCard`.

Each component's guideline (props, examples and the reasoning behind its API) is its card. Library components for the controls; tokens and type classes for your own layout glue.

### Sheets, fields and dialogs

- **Every form, and every modal choice, is a modal bottom sheet.** There is no dialog with fields. The only true dialogs are `ConfirmationDialog` (a destructive yes/no) and `FlightDatePickerDialog` (a calendar).
- The four flows are `PickerSheet` (airport or aircraft), `AddAircraftSheet`, `EditEnvelopeSheet` and `AddFlightSheet`. Compose a new one from `BottomSheet` and `TextField`; never invent a modal vocabulary beside them.
- `BottomSheet` and the dialogs render inline, not in a portal, so a sheet can be one element of a concept. Wrap it in `ScrimOverlay` when the concept is about the modal state; put that inside `PhoneFrame` when it is about the sheet on a screen.
- Two heights: `PickerSheet` takes 90 % of the window because its results are the content; form sheets size to themselves, because a three-field sheet claiming the window reads as something missing.
- `TextField` is the only field. Units go in `suffix`, never the label. A failing field states its reason on itself, in `fp-error`, not in a banner above the form.

```tsx
<PhoneFrame>
  <ScrimOverlay>
    <AddAircraftSheet manufacturer="Cessna" variant="172S Skyhawk" range="640" />
  </ScrimOverlay>
</PhoneFrame>
```

### Weather is one panel

`MetarPanel` is the whole of a station's weather: the sky cross-section edge to edge, then the station, its category and the report's age on one line, the figures as `ValueChip`s, the sky in words, and the raw report behind a tap. The airport screen shows one; the route screen shows two. Never rebuild it from a bare `SkyProfile` and a card title: the panel makes the category read as a consequence of the geometry, and two hand-built ends of a route drift apart. Its figures sit two to a row so labels align down an edge, and a short last row keeps its hole.

### The globe is a step taken on purpose

Flat is first everywhere; the globe costs a renderer and a network. Where a device cannot draw one, the switch is absent, not disabled.

| Surface | How the globe is reached |
| --- | --- |
| `RouteDetailScreen` | `heroMode="globe"` (default `"outline"`) with `globeAvailable` and `onOpenImmersiveGlobe`; the Flat/Globe switch and a fullscreen action sit in the app bar, which goes to glass |
| `ImmersiveGlobeScreen` | the globe with the window to itself; the route reduces to one plate of figures |
| `VisitedNetworkCard` | `globeAvailable` adds a Flat/Globe toggle; `initialView="globe"` seeds it |

Compose a standalone globe from `GlobeHero` (deep hero with chrome), `GlobeRouteScene` (the route on the sphere, no chrome) or `GlobeView` with a camera from `frameRoute` / `framePoints`. Each takes `{ icao, lat, lon }` with real coordinates. In the app the sphere is NASA imagery; the mirror draws an outline globe through the same camera, still, with no gestures. Framing, arc, plates, limb and glass are faithful; only the photograph is a stand-in, so design which projection and framing, not the pixels of the sphere.

```tsx
<RouteDetailScreen
  heroMode="globe"
  globeAvailable
  onOpenImmersiveGlobe={() => {}}
  departure={{ icao: 'EHAM', name: 'Amsterdam Schiphol', lat: 52.3086, lon: 4.7639, runway: '12,467 ft', rules: 'VFR' }}
  destination={{ icao: 'KJFK', name: 'John F. Kennedy Intl', lat: 40.6394, lon: -73.7793, runway: '14,511 ft', rules: 'IFR' }}
  distance="3,153 NM"
  flightTime="7:04"
  bearing="291°"
/>
```

### Statistics, states and the widget

- `StatsScreen` is a composition of `HeroDistanceCard`, `MetricGrid`, `VisitedNetworkCard`, `MonthlyActivityCard` and `RankedListCard`, so redesign a card and it lands in every arrangement at once.
- **`EmptyState` and `ErrorState` are not interchangeable.** One is the app working and waiting; the other is the app having failed. Rendering them alike teaches people to ignore the message.
- `ChallengeWidgetCard` is the home-screen widget, not a screen: it lives outside `PhoneFrame`/`TabletFrame` at its own cell sizes (`wide` for the 250 × 100 dp four-cell layout, omitted for the compact bucket that drops the airframe line). It reuses `RouteMap` at `topInset={36}`. It has no navigation, no scrolling, one tap.

## What the mirror does not carry

These are behaviour in the app; a design must not quietly assume them, and a concept that changes one says so in words.

1. Compact height and landscape phones (the app bar collapses in a short window).
2. Chrome that retracts on scroll: the bottom bar and app bar hide going down and return going up. `PhoneFrame` always draws them, so state which state a concept assumes.
3. Swipe to delete and its undo: `SwipeActionBackground` is here; the gesture and snackbar are not.
4. `PickerSheet`'s search behaviour: focus one frame after opening, a debounced query, results scrolled to the top on each new one. Only its shape and result rows are mirrored.

The drag handle on `BottomSheet` is drawn and does nothing, for the same reason.

## One idiomatic build

```tsx
<FlightPlannerTheme theme="brandDark" fullBleed>
  <PhoneFrame bottomBar={<NavigationBar selected="plan" />}>
    <div style={{ padding: '36px 16px 16px', display: 'flex', flexDirection: 'column', gap: 12 }}>
      <h1 className="fp-type-headline-medium" style={{ color: 'var(--fp-on-background)', margin: 0 }}>
        Plan
      </h1>
      <ModeSelector
        options={[{ label: 'All' }, { label: 'Not flown', count: 116 }]}
        selectedIndex={1}
      />
      <div className="fp-card" style={{ padding: 16, display: 'flex', gap: 8 }}>
        <ValueChip label="DIST" value="3,153 NM" />
        <FlightRulesBadge rules="MVFR" />
      </div>
    </div>
  </PhoneFrame>
</FlightPlannerTheme>
```
