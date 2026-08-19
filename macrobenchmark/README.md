# `:macrobenchmark`

The instrument. Everything in `docs/UI-PLAN.md` that quotes a frame time or a
cold-start figure should eventually come from here.

## Running it

```bash
./gradlew :macrobenchmark:connectedBenchmarkReleaseAndroidTest
```

One physical device, screen on and unlocked. It builds `:app`'s `benchmarkRelease`
variant, installs both APKs, runs six benchmarks and uninstalls again — so
nothing is left on the device afterwards, and nothing stale can be measured by
mistake. `BaselineProfileGenerator` is skipped on this variant, and the six
benchmarks are skipped on `nonMinifiedRelease`: the plugin sets
`androidx.benchmark.enabledRules` per variant, so neither can run where its
numbers would be meaningless.

A single benchmark:

```bash
./gradlew :macrobenchmark:connectedBenchmarkReleaseAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.github.daanbouwman.flightplanner.macrobenchmark.PlanScrollBenchmark#flingNoCompilation
```

Results land in
`macrobenchmark/build/outputs/connected_android_test_additional_output/benchmarkRelease/connected/<device>/`
— a `.txt` summary per benchmark, a `benchmarkData.json` for all of them, and one
Perfetto trace per iteration. Open a trace at <https://ui.perfetto.dev> when a
number moves and you want to know which frame moved it.

## What it measures

| Benchmark | Metric | Question |
| --- | --- | --- |
| `PlanScrollBenchmark.flingNoCompilation` | `FrameTimingMetric` | How bad is the fling with nothing compiled — the first fling after an install |
| `PlanScrollBenchmark.flingPartialCompilation` | `FrameTimingMetric` | How good does it get once the code is compiled |
| `PlanScrollBenchmark.flingBaselineProfile` | `FrameTimingMetric` | The first fling after an install, with P1's profile and nothing else |
| `StartupBenchmark.startupNoCompilation` | `StartupTimingMetric` | Cold start, worst case |
| `StartupBenchmark.startupBaselineProfile` | `StartupTimingMetric` | Cold start on first launch after an install — **the one a shipped app actually has** |
| `StartupBenchmark.startupPartialCompilation` | `StartupTimingMetric` | Cold start, compiled |

**Read the `...BaselineProfile` rows for anything about the profile.** Neither
neighbour can see it: `CompilationMode.None` runs `cmd package compile --reset`,
which discards the installed profile, and `CompilationMode.Partial()` warms the
JIT three times before measuring, so it is hot regardless. They are the floor and
the ceiling; the profile lives between them.

`BaselineProfileGenerator` is in this module too but is not a benchmark: it
measures nothing and reports nothing. It records which code the two journeys above
touch, so ART can compile it at install time. Run it deliberately, and commit what
it writes:

```bash
./gradlew :app:generateBaselineProfile
```

`frameOverrunMs` is the number to read, not `frameDurationCpuMs`: the test device
runs its panel at 120 Hz while scrolling, so a frame's deadline is 8.33 ms and a
CPU duration alone does not say whether it was met. Overrun is measured against
the frame's own deadline, so it stays correct when the refresh rate changes.

## Why it is arranged the way it is

**There is no debug variant.** `:app`'s debug APK is `debuggable`, and a
debuggable process runs largely interpreted — 872 ms cold start against 157 ms for
identical non-debuggable code. Two sessions of Phase P numbers once disagreed by a
factor of two for no reason anybody could find; the reason was that one session's
installs had silently failed and it was reading the debug build. The convention
plugin disables the debug variant outright, so `connectedDebugAndroidTest` does
not exist to be run by accident.

**There are exactly two variants, and only one of them is for measuring.**
`benchmarkRelease` is minified release code, debug-signed and profileable: every
published number comes from it. `nonMinifiedRelease` exists so P1's profile can be
generated against names that survive the next build, and running the benchmarks
there would report real-looking numbers for code R8 never touched.

Both come from `androidx.baselineprofile`. This module and `:app` used to
hand-write a `benchmark` build type that was `benchmarkRelease` in all but the
profileable flag, which was a `<profileable>` manifest in `app/src/benchmark`.
Keeping it once the plugin arrived turned this module into a cross product —
`connectedBenchmarkBenchmarkAndroidTest` and three siblings — so it was deleted
rather than kept for the sake of the shorter task name.

**`androidx.benchmark.suppressErrors` is not set, and must not be.** The library
refuses to measure a debuggable target, a rooted emulator or a device on low
battery. Those refusals are the check that would have caught the afternoon above.

**The fling is the only thing inside `measureBlock`.** Launching, generating fifty
routes and playing the staggered entrance all happen in `setupBlock`. Measuring
them together would give one number that moves whenever any of three unrelated
things does.

**Every scroll iteration kills the process first.** The complaint being quantified
is that the list stutters for a second or two and then smooths out. That is ART
warm-up, which is per-process; without the kill, nine of ten iterations would
measure an already-hot process.
