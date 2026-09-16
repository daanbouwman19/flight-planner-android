package com.github.daanbouwman.flightplanner.launch

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.github.daanbouwman.flightplanner.di.ApplicationScope
import com.github.daanbouwman.flightplanner.navigation.Destination
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** The route the user most recently opened, for the "Last route" shortcut. */
interface LastRouteStore {
    suspend fun read(): Destination.RouteDetail?

    /** Fire-and-forget: a detail screen should not wait on a preferences write. */
    fun remember(route: Destination.RouteDetail)
}

/**
 * Which app version last published the widget's picker preview.
 *
 * `AppWidgetManager.setWidgetPreview` is rate-limited by the system and the
 * preview only changes when the widget's code does, so it is published once
 * per `versionCode` and this remembers which.
 */
interface WidgetPreviewStamp {
    suspend fun publishedVersion(): Long?
    fun setPublishedVersion(versionCode: Long)
}

private val Context.launchStore: DataStore<Preferences> by preferencesDataStore(name = "launch")

/**
 * One small preferences file, `datastore/launch.preferences_pb`, for the two
 * facts about launching the app that are neither settings nor user data.
 *
 * A second DataStore *file* rather than two more keys in `settings`: DataStore
 * refuses two instances over one file, so sharing it would mean widening
 * `SettingsRepository` with a route and a version stamp that are not settings.
 * The file is excluded from backup with the settings one — a restored phone
 * should publish its own preview and start with no "last route" rather than
 * someone else's.
 *
 * The delegate is lazy and the constructor does nothing, so being a `@Singleton`
 * costs the cold start nothing.
 */
@Singleton
internal class DefaultLaunchStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:ApplicationScope private val scope: CoroutineScope,
) : LastRouteStore, WidgetPreviewStamp {

    private val store: DataStore<Preferences> get() = context.launchStore

    override suspend fun read(): Destination.RouteDetail? {
        val encoded = preferences()[LAST_ROUTE] ?: return null
        // A route written by a build whose `RouteDetail` had different fields
        // is not worth a crash on a shortcut tap; it is simply not a last route.
        return try {
            Json.decodeFromString<Destination.RouteDetail>(encoded)
        } catch (malformed: SerializationException) {
            null
        } catch (malformed: IllegalArgumentException) {
            null
        }
    }

    override fun remember(route: Destination.RouteDetail) {
        scope.launch { store.edit { it[LAST_ROUTE] = Json.encodeToString(route) } }
    }

    override suspend fun publishedVersion(): Long? = preferences()[PREVIEW_VERSION]

    override fun setPublishedVersion(versionCode: Long) {
        scope.launch { store.edit { it[PREVIEW_VERSION] = versionCode } }
    }

    /** The file's contents, with an unreadable file read as empty rather than thrown. */
    private suspend fun preferences(): Preferences = store.data
        .catch { failure -> if (failure is IOException) emit(emptyPreferences()) else throw failure }
        .first()

    private companion object {
        val LAST_ROUTE = stringPreferencesKey("last_route")
        val PREVIEW_VERSION = longPreferencesKey("widget_preview_version")
    }
}
