# Flight Planner for Android — native Kotlin fork

## Context

`flight-planner` (this repo) is a Rust desktop app for flight-sim enthusiasts: it generates
flyable routes between real airports, constrained by aircraft range and by runway length vs. the
aircraft's takeoff distance, logs which airframes you have flown and where, and shows statistics.
It ships as an egui desktop binary, a CLI, and a WASM frontend backed by an axum REST server, and
it requires the user to supply an `airports.db3` extracted from their simulator.

The user wants a **separate, ground-up native Android app**: its own repository, 100% Kotlin,
fully offline on-device data, a redesigned storage stack, a modern-2026 Compose UI with full
design freedom, and a 3D map of the route.

**The bar is functional parity.** Every capability the desktop app offers a user must exist in the
Android app. The *technical* implementation is completely reworked — different language, different
database, different renderer, different UI — but no feature is dropped. The CLI, the axum server
and the WASM frontend are *interfaces*, not features; every capability they expose also exists in
the GUI, so they are not carried over.

The outcome: an installable Android app that, on first launch with no PC, no server and no
simulator install, does everything the desktop app does.

## Decisions locked with the user

| Question | Decision |
| --- | --- |
| Repo layout | **Separate forked repo** — new project, not a module here |
| Language | **100% Kotlin.** No NDK, no JNI, no C++ written by us |
| Data | **Fully on-device.** No server, no REST backend. Storage redesigned from scratch |
| Airport source | **Bundle the OurAirports public-domain dataset** as a prebuilt SQLite asset |
| 3D map | **Google Filament** (Kotlin API, Vulkan backend) rendering a custom quadtree tile globe |
| UI | **Fully native Compose, modern 2026 design.** Complete design freedom |
| Scope | Functional parity with the desktop GUI, all in v1 |

On the graphics choice: Vulkan is now Android's preferred API and OpenGL ES is officially legacy,
but Vulkan has **no Java/Kotlin binding** — it is NDK-only. Filament resolves that: it is Google's
real-time renderer with official Kotlin bindings on Maven Central and a Vulkan backend, shipping
prebuilt native libraries so no C++ is written here. Cost: ~3–4 MB of APK and a `matc` material
compile step.

---

## 1. Feature parity matrix

This is the acceptance checklist. Left = desktop capability (with source of truth); right = where
it lives on Android.

### Route generation

| Desktop | Android |
| --- | --- |
| Random routes across the fleet, 50 per batch (`GENERATE_AMOUNT`, `routes.rs:33`) | Plan screen, `Mode.AllAircraft` |
| Routes restricted to **not-flown** aircraft (`generate_random_not_flown_aircraft_routes`) | Plan screen, `Mode.NotFlownOnly` |
| Routes for one **selected aircraft** (`generate_routes_for_aircraft`) | Plan screen, `Mode.Specific(id)` |
| **Locked departure airport**, or random when unset | Departure chip; clearable |
| Departure ICAO validation — generation disabled with an explanatory tooltip | Generate disabled + inline helper text |
| Regenerate automatically on selection change | `flatMapLatest` on the request flow |
| Infinite scroll — "load more routes" | `LazyColumn` paging, appends 50 |
| Runway constraint: both ends' longest runway ≥ takeoff distance (m→ft ×3.28084) | Ported verbatim (§4) |
| Range constraint: great-circle distance < aircraft range | Ported verbatim (§4) |

### Airports

| Desktop | Android |
| --- | --- |
| `Airports` mode — browse the complete database, paginated | Airports screen |
| `RandomAirports` mode — "Get random airports", batch of 50 | Airports screen, **Random** action |
| Columns: ICAO · Name · Longest runway | Airport rows |
| Runways: ident, true heading, length, width, surface (`format_runway`) | Airport detail, with a runway diagram |
| Airport elevation | Airport detail + route detail |
| Lookup by ICAO (`get_airport_by_icao`) | O(1) via the in-memory ICAO map |

### Fleet

| Desktop | Android |
| --- | --- |
| List all aircraft: manufacturer · variant · ICAO code · range · category · cruise speed · date flown | Fleet screen, grouped by category |
| Toggle flown status, stamping `date_flown` | Row toggle, same stamping |
| **Mark all aircraft as not flown**, behind a confirmation | Overflow action + confirm dialog |
| Add a new aircraft (`NewAircraft` / `add_aircraft`) | Add/edit airframe form |
| Import fleet from `aircrafts.csv` (116 rows, 12 categories) | Bundled seed on first run **+** SAF import/export |
| Not-flown count (CLI header) | Badge on the Fleet tab and the Not-flown chip |

### History and statistics

| Desktop | Android |
| --- | --- |
| History list: aircraft · from · to · date, newest first, paginated | Logbook, grouped by month |
| **Mark route as flown** → writes history, flips the flown flag, stamps the date | Route detail action + swipe on a card |
| Manually add history: aircraft + departure + destination via searchable pickers | Add-flight sheet, three pickers + date |
| History row opens the same detail view as a route | ✓ |
| Statistics: all nine `FlightStatistics` fields | Stats screen — **tie-break rules ported exactly**: alphabetically-first ICAO wins ties, lowest aircraft id wins ties, shortest uses strict `<` (first wins), longest uses `>=` (last wins) |

### Search

| Desktop | Android |
| --- | --- |
| Scored search over the currently displayed list — code match scores 2, name/manufacturer/variant/category/date/runway scores 1, sorted by score (`TableItem::search_score_optimized`) | `SearchScorer` in `:core:routing`, ported field-for-field for the fleet and the logbook. **Airport search deliberately diverges**: `AirportSlotSearch` ranks exact code, then prefix, then substring, then name/municipality, breaking ties by size. The desktop feeds a sortable table, where rank only has to be defensible; a type-ahead has no re-sort, so rank is the interface |
| Clear-search affordance | Trailing clear icon |
| Searchable, paginated dropdowns for aircraft and departure | Full-screen `SearchBar` with ranked type-ahead |
| "No results" state distinct from "no data" | Both, per screen |

### Route detail

| Desktop (`route_popup.rs`) | Android |
| --- | --- |
| Copyable `ICAO to ICAO` heading | ✓ as a heading; the *copy* is the Copy plan action, which copies the same summary the desktop does |
| Distance in NM | ✓ |
| Estimated flight time from cruise speed, 300 kt fallback (`calculate_flight_time`) | ✓ ported incl. the 60-minute rollover |
| Copy route summary with "Copied!" feedback | ✓ — no in-app confirmation, because Android draws its own the moment anything is copied |
| Aircraft manufacturer + variant | ✓ |
| **3D globe**: great-circle arc, green DEP / red DEST markers, recenter, compass reset | §5 |
| Per-airport elevation with a Google Maps link | ✓ |
| SkyVector link · SimBrief link | ✓ `ACTION_VIEW` intents |
| METAR both ends: flight rules + raw text, or an error line | ✓ plus a decoded view |
| Mark as flown | ✓ primary action |

