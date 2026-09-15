package com.github.daanbouwman.flightplanner.feature.globe

/**
 * Whether this device can draw a globe, and if not, why.
 *
 * **One type, deliberately.** There used to be two enumerations of exactly these
 * three cases — an internal one the session resolved and a public one the UI
 * mapped it onto — and a third answer besides, from [FilamentProbe], which built
 * a real engine and reported on that. A device that declared GLES 3.0 and then
 * failed engine creation could be told PASS by the self-check while the globe
 * hid its own controls. Every question of the form "is there a renderer" now
 * ends in this type, answered by `GlobeSession.support`, and the probe reports
 * that verdict before it reports anything of its own.
 *
 * The globe itself never explains itself — 3B is explicit that a device without
 * a renderer shows the still map and *nothing drawn to say so*, because that is
 * the app working rather than the app failing. This exists for Settings and the
 * startup self-check, where somebody who has gone looking can find one line.
 */
enum class GlobeStatus {
    /** A renderer came up and there is memory for the atlas. */
    Available,

    /** Filament could not create an engine on any backend, or the platform declares no GLES 3.0. */
    NoRenderer,

    /** The platform reports a low-RAM device; 32 MB of atlas is not affordable. */
    LowMemory,
}
