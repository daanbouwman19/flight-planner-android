package com.github.daanbouwman.flightplanner.core.database.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
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
     * The shipped airport database, extracted from the APK asset on first run.
     *
     * `fallbackToDestructiveMigration` is the correct policy here and only here:
     * this database is derived data with no user content, so replacing it
     * wholesale on a dataset refresh loses nothing. The user database opposite
     * is migrated properly and never destructively.
     */
    @Provides
    @Singleton
    fun provideAirportDatabase(@ApplicationContext context: Context): AirportDatabase =
        Room.databaseBuilder(context, AirportDatabase::class.java, AirportDatabase.NAME)
            .createFromAsset(AirportDatabase.ASSET_PATH)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            // The asset is read-only, so WAL buys nothing and TRUNCATE avoids
            // leaving -wal/-shm sidecars next to a file we never write to.
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

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