### Weather

| Desktop | Android |
| --- | --- |
| METAR per airport, cached in SQLite (`metar_cache`) | Room `metar_cache` in the **user** DB, ~15 min TTL |
| Flight rules VFR/MVFR/IFR/LIFR + descriptions, colour-coded | `FlightRules` ported; semantic chart colours |
| **Inline Dep/Dest rules on every route row**, bulk prefetch, cancellable | Badges on route cards — **one batched request for up to 50 stations**, not 50 requests |
| AVWX API key setting (masked, show/hide, copy, clear, link) | Settings — retained, but **NOAA is the keyless default** (§6) |

### Shell

| Desktop | Android |
| --- | --- |
| Toast notifications (`ToastManager`) | Snackbars |
| Per-mode empty states with a call to action | Per-screen empty states, same copy |
| Loading state during generation | Progress + disabled Generate |
| Startup loading screen while DB/index builds | Splash keep-on-screen (capped ~800 ms) then skeletons |
| Missing-database fatal warning window | N/A — the dataset ships with the app |
| Column resizing · keyboard shortcuts · window sizing | N/A on touch — deliberately not carried over |

---

## 2. Repository and build setup

New repo `J:\projects\flight-planner-android` → `git@github.com:daanbouwman19/flight-planner-android.git`,
MIT, appId `com.github.daanbouwman.flightplanner`. Git LFS for `*.db` and screenshot goldens.

**SDK levels**: `minSdk 36` (**Android 16**), `targetSdk 36` (Play requires it from 31 Aug 2026),
`compileSdk 37`.

The floor was originally 28 to maximise reach. It is now 36 because this is a new app for modern
devices with a small user base, built in a design language that assumes a modern platform. The
trade is install base for a simpler app: dynamic colour, predictive back, edge-to-edge
enforcement, per-app language, Vulkan 1.3 and all of `java.time` are unconditionally present, so
none of them needs a `Build.VERSION.SDK_INT` guard, a compat wrapper or a fallback branch. That
matters more than it sounds — a guard for an API level below `minSdk` is not merely redundant, it
is a branch no device this app can run on will ever take, so it cannot be tested and quietly rots.
Raising the floor later would be a breaking change for installed users; doing it now costs nothing.

**Toolchain** — pin every one of these from live Maven metadata on day one rather than trusting
this table: AGP 9.2.x · Gradle 9.x with configuration cache · Kotlin 2.3.x (JVM target 17) · KSP
matched exactly to the Kotlin version · Compose BOM 2026.08.x · Hilt · OkHttp 5 + Retrofit 3 +
kotlinx-serialization · Coil 3 (for ordinary bitmaps only, **not** tiles).

**Room 2.8.x, not Room 3.** Room 3.0.1 went stable in late July 2026, but the prepackaged-asset
path (`createFromAsset` plus the `room_master_table` identity hash) is the single most
crash-prone part of this build (§3.1), and it is well-trodden on Room 2 and unverified on Room 3.
Ship v1 on 2.8.x with all DAOs suspend/Flow-only, so the `androidx.room` → `androidx.room3`
migration is a package rename once the asset path is proven there.

Use `androidx.sqlite:sqlite-bundled` (`BundledSQLiteDriver`): identical SQLite version, collation
and query planner on every OEM, deterministic behaviour. ~1.5–2 MB native per ABI, absorbed by AAB
ABI splits. The spatial design (§4.2) deliberately avoids depending on `SQLITE_ENABLE_RTREE`,
which we cannot confirm is compiled in.

**Navigation Compose 2.9.x with `@Serializable` type-safe routes**, not Navigation 3 (still
alpha). Keep routes in a sealed hierarchy so a Nav3 move is mechanical.

**Modules** — a `build-logic` composite build supplies convention plugins
(`flightplanner.android.application`, `.android.library`, `.android.library.compose`,
`.jvm.library`, `.android.room`, `.android.hilt`) so compileSdk/JVM target/test config live in one
place.

| Module | Type | Why separate |
| --- | --- | --- |
| `:app` | Android app | DI graph, `MainActivity`, NavHost, all screens under `feature/*` packages |
| `:core:model` | **pure JVM** | Domain types. No `Context` can leak in; tests are JVM-fast |
| `:core:routing` | **pure JVM** | `AirportIndex`, `LatBandIndex`, `GreatCircle`, `RouteGenerator`, `SearchScorer`, statistics reference. The crown jewel — sub-second TDD and a JMH benchmark |
| `:core:database` | Android lib | Two Room DBs, DAOs, asset opening, seeding. Isolated because Room's KSP round-trip is the slowest thing in the build |
| `:core:network` | Android lib | METAR client, tile HTTP client, OkHttp `Cache` config |
| `:core:designsystem` | Android lib | Theme, semantic flight-rules colours, shared atoms |
| `:feature:globe` | Android lib | Filament renderer + Compose host — a different toolset and failure mode from the rest of the UI, and its math half is unit-testable |
| `:tools:airportdb` | **pure JVM, not shipped** | The ETL. Never on the app classpath |
| `:microbench` / `:macrobenchmark` | benchmark | Standard androidx.benchmark layout |

**Deliberately not split**: the six screens. Feature-per-module buys enforced boundaries, but with
one developer and six screens sharing a design system the Gradle and navigation-plumbing cost
exceeds the benefit. Split a feature out the day it passes ~1,500 lines.

---

## 3. Data layer

### 3.1 The airport database is built, not bundled raw

`:tools:airportdb` is a JVM `application` reading checked-in snapshots of OurAirports'
`airports.csv` and `runways.csv` — **public domain**; credit it in About anyway. Run manually
(`./gradlew :tools:airportdb:run --args="…"`), **not** wired into `assemble`: upstream changes
weekly, and a network fetch in the build is a reproducibility and CI liability. The generated
`.db`, its `.sha256`, and a `dataset.properties` (source URL, upstream `Last-Modified`, row
counts) are all checked in.

**Measured against live data (2026-08-16): `airports.csv` 85,912 rows, `runways.csv` 48,165 rows.**
After filtering:

| Tier | Airports | Runway rows |
| --- | --- | --- |
| Real `icao_code` only | 8,190 | 11,029 |
| \+ 4-letter alphabetic `ident` | 13,227 | 16,879 |
| \+ any 4-char alphanumeric `ident` (FAA local codes) | **24,329** | **29,150** |

**Ship the 24,329 tier with a `has_icao` column and a Settings toggle defaulting to "ICAO airports
only."** Strict ICAO is what SimBrief and most sims accept; the permissive tier gives far more GA
and bush variety for those who want it. This costs one column and one boolean and settles the
question either way.

