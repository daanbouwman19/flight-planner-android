---
paths:
  - "app/src/main/java/**/ui/**/*.kt"
  - "core/designsystem/**/*.kt"
  - "docs/DESIGN-SYSTEM.md"
  - "docs/API-GROUND-TRUTH.md"
---

# Before building or reworking any UI

Check for an established pattern before inventing a new one. This app has a
deliberate, documented visual language — treat a new component as a lookup
against it first, not a fresh decision.

1. **Read [`docs/DESIGN-SYSTEM.md`](../../docs/DESIGN-SYSTEM.md)** for the
   `:core:designsystem` component catalogue (shapes, motion tokens, chips,
   states) before writing a new composable. If something close already exists —
   `ModeSelector`'s filled-chip selection grammar, `FlightMotion`'s spring
   tokens, `FlightShapes`/`MorphShape` for shape-morph interactions — reuse and
   extend it rather than reaching for a stock Material component that reads as
   generic against the rest of the screen.
2. **Grep `core/designsystem/` directly** for the shape/motion/color primitive
   a new interaction needs (a FAB, a toggle, an emphasis animation) before
   assuming one has to be built from scratch. `Shapes.kt`'s `FlightShapes`
   object and its KDoc name real, current or previously-orphaned consumers —
   read those comments, they often already describe the exact pattern needed.
3. **For anything about Material 3 spec correctness** (default component
   sizes, what "Expressive" actually prescribes for a given component,
   spring/motion principles) — load the `material-3` skill and ground the
   decision in it rather than guessing from general impressions of what looks
   "more expressive." Size and ornamentation are not the same thing as motion;
   the spec's own default sizes are usually right.
4. **For a genuine visual rework** (not just wiring new data into existing
   components) — use the `frontend-design` skill's brainstorm-then-critique
   pass before writing code, per
   [[use-frontend-design-skill-for-ui-rework]] in memory.
5. **Respect `CLAUDE.md`'s invariants** while doing any of the above:
   Expressive symbols (`MaterialShapes`, `MotionScheme`, etc.) are reached only
   through `:core:designsystem`; `spring()`/`tween()` are never called outside
   it; nothing here needs an `SDK_INT` guard (`minSdk` 35).
6. **Verify a new Material3 API's availability by compiling a throwaway
   probe** before writing real code against it — the pinned `material3`
   version is `1.5.0-alpha26`, above the Compose BOM, and several symbols that
   exist in later stable releases are absent, `internal`, or crash
   deterministically in this exact version (see
   [`docs/API-GROUND-TRUTH.md`](../../docs/API-GROUND-TRUTH.md)). Never wrap
   a `SubcomposeLayout`-based component (a `LazyColumn`/`LazyRow`, `BoxWithConstraints`,
   `TabRow`) inside an `ExposedDropdownMenu` or anything else that asks for
   intrinsic measurements — it crashes at runtime, not compile time.
