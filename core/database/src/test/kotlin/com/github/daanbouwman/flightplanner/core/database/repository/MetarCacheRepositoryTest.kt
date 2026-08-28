package com.github.daanbouwman.flightplanner.core.database.repository

import com.github.daanbouwman.flightplanner.core.database.user.MetarCacheDao
import com.github.daanbouwman.flightplanner.core.database.user.MetarCacheEntity
import com.github.daanbouwman.flightplanner.core.database.user.toEntity
import com.github.daanbouwman.flightplanner.core.database.user.toMetar
import com.github.daanbouwman.flightplanner.model.Ceiling
import com.github.daanbouwman.flightplanner.model.ConvectiveCloud
import com.github.daanbouwman.flightplanner.model.FlightRules
import com.github.daanbouwman.flightplanner.model.Metar
import com.github.daanbouwman.flightplanner.model.MetarSupplement
import com.github.daanbouwman.flightplanner.model.SkyCover
import com.github.daanbouwman.flightplanner.model.buildMetar
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/** A real report, captured from NOAA on 2026-08-27. Three layers, one convective. */
private const val KJFK_RAW = "METAR KJFK 271851Z 17020G27KT 4SM -TSRA BR " +
    "FEW015 BKN043CB OVC130 23/22 A3003 RMK AO2 SLP170 P0001"

/** Every supplement field non-null and distinct, so a crossed wire fails rather than coincidentally matching. */
private fun fullSupplement() = MetarSupplement(
    flightRulesCode = "MVFR",
    reportKindCode = "SPECI",
    observationEpochSeconds = 1_787_853_060L,
    temperatureC = 23.3,
    dewpointC = 21.7,
    seaLevelPressureHpa = 1017.0,
    hourlyPrecipInches = 0.01,
    precip3hInches = 0.02,
    precip6hInches = 0.03,
    precip24hInches = 0.04,
    snowDepthInches = 1.5,
    latitude = 40.6392,
    longitude = -73.7639,
    elevationFt = 10,
    stationName = "New York/JF Kennedy Intl, NY, US",
)

private fun entity(station: String, fetchedAt: Long, raw: String = "$station RAW METAR") =
    Metar.fromRaw(station, raw).toEntity(fetchedAt)

/** Records what it was asked for and returns canned rows; no Room involved. */
private class FakeMetarCacheDao(
    private val rows: MutableMap<String, MetarCacheEntity> = mutableMapOf(),
) : MetarCacheDao {
    var lastForStationsQuery: List<String>? = null
        private set
    var upsertCalls = 0
        private set

    fun put(entity: MetarCacheEntity) {
        rows[entity.station] = entity
    }

    override suspend fun forStations(stations: List<String>): List<MetarCacheEntity> {
        lastForStationsQuery = stations
        return stations.mapNotNull { rows[it] }
    }

    override suspend fun forStation(station: String): MetarCacheEntity? = rows[station]

    override suspend fun upsertAll(rows: List<MetarCacheEntity>) {
        upsertCalls++
        rows.forEach { this.rows[it.station] = it }
    }

    override suspend fun evictOlderThan(olderThanEpochMillis: Long) {
        rows.entries.removeAll { it.value.fetchedAt < olderThanEpochMillis }
    }

    override suspend fun deleteAll() = rows.clear()
}

private const val FIFTEEN_MINUTES_MILLIS = 15 * 60 * 1000L

class MetarCacheRepositoryTest {

    @Test
    fun `an empty station list touches nothing`() = runTest {
        val dao = FakeMetarCacheDao()
        DefaultMetarCacheRepository(dao).freshFor(emptyList(), FIFTEEN_MINUTES_MILLIS) shouldBe emptyMap()
        dao.lastForStationsQuery shouldBe null
    }

    @Test
    fun `a row younger than the TTL is fresh`() = runTest {
        val now = 1_000_000L
        val dao = FakeMetarCacheDao().apply { put(entity("EHAM", fetchedAt = now - 14 * 60 * 1000L)) }

        val fresh = DefaultMetarCacheRepository(dao).freshFor(listOf("EHAM"), FIFTEEN_MINUTES_MILLIS, now)

        fresh.keys shouldBe setOf("EHAM")
    }

    @Test
    fun `a row older than the TTL is absent, not stale`() = runTest {
        val now = 1_000_000L
        val dao = FakeMetarCacheDao().apply { put(entity("EHAM", fetchedAt = now - 16 * 60 * 1000L)) }

        DefaultMetarCacheRepository(dao)
            .freshFor(listOf("EHAM"), FIFTEEN_MINUTES_MILLIS, now) shouldBe emptyMap()
    }

