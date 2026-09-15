package com.github.daanbouwman.flightplanner.weather

import com.github.daanbouwman.flightplanner.core.database.repository.MetarCacheRepository
import com.github.daanbouwman.flightplanner.core.network.avwx.AvwxMetarClient
import com.github.daanbouwman.flightplanner.core.network.noaa.NoaaMetarClient
import com.github.daanbouwman.flightplanner.model.Metar
import com.github.daanbouwman.flightplanner.settings.SettingsRepository
import com.github.daanbouwman.flightplanner.settings.WeatherProvider
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The single entry point every screen fetches weather through: a read-through
 * cache (see [MetarCacheRepository]) in front of whichever provider is
 * currently selected in Settings.
 */
interface WeatherRepository {

    /** Cache hits and fresh fetches, merged. A station with no data at all is simply absent. */
    suspend fun fetch(stations: List<String>): Map<String, Metar>

    companion object {
        /**
         * How long a fetched report stays fresh. Matches the desktop app's own
         * `CACHE_DURATION`.
         *
         * Part of the interface rather than the implementation because callers have
         * a legitimate use for it that the cache cannot serve: **this cache records
         * hits, never misses.** A station that has no report at all is absent from
         * the returned map and absent from the cache, so the very next call asks the
         * network about it again. A caller that fetches on a repeating signal — a
         * scroll position, say — therefore has to remember what it has already
         * asked, and this is the interval it should remember it for.
         */
        const val TTL_MILLIS = 15 * 60 * 1000L
    }
}

@Singleton
internal class DefaultWeatherRepository @Inject constructor(
    private val cache: MetarCacheRepository,
    private val noaaClient: NoaaMetarClient,
    private val avwxClient: AvwxMetarClient,
    private val settingsRepository: SettingsRepository,
) : WeatherRepository {

    override suspend fun fetch(stations: List<String>): Map<String, Metar> {
        val wanted = stations.map { it.trim().uppercase() }.filter { it.isNotEmpty() }.distinct()
        if (wanted.isEmpty()) return emptyMap()

        val fresh = cache.freshFor(wanted, WeatherRepository.TTL_MILLIS)
        val missing = wanted - fresh.keys
        if (missing.isEmpty()) return fresh

        // **Awaited, not read off `.value`.** The settings flow is
        // `stateIn(Eagerly, null)`, so its value is genuinely null until DataStore
        // has read the file once — and `?: NOAA` turned that window into a silent
        // fall-back to NOAA, which is exactly what `AppSettings.avwxApiKey`'s own
        // KDoc says must not happen. It is not a narrow race either: the Plan
        // screen's first debounced batch and an Airport detail opened straight from
        // a cold start both land inside it, and the wrong provider's answer is then
        // written into the cache for a quarter of an hour.
        //
        // Suspending here is free at every call site — `fetch` is already suspend
        // and already about to do IO — and it happens at most once per process,
        // because the flow is hot and keeps its value afterwards.
        val settings = settingsRepository.settings.filterNotNull().first()
        val fetched = when (settings.weatherProvider) {
            WeatherProvider.NOAA -> noaaClient.fetchMetars(missing)
            WeatherProvider.AVWX -> {
                val apiKey = settings.avwxApiKey
                // An explicit AVWX choice with no key reads as "no weather",
                // not a silent fall-back to NOAA — the user asked for a
                // specific provider and has not finished configuring it.
                if (apiKey.isNullOrBlank()) emptyList() else fetchAvwx(missing, apiKey)
            }
        }

        if (fetched.isNotEmpty()) cache.upsertAll(fetched)
        return fresh + fetched.associateBy { it.station }
    }

    /**
     * Bounds how many AVWX requests are in flight at once, across every caller.
     *
     * A field on the singleton rather than a local in [fetchAvwx], so that two
     * screens fetching at the same moment share the one budget instead of each
     * getting four of their own.
     */
    private val avwxInFlight = Semaphore(AVWX_MAX_IN_FLIGHT)

    /**
     * AVWX has no batched endpoint, so the missing stations fan out concurrently
     * instead of one request — but no more than [AVWX_MAX_IN_FLIGHT] at a time.
     *
     * Unbounded, a Plan screen with fifty visible cards opened a hundred sockets
     * at once against a keyed, rate-limited API; the free tier answers that with
     * `429`s, which `fetchMetar` reports as "no report", so the user saw weather
     * for a random subset of the list. Four is well under any published limit
     * and still finishes a full batch inside the debounce window.
     */
    private suspend fun fetchAvwx(stations: List<String>, apiKey: String): List<Metar> = coroutineScope {
        stations.map { station -> async { avwxInFlight.withPermit { avwxClient.fetchMetar(station, apiKey) } } }
            .awaitAll()
            .filterNotNull()
    }

    internal companion object {
        const val AVWX_MAX_IN_FLIGHT = 4
    }
}
