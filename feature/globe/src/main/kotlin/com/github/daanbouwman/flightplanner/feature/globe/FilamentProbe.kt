package com.github.daanbouwman.flightplanner.feature.globe

import android.util.Log
import com.google.android.filament.Engine
import com.google.android.filament.Filament

private const val TAG = "FilamentProbe"

/**
 * What actually happened when Filament started, as opposed to what was asked for.
 */
data class FilamentReport(
    val nativeLibraryLoaded: Boolean,
    /** The backend Filament actually created, which may not be the requested one. */
    val activeBackend: String?,
    val requestedBackend: String,
    val featureLevel: String?,
    val error: String? = null,
) {
    val vulkanActive: Boolean get() = activeBackend == Engine.Backend.VULKAN.name

    fun summary(): String = when {
        error != null -> "failed: $error"
        vulkanActive -> "Vulkan backend active, feature level $featureLevel"
        activeBackend != null -> "$activeBackend backend active (requested $requestedBackend), feature level $featureLevel"
        else -> "engine could not be created"
    }
}

/**
 * Starts a Filament engine once and reports which backend came up.
 *
 * This answers a question that could not be settled from documentation: the
 * published `filament-android` AAR may or may not be compiled with Vulkan
 * support, and Android's Filament default is OpenGL regardless. The
 * `Engine.Backend` enum lists `VULKAN` whether or not the native library can
 * actually provide it, so asking for it and then reading back
 * [Engine.getBackend] is the only reliable check — and it has to happen on real
 * hardware, since an emulator's driver is not representative.
 *
 * Creating and immediately destroying an engine is cheap and has no window or
 * surface attached, so this is safe to run at startup.
 */
object FilamentProbe {

    fun run(requested: Engine.Backend = Engine.Backend.VULKAN): FilamentReport {
        val loaded = runCatching { Filament.init() }
            .onFailure { Log.e(TAG, "Filament native library failed to load", it) }
            .isSuccess

        if (!loaded) {
            return FilamentReport(
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
                nativeLibraryLoaded = true,
                activeBackend = engine.backend.name,
                requestedBackend = requested.name,
                featureLevel = engine.supportedFeatureLevel.name,
            )
        } catch (t: Throwable) {
            Log.e(TAG, "Filament engine creation failed for $requested", t)
            FilamentReport(
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