**Row filters**, each with a logged reject count:
1. `type ∉ {large_airport, medium_airport, small_airport}` — drops heliports (23,163), closed
   (13,437), seaplane bases (1,274), balloonports (61). **The real type string is `"closed"`, not
   `"closed_airport"` as OurAirports' own data dictionary claims.**
2. No usable runway: every runway row closed or with blank/zero `length_ft`.
3. No code: blank `icao_code` **and** `ident` failing `^[A-Z0-9]{4}$`.
4. Blank or out-of-range coordinates.
5. Duplicate resulting `icao` — keep the largest size class, then the longest runway; log collisions.

**Parse by column name, never by position** — the real `airports.csv` header order
(`…,scheduled_service,icao_code,iata_code,gps_code,local_code,…`) does not match the published
data dictionary.

**Schema** — `airports`: `id`, `icao` (unique), `has_icao`, `ident`, `name`, `lat`, `lon`,
`elevation_ft`, `country`, `municipality`, `size_class`, `scheduled_service`, plus denormalised
`longest_runway_ft`, `runway_count`, `has_hard_surface`, `has_lighting`. `runways`: **one row per
runway end** (matching the Rust `Runways` table) with `ident`, `true_heading` (derived from the
ident when blank — `"09"` → 90°), `length_ft`, `width_ft`, `surface` (raw) + `surface_kind`
(normalised), nullable `lat`/`lon`/`elevation_ft` falling back to the airport's.

`surface_kind` needs a hand-written map over the ~50 commonest raw values (`ASP`/`ASPH`/`ASPH-G`/
`asphalt`/`CON`/`CONC`/`PEM` → HARD; `GRE`/`GRS`/`TURF` → GRASS; `GVL`/`GRVL` → GRAVEL; `WATER` →
WATER; …). The ETL must emit unmapped values sorted by frequency so the map stays maintainable.

**Post-processing**: insert airports **ordered by `longest_runway_ft` ASC** so the index build
scan is already sorted, create indices, `ANALYZE`, `PRAGMA journal_mode=DELETE`, `VACUUM`,
`PRAGMA user_version`.

**Making Room accept the asset — do this at M1, not later.** The tool reads Room's exported schema
JSON, emits each entity's `createSql` verbatim with `${TABLE_NAME}` substituted, emits the index
DDL, then writes:

```sql
CREATE TABLE room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT);
INSERT INTO room_master_table VALUES (42, '<schema.identityHash>');
```

Plus a `verifyAirportAsset` Gradle task wired into `check` that reopens the asset and fails the
build if the hash has drifted. Without it, a schema change ships and crashes on first open on
every device. This is the highest-severity technical risk in the project.

**Size**: ~24k airports + ~29k runways + indices ≈ **4–6 MB** uncompressed (~2 MB for the strict
tier), ~1.5–2 MB in the compressed APK, extracted in ~0.5–1.5 s behind the first-run screen. Ship
as a plain asset. **Do not use Play Asset Delivery** — it exists for 100 MB+ payloads and buys
nothing here.

### 3.2 Two databases, deliberately

- **`AirportDatabase`** — read-only, `createFromAsset("databases/airports.db")`,
  `BundledSQLiteDriver`, `fallbackToDestructiveMigration(dropAllTables = true)`. Destructive is
  *correct*: it is derived data, wholly replaceable, and a refresh is `version++` plus a new asset.
- **`UserDatabase`** — `aircraft`, `flight_log`, `metar_cache`. Real `Migration`s (and
  `@AutoMigration` where additive), `MigrationTestHelper` coverage for every step and the full
  chain. **Never destructive.**

`metar_cache` lives in the **user** DB — the Rust app puts it in the airport DB, where a dataset
refresh would wipe it. `flight_log` stores departure/arrival as ICAO text, not a cross-database
foreign key (impossible), and ICAO codes survive dataset refreshes; names resolve against the
in-memory index exactly as `load_history_data` does.

**`settings` becomes Preferences DataStore**, not a table. Eight key/value rows in SQL is strictly
worse than a typed Flow with atomic writes.

### 3.3 Seed fleet

`aircrafts.csv` (116 rows; Light 23, Narrow-body 17, Military 17, Regional 12, Wide-body 10,
Vintage 10, Business 8, Utility 6, Cargo 6, Sport 5, Amphibian 2, Supersonic 1; ranges 87–8900 NM)
ships at `assets/seed/aircrafts.csv`. Seeded in one transaction when the table is empty or
`seedVersion < BuildConfig.SEED_VERSION`. `takeoff_distance` stays in **metres**, converted at the
×3.28084 boundary exactly as in Rust. The same parser backs SAF import, so existing desktop CSVs
just work; export is offered too, since users tune range and takeoff distance per their sim.

### 3.4 Search: no FTS table

With ≤24k airports fully in memory, port the Rust ranked in-memory search instead: exact ICAO >
ICAO prefix > name prefix > name substring > municipality substring, top-K via a bounded min-heap.
Instantaneous, full ranking control, zero SQL. If a future dataset outgrows memory, use
`@Fts4(tokenizer = UNICODE61, tokenizerArgs = ["remove_diacritics=2"])` — **Room has no `@Fts5`
annotation**, so an FTS5 virtual table would sit outside the schema and break the identity-hash
validation just built.

---

## 4. Domain layer — the Kotlin port (`:core:routing`, pure JVM)

### 4.1 Struct-of-arrays index

```kotlin
class AirportIndex internal constructor(
    val size: Int,
    // ALL arrays co-sorted ASCENDING by longestRunwayFt — the load-bearing invariant
    val ids: IntArray, val icao: Array<String>, val name: Array<String>,
    val latDeg: FloatArray, val lonDeg: FloatArray,
    val latRad: FloatArray, val lonRad: FloatArray,
    val sinLat: FloatArray, val cosLat: FloatArray,
    val sinLon: FloatArray, val cosLon: FloatArray,
    val elevationFt: IntArray, val longestRunwayFt: IntArray,
    val flags: IntArray,                 // hasIcao | hardSurface | lighted | sizeClass
    private val icaoToSlot: Map<String, Int>,
    private val bands: LatBandIndex,
) {
    fun slotOfIcao(icao: String): Int
    fun firstSlotWithRunway(requiredFt: Int): Int   // Rust partition_point
}
```

SoA over `List<Airport>` because destination search probes `sinLat/cosLat/sinLon/cosLon` for up to
128 slots × 50 routes ≈ 6,400 times per batch — parallel `FloatArray`s are sequential loads with
no object headers and no GC pressure, versus 6,400 scattered dereferences. Keep it `internal` and
return value objects at the boundary.

**Measured footprint at N = 24,329**: numerics ≈ 1.07 MB, ICAO strings ≈ 1.2 MB, names ≈ 2.0 MB,
band index 200 KB → **≈ 4.5 MB** (≈ 1.6 MB for the strict tier). Negligible.

### 4.2 Spatial index: latitude bands, not an R-tree

