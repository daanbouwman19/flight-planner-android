# Building with Flight Planner's design system

This library mirrors a native Android app: a flight planner that generates flyable
routes between real airports, logs flights, and shows weather as a chart rather
than as an illustration. Its look is Material 3 Expressive with an aviation
vocabulary on top. Build with the real components below; they carry decisions that
are easy to undo by accident.

## Wrap everything in `FlightPlannerTheme`

Every colour in this system is a CSS custom property defined by that wrapper. A
component rendered outside it resolves `var(--fp-primary)` against nothing and
comes out unstyled — the single most common way to get a broken-looking design
here.

```tsx
import { FlightPlannerTheme, RouteCard } from '@flightplanner/design-mirror'

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

`theme` takes `system` (default), `brandLight`, `brandDark`, `cockpit` or `chart`.
Nesting is fine and is how a design shows two themes side by side.

**`cockpit` and `chart` are not a third dark mode and a second light one.** Cockpit
is a near-black instrument panel with amber accents, for flying at night; Chart is
printed chart paper with navy ink. Use them as distinct products, not as tints.

## The styling idiom: CSS custom properties and `fp-` classes

There is no utility framework here. Style your own layout glue with the tokens the
theme defines, and reach for the type classes rather than raw font sizes.

**Colour** — `var(--fp-<role>)`, one per Material role, in kebab-case:

| Family | Names |
| --- | --- |
| Primary | `--fp-primary`, `--fp-on-primary`, `--fp-primary-container`, `--fp-on-primary-container`, `--fp-inverse-primary` |
| Secondary / tertiary | the same five shapes with `secondary` / `tertiary` |
| Surfaces | `--fp-surface`, `--fp-on-surface`, `--fp-surface-variant`, `--fp-on-surface-variant`, `--fp-surface-container-lowest` … `--fp-surface-container-highest`, `--fp-surface-bright`, `--fp-surface-dim` |
| Background & error | `--fp-background`, `--fp-on-background`, `--fp-error`, `--fp-on-error`, `--fp-error-container`, `--fp-on-error-container` |
| Lines | `--fp-outline`, `--fp-outline-variant`, `--fp-scrim` |
| Flight rules | `--fp-vfr-container` / `--fp-vfr-on-container`, and the same for `mvfr`, `ifr`, `lifr`, `unknown` |
| Sky scenery | `--fp-sky-day-low`, `--fp-sky-day-high`, `--fp-sky-day-cloud-body`, `--fp-sky-day-cloud-edge`, `--fp-sky-day-convective`, the same for `twilight` and `night`, plus `--fp-sky-celestial-sun`, `--fp-sky-ground-dry`, `--fp-sky-sock-band` |

**Shape** — `var(--fp-shape-extra-small | small | medium | large | large-increased |
extra-large | extra-large-increased | extra-extra-large)`, 4px to 48px. Cards use
`large-increased`; sheets use `extra-large-increased`.

**Type** — one class per slot: `fp-type-display-large`, `fp-type-headline-large |
-medium | -small`, `fp-type-title-large | -medium | -small`, `fp-type-body-large |
-medium | -small`, `fp-type-label-large | -medium | -small`. Each sets size, line
height, tracking, weight and numeric variant together.

**Motion** — `var(--fp-motion-<token>-duration)` and `var(--fp-motion-<token>-easing)`
for `spatial`, `spatial-fast`, `spatial-slow`, `effects`, `effects-fast`,
`effects-slow`. The easings are the real spring curves, sampled. **Spatial for
anything that moves or resizes; effects for fades, colour and elevation.** Spatial
springs overshoot — that overshoot is the character — and a spatial spring on a
colour fade reads as a flicker.

A single transition often uses **both**: `RouteCard`'s entrance fades on the
effects spring while it rises on the spatial one, because the alpha must not
overshoot and the movement should. Give a card its list position as `enterIndex`
and it staggers 30 ms per row, **capped at eight** — past that rows are simply
there, since a stagger explains where a new list came from but explains nothing
about row 63 of a batch the user is already scrolling towards. `replacing` makes it
arrive from the end edge instead, because the card it replaced was swiped towards
the start edge. Anything staged or infinite — stagger, shimmer, flutter — switches
**off** under `prefers-reduced-motion` rather than shortening.

Ready-made surfaces: `fp-card`, `fp-surface`, `fp-surface-container`,
`fp-surface-container-high`, `fp-surface-container-low`, `fp-button`,
`fp-button--tonal`, `fp-button--text`.

## Phone and tablet are one screen, not two

Every screen takes `layout="phone"` (default, 360 × 800) or `layout="tablet"`
(1280 × 800). It is a width, not a variant — the app has one set of screens that
reflow, so do not design a parallel tablet screen.

Three things change, and all three are decisions rather than defaults:

1. **The bottom bar becomes a rail, and the rail carries five destinations where
   the bar carries four.** Settings is somewhere you go once and come back from, so
   on a phone it lives in the app bar rather than taking a fifth of the bar; a rail
   has room, so it gets a permanent home. The rail also never auto-hides — the bar
   retracts on scroll because a horizontal bar is the only form that gives height
   back, and a rail costs width, which a wide window has spare.
2. **Plan, Fleet and Logbook become list/detail.** Pass a `detail` — a
   `RouteDetailPane`, `FleetDetailPane` or `FlightDetailPane` — and the screen puts
   it beside the list. Selecting a row replaces that pane **in place**, so a pane
   change is a fade-through and never a slide: a slide would claim a direction the
   selection does not have.
3. **Content is width-capped, never stretched.** `fp-content-cap` (640px) and
   `fp-content-cap--wide` (840px, for the detail screens and the logbook, whose
   rows are less dense). Past the cap a single column stops helping — a filter
   field 390px wide to say "Any", a card whose map is mostly ocean — so the extra
   width becomes margin. **It is deliberately not a grid**; making it one is a real
   design decision, not a free win.

```tsx
<PlanScreen layout="tablet" routes={routes} detail={<RouteDetailPane … />} />
```

## Five rules this app does not bend

1. **Flight-rules colours are semantic and never re-themed.** VFR green, MVFR blue,
   IFR red, LIFR magenta are standard chart colours; a pilot reads the colour
   before the letters. They live outside the Material scheme and Cockpit shares the
   dark set unchanged. Never tint them to match an accent, and never use them for
   anything that is not a flight category.
2. **The system bars stay empty.** The window is edge to edge and the status and
   navigation bars are transparent — content scrolls up under the clock and down
   under the gesture handle. Do not paint a bar, a scrim or a gradient there.
   `PhoneFrame` encodes this; build screens inside it.
3. **Every figure is tabular and reads left to right.** ICAO codes, distances,
   runway lengths, headings and times are chart figures. The `label`, `title`,
   `headline` and `display` classes already carry tabular figures; body text
   deliberately does not.
4. **`EmptyState` and `ErrorState` are not interchangeable.** One is the app
   working and waiting; the other is the app having failed. Rendering them alike
   teaches people to ignore the message.
5. **`RouteMap` and `SkyProfile` take data, not decoration.** The map takes
   coordinates and projects real geography; the profile takes a reported sky and
   puts every deck at its true altitude. Do not put a scrim over the map — land at
   8 % is what makes one unnecessary — and do not draw a sun on an IFR field: in
   this system the category is a consequence of the geometry.

## Where the truth lives

- `_ds/<folder>/styles.css` and its imports — every token, in one place. Read it
  before inventing a value.
- `components/<group>/<Name>/<Name>.prompt.md` — per-component usage, with the
  reasoning behind the API.
- Screens (`PlanScreen`, `FleetScreen`, `LogbookScreen`, `StatsScreen`,
  `AirportsScreen`, `AirportDetailScreen`, `RouteDetailScreen`, `SettingsScreen`)
  are the app as it exists. Start a concept from the closest one.

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

Library components for the controls; tokens and type classes for your own layout.

## This mirror is downstream of Kotlin

The app is written in Kotlin and Jetpack Compose. Every colour, type slot, corner
radius, Expressive shape and motion spring here is generated from that source, so
this library follows it and never the other way round. A concept designed here
comes back to the app as **intent** — a hierarchy, a colour role, a spacing rhythm
— never as pixel values transplanted into Compose.
