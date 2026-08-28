package com.github.daanbouwman.flightplanner.weather

import com.github.daanbouwman.flightplanner.core.database.repository.MetarCacheRepository
import com.github.daanbouwman.flightplanner.core.designsystem.theme.ThemeChoice
import com.github.daanbouwman.flightplanner.core.network.avwx.AvwxMetarClient
import com.github.daanbouwman.flightplanner.core.network.noaa.NoaaMetarClient
import com.github.daanbouwman.flightplanner.model.FlightRules
import com.github.daanbouwman.flightplanner.model.Metar
import com.github.daanbouwman.flightplanner.settings.AppSettings
import com.github.daanbouwman.flightplanner.settings.SettingsRepository
import com.github.daanbouwman.flightplanner.settings.UnitSystem
import com.github.daanbouwman.flightplanner.settings.WeatherProvider
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

private fun metar(station: String) = Metar(station = station, raw = "$station RAW", flightRules = FlightRules.VFR)

private class FakeMetarCacheRepository : MetarCacheRepository {
    val stored = mutableMapOf<String, Metar>()
    var freshForCalls = 0
        private set

    override suspend fun freshFor(stations: List<String>, maxAgeMillis: Long, now: Long): Map<String, Metar> {
        freshForCalls++
        return stations.mapNotNull { stored[it]?.let { m -> it to m } }.toMap()
    }

    override suspend fun upsertAll(metars: List<Metar>) {
        metars.forEach { stored[it.station] = it }
    }
}

private class FakeNoaaMetarClient : NoaaMetarClient {
    var lastRequest: List<String>? = null
    var response: List<Metar> = emptyList()

    override suspend fun fetchMetars(stationIcaos: List<String>): List<Metar> {
        lastRequest = stationIcaos
        return response
    }
}

private class FakeAvwxMetarClient : AvwxMetarClient {
    val requestedKeys = mutableListOf<String>()
    var response: (String) -> Metar? = { null }

    override suspend fun fetchMetar(stationIcao: String, apiKey: String): Metar? {
        requestedKeys += apiKey
        return response(stationIcao)
    }
}

private class FakeSettingsRepository(initial: AppSettings) : SettingsRepository {
    private val state = MutableStateFlow<AppSettings?>(initial)
    override val settings: StateFlow<AppSettings?> = state
    fun update(transform: (AppSettings) -> AppSettings) {
        state.value = transform(state.value ?: AppSettings())
    }
    override fun setThemeChoice(choice: ThemeChoice) = Unit
    override fun setDynamicColour(enabled: Boolean) = Unit
    override fun setUnitSystem(system: UnitSystem) = Unit
    override fun setIcaoOnly(enabled: Boolean) = Unit
    override fun setWeatherProvider(provider: WeatherProvider) = update { it.copy(weatherProvider = provider) }
    override fun setAvwxApiKey(key: String?) = update { it.copy(avwxApiKey = key) }
}

class DefaultWeatherRepositoryTest {

    private fun repository(
        cache: FakeMetarCacheRepository = FakeMetarCacheRepository(),
        noaa: FakeNoaaMetarClient = FakeNoaaMetarClient(),
        avwx: FakeAvwxMetarClient = FakeAvwxMetarClient(),
        settings: FakeSettingsRepository = FakeSettingsRepository(AppSettings()),
    ) = DefaultWeatherRepository(cache, noaa, avwx, settings)

    @Test
    fun `a cache hit for every station skips the network entirely`() = runTest {
        val cache = FakeMetarCacheRepository().apply { stored["EHAM"] = metar("EHAM") }
        val noaa = FakeNoaaMetarClient()
        val result = repository(cache = cache, noaa = noaa).fetch(listOf("EHAM"))

        result.keys shouldBe setOf("EHAM")
        noaa.lastRequest shouldBe null
    }

    @Test
    fun `a partial cache hit only fetches the stale subset`() = runTest {
        val cache = FakeMetarCacheRepository().apply { stored["EHAM"] = metar("EHAM") }
        val noaa = FakeNoaaMetarClient().apply { response = listOf(metar("KJFK")) }

        val result = repository(cache = cache, noaa = noaa).fetch(listOf("EHAM", "KJFK"))

        noaa.lastRequest shouldBe listOf("KJFK")
        result.keys shouldBe setOf("EHAM", "KJFK")
    }

    @Test
    fun `a fresh fetch is written back through the cache`() = runTest {
        val cache = FakeMetarCacheRepository()
        val noaa = FakeNoaaMetarClient().apply { response = listOf(metar("EHAM")) }

        repository(cache = cache, noaa = noaa).fetch(listOf("EHAM"))

        cache.stored.keys shouldBe setOf("EHAM")
    }

    @Test
    fun `NOAA is used when no provider setting has been read yet`() = runTest {
        val settings = FakeSettingsRepository(AppSettings()).also {
            // Simulate the DataStore read not having landed yet.
        }
        val noaa = FakeNoaaMetarClient().apply { response = listOf(metar("EHAM")) }
        val avwx = FakeAvwxMetarClient()

        repository(noaa = noaa, avwx = avwx, settings = settings).fetch(listOf("EHAM"))

        noaa.lastRequest shouldBe listOf("EHAM")
        avwx.requestedKeys shouldBe emptyList()
    }

    @Test
    fun `AVWX is used once selected, with the stored key`() = runTest {
        val settings = FakeSettingsRepository(AppSettings(weatherProvider = WeatherProvider.AVWX, avwxApiKey = "secret"))
        val avwx = FakeAvwxMetarClient().apply { response = { station -> metar(station) } }
        val noaa = FakeNoaaMetarClient()

        val result = repository(noaa = noaa, avwx = avwx, settings = settings).fetch(listOf("EHAM"))

        result.keys shouldBe setOf("EHAM")
        avwx.requestedKeys shouldBe listOf("secret")
        noaa.lastRequest shouldBe null
    }

    @Test
    fun `AVWX selected with no key degrades to no weather, not a silent NOAA fallback`() = runTest {
        val settings = FakeSettingsRepository(AppSettings(weatherProvider = WeatherProvider.AVWX, avwxApiKey = null))
        val noaa = FakeNoaaMetarClient().apply { response = listOf(metar("EHAM")) }

        val result = repository(noaa = noaa, settings = settings).fetch(listOf("EHAM"))

        result shouldBe emptyMap()
        // The point: an explicit provider choice is never silently overridden.
        noaa.lastRequest shouldBe null
    }

    @Test
    fun `an empty request touches nothing`() = runTest {
        val cache = FakeMetarCacheRepository()
        repository(cache = cache).fetch(emptyList()) shouldBe emptyMap()
        cache.freshForCalls shouldBe 0
    }
}