```kotlin
internal class LatBandIndex(
    private val bandStart: IntArray,   // 181 entries, one per degree of latitude
    private val bandSlots: IntArray,   // N entries, counting-sorted
) { inline fun forEachInLatRange(minLat: Float, maxLat: Float, action: (slot: Int) -> Unit) }
```

O(N) counting-sort build, ~200 KB. A 200 NM query touches ~7 of 181 bands ≈ 950 visits. Porting
rstar would be ~400 lines of bulk-load and pointer chasing that only wins above ~10⁵ points; a
SQLite R\*Tree costs a driver round-trip per query and may not even be compiled in.

**Fix two real bugs in the Rust version while porting:**
1. **Antimeridian** — `get_random_destination_airport_fast` builds `[lon−r, lon+r]` with no wrap
   handling, so near ±180° the envelope is half empty and destinations silently skew. Split into
   two longitude intervals when the window crosses ±180°.
2. **Poles** — it clamps `cos(lat)` to 0.001, producing a half-width up to 1000°. If the half-width
   reaches 180°, drop the longitude test and rely on the Haversine check.

### 4.3 Math

`EARTH_RADIUS_NM = 3440.0f`, `METERS_TO_FEET = 3.28084`, `DEFAULT_CRUISE_KNOTS = 300.0`. Port the
identity verbatim — `a = 0.5·(1 − (sinLat₁·sinLat₂ + cosLat₁·cosLat₂·cos Δlon))` with
`cos Δlon = sinLon₁·sinLon₂ + cosLon₁·cosLon₂` — keeping `Float`; the Rust comment is right that
0.001 NM precision suffices. Also port `calculate_haversine_threshold` and `calculate_flight_time`,
and add `initialBearingDeg` (new, for the detail screen).

### 4.4 Generator

```kotlin
class RouteGenerator(private val index: AirportIndex, private val config: RouteGeneratorConfig) {
    suspend fun generate(request: RouteRequest, random: Random): List<GeneratedRoute>
}
data class RouteGeneratorConfig(
    val rejectionSamplingThresholdNm: Int = 500,
    val rejectionSamplingAttempts: Int = 128,
)
data class RouteRequest(
    val fleet: List<AircraftSpec>,        // already filtered by mode
    val amount: Int = 50,
    val lockedDepartureIcao: String? = null,
    val minDistanceNm: Int? = null,       // new — kills the 20 NM hops
    val requireHardSurface: Boolean = false,
    val icaoOnly: Boolean = true,
)
```

Faithful to `routes.rs` + `airport.rs`: resolve the locked departure once via `icaoToSlot`;
precompute per aircraft a `Candidate(spec, startSlot = firstSlotWithRunway(...))` hoisted out of
the loop; per route pick a candidate, pick a departure from `[startSlot, N)`, then the **hybrid**
destination search — rejection sampling (≤128 attempts, latitude-delta pre-filter, cached-trig
threshold test) when range ≥ 500 NM, otherwise a band-index scan.

The fallback scan uses **explicit reservoir sampling**, reproducing `rand`'s `IteratorRandom::choose`
semantics with zero allocation:

```kotlin
var chosen = -1; var seen = 0
bands.forEachInLatRange(latMin, latMax) { slot ->
    if (slot != dep && longestRunwayFt[slot] >= requiredFt && inLonWindow(slot) &&
        GreatCircle.withinThreshold(index, dep, slot, threshold)) {
        seen++; if (random.nextInt(seen) == 0) chosen = slot
    }
}
```

A naive `.filter{}.random()` would allocate a list per route.

**Do not port rayon's fan-out.** One route is a binary search plus ≤128 samples of ~10 flops; the
whole 50-route batch is well under a millisecond, so dispatching 50 coroutines costs more than the
work. Run the batch as one `withContext(Dispatchers.Default)` with `ensureActive()` between routes
for cooperative cancellation. If a future dataset changes that arithmetic, chunk it with a
per-chunk `Random(baseSeed + chunkIndex)` so results stay reproducible.

**Determinism**: `Random` is a parameter everywhere — never `Math.random()`, never
`ThreadLocalRandom`. Production passes `Random.Default`, tests `Random(42)`, and the daily-challenge
widget `Random(LocalDate.now().toEpochDay())`.

**Statistics implemented twice on purpose**: SQL aggregates in `FlightLogDao` returning `Flow`s
(reactive, never loads the whole log) as the production path, and a pure-Kotlin
`FlightStatisticsCalculator` mirroring `StatsAccumulator` used only to cross-check it in tests.

`SearchScorer` is a field-for-field port of `TableItem::search_score_optimized` over all four item
types, with the same 2-for-code / 1-for-other scoring and score-descending sort. **Airport search
does not use it.** `AirportSlotSearch` ranks over the index arrays in four tiers — exact code,
code prefix, code substring, then name or municipality — with ties broken by airport size. The
ported scoring produced an unusable picker: with no notion of a prefix and ties breaking by slot
order, which is ascending runway length, typing `EHA` put Schiphol fourth behind two airports whose
codes merely contain those letters. A table can be re-sorted by the user; a type-ahead cannot.

---

## 5. The 3D globe — Filament (`:feature:globe`)

### 5.1 Engine and hosting

```kotlin
val engine = Engine.Builder().backend(Engine.Backend.VULKAN).build()
```

**Android's Filament default backend is OPENGL — Vulkan must be requested explicitly.** Verify in
M6 that the prebuilt `filament-android` AAR is compiled with `FILAMENT_SUPPORTS_VULKAN`; if it is
not, fall back to `Backend.DEFAULT` (which is exactly the GLES path, transparently Vulkan-backed
via ANGLE on Android 15+) and revisit. Gate the choice behind a Settings developer toggle so a
device-specific Vulkan driver bug is one tap away from a workaround.

Artifacts: `com.google.android.filament:filament-android` (+ `filament-utils-android` for the
Kotlin math helpers). **Not `filamat-android`** — that is the runtime shader compiler and it is
large; materials are compiled offline instead (§5.3).

Hosting: `UiHelper` driving a `SurfaceView` inside an `AndroidView`, which is Filament's documented
path — `UiHelper` owns surface creation/resize/destroy and you create the `SwapChain` in
`onNativeWindowChanged`. A `Choreographer` callback drives `renderer.beginFrame/render/endFrame`.

The SurfaceView caveats are real and are design constraints, not bugs to discover late:
- No rounded-corner clipping, no blur, and **no geometric shared-element morph into the globe**.
  Design the card→detail transition as a **crossfade from a static equirectangular preview**.
- `holder.setFormat(PixelFormat.TRANSLUCENT)`, leave the surface **below** the window (do not call
  `setZOrderOnTop`), so Compose overlay chrome — attribution, compass, ± zoom, DEP/DEST labels —
  draws on top naturally.
- `GraphicsLayer.toImageBitmap()` will not capture it, so "share a route image" needs `PixelCopy`
  or a Filament offscreen render target.

