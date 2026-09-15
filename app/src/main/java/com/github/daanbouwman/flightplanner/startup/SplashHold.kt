package com.github.daanbouwman.flightplanner.startup

/**
 * Whether the launch splash stays on screen for another frame.
 *
 * Three things are waited for, and they are not bounded alike:
 *
 * - **The airport database install is waited for without a deadline.** Room
 *   is not using `createFromAsset`, so the first DAO query has to find the
 *   file already copied out of the APK, and `DatabaseModule` guarantees that
 *   with `AirportAssetInstaller.ensureInstalledBlocking()` — a `runBlocking` on
 *   whichever thread first asks for a DAO, which in practice is the main
 *   thread during Plan's first composition. On every launch but the first the
 *   install is a sidecar read that settles in a few milliseconds and this
 *   clause never holds anything. On the first launch it is a ~30 MB copy that
 *   outlives any sensible splash deadline; when the deadline used to cap it,
 *   the app appeared, Plan composed, and the main thread blocked for the rest
 *   of the copy behind a half-drawn screen — the original defect narrowed to
 *   first launch rather than removed. A splash that lasts the copy is honest
 *   about the same wait. It cannot hang forever: `Failed` counts as settled, so
 *   a corrupt or missing asset releases the splash and lets the self-check
 *   screen report it.
 *
 * - **The index build and the stored settings are waited for under the
 *   deadline.** The index normally settles in single-digit milliseconds and the
 *   settings are one small file read; the deadline exists so that an asset the
 *   loader cannot read is a longer splash followed by skeletons rather than an
 *   infinite splash. The settings are waited for at all so the first frame does
 *   not draw in the default theme and flip a few milliseconds later — a light
 *   flash in front of someone who chose Cockpit precisely so they would not get
 *   one.
 *
 * Pure, so the shape above is tested rather than trusted; `MainActivity` feeds
 * it the live values each time the splash asks.
 */
fun splashShouldHold(
    indexSettled: Boolean,
    installSettled: Boolean,
    settingsLoaded: Boolean,
    now: Long,
    deadline: Long,
): Boolean {
    if (!installSettled) return true
    return (!indexSettled || !settingsLoaded) && now < deadline
}
