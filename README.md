# Flight Planner for Android

A native Android app for flight-simulator enthusiasts. It generates realistic, flyable routes
between real-world airports — constrained by your aircraft's range and by runway length versus its
takeoff distance — shows the great-circle route on a 3D globe, reports the weather at both ends,
and keeps a logbook of what you have flown. There is a **Wear OS app** too: one route filling the
round face, a turn of the bezel for the next, and a tap to open it on the phone — plus a
"Today's challenge" tile.

Everything runs **on device**. No server, no account, no simulator install required: the airport
database ships with the app.

This is a native Kotlin fork of the Rust desktop application
[daanbouwman19/flight-planner](https://github.com/daanbouwman19/flight-planner). It targets
functional parity with that app's GUI; the implementation is a ground-up rewrite.

## Status

All eight milestones are built. See `docs/` for the plans — [PLAN.md](docs/PLAN.md) for the
architecture and the parity matrix, [UI-PLAN.md](docs/UI-PLAN.md) for the task-by-task history.

| Milestone | State |
| --- | --- |
| M0 — build skeleton | done |
| M1 — airport database ETL + Room storage | done |
| M2 — route generation engine | done |
| M3 — end-to-end usable app | done |
| M4 — fleet / airports / statistics screens | done |
| M5 — METAR weather | done, then the visual layer was rebuilt as the Sky Profile — see [WEATHER-PLAN.md](docs/WEATHER-PLAN.md) |
| M6 — 3D globe | done, overhauled 2026-09-07 |
| M7 — polish, two widgets, shortcuts, screenshot goldens, R8 | done; the Play listing was dropped by decision |
| Wear OS app | running on a watch — see [WEAR-PLAN.md](docs/WEAR-PLAN.md) |

Two things are worth knowing before reading the code. Cold start is held under 500 ms and measured
with `:macrobenchmark` rather than by hand (166 ms median on an SM-S942B). And 161 screenshot
goldens are part of `check`, so any visual change fails the build until they are re-recorded and
the diff has been looked at. [CLAUDE.md](CLAUDE.md) states the invariants that a compiler cannot
see; three of them are enforced by a `checkInvariants` task wired into `check`.

## Building

Requirements: JDK 17+ and the Android SDK (platform 37). Everything else comes from the Gradle
wrapper.

```bash
./gradlew :app:assembleDebug     # the phone app
./gradlew :wear:assembleDebug    # the watch app, a separate APK for a separate device
./gradlew :core:routing:test     # the fastest, highest-value test suite
./gradlew check                  # everything: tests, lint, the asset verifiers, the invariants
```

`check` also runs `checkInvariants` and compares 161 screenshot goldens, so a visual change fails
the build until `./gradlew :app:recordRoborazziDebug` is run **and the diff has been looked at**.

`local.properties` must point at your SDK, and may carry the globe's imagery key:

```properties
sdk.dir=/path/to/Android/Sdk
# Optional. An ArcGIS Location Platform API key gives the 3D globe Esri World Imagery to z18.
# Without it the build falls back to NASA GIBS Blue Marble, which stops sharpening at z8.
arcgis.apiKey=...
```

The key can also come from the `ARCGIS_API_KEY` environment variable, which is how a CI build
gets it. It is never committed.

## Architecture

| Module | Type | Contents |
| --- | --- | --- |
| `:app` | Android application | Phone Compose UI, ViewModels, navigation, widgets, DI graph |
| `:wear` | Android application | The Wear OS app — a second APK, sharing everything below the UI |
| `:core:model` | pure JVM | Domain types |
| `:core:routing` | pure JVM | Airport index, spatial index, route generator, statistics, map projection |
| `:core:database` | Android library | Room databases, DAOs, asset bootstrap |
| `:core:network` | Android library | METAR client, tile HTTP client |
| `:core:handoff` | pure JVM | What the two apps agree on: the route URI the watch writes, the theme the phone publishes |
| `:core:designsystem` | Android library | Theme, semantic colours, shared components |
| `:feature:globe` | Android library | Filament-based 3D globe |
| `:macrobenchmark` | test-only | Cold start and scroll benchmarks, on the `benchmarkRelease` variant |
| `:tools:airportdb` | pure JVM, not shipped | CSV → SQLite ETL for the bundled airport database |
| `:tools:worldmap` | pure JVM, not shipped | GeoJSON → binary ETL for the bundled world outline |

`:core:model`, `:core:routing` and `:core:handoff` have no Android dependency on purpose: their
tests run on a plain JVM in milliseconds, no `Context` can leak into the domain layer, and both
APKs can depend on them.

The watch is not a smaller copy of the phone. Its Material surface is
`androidx.wear.compose:compose-material3`, a **different library** from the `androidx.compose.material3`
the phone is built on rather than a subset of it, so `:wear` shares no UI code with `:app` and
cannot see `:core:designsystem` at all. What it does share is everything below the drawing: route
generation, the great-circle maths and the map projection are the same code on both devices.

The 3D globe uses [Filament](https://github.com/google/filament) on its Vulkan backend. Filament
ships prebuilt native libraries, so this project contains no C or C++ and needs no NDK.

## The bundled airport database

`:tools:airportdb` turns the checked-in OurAirports snapshots into
`app/src/main/assets/databases/airports.db`. From 85,912 upstream airport rows and 48,165 runway
rows, **24,321 airports survive** (8,185 with a real ICAO code) with 58,221 runway ends — 6.1 MB
on disk, 3.3 MB compressed into the APK.

An airport is kept when it is a real airport (not a heliport, seaplane base, balloonport or a
closed field), has a four-character code, has valid coordinates, and has at least one open runway
of known length. That last condition is by far the strictest.

```bash
./gradlew :tools:airportdb:run                    # regenerate the database
./gradlew :tools:airportdb:verifyAirportAsset     # also runs as part of `check`
```

The verifier is not optional ceremony. Room validates a prepackaged database against an identity
hash derived from the entity definitions; if the two drift, Room refuses to open the database on
first launch, on every device, with no recovery path. The ETL therefore emits its DDL from Room's
own exported schema JSON, and the verifier fails the build if the shipped file no longer matches.
It also generates real routes for the shortest- and longest-range aircraft in the fleet, so the
dataset and the route generator cannot silently stop agreeing.

## The bundled world outline

`:tools:worldmap` turns the checked-in Natural Earth `ne_110m_land` snapshot into
`app/src/main/assets/maps/land.outline` — **122 rings, 4,601 points, 18.9 KB**, which is what a
route card draws its coastline from. Land polygons, not coastlines: a closed ring can be filled,
an open line cannot.

The device never sees GeoJSON. Coordinates are quantised to 16 bits (~610 m of longitude, ~305 m
of latitude — under a twentieth of a pixel at the closest a card zooms), rings carry explicit
offsets rather than a sentinel coordinate, and loading is a file read plus two array copies.

```bash
./gradlew :tools:worldmap:run                     # regenerate the outline
./gradlew :tools:worldmap:verifyWorldOutline      # also runs as part of `check`
```

The verifier does the thing a decode check cannot: it asks whether eleven unmistakable places —
the Sahara, the Amazon, East Antarctica, the central Pacific — come out as land or as water. A
transposed coordinate pair, a flipped sign or a ring table off by one all still decode, still
fill, and still look like *a* planet.

## Data and attribution

- Airport and runway data: [OurAirports](https://ourairports.com/data/) — released to the public
  domain.
- Land outline: [Natural Earth](https://www.naturalearthdata.com/) `ne_110m_land` — released to the
  public domain, so there is nothing to attribute on screen.
- Weather: [NOAA Aviation Weather Center](https://aviationweather.gov/data/api/) — no API key
  required. [AVWX](https://avwx.rest/) is available as an alternative provider.
- Satellite imagery: [Esri World Imagery](https://www.esri.com/en-us/legal/terms/data-attributions)
  through the ArcGIS Location Platform when the build has an API key — "Powered by Esri", with the
  data providers credited on the globe itself as Esri's terms require; otherwise
  [NASA GIBS](https://www.earthdata.nasa.gov/engage/open-data-services-software/earthdata-developer-portal/gibs-api)
  Blue Marble, which is public domain. The credit on the glass, in Settings and on the Licences
  screen follows whichever provider the build selected.

## Licence

MIT — see [LICENSE](LICENSE).