### 5.2 Files

```
:feature:globe/src/main/kotlin/…/globe/
  GlobeView.kt            @Composable host: AndroidView + overlay chrome + gesture layer
  GlobeSurface.kt         SurfaceView + UiHelper + SwapChain + Choreographer loop
  GlobeScene.kt           Engine/Scene/View/Renderer/Camera wiring, per-frame update
  Camera.kt               port of camera.rs + toViewProjection(): FloatArray(16)
  Quadtree.kt             port of quadtree.rs, Long-packed tile keys
  SpherePatchMesh.kt      six static grid VertexBuffers + IndexBuffers (substeps 16/12/8/6/5/4)
  TileAtlas.kt            one large Filament Texture + slot LRU
  TileRenderables.kt      pooled entities + MaterialInstances, one per visible tile
  TileKey.kt              (z,x,y) <-> Long packing
  TileLoader.kt           coroutine fetch/decode pipeline
  TileProvider.kt         interface + EsriWorldImageryProvider (port of providers.rs)
  GreatCircleArc.kt       slerp, ~1 sample/degree, 10..100 steps (port of mod.rs)
  GlobeCameraState.kt     @Stable holder + Saver + AtomicReference<Camera> publish
  GlobeGestures.kt        Modifier.pointerInput gesture state machine
  materials/tile.mat, route.mat, rim.mat   -> compiled to .filamat by a Gradle task
```

### 5.3 Materials and geometry

Author `.mat` files and compile them with **`matc` in a Gradle task**, shipping `.filamat` assets —
this keeps the multi-megabyte `filamat-android` runtime compiler out of the APK. `tile.mat` uses
`shadingModel: unlit`, `blending: transparent`, `depthWrite: false`.

The Rust renderer tessellates each tile on the CPU every frame. **Do not port that.** Ship **one
static unit-grid `VertexBuffer` per substep count** (S ∈ {16,12,8,6,5,4} — the exact
`substeps_for(z)` ladder, < 3 KB total) and let the material's `vertex` block do the
inverse-Mercator and lat/lon→XYZ on the GPU, driven by per-tile material parameters
(`lonMin`, `lonSpan`, `tileYTop`, `tileYSpan`, `numTiles`, atlas UV rect, facing unit vector, cull
threshold). Zero per-frame geometry upload. The limb fade
(`clamp((dot(w, facing) − cullThreshold) * 5.0, 0, 1)`) reproduces the Rust painter's semantics.

Also port `draw_globe_backdrop` (true-limb convex polygon, `#081426`), `draw_globe_rim`
(atmosphere glow + white outline from `limb_points()`), and the great-circle arc. The arc is a
**triangle-strip ribbon**, not `GL_LINE_STRIP` — line widths > 1 are unreliable on mobile GPUs.
Render DEP/DEST **labels in Compose**, projecting their world positions on the CPU each frame and
publishing screen coordinates to the overlay.

### 5.4 Texture memory — the number that governs the design

**The desktop's 512-texture LRU does not transfer: 512 × 256² × RGBA8 = 128 MB.** That is an
immediate OOM on a phone.

Sizing from first principles: a 1080×2400 screen at 1:1 texel density needs ≈ 40 tiles, roughly ×2
for the LOD transition band and horizon ⇒ ~96 visible.

- `MAX_VISIBLE_TILES` = 96 compact / 160 medium / 224 expanded, halved when
  `ActivityManager.isLowRamDevice`.
- **One 4096×4096 `Texture.InternalFormat.RGB565` atlas** holding 256 tile slots — 32 MB, one
  texture, one material, sub-image upload via `Texture.setImage` at a slot offset. 4096 is
  guaranteed on GLES 3.0 and Vulkan. Satellite imagery at 565 is fine; dither on decode.
- Pinned set: **z0–z3 = 85 tiles** prefetched at startup and never evicted, so a coarse ancestor
  always exists and the globe is never blank offline. (The Rust `BASE_LOD = 3` pins only 21; 85 is
  the better mobile choice because the pinned set *is* the offline fallback.)
- LRU over the remaining ~171 slots. On `onTrimMemory(UI_HIDDEN/COMPLETE)`, evict to the pinned set.

`best_texture(z,x,y)` → ancestor fallback with a cropped UV sub-rect: port verbatim. It is the
reason the globe never shows holes.

### 5.5 Fetch pipeline and disk cache

`TileLoader`: a LIFO `ArrayDeque<Long>` plus an in-flight set, drained by **4** coroutines on
`Dispatchers.IO.limitedParallelism(4)` — not the desktop's 8; OkHttp defaults to 5 connections per
host and mobile radios punish concurrency. LIFO so the current camera beats stale requests, with
the same "clear the queue past `PENDING_CAP`" pressure valve.

Decode with `BitmapFactory.Options { inPreferredConfig = RGB_565; inBitmap = pool.acquire() }` so
decode allocates nothing, then hand the buffer to Filament on the render thread.

**Disk cache: OkHttp's own `Cache`** (`256 MB` under `cacheDir/tiles`). ArcGIS tiles carry sane
`Cache-Control`/`ETag`, so conditional revalidation, LRU eviction, journal integrity and crash
safety all come free; a hand-rolled `DiskLruCache` reimplements it worse. Two interceptors: one
rewriting a minimum `max-age` (tiles are effectively immutable), and a
`ForceCacheOnFailureInterceptor` retrying with `FORCE_CACHE` on `IOException` so panning works
offline. **Do not use Coil for tiles** — the pipeline is bytes → pooled RGB565 bitmap → atlas
sub-image, not a Compose image loader.

`TileProvider` mirrors `providers.rs`, with `EsriWorldImageryProvider` using the same `{z}/{y}/{x}`
URL and the same required `ATTRIBUTION = "Imagery © Esri"`, rendered as an always-visible,
non-dismissible Compose `Text`. See §11 for the licensing caveat and the swap plan.

Offline: "Download tiles for this route" walks the quadtree along the great circle at z ≤ 8 and
warms the OkHttp cache from a `CoroutineWorker`.

### 5.6 Camera — one consistency rule

Port `Camera`, `CameraBasis`, `computeBasis()`, `focalPixels()`, `cullThreshold()`,
`screenToWorld()`, `screenToWorldClamped()`, `panTo()` (Newton, 4 iterations) and `latLonToWorld()`
verbatim. Feed Filament via `Camera.setCustomProjection()` + `setModelMatrix()`, built from **the
same `computeBasis()` call** used by the CPU-side `project()`.

**Non-negotiable invariant**: the CPU `project()` (used by the quadtree's screen-space-error metric
and by hit-testing) and the matrices handed to Filament must agree to sub-pixel accuracy. Lock it
with `CameraMatrixConsistencyTest` (§8). Every "the tap landed 40 px off" bug lives here.

### 5.7 Touch gestures

