# Flight Planner for Android

A native Android app for flight-simulator enthusiasts. It generates realistic, flyable routes
between real-world airports — constrained by your aircraft's range and by runway length versus its
takeoff distance — shows the great-circle route on a 3D globe, reports the weather at both ends,
and keeps a logbook of what you have flown.

Everything runs **on device**. No server, no account, no simulator install required: the airport
database ships with the app.

This is a native Kotlin fork of the Rust desktop application
[daanbouwman19/flight-planner](https://github.com/daanbouwman19/flight-planner). It targets
functional parity with that app's GUI; the implementation is a ground-up rewrite.

## Status

Under construction. See `docs/` for the milestone plan.

| Milestone | State |
| --- | --- |
| M0 — build skeleton | done |
| M1 — airport database ETL + Room storage | in progress |
| M2 — route generation engine | not started |
| M3 — end-to-end usable app | not started |
| M4 — fleet / airports / statistics screens | not started |
| M5 — METAR weather | not started |
| M6 — 3D globe | not started |
| M7 — polish, widget, release | not started |

## Building

Requirements: JDK 17+ and the Android SDK (platform 37). Everything else comes from the Gradle
wrapper.

```bash
./gradlew assembleDebug          # build the app
./gradlew :core:routing:test     # the fastest, highest-value test suite
./gradlew check                  # everything, including airport-asset verification
```

`local.properties` must point at your SDK:

```properties
sdk.dir=/path/to/Android/Sdk
```

## Architecture

| Module | Type | Contents |
| --- | --- | --- |
| `:app` | Android application | Compose UI, ViewModels, navigation, DI graph |
| `:core:model` | pure JVM | Domain types |
| `:core:routing` | pure JVM | Airport index, spatial index, route generator, statistics |
| `:core:database` | Android library | Room databases, DAOs, asset bootstrap |
| `:core:network` | Android library | METAR client, tile HTTP client |
| `:core:designsystem` | Android library | Theme, semantic colours, shared components |
| `:feature:globe` | Android library | Filament-based 3D globe |
| `:tools:airportdb` | pure JVM, not shipped | CSV → SQLite ETL for the bundled airport database |

`:core:model` and `:core:routing` have no Android dependency on purpose: their tests run on a
plain JVM in milliseconds, and no `Context` can leak into the domain layer.

The 3D globe uses [Filament](https://github.com/google/filament) on its Vulkan backend. Filament
ships prebuilt native libraries, so this project contains no C or C++ and needs no NDK.

## Data and attribution

- Airport and runway data: [OurAirports](https://ourairports.com/data/) — released to the public
  domain. Regenerate the bundled database with `./gradlew :tools:airportdb:run`.
- Weather: [NOAA Aviation Weather Center](https://aviationweather.gov/data/api/) — no API key
  required. [AVWX](https://avwx.rest/) is available as an alternative provider.
- Satellite imagery: attribution is rendered on the globe and depends on the selected tile
  provider.

## Licence

MIT — see [LICENSE](LICENSE).
