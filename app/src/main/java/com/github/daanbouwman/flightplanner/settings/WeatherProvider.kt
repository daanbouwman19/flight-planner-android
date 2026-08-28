package com.github.daanbouwman.flightplanner.settings

/**
 * Which service resolves METAR and flight-rules data.
 *
 * [NOAA] is the default — verified keyless, no SLA (docs/PLAN.md risk #10).
 * [AVWX] is the desktop app's provider, kept as an option (docs/UI-PLAN.md
 * F5), and needs [AppSettings.avwxApiKey] to be set.
 */
enum class WeatherProvider { NOAA, AVWX }