Put `Modifier.pointerInput` on a **transparent Compose `Box` above the `AndroidView`**, so gestures
live in Compose (velocity tracker, consumption, nested-scroll interop) and the surface stays a dumb
output device.

| Gesture | Behaviour |
| --- | --- |
| 1-finger drag | Grab-the-globe: on down `anchor = screenToWorldClamped(pos)`, on move `panTo(anchor, pos)` — the Rust `DragKind::Pan`, and it feels like spinning a physical globe |
| 1-finger fling | `VelocityTracker` → `AnimationState` with `exponentialDecay(frictionMultiplier = 0.6f)`, re-calling `panTo` each frame; any new down cancels |
| 2-finger pinch | `altitude *= 1/zoomChange` clamped to `[MIN_ALTITUDE, MAX_ALTITUDE]`, then re-pin the world point under the **centroid** — the Rust `handle_scroll` trick |
| 2-finger rotate | `bearing += rotationChange`; snap to 0 within ±3° with a haptic tick |
| 2-finger parallel vertical drag | `tilt = (tilt + dy·(π/3)/viewportH).coerceIn(0f, π/2.5f)` — same constants as `interaction.rs` |
| Double tap | `altitude *= 0.45f`, 300 ms `FastOutSlowIn`, centred on the tap |
| Two-finger tap | Zoom out one step |
| Compass / recenter | Animate bearing+tilt → 0, or re-fit the route, over 400 ms |

**Mode locking is essential.** Run a ~60 ms / 12 dp classification window on the second
pointer-down, then commit to exactly one of {pinch, rotate, tilt} until all pointers lift. Tilt is
chosen when `|rotationChange| < 1.5°` **and** `|zoomChange − 1| < 0.02` **and** both pointers move
the same vertical direction. Without the lock the camera jitters constantly — the single commonest
failure of hand-rolled map gestures. Consume events so the parent list doesn't steal the drag.

**Accessibility**: a SurfaceView is invisible to TalkBack, so provide
`Modifier.semantics { contentDescription = "3D globe, route EHAM to KJFK, 3,162 nautical miles"; customActions = [zoom in, zoom out, reset north] }` plus visible ± and compass buttons.
Honour reduce-motion (`ANIMATOR_DURATION_SCALE == 0`) by disabling inertia.

### 5.8 State across configuration changes

Truth lives in `GlobeCameraState`, held by the screen's ViewModel (survives rotation) and
`rememberSaveable`-backed by a `Saver` writing six floats (survives process death). The renderer
never owns camera truth: the UI thread writes an immutable `Camera` snapshot into an
`AtomicReference<Camera>` and the frame callback reads it exactly once — lock-free, no tearing.

---

## 6. UI — screens and a 2026 design direction

### 6.1 The core translation

The desktop's whole 250 px sidebar is a departure picker, an aircraft picker and three generate
buttons. On a phone that is **two chips and one segmented control** pinned above the list:

> `[ 📍 Anywhere ▾ ]  [ ✈ Any aircraft ▾ ]`
> `( Any | Not flown | This aircraft )`

Tapping a chip opens a full-screen `SearchBar` with the ranked in-memory type-ahead. The sidebar is
gone and nothing was lost.

The big table becomes a `LazyColumn` of **route cards**. A 7-column table is unreadable at 400 dp,
and a card can carry *more* information than the row did because it uses two dimensions. Keep the
table shape only on `WindowWidthSizeClass.Expanded`, where a `ListDetailPaneScaffold` list pane can
afford columns.

### 6.2 Design direction

**Material 3 Expressive** as the base — expressive shape scale with `MaterialShapes` morphing on
the generate FAB, `MotionScheme.expressive()` springs, `FloatingToolbar` for primary actions,
`ButtonGroup` for the mode selector, and the expressive `LoadingIndicator` instead of a spinner.
Expressive was still stabilising in 2026, so keep the theme entry point and any experimental
components behind a thin wrapper file that absorbs the churn.

**Dynamic color** from wallpaper, with a brand fallback seeded from avgas-blue and runway-marking
amber, plus a **"Cockpit"** theme (near-black + amber) for night flying.

**Flight-rules colours are semantic and must NOT be dynamic** — VFR green, MVFR blue, IFR red, LIFR
magenta are the standard chart colours. Define them in a `FlightRulesColors` CompositionLocal with
tone-mapped container/on-container pairs that pass 4.5:1 in both light and dark regardless of
wallpaper.

Edge-to-edge (`enableEdgeToEdge()`), globe under the status bar with a top scrim · predictive back
via NavHost plus a `PredictiveBackHandler` on the detail sheet · `NavigationSuiteScaffold` (bottom
bar → rail → drawer) and `ListDetailPaneScaffold` for Plan and Fleet, hinge-aware on foldables ·
`Modifier.sharedBounds` on the ICAO pair and aircraft name between card and detail ·
`Modifier.animateItem()` on list entrance · haptics on mark-as-flown, swipe threshold and
generate-complete.

### 6.3 Screens

1. **Plan** (start) — chips + segmented control, route cards. **As built**, and diverging from this
   paragraph in three places: there is no generate FAB (it was cut once the screen generated on
   open and pull-to-refresh existed), the sparkline became a full-bleed world map behind the card,
   and the desktop empty-state copy was replaced — it was the one sentence in the app that read as
   marketing. Card: aircraft + category, `EHAM → RJTT` in tabular figures at the card's two edges,
   distance + ETE in translucent chips, runway per end, dep/dest flight-rules slots reserved for
   Phase F, and the route drawn over a coastline as a cased line with a direction arrowhead. Swipe
   right = mark flown (haptic + undo snackbar); swipe left = replace that one, keeping the airframe
   and departure. Both are also custom accessibility actions. Pull-to-refresh regenerates; infinite
   scroll appends 50. Loading = skeleton cards.
2. **Route detail** — `ModalBottomSheet` on compact, detail pane on expanded. Hero globe (~60%
   height) with the arc and DEP/DEST pins; below: distance, est. time, initial/final bearing,
   elevations, longest runway + surface both ends, decoded METAR (wind, visibility, ceiling,
   temp/dew, altimeter) with the raw text in monospace; actions **Mark as flown**, **Copy plan**,
   **SkyVector**, **SimBrief**, **Google Maps**, **Share**.
3. **Logbook** — grouped by month with sticky headers, a summary strip (flights / NM / hours this
   year), swipe-to-delete with undo, FAB → manual entry sheet with three searchable pickers and a
   date picker, distance computed live as you pick.
4. **Fleet** — filter chips (All · Flown · Not flown · Category), rows with a flown toggle that
   stamps the date. Detail: per-airframe stats, "Generate routes for this aircraft", inline editing
   of range/cruise/takeoff distance. Overflow: reset all, import/export CSV, restore defaults.
