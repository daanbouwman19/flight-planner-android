# `:macrobenchmark`

The instrument. Everything in `docs/UI-PLAN.md` that quotes a frame time or a
cold-start figure should eventually come from here.

## Running it

```bash
./gradlew :macrobenchmark:connectedBenchmarkAndroidTest
```

One physical device, screen on and unlocked. It builds `:app`'s `benchmark`
variant, installs both APKs, runs four benchmarks and uninstalls again — so
nothing is left on the device afterwards, and nothing stale can be measured by
mistake.

A single benchmark:

```bash
./gradlew :macrobenchmark:connectedBenchmarkAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.github.daanbouwman.flightplanner.macrobenchmark.PlanScrollBenchmark#flingNoCompilation
```

Results land in
`macrobenchmark/build/outputs/connected_android_test_additional_output/benchmark/connected/<device>/`
— a `.txt` summary per benchmark, a `benchmarkData.json` for all of them, and one
Perfetto trace per iteration. Open a trace at <https://ui.perfetto.dev> when a
number moves and you want to know which frame moved it.

## What it measures

| Benchmark | Metric | Question |
| --- | --- | --- |
| `PlanScrollBenchmark.flingNoCompilation` | `FrameTimingMetric` | How bad is the fling with nothing compiled — the first fling after an install |
| `PlanScrollBenchmark.flingPartialCompilation` | `FrameTimingMetric` | How good does it get once the code is compiled |
| `StartupBenchmark.startupNoCompilation` | `StartupTimingMetric` | Cold start, worst case |
| `StartupBenchmark.startupPartialCompilation` | `StartupTimingMetric` | Cold start, compiled |

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
