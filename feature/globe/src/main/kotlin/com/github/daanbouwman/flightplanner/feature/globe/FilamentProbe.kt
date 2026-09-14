package com.github.daanbouwman.flightplanner.feature.globe

import android.content.Context
import android.util.Log
import com.github.daanbouwman.flightplanner.feature.globe.render.GlobeSession
import com.google.android.filament.Engine
import com.google.android.filament.Filament

private const val TAG = "FilamentProbe"

/**
 * What actually happened when Filament started, as opposed to what was asked for.
 *
 * [support] is the verdict the globe itself acts on — the same [GlobeStatus]
 * `GlobeSession.support` hands the UI — and the rest is the detail behind it.
 * A report whose [support] is not [GlobeStatus.Available] describes a device on
 * which the globe's controls are absent, whatever the backend fields say.
 */
data class FilamentReport(
    val support: GlobeStatus,
    val nativeLibraryLoaded: Boolean,
    /** The backend Filament actually created, which may not be the requested one. */
    val activeBackend: String?,
    val requestedBackend: String,
    val featureLevel: String?,
    val error: String? = null,
) {
    val vulkanActive: Boolean get() = activeBackend == Engine.Backend.VULKAN.name

    fun summary(): String = when {
        support == GlobeStatus.LowMemory -> "not attempted: low-RAM device, the globe is off"
        error != null -> "failed: $error"
        support == GlobeStatus.NoRenderer -> "no renderer: the platform declares no GLES 3.0"
        vulkanActive -> "Vulkan backend active, feature level $featureLevel"
        activeBackend != null -> "$activeBackend backend active (requested $requestedBackend), feature level $featureLevel"
        else -> "engine could not be created"
    }
}

/**
 * Asks whether this device has a renderer, the way the globe asks it — and then
 * finds out which backend Filament actually brings up.
 *
 * ### Two steps, in this order, and why
 *
 * This used to build an engine and report on that alone, while
 * `GlobeSession.resolveSupport` was deliberately changed to read the platform's
 * declared GLES version instead — because it runs inside composition, where an
 * engine cannot be built. Two answers to one question, and they disagreed on
 * exactly the device that matters: one that declares GLES 3.0 and then fails
 * engine creation reported PASS here while the globe hid its own controls.
 *
 * So the first step is `GlobeSession.support`, cached for the process and the
 * verdict every other caller sees. Only if that says [GlobeStatus.Available] is
 * an engine built — the one thing the declared version cannot tell — and if
 * *that* fails the session is told, so the controls go on the same device the
 * report calls failed. A device the platform rules out never has an engine
 * attempted on it: there is nothing to learn from a probe on a low-RAM device
 * except how long a 32 MB allocation takes to be refused.
 *
 * Creating and immediately destroying an engine is cheap and has no window or
 * surface attached, so the second step is safe to run at startup. It answers a
 * question that could not be settled from documentation: the published
 * `filament-android` AAR may or may not be compiled with Vulkan support, and the
 * `Engine.Backend` enum lists `VULKAN` whether or not the native library can
 * actually provide it, so asking for it and then reading back [Engine.getBackend]
 * is the only reliable check — and it has to happen on real hardware, since an
 * emulator's driver is not representative.
 */
object FilamentProbe {

    fun run(context: Context, requested: Engine.Backend = Engine.Backend.VULKAN): FilamentReport {
        val support = GlobeSession.support(context)
        if (support != GlobeStatus.Available) {
            return FilamentReport(
                support = support,
                nativeLibraryLoaded = false,
                activeBackend = null,
                requestedBackend = requested.name,
                featureLevel = null,
            )
        }

        val loaded = runCatching { Filament.init() }
            .onFailure { Log.e(TAG, "Filament native library failed to load", it) }
            .isSuccess

        if (!loaded) {
            GlobeSession.markNoRenderer()
            return FilamentReport(
                support = GlobeStatus.NoRenderer,
                nativeLibraryLoaded = false,
                activeBackend = null,
                requestedBackend = requested.name,
                featureLevel = null,
                error = "native library did not load",
            )
        }

        var engine: Engine? = null
        return try {
            engine = Engine.Builder().backend(requested).build()
            FilamentReport(
                support = support,
                nativeLibraryLoaded = true,
                activeBackend = engine.backend.name,
                requestedBackend = requested.name,
                featureLevel = engine.supportedFeatureLevel.name,
            )
        } catch (t: Throwable) {
            Log.e(TAG, "Filament engine creation failed for $requested", t)
            // The session would find this out on its first `acquire` and cache
            // it then; telling it now means Settings and the route detail agree
            // with this report from the first screen rather than the first globe.
            GlobeSession.markNoRenderer()
            FilamentReport(
                support = GlobeStatus.NoRenderer,
                nativeLibraryLoaded = true,
                activeBackend = null,
                requestedBackend = requested.name,
                featureLevel = null,
                error = t.message ?: t::class.java.simpleName,
            )
        } finally {
            runCatching { engine?.destroy() }
        }
    }
}