5. **Airports** — ranked type-ahead browse plus the Random-50 action; rows show ICAO, name, country
   flag, longest runway, elevation. Detail: a small runway diagram (idents, true headings, surface,
   length), METAR, and "Fly from here" → sets the locked departure and jumps to Plan.
6. **Stats** — the nine `FlightStatistics` fields as an expressive dashboard: hero total distance
   with an animated count-up and an equivalence ("2.3× around the Earth"), monthly bar chart,
   top-aircraft list, a "places you've been" mini-globe with visited dots, longest/shortest cards.
7. **Settings** — theme, dynamic color, units (NM/km, ft/m), **ICAO-airports-only toggle**, weather
   provider (NOAA default / AVWX + key), Filament backend toggle, tile provider + offline download,
   dataset info (OurAirports version, row counts, public-domain notice), fleet import/export,
   licences.
8. **First run** — one screen while the asset DB is extracted and the fleet seeded, with a spinning
   globe as the loading art.

**Weather source default changes from AVWX to NOAA** —
`https://aviationweather.gov/api/data/metar?ids={ICAO}&format=json`, verified keyless and returning
`fltCat` directly along with `rawOb`, `clouds[]`, `visib`, `wdir`, `wspd`, `altim`. Implement
`deriveFlightRules(ceilingFt, visibilitySm)` locally for when `fltCat` is null, using the exact
thresholds in the Rust `FlightRules::description()`. AVWX stays as an optional provider with the
same masked key field, so nothing is lost — but the worst first-run papercut is.

**Widgets and shortcuts** — a **Glance** widget, "Today's challenge": one route seeded by
`LocalDate.toEpochDay()`, deterministic across the day, tap to open. The seeded RNG makes it nearly
free and it is the best retention feature available. Plus static shortcuts: "Generate route", "Log
a flight", "Last route". Skip a quick-settings tile.

---

## 7. State and concurrency

Per screen: `StateFlow<UiState>` via `stateIn(viewModelScope, WhileSubscribed(5_000), Initial)`,
`fun onEvent(e: Event)`, and one-shot effects through `Channel(BUFFERED).receiveAsFlow()` — not a
`SharedFlow`, whose replay causes duplicate snackbars on rotation.

The index is a process-scoped singleton, **not** a ViewModel:

```kotlin
@Singleton class AirportIndexProvider @Inject constructor(
    private val dao: AirportDao, @ApplicationScope private val scope: CoroutineScope,
) {
    private val deferred = scope.async(Dispatchers.Default, CoroutineStart.LAZY) { build() }
    suspend fun get(): AirportIndex = deferred.await()
    val state: StateFlow<IndexState>   // Idle | Loading(progress) | Ready | Failed
}
```

Built once, never released — 4.5 MB is far cheaper than a mid-session rebuild. Build with a single
`Cursor` pass writing straight into pre-sized arrays; never materialise `List<AirportEntity>`
(24k throwaway objects ≈ 2 MB of garbage). The ETL already inserts in `longest_runway_ft` order, so
the `ORDER BY` is free.

Warm it from `Application.onCreate` (or an `androidx.startup.Initializer`) so it overlaps first-frame
inflation, with `SplashScreen.setKeepOnScreenCondition { !ready }` capped at ~800 ms — past that,
show the app with skeletons rather than blocking. This is a deliberate improvement on the desktop's
blocking loading screen.

Route generation is a cancellable pipeline — the clean replacement for the Rust `AtomicU64`
generation counter:

```kotlin
requests.flatMapLatest { req -> flow { emit(generator.generate(req, Random.Default)) } }
        .flowOn(Dispatchers.Default)
```

**METAR batching**: NOAA accepts comma-separated `ids`, so the visible route list resolves flight
rules for up to 50 stations in **one** request instead of 50. Collect visible ICAOs from
`LazyListState`, `debounce(300)`, `distinctUntilChanged()`, chunk by 50, write through
`metar_cache`. A large improvement over the desktop's per-station fetch.

All DAO reads return `Flow`, so the UI is reactive with no manual refresh.

---

## 8. Testing

**`:core:routing` (JVM — fastest and most valuable)**
- `RouteGeneratorTest` on a synthetic 5,000-airport index with `Random(42)`: determinism (same seed
  ⇒ byte-identical output); every destination's longest runway ≥ `ceil(takeoffM × 3.28084)`; every
  distance ≤ range; departure ≠ destination; locked departure honoured and an unknown ICAO returns
  empty rather than throwing; not-flown mode yields only `flown == 0`; an impossible takeoff
  distance yields zero routes.
- `GreatCircleTest`: golden distances (EHAM–KJFK ≈ 3162 NM, KJFK–EGLL ≈ 3000 NM) within ±1 NM; port
  `test_haversine_threshold_consistency` verbatim; antimeridian pairs (NZAA–SCEL); polar pairs.
- `LatBandIndexTest`: for 1,000 random (centre, radius) queries the band index must return **exactly
  the same set** as a brute-force scan. This is the test that catches the antimeridian and pole bugs.
- `FlightStatisticsTest`: SQL aggregates vs. the pure-Kotlin reference, including every tie-break rule.
- `SearchScorerTest`: the 2/1 scoring and sort order for all four item types.
- `kotest-property` for the geometry invariants — real shrinking earns its keep here.

**Globe math (JVM — port the Rust tests directly, they are all pure math)**
`CameraTest` (nadir projects to centre; `panTo` pins a world point within 1 px at tilt 0 and 0.4;
`screenToWorld` round-trip < 1e-2; tilt/bearing behaviour; fast vs. slow basis agree) ·
`QuadtreeTest` (non-empty and within budget at every altitude/tilt; deeper when closer; leaves
unique; **every leaf's ancestors were requested**; nadir covered) · `GreatCircleArcTest` (90° arc →
91 samples; endpoints exact; every sample on the unit sphere) · **`CameraMatrixConsistencyTest`**
(1,000 random cameras × points: CPU `project()` vs. the matrices given to Filament, sub-pixel).

**`:core:database`**
`MigrationTestHelper` for every user-DB step and the full chain · DAO tests with
`inMemoryDatabaseBuilder` · an **asset integrity test** (Robolectric + `sqlite-bundled`, so it runs
on the JVM in CI) opening the shipped `airports.db` and asserting row counts match
`dataset.properties`, `longest_runway_ft > 0` everywhere, `icao` unique, every `runways.airport_id`
resolves, and the `room_master_table` hash matches the current schema. A bad ETL run can then never
ship.

**Compose UI tests** — `createAndroidComposeRule<MainActivity>()` + Hilt test rule + fakes. Test
behaviour, not pixels: generate → 50 cards; swipe-right → flown + undo snackbar; locked departure
filters results; predictive back dismisses the sheet; semantics exist. Prefer semantics matchers
over test tags.

