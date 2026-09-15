package com.github.daanbouwman.flightplanner.core.database.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.github.daanbouwman.flightplanner.core.database.airport.AirportAssetInstaller
import com.github.daanbouwman.flightplanner.core.database.airport.AirportDao
import com.github.daanbouwman.flightplanner.core.database.airport.AirportDatabase
import com.github.daanbouwman.flightplanner.core.database.airport.DatasetMetaDao
import com.github.daanbouwman.flightplanner.core.database.airport.RunwayDao
import com.github.daanbouwman.flightplanner.core.database.user.AircraftDao
import com.github.daanbouwman.flightplanner.core.database.user.FlightLogDao
import com.github.daanbouwman.flightplanner.core.database.user.MetarCacheDao
import com.github.daanbouwman.flightplanner.core.database.user.UserDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /**
     * The shipped airport database.
     *
     * Note the absence of `createFromAsset`: Room rejects it outright when an
     * `SQLiteDriver` is configured ("Pre-Package Database is not supported when
     * an SQLiteDriver is configured"), and the bundled driver is what makes this
     * app behave the same on every OEM's SQLite. [AirportAssetInstaller]
     * therefore performs the copy and Room opens the resulting file.
     *
     * `fallbackToDestructiveMigration` is correct here and only here: this
     * database is derived data with no user content, so discarding it costs
     * nothing. The installer guarantees a matching copy is put back immediately.
     *
     * The wait below is the one blocking call the installer exposes, and this
     * is its one caller. The copy itself was started from `Application.onCreate`
     * on an IO dispatcher and has normally finished long before anything asks
     * for a DAO; this returns at once then. It blocks only for the remainder of
     * a copy still in flight, and it never starts a second one — a completed
     * copy is what makes it safe for Room to open the file, so it cannot be
     * made asynchronous without `createFromAsset`, which the bundled driver
     * rules out.
     */
    @Provides
    @Singleton
    fun provideAirportDatabase(
        @ApplicationContext context: Context,
        installer: AirportAssetInstaller,
    ): AirportDatabase {
        installer.ensureInstalledBlocking()
        return Room.databaseBuilder(context, AirportDatabase::class.java, AirportDatabase.NAME)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            // The database is read-only, so WAL buys nothing and TRUNCATE avoids
            // leaving -wal/-shm sidecars beside a file that is never written to.
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }

    @Provides
    @Singleton
    fun provideUserDatabase(@ApplicationContext context: Context): UserDatabase =
        Room.databaseBuilder(context, UserDatabase::class.java, UserDatabase.NAME)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()

    @Provides fun provideAirportDao(db: AirportDatabase): AirportDao = db.airportDao()

    @Provides fun provideRunwayDao(db: AirportDatabase): RunwayDao = db.runwayDao()

    @Provides fun provideDatasetMetaDao(db: AirportDatabase): DatasetMetaDao = db.datasetMetaDao()

    @Provides fun provideAircraftDao(db: UserDatabase): AircraftDao = db.aircraftDao()

    @Provides fun provideFlightLogDao(db: UserDatabase): FlightLogDao = db.flightLogDao()

    @Provides fun provideMetarCacheDao(db: UserDatabase): MetarCacheDao = db.metarCacheDao()
}