    @Test
    fun `stations are normalised to uppercase and de-duplicated before the query`() = runTest {
        val dao = FakeMetarCacheDao()

        DefaultMetarCacheRepository(dao).freshFor(listOf("eham", "EHAM", " kjfk "), FIFTEEN_MINUTES_MILLIS)

        dao.lastForStationsQuery shouldBe listOf("EHAM", "KJFK")
    }

    @Test
    fun `upserting nothing does not touch the dao`() = runTest {
        val dao = FakeMetarCacheDao()
        DefaultMetarCacheRepository(dao).upsertAll(emptyList())
        dao.upsertCalls shouldBe 0
    }

    @Test
    fun `a report with no raw text is never cached`() = runTest {
        // `raw` is what every decoded field is re-derived from, so a row without
        // it could not be faithfully returned.
        val dao = FakeMetarCacheDao()
        DefaultMetarCacheRepository(dao).upsertAll(listOf(Metar.unknown("ZZZZ")))
        dao.upsertCalls shouldBe 0
    }

    @Test
    fun `cloud layers survive a cache round trip`() = runTest {
        // The anti-flicker test. Under the previous design these were a
        // hand-maintained set of columns and would have been the first thing a
        // forgotten field dropped; now they are re-derived from `raw`, so there
        // is nothing to forget.
        val dao = FakeMetarCacheDao()
        val repository = DefaultMetarCacheRepository(dao)
        val original = buildMetar("KJFK", KJFK_RAW, fullSupplement())

        repository.upsertAll(listOf(original))
        val readBack = repository.freshFor(listOf("KJFK"), FIFTEEN_MINUTES_MILLIS).getValue("KJFK")

        val layers = (readBack.skyCover as SkyCover.Layers).layers
        layers.size shouldBe 3
        layers[1].convective shouldBe ConvectiveCloud.CUMULONIMBUS
        readBack.ceiling shouldBe Ceiling.At(ft = 4_300, indefinite = false)
        readBack.windGustKt shouldBe 27
        readBack.visibilityStatuteMiles shouldBe 4.0
        readBack.presentWeather.size shouldBe 2
        readBack.flightRules shouldBe FlightRules.MVFR
    }

    @Test
    fun `the whole observation round-trips through the entity, field for field`() {
        // Total, by data-class equality: every field including skyCover,
        // presentWeather and the provider-only facts. A field wired to the wrong
        // column fails here, which is the gap the compile-time guard cannot close.
        val original = buildMetar("KJFK", KJFK_RAW, fullSupplement())

        original.toEntity(fetchedAt = 1L).toMetar() shouldBe original
    }

    @Test
    fun `provider-only facts survive, since those are the ones that do need columns`() {
        val original = buildMetar("KJFK", KJFK_RAW, fullSupplement())
        val readBack = original.toEntity(fetchedAt = 1L).toMetar()

        readBack.latitude!! shouldBe (40.6392 plusOrMinus 0.0001)
        readBack.elevationFt shouldBe 10
        readBack.snowDepthInches!! shouldBe (1.5 plusOrMinus 0.001)
        readBack.precip24hInches!! shouldBe (0.04 plusOrMinus 0.001)
        readBack.observationEpochSeconds shouldBe 1_787_853_060L
        readBack.seaLevelPressureHpa!! shouldBe (1017.0 plusOrMinus 0.001)
        readBack.stationName shouldBe "New York/JF Kennedy Intl, NY, US"
        readBack.isSpeci shouldBe true
    }

    @Test
    fun `an unreported sky stays unreported across the cache`() = runTest {
        // The bug, at the cache boundary: a cached report with no cloud group
        // must not come back as clear.
        val dao = FakeMetarCacheDao()
        val repository = DefaultMetarCacheRepository(dao)
        val original = Metar.fromRaw("XXXX", "METAR XXXX 271800Z 31006KT 10SM 22/17 Q1008")

        repository.upsertAll(listOf(original))
        val readBack = repository.freshFor(listOf("XXXX"), FIFTEEN_MINUTES_MILLIS).getValue("XXXX")

        readBack.skyUnknown shouldBe true
        readBack.ceiling shouldBe Ceiling.Unknown
        readBack.flightRules shouldBe FlightRules.UNKNOWN
    }
}