**Screenshot tests — Roborazzi**, not Paparazzi (Paparazzi handles `AndroidView` and newer Compose
APIs poorly). Goldens per screen × {light, dark, dynamic} × {LTR, RTL} × {fontScale 1.0, 2.0} ×
{compact, medium, expanded}, gated by `verifyRoborazziDebug`. **The globe cannot be
screenshot-tested** — stub a placeholder behind the overlay; the renderer is covered by the math
tests plus a manual device smoke check.

**Benchmarks** — JVM JMH on `:core:routing` (`generate50Routes`, `randomDestination`,
`buildAirportIndex`) for sub-second algorithm feedback; on-device `:microbench` for the same three
(ART and HotSpot differ materially on float math); `:macrobenchmark` for cold start and frame
timing while flinging the list and spinning the globe, feeding a **Baseline Profile** — the largest
single startup win available to a Compose app.

**CI** (GitHub Actions): per-PR `assemble + lint + jvmTest + verifyRoborazzi + verifyAirportAsset`;
nightly `connectedCheck` on API 34 and 36 emulators; a **monthly scheduled dataset-refresh job**
that reruns the ETL, updates row-count expectations and opens a PR.

---

## 9. Build order

| Milestone | Deliverable | Stubbed |
| --- | --- | --- |
| **M0** | Repo, version catalog, `build-logic` conventions, one Compose screen, CI green, baseline-profile plugin wired | everything |
| **M1** | ETL producing `airports.db`; both Room DBs; asset open; **`verifyAirportAsset` in `check`**; fleet seeding; DAO + asset tests | UI is a debug screen printing row counts |
| **M2** | `:core:routing` complete and TDD'd — index, band index, great-circle, generator, scorer, statistics. Ends with a JMH number | no UI |
| **M3** ⭐ | **Runnable end-to-end**: Plan → generate 50 → cards → detail → mark flown → Logbook shows it | globe = static equirectangular image; weather = Unknown; fleet read-only |
| **M4** | Fleet, Airports, Stats; `NavigationSuiteScaffold` + `ListDetailPaneScaffold`; DataStore settings; full theming | globe, weather |
| **M5** | NOAA client, batched METAR, cache, flight-rules chips throughout, AVWX fallback | globe |
| **M6** | Globe, in this order: `Camera` + `Quadtree` ported and unit-tested with **no rendering at all**, then a solid-colour Filament sphere, then the tile atlas, then arc + markers, then gestures, then inertia and polish | — |
| **M7** | Motion, haptics, predictive back, Glance widget, shortcuts, screenshot goldens, baseline profile, R8 rules, Play listing | — |

M3 matters: the app is genuinely usable end-to-end within the first third of the schedule, and
every later milestone replaces a stub rather than adding a missing layer.

---

## 10. Verification

- `./gradlew :core:routing:test` — every invariant in §8 must pass.
- `./gradlew :tools:airportdb:run … && ./gradlew check` — `verifyAirportAsset` must pass; a clean
  clone must build offline.
- `./gradlew connectedCheck` — DAO, migration and Compose tests on a device.
- **Walk the parity matrix (§1) on a real device** — an emulator is not representative for
  Filament/Vulkan. Every row gets exercised: generate in all three modes, with and without a locked
  departure; scroll to trigger paging; search each list; toggle an airframe and confirm the date
  stamp; mark-all-not-flown; import and export CSV; add a flight manually; open route detail and
  check every element including both METARs and all gestures; mark flown and confirm the Logbook
  and statistics update; kill the app and confirm history and tiles survive.
- Long-range (8900 NM) and short-range (87 NM) airframes must both produce plausible in-range routes.
- Airplane-mode pass: everything except METAR and new tiles must still work, and the pinned z0–z3
  tiles must keep the globe legible.
- Confirm the Filament engine actually came up on `Backend.VULKAN` (log it), and re-verify on a
  low-RAM device that the atlas budget holds.

---

## 11. Risks and open questions

1. **Esri tile terms — highest-severity non-technical risk.** The anonymous ArcGIS
   `World_Imagery` endpoint is not obviously licensed for a consumer mobile app at scale.
   Attribution is necessary but almost certainly not sufficient. Resolve before Play release; keep
   `TileProvider` swappable and have a fallback ready (an ArcGIS developer key, or **NASA GIBS**,
   which is public domain and keyless but caps around z8–z9 — ample for a route-overview globe,
   insufficient for close inspection).
2. **Room prepackaged-asset identity hash** — highest-severity *technical* risk. Drift between the
   ETL's DDL and Room's expectation crashes at first open on every device. The schema-JSON-driven
   ETL plus `verifyAirportAsset` mitigates it, but **both must exist at M1**.
3. **Filament Vulkan availability.** Android's Filament default is OPENGL, and it is unconfirmed
   whether the prebuilt AAR is compiled with `FILAMENT_SUPPORTS_VULKAN`. Verify in M6; the
   `Backend.DEFAULT` fallback is a one-line change and loses nothing measurable at this workload.
4. **Runway data is the binding constraint.** Only 11k–29k runway rows survive filtering, and
   `length_ft` is blank or zero for many small fields — whose airports are then dropped silently,
   removing real destinations. Consider a `runway_length_known` flag and an "include airports with
   unknown runway length" option for short-field aircraft.
5. **Surface strings are messy** (`ASPH-G`, `GRVL`, `TURF`, `asphalt`, `PEM`…). A hard-surface
   filter needs ~50 hand-written mappings plus a long tail; the ETL must report unmapped values by
   frequency.
6. **GPU texture budget.** The 4096² RGB565 atlas ≈ 32 MB plus decode buffers must be measured on a
   real low-end device before M6 is called done, with the `isLowRamDevice` halving verified.
7. **SurfaceView compositing limits** (§5.1): no shared-element morph into the globe, and
   `toImageBitmap()` won't capture it — "share a route image" needs `PixelCopy` or an offscreen
   Filament render target.
8. **Version churn.** Material 3 Expressive, Navigation 3, and Room 3's asset support were all
   moving through 2026. Pin everything from live Maven metadata at M0 and keep the Expressive
   surface behind one wrapper file.
9. **Sim-only airport fields are simply gone** — OurAirports has no `PrimaryID`,
   `TransitionAltitude`, `TransitionLevel`, `SpeedLimit`, `SpeedLimitAltitude`. Drop them, or
   approximate transition altitude from a static ~200-row country table; pilots do care about that
   one and it is cheap.
10. **NOAA coverage/SLA** — free, keyless, global, but latency and completeness outside the US are
    uneven and there is no SLA. Send a descriptive `User-Agent`, cache aggressively, keep AVWX as
    the fallback.
11. **Route realism is thin.** Range + runway length is the whole model. Cheap additions that
    materially improve output: a minimum distance (kills 20 NM hops), a water/land check for float
    and amphibious aircraft, and an optional "destination has scheduled service" filter.
12. **Most likely v2 request:** importing a simulator's own `airports.db3` for sim-exact data. The
    two-database split (§3.2) is designed so it can be added later as an alternative airport source.
